package com.aliqo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.util.concurrent.TimeUnit

private val gateClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(75, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(75, TimeUnit.SECONDS)
        .build()
}

private val gateSocialApi: AliqoApi by lazy {
    Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(gateClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AliqoApi::class.java)
}

private interface GateNotificationsApi {
    @GET("notifications")
    suspend fun list(@Header("Authorization") auth: String): List<NotificationDto>
}

private val gateNotificationsApi: GateNotificationsApi by lazy {
    Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(gateClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GateNotificationsApi::class.java)
}

@Composable
fun FriendsEntryScreen(auth: String, me: UserDto?) {
    val context = LocalContext.current
    val alreadyReady = remember(auth) {
        PersistentUiCache.has(context, "friends") &&
            PersistentUiCache.has(context, "friend_requests") &&
            PersistentUiCache.has(context, "blocked_users")
    }
    var ready by remember(auth) { mutableStateOf(alreadyReady) }
    var failed by remember(auth) { mutableStateOf(false) }
    var attempt by remember(auth) { mutableIntStateOf(0) }

    LaunchedEffect(auth, attempt) {
        if (ready) return@LaunchedEffect
        failed = false
        try {
            val friends = gateSocialApi.friends(auth)
            val requests = gateSocialApi.friendRequests(auth)
            val blocked = gateSocialApi.blockedUsers(auth)
            PersistentUiCache.saveUsers(context, "friends", friends)
            PersistentUiCache.saveFriendRequests(context, "friend_requests", requests)
            PersistentUiCache.saveUsers(context, "blocked_users", blocked)
            ready = true
        } catch (_: Exception) {
            failed = true
        }
    }

    if (ready) {
        ArenaFriendsScreen(auth, me)
    } else {
        FirstLoadPanel(
            text = if (failed) "تعذر تحميل الأصدقاء" else "جارٍ تحميل الأصدقاء لأول مرة...",
            retry = if (failed) {{ attempt++ }} else null
        )
    }
}

@Composable
fun NotificationsEntryScreen(auth: String, onUnreadChanged: (Int) -> Unit = {}) {
    val context = LocalContext.current
    val alreadyReady = remember(auth) { PersistentUiCache.has(context, "notifications") }
    var ready by remember(auth) { mutableStateOf(alreadyReady) }
    var failed by remember(auth) { mutableStateOf(false) }
    var attempt by remember(auth) { mutableIntStateOf(0) }

    LaunchedEffect(auth, attempt) {
        if (ready) return@LaunchedEffect
        failed = false
        try {
            val items = gateNotificationsApi.list(auth)
            PersistentUiCache.saveNotifications(context, "notifications", items)
            onUnreadChanged(items.count { it.readAt == null })
            ready = true
        } catch (_: Exception) {
            failed = true
        }
    }

    if (ready) {
        NotificationsScreen(auth, onUnreadChanged)
    } else {
        FirstLoadPanel(
            text = if (failed) "تعذر تحميل التنبيهات" else "جارٍ تحميل التنبيهات لأول مرة...",
            retry = if (failed) {{ attempt++ }} else null
        )
    }
}

@Composable
private fun FirstLoadPanel(text: String, retry: (() -> Unit)?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF061126)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (retry == null) CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text, color = Color.White)
            if (retry != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = retry) { Text("إعادة المحاولة") }
            }
        }
    }
}
