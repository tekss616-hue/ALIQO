package com.aliqo.app

import androidx.compose.foundation.background
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
import kotlinx.coroutines.launch

private val DirectoryBg=Color(0xFF061126)
private val DirectoryCard=Color(0xFF0B1B35)
private val DirectoryMuted=Color(0xFF9AAAC6)
private val DirectoryPurple=Color(0xFF7C2CFF)

@Composable
fun PlayerProfilesDirectoryScreen(auth:String,onBack:()->Unit){
    var selected by remember{mutableStateOf<UserDto?>(null)}
    if(selected!=null){PlayerProfileScreen(auth=auth,userId=selected!!.id,isMine=false,onBack={selected=null});return}
    var friends by remember{mutableStateOf<List<UserDto>>(emptyList())}
    var query by remember{mutableStateOf("")}
    var results by remember{mutableStateOf<List<UserDto>>(emptyList())}
    var loading by remember{mutableStateOf(true)}
    var status by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    LaunchedEffect(auth){try{friends=directoryApi.friends(auth)}catch(_:Exception){status="تعذر تحميل اللاعبين"};loading=false}
    Column(Modifier.fillMaxSize().background(DirectoryBg).padding(horizontal=14.dp,vertical=8.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("‹",color=Color.White,fontSize=34.sp)};Column(Modifier.weight(1f)){Text("ملفات اللاعبين",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Black);Text("اضغط على أي لاعب لعرض ملفه وإحصائياته",color=DirectoryMuted,fontSize=12.sp)}}
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value=query,onValueChange={query=it.lowercase().replace(" ","")},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("ابحث باسم المستخدم",color=DirectoryMuted)},trailingIcon={if(query.length>=2)TextButton(onClick={scope.launch{try{results=directoryApi.searchUsers(auth,query);status=""}catch(_:Exception){status="تعذر البحث"}}}){Text("بحث",color=DirectoryPurple)}},shape=RoundedCornerShape(18.dp),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedContainerColor=DirectoryCard,unfocusedContainerColor=DirectoryCard,focusedBorderColor=DirectoryPurple,unfocusedBorderColor=Color(0xFF17375F)))
        if(status.isNotBlank())Text(status,color=Color(0xFFFF7388),fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
        Spacer(Modifier.height(12.dp))
        val shown=if(query.length>=2&&results.isNotEmpty())results else friends
        if(loading){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(color=DirectoryPurple)}}else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(shown.isEmpty())item{Text(if(query.length>=2)"لا توجد نتائج" else "لا يوجد أصدقاء حتى الآن",color=DirectoryMuted,modifier=Modifier.padding(24.dp))}
            items(shown,key={it.id}){u->PlayerDirectoryRow(u){selected=u}}
        }
    }
}

private val directoryApi:AliqoApi by lazy{RetrofitDirectory.api}
private object RetrofitDirectory{
    private val client=okhttp3.OkHttpClient.Builder().connectTimeout(75,java.util.concurrent.TimeUnit.SECONDS).readTimeout(75,java.util.concurrent.TimeUnit.SECONDS).build()
    val api:AliqoApi=retrofit2.Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create()).build().create(AliqoApi::class.java)
}

@Composable private fun PlayerDirectoryRow(user:UserDto,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DirectoryCard).clickable(onClick=onClick).padding(12.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF254B7D),Color(0xFF522D91)))),contentAlignment=Alignment.Center){Text((user.profile?.displayName?.ifBlank{user.username}?:user.username).take(1).uppercase(),color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Black)}
        Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(user.profile?.displayName?.ifBlank{user.username}?:user.username,color=Color.White,fontWeight=FontWeight.Bold);Text("@${user.username}",color=DirectoryMuted,fontSize=12.sp);Text(if(user.profile?.isOnline==true)"متصل الآن" else "غير متصل",color=if(user.profile?.isOnline==true)Color(0xFF22D978) else DirectoryMuted,fontSize=11.sp)}
        Text("عرض الملف ›",color=Color(0xFFB96CFF),fontSize=12.sp,fontWeight=FontWeight.Bold)
    }
}
