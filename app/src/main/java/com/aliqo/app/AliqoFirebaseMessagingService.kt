package com.aliqo.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AliqoFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val prefs = getSharedPreferences("aliqo_session", Context.MODE_PRIVATE)
        prefs.edit().putString("fcmToken", token).apply()
        val access = prefs.getString("accessToken", "").orEmpty()
        if (access.isNotBlank()) registerToken(access, token)
    }

    private fun registerToken(access: String, token: String) {
        serviceScope.launch {
            runCatching {
                batch2TransportApi.registerDevice(
                    "Bearer $access",
                    RegisterDeviceRequest(token = token, appVersion = BuildConfig.VERSION_NAME),
                )
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "ALIQO"
        val body = message.notification?.body ?: message.data["body"] ?: "لديك تنبيه جديد"
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "aliqo_messages"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(channelId, "رسائل وتنبيهات ALIQO", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(this).notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification)
    }
}
