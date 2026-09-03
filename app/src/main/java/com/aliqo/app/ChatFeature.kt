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
 @GET("friends") suspend fun friends(@Header("Authorization") auth:String):List<UserDto>
}
private val chatApi:ChatApi by lazy { val c=OkHttpClient.Builder().connectTimeout(75,TimeUnit.SECONDS).readTimeout(75,TimeUnit.SECONDS).build(); Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(c).addConverterFactory(GsonConverterFactory.create()).build().create(ChatApi::class.java) }
private fun chatTitle(c:ChatDto,me:UserDto?)=c.members.firstOrNull{it.userId!=me?.id}?.user?.profile?.displayName?.ifBlank{null}?:c.members.firstOrNull{it.userId!=me?.id}?.user?.username?:c.title?:"محادثة"

@Composable fun ChatsScreen(auth:String,me:UserDto?){
 var mode by remember{ mutableStateOf("private") }; var selected by remember{ mutableStateOf<ChatDto?>(null) }
 selected?.let{ ChatRoomScreen(auth,me,it){selected=null}; return }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("المحادثات",style=MaterialTheme.typography.headlineSmall)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
   Button({mode="private"},Modifier.weight(1f)){Text("💬 خاص")}; Button({mode="match"},Modifier.weight(1f)){Text("⚡ التطابق")}; Button({mode="rooms"},Modifier.weight(1f)){Text("👥 رومات")}
  }
  when(mode){"match"->MatchChallengesPanel();"rooms"->RoomsPanel();else->PrivateChatsPanel(auth,me){selected=it}}
 }
}

@Composable private fun PrivateChatsPanel(auth:String,me:UserDto?,open:(ChatDto)->Unit){
 var chats by remember{ mutableStateOf<List<ChatDto>>(emptyList()) }; var friends by remember{ mutableStateOf<List<UserDto>>(emptyList()) }; var status by remember{ mutableStateOf("") }; val scope=rememberCoroutineScope()
 suspend fun refresh(){chats=chatApi.chats(auth).filter{it.type=="DIRECT"};friends=chatApi.friends(auth)}
 LaunchedEffect(auth){try{refresh()}catch(e:Exception){status="تعذر تحميل الخاص"}}
 DisposableEffect(auth){val s=IO.socket(BuildConfig.REALTIME_URL,IO.Options.builder().setAuth(mapOf("token" to auth.removePrefix("Bearer ").trim())).setReconnection(true).build());val l=Emitter.Listener{scope.launch{try{refresh()}catch(_:Exception){}}};s.on("chats:changed",l);s.connect();onDispose{s.off();s.disconnect();s.close()}}
 Text("محادثاتك الخاصة",fontWeight=FontWeight.Bold)
 if(chats.isEmpty()) Text("ابدأ محادثة من قائمة أصدقائك")
 chats.forEach{c->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(chatTitle(c,me),fontWeight=FontWeight.Bold);c.messages.firstOrNull()?.text?.let{Text(it)};Button({open(c)},Modifier.fillMaxWidth()){Text("فتح")}}}}
 HorizontalDivider();Text("أصدقاؤك",fontWeight=FontWeight.Bold)
 friends.forEach{u->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(u.profile?.displayName?:"@${u.username}");Button({scope.launch{try{open(chatApi.direct(auth,CreateDirectRequest(u.id)))}catch(_:Exception){status="تعذر فتح المحادثة"}}}){Text("مراسلة")}}}
 if(status.isNotBlank())Text(status)
}

@Composable private fun MatchChallengesPanel(){
 Text("⚡ تحديات التطابق",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
 Text("ابحث عشوائيًا عن لاعبين متاحين، ثم ابدأ السوالف والتحديات داخل جلسة التطابق.")
 Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("⚔️ فردي 1 VS 1",fontWeight=FontWeight.Bold);Text("تطابق عشوائي مع لاعب واحد. سيتم تفعيل البحث والجلسة مع محرك التحديات.")}}
 Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("👥 جماعي 5–10",fontWeight=FontWeight.Bold);Text("يجمع النظام 5 لاعبين على الأقل، ويسمح بانضمام حتى 10 حسب المتاح.")}}
 Text("هذه الخيارات معروضة للتعريف فقط الآن؛ لن نضع زر بحث وهمي قبل اكتمال نظام الطوابير والجلسات.")
}
@Composable private fun RoomsPanel(){Text("👥 الرومات",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("غرف للسوالف الجماعية. سنربط الإنشاء والانضمام وإدارة الرومات بالخادم قبل تفعيل أزرارها.")}

@Composable private fun ChatRoomScreen(auth:String,me:UserDto?,chat:ChatDto,onBack:()->Unit){
 var messages by remember(chat.id){mutableStateOf<List<MessageDto>>(emptyList())};var text by remember{mutableStateOf("")};var reply by remember{mutableStateOf<MessageDto?>(null)};var edit by remember{mutableStateOf<MessageDto?>(null)};var typing by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 suspend fun refresh(){messages=chatApi.messages(auth,chat.id).reversed()}
 LaunchedEffect(chat.id){try{refresh();messages.lastOrNull()?.let{chatApi.read(auth,chat.id,ReadRequest(it.id))}}catch(_:Exception){}}
 DisposableEffect(chat.id){val s=IO.socket(BuildConfig.REALTIME_URL,IO.Options.builder().setAuth(mapOf("token" to auth.removePrefix("Bearer ").trim())).setReconnection(true).build());val l=Emitter.Listener{scope.launch{try{refresh()}catch(_:Exception){}}};val t=Emitter.Listener{a->val o=a.firstOrNull() as? org.json.JSONObject?:return@Listener;if(o.optString("chatId")==chat.id&&o.optString("userId")!=me?.id)typing=o.optBoolean("isTyping")};s.on("connect"){s.emit("chat:join",org.json.JSONObject().put("chatId",chat.id))};listOf("message:new","message:updated","message:deleted","message:reactions","message:pinned").forEach{s.on(it,l)};s.on("typing:changed",t);s.connect();onDispose{s.off();s.disconnect();s.close()}}
 Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton(onBack){Text("رجوع")};Text(chatTitle(chat,me),fontWeight=FontWeight.Bold)};Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){messages.forEach{m->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(9.dp)){Text(if(m.senderId==me?.id)"أنت" else m.sender.profile?.displayName?:m.sender.username,fontWeight=FontWeight.Bold);m.replyTo?.let{Text("↩ ${it.text?:"رسالة"}")};Text(m.text?:"رسالة");Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){TextButton({reply=m;edit=null}){Text("رد")};TextButton({scope.launch{chatApi.react(auth,chat.id,m.id,ReactionRequest("❤️"));refresh()}}){Text("❤️")};TextButton({scope.launch{chatApi.pin(auth,chat.id,m.id);refresh()}}){Text("تثبيت")};if(m.senderId==me?.id){TextButton({edit=m;text=m.text.orEmpty()}){Text("تعديل")};TextButton({scope.launch{chatApi.deleteMessage(auth,chat.id,m.id);refresh()}}){Text("حذف")}}}}}};if(typing)Text("يكتب الآن…");reply?.let{Text("رد على: ${it.text?:"رسالة"}")};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){OutlinedTextField(text,{text=it.take(4000)},Modifier.weight(1f),label={Text("اكتب رسالة")});Button({val b=text.trim();if(b.isNotBlank())scope.launch{if(edit!=null)chatApi.edit(auth,chat.id,edit!!.id,EditMessageRequest(b))else chatApi.send(auth,chat.id,SendMessageRequest(text=b,replyToId=reply?.id));text="";edit=null;reply=null;refresh()}}){Text(if(edit!=null)"حفظ" else "إرسال")}}}
}
