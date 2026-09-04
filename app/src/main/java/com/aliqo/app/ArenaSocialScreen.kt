package com.aliqo.app

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.socket.client.IO
import io.socket.emitter.Emitter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private val SocialBg=Color(0xFF061126)
private val SocialCard=Color(0xFF0B1B35)
private val SocialCard2=Color(0xFF10233F)
private val SocialLine=Color(0xFF17375F)
private val SocialMuted=Color(0xFF9AAAC6)
private val SocialPurple=Color(0xFF7C2CFF)
private val SocialBlue=Color(0xFF176BFF)
private val SocialGreen=Color(0xFF22D978)
private val socialClient by lazy{OkHttpClient.Builder().connectTimeout(75,TimeUnit.SECONDS).readTimeout(75,TimeUnit.SECONDS).writeTimeout(75,TimeUnit.SECONDS).build()}
private val socialApi:AliqoApi by lazy{Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(socialClient).addConverterFactory(GsonConverterFactory.create()).build().create(AliqoApi::class.java)}
private val socialChatApi:ChatApi by lazy{Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(socialClient).addConverterFactory(GsonConverterFactory.create()).build().create(ChatApi::class.java)}

@Composable
fun ArenaFriendsScreen(auth:String,me:UserDto?){
    var opened by remember{mutableStateOf<UserDto?>(null)}
    if(opened!=null){ArenaDirectChatScreen(auth,me,opened!!){opened=null};return}
    var section by remember{mutableStateOf("friends")}
    var query by remember{mutableStateOf("")}
    var friends by remember{mutableStateOf<List<UserDto>>(emptyList())}
    var requests by remember{mutableStateOf<List<FriendRequestDto>>(emptyList())}
    var blocked by remember{mutableStateOf<List<UserDto>>(emptyList())}
    var results by remember{mutableStateOf<List<UserDto>>(emptyList())}
    var status by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    suspend fun refresh(){friends=socialApi.friends(auth);requests=socialApi.friendRequests(auth);blocked=socialApi.blockedUsers(auth)}
    suspend fun search(){val q=query.trim();if(q.length<2){results=emptyList();return};val ids=friends.map{it.id}.toSet();results=socialApi.searchUsers(auth,q).filterNot{it.id in ids||it.id==me?.id}}
    LaunchedEffect(auth){try{refresh()}catch(_:Exception){status="تعذر تحميل الأصدقاء"};while(isActive){delay(30000);try{refresh()}catch(_:Exception){}}}
    DisposableEffect(auth){
        val socket=IO.socket(BuildConfig.REALTIME_URL,IO.Options.builder().setAuth(mapOf("token" to auth.removePrefix("Bearer ").trim())).setReconnection(true).build())
        val listener=Emitter.Listener{scope.launch{try{refresh()}catch(_:Exception){}}}
        socket.on("friends:changed",listener);socket.on("profile:updated",listener);socket.connect()
        onDispose{socket.off();socket.disconnect();socket.close()}
    }
    Column(Modifier.fillMaxSize().background(SocialBg).padding(horizontal=18.dp,vertical=12.dp)){
        Text("ALIQO",color=Color.White,fontSize=29.sp,fontWeight=FontWeight.Black,letterSpacing=1.2.sp)
        Spacer(Modifier.height(10.dp));Text("الأصدقاء",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Black)
        Text("${friends.size} صديق",color=SocialMuted,fontSize=13.sp);Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            SocialTab("أصدقائي (${friends.size})",section=="friends",Modifier.weight(1f)){section="friends"}
            SocialTab("الطلبات (${requests.size})",section=="requests",Modifier.weight(1f)){section="requests"}
            SocialTab("المحظور",section=="blocked",Modifier.weight(1f)){section="blocked"}
        }
        Spacer(Modifier.height(14.dp))
        if(section=="friends"){
            OutlinedTextField(value=query,onValueChange={query=it.lowercase().replace(" ","")},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("ابحث عن صديق...",color=SocialMuted)},leadingIcon={Text("⌕",color=Color(0xFF8B63FF),fontSize=25.sp)},trailingIcon={if(query.length>=2) TextButton(onClick={scope.launch{try{search();status=""}catch(_:Exception){status="تعذر البحث"}}}){Text("بحث",color=Color(0xFFB76BFF))}},shape=RoundedCornerShape(18.dp),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedContainerColor=SocialCard,unfocusedContainerColor=SocialCard,focusedBorderColor=Color(0xFF365E91),unfocusedBorderColor=SocialLine))
            if(results.isNotEmpty()){Spacer(Modifier.height(10.dp));Text("نتائج البحث",color=Color.White,fontWeight=FontWeight.Bold);results.forEach{u->SocialUserRow(u,"إضافة"){scope.launch{try{socialApi.addFriend(auth,u.id);results=results.filterNot{it.id==u.id};refresh()}catch(_:Exception){status="تعذر إرسال الطلب"}}}}}
        }
        if(status.isNotBlank()){Spacer(Modifier.height(8.dp));Text(status,color=Color(0xFFFF7388),fontSize=13.sp)}
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
            when(section){
                "requests"->{if(requests.isEmpty())item{EmptySocial("لا توجد طلبات صداقة معلقة")};items(requests,key={it.id}){r->SocialRequestRow(r.user,onAccept={scope.launch{try{socialApi.acceptFriend(auth,r.id);refresh()}catch(_:Exception){status="تعذر قبول الطلب"}}},onReject={scope.launch{try{socialApi.rejectFriend(auth,r.id);refresh()}catch(_:Exception){status="تعذر رفض الطلب"}}})}}
                "blocked"->{if(blocked.isEmpty())item{EmptySocial("لا يوجد مستخدمون محظورون")};items(blocked,key={it.id}){u->SocialUserRow(u,"إلغاء الحظر"){scope.launch{try{socialApi.unblockUser(auth,u.id);refresh()}catch(_:Exception){status="تعذر إلغاء الحظر"}}}}}
                else->{if(friends.isEmpty())item{EmptySocial("لا يوجد أصدقاء حتى الآن")};items(friends,key={it.id}){u->FriendArenaRow(u,onChat={opened=u},onRemove={scope.launch{try{socialApi.removeFriend(auth,u.id);refresh()}catch(_:Exception){status="تعذر حذف الصديق"}}},onBlock={scope.launch{try{socialApi.blockUser(auth,u.id);refresh()}catch(_:Exception){status="تعذر الحظر"}}})}}
            }
        }
    }
}

@Composable private fun SocialTab(label:String,selected:Boolean,modifier:Modifier,onClick:()->Unit){Box(modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(if(selected)Brush.horizontalGradient(listOf(Color(0xFF5920B9),SocialPurple))else Brush.horizontalGradient(listOf(SocialCard,SocialCard))),contentAlignment=Alignment.Center){TextButton(onClick=onClick,modifier=Modifier.fillMaxSize()){Text(label,color=if(selected)Color.White else SocialMuted,fontSize=12.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium)}}}
@Composable private fun Avatar(user:UserDto,size:Int=48){Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF254B7D),Color(0xFF522D91)))),contentAlignment=Alignment.Center){Text((user.profile?.displayName?.ifBlank{user.username}?:user.username).take(1).uppercase(),color=Color.White,fontWeight=FontWeight.Black,fontSize=(size*.38f).sp)}}
@Composable private fun FriendArenaRow(user:UserDto,onChat:()->Unit,onRemove:()->Unit,onBlock:()->Unit){var menu by remember{mutableStateOf(false)};Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SocialCard).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Avatar(user);Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(user.profile?.displayName?.ifBlank{user.username}?:user.username,color=Color.White,fontWeight=FontWeight.Bold);Text("@${user.username}",color=SocialMuted,fontSize=12.sp);Text(if(user.profile?.isOnline==true)"متصل الآن" else "غير متصل",color=if(user.profile?.isOnline==true)SocialGreen else SocialMuted,fontSize=11.sp)};IconButton(onClick=onChat,modifier=Modifier.size(43.dp).clip(CircleShape).background(Color(0xFF251255))){AliqoArenaIcon(AliqoIcon.CHAT,size=25.dp,active=true)};Box{TextButton(onClick={menu=true}){Text("⋮",color=SocialMuted,fontSize=25.sp)};DropdownMenu(expanded=menu,onDismissRequest={menu=false},containerColor=SocialCard2){DropdownMenuItem(text={Text("حظر المستخدم",color=Color.White)},onClick={menu=false;onBlock()});DropdownMenuItem(text={Text("حذف من الأصدقاء",color=Color(0xFFFF667D))},onClick={menu=false;onRemove()})}}}}
@Composable private fun SocialUserRow(user:UserDto,action:String,onAction:()->Unit){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Avatar(user,43);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(user.profile?.displayName?.ifBlank{user.username}?:user.username,color=Color.White,fontWeight=FontWeight.Bold);Text("@${user.username}",color=SocialMuted,fontSize=12.sp)};OutlinedButton(onClick=onAction,shape=RoundedCornerShape(14.dp)){Text(action,color=Color(0xFFB96CFF))}}}
@Composable private fun SocialRequestRow(user:UserDto,onAccept:()->Unit,onReject:()->Unit){Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SocialCard).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Avatar(user);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(user.profile?.displayName?.ifBlank{user.username}?:user.username,color=Color.White,fontWeight=FontWeight.Bold);Text("@${user.username}",color=SocialMuted,fontSize=12.sp)};Button(onClick=onAccept,colors=ButtonDefaults.buttonColors(containerColor=SocialPurple),shape=RoundedCornerShape(13.dp)){Text("قبول")};Spacer(Modifier.width(5.dp));OutlinedButton(onClick=onReject,shape=RoundedCornerShape(13.dp)){Text("رفض",color=SocialMuted)}}}
@Composable private fun EmptySocial(text:String){Box(Modifier.fillMaxWidth().padding(35.dp),contentAlignment=Alignment.Center){Text(text,color=SocialMuted)}}

@Composable private fun ArenaDirectChatScreen(auth:String,me:UserDto?,friend:UserDto,onBack:()->Unit){
    var chat by remember(friend.id){mutableStateOf<ChatDto?>(null)}
    var status by remember{mutableStateOf("جارٍ فتح المحادثة...")}
    LaunchedEffect(friend.id){try{chat=socialChatApi.direct(auth,CreateDirectRequest(friend.id));status=""}catch(_:Exception){status="تعذر فتح المحادثة"}}
    val current=chat
    if(current!=null){ArenaChatRoom(auth,me,friend,current,onBack)}else Box(Modifier.fillMaxSize().background(SocialBg),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator(color=SocialPurple);Spacer(Modifier.height(12.dp));Text(status,color=SocialMuted);TextButton(onClick=onBack){Text("رجوع")}}}
}

@Composable private fun ArenaChatRoom(auth:String,me:UserDto?,friend:UserDto,chat:ChatDto,onBack:()->Unit){
    var messages by remember(chat.id){mutableStateOf<List<MessageDto>>(emptyList())}
    var text by remember{mutableStateOf("")}
    var status by remember{mutableStateOf("")}
    var typing by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    suspend fun refresh(){messages=socialChatApi.messages(auth,chat.id,60,null).reversed()}
    suspend fun markRead(){messages.lastOrNull()?.let{socialChatApi.read(auth,chat.id,ReadRequest(it.id))}}
    LaunchedEffect(chat.id){try{refresh();markRead()}catch(_:Exception){status="تعذر تحميل الرسائل"}}
    DisposableEffect(chat.id){
        val socket=IO.socket(BuildConfig.REALTIME_URL,IO.Options.builder().setAuth(mapOf("token" to auth.removePrefix("Bearer ").trim())).setReconnection(true).build())
        val update=Emitter.Listener{scope.launch{try{refresh();markRead()}catch(_:Exception){}}}
        val type=Emitter.Listener{args->val o=args.firstOrNull() as? org.json.JSONObject?:return@Listener;if(o.optString("chatId")==chat.id&&o.optString("userId")!=me?.id)typing=o.optBoolean("isTyping")}
        socket.on("connect"){socket.emit("chat:join",org.json.JSONObject().put("chatId",chat.id))}
        listOf("message:new","message:updated","message:deleted","message:reactions","message:pinned").forEach{socket.on(it,update)}
        socket.on("typing:changed",type);socket.connect()
        onDispose{socket.emit("chat:leave",org.json.JSONObject().put("chatId",chat.id));socket.off();socket.disconnect();socket.close()}
    }
    Column(Modifier.fillMaxSize().background(SocialBg)){
        Row(Modifier.fillMaxWidth().background(Color(0xFF07152A)).padding(horizontal=10.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Text("‹",color=Color.White,fontSize=36.sp)};Avatar(friend,45);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(friend.profile?.displayName?.ifBlank{friend.username}?:friend.username,color=Color.White,fontWeight=FontWeight.Bold);Text(if(typing)"يكتب الآن..." else if(friend.profile?.isOnline==true)"متصل الآن" else "غير متصل",color=if(typing||friend.profile?.isOnline==true)SocialGreen else SocialMuted,fontSize=12.sp)}}
        HorizontalDivider(color=SocialLine)
        LazyColumn(Modifier.weight(1f).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(messages,key={it.id}){m->val mine=m.senderId==me?.id;Row(Modifier.fillMaxWidth(),horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start){Column(Modifier.widthIn(max=285.dp).clip(RoundedCornerShape(18.dp)).background(if(mine)Color(0xFF6530D8) else SocialCard2).padding(horizontal=13.dp,vertical=9.dp)){Text(m.text?:when(m.type){"IMAGE"->"🖼️ صورة";"VIDEO"->"🎬 فيديو";"VOICE"->"🎤 رسالة صوتية";"FILE"->"📎 ${m.mediaName?:"ملف"}";else->"رسالة"},color=Color.White,fontSize=15.sp);Text(m.createdAt?.take(16)?.replace("T"," ")?:"",color=Color.White.copy(alpha=.62f),fontSize=9.sp)}}}
        }
        if(status.isNotBlank())Text(status,color=Color(0xFFFF7388),fontSize=12.sp,modifier=Modifier.padding(horizontal=16.dp))
        Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(value=text,onValueChange={text=it.take(4000)},modifier=Modifier.weight(1f),maxLines=4,placeholder={Text("اكتب رسالة...",color=SocialMuted)},shape=RoundedCornerShape(24.dp),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedContainerColor=SocialCard,unfocusedContainerColor=SocialCard,focusedBorderColor=Color(0xFF315584),unfocusedBorderColor=SocialLine));IconButton(onClick={val body=text.trim();if(body.isNotBlank())scope.launch{try{socialChatApi.send(auth,chat.id,SendMessageRequest(text=body));text="";refresh();markRead();status=""}catch(_:Exception){status="تعذر إرسال الرسالة"}}},modifier=Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(SocialPurple,SocialBlue)))){Text("➤",color=Color.White,fontSize=22.sp)}}
    }
}
