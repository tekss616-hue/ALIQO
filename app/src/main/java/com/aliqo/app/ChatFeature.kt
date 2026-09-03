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
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

data class ChatMemberDto(val id:String,val userId:String,val isAdmin:Boolean=false,val lastReadAt:String?=null,val user:UserDto)
data class MessageReactionDto(val id:String?=null,val messageId:String?=null,val userId:String,val emoji:String)
data class MessageReplyDto(val id:String,val text:String?=null,val type:String?=null,val senderId:String?=null)
data class MessageDto(val id:String,val chatId:String,val senderId:String,val type:String="TEXT",val text:String?=null,val mediaUrl:String?=null,val mediaName:String?=null,val mediaMime:String?=null,val mediaSize:Int?=null,val replyToId:String?=null,val isEdited:Boolean=false,val pinnedAt:String?=null,val createdAt:String?=null,val sender:UserDto,val replyTo:MessageReplyDto?=null,val reactions:List<MessageReactionDto> = emptyList())
data class ChatDto(val id:String,val type:String,val title:String?=null,val avatarUrl:String?=null,val members:List<ChatMemberDto> = emptyList(),val messages:List<MessageDto> = emptyList())
data class CreateDirectRequest(val userId:String)
data class SendMessageRequest(val type:String="TEXT",val text:String?=null,val mediaUrl:String?=null,val mediaName:String?=null,val mediaMime:String?=null,val mediaSize:Int?=null,val replyToId:String?=null)
data class EditMessageRequest(val text:String)
data class ReactionRequest(val emoji:String)
data class ReadRequest(val messageId:String?=null)

interface ChatApi {
    @GET("chats") suspend fun chats(@Header("Authorization") auth:String):List<ChatDto>
    @POST("chats/direct") suspend fun direct(@Header("Authorization") auth:String,@Body body:CreateDirectRequest):ChatDto
    @GET("chats/{chatId}/messages") suspend fun messages(@Header("Authorization") auth:String,@Path("chatId") chatId:String,@Query("take") take:Int=80):List<MessageDto>
    @POST("chats/{chatId}/messages") suspend fun send(@Header("Authorization") auth:String,@Path("chatId") chatId:String,@Body body:SendMessageRequest):MessageDto
    @PATCH("chats/{chatId}/messages/{messageId}") suspend fun edit(@Header("Authorization") auth:String,@Path("chatId") chatId:String,@Path("messageId") messageId:String,@Body body:EditMessageRequest):MessageDto
    @DELETE("chats/{chatId}/messages/{messageId}") suspend fun deleteMessage(@Header("Authorization") auth:String,@Path("chatId") chatId:String,@Path("messageId") messageId:String):OkResponse
    @POST("chats/{chatId}/messages/{messageId}/reactions") suspend fun react(@Header("Authorization") auth:String,@Path("chatId") chatId:String,@Path("messageId") messageId:String,@Body body:ReactionRequest):List<MessageReactionDto>
    @POST("chats/{chatId}/messages/{messageId}/pin") suspend fun pin(@Header("Authorization") auth:String,@Path("chatId") chatId:String,@Path("messageId") messageId:String):MessageDto
    @POST("chats/{chatId}/read") suspend fun read(@Header("Authorization") auth:String,@Path("chatId") chatId:String,@Body body:ReadRequest):OkResponse
}

private val chatApi:ChatApi by lazy {
    val client=OkHttpClient.Builder().connectTimeout(75,TimeUnit.SECONDS).readTimeout(75,TimeUnit.SECONDS).build()
    Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(ChatApi::class.java)
}

private fun chatTitle(c:ChatDto,me:UserDto?):String =
    c.members.firstOrNull{it.userId!=me?.id}?.user?.profile?.displayName?.ifBlank{null}
        ?:c.members.firstOrNull{it.userId!=me?.id}?.user?.username
        ?:c.title
        ?:"محادثة"

@Composable
fun ChatsScreen(auth:String,me:UserDto?){
    var mode by remember{ mutableStateOf("match") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("اكتشف والعب",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("التطابق والرومات في مكان واحد")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Button(onClick={mode="match"},modifier=Modifier.weight(1f)){Text("⚡ التطابق")}
            OutlinedButton(onClick={mode="rooms"},modifier=Modifier.weight(1f)){Text("👥 الرومات")}
        }
        if(mode=="match") MatchChallengesPanel() else RoomsPanel()
    }
}

@Composable
fun DirectChatScreen(auth:String,me:UserDto?,friend:UserDto,onBack:()->Unit){
    var chat by remember(friend.id){mutableStateOf<ChatDto?>(null)}
    var status by remember(friend.id){mutableStateOf("جارٍ فتح المحادثة...")}
    LaunchedEffect(friend.id){
        try{chat=chatApi.direct(auth,CreateDirectRequest(friend.id));status=""}
        catch(_:Exception){status="تعذر فتح المحادثة"}
    }
    chat?.let{ChatRoomScreen(auth,me,it,onBack);return}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)){
        OutlinedButton(onClick=onBack){Text("رجوع")}
        Text(friend.profile?.displayName?.ifBlank{friend.username}?:friend.username,style=MaterialTheme.typography.titleLarge)
        Text(status)
    }
}

@Composable
private fun MatchChallengesPanel(){
    Text("⚡ تحديات التطابق",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    Text("ادخل تطابقًا عشوائيًا ثم ابدأ السوالف والتحديات داخل الجلسة.")
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("⚔️ فردي 1 VS 1",fontWeight=FontWeight.Bold)
            Text("لاعب ضد لاعب، جلسة مؤقتة، ثم نضيف التحديات والنتيجة في المرحلة الثالثة.")
            Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth()){Text("البحث قريبًا")}
        }
    }
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("👥 جماعي 5–10",fontWeight=FontWeight.Bold)
            Text("يبدأ عند توفر 5 لاعبين، ويستقبل حتى 10 حسب الموجودين.")
            Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth()){Text("البحث الجماعي قريبًا")}
        }
    }
}

@Composable
private fun RoomsPanel(){
    Text("👥 الرومات",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    Text("رومات اجتماعية للسوالف الجماعية. سنفعّل الإنشاء والانضمام بعد ربطها بالخادم الحقيقي.")
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("رومات عامة",fontWeight=FontWeight.Bold)
            Text("اكتشف رومًا، ادخل، واسولف مع الموجودين بدون ما تكونون أصدقاء مسبقًا.")
        }
    }
}

@Composable
private fun ChatRoomScreen(auth:String,me:UserDto?,chat:ChatDto,onBack:()->Unit){
    var messages by remember(chat.id){mutableStateOf<List<MessageDto>>(emptyList())}
    var text by remember{mutableStateOf("")}
    var reply by remember{mutableStateOf<MessageDto?>(null)}
    var edit by remember{mutableStateOf<MessageDto?>(null)}
    var typing by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()

    suspend fun refresh(){messages=chatApi.messages(auth,chat.id).reversed()}
    LaunchedEffect(chat.id){
        try{
            refresh()
            messages.lastOrNull()?.let{chatApi.read(auth,chat.id,ReadRequest(it.id))}
        }catch(_:Exception){}
    }
    DisposableEffect(chat.id){
        val socket=IO.socket(BuildConfig.REALTIME_URL,IO.Options.builder().setAuth(mapOf("token" to auth.removePrefix("Bearer ").trim())).setReconnection(true).build())
        val listener=Emitter.Listener{scope.launch{try{refresh()}catch(_:Exception){}}}
        val typingListener=Emitter.Listener{args->
            val obj=args.firstOrNull() as? org.json.JSONObject?:return@Listener
            if(obj.optString("chatId")==chat.id&&obj.optString("userId")!=me?.id) typing=obj.optBoolean("isTyping")
        }
        socket.on("connect"){socket.emit("chat:join",org.json.JSONObject().put("chatId",chat.id))}
        listOf("message:new","message:updated","message:deleted","message:reactions","message:pinned").forEach{socket.on(it,listener)}
        socket.on("typing:changed",typingListener)
        socket.connect()
        onDispose{socket.off();socket.disconnect();socket.close()}
    }

    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            OutlinedButton(onClick=onBack){Text("رجوع")}
            Text(chatTitle(chat,me),fontWeight=FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
            messages.forEach{message->
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(9.dp)){
                        Text(if(message.senderId==me?.id)"أنت" else message.sender.profile?.displayName?:message.sender.username,fontWeight=FontWeight.Bold)
                        message.replyTo?.let{Text("↩ ${it.text?:"رسالة"}")}
                        Text(message.text?:"رسالة")
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                            TextButton(onClick={reply=message;edit=null}){Text("رد")}
                            TextButton(onClick={scope.launch{chatApi.react(auth,chat.id,message.id,ReactionRequest("❤️"));refresh()}}){Text("❤️")}
                            TextButton(onClick={scope.launch{chatApi.pin(auth,chat.id,message.id);refresh()}}){Text("تثبيت")}
                            if(message.senderId==me?.id){
                                TextButton(onClick={edit=message;text=message.text.orEmpty()}){Text("تعديل")}
                                TextButton(onClick={scope.launch{chatApi.deleteMessage(auth,chat.id,message.id);refresh()}}){Text("حذف")}
                            }
                        }
                    }
                }
            }
        }
        if(typing) Text("يكتب الآن…")
        reply?.let{Text("رد على: ${it.text?:"رسالة"}")}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
            OutlinedTextField(text,{text=it.take(4000)},Modifier.weight(1f),label={Text("اكتب رسالة")})
            Button(onClick={
                val body=text.trim()
                if(body.isNotBlank()) scope.launch{
                    if(edit!=null) chatApi.edit(auth,chat.id,edit!!.id,EditMessageRequest(body))
                    else chatApi.send(auth,chat.id,SendMessageRequest(text=body,replyToId=reply?.id))
                    text="";edit=null;reply=null;refresh()
                }
            }){Text(if(edit!=null)"حفظ" else "إرسال")}
        }
    }
}
