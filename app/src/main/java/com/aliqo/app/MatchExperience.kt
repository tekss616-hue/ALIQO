package com.aliqo.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ChallengeBg=Color(0xFF061126)
private val ChallengeCard=Color(0xFF101C34)
private val ChallengeWhite=Color(0xFFF7F9FF)
private val ChallengeMuted=Color(0xFFAEB8D1)
private val ChallengePurple=Color(0xFF7C32F2)

data class ChallengeGame(val icon:String,val title:String,val subtitle:String,val tag:String)

private val challengeGames=listOf(
    ChallengeGame("✊✋✌️","حجر ورقة مقص","اختر حركتك سرًا واكشف النتيجة مع خصمك","سريع"),
    ChallengeGame("🧠","تحدي التوافق","أجيبا عن نفس الأسئلة واكتشفا نسبة التوافق","توافق"),
    ChallengeGame("⚡","أسرع إجابة","سؤال وأربع اختيارات.. الأسرع بالصحيح يكسب","سرعة"),
    ChallengeGame("🎯","خمن اختيار خصمك","توقع ماذا سيختار خصمك قبل كشف الإجابة","تخمين"),
    ChallengeGame("🕵️","مين الكذاب؟","اكتشف العبارة المزيفة قبل خصمك","ذكاء"),
    ChallengeGame("🔢","الرقم السري","خمن الرقم باستخدام تلميحات أعلى وأقل","منطق"),
    ChallengeGame("🧩","فك الشفرة","حل الرموز والإيموجي قبل منافسك","ألغاز"),
    ChallengeGame("💣","لا تختار نفس الشيء","اختيارك سرّي.. لا تقع على نفس اختيار خصمك","نجاة"),
    ChallengeGame("👑","ملك التحدي","مواجهات جماعية متتابعة حتى يبقى ملك الساحة","جماعي")
)

@Composable
fun PremiumMatchExperience(auth:String,open:(ChatDto)->Unit){
    var mode by remember{mutableStateOf<String?>(null)}
    var selected by remember{mutableStateOf<ChallengeGame?>(null)}
    when {
        selected!=null -> ChallengePreview(selected!!,mode.orEmpty()){selected=null}
        mode!=null -> ChallengePicker(mode!!,{mode=null}){selected=it}
        else -> ChallengeLanding(onOne={mode="ONE"},onGroup={mode="GROUP"})
    }
}

@Composable private fun ChallengeLanding(onOne:()->Unit,onGroup:()->Unit){
    Column(Modifier.fillMaxSize().background(ChallengeBg).verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text("⚔️ التحديات",color=ChallengeWhite,fontSize=28.sp,fontWeight=FontWeight.Black);Spacer(Modifier.weight(1f));Text("🏆",fontSize=23.sp)}
        Text("اختر نوع المواجهة",color=ChallengeWhite,fontSize=22.sp,fontWeight=FontWeight.Bold)
        Text("ادخل الساحة، اختبر مهارتك، واكسب المواجهة",color=ChallengeMuted,fontSize=14.sp)
        ChallengeModeCard("⚔️","1 ضد 1","واجه لاعبًا في تحديات سريعة ومباشرة",Brush.linearGradient(listOf(Color(0xFF5727A9),Color(0xFF18245A))),onOne)
        ChallengeModeCard("👥","جماعي","تحديات تجمع عدة لاعبين في ساحة واحدة",Brush.linearGradient(listOf(Color(0xFF0875C9),Color(0xFF11274E))),onGroup)
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF25164E)),shape=RoundedCornerShape(22.dp)){Row(Modifier.fillMaxWidth().padding(17.dp),verticalAlignment=Alignment.CenterVertically){Text("🏆",fontSize=31.sp);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("بطولة الشهر",color=ChallengeWhite,fontWeight=FontWeight.ExtraBold,fontSize=17.sp);Text("منافسة شهرية كبيرة",color=ChallengeMuted,fontSize=12.sp)};Surface(shape=RoundedCornerShape(12.dp),color=Color(0xFF56318D)){Text("قريبًا",Modifier.padding(horizontal=10.dp,vertical=5.dp),color=Color(0xFFE8D5FF),fontSize=11.sp,fontWeight=FontWeight.Bold)}}}
    }
}

@Composable private fun ChallengeModeCard(icon:String,title:String,subtitle:String,brush:Brush,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(23.dp),colors=CardDefaults.cardColors(containerColor=Color.Transparent)){Row(Modifier.fillMaxWidth().height(110.dp).background(brush).padding(17.dp),verticalAlignment=Alignment.CenterVertically){Text(icon,fontSize=38.sp);Spacer(Modifier.width(15.dp));Column(Modifier.weight(1f)){Text(title,color=ChallengeWhite,fontSize=22.sp,fontWeight=FontWeight.ExtraBold);Text(subtitle,color=ChallengeMuted,fontSize=13.sp,lineHeight=18.sp)};Text("‹",color=ChallengeWhite,fontSize=31.sp)}}}

@Composable private fun ChallengePicker(mode:String,onBack:()->Unit,onSelect:(ChallengeGame)->Unit){
    val games=if(mode=="GROUP") challengeGames.filter{it.title=="ملك التحدي"||it.title=="تحدي التوافق"||it.title=="أسرع إجابة"||it.title=="لا تختار نفس الشيء"} else challengeGames.filter{it.title!="ملك التحدي"}
    Column(Modifier.fillMaxSize().background(ChallengeBg).verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text("‹",color=ChallengeWhite,fontSize=34.sp,modifier=Modifier.clickable(onClick=onBack));Spacer(Modifier.width(8.dp));Column{Text(if(mode=="GROUP")"👥 تحديات جماعية" else "⚔️ تحديات 1 ضد 1",color=ChallengeWhite,fontSize=25.sp,fontWeight=FontWeight.Black);Text("اختر لعبتك وادخل المواجهة",color=ChallengeMuted,fontSize=12.sp)}}
        games.forEach{game->Card(Modifier.fillMaxWidth().clickable{onSelect(game)},shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=ChallengeCard),border=BorderStroke(1.dp,Color(0xFF263650))){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(52.dp).background(Color(0xFF241B50),RoundedCornerShape(15.dp)),contentAlignment=Alignment.Center){Text(game.icon,fontSize=27.sp)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(game.title,color=ChallengeWhite,fontSize=17.sp,fontWeight=FontWeight.Bold);Text(game.subtitle,color=ChallengeMuted,fontSize=11.sp,lineHeight=16.sp)};Surface(shape=RoundedCornerShape(10.dp),color=Color(0xFF29204E)){Text(game.tag,Modifier.padding(horizontal=8.dp,vertical=4.dp),color=Color(0xFFC9B7FF),fontSize=9.sp)};Spacer(Modifier.width(5.dp));Text("‹",color=ChallengeWhite,fontSize=25.sp)}}}
        Spacer(Modifier.height(10.dp))
    }
}

@Composable private fun ChallengePreview(game:ChallengeGame,mode:String,onBack:()->Unit){
    Column(Modifier.fillMaxSize().background(ChallengeBg).padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        Text(game.icon,fontSize=70.sp);Spacer(Modifier.height(16.dp));Text(game.title,color=ChallengeWhite,fontSize=28.sp,fontWeight=FontWeight.Black,textAlign=TextAlign.Center);Spacer(Modifier.height(8.dp));Text(game.subtitle,color=ChallengeMuted,fontSize=15.sp,textAlign=TextAlign.Center,lineHeight=22.sp);Spacer(Modifier.height(24.dp));Surface(shape=RoundedCornerShape(16.dp),color=ChallengeCard){Text(if(mode=="GROUP")"👥 وضع جماعي" else "⚔️ وضع 1 ضد 1",Modifier.padding(horizontal=18.dp,vertical=9.dp),color=ChallengeWhite,fontWeight=FontWeight.Bold)};Spacer(Modifier.height(28.dp));Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(27.dp),colors=ButtonDefaults.buttonColors(disabledContainerColor=ChallengePurple.copy(alpha=.55f),disabledContentColor=Color.White)){Text("قريبًا — تجهيز نظام اللعب",fontWeight=FontWeight.Bold)};TextButton(onClick=onBack){Text("اختيار تحدٍ آخر",color=ChallengeMuted)}}
}
