package com.aliqo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

private val fastAuthClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(75, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(75, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
}

private val fastAuthApi: AliqoApi by lazy {
    Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(fastAuthClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AliqoApi::class.java)
}

private fun fastAuthMessage(e: Exception): String {
    if (e is HttpException) return when (e.code()) {
        400 -> "الطلب غير مقبول"
        401 -> "بيانات الدخول غير صحيحة"
        409 -> "البريد أو اسم المستخدم مستخدم مسبقًا"
        429 -> "محاولات كثيرة، حاول بعد قليل"
        in 500..599 -> "الخادم غير متاح حاليًا (${e.code()})"
        else -> "تعذر تسجيل الدخول (${e.code()})"
    }
    return when (e) {
        is SocketTimeoutException -> "الخادم استغرق وقتًا طويلًا؛ حاول مرة أخرى"
        is UnknownHostException -> "تعذر الوصول للإنترنت أو الخادم"
        else -> "تعذر الاتصال بالخادم"
    }
}

@Composable
fun FastAuthScreen(onAuthenticated: (AuthResponse) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (busy) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF071126)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ALIQO", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(26.dp))
                CircularProgressIndicator(color = Color(0xFF7C32F2))
                Spacer(Modifier.height(20.dp))
                Text(
                    if (register) "جارٍ تجهيز حسابك..." else "جارٍ دخولك إلى الساحة...",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text("لحظات ونبدأ ⚔️", color = Color(0xFFAEB8D1))
            }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("ALIQO", fontSize = 42.sp, fontWeight = FontWeight.Bold)
        Text(if (register) "إنشاء حساب جديد" else "تسجيل الدخول", style = MaterialTheme.typography.headlineSmall)
        Text("تواصل، أصدقاء، وتحديات اجتماعية في مكان واحد")

        OutlinedTextField(
            email,
            { email = it.trim() },
            Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("البريد الإلكتروني") }
        )

        if (register) {
            OutlinedTextField(
                username,
                { username = it.lowercase().replace(" ", "") },
                Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("اسم المستخدم الفريد") }
            )
            OutlinedTextField(
                displayName,
                { displayName = it },
                Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("الاسم الظاهر") }
            )
        }

        OutlinedTextField(
            password,
            { password = it },
            Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("كلمة المرور - 8 أحرف على الأقل") }
        )

        Button(
            onClick = {
                status = ""
                busy = true
                scope.launch {
                    try {
                        val result = if (register) {
                            fastAuthApi.register(RegisterRequest(email, username, password, displayName.trim()))
                        } else {
                            fastAuthApi.login(LoginRequest(email, password))
                        }
                        onAuthenticated(result)
                    } catch (e: Exception) {
                        status = fastAuthMessage(e)
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            enabled = email.isNotBlank() && password.length >= 8 && (!register || (username.length >= 3 && displayName.isNotBlank()))
        ) {
            Text(if (register) "إنشاء الحساب" else "دخول", fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = { register = !register; status = "" },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (register) "لديك حساب؟ تسجيل الدخول" else "ليس لديك حساب؟ إنشاء حساب")
        }

        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.error)
        }
    }
}
