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
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private val modernHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(75, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(75, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
}

private val modernApi: AliqoApi by lazy {
    Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(modernHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AliqoApi::class.java)
}

class ModernMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { ModernAliqoApp() }
            }
        }
    }
}

@Composable
private fun ModernAliqoApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("aliqo_session", Context.MODE_PRIVATE) }
    var accessToken by remember { mutableStateOf(prefs.getString("accessToken", "") ?: "") }
    var refreshToken by remember { mutableStateOf(prefs.getString("refreshToken", "") ?: "") }

    fun saveTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
        prefs.edit().putString("accessToken", access).putString("refreshToken", refresh).apply()
    }

    fun signOutLocal() {
        accessToken = ""
        refreshToken = ""
        prefs.edit().clear().apply()
    }

    if (accessToken.isBlank()) {
        AuthScreen { saveTokens(it.accessToken, it.refreshToken) }
    } else {
        ModernMainShell(
            accessToken = accessToken,
            refreshToken = refreshToken,
            onTokensUpdated = ::saveTokens,
            onSignedOut = ::signOutLocal,
        )
    }
}

@Composable
private fun ModernMainShell(
    accessToken: String,
    refreshToken: String,
    onTokensUpdated: (String, String) -> Unit,
    onSignedOut: () -> Unit,
) {
    var currentAccess by remember(accessToken) { mutableStateOf(accessToken) }
    var currentRefresh by remember(refreshToken) { mutableStateOf(refreshToken) }
    var tab by remember { mutableStateOf("home") }
    var me by remember { mutableStateOf<UserDto?>(null) }
    var onlineFriends by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var status by remember { mutableStateOf("جارٍ تحميل حسابك...") }
    var unread by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun refreshSession(): Boolean = try {
        if (currentRefresh.isBlank()) false
        else {
            val t = modernApi.refresh(RefreshRequest(currentRefresh))
            currentAccess = t.accessToken
            currentRefresh = t.refreshToken
            onTokensUpdated(t.accessToken, t.refreshToken)
            true
        }
    } catch (_: Exception) {
        false
    }

    suspend fun loadHomeData() {
        val auth = "Bearer $currentAccess"
        me = modernApi.me(auth)
        onlineFriends = try {
            modernApi.friends(auth).filter { it.profile?.isOnline == true }
        } catch (_: Exception) {
            emptyList()
        }
        status = ""
    }

    fun reloadMe() {
        scope.launch {
            try {
                loadHomeData()
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 401 && refreshSession()) {
                    try { loadHomeData() } catch (_: Exception) { onSignedOut() }
                } else if (e is HttpException && e.code() == 401) {
                    onSignedOut()
                } else {
                    status = "تعذر تحميل الحساب"
                }
            }
        }
    }

    LaunchedEffect(accessToken) { reloadMe() }
    val auth = "Bearer $currentAccess"
    val onHome = tab == "home"
    val homeBackground = Color(0xFF071126)

    Scaffold(
        containerColor = if (onHome) homeBackground else MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = if (onHome) Color(0xFF081126) else MaterialTheme.colorScheme.surface,
                tonalElevation = if (onHome) 0.dp else NavigationBarDefaults.Elevation,
            ) {
                val selectedHome = if (onHome) Color(0xFF6D28D9) else MaterialTheme.colorScheme.secondaryContainer
                val selectedOther = MaterialTheme.colorScheme.secondaryContainer
                NavigationBarItem(
                    selected = tab == "home",
                    onClick = { tab = "home" },
                    icon = { Text("⌂") },
                    label = { Text("الرئيسية") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (onHome) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = if (onHome) Color.White else MaterialTheme.colorScheme.onSurface,
                        indicatorColor = selectedHome,
                        unselectedIconColor = if (onHome) Color(0xFFAAB5D2) else MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = if (onHome) Color(0xFFAAB5D2) else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                NavigationBarItem(
                    selected = tab == "chats",
                    onClick = { tab = "chats" },
                    icon = { Text("⚡") },
                    label = { Text("اكتشف") },
                    colors = navItemColors(onHome, selectedOther),
                )
                NavigationBarItem(
                    selected = tab == "friends",
                    onClick = { tab = "friends" },
                    icon = { Text("👥") },
                    label = { Text("الأصدقاء") },
                    colors = navItemColors(onHome, selectedOther),
                )
                NavigationBarItem(
                    selected = tab == "notifications",
                    onClick = { tab = "notifications" },
                    icon = { Text(if (unread > 0) "🔔$unread" else "🔔") },
                    label = { Text("تنبيهات") },
                    colors = navItemColors(onHome, selectedOther),
                )
                NavigationBarItem(
                    selected = tab == "profile",
                    onClick = { tab = "profile" },
                    icon = { Text("●") },
                    label = { Text("الملف") },
                    colors = navItemColors(onHome, selectedOther),
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (onHome) homeBackground else MaterialTheme.colorScheme.background)
        ) {
            when (tab) {
                "home" -> ApprovedHomeDashboard(
                    me = me,
                    onlineFriends = onlineFriends,
                    unread = unread,
                    onDiscover = { tab = "chats" },
                    onNotifications = { tab = "notifications" },
                    onProfile = { tab = "profile" },
                )
                "chats" -> ScreenFrame(status) { ChatsScreen(auth, me) }
                "friends" -> ScreenFrame(status) { FriendsScreen(auth, me) }
                "notifications" -> ScreenFrame(status) { NotificationsScreen(auth) { unread = it } }
                "profile" -> ScreenFrame(status) {
                    ProfileScreen(auth, me, ::reloadMe, currentRefresh, onSignedOut)
                }
            }
        }
    }
}

@Composable
private fun ScreenFrame(status: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("ALIQO", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        if (status.isNotBlank()) Text(status)
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun navItemColors(home: Boolean, selectedOther: Color): NavigationBarItemColors =
    NavigationBarItemDefaults.colors(
        selectedIconColor = if (home) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
        selectedTextColor = if (home) Color.White else MaterialTheme.colorScheme.onSurface,
        indicatorColor = if (home) Color(0xFF6D28D9) else selectedOther,
        unselectedIconColor = if (home) Color(0xFFAAB5D2) else MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = if (home) Color(0xFFAAB5D2) else MaterialTheme.colorScheme.onSurfaceVariant,
    )
