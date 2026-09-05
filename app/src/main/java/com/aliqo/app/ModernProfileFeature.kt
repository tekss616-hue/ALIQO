package com.aliqo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    var editing by remember{mutableStateOf(false)}
    if(!editing){
        PlayerProfileScreen(auth=auth,isMine=true,onEdit={editing=true})
    }else{
        EditOwnProfileScreen(auth,me,onProfileUpdated,refreshToken,onSignedOut,onBack={editing=false})
    }
}

@Composable
private fun EditOwnProfileScreen(auth:String,me:UserDto?,onProfileUpdated:()->Unit,refreshToken:String,onSignedOut:()->Unit,onBack:()->Unit){
    val originalName=me?.profile?.displayName?.trim().orEmpty()
    val originalBio=me?.profile?.bio?.trim().orEmpty()
    var displayName by remember(me?.profile?.displayName){mutableStateOf(me?.profile?.displayName?:"")}
    var bio by remember(me?.profile?.bio){mutableStateOf(me?.profile?.bio?:"")}
    var status by remember{mutableStateOf("")}
    var saved by remember{mutableStateOf(false)}
    var saving by remember{mutableStateOf(false)}
    var confirmDelete by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val hasChanges=displayName.trim()!=originalName||bio.trim()!=originalBio

    if(confirmDelete)AlertDialog(containerColor=ProfileCard,onDismissRequest={confirmDelete=false},title={Text("حذف الحساب؟",color=Color.White)},text={Text("سيتم تعطيل الحساب وإلغاء جلسات الدخول نهائيًا.",color=ProfileMuted)},confirmButton={TextButton(onClick={confirmDelete=false;scope.launch{try{modernProfileApi.deleteAccount(auth);onSignedOut()}catch(_:Exception){status="تعذر حذف الحساب"}}}){Text("حذف نهائي",color=Color(0xFFFF6075))}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("إلغاء",color=ProfileMuted)}})

    Column(Modifier.fillMaxSize().background(ProfileBg).verticalScroll(rememberScrollState()).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
            TextButton(onClick=onBack){Text("‹",color=Color.White,fontSize=34.sp)}
            Text("تعديل الملف الشخصي",modifier=Modifier.weight(1f),color=Color.White,fontSize=27.sp,fontWeight=FontWeight.ExtraBold)
        }
        ProfileField(value=displayName,onValueChange={displayName=it.take(60);saved=false;status=""},label="الاسم الظاهر",singleLine=true)
        ProfileField(value=bio,onValueChange={bio=it.take(280);saved=false;status=""},label="نبذة مختصرة (${bio.length}/280)",singleLine=false)
        if(!hasChanges)Card(colors=CardDefaults.cardColors(containerColor=ProfileCard),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){Text("لا توجد تغييرات جديدة للحفظ",color=ProfileMuted,modifier=Modifier.padding(14.dp),fontSize=13.sp)}
        Button(onClick={
            if(!hasChanges||saving)return@Button
            scope.launch{
                saving=true;saved=false;status=""
                try{
                    modernProfileApi.updateProfile(auth,UpdateProfileRequest(displayName.trim(),bio.trim(),me?.profile?.avatarUrl))
                    onProfileUpdated();saved=true;delay(800);saved=false;onBack()
                }catch(_:Exception){status="تعذر حفظ التغييرات"}
                saving=false
            }
        },enabled=displayName.isNotBlank()&&!saving,modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(20.dp),colors=ButtonDefaults.buttonColors(containerColor=ProfilePurple,contentColor=Color.White,disabledContainerColor=ProfilePurple,disabledContentColor=Color.White)){Text(if(saved)"✓ تم الحفظ" else "حفظ التغييرات",fontWeight=FontWeight.Bold,fontSize=16.sp)}
        Text("يظهر «تم الحفظ» فقط عندما تغيّر بيانات فعلية ويتم حفظها بنجاح.",color=ProfileMuted,fontSize=11.sp)
        HorizontalDivider(color=Color(0xFF17375F))
        OutlinedButton(onClick={scope.launch{try{if(refreshToken.isNotBlank())modernProfileApi.logout(RefreshRequest(refreshToken))}catch(_:Exception){};onSignedOut()}},modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(20.dp),border=ButtonDefaults.outlinedButtonBorder(enabled=true).copy(brush=Brush.linearGradient(listOf(ProfileMuted,ProfileMuted)))){Text("تسجيل الخروج",color=Color.White)}
        TextButton(onClick={confirmDelete=true},modifier=Modifier.fillMaxWidth()){Text("حذف الحساب",color=Color(0xFFFF6075))}
        if(status.isNotBlank())Text(status,color=Color(0xFFFF7A8A))
    }
}

@Composable private fun ProfileField(value:String,onValueChange:(String)->Unit,label:String,singleLine:Boolean){
    OutlinedTextField(value=value,onValueChange=onValueChange,modifier=Modifier.fillMaxWidth(),singleLine=singleLine,minLines=if(singleLine)1 else 3,label={Text(label)},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=ProfilePurple,unfocusedBorderColor=Color(0xFF334A70),focusedLabelColor=ProfileBlue,unfocusedLabelColor=ProfileMuted,cursorColor=ProfileBlue),shape=RoundedCornerShape(18.dp))
}
