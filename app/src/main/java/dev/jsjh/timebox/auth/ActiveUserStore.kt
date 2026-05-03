package dev.jsjh.timebox.auth

import android.content.Context

object ActiveUserStore {
    private const val PREFS_NAME = "timeboxing_active_user"
    private const val KEY_USER_ID = "user_id"
    private const val GUEST_USER_ID = "guest"

    fun saveLoggedIn(context: Context, userId: String) {
        writeUserId(context, userId.ifBlank { GUEST_USER_ID })
    }

    fun saveGuest(context: Context) {
        writeUserId(context, GUEST_USER_ID)
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_USER_ID)
            .apply()
    }

    fun readUserId(context: Context): String {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: GUEST_USER_ID
    }

    private fun writeUserId(context: Context, userId: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_ID, userId)
            .apply()
    }
}
