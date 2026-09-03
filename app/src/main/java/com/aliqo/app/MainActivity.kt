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
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { AliqoApp() } } }
    }
}

data class ProfileDto(val displayName: String = "", val bio: String? = null, val avatarUrl: String? = null, val isOnline: Boolean = false)
data class UserDto(val id: String, val email: String? = null, val username: String, val role: String? = null, val profile: ProfileDto? = null)
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
    @PATCH("users/me/profile") suspend fun updateProfile(@Header("Authorization") auth: String, @Body request: UpdateProfileRequest): ProfileDto
    @GET("users/search") suspend fun searchUsers(@Header("Authorization") auth: String, @Query("q") query: String): List<UserDto>
    @DELETE("users/me") suspend fun deleteAccount(@Header("Authorization") auth: String): OkResponse
    @GET("friends") suspend fun friends(@Header("Authorization") auth: String): List<UserDto>
    @POST("friends/{userId}/request") suspend fun addFriend(@Header("Authorization") auth: String, @Path("userId") userId: String): FriendshipDto
    @DELETE("friends/{userId}") suspend fun removeFriend(@Header("Authorization") auth: String, @Path("userId") userId: String): OkResponse
    @POST("friends/{userId}/block") suspend fun blockUser(@Header("Authorization") auth: String, @Path("userId") userId: String): OkResponse
}

private val httpClient by lazy {
    OkHttpClient.Builder().connectTimeout(75, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS).writeTimeout(75, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
}

private val api: AliqoApi by lazy {
    Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(httpClient).addConverterFactory(GsonConverterFactory.create()).build().create(AliqoApi::class.java)
}

private fun messageFor(e: Exception): String {
    if (e is HttpException) {
        val serverMessage = try { e.response()?.errorBody()?.string()?.take(220) } catch (_: Exception) { null }
        return when (e.code()) {
            400 -> "الطلب غير مقبول من الخادم${serverMessage?.let { ": $it" } ?: ""}"
            401 -> "بيانات الدخول غير صحيحة أو انتهت الجلسة"
            404 -> "العنصر غير موجود"
            409 -> "البريد أو اسم المستخدم مستخدم مسبقًا"
            429 -> "محاولات كثيرة، انتظر قليلًا ثم حاول مجددًا"
            in 500..599 -> "خطأ في الخادم (${e.code()})${serverMessage?.let { ": $it" } ?: ""}"
            else -> "خطأ HTTP ${e.code()}${serverMessage?.let { ": $it" } ?: ""}"
        }
    }
    return when (e) {
        is SocketTimeoutException -> "الخادم استغرق وقتًا طويلًا. قد يكون يستيقظ من وضع السكون؛ حاول مرة أخرى."
        is UnknownHostException -> "تعذر الوصول للإنترنت أو اسم الخادم"
        else -> "خطأ اتصال: ${e.javaClass.simpleName}${e.message?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""}"
    }
}

@Composable
fun AliqoApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aliqo_session", Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(prefs.getString("accessToken", "") ?: "") }
    var refreshToken by remember { mutableStateOf(prefs.getString("refreshToken", "") ?: "") }
    val signedOut = { token = ""; refreshToken = ""; prefs.edit().clear().apply() }
    if (token.isBlank()) AuthScreen { result ->
        token = result.accessToken; refreshToken = result.refreshToken
        prefs.edit().putString("accessToken", token).putString("refreshToken", refreshToken).apply()
    } else MainShell(token, refreshToken, signedOut)
}

@Composable
fun AuthScreen(onAuthenticated: (AuthResponse) -> Unit) {
    var register by remember { mutableStateOf(false) }; var email by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var status by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(18.dp)); Text("ALIQO", fontSize = 42.sp, fontWeight = FontWeight.Bold)
        Text(if (register) "إنشاء حساب جديد" else "تسجيل الدخول", style = MaterialTheme.typography.headlineSmall); Text("تواصل، أصدقاء، وتحديات اجتماعية في مكان واحد")
        OutlinedTextField(email, { email = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("البريد الإلكتروني") })
        if (register) {
            OutlinedTextField(username, { username = it.lowercase().replace(" ", "") }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("اسم المستخدم الفريد") })
            OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("الاسم الظاهر") })
        }
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text("كلمة المرور - 8 أحرف على الأقل") })
        Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank() && password.length >= 8 && (!register || (username.length >= 3 && displayName.isNotBlank())), onClick = {
            busy = true; status = "جارٍ الاتصال بالخادم... قد يستغرق أول اتصال قرابة دقيقة"
            scope.launch { try { val result = if (register) api.register(RegisterRequest(email, username, password, displayName)) else api.login(LoginRequest(email, password)); status = "تم بنجاح"; onAuthenticated(result) } catch (e: Exception) { status = messageFor(e) }; busy = false }
        }) { Text(if (register) "إنشاء الحساب" else "دخول") }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = { register = !register; status = "" }) { Text(if (register) "لديك حساب؟ تسجيل الدخول" else "ليس لديك حساب؟ إنشاء حساب") }
        if (status.isNotBlank()) Text(status)
    }
}

@Composable
fun MainShell(token: String, refreshToken: String, onSignedOut: () -> Unit) {
    val auth = "Bearer $token"; var tab by remember { mutableStateOf("home") }; var me by remember { mutableStateOf<UserDto?>(null) }; var status by remember { mutableStateOf("جارٍ تحميل حسابك...") }; val scope = rememberCoroutineScope()
    fun reloadMe() { scope.launch { try { me = api.me(auth); status = "" } catch (e: Exception) { status = messageFor(e); if (e is HttpException && e.code() == 401) onSignedOut() } } }
    LaunchedEffect(token) { reloadMe() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ALIQO", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { tab = "home" }, modifier = Modifier.weight(1f)) { Text("الرئيسية") }
            Button(onClick = { tab = "friends" }, modifier = Modifier.weight(1f)) { Text("الأصدقاء") }
            Button(onClick = { tab = "profile" }, modifier = Modifier.weight(1f)) { Text("الملف") }
        }
        Spacer(Modifier.height(10.dp)); if (status.isNotBlank()) Text(status)
        when (tab) { "friends" -> FriendsScreen(auth); "profile" -> ProfileScreen(auth, me, { reloadMe() }, refreshToken, onSignedOut); else -> HomeScreen(me) }
    }
}

@Composable
fun HomeScreen(me: UserDto?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("مرحبًا ${me?.profile?.displayName ?: me?.username.orEmpty()}", style = MaterialTheme.typography.headlineSmall); Text("@${me?.username.orEmpty()}"); Text("الحساب: ${me?.role ?: "USER"}") } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("⚡ التحديات", fontWeight = FontWeight.Bold, fontSize = 22.sp); Text("المحرك الاجتماعي الرئيسي سيُفعّل في الدفعة الثالثة بعد تثبيت المحادثات في الدفعة الثانية.") } }
        Text("الدفعة الأولى: الحسابات والملف الشخصي والأصدقاء مرتبطة بالخادم الحقيقي.")
    }
}

@Composable
fun FriendsScreen(auth: String) {
    var query by remember { mutableStateOf("") }; var results by remember { mutableStateOf<List<UserDto>>(emptyList()) }; var friends by remember { mutableStateOf<List<UserDto>>(emptyList()) }; var status by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    fun loadFriends() { scope.launch { try { friends = api.friends(auth) } catch (e: Exception) { status = messageFor(e) } } }; LaunchedEffect(auth) { loadFriends() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("الأصدقاء", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(query, { query = it.lowercase().replace(" ", "") }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("ابحث باسم المستخدم") })
        Button(onClick = { busy = true; scope.launch { try { results = api.searchUsers(auth, query); status = if (results.isEmpty()) "لا توجد نتائج" else "" } catch (e: Exception) { status = messageFor(e) }; busy = false } }, modifier = Modifier.fillMaxWidth(), enabled = !busy && query.length >= 2) { Text("بحث") }
        if (status.isNotBlank()) Text(status); if (results.isNotEmpty()) Text("نتائج البحث", fontWeight = FontWeight.Bold)
        results.forEach { user -> UserCard(user, "إضافة", { scope.launch { try { val r = api.addFriend(auth, user.id); status = if (r.status == "ACCEPTED") "تمت إضافة @${user.username} كصديق" else "تم إرسال طلب الصداقة إلى @${user.username}"; loadFriends() } catch (e: Exception) { status = messageFor(e) } } }, "حظر", { scope.launch { try { api.blockUser(auth, user.id); results = results.filterNot { it.id == user.id }; status = "تم حظر @${user.username}"; loadFriends() } catch (e: Exception) { status = messageFor(e) } } }) }
        Text("قائمة أصدقائك (${friends.size})", fontWeight = FontWeight.Bold); if (friends.isEmpty()) Text("لا يوجد أصدقاء حتى الآن. ابحث عن مستخدم وأرسل له طلبًا.")
        friends.forEach { user -> UserCard(user, "حذف", { scope.launch { try { api.removeFriend(auth, user.id); status = "تم حذف @${user.username} من الأصدقاء"; loadFriends() } catch (e: Exception) { status = messageFor(e) } } }, "حظر", { scope.launch { try { api.blockUser(auth, user.id); status = "تم حظر @${user.username}"; loadFriends() } catch (e: Exception) { status = messageFor(e) } } }) }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun UserCard(user: UserDto, primaryLabel: String, onPrimary: () -> Unit, secondaryLabel: String, onSecondary: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(user.profile?.displayName?.ifBlank { user.username } ?: user.username, fontWeight = FontWeight.Bold); Text("@${user.username}"); user.profile?.bio?.takeIf { it.isNotBlank() }?.let { Text(it) }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onPrimary) { Text(primaryLabel) }; OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) } } } }
}

@Composable
fun ProfileScreen(auth: String, me: UserDto?, onProfileUpdated: () -> Unit, refreshToken: String, onSignedOut: () -> Unit) {
    var displayName by remember(me?.profile?.displayName) { mutableStateOf(me?.profile?.displayName ?: "") }; var bio by remember(me?.profile?.bio) { mutableStateOf(me?.profile?.bio ?: "") }; var status by remember { mutableStateOf("") }; var confirmDelete by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("حذف الحساب؟") }, text = { Text("سيتم تعطيل الحساب وحذف جلساتك. لا يمكن التراجع من داخل التطبيق.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { try { api.deleteAccount(auth); onSignedOut() } catch (e: Exception) { status = messageFor(e) } } }) { Text("حذف نهائي") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ملفي الشخصي", style = MaterialTheme.typography.headlineSmall); Text("@${me?.username.orEmpty()}"); me?.email?.let { Text(it) }
        OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text("الاسم الظاهر") }); OutlinedTextField(bio, { if (it.length <= 280) bio = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("نبذة مختصرة (${bio.length}/280)") })
        Button(onClick = { scope.launch { try { api.updateProfile(auth, UpdateProfileRequest(displayName.trim(), bio.trim())); status = "تم حفظ الملف الشخصي"; onProfileUpdated() } catch (e: Exception) { status = messageFor(e) } } }, modifier = Modifier.fillMaxWidth(), enabled = displayName.isNotBlank()) { Text("حفظ التغييرات") }
        OutlinedButton(onClick = { scope.launch { try { if (refreshToken.isNotBlank()) api.logout(mapOf("refreshToken" to refreshToken)) } catch (_: Exception) {}; onSignedOut() } }, modifier = Modifier.fillMaxWidth()) { Text("تسجيل الخروج") }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("حذف الحساب") }
        if (status.isNotBlank()) Text(status); Spacer(Modifier.height(30.dp))
    }
}
