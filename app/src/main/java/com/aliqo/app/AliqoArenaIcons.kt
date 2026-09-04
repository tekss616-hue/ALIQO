package com.aliqo.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AliqoIcon { HOME, FRIENDS, BELL, PROFILE, SWORDS, ROOMS, TROPHY, SPEED, PREDICT, COMPAT, DETECTIVE, NUMBER, PUZZLE, AVOID, GAMEPAD, ROCK, PAPER, SCISSORS, PLAYER }

@Composable fun AliqoArenaIcon(type:AliqoIcon,modifier:Modifier=Modifier,size:Dp=30.dp,active:Boolean=true){
 val violet=if(active) Color(0xFFA855F7) else Color(0xFF9BA8C7); val blue=if(active) Color(0xFF19B8FF) else Color(0xFF9BA8C7); val gold=Color(0xFFFFC44D); val white=Color(0xFFF5F2FF)
 Canvas(modifier.size(size)){
  val w=this.size.width; val h=this.size.height; val sw=w*.065f
  val glow=Brush.radialGradient(listOf(violet.copy(alpha=.38f),blue.copy(alpha=.12f),Color.Transparent),Offset(w*.5f,h*.5f),w*.58f)
  drawCircle(glow,w*.48f,Offset(w*.5f,h*.5f))
  fun ln(a:Offset,b:Offset,c:Color=violet,s:Float=sw){drawLine(c,a,b,s,StrokeCap.Round)}
  fun orb(x:Float,y:Float,r:Float,c1:Color=violet,c2:Color=blue){drawCircle(Brush.radialGradient(listOf(white,c1,c2),Offset(w*x-w*r*.3f,h*y-h*r*.3f),w*r*1.7f),w*r,Offset(w*x,h*y));drawCircle(white.copy(alpha=.45f),w*r,Offset(w*x,h*y),style=Stroke(sw*.35f))}
  when(type){
   AliqoIcon.HOME->{val p=Path().apply{moveTo(w*.13f,h*.5f);lineTo(w*.5f,h*.14f);lineTo(w*.87f,h*.5f);lineTo(w*.78f,h*.86f);lineTo(w*.22f,h*.86f);close()};drawPath(p,Brush.linearGradient(listOf(white,violet,blue)));drawRoundRect(Color(0xFF111B3A),Offset(w*.42f,h*.58f),Size(w*.16f,h*.28f),CornerRadius(w*.04f))}
   AliqoIcon.FRIENDS,AliqoIcon.ROOMS->{orb(.5f,.32f,.17f);orb(.23f,.43f,.115f,blue,violet);orb(.77f,.43f,.115f,violet,blue);drawArc(Brush.linearGradient(listOf(violet,blue)),195f,150f,false,Offset(w*.19f,h*.49f),Size(w*.62f,h*.38f),style=Stroke(sw*1.8f,cap=StrokeCap.Round))}
   AliqoIcon.BELL->{val p=Path().apply{moveTo(w*.2f,h*.69f);lineTo(w*.3f,h*.56f);lineTo(w*.3f,h*.38f);cubicTo(w*.3f,h*.12f,w*.7f,h*.12f,w*.7f,h*.38f);lineTo(w*.7f,h*.56f);lineTo(w*.8f,h*.69f);close()};drawPath(p,Brush.linearGradient(listOf(gold,Color(0xFFFF8A3D),violet)));drawCircle(gold,w*.075f,Offset(w*.5f,h*.79f));drawCircle(Color(0xFFFF2773),w*.075f,Offset(w*.78f,h*.19f))}
   AliqoIcon.PROFILE,AliqoIcon.PLAYER->{orb(.5f,.32f,.19f);drawArc(Brush.linearGradient(listOf(violet,blue)),200f,140f,false,Offset(w*.18f,h*.48f),Size(w*.64f,h*.38f),style=Stroke(sw*1.9f,cap=StrokeCap.Round));if(type==AliqoIcon.PROFILE)drawCircle(white.copy(alpha=.7f),w*.46f,Offset(w*.5f,h*.5f),style=Stroke(sw*.5f))}
   AliqoIcon.SWORDS->{ln(Offset(w*.18f,h*.13f),Offset(w*.78f,h*.76f),blue,sw*1.35f);ln(Offset(w*.82f,h*.13f),Offset(w*.22f,h*.76f),violet,sw*1.35f);ln(Offset(w*.13f,h*.68f),Offset(w*.31f,h*.86f),gold,sw);ln(Offset(w*.87f,h*.68f),Offset(w*.69f,h*.86f),gold,sw)}
   AliqoIcon.TROPHY->{drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFF0A3),gold,Color(0xFFFF8A32))),Offset(w*.3f,h*.14f),Size(w*.4f,h*.42f),CornerRadius(w*.06f));drawArc(gold,90f,180f,false,Offset(w*.09f,h*.2f),Size(w*.3f,h*.32f),style=Stroke(sw));drawArc(gold,270f,180f,false,Offset(w*.61f,h*.2f),Size(w*.3f,h*.32f),style=Stroke(sw));ln(Offset(w*.5f,h*.56f),Offset(w*.5f,h*.76f),gold);ln(Offset(w*.31f,h*.82f),Offset(w*.69f,h*.82f),violet,sw*1.3f)}
   AliqoIcon.SPEED->{drawCircle(Brush.radialGradient(listOf(Color(0xFF24105B),Color(0xFF071126))),w*.34f,Offset(w*.5f,h*.54f));drawCircle(violet,w*.34f,Offset(w*.5f,h*.54f),style=Stroke(sw));val p=Path().apply{moveTo(w*.56f,h*.25f);lineTo(w*.36f,h*.55f);lineTo(w*.5f,h*.55f);lineTo(w*.42f,h*.79f);lineTo(w*.68f,h*.45f);lineTo(w*.53f,h*.45f);close()};drawPath(p,Brush.linearGradient(listOf(gold,Color(0xFFFF6A3D))));ln(Offset(w*.39f,h*.12f),Offset(w*.61f,h*.12f),blue)}
   AliqoIcon.PREDICT->{orb(.42f,.42f,.18f);drawArc(violet,200f,135f,false,Offset(w*.13f,h*.56f),Size(w*.56f,h*.28f),style=Stroke(sw*1.3f));drawCircle(Color(0xFF08122B),w*.15f,Offset(w*.72f,h*.4f));drawCircle(gold,w*.15f,Offset(w*.72f,h*.4f),style=Stroke(sw));drawCircle(blue,w*.055f,Offset(w*.72f,h*.4f));ln(Offset(w*.81f,h*.51f),Offset(w*.9f,h*.63f),gold)}
   AliqoIcon.COMPAT->{orb(.32f,.38f,.14f);orb(.68f,.38f,.14f,blue,violet);val heart=Path().apply{moveTo(w*.5f,h*.8f);cubicTo(w*.18f,h*.6f,w*.28f,h*.48f,w*.5f,h*.61f);cubicTo(w*.72f,h*.48f,w*.82f,h*.6f,w*.5f,h*.8f)};drawPath(heart,Brush.linearGradient(listOf(violet,blue)))}
   AliqoIcon.DETECTIVE->{drawCircle(Brush.radialGradient(listOf(Color(0xFF38206D),Color(0xFF0B1530))),w*.24f,Offset(w*.5f,h*.5f));drawCircle(violet,w*.24f,Offset(w*.5f,h*.5f),style=Stroke(sw));ln(Offset(w*.65f,h*.66f),Offset(w*.84f,h*.84f),blue,sw*1.25f);ln(Offset(w*.23f,h*.29f),Offset(w*.77f,h*.29f),gold,sw);ln(Offset(w*.36f,h*.15f),Offset(w*.64f,h*.15f),violet,sw*1.3f)}
   AliqoIcon.NUMBER->{drawRoundRect(Brush.linearGradient(listOf(violet,blue)),Offset(w*.14f,h*.14f),Size(w*.72f,h*.72f),CornerRadius(w*.11f));drawRoundRect(Color(0xFF10204A),Offset(w*.2f,h*.2f),Size(w*.6f,h*.6f),CornerRadius(w*.07f));ln(Offset(w*.5f,h*.21f),Offset(w*.5f,h*.79f),blue,sw*.55f);ln(Offset(w*.21f,h*.5f),Offset(w*.79f,h*.5f),blue,sw*.55f);drawContext.canvas.nativeCanvas.apply{} }
   AliqoIcon.PUZZLE->{drawRoundRect(Brush.linearGradient(listOf(violet,blue)),Offset(w*.17f,h*.18f),Size(w*.66f,h*.64f),CornerRadius(w*.1f));drawCircle(Color(0xFF071126),w*.11f,Offset(w*.5f,h*.18f));drawCircle(gold,w*.085f,Offset(w*.83f,h*.5f));drawCircle(white.copy(alpha=.35f),w*.25f,Offset(w*.36f,h*.36f))}
   AliqoIcon.AVOID->{val p=Path().apply{moveTo(w*.16f,h*.31f);lineTo(w*.4f,h*.17f);lineTo(w*.59f,h*.3f);lineTo(w*.83f,h*.2f);lineTo(w*.77f,h*.78f);lineTo(w*.23f,h*.78f);close()};drawPath(p,Brush.linearGradient(listOf(violet,blue)));ln(Offset(w*.34f,h*.43f),Offset(w*.66f,h*.65f),gold,sw);ln(Offset(w*.66f,h*.43f),Offset(w*.34f,h*.65f),white,sw)}
   AliqoIcon.GAMEPAD->{drawRoundRect(Brush.linearGradient(listOf(Color(0xFF43207B),violet,blue)),Offset(w*.1f,h*.27f),Size(w*.8f,h*.5f),CornerRadius(w*.17f));drawRoundRect(Color(0xFF111B3A),Offset(w*.16f,h*.33f),Size(w*.68f,h*.38f),CornerRadius(w*.12f));ln(Offset(w*.27f,h*.52f),Offset(w*.47f,h*.52f),blue,sw*1.2f);ln(Offset(w*.37f,h*.42f),Offset(w*.37f,h*.62f),blue,sw*1.2f);drawCircle(gold,w*.065f,Offset(w*.67f,h*.46f));drawCircle(Color(0xFFFF72D2),w*.065f,Offset(w*.77f,h*.57f))}
   AliqoIcon.ROCK->{val p=Path().apply{moveTo(w*.16f,h*.69f);lineTo(w*.25f,h*.34f);lineTo(w*.42f,h*.16f);lineTo(w*.65f,h*.22f);lineTo(w*.84f,h*.45f);lineTo(w*.77f,h*.76f);lineTo(w*.54f,h*.86f);lineTo(w*.27f,h*.8f);close()};drawPath(p,Brush.linearGradient(listOf(white,Color(0xFFB7A9D9),violet)));ln(Offset(w*.32f,h*.52f),Offset(w*.64f,h*.43f),blue,sw*.55f)}
   AliqoIcon.PAPER->{drawRoundRect(Brush.linearGradient(listOf(white,blue,violet)),Offset(w*.2f,h*.11f),Size(w*.6f,h*.78f),CornerRadius(w*.08f));drawRoundRect(Color(0xFF111B3A),Offset(w*.28f,h*.22f),Size(w*.44f,h*.56f),CornerRadius(w*.04f));ln(Offset(w*.35f,h*.38f),Offset(w*.65f,h*.38f),blue,sw*.55f);ln(Offset(w*.35f,h*.53f),Offset(w*.65f,h*.53f),violet,sw*.55f);ln(Offset(w*.35f,h*.68f),Offset(w*.58f,h*.68f),gold,sw*.55f)}
   AliqoIcon.SCISSORS->{drawCircle(violet,w*.14f,Offset(w*.3f,h*.72f));drawCircle(blue,w*.14f,Offset(w*.7f,h*.72f));drawCircle(Color(0xFF071126),w*.075f,Offset(w*.3f,h*.72f));drawCircle(Color(0xFF071126),w*.075f,Offset(w*.7f,h*.72f));ln(Offset(w*.39f,h*.61f),Offset(w*.76f,h*.14f),white,sw);ln(Offset(w*.61f,h*.61f),Offset(w*.24f,h*.14f),blue,sw)}
  }
 }
}