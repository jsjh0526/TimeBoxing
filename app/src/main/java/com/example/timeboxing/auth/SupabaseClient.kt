package com.example.timeboxing.auth

import android.content.Context
import android.content.SharedPreferences
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SESSION_KEY = "session"

private val sessionJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private class SharedPreferencesSessionManager(
    private val prefs: SharedPreferences
) : SessionManager {
    override suspend fun loadSession(): UserSession? {
        val encoded = prefs.getString(SESSION_KEY, null) ?: return null
        return runCatching { sessionJson.decodeFromString<UserSession>(encoded) }.getOrNull()
    }

    override suspend fun saveSession(session: UserSession) {
        prefs.edit()
            .putString(SESSION_KEY, sessionJson.encodeToString(session))
            .apply()
    }

    override suspend fun deleteSession() {
        prefs.edit().remove(SESSION_KEY).apply()
    }
}

lateinit var supabase: SupabaseClient
    private set

fun initSupabase(context: Context) {
    if (::supabase.isInitialized) return
    val prefs = context.applicationContext.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
    supabase = createSupabaseClient(
        supabaseUrl = "https://vimywtpiqsixlfiegpdd.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZpbXl3dHBpcXNpeGxmaWVncGRkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzcyODI3MDUsImV4cCI6MjA5Mjg1ODcwNX0.W4f-v9KlWd6LdRgCYnz7GvrJ9NvRzDz4QjJMOAwmRoM"
    ) {
        install(Auth) {
            sessionManager = SharedPreferencesSessionManager(prefs)
        }
        install(Postgrest)
    }
}
