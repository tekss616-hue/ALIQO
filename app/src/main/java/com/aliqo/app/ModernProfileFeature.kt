package com.aliqo.app

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

private interface ModernProfileApi{
    @PATCH("users/me/profile") suspend fun updateProfile(@Header("Authorization") auth:String,@Body request:UpdateProfileRequest):ProfileDto
    @POST("auth/logout") suspend fun logout(@Body request:RefreshRequest):OkResponse
    @DELETE("users/me") suspend fun deleteAccount(@Header("Authorization") auth:String):OkResponse
}
private val modernProfileApi:ModernProfileApi by lazy{val client=OkHttpClient.Builder().connectTimeout(75,TimeUnit.SECONDS).readTimeout(75,TimeUnit.SECONDS).build();Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(ModernProfileApi::class.java)}
private val ProfileBg=Color(0xFF071126)
private val ProfileCard=Color(0xFF0C1B36)
private val ProfileMuted=Color(0xFFAAB5D2)
private val ProfilePurple=Color(0xFF7C2CFF)
private val ProfileBlue=Color(0xFF22B8FF)

@Composable
fun ModernProfileScreen(auth:String,me:UserDto?,onProfileUpdated:()->Unit,refreshToken:String,onSignedOut:()->Unit){
    var displayName by remember(me?.profile?.displayName){mutableStateOf(me?.profile?.displayName?:"")}
    var bio by remember(me?.profile?.bio){mutableStateOf(me?.profile?.bio?:"")}
    var status by remember{mutableStateOf("")}
    var saved by remember{mutableStateOf(false)}
    var saving by remember{mutableStateOf(false)}
    var confirmDelete by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    if(confirmDelete)AlertDialog(containerColor=ProfileCard,onDismissRequest={confirmDelete=false},title={Text("حذف الحساب؟",color=Color.White)},text={Text("سيتم تعطيل الحساب وإلغاء جلسات الدخول نهائيًا.",color=ProfileMuted)},confirmButton={TextButton(onClick={confirmDelete=false;scope.launch{try{modernProfileApi.deleteAccount(auth);onSignedOut()}catch(_:Exception){status="تعذر حذف الحساب"}}}){Text("حذف نهائي",color=Color(0xFFFF6075))}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("إلغاء",color=ProfileMuted)}})

    Column(Modifier.fillMaxSize().background(ProfileBg).verticalScroll(rememberScrollState()).padding(bottom=28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("الملف الشخصي",modifier=Modifier.fillMaxWidth().padding(top=8.dp),color=Color.White,fontSize=34.sp,fontWeight=FontWeight.ExtraBold)
        Spacer(Modifier.height(2.dp))
        Box(contentAlignment=Alignment.BottomEnd){
            Box(Modifier.size(94.dp).clip(CircleShape).background(Brush.linearGradient(listOf(ProfilePurple.copy(alpha=.5f),ProfileBlue.copy(alpha=.35f)))).border(2.dp,ProfilePurple,CircleShape),contentAlignment=Alignment.Center){Text((me?.profile?.displayName?.ifBlank{me.username}?:me?.username?:"؟").take(1),color=Color.White,fontSize=34.sp,fontWeight=FontWeight.Bold)}
            Box(Modifier.size(30.dp).clip(CircleShape).background(ProfilePurple),contentAlignment=Alignment.Center){Text("✦",color=Color.White,fontSize=16.sp)}
        }
        Text("@${me?.username.orEmpty()}",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold)
        me?.email?.let{Text(it,color=ProfileMuted,fontSize=14.sp)}
        Spacer(Modifier.height(4.dp))
        ProfileField(value=displayName,onValueChange={displayName=it.take(60);saved=false},label="الاسم الظاهر",singleLine=true)
        ProfileField(value=bio,onValueChange={bio=it.take(280);saved=false},label="نبذة مختصرة (${bio.length}/280)",singleLine=false)
        Button(onClick={scope.launch{saving=true;saved=false;status="";try{modernProfileApi.updateProfile(auth,UpdateProfileRequest(displayName.trim(),bio.trim(),me?.profile?.avatarUrl));saved=true;onProfileUpdated();delay(800);saved=false}catch(_:Exception){status="تعذر حفظ التغييرات"};saving=false}},enabled=displayName.isNotBlank()&&!saving,modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(20.dp),colors=ButtonDefaults.buttonColors(containerColor=ProfilePurple)){Text(if(saved)"✓ تم الحفظ" else "حفظ التغييرات",fontWeight=FontWeight.Bold,fontSize=16.sp)}
        OutlinedButton(onClick={scope.launch{try{if(refreshToken.isNotBlank())modernProfileApi.logout(RefreshRequest(refreshToken))}catch(_:Exception){};onSignedOut()}},modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(20.dp),border=ButtonDefaults.outlinedButtonBorder(enabled=true).copy(brush=Brush.linearGradient(listOf(ProfileMuted,ProfileMuted)))){Text("تسجيل الخروج",color=Color.White)}
        TextButton(onClick={confirmDelete=true},modifier=Modifier.fillMaxWidth()){Text("حذف الحساب",color=Color(0xFFFF6075))}
        if(status.isNotBlank())Text(status,color=Color(0xFFFF7A8A))
    }
}

@Composable private fun ProfileField(value:String,onValueChange:(String)->Unit,label:String,singleLine:Boolean){
    OutlinedTextField(value=value,onValueChange=onValueChange,modifier=Modifier.fillMaxWidth(),singleLine=singleLine,minLines=if(singleLine)1 else 3,label={Text(label)},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=ProfilePurple,unfocusedBorderColor=Color(0xFF334A70),focusedLabelColor=ProfileBlue,unfocusedLabelColor=ProfileMuted,cursorColor=ProfileBlue),shape=RoundedCornerShape(18.dp))
}
