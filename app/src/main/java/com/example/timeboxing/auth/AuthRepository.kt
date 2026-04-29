package com.example.timeboxing.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val GOOGLE_WEB_CLIENT_ID = "933064419635-kvv46puq64ec0jgc3usidd0a5jekn1jc.apps.googleusercontent.com"

sealed class AuthState {
    data object SignedOut : AuthState()
    data object Guest   : AuthState()
    data object Loading : AuthState()
    data class LoggedIn(
        val userId: String,
        val email: String,
        val displayName: String?,
        val avatarUrl: String?
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}

object AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // 구글 로그인 전 게스트 모드였는지 여부
    // TimeBoxingApp에서 마이그레이션 다이얼로그 표시 여부 결정에 사용
    var hadGuestSessionBeforeLogin: Boolean = false
        private set

    // ── 세션 복원 ─────────────────────────────────────────────────────────────
    suspend fun restoreSession(context: Context) {
        try {
            supabase.auth.awaitInitialization()
            val hasSession = supabase.auth.currentSessionOrNull() != null
                    || supabase.auth.loadFromStorage()

            if (!hasSession) {
                ActiveUserStore.clear(context)
                _authState.value = AuthState.SignedOut
                return
            }

            val user = supabase.auth.currentUserOrNull()
                ?: runCatching { supabase.auth.retrieveUserForCurrentSession(updateSession = true) }.getOrNull()

            val nextState = user?.toAuthState()
            if (nextState != null) {
                ActiveUserStore.saveLoggedIn(context, nextState.userId)
                _authState.value = nextState
            } else {
                ActiveUserStore.clear(context)
                _authState.value = AuthState.SignedOut
            }
        } catch (_: Exception) {
            ActiveUserStore.clear(context)
            _authState.value = AuthState.SignedOut
        }
    }

    // ── 게스트 모드 진입 ──────────────────────────────────────────────────────
    fun continueAsGuest(context: Context) {
        hadGuestSessionBeforeLogin = false
        ActiveUserStore.saveGuest(context)
        _authState.value = AuthState.Guest
    }

    // ── 구글 로그인 ───────────────────────────────────────────────────────────
    suspend fun signInWithGoogle(context: Context) {
        // 로그인 시도 전 게스트였는지 기록 (마이그레이션 다이얼로그 표시 조건)
        hadGuestSessionBeforeLogin = _authState.value is AuthState.Guest
        try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context = context, request = request)
            val credential = GoogleIdTokenCredential.createFrom(result.credential.data)

            supabase.auth.signInWith(IDToken) {
                provider = Google
                idToken = credential.idToken
            }

            supabase.auth.currentSessionOrNull()?.let { session ->
                supabase.auth.sessionManager.saveSession(session)
            }

            val user = supabase.auth.currentUserOrNull()
                ?: supabase.auth.retrieveUserForCurrentSession(updateSession = true)
            val nextState = user.toAuthState()
            ActiveUserStore.saveLoggedIn(context, nextState.userId)
            _authState.value = nextState
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            if (hadGuestSessionBeforeLogin) {
                _authState.value = AuthState.Guest
                return
            }
            if (message.contains("cancel", ignoreCase = true)) {
                hadGuestSessionBeforeLogin = false
                _authState.value = AuthState.SignedOut
                return
            }
            hadGuestSessionBeforeLogin = false
            _authState.value = AuthState.Error(message.ifBlank { "Login failed" })
        }
    }

    // ── 로그아웃 ──────────────────────────────────────────────────────────────
    suspend fun signOut(context: Context) {
        try {
            supabase.auth.signOut()
        } catch (_: Exception) {
            supabase.auth.sessionManager.deleteSession()
        } finally {
            hadGuestSessionBeforeLogin = false
            ActiveUserStore.clear(context)
            _authState.value = AuthState.SignedOut
        }
    }
}

private fun UserInfo.toAuthState(): AuthState.LoggedIn =
    AuthState.LoggedIn(
        userId      = id,
        email       = email.orEmpty(),
        displayName = userMetadata?.get("full_name")?.toString()?.trim('"'),
        avatarUrl   = userMetadata?.get("avatar_url")?.toString()?.trim('"')
    )
