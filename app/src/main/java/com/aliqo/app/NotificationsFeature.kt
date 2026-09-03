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

data class NotificationDto(
    val id:String,
    val type:String,
    val title:String,
    val body:String?=null,
    val dataJson:String?=null,
    val readAt:String?=null,
    val createdAt:String?=null
)

interface NotificationsApi {
    @GET("notifications") suspend fun list(@Header("Authorization") auth:String):List<NotificationDto>
    @POST("notifications/{id}/read") suspend fun read(@Header("Authorization") auth:String,@Path("id") id:String):NotificationDto
    @POST("notifications/read-all") suspend fun readAll(@Header("Authorization") auth:String):OkResponse
}

private val notificationsApi:NotificationsApi by lazy {
    val client=OkHttpClient.Builder().connectTimeout(75,TimeUnit.SECONDS).readTimeout(75,TimeUnit.SECONDS).build()
    Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(NotificationsApi::class.java)
}

@Composable
fun NotificationsScreen(auth:String,onUnreadChanged:(Int)->Unit={}){
    var items by remember{mutableStateOf<List<NotificationDto>>(emptyList())}
    var status by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()

    suspend fun refresh(){
        items=notificationsApi.list(auth)
        onUnreadChanged(items.count{it.readAt==null})
    }

    LaunchedEffect(auth){
        try{refresh()}catch(_:Exception){status="تعذر تحميل التنبيهات"}
        while(true){delay(30000);try{refresh()}catch(_:Exception){}}
    }

    DisposableEffect(auth){
        val socket=IO.socket(BuildConfig.REALTIME_URL,IO.Options.builder().setAuth(mapOf("token" to auth.removePrefix("Bearer ").trim())).setReconnection(true).build())
        val listener=Emitter.Listener{scope.launch{try{refresh()}catch(_:Exception){}}}
        socket.on("notifications:changed",listener)
        socket.connect()
        onDispose{socket.off("notifications:changed",listener);socket.disconnect();socket.close()}
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text("التنبيهات",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            if(items.any{it.readAt==null}) TextButton(onClick={scope.launch{busy=true;try{notificationsApi.readAll(auth);refresh();status=""}catch(_:Exception){status="تعذر تعليم الكل كمقروء"};busy=false}},enabled=!busy){Text("قراءة الكل")}
        }
        if(items.isEmpty()) Text("لا توجد تنبيهات حتى الآن")
        items.forEach{item->
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                        Text(item.title,fontWeight=if(item.readAt==null)FontWeight.Bold else FontWeight.Normal)
                        if(item.readAt==null) Text("●")
                    }
                    item.body?.takeIf{it.isNotBlank()}?.let{Text(it)}
                    if(item.readAt==null) TextButton(onClick={scope.launch{try{notificationsApi.read(auth,item.id);refresh()}catch(_:Exception){status="تعذر تحديث التنبيه"}}}){Text("تعليم كمقروء")}
                }
            }
        }
        if(status.isNotBlank()) Text(status)
        Spacer(Modifier.height(24.dp))
    }
}
