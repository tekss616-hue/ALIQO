package com.aliqo.app

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val PROFILE_THEME_DEFAULT="DEFAULT"
const val PROFILE_THEME_ROYAL_GOLD="ROYAL_GOLD"

object ProfileThemePrefs{
    private const val PREFS="aliqo_profile_store"
    private const val KEY_EQUIPPED="equipped_theme"
    fun equipped(context:Context):String=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_EQUIPPED,PROFILE_THEME_DEFAULT)?:PROFILE_THEME_DEFAULT
    fun setEquipped(context:Context,theme:String){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_EQUIPPED,theme).apply()}
}

private val StoreBg=Color(0xFF061126)
private val StoreCard=Color(0xFF0D1C36)
private val StoreMuted=Color(0xFFAAB5D2)
private val StorePurple=Color(0xFF7C2CFF)
private val RoyalGold=Color(0xFFFFC857)
private val RoyalGoldDeep=Color(0xFFB76A00)

@Composable
fun ProfileStoreScreen(auth:String,onThemeChanged:(String)->Unit){
    var preview by remember{mutableStateOf(false)}
    if(preview){
        Box(Modifier.fillMaxSize()){
            PlayerProfileScreen(auth=auth,isMine=true,onBack={preview=false},themeOverride=PROFILE_THEME_ROYAL_GOLD,previewMode=true)
        }
        return
    }
    Column(Modifier.fillMaxSize().background(StoreBg).padding(horizontal=14.dp,vertical=10.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            NeonStoreIcon(38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)){Text("المتجر",color=Color.White,fontSize=29.sp,fontWeight=FontWeight.Black);Text("خصص ملفك بطريقتك",color=StoreMuted,fontSize=12.sp)}
        }
        Spacer(Modifier.height(18.dp))
        Text("الحزم المميزة",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        RoyalGoldStoreCard(onPreview={preview=true})
        Spacer(Modifier.height(18.dp))
        Text("قطع منفصلة",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("قريبًا: إطارات، خلفيات، رموز للصورة، أيقونات إحصائيات وتأثيرات مستقلة.",color=StoreMuted,fontSize=13.sp,lineHeight=20.sp)
    }
}

@Composable
private fun RoyalGoldStoreCard(onPreview:()->Unit){
    Card(
        colors=CardDefaults.cardColors(containerColor=StoreCard),
        shape=RoundedCornerShape(24.dp),
        border=androidx.compose.foundation.BorderStroke(1.5.dp,RoyalGold.copy(alpha=.75f)),
        modifier=Modifier.fillMaxWidth()
    ){
        Column(Modifier.fillMaxWidth().padding(16.dp)){
            Box(
                Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(19.dp)).background(
                    Brush.radialGradient(listOf(Color(0xFF4A2B05),Color(0xFF171008),Color(0xFF070A12)))
                ).border(1.dp,RoyalGold.copy(alpha=.55f),RoundedCornerShape(19.dp)),
                contentAlignment=Alignment.Center
            ){
                RoyalGoldEmblem(92.dp)
                Text("ROYAL GOLD",color=RoyalGold,fontSize=12.sp,fontWeight=FontWeight.Black,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=8.dp))
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text("الحزمة الملكية الذهبية",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Black);Text("حزمة ملف شخصي كاملة • مدفوعة",color=RoyalGold,fontSize=12.sp,fontWeight=FontWeight.Bold)}
                Surface(color=Color(0xFF2A1C08),shape=RoundedCornerShape(10.dp),border=androidx.compose.foundation.BorderStroke(1.dp,RoyalGold.copy(alpha=.6f))){Text("PREMIUM",color=RoyalGold,fontSize=10.sp,fontWeight=FontWeight.Black,modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp))}
            }
            Spacer(Modifier.height(9.dp))
            Text("تغيّر خلفية الملف، الإطار والشعار، أيقونات الفوز والخسارة والمباريات، النجمة، أشرطة التقدم، وأيقونة الملف في الشريط السفلي فقط.",color=StoreMuted,fontSize=12.sp,lineHeight=18.sp)
            Spacer(Modifier.height(13.dp))
            Button(onClick=onPreview,modifier=Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=RoyalGoldDeep,contentColor=Color.White)){Text("معاينة على ملفي",fontWeight=FontWeight.Black)}
            Spacer(Modifier.height(7.dp))
            Text("الشراء سيُربط بمنتج Google Play قبل الإطلاق؛ المعاينة لا تجهز الحزمة ولا تخصم أي مبلغ.",color=StoreMuted,fontSize=10.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun NeonStoreIcon(size:Dp,modifier:Modifier=Modifier){
    Canvas(modifier.size(size)){
        val w=this.size.width;val h=this.size.height
        val cyan=Color(0xFF00D8FF);val magenta=Color(0xFFFF38E8)
        drawRoundRect(brush=Brush.linearGradient(listOf(cyan,magenta)),topLeft=Offset(w*.16f,h*.34f),size=androidx.compose.ui.geometry.Size(w*.68f,h*.53f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.11f,w*.11f))
        drawRoundRect(color=Color(0xFF071126),topLeft=Offset(w*.205f,h*.385f),size=androidx.compose.ui.geometry.Size(w*.59f,h*.44f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.08f,w*.08f))
        drawArc(brush=Brush.linearGradient(listOf(cyan,magenta)),startAngle=190f,sweepAngle=160f,useCenter=false,topLeft=Offset(w*.28f,h*.08f),size=androidx.compose.ui.geometry.Size(w*.44f,h*.52f),style=androidx.compose.ui.graphics.drawscope.Stroke(width=w*.085f))
        val p=Path().apply{moveTo(w*.5f,h*.47f);lineTo(w*.55f,h*.58f);lineTo(w*.67f,h*.60f);lineTo(w*.58f,h*.68f);lineTo(w*.60f,h*.80f);lineTo(w*.5f,h*.74f);lineTo(w*.40f,h*.80f);lineTo(w*.42f,h*.68f);lineTo(w*.33f,h*.60f);lineTo(w*.45f,h*.58f);close()}
        drawPath(p,brush=Brush.linearGradient(listOf(RoyalGold,Color(0xFFFF8A00))))
    }
}

@Composable
fun RoyalGoldProfileNavIcon(size:Dp=31.dp,modifier:Modifier=Modifier){
    Canvas(modifier.size(size)){
        val gold=Color(0xFFFFC857);val deep=Color(0xFFB76A00)
        drawCircle(brush=Brush.radialGradient(listOf(gold,deep)),radius=this.size.minDimension*.34f,center=Offset(this.size.width*.5f,this.size.height*.42f))
        drawCircle(color=Color(0xFF0B101B),radius=this.size.minDimension*.24f,center=Offset(this.size.width*.5f,this.size.height*.42f))
        val p=Path().apply{moveTo(this@Canvas.size.width*.5f,this@Canvas.size.height*.23f);lineTo(this@Canvas.size.width*.62f,this@Canvas.size.height*.42f);lineTo(this@Canvas.size.width*.54f,this@Canvas.size.height*.39f);lineTo(this@Canvas.size.width*.5f,this@Canvas.size.height*.53f);lineTo(this@Canvas.size.width*.46f,this@Canvas.size.height*.39f);lineTo(this@Canvas.size.width*.38f,this@Canvas.size.height*.42f);close()}
        drawPath(p,color=gold)
        drawArc(color=gold,startAngle=200f,sweepAngle=140f,useCenter=false,topLeft=Offset(this.size.width*.17f,this.size.height*.52f),size=androidx.compose.ui.geometry.Size(this.size.width*.66f,this.size.height*.34f),style=androidx.compose.ui.graphics.drawscope.Stroke(width=this.size.width*.085f))
    }
}

@Composable
fun RoyalGoldEmblem(size:Dp){
    Box(Modifier.size(size),contentAlignment=Alignment.Center){
        Canvas(Modifier.fillMaxSize()){
            val gold=Color(0xFFFFC857);val bright=Color(0xFFFFE09A);val deep=Color(0xFF9D5600)
            drawCircle(brush=Brush.radialGradient(listOf(Color(0xFF4A2B05),Color(0xFF080A0F))),radius=this.size.minDimension*.48f)
            drawCircle(color=deep,radius=this.size.minDimension*.43f,style=androidx.compose.ui.graphics.drawscope.Stroke(width=this.size.minDimension*.045f))
            drawCircle(color=gold,radius=this.size.minDimension*.39f,style=androidx.compose.ui.graphics.drawscope.Stroke(width=this.size.minDimension*.018f))
            val p=Path().apply{moveTo(this@Canvas.size.width*.50f,this@Canvas.size.height*.22f);lineTo(this@Canvas.size.width*.69f,this@Canvas.size.height*.66f);lineTo(this@Canvas.size.width*.58f,this@Canvas.size.height*.59f);lineTo(this@Canvas.size.width*.50f,this@Canvas.size.height*.76f);lineTo(this@Canvas.size.width*.42f,this@Canvas.size.height*.59f);lineTo(this@Canvas.size.width*.31f,this@Canvas.size.height*.66f);close()}
            drawPath(p,brush=Brush.linearGradient(listOf(bright,gold,deep)))
            val crown=Path().apply{moveTo(this@Canvas.size.width*.34f,this@Canvas.size.height*.20f);lineTo(this@Canvas.size.width*.40f,this@Canvas.size.height*.10f);lineTo(this@Canvas.size.width*.50f,this@Canvas.size.height*.19f);lineTo(this@Canvas.size.width*.60f,this@Canvas.size.height*.10f);lineTo(this@Canvas.size.width*.66f,this@Canvas.size.height*.20f);lineTo(this@Canvas.size.width*.62f,this@Canvas.size.height*.26f);lineTo(this@Canvas.size.width*.38f,this@Canvas.size.height*.26f);close()}
            drawPath(crown,brush=Brush.verticalGradient(listOf(bright,gold)))
        }
    }
}
