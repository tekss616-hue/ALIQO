package com.aliqo.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import io.socket.client.Socket
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

private object ArenaFriendsCache{
    var friends:List<UserDto>?=null
    var requests:List<FriendRequestDto>?=null
    var blocked:List<UserDto>?=null
}

@Composable
fun ArenaFriendsScreen(auth:String,me:UserDto?){
    val context=LocalContext.current
    var opened by remember{mutableStateOf<UserDto?>(null)}
    if(opened!=null){ArenaDirectChatScreen(auth,me,opened!!){opened=null};return}
    var section by remember{mutableStateOf("friends")}
    var query by remember{mutableStateOf("")}
    val storedFriends=remember{PersistentUiCache.loadUsers(context,"friends")}
    val storedRequests=remember{PersistentUiCache.loadFriendRequests(context,"friend_requests")}
    val storedBlocked=remember{PersistentUiCache.loadUsers(context,"blocked_users")}
    val hadStored=remember{PersistentUiCache.has(context,"friends")&&PersistentUiCache.has(context,"friend_requests")&&PersistentUiCache.has(context,"blocked_users")}
    var friends by remember{mutableStateOf(ArenaFriendsCache.friends?:storedFriends)}
    var requests by remember{mutableStateOf(ArenaFriendsCache.requests?:storedRequests)}
    var blocked by remember{mutableStateOf(ArenaFriendsCache.blocked?:storedBlocked)}
    var resolved by remember{mutableStateOf(ArenaFriendsCache.friends!=null||hadStored)}
    var results by remember{mutableStateOf<List<UserDto>>(emptyList())}
    var status by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()

    fun applyPresence(userId:String,isOnline:Boolean){
        val updated=friends.map{u->if(u.id==userId)u.copy(profile=(u.profile?:ProfileDto()).copy(isOnline=isOnline)) else u}
        if(updated!=friends){friends=updated;ArenaFriendsCache.friends=updated;PersistentUiCache.saveUsers(context,"friends",updated)}
    }
    suspend fun refresh(){
        val newFriends=socialApi.friends(auth)
        val newRequests=socialApi.friendRequests(auth)
        val newBlocked=socialApi.blockedUsers(auth)
        friends=newFriends;requests=newRequests;blocked=newBlocked;resolved=true
        ArenaFriendsCache.friends=newFriends;ArenaFriendsCache.requests=newRequests;ArenaFriendsCache.blocked=newBlocked
        PersistentUiCache.saveUsers(context,"friends",newFriends)
        PersistentUiCache.saveFriendRequests(context,"friend_requests",newRequests)
        PersistentUiCache.saveUsers(context,"blocked_users",newBlocked)
    }
    suspend fun search(){val q=query.trim();if(q.length<2){results=emptyList();return};val ids=friends.map{it.id}.toSet();results=socialApi.searchUsers(auth,q).filterNot{it.id in ids||it.id==me?.id}}

    LaunchedEffect(auth){
        try{refresh();status=""}catch(_:Exception){resolved=true;if(friends.isEmpty()&&!hadStored)status="تعذر تحميل الأصدقاء"}
        while(isActive){delay(5000);try{refresh()}catch(_:Exception){}}
    }
    DisposableEffect(auth){
        val socket=IO.socket(BuildConfig.REALTIME_URL,IO.Options.builder().setAuth(mapOf("token" to auth.removePrefix("Bearer ").trim())).setReconnection(true).build())
        val listener=Emitter.Listener{scope.launch{try{refresh()}catch(_:Exception){}}}
        val connected=Emitter.Listener{scope.launch{try{refresh()}catch(_:Exception){}}}
        val presence=Emitter.Listener{args->val payload=args.firstOrNull() as? org.json.JSONObject?:return@Listener;val userId=payload.optString("userId");if(userId.isNotBlank())scope.launch{applyPresence(userId,payload.optBoolean("isOnline"))}}
        socket.on("connect",connected);socket.on("friends:changed",listener);socket.on("profile:updated",listener);socket.on("presence:changed",presence);socket.connect()
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
            if(results.isNotEmpty()){Spacer(Modifier.height(10.dp));Text("نتائج البحث",color=Color.White,fontWeight=FontWeight.Bold);results.forEach{u->SocialUserRow(u,"إضافة",onProfile={PlayerProfileNavigation.open(u.id)}){scope.launch{try{socialApi.addFriend(auth,u.id);results=results.filterNot{it.id==u.id};refresh()}catch(_:Exception){status="تعذر إرسال الطلب"}}}}}
        }
        if(status.isNotBlank()){Spacer(Modifier.height(8.dp));Text(status,color=Color(0xFFFF7388),fontSize=13.sp)}
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
            when(section){
                "requests"->{if(resolved&&requests.isEmpty())item{EmptySocial("لا توجد طلبات صداقة معلقة")};items(requests,key={it.id}){r->SocialRequestRow(r.user,onProfile={PlayerProfileNavigation.open(r.user.id)},onAccept={scope.launch{try{socialApi.acceptFriend(auth,r.id);refresh()}catch(_:Exception){status="تعذر قبول الطلب"}}},onReject={scope.launch{try{socialApi.rejectFriend(auth,r.id);refresh()}catch(_:Exception){status="تعذر رفض الطلب"}}})}}
                "blocked"->{if(resolved&&blocked.isEmpty())item{EmptySocial("لا يوجد مستخدمون محظورون")};items(blocked,key={it.id}){u->SocialUserRow(u,"إلغاء الحظر",onProfile={PlayerProfileNavigation.open(u.id)}){scope.launch{try{socialApi.unblockUser(auth,u.id);refresh()}catch(_:Exception){status="تعذر إلغاء الحظر"}}}}}
                else->{if(resolved&&friends.isEmpty())item{EmptySocial("لا يوجد أصدقاء حتى الآن")};items(friends,key={it.id}){u->FriendArenaRow(u,onProfile={PlayerProfileNavigation.open(u.id)},onChat={opened=u},onRemove={scope.launch{try{socialApi.removeFriend(auth,u.id);refresh()}catch(_:Exception){status="تعذر حذف الصديق"}}},onBlock={scope.launch{try{socialApi.blockUser(auth,u.id);refresh()}catch(_:Exception){status="تعذر الحظر"}}})}}
            }
        }
    }
}

@Composable private fun SocialTab(label:String,selected:Boolean,modifier:Modifier,onClick:()->Unit){Box(modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(if(selected)Brush.horizontalGradient(listOf(Color(0xFF5920B9),SocialPurple))else Brush.horizontalGradient(listOf(SocialCard,SocialCard))),contentAlignment=Alignment.Center){TextButton(onClick=onClick,modifier=Modifier.fillMaxSize()){Text(label,color=if(selected)Color.White else SocialMuted,fontSize=12.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium)}}}
@Composable private fun Avatar(user:UserDto,size:Int=48){Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF254B7D),Color(0xFF522D91)))),contentAlignment=Alignment.Center){Text((user.profile?.displayName?.ifBlank{user.username}?:user.username).take(1).uppercase(),color=Color.White,fontWeight=FontWeight.Black,fontSize=(size*.38f).sp)}}
@Composable private fun FriendArenaRow(user:UserDto,onProfile:()->Unit,onChat:()->Unit,onRemove:()->Unit,onBlock:()->Unit){var menu by remember{mutableStateOf(false)};Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SocialCard).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Row(Modifier.weight(1f).clickable(onClick=onProfile),verticalAlignment=Alignment.CenterVertically){Avatar(user);Spacer(Modifier.width(11.dp));Column{Text(user.profile?.displayName?.ifBlank{user.username}?:user.username,color=Color.White,fontWeight=FontWeight.Bold);Text("@${user.username}",color=SocialMuted,fontSize=12.sp);Text(if(user.profile?.isOnline==true)"متصل الآن" else "غير متصل",color=if(user.profile?.isOnline==true)SocialGreen else SocialMuted,fontSize=11.sp)}};IconButton(onClick=onChat,modifier=Modifier.size(43.dp).clip(CircleShape).background(Color(0xFF251255))){AliqoArenaIcon(AliqoIcon.CHAT,size=25.dp,active=true)};Box{TextButton(onClick={menu=true}){Text("⋮",color=SocialMuted,fontSize=25.sp)};DropdownMenu(expanded=menu,onDismissRequest={menu=false},containerColor=SocialCard2){DropdownMenuItem(text={Text("حظر المستخدم",color=Color.White)},onClick={menu=false;onBlock()});DropdownMenuItem(text={Text("حذف من الأصدقاء",color=Color(0xFFFF667D))},onClick={menu=false;onRemove()})}}}}
@Composable private fun SocialUserRow(user:UserDto,action:String,onProfile:()->Unit,onAction:()->Unit){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Row(Modifier.weight(1f).clickable(onClick=onProfile),verticalAlignment=Alignment.CenterVertically){Avatar(user,43);Spacer(Modifier.width(10.dp));Column{Text(user.profile?.displayName?.ifBlank{user.username}?:user.username,color=Color.White,fontWeight=FontWeight.Bold);Text("@${user.username}",color=SocialMuted,fontSize=12.sp)}};OutlinedButton(onClick=onAction,shape=RoundedCornerShape(14.dp)){Text(action,color=Color(0xFFB96CFF))}}}
@Composable private fun SocialRequestRow(user:UserDto,onProfile:()->Unit,onAccept:()->Unit,onReject:()->Unit){Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SocialCard).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Row(Modifier.weight(1f).clickable(onClick=onProfile),verticalAlignment=Alignment.CenterVertically){Avatar(user);Spacer(Modifier.width(10.dp));Column{Text(user.profile?.displayName?.ifBlank{user.username}?:user.username,color=Color.White,fontWeight=FontWeight.Bold);Text("@${user.username}",color=SocialMuted,fontSize=12.sp)}};Button(onClick=onAccept,colors=ButtonDefaults.buttonColors(containerColor=SocialPurple),shape=RoundedCornerShape(13.dp)){Text("قبول")};Spacer(Modifier.width(5.dp));OutlinedButton(onClick=onReject,shape=RoundedCornerShape(13.dp)){Text("رفض",color=SocialMuted)}}}
@Composable private fun EmptySocial(text:String){Box(Modifier.fillMaxWidth().padding(35.dp),contentAlignment=Alignment.Center){Text(text,color=SocialMuted)}}

@Composable private fun ArenaDirectChatScreen(auth:String,me:UserDto?,friend:UserDto,onBack:()->Unit){
    val context=LocalContext.current
    val cacheKey="direct_chat_${friend.id}"
    val cached=remember(friend.id){PersistentUiCache.loadChat(context,cacheKey)}
    var chat by remember(friend.id){mutableStateOf(cached)}
    var status by remember(friend.id){mutableStateOf(if(cached==null)"جارٍ فتح المحادثة..." else "")}
    LaunchedEffect(friend.id){try{val fresh=socialChatApi.direct(auth,CreateDirectRequest(friend.id));chat=fresh;PersistentUiCache.saveChat(context,cacheKey,fresh);status=""}catch(_:Exception){if(chat==null)status="تعذر فتح المحادثة"}}
    val current=chat
    if(current!=null){ArenaChatRoom(auth,me,friend,current,onBack)}else Box(Modifier.fillMaxSize().background(SocialBg),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator(color=SocialPurple);Spacer(Modifier.height(12.dp));Text(status,color=SocialMuted);TextButton(onClick=onBack){Text("رجوع")}}}
}

@OptIn(ExperimentalFoundationApi::class,ExperimentalMaterial3Api::class)
@Composable private fun ArenaChatRoom(auth:String,me:UserDto?,friend:UserDto,chat:ChatDto,onBack:()->Unit){
    val context=LocalContext.current
    val messageKey="messages_${chat.id}"
    val cachedMessages=remember(chat.id){PersistentUiCache.loadMessages(context,messageKey)}
    var messages by remember(chat.id){mutableStateOf(cachedMessages)}
    var text by remember{mutableStateOf("")}
    var status by remember{mutableStateOf("")}
    var typing by remember{mutableStateOf(false)}
    var friendOnline by remember(friend.id){mutableStateOf(friend.profile?.isOnline==true)}
    var peerLastReadMsgId by remember(chat.id){mutableStateOf(chat.members.firstOrNull{it.userId==friend.id}?.lastReadMsgId)}
    var chatSocket by remember(chat.id){mutableStateOf<Socket?>(null)}
    var reply by remember{mutableStateOf<MessageDto?>(null)}
    var edit by remember{mutableStateOf<MessageDto?>(null)}
    var menuMessage by remember{mutableStateOf<MessageDto?>(null)}
    val scope=rememberCoroutineScope()
    suspend fun refresh(){val fresh=socialChatApi.messages(auth,chat.id,60,null).reversed();messages=fresh;PersistentUiCache.saveMessages(context,messageKey,fresh)}
    suspend fun markRead(){messages.lastOrNull()?.let{socialChatApi.read(auth,chat.id,ReadRequest(it.id))}}
    LaunchedEffect(chat.id){try{refresh();markRead()}catch(_:Exception){if(messages.isEmpty())status="تعذر تحميل الرسائل"}}
    LaunchedEffect(text,chat.id){val payload=org.json.JSONObject().put("chatId",chat.id);if(text.isNotBlank()){chatSocket?.emit("typing:start",payload);delay(1200);chatSocket?.emit("typing:stop",payload)}else chatSocket?.emit("typing:stop",payload)}
    DisposableEffect(chat.id){
        val socket=IO.socket(BuildConfig.REALTIME_URL,IO.Options.builder().setAuth(mapOf("token" to auth.removePrefix("Bearer ").trim())).setReconnection(true).build());chatSocket=socket
        val update=Emitter.Listener{scope.launch{try{refresh();markRead()}catch(_:Exception){}}}
        val type=Emitter.Listener{args->val o=args.firstOrNull() as? org.json.JSONObject?:return@Listener;if(o.optString("chatId")==chat.id&&o.optString("userId")!=me?.id)scope.launch{typing=o.optBoolean("isTyping")}}
        val presence=Emitter.Listener{args->val o=args.firstOrNull() as? org.json.JSONObject?:return@Listener;if(o.optString("userId")==friend.id)scope.launch{friendOnline=o.optBoolean("isOnline")}}
        val read=Emitter.Listener{args->val o=args.firstOrNull() as? org.json.JSONObject?:return@Listener;if(o.optString("chatId")==chat.id&&o.optString("userId")==friend.id)scope.launch{peerLastReadMsgId=o.optString("messageId").takeIf{it.isNotBlank()}}}
        socket.on("connect"){socket.emit("chat:join",org.json.JSONObject().put("chatId",chat.id))}
        listOf("message:new","message:updated","message:deleted","message:reactions","message:pinned").forEach{socket.on(it,update)}
        socket.on("typing:changed",type);socket.on("presence:changed",presence);socket.on("chat:read",read);socket.connect()
        onDispose{socket.emit("typing:stop",org.json.JSONObject().put("chatId",chat.id));socket.emit("chat:leave",org.json.JSONObject().put("chatId",chat.id));chatSocket=null;socket.off();socket.disconnect();socket.close()}
    }
    val readIndex=messages.indexOfFirst{it.id==peerLastReadMsgId}
    Column(Modifier.fillMaxSize().background(SocialBg)){
        Row(Modifier.fillMaxWidth().background(Color(0xFF07152A)).padding(horizontal=10.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack){Text("‹",color=Color.White,fontSize=36.sp)}
            Row(Modifier.weight(1f).clickable{PlayerProfileNavigation.open(friend.id)},verticalAlignment=Alignment.CenterVertically){Avatar(friend,45);Spacer(Modifier.width(10.dp));Column{Text(friend.profile?.displayName?.ifBlank{friend.username}?:friend.username,color=Color.White,fontWeight=FontWeight.Bold);Text(if(typing)"جارٍ الكتابة…" else if(friendOnline)"متصل الآن" else "غير متصل",color=if(typing||friendOnline)SocialGreen else SocialMuted,fontSize=12.sp)}}
        }
        HorizontalDivider(color=SocialLine)
        LazyColumn(Modifier.weight(1f).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            itemsIndexed(messages,key={_,m->m.id}){index,m->val mine=m.senderId==me?.id;Row(Modifier.fillMaxWidth(),horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start){Column(Modifier.widthIn(max=285.dp).clip(RoundedCornerShape(18.dp)).background(if(mine)Color(0xFF6530D8) else SocialCard2).combinedClickable(onClick={},onLongClick={menuMessage=m}).padding(horizontal=13.dp,vertical=9.dp)){m.replyTo?.let{Text("↩ ${it.text?:"رسالة"}",color=Color(0xFFC8B7FF),fontSize=11.sp)};Text(m.text?:when(m.type){"IMAGE"->"🖼️ صورة";"VIDEO"->"🎬 فيديو";"VOICE"->"🎤 رسالة صوتية";"FILE"->"📎 ${m.mediaName?:"ملف"}";else->"رسالة"},color=Color.White,fontSize=15.sp);if(m.isEdited)Text("معدلة",color=Color.White.copy(alpha=.6f),fontSize=9.sp);if(m.pinnedAt!=null)Text("📌 مثبت",color=Color.White.copy(alpha=.75f),fontSize=10.sp);if(m.reactions.isNotEmpty())Text(m.reactions.joinToString(" "){it.emoji},fontSize=12.sp);Row(verticalAlignment=Alignment.CenterVertically){Text(m.createdAt?.take(16)?.replace("T"," ")?:"",color=Color.White.copy(alpha=.62f),fontSize=9.sp);if(mine){Spacer(Modifier.width(5.dp));Text(if(readIndex>=0&&index<=readIndex)"✓✓" else "✓",color=if(readIndex>=0&&index<=readIndex)Color(0xFF69D7FF) else Color.White.copy(alpha=.7f),fontSize=11.sp,fontWeight=FontWeight.Bold)}}}}}
        }
        if(reply!=null||edit!=null)Row(Modifier.fillMaxWidth().background(Color(0xFF0C1D37)).padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Text(if(edit!=null)"تعديل الرسالة" else "رد على: ${reply?.text?:"رسالة"}",color=SocialMuted,modifier=Modifier.weight(1f),maxLines=1);TextButton(onClick={reply=null;edit=null;text=""}){Text("×",color=Color.White)}}
        if(status.isNotBlank())Text(status,color=Color(0xFFFF7388),fontSize=12.sp,modifier=Modifier.padding(horizontal=16.dp))
        Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(value=text,onValueChange={text=it.take(4000)},modifier=Modifier.weight(1f),maxLines=4,placeholder={Text("اكتب رسالة...",color=SocialMuted)},shape=RoundedCornerShape(24.dp),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedContainerColor=SocialCard,unfocusedContainerColor=SocialCard,focusedBorderColor=Color(0xFF315584),unfocusedBorderColor=SocialLine))
            IconButton(onClick={val body=text.trim();if(body.isNotBlank())scope.launch{try{chatSocket?.emit("typing:stop",org.json.JSONObject().put("chatId",chat.id));if(edit!=null)socialChatApi.edit(auth,chat.id,edit!!.id,EditMessageRequest(body))else socialChatApi.send(auth,chat.id,SendMessageRequest(text=body,replyToId=reply?.id));text="";reply=null;edit=null;refresh();markRead();status=""}catch(_:Exception){status="تعذر إرسال الرسالة"}}},modifier=Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(SocialPurple,SocialBlue)))){Text("➤",color=Color.White,fontSize=22.sp)}
        }
    }
    menuMessage?.let{m->ModalBottomSheet(onDismissRequest={menuMessage=null},containerColor=SocialCard2){Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp)){Text("خيارات الرسالة",color=Color.White,fontWeight=FontWeight.Bold,fontSize=18.sp);ChatOption("↩  رد"){reply=m;edit=null;menuMessage=null};m.text?.let{copy->ChatOption("▣  نسخ"){(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ALIQO message",copy));menuMessage=null}};ChatOption("♥  تفاعل"){scope.launch{try{socialChatApi.react(auth,chat.id,m.id,ReactionRequest("❤️"));refresh()}catch(_:Exception){}};menuMessage=null};ChatOption("⌖  تثبيت"){scope.launch{try{socialChatApi.pin(auth,chat.id,m.id);refresh()}catch(_:Exception){}};menuMessage=null};if(m.senderId==me?.id&&m.type=="TEXT")ChatOption("✎  تعديل"){edit=m;reply=null;text=m.text.orEmpty();menuMessage=null};if(m.senderId==me?.id)ChatOption("⌫  حذف",danger=true){scope.launch{try{socialChatApi.deleteMessage(auth,chat.id,m.id);refresh()}catch(_:Exception){}};menuMessage=null};Spacer(Modifier.height(20.dp))}}}
}

@Composable private fun ChatOption(label:String,danger:Boolean=false,onClick:()->Unit){TextButton(onClick=onClick,modifier=Modifier.fillMaxWidth()){Text(label,color=if(danger)Color(0xFFFF667D)else Color.White,modifier=Modifier.fillMaxWidth(),fontSize=16.sp)}}
