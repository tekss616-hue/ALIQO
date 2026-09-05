package com.aliqo.app

import androidx.compose.runtime.mutableStateOf

object PlayerProfileNavigation {
    val selectedUserId = mutableStateOf<String?>(null)

    fun open(userId: String) {
        if (userId.isNotBlank()) selectedUserId.value = userId
    }

    fun close() {
        selectedUserId.value = null
    }
}
