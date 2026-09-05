package com.aliqo.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.util.concurrent.TimeUnit

data class LeaderboardPlayerDto(
    val id:String="",
    val username:String="",
    val displayName:String?=null,
    val avatarUrl:String?=null,
    val wins:Int=0,
    val losses:Int=0,
    val draws:Int=0,
    val matchesPlayed:Int=0,
    val xp:Int=0,
    val winStreak:Int=0,
    val bestWinStreak:Int=0,
    val position:Int=0,
    val rank:String="BRONZE",
    val winRate:Int=0
)
data class LeaderboardResponse(val items:List<LeaderboardPlayerDto> = emptyList(),val me:LeaderboardPlayerDto?=null)
private interface LeaderboardApi{@GET("players/leaderboard/top") suspend fun top(@Header("Authorization") auth:String):LeaderboardResponse}
private val leaderboardApi:LeaderboardApi by lazy{val client=SessionAuth.clientBuilder().connectTimeout(75,TimeUnit.SECONDS).readTimeout(75,TimeUnit.SECONDS).build();Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(LeaderboardApi::class.java)}
private val LBg=Color(0xFF061126)
private val LCard=Color(0xFF0E1D38)
private val LMuted=Color(0xFFAAB5D2)
private val LPurple=Color(0xFF7C2CFF)
private val LGold=Color(0xFFFFC857)

@Composable fun LeaderboardScreen(auth:String){
    var data by remember{mutableStateOf(LeaderboardResponse())}
    var loading by remember{mutableStateOf(true)}
    var status by remember{mutableStateOf("")}
    LaunchedEffect(auth){while(true){try{data=leaderboardApi.top(auth);status=""}catch(_:Exception){if(data.items.isEmpty())status="تعذر تحميل المتصدرين"};loading=false;delay(15000)}}
    Column(Modifier.fillMaxSize().background(LBg)){
        Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically){AliqoArenaIcon(AliqoIcon.TROPHY,size=38.dp);Spacer(Modifier.width(10.dp));Column{Text("المتصدرون",color=Color.White,fontSize=31.sp,fontWeight=FontWeight.Black);Text("TOP ALIQO — المجد يُنتزع",color=LMuted,fontSize=12.sp)}}
        if(loading&&data.items.isEmpty()){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(color=LPurple)};return@Column}
        data.me?.let{me->Surface(shape=RoundedCornerShape(20.dp),color=Color(0xFF24194A),modifier=Modifier.fillMaxWidth().padding(vertical=10.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text("#${me.position}",color=LGold,fontWeight=FontWeight.Black,fontSize=22.sp);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("مركزك الحالي",color=LMuted,fontSize=11.sp);Text(me.displayName?.ifBlank{me.username}?:me.username,color=Color.White,fontWeight=FontWeight.Black)};Column(horizontalAlignment=Alignment.End){Text(rankArabic(me.rank),color=LGold,fontWeight=FontWeight.Bold);Text("${me.xp} XP",color=LMuted,fontSize=11.sp)}}}}
        val top3=data.items.take(3)
        if(top3.isNotEmpty()){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Bottom){top3.forEachIndexed{index,p->PodiumPlayer(p,index,Modifier.weight(1f))}}}
        Text("الترتيب العام",color=Color.White,fontWeight=FontWeight.Black,fontSize=19.sp,modifier=Modifier.padding(vertical=8.dp))
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=24.dp)){
            items(data.items.drop(3),key={it.id}){p->LeaderboardRow(p)}
            if(status.isNotBlank())item{Text(status,color=Color(0xFFFF7A8A),modifier=Modifier.padding(10.dp))}
        }
    }
}

private fun rankBadge(position:Int)=when(position){1->R.drawable.aliqo_crown_rank1;2->R.drawable.aliqo_medal_rank2;3->R.drawable.aliqo_medal_rank3;else->R.drawable.aliqo_medal_rank3}

@Composable private fun PodiumPlayer(p:LeaderboardPlayerDto,index:Int,modifier:Modifier){
    val height=when(p.position){1->168.dp;2->145.dp;else->138.dp}
    val iconSize=when(p.position){1->66.dp;else->58.dp}
    Card(modifier.height(height).clickable{PlayerProfileNavigation.open(p.id)},shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=if(p.position==1)Color(0xFF34204F) else LCard)){
        Column(Modifier.fillMaxSize().padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
            if(p.position==2){
                Image(bitmap=rememberCleanDarkEdgeBitmap(R.drawable.aliqo_medal_rank2),contentDescription=null,contentScale=ContentScale.Fit,modifier=Modifier.size(iconSize))
            }else{
                Image(painter=painterResource(rankBadge(p.position)),contentDescription=null,contentScale=ContentScale.Fit,modifier=Modifier.size(iconSize))
            }
            Text("#${p.position}",color=LGold,fontWeight=FontWeight.Black)
            Text(p.displayName?.ifBlank{p.username}?:p.username,color=Color.White,fontWeight=FontWeight.Bold,maxLines=1,textAlign=TextAlign.Center,fontSize=13.sp)
            Text("${p.xp} XP",color=LMuted,fontSize=11.sp)
            Text("${p.winRate}% فوز",color=Color(0xFF74E9FF),fontSize=10.sp)
        }
    }
}
@Composable private fun LeaderboardRow(p:LeaderboardPlayerDto){
    Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(LCard,Color(0xFF0A1830))),RoundedCornerShape(18.dp)).clickable{PlayerProfileNavigation.open(p.id)}.padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
        Text("#${p.position}",color=if(p.position<=10)LGold else LMuted,fontWeight=FontWeight.Black,modifier=Modifier.width(42.dp));Column(Modifier.weight(1f)){Text(p.displayName?.ifBlank{p.username}?:p.username,color=Color.White,fontWeight=FontWeight.Bold);Text("${rankArabic(p.rank)} • ${p.wins} فوز • ${p.winRate}%",color=LMuted,fontSize=11.sp)};if(p.bestWinStreak>=3)Text("🔥${p.bestWinStreak}",color=LGold,fontWeight=FontWeight.Bold)
    }
}
private fun rankArabic(rank:String)=when(rank){"MASTER"->"ماستر";"DIAMOND"->"ألماسي";"PLATINUM"->"بلاتيني";"GOLD"->"ذهبي";"SILVER"->"فضي";else->"برونزي"}
