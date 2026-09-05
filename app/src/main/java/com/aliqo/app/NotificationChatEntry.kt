package com.aliqo.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.socket.client.IO
import io.socket.emitter.Emitter
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private val notificationChatClient by lazy { OkHttpClient.Builder().connectTimeout(75,TimeUnit.SECONDS).readTimeout(75,TimeUnit.SECONDS).writeTimeout(75,TimeUnit.SECONDS).build() }
private val notificationChatApi:ChatApi by lazy { Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(notificationChatClient).addConverterFactory(GsonConverterFactory.create()).build().create(ChatApi::class.java) }
private val notificationUserApi:AliqoApi by lazy { Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(notificationChatClient).addConverterFactory(GsonConverterFactory.create()).build().create(AliqoApi::class.java) }
private val NBg=Color(0xFF061126)
private val NCard=Color(0xFF10233F)
private val NLine=Color(0xFF17375F)
private val NMuted=Color(0xFF9AAAC6)
private val NPurple=Color(0xFF7C2CFF)
private val NBlue=Color(0xFF176BFF)
private val NGreen=Color(0xFF22D978)

@Composable
fun NotificationDirectChatEntry(auth:String,chatId:String,onBack:()->Unit){
    var me by remember(chatId){mutableStateOf<UserDto?>(null)}
    var friend by remember(chatId){mutableStateOf<UserDto?>(null)}
    var chat by remember(chatId){mutableStateOf<ChatDto?>(null)}
    var failed by remember(chatId){mutableStateOf(false)}
    LaunchedEffect(chatId){try{val currentMe=notificationUserApi.me(auth);val target=notificationChatApi.chats(auth).firstOrNull{it.id==chatId&&it.type=="DIRECT"};val other=target?.members?.firstOrNull{it.userId!=currentMe.id}?.user;if(target==null||other==null)failed=true else{me=currentMe;friend=other;chat=target}}catch(_:Exception){failed=true}}
    val f=friend;val c=chat
    if(f!=null&&c!=null){NotificationModernChat(auth,me,f,c,onBack)}else Box(Modifier.fillMaxSize().background(NBg),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){if(!failed)CircularProgressIndicator(color=NPurple);Spacer(Modifier.height(12.dp));Text(if(failed)"تعذر فتح المحادثة" else "جارٍ فتح المحادثة...",color=NMuted);if(failed)TextButton(onClick=onBack){Text("رجوع")}}}
}

@OptIn(ExperimentalFoundationApi::class,ExperimentalMaterial3Api::class)
@Composable
private fun NotificationModernChat(auth:String,me:UserDto?,friend:UserDto,chat:ChatDto,onBack:()->Unit){
    val context=LocalContext.current
    val key="messages_${chat.id}"
    var messages by remember(chat.id){mutableStateOf(PersistentUiCache.loadMessages(context,key))}
    var text by remember{mutableStateOf("")}
    var status by remember{mutableStateOf("")}
    var typing by remember{mutableStateOf(false)}
    var online by remember(friend.id){mutableStateOf(friend.profile?.isOnline==true)}
    var reply by remember{mutableStateOf<MessageDto?>(null)}
    var edit by remember{mutableStateOf<MessageDto?>(null)}
    var menuMessage by remember{mutableStateOf<MessageDto?>(null)}
    val scope=rememberCoroutineScope()
    suspend fun refresh(){val fresh=notificationChatApi.messages(auth,chat.id,60,null).reversed();messages=fresh;PersistentUiCache.saveMessages(context,key,fresh)}
    suspend fun markRead(){messages.lastOrNull()?.let{notificationChatApi.read(auth,chat.id,ReadRequest(it.id))}}
    LaunchedEffect(chat.id){try{refresh();markRead()}catch(_:Exception){if(messages.isEmpty())status="تعذر تحميل الرسائل"}}
    DisposableEffect(chat.id){
        val socket=IO.socket(BuildConfig.REALTIME_URL,IO.Options.builder().setAuth(mapOf("token" to auth.removePrefix("Bearer ").trim())).setReconnection(true).build())
        val update=Emitter.Listener{scope.launch{try{refresh();markRead()}catch(_:Exception){}}}
        val type=Emitter.Listener{args->val o=args.firstOrNull() as? org.json.JSONObject?:return@Listener;if(o.optString("chatId")==chat.id&&o.optString("userId")!=me?.id)scope.launch{typing=o.optBoolean("isTyping")}}
        val presence=Emitter.Listener{args->val o=args.firstOrNull() as? org.json.JSONObject?:return@Listener;if(o.optString("userId")==friend.id)scope.launch{online=o.optBoolean("isOnline")}}
        socket.on("connect"){socket.emit("chat:join",org.json.JSONObject().put("chatId",chat.id))}
        listOf("message:new","message:updated","message:deleted","message:reactions","message:pinned").forEach{socket.on(it,update)}
        socket.on("typing:changed",type);socket.on("presence:changed",presence);socket.connect()
        onDispose{socket.emit("chat:leave",org.json.JSONObject().put("chatId",chat.id));socket.off();socket.disconnect();socket.close()}
    }
    Column(Modifier.fillMaxSize().background(NBg)){
        Row(Modifier.fillMaxWidth().background(Color(0xFF07152A)).padding(horizontal=10.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Text("‹",color=Color.White,fontSize=36.sp)}
            Box(Modifier.size(45.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF254B7D),Color(0xFF522D91)))),contentAlignment=Alignment.Center){Text((friend.profile?.displayName?.ifBlank{friend.username}?:friend.username).take(1).uppercase(),color=Color.White,fontWeight=FontWeight.Black,fontSize=18.sp)}
            Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(friend.profile?.displayName?.ifBlank{friend.username}?:friend.username,color=Color.White,fontWeight=FontWeight.Bold);Text(if(typing)"يكتب الآن..." else if(online)"متصل الآن" else "غير متصل",color=if(typing||online)NGreen else NMuted,fontSize=12.sp)}
        }
        HorizontalDivider(color=NLine)
        LazyColumn(Modifier.weight(1f).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(messages,key={it.id}){m->
                val mine=m.senderId==me?.id
                Row(Modifier.fillMaxWidth(),horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start){
                    Column(Modifier.widthIn(max=285.dp).clip(RoundedCornerShape(18.dp)).background(if(mine)Color(0xFF6530D8) else NCard).combinedClickable(onClick={},onLongClick={menuMessage=m}).padding(horizontal=13.dp,vertical=9.dp)){
                        m.replyTo?.let{Text("↩ ${it.text?:"رسالة"}",color=Color(0xFFC8B7FF),fontSize=11.sp)}
                        Text(m.text?:when(m.type){"IMAGE"->"🖼️ صورة";"VIDEO"->"🎬 فيديو";"VOICE"->"🎤 رسالة صوتية";"FILE"->"📎 ${m.mediaName?:"ملف"}";else->"رسالة"},color=Color.White,fontSize=15.sp)
                        if(m.isEdited)Text("معدلة",color=Color.White.copy(alpha=.6f),fontSize=9.sp)
                        if(m.pinnedAt!=null)Text("📌 مثبت",color=Color.White.copy(alpha=.75f),fontSize=10.sp)
                        if(m.reactions.isNotEmpty())Text(m.reactions.joinToString(" "){it.emoji},fontSize=12.sp)
                        Text(m.createdAt?.take(16)?.replace("T"," ")?:"",color=Color.White.copy(alpha=.62f),fontSize=9.sp)
                    }
                }
            }
        }
        if(reply!=null||edit!=null)Row(Modifier.fillMaxWidth().background(Color(0xFF0C1D37)).padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Text(if(edit!=null)"تعديل الرسالة" else "رد على: ${reply?.text?:"رسالة"}",color=NMuted,modifier=Modifier.weight(1f),maxLines=1);TextButton(onClick={reply=null;edit=null;text=""}){Text("×",color=Color.White)}}
        if(status.isNotBlank())Text(status,color=Color(0xFFFF7388),fontSize=12.sp,modifier=Modifier.padding(horizontal=16.dp))
        Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(value=text,onValueChange={text=it.take(4000)},modifier=Modifier.weight(1f),maxLines=4,placeholder={Text("اكتب رسالة...",color=NMuted)},shape=RoundedCornerShape(24.dp),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedContainerColor=Color(0xFF0B1B35),unfocusedContainerColor=Color(0xFF0B1B35),focusedBorderColor=Color(0xFF315584),unfocusedBorderColor=NLine))
            IconButton(onClick={val body=text.trim();if(body.isNotBlank())scope.launch{try{if(edit!=null)notificationChatApi.edit(auth,chat.id,edit!!.id,EditMessageRequest(body))else notificationChatApi.send(auth,chat.id,SendMessageRequest(text=body,replyToId=reply?.id));text="";reply=null;edit=null;refresh();markRead();status=""}catch(_:Exception){status="تعذر إرسال الرسالة"}}},modifier=Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(NPurple,NBlue)))){Text("➤",color=Color.White,fontSize=22.sp)}
        }
    }
    menuMessage?.let{m->
        ModalBottomSheet(onDismissRequest={menuMessage=null},containerColor=NCard){
            Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp)){
                Text("خيارات الرسالة",color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp)
                NotificationChatOption("↩  رد"){reply=m;edit=null;menuMessage=null}
                m.text?.let{copy->NotificationChatOption("▣  نسخ"){(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ALIQO message",copy));menuMessage=null}}
                NotificationChatOption("♥  تفاعل"){scope.launch{try{notificationChatApi.react(auth,chat.id,m.id,ReactionRequest("❤️"));refresh()}catch(_:Exception){}};menuMessage=null}
                NotificationChatOption("⌖  تثبيت"){scope.launch{try{notificationChatApi.pin(auth,chat.id,m.id);refresh()}catch(_:Exception){}};menuMessage=null}
                if(m.senderId==me?.id&&m.type=="TEXT")NotificationChatOption("✎  تعديل"){edit=m;reply=null;text=m.text.orEmpty();menuMessage=null}
                if(m.senderId==me?.id)NotificationChatOption("⌫  حذف",danger=true){scope.launch{try{notificationChatApi.deleteMessage(auth,chat.id,m.id);refresh()}catch(_:Exception){}};menuMessage=null}
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun NotificationChatOption(label:String,danger:Boolean=false,onClick:()->Unit){TextButton(onClick=onClick,modifier=Modifier.fillMaxWidth()){Text(label,color=if(danger)Color(0xFFFF667D)else Color.White,modifier=Modifier.fillMaxWidth(),fontSize=16.sp)}}
