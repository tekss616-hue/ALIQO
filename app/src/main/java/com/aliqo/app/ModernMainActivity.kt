package com.aliqo.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private val modernHttpClient by lazy {
    OkHttpClient.Builder().connectTimeout(75, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS).writeTimeout(75, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
}
private val modernApi: AliqoApi by lazy { Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(modernHttpClient).addConverterFactory(GsonConverterFactory.create()).build().create(AliqoApi::class.java) }
class ModernMainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState);setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { ModernAliqoApp() } } } } }
@Composable private fun ModernAliqoApp() { val context=androidx.compose.ui.platform.LocalContext.current;val prefs=remember{context.getSharedPreferences("aliqo_session",Context.MODE_PRIVATE)};var accessToken by remember{mutableStateOf(prefs.getString("accessToken","")?:"")};var refreshToken by remember{mutableStateOf(prefs.getString("refreshToken","")?:"")};fun saveTokens(access:String,refresh:String){accessToken=access;refreshToken=refresh;prefs.edit().putString("accessToken",access).putString("refreshToken",refresh).apply()};fun signOutLocal(){accessToken="";refreshToken="";prefs.edit().clear().apply()};if(accessToken.isBlank())FastAuthScreen{saveTokens(it.accessToken,it.refreshToken)}else ModernMainShell(accessToken,refreshToken,::saveTokens,::signOutLocal)}
@Composable private fun ModernMainShell(accessToken:String,refreshToken:String,onTokensUpdated:(String,String)->Unit,onSignedOut:()->Unit){
 var currentAccess by remember(accessToken){mutableStateOf(accessToken)};var currentRefresh by remember(refreshToken){mutableStateOf(refreshToken)};var tab by remember{mutableStateOf("home")};var challengeArena by remember{mutableStateOf(false)};var me by remember{mutableStateOf<UserDto?>(null)};var onlineFriends by remember{mutableStateOf<List<UserDto>>(emptyList())};var status by remember{mutableStateOf("جارٍ تحميل حسابك...")};var unread by remember{mutableStateOf(0)};var openedRoomChat by remember{mutableStateOf<ChatDto?>(null)};var openedRoomId by remember{mutableStateOf<String?>(null)};var openedRoomCreator by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 suspend fun refreshSession():Boolean=try{if(currentRefresh.isBlank())false else{val t=modernApi.refresh(RefreshRequest(currentRefresh));currentAccess=t.accessToken;currentRefresh=t.refreshToken;onTokensUpdated(t.accessToken,t.refreshToken);true}}catch(_:Exception){false}
 suspend fun loadHomeData(){val auth="Bearer $currentAccess";me=modernApi.me(auth);onlineFriends=try{modernApi.friends(auth).filter{it.profile?.isOnline==true}}catch(_:Exception){emptyList()};status=""}
 fun reloadMe(){scope.launch{try{loadHomeData()}catch(e:Exception){if(e is HttpException&&e.code()==401&&refreshSession()){try{loadHomeData()}catch(_:Exception){onSignedOut()}}else if(e is HttpException&&e.code()==401)onSignedOut()else status="تعذر تحميل الحساب"}}}
 LaunchedEffect(accessToken){reloadMe()};val auth="Bearer $currentAccess";val darkShell=tab=="home"||tab=="match"||tab=="rooms";val homeBackground=Color(0xFF071126);fun go(newTab:String){openedRoomChat=null;if(newTab!="match")challengeArena=false;tab=newTab}
 Scaffold(containerColor=if(darkShell)homeBackground else MaterialTheme.colorScheme.background,bottomBar={if(!(tab=="match"&&challengeArena)){NavigationBar(containerColor=if(darkShell)Color(0xFF081126)else MaterialTheme.colorScheme.surface,tonalElevation=if(darkShell)0.dp else NavigationBarDefaults.Elevation){val selectedDark=Color(0xFF6D28D9);val selectedOther=MaterialTheme.colorScheme.secondaryContainer
  NavigationBarItem(selected=tab=="home",onClick={go("home")},icon={AliqoArenaIcon(AliqoIcon.HOME,size=31.dp,active=tab=="home")},label={Text("الرئيسية")},colors=navItemColors(darkShell,if(darkShell)selectedDark else selectedOther))
  NavigationBarItem(selected=tab=="friends",onClick={go("friends")},icon={AliqoArenaIcon(AliqoIcon.FRIENDS,size=31.dp,active=tab=="friends")},label={Text("الأصدقاء")},colors=navItemColors(darkShell,selectedOther))
  NavigationBarItem(selected=tab=="notifications",onClick={go("notifications")},icon={Box{AliqoArenaIcon(AliqoIcon.BELL,size=31.dp,active=tab=="notifications");if(unread>0)Badge{Text(if(unread>99)"99+" else unread.toString())}}},label={Text("تنبيهات")},colors=navItemColors(darkShell,selectedOther))
  NavigationBarItem(selected=tab=="profile",onClick={go("profile")},icon={AliqoArenaIcon(AliqoIcon.PROFILE,size=31.dp,active=tab=="profile")},label={Text("الملف")},colors=navItemColors(darkShell,selectedOther))
 }}}){padding->Box(Modifier.fillMaxSize().padding(padding).background(if(darkShell)homeBackground else MaterialTheme.colorScheme.background)){when(tab){
 "home"->ApprovedHomeDashboard(me,onlineFriends,unread,{tab="match"},{openedRoomChat=null;tab="rooms"},{tab="notifications"},{tab="profile"})
 "match"->PremiumMatchExperience(auth){challengeArena=it}
 "rooms"->{if(openedRoomChat==null)PremiumRoomsScreen(auth,onOpenRoom={room,creator->openedRoomChat=room;openedRoomId=room.id;openedRoomCreator=creator})else ModernRoomChatScreen(auth,openedRoomChat!!,openedRoomId?:openedRoomChat!!.id,openedRoomCreator,onBack={openedRoomChat=null;openedRoomId=null;openedRoomCreator=false})}
 "friends"->ModernFriendsScreen(auth)
 "notifications"->ModernNotificationsScreen(auth,onUnreadChanged={unread=it})
 "profile"->ModernProfileScreen(auth=auth,me=me,status=status,onReload={reloadMe()},onSignedOut=onSignedOut)
 }}}
}
@Composable private fun navItemColors(dark:Boolean,indicator:Color)=NavigationBarItemDefaults.colors(selectedIconColor=if(dark)Color.White else MaterialTheme.colorScheme.onSecondaryContainer,selectedTextColor=if(dark)Color.White else MaterialTheme.colorScheme.onSurface,indicatorColor=indicator,unselectedIconColor=if(dark)Color(0xFFAEB8D1) else MaterialTheme.colorScheme.onSurfaceVariant,unselectedTextColor=if(dark)Color(0xFFAEB8D1) else MaterialTheme.colorScheme.onSurfaceVariant)
