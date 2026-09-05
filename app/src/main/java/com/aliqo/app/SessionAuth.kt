package com.aliqo.app

import android.content.Context
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Central session transport used by authenticated REST clients.
 * It always substitutes the newest access token and refreshes/retries once on 401.
 */
object SessionAuth {
    @Volatile private var appContext: Context? = null
    @Volatile private var accessToken: String = ""
    @Volatile private var refreshToken: String = ""
    private val refreshLock = Any()

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        val prefs = context.getSharedPreferences("aliqo_session", Context.MODE_PRIVATE)
        if (accessToken.isBlank()) accessToken = prefs.getString("accessToken", "").orEmpty()
        if (refreshToken.isBlank()) refreshToken = prefs.getString("refreshToken", "").orEmpty()
    }

    fun saveTokens(access: String, refresh: String) {
        accessToken = access
        refreshToken = refresh
        appContext?.getSharedPreferences("aliqo_session", Context.MODE_PRIVATE)?.edit()
            ?.putString("accessToken", access)
            ?.putString("refreshToken", refresh)
            ?.apply()
    }

    fun clear() {
        accessToken = ""
        refreshToken = ""
        appContext?.getSharedPreferences("aliqo_session", Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
    }

    fun currentAccess(): String = accessToken
    fun currentRefresh(): String = refreshToken
    fun authHeader(fallback: String = ""): String = if (accessToken.isNotBlank()) "Bearer $accessToken" else fallback

    private fun refreshBlocking(failedToken: String?): String? = synchronized(refreshLock) {
        if (accessToken.isNotBlank() && failedToken != null && failedToken != accessToken) return@synchronized accessToken
        val refresh = refreshToken
        if (refresh.isBlank()) return@synchronized null
        return@synchronized try {
            val body = JSONObject().put("refreshToken", refresh).toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = okhttp3.Request.Builder()
                .url(BuildConfig.API_BASE_URL + "auth/refresh")
                .post(body)
                .build()
            val response = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
            response.use {
                if (!it.isSuccessful) return@synchronized null
                val json = JSONObject(it.body?.string().orEmpty())
                val nextAccess = json.optString("accessToken")
                val nextRefresh = json.optString("refreshToken")
                if (nextAccess.isBlank() || nextRefresh.isBlank()) return@synchronized null
                saveTokens(nextAccess, nextRefresh)
                nextAccess
            }
        } catch (_: Exception) {
            null
        }
    }

    private val tokenInterceptor = Interceptor { chain ->
        val original = chain.request()
        val hasAuthorization = original.header("Authorization") != null
        val token = accessToken
        val request = if (hasAuthorization && token.isNotBlank()) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else original
        chain.proceed(request)
    }

    private val tokenAuthenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): okhttp3.Request? {
            var count = 1
            var prior = response.priorResponse
            while (prior != null) { count++; prior = prior.priorResponse }
            if (count >= 2) return null
            val failed = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
            val fresh = refreshBlocking(failed) ?: return null
            return response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
        }
    }

    fun clientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .addInterceptor(tokenInterceptor)
        .authenticator(tokenAuthenticator)
}
