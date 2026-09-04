package com.aliqo.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AliqoIcon { HOME, FRIENDS, BELL, PROFILE, SWORDS, ROOMS, TROPHY, SPEED, PREDICT, COMPAT, DETECTIVE, NUMBER, PUZZLE, AVOID, GAMEPAD, ROCK, PAPER, SCISSORS, PLAYER }

@Composable fun AliqoArenaIcon(type:AliqoIcon,modifier:Modifier=Modifier,size:Dp=30.dp,active:Boolean=true){
 val primary=if(active)Color(0xFF8B5CFF)else Color(0xFFAAB5D2);val cyan=if(active)Color(0xFF35B9FF)else Color(0xFFAAB5D2);val gold=Color(0xFFFFC857)
 Canvas(modifier.size(size)){val w=this.size.width;val h=this.size.height;val sw=w*.09f
  fun line(a:Offset,b:Offset,c:Color=primary,s:Float=sw)=drawLine(c,a,b,s,StrokeCap.Round)
  when(type){
   AliqoIcon.HOME->{val p=Path().apply{moveTo(w*.16f,h*.48f);lineTo(w*.5f,h*.18f);lineTo(w*.84f,h*.48f);lineTo(w*.78f,h*.84f);lineTo(w*.22f,h*.84f);close()};drawPath(p,primary,style=Stroke(sw));line(Offset(w*.43f,h*.84f),Offset(w*.43f,h*.61f),cyan);line(Offset(w*.57f,h*.84f),Offset(w*.57f,h*.61f),cyan)}
   AliqoIcon.FRIENDS,AliqoIcon.ROOMS->{drawCircle(primary,w*.15f,Offset(w*.5f,h*.34f));drawCircle(cyan,w*.11f,Offset(w*.23f,h*.42f));drawCircle(cyan,w*.11f,Offset(w*.77f,h*.42f));drawArc(primary,200f,140f,false,Offset(w*.23f,h*.48f),Size(w*.54f,h*.38f),style=Stroke(sw));drawArc(cyan,205f,120f,false,Offset(w*.03f,h*.56f),Size(w*.38f,h*.27f),style=Stroke(sw*.8f));drawArc(cyan,215f,120f,false,Offset(w*.59f,h*.56f),Size(w*.38f,h*.27f),style=Stroke(sw*.8f))}
   AliqoIcon.BELL->{val p=Path().apply{moveTo(w*.25f,h*.66f);lineTo(w*.32f,h*.55f);lineTo(w*.32f,h*.38f);cubicTo(w*.32f,h*.16f,w*.68f,h*.16f,w*.68f,h*.38f);lineTo(w*.68f,h*.55f);lineTo(w*.75f,h*.66f);close()};drawPath(p,primary,style=Stroke(sw));drawCircle(gold,w*.06f,Offset(w*.5f,h*.76f));drawCircle(cyan,w*.055f,Offset(w*.72f,h*.23f))}
   AliqoIcon.PROFILE,AliqoIcon.PLAYER->{drawCircle(primary,w*.18f,Offset(w*.5f,h*.34f));drawArc(cyan,205f,130f,false,Offset(w*.2f,h*.5f),Size(w*.6f,h*.35f),style=Stroke(sw));if(type==AliqoIcon.PROFILE)drawCircle(primary,w*.43f,Offset(w*.5f,h*.5f),style=Stroke(sw*.65f))}
   AliqoIcon.SWORDS->{line(Offset(w*.2f,h*.18f),Offset(w*.78f,h*.8f),cyan);line(Offset(w*.8f,h*.18f),Offset(w*.22f,h*.8f),primary);line(Offset(w*.16f,h*.69f),Offset(w*.31f,h*.84f),gold,sw*.8f);line(Offset(w*.84f,h*.69f),Offset(w*.69f,h*.84f),gold,sw*.8f)}
   AliqoIcon.TROPHY->{drawRect(gold,Offset(w*.31f,h*.18f),Size(w*.38f,h*.38f),style=Stroke(sw));drawArc(gold,90f,180f,false,Offset(w*.12f,h*.22f),Size(w*.28f,h*.3f),style=Stroke(sw*.75f));drawArc(gold,270f,180f,false,Offset(w*.6f,h*.22f),Size(w*.28f,h*.3f),style=Stroke(sw*.75f));line(Offset(w*.5f,h*.56f),Offset(w*.5f,h*.76f),primary);line(Offset(w*.34f,h*.8f),Offset(w*.66f,h*.8f),primary)}
   AliqoIcon.SPEED->{drawCircle(primary,w*.3f,Offset(w*.5f,h*.52f),style=Stroke(sw));line(Offset(w*.5f,h*.52f),Offset(w*.7f,h*.32f),cyan);line(Offset(w*.37f,h*.15f),Offset(w*.63f,h*.15f),primary);line(Offset(w*.5f,h*.15f),Offset(w*.5f,h*.23f),primary)}
   AliqoIcon.PREDICT->{drawCircle(primary,w*.2f,Offset(w*.42f,h*.42f));drawArc(cyan,205f,130f,false,Offset(w*.12f,h*.56f),Size(w*.6f,h*.3f),style=Stroke(sw));drawCircle(gold,w*.13f,Offset(w*.72f,h*.38f),style=Stroke(sw*.75f));line(Offset(w*.78f,h*.49f),Offset(w*.88f,h*.62f),gold,sw*.75f)}
   AliqoIcon.COMPAT->{drawCircle(primary,w*.12f,Offset(w*.32f,h*.38f));drawCircle(cyan,w*.12f,Offset(w*.68f,h*.38f));line(Offset(w*.39f,h*.52f),Offset(w*.61f,h*.52f),gold);drawArc(primary,200f,120f,false,Offset(w*.13f,h*.53f),Size(w*.38f,h*.27f),style=Stroke(sw*.75f));drawArc(cyan,220f,120f,false,Offset(w*.49f,h*.53f),Size(w*.38f,h*.27f),style=Stroke(sw*.75f))}
   AliqoIcon.DETECTIVE->{drawCircle(primary,w*.22f,Offset(w*.5f,h*.5f),style=Stroke(sw));line(Offset(w*.65f,h*.65f),Offset(w*.82f,h*.82f),cyan);line(Offset(w*.27f,h*.3f),Offset(w*.73f,h*.3f),gold);line(Offset(w*.36f,h*.18f),Offset(w*.64f,h*.18f),gold)}
   AliqoIcon.NUMBER->{drawRoundRect(primary,Offset(w*.16f,h*.16f),Size(w*.68f,h*.68f),CornerRadius(w*.1f),style=Stroke(sw));line(Offset(w*.39f,h*.28f),Offset(w*.39f,h*.72f),cyan,sw*.65f);line(Offset(w*.61f,h*.28f),Offset(w*.61f,h*.72f),cyan,sw*.65f);line(Offset(w*.28f,h*.42f),Offset(w*.72f,h*.42f),cyan,sw*.65f);line(Offset(w*.28f,h*.59f),Offset(w*.72f,h*.59f),cyan,sw*.65f)}
   AliqoIcon.PUZZLE->{drawRect(primary,Offset(w*.2f,h*.2f),Size(w*.6f,h*.6f),style=Stroke(sw));drawCircle(cyan,w*.1f,Offset(w*.5f,h*.2f),style=Stroke(sw*.7f));drawCircle(gold,w*.1f,Offset(w*.8f,h*.5f),style=Stroke(sw*.7f))}
   AliqoIcon.AVOID->{val p=Path().apply{moveTo(w*.22f,h*.3f);lineTo(w*.43f,h*.2f);lineTo(w*.62f,h*.31f);lineTo(w*.78f,h*.24f);lineTo(w*.74f,h*.75f);lineTo(w*.26f,h*.75f);close()};drawPath(p,primary,style=Stroke(sw));line(Offset(w*.36f,h*.43f),Offset(w*.64f,h*.62f),gold);line(Offset(w*.64f,h*.43f),Offset(w*.36f,h*.62f),gold)}
   AliqoIcon.GAMEPAD->{drawRoundRect(primary,Offset(w*.12f,h*.28f),Size(w*.76f,h*.48f),CornerRadius(w*.16f),style=Stroke(sw));line(Offset(w*.3f,h*.52f),Offset(w*.48f,h*.52f),cyan);line(Offset(w*.39f,h*.43f),Offset(w*.39f,h*.61f),cyan);drawCircle(gold,w*.055f,Offset(w*.68f,h*.47f));drawCircle(gold,w*.055f,Offset(w*.76f,h*.57f))}
   AliqoIcon.ROCK->{val p=Path().apply{moveTo(w*.2f,h*.68f);lineTo(w*.27f,h*.35f);lineTo(w*.42f,h*.2f);lineTo(w*.62f,h*.24f);lineTo(w*.79f,h*.44f);lineTo(w*.75f,h*.72f);lineTo(w*.55f,h*.82f);lineTo(w*.3f,h*.78f);close()};drawPath(p,primary,style=Stroke(sw));line(Offset(w*.34f,h*.52f),Offset(w*.62f,h*.45f),cyan,sw*.6f)}
   AliqoIcon.PAPER->{drawRoundRect(primary,Offset(w*.23f,h*.14f),Size(w*.54f,h*.72f),CornerRadius(w*.06f),style=Stroke(sw));line(Offset(w*.34f,h*.36f),Offset(w*.66f,h*.36f),cyan,sw*.55f);line(Offset(w*.34f,h*.51f),Offset(w*.66f,h*.51f),cyan,sw*.55f);line(Offset(w*.34f,h*.66f),Offset(w*.57f,h*.66f),gold,sw*.55f)}
   AliqoIcon.SCISSORS->{drawCircle(primary,w*.13f,Offset(w*.32f,h*.7f),style=Stroke(sw));drawCircle(cyan,w*.13f,Offset(w*.68f,h*.7f),style=Stroke(sw));line(Offset(w*.4f,h*.6f),Offset(w*.72f,h*.18f),primary);line(Offset(w*.6f,h*.6f),Offset(w*.28f,h*.18f),cyan)}
  }
 }
}