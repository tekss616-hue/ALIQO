package com.aliqo.app

import android.content.Context
import com.google.gson.Gson

object PersistentUiCache {
    private const val PREFS = "aliqo_ui_cache"
    private val gson = Gson()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun has(context: Context, key: String): Boolean = prefs(context).contains(key)

    fun loadUser(context: Context, key: String): UserDto? = load(context, key, UserDto::class.java)
    fun saveUser(context: Context, key: String, value: UserDto?) = save(context, key, value)

    fun loadUsers(context: Context, key: String): List<UserDto> = loadArray(context, key, Array<UserDto>::class.java)
    fun saveUsers(context: Context, key: String, value: List<UserDto>) = save(context, key, value)

    fun loadFriendRequests(context: Context, key: String): List<FriendRequestDto> = loadArray(context, key, Array<FriendRequestDto>::class.java)
    fun saveFriendRequests(context: Context, key: String, value: List<FriendRequestDto>) = save(context, key, value)

    fun loadNotifications(context: Context, key: String): List<NotificationDto> = loadArray(context, key, Array<NotificationDto>::class.java)
    fun saveNotifications(context: Context, key: String, value: List<NotificationDto>) = save(context, key, value)

    fun loadRooms(context: Context, key: String): List<RoomDto> = loadArray(context, key, Array<RoomDto>::class.java)
    fun saveRooms(context: Context, key: String, value: List<RoomDto>) = save(context, key, value)

    fun loadChat(context: Context, key: String): ChatDto? = load(context, key, ChatDto::class.java)
    fun saveChat(context: Context, key: String, value: ChatDto?) = save(context, key, value)

    fun loadMessages(context: Context, key: String): List<MessageDto> = loadArray(context, key, Array<MessageDto>::class.java)
    fun saveMessages(context: Context, key: String, value: List<MessageDto>) = save(context, key, value)

    private fun <T> load(context: Context, key: String, clazz: Class<T>): T? {
        val raw = prefs(context).getString(key, null) ?: return null
        return try { gson.fromJson(raw, clazz) } catch (_: Exception) { null }
    }

    private fun <T> loadArray(context: Context, key: String, clazz: Class<Array<T>>): List<T> {
        val raw = prefs(context).getString(key, null) ?: return emptyList()
        return try { gson.fromJson(raw, clazz)?.toList().orEmpty() } catch (_: Exception) { emptyList() }
    }

    private fun save(context: Context, key: String, value: Any?) {
        if (value == null) prefs(context).edit().remove(key).apply()
        else prefs(context).edit().putString(key, gson.toJson(value)).apply()
    }
}
