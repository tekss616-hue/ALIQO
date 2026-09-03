package com.aliqo.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.socket.client.IO
import io.socket.emitter.Emitter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

data class ChatMemberDto(val id: String, val userId: String, val isAdmin: Boolean = false, val lastReadAt: String? = null, val user: UserDto)
data class MessageReactionDto(val id: String? = null, val messageId: String? = null, val userId: String, val emoji: String)
data class MessageReplyDto(val id: String, val text: String? = null, val type: String? = null, val senderId: String? = null)
data class MessageDto(
    val id: String,
    val chatId: String,
    val senderId: String,
    val type: String = "TEXT",
    val text: String? = null,
    val mediaUrl: String? = null,
    val mediaName: String? = null,
    val mediaMime: String? = null,
    val mediaSize: Int? = null,
    val replyToId: String? = null,
    val isEdited: Boolean = false,
    val pinnedAt: String? = null,
    val createdAt: String? = null,
    val sender: UserDto,
    val replyTo: MessageReplyDto? = null,
    val reactions: List<MessageReactionDto> = emptyList()
)
data class ChatDto(val id: String, val type: String, val title: String? = null, val avatarUrl: String? = null, val members: List<ChatMemberDto> = emptyList(), val messages: List<MessageDto> = emptyList())
data class CreateDirectRequest(val userId: String)
data class CreateGroupRequest(val title: String, val memberIds: List<String>)
data class SendMessageRequest(val type: String = "TEXT", val text: String? = null, val mediaUrl: String? = null, val mediaName: String? = null, val mediaMime: String? = null, val mediaSize: Int? = null, val replyToId: String? = null)
data class EditMessageRequest(val text: String)
data class ReactionRequest(val emoji: String)
data class ReadRequest(val messageId: String? = null)
data class GroupMemberRequest(val userId: String)

interface ChatApi {
    @GET("chats") suspend fun chats(@Header("Authorization") auth: String): List<ChatDto>
    @POST("chats/direct") suspend fun direct(@Header("Authorization") auth: String, @Body body: CreateDirectRequest): ChatDto
    @POST("chats/group") suspend fun group(@Header("Authorization") auth: String, @Body body: CreateGroupRequest): ChatDto
    @GET("chats/{chatId}/messages") suspend fun messages(@Header("Authorization") auth: String, @Path("chatId") chatId: String, @Query("take") take: Int = 80): List<MessageDto>
    @GET("chats/{chatId}/search") suspend fun searchMessages(@Header("Authorization") auth: String, @Path("chatId") chatId: String, @Query("q") query: String): List<MessageDto>
    @POST("chats/{chatId}/messages") suspend fun send(@Header("Authorization") auth: String, @Path("chatId") chatId: String, @Body body: SendMessageRequest): MessageDto
    @PATCH("chats/{chatId}/messages/{messageId}") suspend fun edit(@Header("Authorization") auth: String, @Path("chatId") chatId: String, @Path("messageId") messageId: String, @Body body: EditMessageRequest): MessageDto
    @DELETE("chats/{chatId}/messages/{messageId}") suspend fun deleteMessage(@Header("Authorization") auth: String, @Path("chatId") chatId: String, @Path("messageId") messageId: String): OkResponse
    @POST("chats/{chatId}/messages/{messageId}/reactions") suspend fun react(@Header("Authorization") auth: String, @Path("chatId") chatId: String, @Path("messageId") messageId: String, @Body body: ReactionRequest): List<MessageReactionDto>
    @POST("chats/{chatId}/messages/{messageId}/pin") suspend fun pin(@Header("Authorization") auth: String, @Path("chatId") chatId: String, @Path("messageId") messageId: String): MessageDto
    @POST("chats/{chatId}/read") suspend fun read(@Header("Authorization") auth: String, @Path("chatId") chatId: String, @Body body: ReadRequest): OkResponse
    @GET("friends") suspend fun friends(@Header("Authorization") auth: String): List<UserDto>
}

private val chatApi: ChatApi by lazy {
    val client = OkHttpClient.Builder().connectTimeout(75, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS).writeTimeout(75, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
    Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(ChatApi::class.java)
}

private fun chatTitle(chat: ChatDto, me: UserDto?): String {
    if (chat.type == "GROUP") return chat.title ?: "مجموعة"
    return chat.members.firstOrNull { it.userId != me?.id }?.user?.profile?.displayName?.ifBlank { null }
        ?: chat.members.firstOrNull { it.userId != me?.id }?.user?.username ?: "محادثة"
}

@Composable
fun ChatsScreen(auth: String, me: UserDto?) {
    var chats by remember { mutableStateOf<List<ChatDto>>(emptyList()) }
    var friends by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var selectedChat by remember { mutableStateOf<ChatDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        chats = chatApi.chats(auth)
        friends = chatApi.friends(auth)
        selectedChat?.let { chosen -> chats.firstOrNull { it.id == chosen.id }?.let { selectedChat = it } }
    }
    LaunchedEffect(auth) { try { refresh() } catch (e: Exception) { status = e.message ?: "تعذر تحميل المحادثات" } }

    DisposableEffect(auth) {
        val token = auth.removePrefix("Bearer ").trim()
        val socket = IO.socket(BuildConfig.REALTIME_URL, IO.Options.builder().setAuth(mapOf("token" to token)).setReconnection(true).build())
        val changed = Emitter.Listener { scope.launch { try { refresh() } catch (_: Exception) {} } }
        socket.on("chats:changed", changed); socket.on("notifications:changed", changed); socket.connect()
        onDispose { socket.off("chats:changed", changed); socket.off("notifications:changed", changed); socket.disconnect(); socket.close() }
    }

    selectedChat?.let { chat ->
        ChatRoomScreen(auth, me, chat, onBack = { selectedChat = null; scope.launch { try { refresh() } catch (_: Exception) {} } })
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("المحادثات", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { creating = !creating }) { Text(if (creating) "إغلاق" else "محادثة جديدة") }
        }
        if (creating) NewChatPanel(auth, friends, onCreated = { chat -> selectedChat = chat; creating = false })
        if (chats.isEmpty()) Text("لا توجد محادثات بعد")
        chats.forEach { chat ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(chatTitle(chat, me), fontWeight = FontWeight.Bold)
                    val last = chat.messages.firstOrNull()
                    if (last != null) Text(last.text ?: when (last.type) { "IMAGE" -> "📷 صورة"; "VIDEO" -> "🎥 فيديو"; "VOICE" -> "🎙️ رسالة صوتية"; "FILE" -> "📎 ملف"; else -> "رسالة" })
                    Button(onClick = { selectedChat = chat }, modifier = Modifier.fillMaxWidth()) { Text("فتح المحادثة") }
                }
            }
        }
        if (status.isNotBlank()) Text(status)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun NewChatPanel(auth: String, friends: List<UserDto>, onCreated: (ChatDto) -> Unit) {
    var groupTitle by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("محادثة خاصة", fontWeight = FontWeight.Bold)
            friends.forEach { user ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(user.profile?.displayName ?: "@${user.username}")
                    Button(onClick = { scope.launch { try { onCreated(chatApi.direct(auth, CreateDirectRequest(user.id))) } catch (e: Exception) { status = e.message ?: "تعذر إنشاء المحادثة" } } }) { Text("مراسلة") }
                }
            }
            HorizontalDivider()
            Text("إنشاء مجموعة", fontWeight = FontWeight.Bold)
            OutlinedTextField(groupTitle, { groupTitle = it.take(80) }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم المجموعة") })
            friends.forEach { user ->
                val checked = user.id in selected
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(user.profile?.displayName ?: "@${user.username}")
                    OutlinedButton(onClick = { selected = if (checked) selected - user.id else selected + user.id }) { Text(if (checked) "✓ مضاف" else "إضافة") }
                }
            }
            Button(onClick = { scope.launch { try { onCreated(chatApi.group(auth, CreateGroupRequest(groupTitle.trim(), selected.toList()))) } catch (e: Exception) { status = e.message ?: "تعذر إنشاء المجموعة" } } }, modifier = Modifier.fillMaxWidth(), enabled = groupTitle.isNotBlank()) { Text("إنشاء المجموعة") }
            if (status.isNotBlank()) Text(status)
        }
    }
}

@Composable
private fun ChatRoomScreen(auth: String, me: UserDto?, chat: ChatDto, onBack: () -> Unit) {
    var messages by remember(chat.id) { mutableStateOf<List<MessageDto>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<MessageDto?>(null) }
    var editing by remember { mutableStateOf<MessageDto?>(null) }
    var typing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun refresh() { messages = chatApi.messages(auth, chat.id).reversed() }
    LaunchedEffect(chat.id) { try { refresh(); messages.lastOrNull()?.let { chatApi.read(auth, chat.id, ReadRequest(it.id)) } } catch (e: Exception) { status = e.message ?: "تعذر تحميل الرسائل" } }

    DisposableEffect(chat.id, auth) {
        val token = auth.removePrefix("Bearer ").trim()
        val socket = IO.socket(BuildConfig.REALTIME_URL, IO.Options.builder().setAuth(mapOf("token" to token)).setReconnection(true).build())
        val refreshListener = Emitter.Listener { scope.launch { try { refresh() } catch (_: Exception) {} } }
        val typingListener = Emitter.Listener { args ->
            val obj = args.firstOrNull() as? org.json.JSONObject ?: return@Listener
            if (obj.optString("chatId") == chat.id && obj.optString("userId") != me?.id) typing = obj.optBoolean("isTyping")
        }
        socket.on("connect") { socket.emit("chat:join", org.json.JSONObject().put("chatId", chat.id)) }
        socket.on("message:new", refreshListener); socket.on("message:updated", refreshListener); socket.on("message:deleted", refreshListener); socket.on("message:reactions", refreshListener); socket.on("message:pinned", refreshListener); socket.on("typing:changed", typingListener)
        socket.connect()
        onDispose { socket.emit("typing:stop", org.json.JSONObject().put("chatId", chat.id)); socket.off(); socket.disconnect(); socket.close() }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onBack) { Text("رجوع") }
            Text(chatTitle(chat, me), fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(search, { search = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("بحث في الرسائل") })
            Button(onClick = { scope.launch { try { messages = if (search.length >= 2) chatApi.searchMessages(auth, chat.id, search).reversed() else chatApi.messages(auth, chat.id).reversed() } catch (_: Exception) {} } }) { Text("بحث") }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            messages.forEach { msg -> MessageCard(msg, mine = msg.senderId == me?.id, onReply = { replyTo = msg; editing = null }, onEdit = { editing = msg; text = msg.text.orEmpty(); replyTo = null }, onDelete = { scope.launch { try { chatApi.deleteMessage(auth, chat.id, msg.id); refresh() } catch (_: Exception) {} } }, onReact = { emoji -> scope.launch { try { chatApi.react(auth, chat.id, msg.id, ReactionRequest(emoji)); refresh() } catch (_: Exception) {} } }, onPin = { scope.launch { try { chatApi.pin(auth, chat.id, msg.id); refresh() } catch (_: Exception) {} } }) }
        }
        if (typing) Text("يكتب الآن…")
        replyTo?.let { Text("رد على: ${it.text ?: it.type}") }
        editing?.let { Text("تعديل الرسالة") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(text, {
                text = it.take(4000)
            }, modifier = Modifier.weight(1f), maxLines = 4, label = { Text("اكتب رسالة") })
            Button(onClick = {
                val body = text.trim(); if (body.isBlank()) return@Button
                scope.launch {
                    try {
                        if (editing != null) chatApi.edit(auth, chat.id, editing!!.id, EditMessageRequest(body))
                        else chatApi.send(auth, chat.id, SendMessageRequest(text = body, replyToId = replyTo?.id))
                        text = ""; editing = null; replyTo = null; refresh()
                    } catch (e: Exception) { status = e.message ?: "تعذر إرسال الرسالة" }
                }
            }) { Text(if (editing != null) "حفظ" else "إرسال") }
        }
        if (status.isNotBlank()) Text(status)
    }
}

@Composable
private fun MessageCard(msg: MessageDto, mine: Boolean, onReply: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onReact: (String) -> Unit, onPin: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (mine) "أنت" else (msg.sender.profile?.displayName ?: "@${msg.sender.username}"), fontWeight = FontWeight.Bold)
            msg.replyTo?.let { Text("↩ ${it.text ?: it.type}") }
            Text(msg.text ?: when (msg.type) { "IMAGE" -> "📷 ${msg.mediaName ?: "صورة"}"; "VIDEO" -> "🎥 ${msg.mediaName ?: "فيديو"}"; "VOICE" -> "🎙️ ${msg.mediaName ?: "رسالة صوتية"}"; "FILE" -> "📎 ${msg.mediaName ?: "ملف"}"; else -> "رسالة" })
            if (msg.isEdited) Text("تم التعديل", style = MaterialTheme.typography.labelSmall)
            if (msg.pinnedAt != null) Text("📌 مثبتة", style = MaterialTheme.typography.labelSmall)
            if (msg.reactions.isNotEmpty()) Text(msg.reactions.joinToString(" ") { it.emoji })
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onReply) { Text("رد") }
                TextButton(onClick = { onReact("❤️") }) { Text("❤️") }
                TextButton(onClick = onPin) { Text("تثبيت") }
                if (mine && msg.type == "TEXT") TextButton(onClick = onEdit) { Text("تعديل") }
                if (mine) TextButton(onClick = onDelete) { Text("حذف") }
            }
        }
    }
}
