package com.aliqo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import io.socket.client.IO
import io.socket.emitter.Emitter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

data class NotificationDto(val id:String,val type:String,val title:String,val body:String?=null,val dataJson:String?=null,val readAt:String?=null,val createdAt:String?=null)
interface NotificationsApi{
    @GET("notifications/smart") suspend fun list(@Header("Authorization") auth:String):List<NotificationDto>
    @POST("notifications/{id}/read") suspend fun read(@Header("Authorization") auth:String,@Path("id") id:String):NotificationDto
    @POST("notifications/read-all") suspend fun readAll(@Header("Authorization") auth:String):OkResponse
    @POST("notifications/chat/{chatId}/read") suspend fun readChat(@Header("Authorization") auth:String,@Path("chatId") chatId:String):OkResponse
    @DELETE("notifications/{id}") suspend fun deleteOne(@Header("Authorization") auth:String,@Path("id") id:String):OkResponse
    @DELETE("notifications") suspend fun deleteAll(@Header("Authorization") auth:String):OkResponse
}
private val notificationsApi:NotificationsApi by lazy{val client=OkHttpClient.Builder().connectTimeout(75,TimeUnit.SECONDS).readTimeout(75,TimeUnit.SECONDS).build();Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(NotificationsApi::class.java)}
suspend fun markChatNotificationsRead(auth:String,chatId:String){try{notificationsApi.readChat(auth,chatId)}catch(_:Exception){}}
private object NotificationsCache{var items:List<NotificationDto>?=null}
private val NotificationsBg=Color(0xFF071126);private val NotificationsCard=Color(0xFF0C1B36);private val NotificationsMuted=Color(0xFFAAB5D2);private val NotificationsPurple=Color(0xFF7C2CFF);private val NotificationsBlue=Color(0xFF22B8FF)
private fun notificationChatId(item:NotificationDto):String?{if(item.type!="CHAT_MESSAGE")return null;return try{item.dataJson?.let{JSONObject(it).optString("chatId").takeIf(String::isNotBlank)}}catch(_:Exception){null}}

@Composable fun NotificationsScreen(auth:String,onUnreadChanged:(Int)->Unit={}){
 val context=LocalContext.current;val stored=remember{PersistentUiCache.loadNotifications(context,"notifications")};val hadStored=remember{PersistentUiCache.has(context,"notifications")};val initial=NotificationsCache.items?:stored
 var allItems by remember{mutableStateOf(initial)};var resolved by remember{mutableStateOf(NotificationsCache.items!=null||hadStored)};var filter by remember{mutableStateOf("all")};var status by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var openedChatId by remember{mutableStateOf<String?>(null)};var confirmDeleteAll by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 suspend fun refresh(){val fresh=notificationsApi.list(auth);allItems=fresh;NotificationsCache.items=fresh;PersistentUiCache.saveNotifications(context,"notifications",fresh);resolved=true;onUnreadChanged(fresh.count{it.readAt==null})}
 if(openedChatId!=null){NotificationDirectChatEntry(auth,openedChatId!!){openedChatId=null;scope.launch{try{refresh()}catch(_:Exception){}}};return}
 if(confirmDeleteAll)AlertDialog(containerColor=NotificationsCard,onDismissRequest={confirmDeleteAll=false},title={Text("حذف جميع التنبيهات؟",color=Color.White)},text={Text("سيتم حذف التنبيهات نهائيًا ولن تعود بعد تحديث التطبيق.",color=NotificationsMuted)},confirmButton={TextButton(onClick={confirmDeleteAll=false;scope.launch{busy=true;try{notificationsApi.deleteAll(auth);refresh();status=""}catch(_:Exception){status="تعذر حذف التنبيهات"};busy=false}}){Text("حذف الكل",color=Color(0xFFFF667D))}},dismissButton={TextButton(onClick={confirmDeleteAll=false}){Text("إلغاء",color=NotificationsMuted)}})
 LaunchedEffect(auth){if(resolved)onUnreadChanged(allItems.count{it.readAt==null});try{refresh();status=""}catch(_:Exception){resolved=true;if(allItems.isEmpty()&&!hadStored)status="تعذر تحميل التنبيهات"};while(true){delay(7000);try{refresh()}catch(_:Exception){}}}
 DisposableEffect(auth){val socket=IO.socket(BuildConfig.REALTIME_URL,IO.Options.builder().setAuth(mapOf("token" to auth.removePrefix("Bearer ").trim())).setReconnection(true).build());val listener=Emitter.Listener{scope.launch{try{refresh()}catch(_:Exception){}}};val connected=Emitter.Listener{scope.launch{try{refresh()}catch(_:Exception){}}};socket.on("connect",connected);socket.on("notifications:changed",listener);socket.connect();onDispose{socket.off();socket.disconnect();socket.close()}}
 val shown=if(filter=="unread")allItems.filter{it.readAt==null}else allItems
 LazyColumn(Modifier.fillMaxSize().background(NotificationsBg),contentPadding=PaddingValues(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Column(Modifier.fillMaxWidth().padding(top=8.dp,bottom=6.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("التنبيهات",color=Color.White,fontSize=34.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.weight(1f));if(allItems.isNotEmpty())TextButton(onClick={confirmDeleteAll=true},enabled=!busy){Text("حذف الكل",color=Color(0xFFFF7286))}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){NotificationFilter("الكل",filter=="all",Modifier.weight(1f)){filter="all"};NotificationFilter("غير المقروءة",filter=="unread",Modifier.weight(1f)){filter="unread"}};if(allItems.any{it.readAt==null})TextButton(onClick={scope.launch{busy=true;try{notificationsApi.readAll(auth);refresh();status=""}catch(_:Exception){status="تعذر تعليم الكل كمقروء"};busy=false}},enabled=!busy,modifier=Modifier.align(Alignment.End)){Text("قراءة الكل",color=NotificationsMuted)}}}
  if(resolved&&shown.isEmpty())item{Box(Modifier.fillParentMaxWidth().padding(top=56.dp),contentAlignment=Alignment.Center){Text(if(filter=="unread")"لا توجد تنبيهات غير مقروءة" else "لا توجد تنبيهات حتى الآن",color=NotificationsMuted)}}
  items(shown,key={it.id}){item->NotificationCard(item,onOpen={scope.launch{try{if(item.readAt==null){notificationsApi.read(auth,item.id);refresh()};notificationChatId(item)?.let{openedChatId=it}}catch(_:Exception){status="تعذر تحديث التنبيه"}}},onDelete={scope.launch{try{notificationsApi.deleteOne(auth,item.id);refresh();status=""}catch(_:Exception){status="تعذر حذف التنبيه"}}})}
  if(status.isNotBlank())item{Text(status,color=Color(0xFFFF7A8A),modifier=Modifier.padding(top=8.dp))}
 }
}
@Composable private fun NotificationFilter(label:String,selected:Boolean,modifier:Modifier=Modifier,onClick:()->Unit){val shape=RoundedCornerShape(22.dp);Box(modifier.height(48.dp).clip(shape).background(if(selected)Brush.horizontalGradient(listOf(Color(0xFF5B21B6),Color(0xFF8B2CFF)))else Brush.horizontalGradient(listOf(NotificationsCard,NotificationsCard))).clickable(onClick=onClick),contentAlignment=Alignment.Center){Text(label,color=if(selected)Color.White else NotificationsMuted,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium)}}
@Composable private fun NotificationCard(item:NotificationDto,onOpen:()->Unit,onDelete:()->Unit){val unread=item.readAt==null;val shape=RoundedCornerShape(22.dp);Row(Modifier.fillMaxWidth().clip(shape).background(NotificationsCard).then(if(unread)Modifier.border(1.dp,NotificationsPurple.copy(alpha=.65f),shape)else Modifier).clickable(onClick=onOpen).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)){Box(Modifier.size(50.dp).clip(CircleShape).background(Brush.linearGradient(listOf(NotificationsPurple.copy(alpha=.45f),NotificationsBlue.copy(alpha=.30f)))),contentAlignment=Alignment.Center){AliqoArenaIcon(AliqoIcon.BELL,size=26.dp,active=unread)};Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(item.title,color=Color.White,fontWeight=if(unread)FontWeight.Bold else FontWeight.Medium,fontSize=17.sp);item.body?.takeIf{it.isNotBlank()}?.let{Text(it,color=NotificationsMuted,fontSize=14.sp,maxLines=2)};item.createdAt?.takeIf{it.isNotBlank()}?.let{Text(it.take(16).replace("T","  "),color=NotificationsMuted.copy(alpha=.72f),fontSize=11.sp)}};Column(horizontalAlignment=Alignment.CenterHorizontally){if(unread)Box(Modifier.size(9.dp).clip(CircleShape).background(NotificationsPurple));TextButton(onClick=onDelete,contentPadding=PaddingValues(4.dp)){Text("×",color=Color(0xFFFF7286),fontSize=22.sp)}}}}
