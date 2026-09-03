package com.aliqo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) { AuthScreen() }
            }
        }
    }
}

data class RegisterRequest(val email: String, val username: String, val password: String, val displayName: String)
data class LoginRequest(val email: String, val password: String)
data class AuthUser(val id: String, val email: String, val username: String, val role: String)
data class AuthResponse(val user: AuthUser, val accessToken: String, val refreshToken: String)

interface AliqoApi {
    @POST("auth/register") suspend fun register(@Body request: RegisterRequest): AuthResponse
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): AuthResponse
}

private val api: AliqoApi by lazy {
    Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).addConverterFactory(GsonConverterFactory.create()).build().create(AliqoApi::class.java)
}

@Composable
fun AuthScreen() {
    var mode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("جاهز") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ALIQO", style = MaterialTheme.typography.headlineLarge)
        Text(if (mode == "login") "تسجيل الدخول" else "إنشاء حساب")
        OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("البريد الإلكتروني") })
        if (mode == "register") {
            OutlinedTextField(username, { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم المستخدم") })
            OutlinedTextField(displayName, { displayName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الاسم") })
        }
        OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("كلمة المرور") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = {
                busy = true
                status = "جارٍ الاتصال..."
                scope.launch {
                    status = try {
                        val result = if (mode == "login") api.login(LoginRequest(email, password)) else api.register(RegisterRequest(email, username, password, displayName))
                        "تم الدخول: ${result.user.username} (${result.user.role})"
                    } catch (e: Exception) {
                        "تعذر الاتصال أو البيانات غير صحيحة"
                    }
                    busy = false
                }
            }) { Text(if (mode == "login") "دخول" else "إنشاء") }
            Button(enabled = !busy, onClick = { mode = if (mode == "login") "register" else "login" }) { Text("تبديل") }
        }
        Text(status)
    }
}
