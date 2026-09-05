package com.aliqo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private val notificationChatClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(75, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(75, TimeUnit.SECONDS)
        .build()
}
private val notificationChatApi: ChatApi by lazy {
    Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(notificationChatClient)
        .addConverterFactory(GsonConverterFactory.create()).build().create(ChatApi::class.java)
}
private val notificationUserApi: AliqoApi by lazy {
    Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(notificationChatClient)
        .addConverterFactory(GsonConverterFactory.create()).build().create(AliqoApi::class.java)
}

@Composable
fun NotificationDirectChatEntry(auth: String, chatId: String, onBack: () -> Unit) {
    var me by remember(chatId) { mutableStateOf<UserDto?>(null) }
    var friend by remember(chatId) { mutableStateOf<UserDto?>(null) }
    var failed by remember(chatId) { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        try {
            val currentMe = notificationUserApi.me(auth)
            val target = notificationChatApi.chats(auth).firstOrNull { it.id == chatId && it.type == "DIRECT" }
            val other = target?.members?.firstOrNull { it.userId != currentMe.id }?.user
            if (other == null) failed = true else {
                me = currentMe
                friend = other
            }
        } catch (_: Exception) {
            failed = true
        }
    }

    val targetFriend = friend
    if (targetFriend != null) {
        DirectChatScreen(auth, me, targetFriend, onBack)
    } else {
        Box(Modifier.fillMaxSize().background(Color(0xFF071126)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!failed) CircularProgressIndicator(color = Color(0xFF7C2CFF))
                Spacer(Modifier.height(12.dp))
                Text(if (failed) "تعذر فتح المحادثة" else "جارٍ فتح المحادثة...", color = Color(0xFFAAB5D2))
                if (failed) TextButton(onClick = onBack) { Text("رجوع") }
            }
        }
    }
}
