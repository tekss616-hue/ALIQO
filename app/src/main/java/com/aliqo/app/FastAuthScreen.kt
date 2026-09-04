package com.aliqo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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

private val AuthBg = Color(0xFF061126)
private val AuthCard = Color(0xFF0A1730)
private val AuthBorder = Color(0xFF244B86)
private val AuthMuted = Color(0xFFAEB8D1)
private val AuthPurple = Color(0xFF8A2CFF)
private val AuthBlue = Color(0xFF126DFF)

private val fastAuthClient by lazy {
    OkHttpClient.Builder().connectTimeout(75, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(75, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
}
private val fastAuthApi: AliqoApi by lazy {
    Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(fastAuthClient)
        .addConverterFactory(GsonConverterFactory.create()).build().create(AliqoApi::class.java)
}
private fun fastAuthMessage(e: Exception): String {
    if (e is HttpException) return when (e.code()) {
        400 -> "الطلب غير مقبول"; 401 -> "بيانات الدخول غير صحيحة"; 409 -> "البريد أو اسم المستخدم مستخدم مسبقًا"
        429 -> "محاولات كثيرة، حاول بعد قليل"; in 500..599 -> "الخادم غير متاح حاليًا (${e.code()})"
        else -> "تعذر تسجيل الدخول (${e.code()})"
    }
    return when (e) { is SocketTimeoutException -> "الخادم استغرق وقتًا طويلًا؛ حاول مرة أخرى"; is UnknownHostException -> "تعذر الوصول للإنترنت أو الخادم"; else -> "تعذر الاتصال بالخادم" }
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
        Box(Modifier.fillMaxSize().background(AuthBg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AuthLogo(96.dp)
                Spacer(Modifier.height(16.dp)); CircularProgressIndicator(color = AuthPurple); Spacer(Modifier.height(16.dp))
                Text(if (register) "جارٍ تجهيز حسابك..." else "جارٍ دخولك إلى الساحة...", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp)); Text("لحظات ونبدأ ⚔️", color = AuthMuted)
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF07142D), AuthBg, Color(0xFF040B18))))) {
        Box(Modifier.size(230.dp).align(Alignment.TopCenter).offset(y = (-135).dp).clip(CircleShape).background(AuthPurple.copy(alpha = .08f)))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp)); AuthLogo(108.dp); Spacer(Modifier.height(9.dp))
            Text(if (register) "أنشئ حسابك" else "ادخل الساحة", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text(if (register) "انضم إلى ALIQO وابدأ المنافسة" else "تحديات حقيقية • منافسات • أصدقاء", color = AuthMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(19.dp))
            AuthField(email, { email = it.trim() }, "✉  البريد الإلكتروني")
            if (register) {
                Spacer(Modifier.height(10.dp)); AuthField(username, { username = it.lowercase().replace(" ", "") }, "◉  اسم المستخدم الفريد")
                Spacer(Modifier.height(10.dp)); AuthField(displayName, { displayName = it }, "✦  الاسم الظاهر")
            }
            Spacer(Modifier.height(10.dp)); AuthField(password, { password = it }, "🔒  كلمة المرور - 8 أحرف على الأقل", password = true)
            Spacer(Modifier.height(16.dp))
            val enabled = email.isNotBlank() && password.length >= 8 && (!register || (username.length >= 3 && displayName.isNotBlank()))
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (enabled) Brush.horizontalGradient(listOf(AuthPurple, AuthBlue)) else Brush.horizontalGradient(listOf(Color(0xFF29324A), Color(0xFF202B42))))
                    .clickable(enabled = enabled) {
                        status = ""; busy = true
                        scope.launch {
                            try {
                                val result = if (register) fastAuthApi.register(RegisterRequest(email, username, password, displayName.trim())) else fastAuthApi.login(LoginRequest(email, password))
                                onAuthenticated(result)
                            } catch (e: Exception) { status = fastAuthMessage(e); busy = false }
                        }
                    }, contentAlignment = Alignment.Center
            ) { Text(if (register) "إنشاء الحساب  ←" else "دخول إلى الساحة  ←", color = if (enabled) Color.White else AuthMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            if (status.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(status, color = Color(0xFFFF6B82), textAlign = TextAlign.Center) }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (register) "لديك حساب؟ " else "جديد في ALIQO؟ ", color = AuthMuted, fontSize = 14.sp)
                Text(if (register) "تسجيل الدخول" else "إنشاء حساب", color = Color(0xFFB865FF), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { register = !register; status = "" })
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable private fun AuthLogo(size: androidx.compose.ui.unit.Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF321064), Color(0xFF09152E)))).padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.aliqo_brand_logo), contentDescription = "ALIQO",
                modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.height(4.dp)); Text("ALIQO", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
    }
}

@Composable private fun AuthField(value: String, onValueChange: (String) -> Unit, hint: String, password: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(hint, color = AuthMuted, fontSize = 13.sp) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(15.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color(0xFFB865FF),
            focusedBorderColor = Color(0xFF7356FF), unfocusedBorderColor = AuthBorder,
            focusedContainerColor = AuthCard, unfocusedContainerColor = AuthCard
        )
    )
}
