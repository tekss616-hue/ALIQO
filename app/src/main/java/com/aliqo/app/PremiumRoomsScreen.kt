package com.aliqo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private val RoomsBg = Color(0xFF061126)
private val RoomsCard = Color(0xFF101C34)
private val RoomsCard2 = Color(0xFF15233D)
private val RoomsWhite = Color(0xFFF7F9FF)
private val RoomsMuted = Color(0xFFAEB8D1)
private val RoomsPurple = Color(0xFF7C32F2)
private val RoomsPurple2 = Color(0xFFB14DFF)
private val RoomsGreen = Color(0xFF18D67C)

private val roomsUiApi: ChatApi by lazy {
    val client = OkHttpClient.Builder().connectTimeout(75, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS).writeTimeout(75, TimeUnit.SECONDS).build()
    Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(ChatApi::class.java)
}

private object RoomsMemoryCache { var rooms:List<RoomDto>?=null }

@Composable
fun PremiumRoomsScreen(auth:String, me:UserDto?, openChat:(ChatDto,String,Boolean)->Unit) {
    val context=LocalContext.current
    val stored=remember{PersistentUiCache.loadRooms(context,"rooms")}
    val hadStored=remember{PersistentUiCache.has(context,"rooms")}
    var rooms by remember { mutableStateOf(RoomsMemoryCache.rooms?:stored) }
    var resolved by remember { mutableStateOf(RoomsMemoryCache.rooms!=null||hadStored) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("الكل") }
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        val fresh=roomsUiApi.rooms(auth)
        rooms=fresh
        RoomsMemoryCache.rooms=fresh
        PersistentUiCache.saveRooms(context,"rooms",fresh)
        resolved=true
    }
    LaunchedEffect(auth) {
        while (isActive) {
            try { refresh(); if (status == "تعذر تحميل الرومات") status = "" } catch (_:Exception) { resolved=true;if(rooms.isEmpty()&&!hadStored)status = "تعذر تحميل الرومات" }
            delay(15000)
        }
    }
    DisposableEffect(auth) {
        val socket=IO.socket(BuildConfig.REALTIME_URL,IO.Options.builder().setAuth(mapOf("token" to auth.removePrefix("Bearer ").trim())).setReconnection(true).build())
        val listener=Emitter.Listener { scope.launch { try { refresh() } catch (_:Exception) {} } }
        socket.on("room:member-joined",listener);socket.on("room:member-left",listener);socket.on("room:closed",listener);socket.connect()
        onDispose { socket.off();socket.disconnect();socket.close() }
    }

    val visibleRooms = rooms.filter { room ->
        val q=query.trim()
        q.isBlank() || room.name.contains(q,true) || room.description.orEmpty().contains(q,true)
    }

    Column(
        Modifier.fillMaxSize().background(RoomsBg).verticalScroll(rememberScrollState()).padding(horizontal=16.dp, vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            Column {
                Text("👥 الرومات", color=RoomsWhite, fontSize=29.sp, fontWeight=FontWeight.ExtraBold)
                Text("مكان واحد.. آلاف السوالف", color=RoomsMuted, fontSize=13.sp)
            }
            Spacer(Modifier.weight(1f))
            FilledIconButton(onClick={showCreate=!showCreate}, colors=IconButtonDefaults.filledIconButtonColors(containerColor=RoomsPurple)) { Text(if(showCreate) "×" else "+", color=Color.White, fontSize=24.sp) }
        }

        OutlinedTextField(
            value=query,onValueChange={query=it.take(80)},modifier=Modifier.fillMaxWidth(),singleLine=true,
            placeholder={Text("ابحث عن روم...",color=RoomsMuted)},leadingIcon={Text("⌕",color=Color(0xFFC084FC),fontSize=22.sp)},
            shape=RoundedCornerShape(22.dp),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=RoomsWhite,unfocusedTextColor=RoomsWhite,focusedContainerColor=RoomsCard,unfocusedContainerColor=RoomsCard,focusedBorderColor=Color(0xFF51359B),unfocusedBorderColor=Color(0xFF243451))
        )

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("الكل" to "▦","سوالف" to "💬","ألعاب" to "🎮","أنمي" to "🐱","رياضة" to "⚽","موسيقى" to "🎵").forEach { (label,icon) ->
                val selected=category==label
                Surface(onClick={category=label},shape=RoundedCornerShape(18.dp),color=if(selected) RoomsPurple else RoomsCard,border=if(selected) null else androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF293A5C))) {
                    Column(Modifier.width(66.dp).padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)){Text(icon,fontSize=21.sp);Text(label,color=RoomsWhite,fontSize=11.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium)}
                }
            }
        }

        if(showCreate) {
            Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=RoomsCard)) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text("إنشاء روم جديد",color=RoomsWhite,fontWeight=FontWeight.Bold,fontSize=18.sp)
                    OutlinedTextField(name,{name=it.take(80)},Modifier.fillMaxWidth(),singleLine=true,label={Text("اسم الروم")},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=RoomsWhite,unfocusedTextColor=RoomsWhite,focusedBorderColor=RoomsPurple,unfocusedBorderColor=Color(0xFF40506E),focusedLabelColor=RoomsMuted,unfocusedLabelColor=RoomsMuted))
                    OutlinedTextField(description,{description=it.take(280)},Modifier.fillMaxWidth(),label={Text("وصف مختصر - اختياري")},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=RoomsWhite,unfocusedTextColor=RoomsWhite,focusedBorderColor=RoomsPurple,unfocusedBorderColor=Color(0xFF40506E),focusedLabelColor=RoomsMuted,unfocusedLabelColor=RoomsMuted))
                    Button(onClick={scope.launch{creating=true;try{val r=roomsUiApi.createRoom(auth,CreateRoomRequest(name.trim(),description.trim().ifBlank{null}));name="";description="";showCreate=false;refresh();openChat(r.chat,r.room.id,true);status=""}catch(_:Exception){status="تعذر إنشاء الروم"};creating=false}},enabled=!creating&&name.trim().length>=2,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=RoomsPurple),shape=RoundedCornerShape(18.dp)){Text(if(creating)"جارٍ الإنشاء..." else "إنشاء ودخول")}
                }
            }
        }

        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("الرومات النشطة",color=RoomsWhite,fontSize=20.sp,fontWeight=FontWeight.ExtraBold);Spacer(Modifier.width(6.dp));Text("🔥",fontSize=18.sp);Spacer(Modifier.weight(1f));TextButton(onClick={scope.launch{try{refresh();status=""}catch(_:Exception){status="تعذر تحديث الرومات"}}}){Text("تحديث",color=Color(0xFFC084FC))}}

        if(resolved&&visibleRooms.isEmpty()) {
            Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=RoomsCard)) {
                Column(Modifier.fillMaxWidth().padding(vertical=34.dp,horizontal=18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("🪐",fontSize=50.sp);Text(if(query.isBlank())"لا توجد رومات نشطة الآن" else "ما لقينا روم بهذا الاسم",color=RoomsWhite,fontSize=18.sp,fontWeight=FontWeight.Bold);Text(if(query.isBlank())"كن أول من ينشئ روم ويبدأ السالفة!" else "جرّب كلمة بحث ثانية",color=RoomsMuted,fontSize=13.sp)
                    if(query.isBlank()) Button(onClick={showCreate=true},colors=ButtonDefaults.buttonColors(containerColor=RoomsPurple),shape=RoundedCornerShape(18.dp)){Text("＋ إنشاء روم")}
                }
            }
        } else visibleRooms.forEachIndexed { index,room ->
            val icon=listOf("💬","🎮","🐱","🎧","⚽","💻")[index%6]
            Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=RoomsCard)) {
                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF243A78),Color(0xFF351064)))),contentAlignment=Alignment.Center){Text(icon,fontSize=28.sp)}
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(room.name,color=RoomsWhite,fontWeight=FontWeight.Bold,fontSize=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        room.description?.takeIf{it.isNotBlank()}?.let{Text(it,color=RoomsMuted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
                        Text("●  ${room._count.members}/${room.capacity}",color=RoomsGreen,fontSize=11.sp)
                    }
                    Button(onClick={scope.launch{try{val chat=roomsUiApi.joinRoom(auth,room.id);openChat(chat,room.id,room.creatorId==me?.id);status=""}catch(_:Exception){status="تعذر دخول الروم"}}},colors=ButtonDefaults.buttonColors(containerColor=RoomsPurple),contentPadding=PaddingValues(horizontal=17.dp,vertical=8.dp),shape=RoundedCornerShape(16.dp)){Text("دخول",fontSize=12.sp)}
                }
            }
        }
        if(status.isNotBlank()) Text(status,color=Color(0xFFFFB4AB),fontSize=12.sp)
        Spacer(Modifier.height(8.dp))
    }
}
