package com.aliqo.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) { AliqoApp() }
            }
        }
    }
}

data class ProfileDto(
    val displayName: String = "",
    val bio: String? = null,
    val avatarUrl: String? = null,
    val isOnline: Boolean = false
)

data class UserDto(
    val id: String,
    val email: String? = null,
    val username: String,
    val role: String? = null,
    val profile: ProfileDto? = null
)

data class RegisterRequest(val email: String, val username: String, val password: String, val displayName: String)
data class LoginRequest(val email: String, val password: String)
data class UpdateProfileRequest(val displayName: String, val bio: String)
data class AuthResponse(val user: UserDto, val accessToken: String, val refreshToken: String)
data class FriendshipDto(val id: String? = null, val status: String? = null)
data class OkResponse(val ok: Boolean = true)

interface AliqoApi {
    @POST("auth/register") suspend fun register(@Body request: RegisterRequest): AuthResponse
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): AuthResponse
    @POST("auth/logout") suspend fun logout(@Body body: Map<String, String>): OkResponse

    @GET("users/me") suspend fun me(@Header("Authorization") auth: String): UserDto
    @PATCH("users/me/profile") suspend fun updateProfile(
        @Header("Authorization") auth: String,
        @Body request: UpdateProfileRequest
    ): ProfileDto
    @GET("users/search") suspend fun searchUsers(
        @Header("Authorization") auth: String,
        @Query("q") query: String
    ): List<UserDto>
    @DELETE("users/me") suspend fun deleteAccount(@Header("Authorization") auth: String): OkResponse

    @GET("friends") suspend fun friends(@Header("Authorization") auth: String): List<UserDto>
    @POST("friends/{userId}/request") suspend fun addFriend(
        @Header("Authorization") auth: String,
        @Path("userId") userId: String
    ): FriendshipDto
    @DELETE("friends/{userId}") suspend fun removeFriend(
        @Header("Authorization") auth: String,
        @Path("userId") userId: String
    ): OkResponse
    @POST("friends/{userId}/block") suspend fun blockUser(
        @Header("Authorization") auth: String,
        @Path("userId") userId: String
    ): OkResponse
}

private val api: AliqoApi by lazy {
    Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AliqoApi::class.java)
}

private fun messageFor(e: Exception): String = when (e) {
    is HttpException -> when (e.code()) {
        400 -> "تحقق من البيانات وحاول مرة أخرى"
        401 -> "انتهت الجلسة أو بيانات الدخول غير صحيحة"
        404 -> "العنصر غير موجود"
        409 -> "البريد أو اسم المستخدم مستخدم مسبقًا"
        else -> "خطأ من الخادم (${e.code()})"
    }
    else -> "تعذر الاتصال بالخادم"
}

@Composable
fun AliqoApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aliqo_session", Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(prefs.getString("accessToken", "") ?: "") }
    var refreshToken by remember { mutableStateOf(prefs.getString("refreshToken", "") ?: "") }

    if (token.isBlank()) {
        AuthScreen { result ->
            token = result.accessToken
            refreshToken = result.refreshToken
            prefs.edit()
                .putString("accessToken", result.accessToken)
                .putString("refreshToken", result.refreshToken)
                .apply()
        }
    } else {
        MainShell(
            token = token,
            refreshToken = refreshToken,
            onSignedOut = {
                token = ""
                refreshToken = ""
                prefs.edit().clear().apply()
            }
        )
    }
}

@Composable
fun AuthScreen(onAuthenticated: (AuthResponse) -> Unit) {
    var mode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("ALIQO", fontSize = 42.sp, fontWeight = FontWeight.Bold)
        Text(if (mode == "login") "تسجيل الدخول" else "إنشاء حساب جديد", style = MaterialTheme.typography.headlineSmall)
        Text("تواصل، أصدقاء، وتحديات اجتماعية في مكان واحد")

        OutlinedTextField(email, { email = it.trim() }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("البريد الإلكتروني") })
        if (mode == "register") {
            OutlinedTextField(username, { username = it.lowercase().replace(" ", "") }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("اسم المستخدم الفريد") })
            OutlinedTextField(displayName, { displayName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("الاسم الظاهر") })
        }
        OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("كلمة المرور - 8 أحرف على الأقل") })

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && email.isNotBlank() && password.length >= 8 && (mode == "login" || (username.length >= 3 && displayName.isNotBlank())),
            onClick = {
                busy = true
                status = "جارٍ الاتصال..."
                scope.launch {
                    try {
                        val result = if (mode == "login") {
                            api.login(LoginRequest(email, password))
                        } else {
                            api.register(RegisterRequest(email, username, password, displayName))
                        }
                        status = "تم بنجاح"
                        onAuthenticated(result)
                    } catch (e: Exception) {
                        status = messageFor(e)
                    }
                    busy = false
                }
            }
        ) { Text(if (mode == "login") "دخول" else "إنشاء الحساب") }

        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = {
            mode = if (mode == "login") "register" else "login"
            status = ""
        }) { Text(if (mode == "login") "ليس لديك حساب؟ إنشاء حساب" else "لديك حساب؟ تسجيل الدخول") }

        if (status.isNotBlank()) Text(status)
    }
}

@Composable
fun MainShell(token: String, refreshToken: String, onSignedOut: () -> Unit) {
    val auth = "Bearer $token"
    var tab by remember { mutableStateOf("home") }
    var me by remember { mutableStateOf<UserDto?>(null) }
    var status by remember { mutableStateOf("جارٍ تحميل حسابك...") }
    val scope = rememberCoroutineScope()

    fun reloadMe() {
        scope.launch {
            try {
                me = api.me(auth)
                status = ""
            } catch (e: Exception) {
                status = messageFor(e)
                if (e is HttpException && e.code() == 401) onSignedOut()
            }
        }
    }

    LaunchedEffect(token) { reloadMe() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("ALIQO", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = { tab = "home" }) { Text("الرئيسية") }
            Button(modifier = Modifier.weight(1f), onClick = { tab = "friends" }) { Text("الأصدقاء") }
            Button(modifier = Modifier.weight(1f), onClick = { tab = "profile" }) { Text("الملف") }
        }
        Spacer(Modifier.height(10.dp))

        if (status.isNotBlank()) Text(status)
        when (tab) {
            "friends" -> FriendsScreen(auth)
            "profile" -> ProfileScreen(auth, me, onProfileUpdated = { reloadMe() }, refreshToken = refreshToken, onSignedOut = onSignedOut)
            else -> HomeScreen(me)
        }
    }
}

@Composable
fun HomeScreen(me: UserDto?) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("مرحبًا ${me?.profile?.displayName ?: me?.username.orEmpty()}", style = MaterialTheme.typography.headlineSmall)
                Text("@${me?.username.orEmpty()}")
                Text("الحساب: ${me?.role ?: "USER"}")
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚡ التحديات", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("المحرك الاجتماعي الرئيسي سيُفعّل في الدفعة الثالثة بعد تثبيت المحادثات في الدفعة الثانية.")
            }
        }
        Text("الدفعة الأولى: الحسابات والملف الشخصي والأصدقاء تعمل على الخادم الحقيقي.")
    }
}

@Composable
fun FriendsScreen(auth: String) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var friends by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadFriends() {
        scope.launch {
            try { friends = api.friends(auth) } catch (e: Exception) { status = messageFor(e) }
        }
    }
    LaunchedEffect(auth) { loadFriends() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("الأصدقاء", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(query, { query = it.lowercase().replace(" ", "") }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("ابحث باسم المستخدم") })
        Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && query.length >= 2, onClick = {
            busy = true
            scope.launch {
                try {
                    results = api.searchUsers(auth, query)
                    status = if (results.isEmpty()) "لا توجد نتائج" else ""
                } catch (e: Exception) { status = messageFor(e) }
                busy = false
            }
        }) { Text("بحث") }

        if (status.isNotBlank()) Text(status)

        if (results.isNotEmpty()) {
            Text("نتائج البحث", fontWeight = FontWeight.Bold)
            results.forEach { user ->
                UserCard(user = user, primaryLabel = "إضافة", onPrimary = {
                    scope.launch {
                        try {
                            val r = api.addFriend(auth, user.id)
                            status = if (r.status == "ACCEPTED") "تمت إضافة @${user.username} كصديق" else "تم إرسال طلب الصداقة إلى @${user.username}"
                            loadFriends()
                        } catch (e: Exception) { status = messageFor(e) }
                    }
                }, secondaryLabel = "حظر", onSecondary = {
                    scope.launch {
                        try {
                            api.blockUser(auth, user.id)
                            results = results.filterNot { it.id == user.id }
                            status = "تم حظر @${user.username}"
                            loadFriends()
                        } catch (e: Exception) { status = messageFor(e) }
                    }
                })
            }
        }

        Text("قائمة أصدقائك (${friends.size})", fontWeight = FontWeight.Bold)
        if (friends.isEmpty()) Text("لا يوجد أصدقاء حتى الآن. ابحث عن مستخدم وأرسل له طلبًا.")
        friends.forEach { user ->
            UserCard(user = user, primaryLabel = "حذف", onPrimary = {
                scope.launch {
                    try {
                        api.removeFriend(auth, user.id)
                        status = "تم حذف @${user.username} من الأصدقاء"
                        loadFriends()
                    } catch (e: Exception) { status = messageFor(e) }
                }, secondaryLabel = "حظر", onSecondary = {
                    scope.launch {
                        try {
                            api.blockUser(auth, user.id)
                            status = "تم حظر @${user.username}"
                            loadFriends()
                        } catch (e: Exception) { status = messageFor(e) }
                    }
                })
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun UserCard(user: UserDto, primaryLabel: String, onPrimary: () -> Unit, secondaryLabel: String, onSecondary: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(user.profile?.displayName?.ifBlank { user.username } ?: user.username, fontWeight = FontWeight.Bold)
            Text("@${user.username}")
            user.profile?.bio?.takeIf { it.isNotBlank() }?.let { Text(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrimary) { Text(primaryLabel) }
                OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    auth: String,
    me: UserDto?,
    onProfileUpdated: () -> Unit,
    refreshToken: String,
    onSignedOut: () -> Unit
) {
    var displayName by remember(me?.profile?.displayName) { mutableStateOf(me?.profile?.displayName ?: "") }
    var bio by remember(me?.profile?.bio) { mutableStateOf(me?.profile?.bio ?: "") }
    var status by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف الحساب؟") },
            text = { Text("سيتم تعطيل الحساب وحذف جلساتك. لا يمكن التراجع من داخل التطبيق.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            api.deleteAccount(auth)
                            onSignedOut()
                        } catch (e: Exception) { status = messageFor(e) }
                    }
                }) { Text("حذف نهائي") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ملفي الشخصي", style = MaterialTheme.typography.headlineSmall)
        Text("@${me?.username.orEmpty()}")
        me?.email?.let { Text(it) }
        OutlinedTextField(displayName, { displayName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الاسم الظاهر") })
        OutlinedTextField(bio, { if (it.length <= 280) bio = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("نبذة مختصرة (${bio.length}/280)") })
        Button(modifier = Modifier.fillMaxWidth(), enabled = displayName.isNotBlank(), onClick = {
            scope.launch {
                try {
                    api.updateProfile(auth, UpdateProfileRequest(displayName.trim(), bio.trim()))
                    status = "تم حفظ الملف الشخصي"
                    onProfileUpdated()
                } catch (e: Exception) { status = messageFor(e) }
            }
        }) { Text("حفظ التغييرات") }

        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                try { if (refreshToken.isNotBlank()) api.logout(mapOf("refreshToken" to refreshToken)) } catch (_: Exception) { }
                onSignedOut()
            }
        }) { Text("تسجيل الخروج") }

        TextButton(modifier = Modifier.fillMaxWidth(), onClick = { confirmDelete = true }) { Text("حذف الحساب") }
        if (status.isNotBlank()) Text(status)
        Spacer(Modifier.height(30.dp))
    }
}
