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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException

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

@Composable fun PremiumMatchExperience(auth:String,onArenaChanged:(Boolean)->Unit={}){
    var mode by remember{mutableStateOf<String?>(null)}
    var selected by remember{mutableStateOf<ChallengeGame?>(null)}
    when{
        selected?.title=="حجر ورقة مقص"->RockPaperScissors(auth,onArenaChanged){selected=null}
        selected!=null->{onArenaChanged(false);ChallengePreview(selected!!,mode.orEmpty()){selected=null}}
        mode!=null->{onArenaChanged(false);ChallengePicker(mode!!,{mode=null}){selected=it}}
        else->{onArenaChanged(false);ChallengeLanding({mode="ONE"},{mode="GROUP"})}
    }
}

@Composable private fun ChallengeLanding(onOne:()->Unit,onGroup:()->Unit){Column(Modifier.fillMaxSize().background(ChallengeBg).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Text("⚔️ التحديات",color=ChallengeWhite,fontSize=28.sp,fontWeight=FontWeight.Black);Text("اختر نوع المواجهة",color=ChallengeWhite,fontSize=22.sp,fontWeight=FontWeight.Bold);Text("ادخل الساحة، اختبر مهارتك، واكسب المواجهة",color=ChallengeMuted);Mode("⚔️","1 ضد 1","واجه لاعبًا في تحديات سريعة ومباشرة",Color(0xFF6425C7),onOne);Mode("👥","جماعي","تحديات تجمع عدة لاعبين في ساحة واحدة",Color(0xFF0875C9),onGroup);Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF25164E)),shape=RoundedCornerShape(22.dp)){Row(Modifier.fillMaxWidth().padding(17.dp),verticalAlignment=Alignment.CenterVertically){Text("🏆",fontSize=31.sp);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("بطولة الشهر",color=ChallengeWhite,fontWeight=FontWeight.Bold,fontSize=18.sp);Text("منافسة شهرية كبيرة",color=ChallengeMuted)};Text("قريبًا",color=Color(0xFFD7B7FF))}}}}
@Composable private fun Mode(icon:String,title:String,sub:String,color:Color,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(23.dp),colors=CardDefaults.cardColors(containerColor=color)){Row(Modifier.fillMaxWidth().height(110.dp).padding(17.dp),verticalAlignment=Alignment.CenterVertically){Text(icon,fontSize=38.sp);Spacer(Modifier.width(15.dp));Column(Modifier.weight(1f)){Text(title,color=ChallengeWhite,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(sub,color=ChallengeMuted)};Text("‹",color=ChallengeWhite,fontSize=30.sp)}}}
@Composable private fun ChallengePicker(mode:String,onBack:()->Unit,onSelect:(ChallengeGame)->Unit){val games=if(mode=="GROUP")challengeGames.filter{it.title in listOf("ملك التحدي","تحدي التوافق","أسرع إجابة","لا تختار نفس الشيء")}else challengeGames.filter{it.title!="ملك التحدي"};Column(Modifier.fillMaxSize().background(ChallengeBg).verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("‹",color=ChallengeWhite,fontSize=34.sp,modifier=Modifier.clickable(onClick=onBack));Spacer(Modifier.width(8.dp));Column{Text(if(mode=="GROUP")"👥 تحديات جماعية" else "⚔️ تحديات 1 ضد 1",color=ChallengeWhite,fontSize=25.sp,fontWeight=FontWeight.Black);Text("اختر لعبتك وادخل المواجهة",color=ChallengeMuted)}};games.forEach{g->Card(Modifier.fillMaxWidth().clickable{onSelect(g)},shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=ChallengeCard),border=BorderStroke(1.dp,Color(0xFF263650))){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(52.dp).background(Color(0xFF241B50),RoundedCornerShape(15.dp)),contentAlignment=Alignment.Center){Text(g.icon,fontSize=25.sp)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(g.title,color=ChallengeWhite,fontSize=17.sp,fontWeight=FontWeight.Bold);Text(g.subtitle,color=ChallengeMuted,fontSize=11.sp)};Text(g.tag,color=Color(0xFFC9B7FF));Spacer(Modifier.width(7.dp));Text("‹",color=ChallengeWhite,fontSize=25.sp)}}}}}
@Composable private fun ChallengePreview(game:ChallengeGame,mode:String,onBack:()->Unit){Column(Modifier.fillMaxSize().background(ChallengeBg).padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(game.icon,fontSize=70.sp);Text(game.title,color=ChallengeWhite,fontSize=28.sp,fontWeight=FontWeight.Black);Text(game.subtitle,color=ChallengeMuted,textAlign=TextAlign.Center);Spacer(Modifier.height(24.dp));Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth()){Text("قريبًا — تجهيز نظام اللعب")};TextButton(onClick=onBack){Text("اختيار تحدٍ آخر",color=ChallengeMuted)}}}

private enum class RpsScreen{INFO,SEARCH,GAME,ERROR}

private fun rpsErrorMessage(e:Exception):String{
    if(e is HttpException){
        val body=runCatching{e.response()?.errorBody()?.string()}.getOrNull()?.replace("\n"," ")?.take(260)
        return if(body.isNullOrBlank()) "HTTP ${e.code()} — السيرفر لم يرسل تفاصيل" else "HTTP ${e.code()}\n$body"
    }
    val detail=e.message?.take(260) ?: e.javaClass.simpleName
    return "${e.javaClass.simpleName}\n$detail"
}

@Composable private fun RockPaperScissors(auth:String,onArenaChanged:(Boolean)->Unit,onBack:()->Unit){
    var screen by remember{mutableStateOf(RpsScreen.INFO)}
    var sessionId by remember{mutableStateOf<String?>(null)}
    var game by remember{mutableStateOf(RpsStateDto())}
    var error by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()

    LaunchedEffect(screen){onArenaChanged(screen==RpsScreen.SEARCH||screen==RpsScreen.GAME)}
    DisposableEffect(Unit){onDispose{onArenaChanged(false)}}

    LaunchedEffect(screen){
        if(screen==RpsScreen.SEARCH){
            try{
                error=""
                var match=rpsLiveApi.matchQueue(auth,MatchQueueRequest("ONE_V_ONE"))
                while(isActive&&screen==RpsScreen.SEARCH){
                    if(match.state=="MATCHED"&&match.sessionId!=null){sessionId=match.sessionId;game=rpsLiveApi.state(auth,match.sessionId);screen=RpsScreen.GAME;break}
                    delay(1200);match=rpsLiveApi.matchStatus(auth)
                }
            }catch(e:CancellationException){throw e}catch(e:Exception){error=rpsErrorMessage(e);screen=RpsScreen.ERROR}
        }
    }
    LaunchedEffect(screen,sessionId,game.phase,game.readyForNext){
        val id=sessionId ?: return@LaunchedEffect
        val shouldPoll=screen==RpsScreen.GAME&&(game.phase=="WAITING"||(game.phase=="RESULT"&&game.readyForNext))
        if(shouldPoll){while(isActive&&screen==RpsScreen.GAME){delay(350);try{game=rpsLiveApi.state(auth,id)}catch(e:CancellationException){throw e}catch(_:Exception){};if(!(game.phase=="WAITING"||(game.phase=="RESULT"&&game.readyForNext)))break}}
    }
    LaunchedEffect(screen,sessionId,game.phase,game.round,game.readyForNext){
        val id=sessionId ?: return@LaunchedEffect
        if(screen==RpsScreen.GAME&&game.phase=="RESULT"&&!game.readyForNext){delay(1000);try{game=rpsLiveApi.next(auth,id)}catch(e:CancellationException){throw e}catch(e:Exception){error=rpsErrorMessage(e)}}
    }

    Column(Modifier.fillMaxSize().background(ChallengeBg).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            if(screen==RpsScreen.INFO){Text("‹",color=ChallengeWhite,fontSize=36.sp,modifier=Modifier.clickable{onBack()})}
            else if(screen==RpsScreen.SEARCH){Text("‹",color=ChallengeWhite,fontSize=36.sp,modifier=Modifier.clickable{if(!busy)scope.launch{busy=true;try{rpsLiveApi.cancelQueue(auth)}catch(_:Exception){};screen=RpsScreen.INFO;sessionId=null;busy=false}})}
            else if(screen==RpsScreen.ERROR){Text("‹",color=ChallengeWhite,fontSize=36.sp,modifier=Modifier.clickable{screen=RpsScreen.INFO;sessionId=null})}
            Spacer(Modifier.weight(1f));if(screen!=RpsScreen.INFO)Text("حجر ورقة مقص",color=ChallengeWhite,fontWeight=FontWeight.Bold,fontSize=20.sp)
        }
        Spacer(Modifier.weight(1f))
        when(screen){
            RpsScreen.INFO->Info{screen=RpsScreen.SEARCH}
            RpsScreen.SEARCH->Search()
            RpsScreen.ERROR->{Text("⚠️",fontSize=62.sp);Text("تشخيص بدء المواجهة",color=ChallengeMuted,fontSize=14.sp);Spacer(Modifier.height(8.dp));Text(error,color=ChallengeWhite,fontSize=17.sp,textAlign=TextAlign.Center);Spacer(Modifier.height(20.dp));Button(onClick={screen=RpsScreen.SEARCH},colors=ButtonDefaults.buttonColors(containerColor=ChallengePurple)){Text("إعادة المحاولة")}}
            RpsScreen.GAME->{val id=sessionId!!;when(game.phase){"PLAY"->LivePlay(game){move->if(!busy)scope.launch{busy=true;try{game=rpsLiveApi.move(auth,id,RpsMoveRequest(move))}catch(e:CancellationException){throw e}catch(e:Exception){error=rpsErrorMessage(e)};busy=false}};"WAITING"->WaitChoice(game.myMove);"RESULT"->LiveResult(game);"FINISHED"->LiveFinished(game,{scope.launch{busy=true;try{game=rpsLiveApi.rematch(auth,id)}catch(e:CancellationException){throw e}catch(e:Exception){error=rpsErrorMessage(e)};busy=false}},{onArenaChanged(false);onBack()});else->CircularProgressIndicator(color=ChallengePurple)}}
        }
        if(error.isNotBlank()&&screen==RpsScreen.GAME){Spacer(Modifier.height(12.dp));Text(error,color=Color(0xFFFF9A9A),fontSize=12.sp,textAlign=TextAlign.Center)}
        Spacer(Modifier.weight(1f))
    }
}

@Composable private fun Info(start:()->Unit){Text("✊  ✋  ✌️",fontSize=54.sp);Text("حجر ورقة مقص",color=ChallengeWhite,fontSize=30.sp,fontWeight=FontWeight.Black);Text("اختر حركتك سرًا واكشف النتيجة مع خصمك",color=ChallengeMuted,textAlign=TextAlign.Center);Spacer(Modifier.height(24.dp));Surface(shape=RoundedCornerShape(18.dp),color=ChallengeCard){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("🎯  وضع 1 ضد 1",color=ChallengeWhite);Text("🔟  المباراة 10 جولات كاملة",color=ChallengeWhite);Text("⭐  كل فوز = نقطة، والتعادل بدون نقطة",color=ChallengeWhite);Text("🏆  الأعلى نقاطًا بعد الجولة العاشرة يفوز",color=ChallengeWhite)}};Spacer(Modifier.height(30.dp));Button(start,Modifier.fillMaxWidth().height(55.dp),colors=ButtonDefaults.buttonColors(containerColor=ChallengePurple)){Text("ابدأ البحث عن خصم",fontWeight=FontWeight.Bold)}}
@Composable private fun Search(){Text("جارٍ البحث عن خصم...",color=ChallengeWhite,fontSize=27.sp,fontWeight=FontWeight.Black);Text("يمكنك الرجوع وإلغاء البحث قبل دخول المباراة",color=ChallengeMuted);Spacer(Modifier.height(30.dp));Text("👤     VS     👤",fontSize=48.sp);Spacer(Modifier.height(30.dp));CircularProgressIndicator(color=ChallengePurple)}
@Composable private fun Score(g:RpsStateDto){Text("الجولة ${g.round} من ${g.totalRounds}",color=ChallengeMuted);Spacer(Modifier.height(10.dp));Text("أنت   ${g.myScore}   ⚔️   ${g.opponentScore}   الخصم",color=ChallengeWhite,fontSize=20.sp,fontWeight=FontWeight.Bold)}
@Composable private fun LivePlay(g:RpsStateDto,choose:(String)->Unit){Score(g);Spacer(Modifier.height(35.dp));Text("اختر حركتك الآن!",color=ChallengeWhite,fontSize=24.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(22.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){listOf(Triple("✊","حجر","ROCK"),Triple("✋","ورقة","PAPER"),Triple("✌️","مقص","SCISSORS")).forEach{(emoji,name,move)->Card(Modifier.weight(1f).clickable{choose(move)},shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=ChallengeCard),border=BorderStroke(1.dp,Color(0xFF5635B5))){Column(Modifier.fillMaxWidth().padding(vertical=18.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(emoji,fontSize=38.sp);Text(name,color=ChallengeWhite,fontWeight=FontWeight.Bold)}}}}}
private fun moveEmoji(move:String?):String=when(move){"ROCK"->"✊";"PAPER"->"✋";"SCISSORS"->"✌️";else->"❔"}
@Composable private fun WaitChoice(move:String?){Text(moveEmoji(move),fontSize=75.sp);Text("تم اختيارك! ✓",color=ChallengeWhite,fontSize=27.sp,fontWeight=FontWeight.Black);Text("بانتظار اختيار الخصم...",color=ChallengeMuted);Spacer(Modifier.height(25.dp));CircularProgressIndicator(color=ChallengePurple)}
@Composable private fun LiveResult(g:RpsStateDto){Score(g);Spacer(Modifier.height(30.dp));Text("${moveEmoji(g.myMove)}   VS   ${moveEmoji(g.opponentMove)}",fontSize=58.sp);Spacer(Modifier.height(25.dp));val title=when(g.roundResult){"WIN"->"🏆 فزت بالجولة!";"LOSE"->"خسرت الجولة";else->"🤝 تعادل"};val accent=when(g.roundResult){"WIN"->Color(0xFF2DFFAA);"LOSE"->Color(0xFFFF7D7D);else->Color(0xFFFFD66B)};Surface(shape=RoundedCornerShape(22.dp),color=ChallengeCard){Column(Modifier.padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(title,color=accent,fontSize=24.sp,fontWeight=FontWeight.Black);Text("النقاط ${g.myScore} - ${g.opponentScore}",color=ChallengeWhite)}};Spacer(Modifier.height(18.dp));Text(if(g.readyForNext)"جارٍ بدء الجولة التالية..." else "النتيجة تظهر لثانية واحدة",color=ChallengeMuted)}
@Composable private fun LiveFinished(g:RpsStateDto,replay:()->Unit,back:()->Unit){val draw=g.myScore==g.opponentScore;val won=!draw&&g.myScore>g.opponentScore;Text(if(won)"👑" else if(draw)"🤝" else "⚔️",fontSize=78.sp);Text(if(won)"انتصار!" else if(draw)"تعادل!" else "انتهت المواجهة",color=if(won)Color(0xFFFFC928)else if(draw)Color(0xFFFFD66B)else ChallengeWhite,fontSize=38.sp,fontWeight=FontWeight.Black);Text("${g.myScore}  -  ${g.opponentScore}",color=ChallengeWhite,fontSize=34.sp,fontWeight=FontWeight.Bold);Text(if(won)"جمعت نقاطًا أكثر وفزت بالمواجهة" else if(draw)"انتهت الجولات العشر بالتعادل" else "جمع الخصم نقاطًا أكثر",color=ChallengeMuted);Spacer(Modifier.height(30.dp));Button(replay,Modifier.fillMaxWidth().height(54.dp),colors=ButtonDefaults.buttonColors(containerColor=ChallengePurple)){Text("إعادة التحدي",fontWeight=FontWeight.Bold)};OutlinedButton(back,Modifier.fillMaxWidth()){Text("العودة للتحديات",color=ChallengeWhite)}}
