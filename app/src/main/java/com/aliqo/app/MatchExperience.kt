package com.aliqo.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.socket.client.IO
import io.socket.emitter.Emitter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val MatchBg = Color(0xFF061126)
private val MatchCard = Color(0xFF101C34)
private val MatchWhite = Color(0xFFF7F9FF)
private val MatchMuted = Color(0xFFAEB8D1)
private val MatchPurple = Color(0xFF7C32F2)
private val MatchBlue = Color(0xFF168EF4)

@Composable
fun PremiumMatchExperience(auth: String, open: (ChatDto) -> Unit) {
    var match by remember { mutableStateOf(MatchStatusDto()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf(false) }
    val chosen = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    suspend fun refresh() { match = matchApi.matchStatus(auth) }
    LaunchedEffect(auth) { while (isActive) { try { refresh() } catch (_: Exception) {} ; delay(if (match.state == "WAITING") 5000 else 15000) } }
    DisposableEffect(auth) {
        val socket = IO.socket(BuildConfig.REALTIME_URL, IO.Options.builder().setAuth(mapOf("token" to auth.removePrefix("Bearer ").trim())).setReconnection(true).build())
        val listener = Emitter.Listener { scope.launch { try { refresh() } catch (_: Exception) {} } }
        socket.on("match:found", listener); socket.on("match:queue", listener); socket.on("match:cancelled", listener); socket.connect()
        onDispose { socket.off(); socket.disconnect(); socket.close() }
    }
    Box(Modifier.fillMaxSize().background(MatchBg)) {
        when {
            match.state == "WAITING" -> MatchSearching(match) { scope.launch { busy = true; try { match = matchApi.cancelMatch(auth); error = "" } catch (_: Exception) { error = "تعذر إلغاء البحث" }; busy = false } }
            match.state == "MATCHED" -> MatchFound(match, open) { scope.launch { match.sessionId?.let { try { matchApi.leaveMatch(auth, it); match = MatchStatusDto() } catch (_: Exception) { error = "تعذر مغادرة التطابق" } } } }
            interests -> InterestPicker(chosen, onBack = { interests = false }) { interests = false; scope.launch { busy = true; try { match = matchApi.matchQueue(auth, MatchQueueRequest("ONE_V_ONE")); error = "" } catch (_: Exception) { error = "تعذر بدء البحث" }; busy = false } }
            else -> MatchLanding(busy, error, { interests = true }, { scope.launch { busy = true; try { match = matchApi.matchQueue(auth, MatchQueueRequest("GROUP")); error = "" } catch (_: Exception) { error = "تعذر بدء البحث الجماعي" }; busy = false } }, { interests = true })
        }
    }
}

@Composable private fun MatchLanding(busy:Boolean,error:String,onOne:()->Unit,onGroup:()->Unit,onRandom:()->Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){ Text("⚡ التطابق",color=MatchWhite,fontSize=27.sp,fontWeight=FontWeight.Black);Spacer(Modifier.weight(1f));Text("ⓘ",color=MatchWhite,fontSize=22.sp) }
        Text("اختر الطريقة المناسبة لك",color=MatchWhite,fontSize=21.sp,fontWeight=FontWeight.Bold)
        Text("تعرّف على أشخاص جدد بطريقتك المفضلة",color=MatchMuted,fontSize=14.sp)
        MatchChoice("⚔️","1 ضد 1","محادثة مباشرة مع شخص واحد",Brush.linearGradient(listOf(Color(0xFF4D2A9A),Color(0xFF18245A))),!busy,onOne)
        MatchChoice("👥","جماعي","تطابق جماعي من 5 إلى 10 أشخاص",Brush.linearGradient(listOf(Color(0xFF075EAA),Color(0xFF11274E))),!busy,onGroup)
        MatchChoice("🎲","عشوائي","دعنا نختار لك تجربة بشكل عشوائي",Brush.linearGradient(listOf(Color(0xFF7225A8),Color(0xFF122D5A))),!busy,onRandom)
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF25164E)),shape=RoundedCornerShape(20.dp)){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text("💡",fontSize=28.sp);Spacer(Modifier.width(12.dp));Column{Text("نصيحة لتطابق أفضل",color=MatchWhite,fontWeight=FontWeight.Bold);Text("اختر اهتماماتك لزيادة فرصة العثور على شخص مناسب",color=MatchMuted,fontSize=12.sp)}}}
        if(error.isNotBlank()) Text(error,color=Color(0xFFFF8A9A),fontSize=13.sp)
    }
}

@Composable private fun MatchChoice(icon:String,title:String,subtitle:String,brush:Brush,enabled:Boolean,onClick:()->Unit){
    Card(Modifier.fillMaxWidth().clickable(enabled=enabled,onClick=onClick),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color.Transparent)){
        Row(Modifier.fillMaxWidth().height(92.dp).background(brush).padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(icon,fontSize=34.sp);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,color=MatchWhite,fontSize=20.sp,fontWeight=FontWeight.ExtraBold);Text(subtitle,color=MatchMuted,fontSize=13.sp)};Text("‹",color=MatchWhite,fontSize=30.sp)}
    }
}

@Composable private fun InterestPicker(chosen:MutableList<String>,onBack:()->Unit,onContinue:()->Unit){
    val items=listOf("🎮" to "الألعاب","🐱" to "الأنمي","🎬" to "الأفلام","🎵" to "الموسيقى","💻" to "التقنية","📚" to "الكتب","⚽" to "الرياضة","✈️" to "السفر","🍴" to "الطعام","🎨" to "الفن","📷" to "التصوير","•••" to "أخرى")
    Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text("‹",color=MatchWhite,fontSize=32.sp,modifier=Modifier.clickable(onClick=onBack));Spacer(Modifier.weight(1f));Text("🎯 اهتماماتك",color=MatchWhite,fontSize=25.sp,fontWeight=FontWeight.Black);Spacer(Modifier.weight(1f));Text("تخطي",color=MatchMuted,modifier=Modifier.clickable(onClick=onContinue))}
        Text("اختر ما يهمك لنجد لك تطابقًا أفضل",color=MatchMuted,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
        items.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){row.forEach{(icon,name)->val active=name in chosen;Surface(Modifier.weight(1f).height(82.dp).clickable{if(active)chosen.remove(name) else chosen.add(name)},shape=RoundedCornerShape(18.dp),color=if(active)Color(0xFF321B69) else MatchCard,border=BorderStroke(1.dp,if(active)Color(0xFFB24DFF) else Color(0xFF263650))){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center,modifier=Modifier.fillMaxSize()){Text(icon,fontSize=25.sp);Text(name,color=MatchWhite,fontSize=13.sp)}}}}
        }
        Spacer(Modifier.weight(1f));Button(onClick=onContinue,Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(27.dp),colors=ButtonDefaults.buttonColors(containerColor=MatchPurple)){Text(if(chosen.isEmpty())"متابعة" else "متابعة (${chosen.size})",fontWeight=FontWeight.Bold)}
    }
}

@Composable private fun MatchSearching(match:MatchStatusDto,onCancel:()->Unit){
    Column(Modifier.fillMaxSize().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        Box(Modifier.size(210.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF6D28D9),Color(0xFF171B4C),MatchBg))),contentAlignment=Alignment.Center){Box(Modifier.size(105.dp).clip(CircleShape).background(MatchPurple),contentAlignment=Alignment.Center){Text("⚡",fontSize=48.sp)}}
        Spacer(Modifier.height(28.dp));Text("جاري البحث عن تطابق",color=MatchWhite,fontSize=27.sp,fontWeight=FontWeight.Black);Spacer(Modifier.height(8.dp));Text(if(match.mode=="GROUP")"نجمع لك مجموعة مناسبة الآن" else "نبحث عن شخص متاح يناسب اختيارك",color=MatchMuted,textAlign=TextAlign.Center);Spacer(Modifier.height(28.dp));Surface(shape=RoundedCornerShape(20.dp),color=MatchCard){Text("جاري البحث… قد يستغرق ذلك بضع ثوانٍ",Modifier.padding(18.dp),color=MatchWhite)};Spacer(Modifier.height(20.dp));OutlinedButton(onClick=onCancel,Modifier.fillMaxWidth(),border=BorderStroke(1.dp,Color(0xFF53627D))){Text("إلغاء البحث",color=MatchWhite)}
    }
}

@Composable private fun MatchFound(match:MatchStatusDto,open:(ChatDto)->Unit,onLeave:()->Unit){
    val players=match.players
    Column(Modifier.fillMaxSize().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        Text("🎉",fontSize=42.sp);Text("وجدنا لك تطابقًا!",color=MatchWhite,fontSize=28.sp,fontWeight=FontWeight.Black);Text("ابدأ المحادثة وتعرّف على شخص جديد",color=MatchMuted);Spacer(Modifier.height(28.dp));Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)){MatchAvatar(players.getOrNull(0),"أنت");Text("💜",fontSize=30.sp);MatchAvatar(players.getOrNull(1),"تطابق")};Spacer(Modifier.height(28.dp));Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=MatchCard){Column(Modifier.padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("✨ جاهزين للتعارف",color=MatchWhite,fontWeight=FontWeight.Bold);Text("ابدأ الحديث واكتشف الاهتمامات المشتركة",color=MatchMuted,fontSize=13.sp)}};Spacer(Modifier.height(18.dp));Button(onClick={match.chat?.let(open)},enabled=match.chat!=null,modifier=Modifier.fillMaxWidth().height(54.dp),colors=ButtonDefaults.buttonColors(containerColor=MatchPurple)){Text("💬 ابدأ المحادثة",fontWeight=FontWeight.Bold)};TextButton(onClick=onLeave){Text("ابحث عن تطابق آخر",color=MatchMuted)}
    }
}

@Composable private fun MatchAvatar(user:UserDto?,fallback:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(86.dp).clip(CircleShape).background(Brush.linearGradient(listOf(MatchPurple,MatchBlue))),contentAlignment=Alignment.Center){Text((user?.profile?.displayName?.ifBlank{user.username}?:user?.username?:fallback).take(1).uppercase(),color=MatchWhite,fontSize=30.sp,fontWeight=FontWeight.Bold)};Spacer(Modifier.height(7.dp));Text(user?.profile?.displayName?.ifBlank{user.username}?:user?.username?:fallback,color=MatchWhite,fontWeight=FontWeight.Bold)}}
