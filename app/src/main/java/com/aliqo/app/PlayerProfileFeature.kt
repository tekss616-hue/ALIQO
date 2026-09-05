package com.aliqo.app

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class PlayerAchievementDto(val code:String="",val title:String="",val unlocked:Boolean=false,val progress:Int=0,val target:Int=1)
data class PlayerProgressDto(val wins:Int=0,val losses:Int=0,val draws:Int=0,val matchesPlayed:Int=0,val xp:Int=0,val level:Int=1,val winRate:Int=0,val winStreak:Int=0,val bestWinStreak:Int=0,val achievements:List<PlayerAchievementDto> = emptyList())
data class PlayerProfileViewDto(val id:String="",val username:String="",val createdAt:String?=null,val profile:ProfileDto?=null,val progress:PlayerProgressDto=PlayerProgressDto())
private interface PlayerProfileApi{@GET("players/me/profile") suspend fun mine(@Header("Authorization") auth:String):PlayerProfileViewDto;@GET("players/{userId}/profile") suspend fun player(@Header("Authorization") auth:String,@Path("userId") userId:String):PlayerProfileViewDto}
private val playerProfileApi:PlayerProfileApi by lazy{val client=OkHttpClient.Builder().connectTimeout(75,TimeUnit.SECONDS).readTimeout(75,TimeUnit.SECONDS).writeTimeout(75,TimeUnit.SECONDS).callTimeout(90,TimeUnit.SECONDS).build();Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(PlayerProfileApi::class.java)}
private object PlayerProfileCache{private const val PREFS="aliqo_player_profiles";private val gson=Gson();fun key(isMine:Boolean,userId:String?)=if(isMine)"me" else "player_${userId.orEmpty()}";fun load(context:Context,key:String):PlayerProfileViewDto?=try{context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(key,null)?.let{gson.fromJson(it,PlayerProfileViewDto::class.java)}}catch(_:Exception){null};fun save(context:Context,key:String,value:PlayerProfileViewDto){try{context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(key,gson.toJson(value)).apply()}catch(_:Exception){}}}
private val PBg=Color(0xFF071126);private val PCard=Color(0xFF0C1B36);private val PMuted=Color(0xFFAAB5D2);private val PPurple=Color(0xFF7C2CFF);private val PBlue=Color(0xFF22B8FF);private val PGreen=Color(0xFF22D978);private val PGold=Color(0xFFFFC857);private val PGoldDeep=Color(0xFF9A5B00)

@Composable fun PlayerProfileScreen(auth:String,userId:String?=null,isMine:Boolean=false,onBack:(()->Unit)?=null,onEdit:(()->Unit)?=null,themeOverride:String?=null,previewMode:Boolean=false){
    val context=LocalContext.current
    val cacheKey=remember(userId,isMine){PlayerProfileCache.key(isMine,userId)}
    val cached=remember(cacheKey){PlayerProfileCache.load(context,cacheKey)}
    var data by remember(userId,auth){mutableStateOf(cached)}
    var failed by remember(userId,auth){mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    suspend fun load(){failed=false;var success=false;repeat(2){attempt->if(success)return@repeat;try{val fresh=if(isMine)playerProfileApi.mine(auth) else playerProfileApi.player(auth,userId?:return);data=fresh;PlayerProfileCache.save(context,cacheKey,fresh);failed=false;success=true}catch(_:Exception){if(attempt==0)delay(450)}};if(!success)failed=true}
    LaunchedEffect(auth,userId,isMine){load()}
    val p=data?:PlayerProfileViewDto(username="")
    val s=p.progress
    val name=(p.profile?.displayName?.ifBlank{p.username}?:p.username).ifBlank{" "}
    val theme=themeOverride?:if(isMine)ProfileThemePrefs.equipped(context) else PROFILE_THEME_DEFAULT
    val royal=theme==PROFILE_THEME_ROYAL_GOLD
    val bg=if(royal)Color(0xFF080A0F) else PBg
    val card=if(royal)Color(0xE6111720) else PCard
    val accent=if(royal)PGold else PPurple
    Box(Modifier.fillMaxSize().background(bg)){
        if(royal)RoyalBackdrop()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=4.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){if(onBack!=null)TextButton(onClick=onBack){Text("‹",color=Color.White,fontSize=34.sp)} else Spacer(Modifier.width(48.dp));Text(if(previewMode)"معاينة الحزمة" else if(isMine)"ملفي الشخصي" else "ملف اللاعب",modifier=Modifier.weight(1f),color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Black);if(!previewMode&&isMine&&onEdit!=null)OutlinedButton(onClick=onEdit,shape=RoundedCornerShape(14.dp),border=androidx.compose.foundation.BorderStroke(1.dp,if(royal)PGold else Color.Gray)){Text("تعديل الملف",color=Color.White,fontSize=12.sp)} else Spacer(Modifier.width(48.dp))}
            if(previewMode){Text("هذه معاينة فقط — لم يتم تجهيز الحزمة",color=PGold,fontSize=11.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(4.dp))}
            Spacer(Modifier.height(12.dp))
            Box(contentAlignment=Alignment.BottomCenter){
                if(royal){RoyalGoldEmblem(126.dp)}else{Box(Modifier.size(112.dp).clip(CircleShape).background(Brush.linearGradient(listOf(PPurple.copy(alpha=.65f),PBlue.copy(alpha=.45f)))).border(3.dp,PPurple,CircleShape),contentAlignment=Alignment.Center){Text(if(p.username.isBlank())"" else name.take(1).uppercase(),color=Color.White,fontSize=42.sp,fontWeight=FontWeight.Black)}}
                Box(Modifier.offset(y=10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF111A20)).border(1.dp,accent,RoundedCornerShape(10.dp)).padding(horizontal=9.dp,vertical=3.dp)){Text("Lv. ${s.level}",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold)}
            }
            Spacer(Modifier.height(18.dp));Text(name,color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Black);Text(if(p.username.isBlank())" " else "@${p.username}",color=PMuted,fontSize=14.sp);Text(if(p.username.isBlank())" " else if(p.profile?.isOnline==true)"● متصل الآن" else "● غير متصل",color=if(p.profile?.isOnline==true)PGreen else PMuted,fontSize=12.sp)
            p.profile?.bio?.takeIf{it.isNotBlank()}?.let{Spacer(Modifier.height(8.dp));Text(it,color=Color.White,fontSize=14.sp)}
            Spacer(Modifier.height(18.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){ThemeStatTile("🏆",s.wins.toString(),"فوز",Modifier.weight(1f),royal,card);ThemeStatTile("💀",s.losses.toString(),"خسارة",Modifier.weight(1f),royal,card);ThemeStatTile("🎮",s.matchesPlayed.toString(),"مباراة",Modifier.weight(1f),royal,card)}
            Spacer(Modifier.height(10.dp));Card(colors=CardDefaults.cardColors(containerColor=card),shape=RoundedCornerShape(18.dp),border=if(royal)androidx.compose.foundation.BorderStroke(1.dp,PGold.copy(.7f)) else null,modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(58.dp).clip(CircleShape).background(if(royal)Color(0xFF2C1C08) else Color(0xFF102948)),contentAlignment=Alignment.Center){Text("${s.winRate}%",color=if(royal)PGold else PBlue,fontWeight=FontWeight.Black,fontSize=17.sp)};Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text("نسبة الفوز",color=Color.White,fontWeight=FontWeight.Bold);Spacer(Modifier.height(7.dp));LinearProgressIndicator(progress={s.winRate.coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),color=accent,trackColor=Color(0xFF273043))}}}
            Spacer(Modifier.height(10.dp));Card(colors=CardDefaults.cardColors(containerColor=card),shape=RoundedCornerShape(18.dp),border=if(royal)androidx.compose.foundation.BorderStroke(1.dp,PGold.copy(.7f)) else null,modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Row(Modifier.fillMaxWidth()){Text((if(royal)"✦" else "⭐")+" المستوى ${s.level}",color=if(royal)PGold else Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text("${s.xp} XP",color=PMuted,fontSize=12.sp)};Spacer(Modifier.height(8.dp));val inLevel=s.xp%500;LinearProgressIndicator(progress={inLevel/500f},modifier=Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),color=accent,trackColor=Color(0xFF273043));Spacer(Modifier.height(9.dp));Row(Modifier.fillMaxWidth()){Text("السلسلة الحالية: ${s.winStreak}",color=PMuted,fontSize=12.sp,modifier=Modifier.weight(1f));Text("أفضل سلسلة: ${s.bestWinStreak}",color=PMuted,fontSize=12.sp)}}}
            Spacer(Modifier.height(16.dp));Text("الإنجازات",modifier=Modifier.fillMaxWidth(),color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Black);Spacer(Modifier.height(8.dp));val shown=s.achievements.take(8);if(shown.isEmpty())Text("ستظهر إنجازاتك هنا مع اللعب",color=PMuted,modifier=Modifier.fillMaxWidth());shown.chunked(2).forEach{pair->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){pair.forEach{a->AchievementTile(a,Modifier.weight(1f),royal)};if(pair.size==1)Spacer(Modifier.weight(1f))};Spacer(Modifier.height(8.dp))}
            if(!isMine&&p.username.isNotBlank()){Spacer(Modifier.height(4.dp));Text("إحصائيات هذا الملف مصدرها السيرفر وتتحدث مع نتائج اللعب.",color=PMuted,fontSize=11.sp)}
            if(failed&&data!=null){Spacer(Modifier.height(8.dp));TextButton(onClick={scope.launch{load()}}){Text("تعذر تحديث البيانات — نعرض آخر نسخة محفوظة، اضغط للمحاولة",color=PMuted,fontSize=11.sp)}}
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun RoyalBackdrop(){Canvas(Modifier.fillMaxSize()){drawRect(brush=Brush.verticalGradient(listOf(Color(0xFF07080B),Color(0xFF171008),Color(0xFF070A0F))));val gold=PGold.copy(.22f);for(i in 0..4){val x=size.width*(.08f+i*.21f);drawLine(gold,Offset(x,0f),Offset(x,size.height),strokeWidth=2f)};for(i in 0..18){val x=(i*97%100)/100f*size.width;val y=(i*173%100)/100f*size.height;drawCircle(PGold.copy(.35f),radius=2.3f,center=Offset(x,y))}}}
@Composable private fun ThemeStatTile(icon:String,value:String,label:String,modifier:Modifier,royal:Boolean,card:Color){Card(modifier=modifier,colors=CardDefaults.cardColors(containerColor=card),shape=RoundedCornerShape(17.dp),border=if(royal)androidx.compose.foundation.BorderStroke(1.dp,PGold.copy(.65f)) else null){Column(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(icon,fontSize=18.sp,color=if(royal)PGold else Color.Unspecified);Text(value,color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Black);Text(label,color=PMuted,fontSize=11.sp)}}}
@Composable private fun AchievementTile(a:PlayerAchievementDto,modifier:Modifier,royal:Boolean){val card=if(royal)Color(0xE6111720) else if(a.unlocked)Color(0xFF142448) else Color(0xFF0A1730);Card(modifier=modifier,colors=CardDefaults.cardColors(containerColor=card),shape=RoundedCornerShape(15.dp),border=if(royal)androidx.compose.foundation.BorderStroke(1.dp,PGold.copy(.45f)) else null){Column(Modifier.fillMaxWidth().padding(12.dp)){Text(if(a.unlocked)"✦" else "🔒",fontSize=18.sp,color=if(royal)PGold else Color.Unspecified);Spacer(Modifier.height(4.dp));Text(a.title,color=if(a.unlocked)Color.White else PMuted,fontSize=12.sp,fontWeight=FontWeight.Bold);Text(if(a.unlocked)"تم الإنجاز" else "${a.progress}/${a.target}",color=if(royal)PGold else if(a.unlocked)PBlue else PMuted,fontSize=10.sp)}}}
