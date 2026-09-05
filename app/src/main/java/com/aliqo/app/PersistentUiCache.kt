package com.aliqo.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PersistentUiCache {
    private const val PREFS = "aliqo_ui_cache"
    private val gson = Gson()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun has(context: Context, key: String): Boolean = prefs(context).contains(key)

    fun loadUser(context: Context, key: String): UserDto? = load(context, key)
    fun saveUser(context: Context, key: String, value: UserDto?) = save(context, key, value)

    fun loadUsers(context: Context, key: String): List<UserDto> = loadList(context, key)
    fun saveUsers(context: Context, key: String, value: List<UserDto>) = save(context, key, value)

    fun loadFriendRequests(context: Context, key: String): List<FriendRequestDto> = loadList(context, key)
    fun saveFriendRequests(context: Context, key: String, value: List<FriendRequestDto>) = save(context, key, value)

    fun loadNotifications(context: Context, key: String): List<NotificationDto> = loadList(context, key)
    fun saveNotifications(context: Context, key: String, value: List<NotificationDto>) = save(context, key, value)

    fun loadRooms(context: Context, key: String): List<RoomDto> = loadList(context, key)
    fun saveRooms(context: Context, key: String, value: List<RoomDto>) = save(context, key, value)

    fun loadChat(context: Context, key: String): ChatDto? = load(context, key)
    fun saveChat(context: Context, key: String, value: ChatDto?) = save(context, key, value)

    fun loadMessages(context: Context, key: String): List<MessageDto> = loadList(context, key)
    fun saveMessages(context: Context, key: String, value: List<MessageDto>) = save(context, key, value)

    private inline fun <reified T> load(context: Context, key: String): T? {
        val raw = prefs(context).getString(key, null) ?: return null
        return try { gson.fromJson(raw, T::class.java) } catch (_: Exception) { null }
    }

    private inline fun <reified T> loadList(context: Context, key: String): List<T> {
        val raw = prefs(context).getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<T>>() {}.type
            gson.fromJson<List<T>>(raw, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, key: String, value: Any?) {
        if (value == null) prefs(context).edit().remove(key).apply()
        else prefs(context).edit().putString(key, gson.toJson(value)).apply()
    }
}
