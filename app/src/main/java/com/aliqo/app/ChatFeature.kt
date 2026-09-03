package com.aliqo.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
data class MatchQueueRequest(val mode:String)
data class MatchStatusDto(val state:String="IDLE",val mode:String?=null,val queueId:String?=null,val sessionId:String?=null,val chat:ChatDto?=null,val players:List<UserDto> = emptyList())
data class RoomCountDto(val members:Int=0)
data class RoomDto(val id:String,val name:String,val description:String?=null,val capacity:Int=50,val creatorId:String,val chatId:String,val creator:UserDto?=null,val _count:RoomCountDto=RoomCountDto())
data class CreateRoomRequest(val name:String,val description:String?=null,val capacity:Int=50)
data class CreateRoomResponse(val room:RoomDto,val chat:ChatDto)

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
    @GET("matchmaking/status") suspend fun matchStatus(@Header("Authorization") auth:String):MatchStatusDto
    @POST("matchmaking/queue") suspend fun matchQueue(@Header("Authorization") auth:String,@Body body:MatchQueueRequest):MatchStatusDto
    @DELETE("matchmaking/queue") suspend fun cancelMatch(@Header("Authorization") auth:String):MatchStatusDto
    @POST("matchmaking/session/{sessionId}/leave") suspend fun leaveMatch(@Header("Authorization") auth:String,@Path("sessionId") sessionId:String):OkResponse
    @GET("rooms") suspend fun rooms(@Header("Authorization") auth:String):List<RoomDto>
    @POST("rooms") suspend fun createRoom(@Header("Authorization") auth:String,@Body body:CreateRoomRequest):CreateRoomResponse
    @POST("rooms/{roomId}/join") suspend fun joinRoom(@Header("Authorization") auth:String,@Path("roomId") roomId:String):ChatDto
    @DELETE("rooms/{roomId}/leave") suspend fun leaveRoom(@Header("Authorization") auth:String,@Path("roomId") roomId:String):OkResponse
    @DELETE("rooms/{roomId}") suspend fun closeRoom(@Header("Authorization") auth:String,@Path("roomId") roomId:String):OkResponse
}

private val chatApi:ChatApi by lazy {
    val client=OkHttpClient.Builder().connectTimeout(75,TimeUnit.SECONDS).readTimeout(75,TimeUnit.SECONDS).writeTimeout(75,TimeUnit.SECONDS).build()
    Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(ChatApi::class.java)
}

private fun chatTitle(c:ChatDto,me:UserDto?):String = c.title?.takeIf{it.isNotBlank()} ?: c.members.firstOrNull{it.userId!=me?.id}?.user?.profile?.displayName?.ifBlank{null} ?: c.members.firstOrNull{it.userId!=me?.id}?.user?.username ?: "محادثة"

@Composable
fun ChatsScreen(auth:String,me:UserDto?){
    var mode by remember{mutableStateOf("match")}
    var selected by remember{mutableStateOf<ChatDto?>(null)}
    selected?.let{ChatRoomScreen(auth,me,it){selected=null};return}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("اكتشف والعب",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("التطابق والرومات في مكان واحد")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(mode=="match") Button(onClick={mode="match"},modifier=Modifier.weight(1f)){Text("⚡ التطابق")} else OutlinedButton(onClick={mode="match"},modifier=Modifier.weight(1f)){Text("⚡ التطابق")}
            if(mode=="rooms") Button(onClick={mode="rooms"},modifier=Modifier.weight(1f)){Text("👥 الرومات")} else OutlinedButton(onClick={mode="rooms"},modifier=Modifier.weight(1f)){Text("👥 الرومات")}
        }
        if(mode=="match") MatchChallengesPanel(auth){selected=it} else RoomsPanel(auth,me){selected=it}
    }
}

@Composable
fun DirectChatScreen(auth:String,me:UserDto?,friend:UserDto,onBack:()->Unit){
    var chat by remember(friend.id){mutableStateOf<ChatDto?>(null)}
    var status by remember(friend.id){mutableStateOf("جارٍ فتح المحادثة...")}
    LaunchedEffect(friend.id){try{chat=chatApi.direct(auth,CreateDirectRequest(friend.id));status=""}catch(_:Exception){status="تعذر فتح المحادثة"}}
    chat?.let{ChatRoomScreen(auth,me,it,onBack);return}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)){
        OutlinedButton(onClick=onBack){Text("رجوع")}
        Text(friend.profile?.displayName?.ifBlank{friend.username}?:friend.username,style=MaterialTheme.typography.titleLarge)
        Text(status)
    }
}

@Composable
private fun MatchChallengesPanel(auth:String,open:(ChatDto)->Unit){
    var match by remember{mutableStateOf(MatchStatusDto())}
    var busy by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    suspend fun refresh(){match=chatApi.matchStatus(auth)}

    LaunchedEffect(auth){
        while(true){
            try{refresh()}catch(_:Exception){if(match.state=="IDLE")status="تعذر تحميل حالة التطابق"}
            delay(if(match.state=="WAITING")5000 else 15000)
        }
    }

    DisposableEffect(auth){
        val socket=IO.socket(BuildConfig.REALTIME_URL,IO.Options.builder().setAuth(mapOf("token" to auth.removePrefix("Bearer ").trim())).setReconnection(true).build())
        val listener=Emitter.Listener{scope.launch{try{refresh()}catch(_:Exception){}}}
        socket.on("match:found",listener)
        socket.on("match:queue",listener)
        socket.on("match:cancelled",listener)
        socket.connect()
        onDispose{socket.off();socket.disconnect();socket.close()}
    }

    Text("⚡ تحديات التطابق",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    when(match.state){
        "WAITING" -> Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("جارٍ البحث عن لاعبين…",fontWeight=FontWeight.Bold)
                Text(if(match.mode=="ONE_V_ONE")"نبحث عن خصم متاح لـ 1 ضد 1" else "ننتظر اكتمال 5 لاعبين على الأقل")
                OutlinedButton(onClick={scope.launch{busy=true;try{match=chatApi.cancelMatch(auth);status=""}catch(_:Exception){status="تعذر إلغاء البحث"};busy=false}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("إلغاء البحث")}
            }
        }
        "MATCHED" -> Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("تم العثور على تطابق 🎉",fontWeight=FontWeight.Bold)
                Text("عدد اللاعبين: ${match.players.size.coerceAtLeast(match.chat?.members?.size?:0)}")
                Button(onClick={match.chat?.let{open(it)}},enabled=match.chat!=null,modifier=Modifier.fillMaxWidth()){Text("دخول الجلسة")}
                match.sessionId?.let{id->OutlinedButton(onClick={scope.launch{try{chatApi.leaveMatch(auth,id);match=MatchStatusDto();status=""}catch(_:Exception){status="تعذر مغادرة الجلسة"}}},modifier=Modifier.fillMaxWidth()){Text("مغادرة التطابق")}}
            }
        }
        else -> {
            Text("اختر نوع التطابق، والنظام يجمعك تلقائيًا بدون شرط الصداقة.")
            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("⚔️ فردي 1 VS 1",fontWeight=FontWeight.Bold);Button(onClick={scope.launch{busy=true;try{match=chatApi.matchQueue(auth,MatchQueueRequest("ONE_V_ONE"));status=""}catch(_:Exception){status="تعذر بدء البحث"};busy=false}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("ابدأ 1 ضد 1")}}}
            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("👥 جماعي 5–10",fontWeight=FontWeight.Bold);Button(onClick={scope.launch{busy=true;try{match=chatApi.matchQueue(auth,MatchQueueRequest("GROUP"));status=""}catch(_:Exception){status="تعذر بدء البحث الجماعي"};busy=false}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("ابدأ جماعي")}}}
        }
    }
    if(status.isNotBlank())Text(status)
}

@Composable
private fun RoomsPanel(auth:String,me:UserDto?,open:(ChatDto)->Unit){
    var rooms by remember{mutableStateOf<List<RoomDto>>(emptyList())}
    var name by remember{mutableStateOf("")}
    var description by remember{mutableStateOf("")}
    var creating by remember{mutableStateOf(false)}
    var showCreate by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    suspend fun refresh(){rooms=chatApi.rooms(auth)}
    LaunchedEffect(auth){try{refresh()}catch(_:Exception){status="تعذر تحميل الرومات"}}

    Text("👥 الرومات",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    Text("رومات عامة للسوالف؛ تدخل بدون شرط الصداقة.")
    OutlinedButton(onClick={showCreate=!showCreate},modifier=Modifier.fillMaxWidth()){Text(if(showCreate)"إغلاق إنشاء روم" else "إنشاء روم جديد")}
    if(showCreate){
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(name,{name=it.take(80)},Modifier.fillMaxWidth(),singleLine=true,label={Text("اسم الروم")})
                OutlinedTextField(description,{description=it.take(280)},Modifier.fillMaxWidth(),label={Text("وصف مختصر - اختياري")})
                Button(onClick={scope.launch{creating=true;try{val r=chatApi.createRoom(auth,CreateRoomRequest(name.trim(),description.trim().ifBlank{null}));name="";description="";showCreate=false;refresh();open(r.chat)}catch(_:Exception){status="تعذر إنشاء الروم"};creating=false}},enabled=!creating&&name.trim().length>=2,modifier=Modifier.fillMaxWidth()){Text("إنشاء ودخول")}
            }
        }
    }
    if(rooms.isEmpty())Text("لا توجد رومات عامة حاليًا. تقدر تكون أول من ينشئ روم.")
    rooms.forEach{room->
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(room.name,fontWeight=FontWeight.Bold)
                room.description?.takeIf{it.isNotBlank()}?.let{Text(it)}
                Text("${room._count.members} / ${room.capacity} أعضاء")
                Button(onClick={scope.launch{try{open(chatApi.joinRoom(auth,room.id));status=""}catch(_:Exception){status="تعذر دخول الروم"}}},modifier=Modifier.fillMaxWidth()){Text("دخول الروم")}
                if(room.creatorId==me?.id)OutlinedButton(onClick={scope.launch{try{chatApi.closeRoom(auth,room.id);refresh();status=""}catch(_:Exception){status="تعذر إغلاق الروم"}}},modifier=Modifier.fillMaxWidth()){Text("إغلاق الروم")}
            }
        }
    }
    if(status.isNotBlank())Text(status)
}

@Composable
private fun ChatRoomScreen(auth:String,me:UserDto?,chat:ChatDto,onBack:()->Unit){
    var messages by remember(chat.id){mutableStateOf<List<MessageDto>>(emptyList())}
    var text by remember{mutableStateOf("")}
    var reply by remember{mutableStateOf<MessageDto?>(null)}
    var edit by remember{mutableStateOf<MessageDto?>(null)}
    var typing by remember{mutableStateOf(false)}
    var socketRef by remember(chat.id){mutableStateOf<io.socket.client.Socket?>(null)}
    val scope=rememberCoroutineScope()
    val context=LocalContext.current

    suspend fun refresh(){messages=chatApi.messages(auth,chat.id).reversed()}
    LaunchedEffect(chat.id){try{refresh();messages.lastOrNull()?.let{chatApi.read(auth,chat.id,ReadRequest(it.id))}}catch(_:Exception){}}

    DisposableEffect(chat.id){
        val socket=IO.socket(BuildConfig.REALTIME_URL,IO.Options.builder().setAuth(mapOf("token" to auth.removePrefix("Bearer ").trim())).setReconnection(true).build())
        socketRef=socket
        val listener=Emitter.Listener{scope.launch{try{refresh()}catch(_:Exception){}}}
        val typingListener=Emitter.Listener{args->
            val obj=args.firstOrNull() as? org.json.JSONObject ?: return@Listener
            if(obj.optString("chatId")==chat.id&&obj.optString("userId")!=me?.id)typing=obj.optBoolean("isTyping")
        }
        socket.on("connect"){socket.emit("chat:join",org.json.JSONObject().put("chatId",chat.id))}
        listOf("message:new","message:updated","message:deleted","message:reactions","message:pinned").forEach{socket.on(it,listener)}
        socket.on("typing:changed",typingListener)
        socket.connect()
        onDispose{
            socket.emit("typing:stop",org.json.JSONObject().put("chatId",chat.id))
            socket.off()
            socket.disconnect()
            socket.close()
            socketRef=null
        }
    }

    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton(onClick=onBack){Text("رجوع")};Text(chatTitle(chat,me),fontWeight=FontWeight.Bold)}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
            messages.forEach{message->
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(9.dp)){
                        Text(if(message.senderId==me?.id)"أنت" else message.sender.profile?.displayName?:message.sender.username,fontWeight=FontWeight.Bold)
                        message.replyTo?.let{Text("↩ ${it.text?:"رسالة"}")}
                        Text(message.text?:when(message.type){"IMAGE"->"صورة";"VIDEO"->"فيديو";"VOICE"->"رسالة صوتية";"FILE"->message.mediaName?:"ملف";else->"رسالة"})
                        if(message.reactions.isNotEmpty())Text(message.reactions.joinToString(" "){it.emoji})
                        Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){
                            TextButton(onClick={reply=message;edit=null}){Text("رد")}
                            message.text?.let{copyText->TextButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ALIQO message",copyText))}){Text("نسخ")}}
                            TextButton(onClick={scope.launch{try{chatApi.react(auth,chat.id,message.id,ReactionRequest("❤️"));refresh()}catch(_:Exception){}}}){Text("❤️")}
                            TextButton(onClick={scope.launch{try{chatApi.pin(auth,chat.id,message.id);refresh()}catch(_:Exception){}}}){Text("تثبيت")}
                            if(message.senderId==me?.id){TextButton(onClick={edit=message;text=message.text.orEmpty()}){Text("تعديل")};TextButton(onClick={scope.launch{try{chatApi.deleteMessage(auth,chat.id,message.id);refresh()}catch(_:Exception){}}}){Text("حذف")}}
                        }
                    }
                }
            }
        }
        if(typing)Text("يكتب الآن…")
        reply?.let{Text("رد على: ${it.text?:"رسالة"}")}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
            OutlinedTextField(text,{newText->text=newText.take(4000);socketRef?.emit(if(text.isBlank())"typing:stop" else "typing:start",org.json.JSONObject().put("chatId",chat.id))},Modifier.weight(1f),label={Text("اكتب رسالة")})
            Button(onClick={val body=text.trim();if(body.isNotBlank())scope.launch{try{if(edit!=null)chatApi.edit(auth,chat.id,edit!!.id,EditMessageRequest(body))else chatApi.send(auth,chat.id,SendMessageRequest(text=body,replyToId=reply?.id));socketRef?.emit("typing:stop",org.json.JSONObject().put("chatId",chat.id));text="";edit=null;reply=null;refresh()}catch(_:Exception){}}}){Text(if(edit!=null)"حفظ" else "إرسال")}
        }
    }
}
