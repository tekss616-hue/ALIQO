package com.aliqo.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.socket.client.IO
import io.socket.emitter.Emitter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class ProfileDto(val displayName: String = "", val bio: String? = null, val avatarUrl: String? = null, val isOnline: Boolean = false)
data class UserDto(val id: String, val email: String? = null, val username: String, val role: String? = null, val profile: ProfileDto? = null)
data class RegisterRequest(val email: String, val username: String, val password: String, val displayName: String)
data class LoginRequest(val email: String, val password: String)
data class RefreshRequest(val refreshToken: String)
data class UpdateProfileRequest(val displayName: String, val bio: String, val avatarUrl: String?)
data class AuthResponse(val user: UserDto, val accessToken: String, val refreshToken: String)
data class TokenResponse(val accessToken: String, val refreshToken: String)
data class FriendshipDto(val id: String? = null, val status: String? = null)
data class FriendRequestDto(val id: String, val user: UserDto)
data class OkResponse(val ok: Boolean = true)

interface AliqoApi {
    @POST("auth/register") suspend fun register(@Body request: RegisterRequest): AuthResponse
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): AuthResponse
    @POST("auth/refresh") suspend fun refresh(@Body request: RefreshRequest): TokenResponse
    @POST("auth/logout") suspend fun logout(@Body request: RefreshRequest): OkResponse
    @GET("users/me") suspend fun me(@Header("Authorization") auth: String): UserDto
    @PATCH("users/me/profile") suspend fun updateProfile(@Header("Authorization") auth: String, @Body request: UpdateProfileRequest): ProfileDto
    @GET("users/search") suspend fun searchUsers(@Header("Authorization") auth: String, @Query("q") query: String): List<UserDto>
    @DELETE("users/me") suspend fun deleteAccount(@Header("Authorization") auth: String): OkResponse
    @GET("friends") suspend fun friends(@Header("Authorization") auth: String): List<UserDto>
    @GET("friends/requests") suspend fun friendRequests(@Header("Authorization") auth: String): List<FriendRequestDto>
    @GET("friends/blocked") suspend fun blockedUsers(@Header("Authorization") auth: String): List<UserDto>
    @POST("friends/{userId}/request") suspend fun addFriend(@Header("Authorization") auth: String, @Path("userId") userId: String): FriendshipDto
    @POST("friends/{requestId}/accept") suspend fun acceptFriend(@Header("Authorization") auth: String, @Path("requestId") requestId: String): FriendshipDto
    @POST("friends/{requestId}/reject") suspend fun rejectFriend(@Header("Authorization") auth: String, @Path("requestId") requestId: String): OkResponse
    @DELETE("friends/{userId}") suspend fun removeFriend(@Header("Authorization") auth: String, @Path("userId") userId: String): OkResponse
    @POST("friends/{userId}/block") suspend fun blockUser(@Header("Authorization") auth: String, @Path("userId") userId: String): OkResponse
    @DELETE("friends/{userId}/block") suspend fun unblockUser(@Header("Authorization") auth: String, @Path("userId") userId: String): OkResponse
}

private val httpClient by lazy {
    OkHttpClient.Builder().connectTimeout(75, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(75, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
}
private val api: AliqoApi by lazy {
    Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(httpClient)
        .addConverterFactory(GsonConverterFactory.create()).build().create(AliqoApi::class.java)
}

private fun messageFor(e: Exception): String {
    if (e is HttpException) return when (e.code()) {
        400 -> "الطلب غير مقبول"
        401 -> "بيانات الدخول غير صحيحة أو انتهت الجلسة"
        404 -> "العنصر غير موجود"
        409 -> "البريد أو اسم المستخدم مستخدم مسبقًا"
        429 -> "محاولات كثيرة، حاول بعد قليل"
        in 500..599 -> "خطأ في الخادم (${e.code()})"
        else -> "خطأ HTTP ${e.code()}"
    }
    return when (e) {
        is SocketTimeoutException -> "الخادم استغرق وقتًا طويلًا؛ حاول مرة أخرى"
        is UnknownHostException -> "تعذر الوصول للإنترنت أو الخادم"
        else -> "خطأ اتصال: ${e.javaClass.simpleName}"
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { AliqoApp() } } }
    }
}

@Composable
fun AliqoApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aliqo_session", Context.MODE_PRIVATE) }
    var accessToken by remember { mutableStateOf(prefs.getString("accessToken", "") ?: "") }
    var refreshToken by remember { mutableStateOf(prefs.getString("refreshToken", "") ?: "") }
    fun saveTokens(access: String, refresh: String) {
        accessToken = access; refreshToken = refresh
        prefs.edit().putString("accessToken", access).putString("refreshToken", refresh).apply()
    }
    fun signOutLocal() { accessToken = ""; refreshToken = ""; prefs.edit().clear().apply() }
    if (accessToken.isBlank()) AuthScreen { saveTokens(it.accessToken, it.refreshToken) }
    else MainShell(accessToken, refreshToken, { a, r -> saveTokens(a, r) }, ::signOutLocal)
}

@Composable
fun AuthScreen(onAuthenticated: (AuthResponse) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(18.dp)); Text("ALIQO", fontSize = 42.sp, fontWeight = FontWeight.Bold)
        Text(if (register) "إنشاء حساب جديد" else "تسجيل الدخول", style = MaterialTheme.typography.headlineSmall)
        Text("تواصل، أصدقاء، وتحديات اجتماعية في مكان واحد")
        OutlinedTextField(email, { email = it.trim() }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("البريد الإلكتروني") })
        if (register) {
            OutlinedTextField(username, { username = it.lowercase().replace(" ", "") }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("اسم المستخدم الفريد") })
            OutlinedTextField(displayName, { displayName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("الاسم الظاهر") })
        }
        OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text("كلمة المرور - 8 أحرف على الأقل") })
        Button(onClick = {
            busy = true; status = "جارٍ الاتصال بالخادم..."
            scope.launch {
                try { onAuthenticated(if (register) api.register(RegisterRequest(email, username, password, displayName.trim())) else api.login(LoginRequest(email, password))) }
                catch (e: Exception) { status = messageFor(e) }
                busy = false
            }
        }, modifier = Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank() && password.length >= 8 && (!register || (username.length >= 3 && displayName.isNotBlank()))) {
            Text(if (register) "إنشاء الحساب" else "دخول")
        }
        OutlinedButton(onClick = { register = !register; status = "" }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
            Text(if (register) "لديك حساب؟ تسجيل الدخول" else "ليس لديك حساب؟ إنشاء حساب")
        }
        if (status.isNotBlank()) Text(status)
    }
}

@Composable
fun MainShell(accessToken: String, refreshToken: String, onTokensUpdated: (String, String) -> Unit, onSignedOut: () -> Unit) {
    var currentAccess by remember(accessToken) { mutableStateOf(accessToken) }
    var currentRefresh by remember(refreshToken) { mutableStateOf(refreshToken) }
    var tab by remember { mutableStateOf("home") }
    var me by remember { mutableStateOf<UserDto?>(null) }
    var status by remember { mutableStateOf("جارٍ تحميل حسابك...") }
    val scope = rememberCoroutineScope()
    suspend fun refreshSession(): Boolean = try {
        if (currentRefresh.isBlank()) false else {
            val t = api.refresh(RefreshRequest(currentRefresh)); currentAccess = t.accessToken; currentRefresh = t.refreshToken
            onTokensUpdated(t.accessToken, t.refreshToken); true
        }
    } catch (_: Exception) { false }
    fun reloadMe() {
        scope.launch {
            try { me = api.me("Bearer $currentAccess"); status = "" }
            catch (e: Exception) {
                if (e is HttpException && e.code() == 401 && refreshSession()) {
                    try { me = api.me("Bearer $currentAccess"); status = "" } catch (_: Exception) { onSignedOut() }
                } else if (e is HttpException && e.code() == 401) onSignedOut() else status = messageFor(e)
            }
        }
    }
    LaunchedEffect(accessToken) { reloadMe() }
    val auth = "Bearer $currentAccess"
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ALIQO", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { tab = "home" }, modifier = Modifier.weight(1f)) { Text("الرئيسية") }
            Button(onClick = { tab = "friends" }, modifier = Modifier.weight(1f)) { Text("الأصدقاء") }
            Button(onClick = { tab = "profile" }, modifier = Modifier.weight(1f)) { Text("الملف") }
        }
        Spacer(Modifier.height(10.dp)); if (status.isNotBlank()) Text(status)
        when (tab) {
            "friends" -> FriendsScreen(auth)
            "profile" -> ProfileScreen(auth, me, ::reloadMe, currentRefresh, onSignedOut)
            else -> HomeScreen(me)
        }
    }
}

@Composable
fun HomeScreen(me: UserDto?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("مرحبًا ${me?.profile?.displayName ?: me?.username.orEmpty()}", style = MaterialTheme.typography.headlineSmall)
            Text("@${me?.username.orEmpty()}"); Text("الحساب: ${me?.role ?: "USER"}")
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⚡ التحديات", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("ستدخل مع محرك المحادثات والتحديات في الدفعات التالية.")
        } }
        Text("الدفعة الأولى: الحسابات، الجلسات، الملف الشخصي، البحث، طلبات الصداقة والحظر مرتبطة بالخادم الحقيقي.")
    }
}

@Composable
fun FriendsScreen(auth: String) {
    var section by remember { mutableStateOf("friends") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var friends by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var requests by remember { mutableStateOf<List<FriendRequestDto>>(emptyList()) }
    var blocked by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refreshData(refreshSearch: Boolean = true) {
        val newFriends = api.friends(auth)
        val newRequests = api.friendRequests(auth)
        val newBlocked = api.blockedUsers(auth)
        friends = newFriends; requests = newRequests; blocked = newBlocked
        if (refreshSearch && query.length >= 2) {
            val friendIds = newFriends.map { it.id }.toSet()
            results = api.searchUsers(auth, query).filterNot { it.id in friendIds }
        }
    }
    fun showStatus(message: String, millis: Long = 1000) {
        status = message
        scope.launch { delay(millis); if (status == message) status = "" }
    }
    fun reloadAll() { scope.launch { try { refreshData(); status = "" } catch (e: Exception) { status = messageFor(e) } } }

    LaunchedEffect(auth) { reloadAll() }

    DisposableEffect(auth) {
        val token = auth.removePrefix("Bearer ").trim()
        val options = IO.Options.builder().setAuth(mapOf("token" to token)).setReconnection(true).build()
        val socket = IO.socket(BuildConfig.REALTIME_URL, options)
        val listener = Emitter.Listener {
            scope.launch { try { refreshData(refreshSearch = query.length >= 2) } catch (_: Exception) {} }
        }
        socket.on("friends:changed", listener)
        socket.connect()
        onDispose {
            socket.off("friends:changed", listener)
            socket.disconnect()
            socket.close()
        }
    }

    // Low-frequency safety fallback only; normal friendship changes arrive instantly over Socket.IO.
    LaunchedEffect(auth) {
        while (isActive) {
            delay(30000)
            try { refreshData(refreshSearch = query.length >= 2) } catch (_: Exception) {}
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("الأصدقاء", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = { section = "friends" }, modifier = Modifier.weight(1f)) { Text("أصدقائي") }
            OutlinedButton(onClick = { section = "requests" }, modifier = Modifier.weight(1f)) { Text("الطلبات ${requests.size}") }
            OutlinedButton(onClick = { section = "blocked" }, modifier = Modifier.weight(1f)) { Text("المحظور") }
        }
        when (section) {
            "requests" -> {
                if (requests.isEmpty()) Text("لا توجد طلبات صداقة معلقة")
                requests.forEach { request ->
                    UserCard(request.user, "قبول", {
                        scope.launch { try { api.acceptFriend(auth, request.id); refreshData(); showStatus("تم قبول @${request.user.username}") } catch (e: Exception) { status = messageFor(e) } }
                    }, "رفض", {
                        scope.launch { try { api.rejectFriend(auth, request.id); refreshData(); showStatus("تم رفض الطلب") } catch (e: Exception) { status = messageFor(e) } }
                    })
                }
            }
            "blocked" -> {
                if (blocked.isEmpty()) Text("لا يوجد مستخدمون محظورون")
                blocked.forEach { user -> UserCard(user, "إلغاء الحظر", {
                    scope.launch { try { api.unblockUser(auth, user.id); refreshData(); showStatus("تم إلغاء حظر @${user.username}") } catch (e: Exception) { status = messageFor(e) } }
                }, "—", {}) }
            }
            else -> {
                OutlinedTextField(query, { query = it.lowercase().replace(" ", "") }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("ابحث باسم المستخدم") })
                Button(onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val friendIds = friends.map { it.id }.toSet()
                            results = api.searchUsers(auth, query).filterNot { it.id in friendIds }
                            status = if (results.isEmpty()) "لا توجد نتائج" else ""
                        } catch (e: Exception) { status = messageFor(e) }
                        busy = false
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !busy && query.length >= 2) { Text("بحث") }
                if (results.isNotEmpty()) Text("نتائج البحث", fontWeight = FontWeight.Bold)
                results.forEach { user -> UserCard(user, "إضافة", {
                    scope.launch {
                        try {
                            val r = api.addFriend(auth, user.id); results = results.filterNot { it.id == user.id }; refreshData()
                            showStatus(if (r.status == "ACCEPTED") "أصبحتما صديقين" else "تم إرسال طلب الصداقة")
                        } catch (e: Exception) { status = messageFor(e) }
                    }
                }, "حظر", {
                    scope.launch { try { api.blockUser(auth, user.id); results = results.filterNot { it.id == user.id }; refreshData(); showStatus("تم حظر @${user.username}") } catch (e: Exception) { status = messageFor(e) } }
                }) }
                Text("قائمة أصدقائك (${friends.size})", fontWeight = FontWeight.Bold)
                if (friends.isEmpty()) Text("لا يوجد أصدقاء حتى الآن")
                friends.forEach { user -> UserCard(user, "حذف", {
                    scope.launch { try { api.removeFriend(auth, user.id); refreshData(); showStatus("تم حذف @${user.username}") } catch (e: Exception) { status = messageFor(e) } }
                }, "حظر", {
                    scope.launch { try { api.blockUser(auth, user.id); refreshData(); showStatus("تم حظر @${user.username}") } catch (e: Exception) { status = messageFor(e) } }
                }) }
            }
        }
        if (status.isNotBlank()) Text(status)
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun UserCard(user: UserDto, primaryLabel: String, onPrimary: () -> Unit, secondaryLabel: String, onSecondary: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(user.profile?.displayName?.ifBlank { user.username } ?: user.username, fontWeight = FontWeight.Bold)
            Text("@${user.username}")
            user.profile?.bio?.takeIf { it.isNotBlank() }?.let { Text(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrimary) { Text(primaryLabel) }
                if (secondaryLabel != "—") OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}

@Composable
fun ProfileScreen(auth: String, me: UserDto?, onProfileUpdated: () -> Unit, refreshToken: String, onSignedOut: () -> Unit) {
    var displayName by remember(me?.profile?.displayName) { mutableStateOf(me?.profile?.displayName ?: "") }
    var bio by remember(me?.profile?.bio) { mutableStateOf(me?.profile?.bio ?: "") }
    var avatarUrl by remember(me?.profile?.avatarUrl) { mutableStateOf(me?.profile?.avatarUrl ?: "") }
    var status by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("حذف الحساب؟") },
        text = { Text("سيتم تعطيل الحساب وإلغاء جلسات الدخول نهائيًا.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { try { api.deleteAccount(auth); onSignedOut() } catch (e: Exception) { status = messageFor(e) } } }) { Text("حذف نهائي") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } }
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ملفي الشخصي", style = MaterialTheme.typography.headlineSmall)
        Text("@${me?.username.orEmpty()}"); me?.email?.let { Text(it) }
        OutlinedTextField(displayName, { displayName = it.take(60) }, modifier = Modifier.fillMaxWidth(), label = { Text("الاسم الظاهر") })
        OutlinedTextField(bio, { bio = it.take(280) }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("نبذة مختصرة (${bio.length}/280)") })
        OutlinedTextField(avatarUrl, { avatarUrl = it.take(500) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("رابط الصورة الشخصية - اختياري") })
        Button(onClick = {
            scope.launch {
                try {
                    api.updateProfile(auth, UpdateProfileRequest(displayName.trim(), bio.trim(), avatarUrl.trim().ifBlank { null }))
                    val success = "تم حفظ الملف الشخصي"; status = success; onProfileUpdated(); delay(450); if (status == success) status = ""
                } catch (e: Exception) { status = messageFor(e) }
            }
        }, modifier = Modifier.fillMaxWidth(), enabled = displayName.isNotBlank()) { Text("حفظ التغييرات") }
        OutlinedButton(onClick = { scope.launch { try { if (refreshToken.isNotBlank()) api.logout(RefreshRequest(refreshToken)) } catch (_: Exception) {}; onSignedOut() } }, modifier = Modifier.fillMaxWidth()) { Text("تسجيل الخروج") }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("حذف الحساب") }
        if (status.isNotBlank()) Text(status)
        Spacer(Modifier.height(30.dp))
    }
}
