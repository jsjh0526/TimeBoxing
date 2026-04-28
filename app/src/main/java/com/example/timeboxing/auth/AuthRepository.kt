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
    data object Guest : AuthState()
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

    suspend fun restoreSession() {
        try {
            supabase.auth.awaitInitialization()
            val hasStoredSession = supabase.auth.currentSessionOrNull() != null || supabase.auth.loadFromStorage()
            if (!hasStoredSession) {
                _authState.value = AuthState.Guest
                return
            }

            val user = supabase.auth.currentUserOrNull()
                ?: runCatching { supabase.auth.retrieveUserForCurrentSession(updateSession = true) }.getOrNull()

            _authState.value = user?.toAuthState() ?: AuthState.Guest
        } catch (_: Exception) {
            _authState.value = AuthState.Guest
        }
    }

    suspend fun signInWithGoogle(context: Context) {
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
            _authState.value = user.toAuthState()
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Login failed")
        }
    }

    suspend fun signOut() {
        try {
            supabase.auth.signOut()
        } catch (_: Exception) {
            supabase.auth.sessionManager.deleteSession()
        } finally {
            _authState.value = AuthState.Guest
        }
    }
}

private fun UserInfo.toAuthState(): AuthState.LoggedIn =
    AuthState.LoggedIn(
        userId = id,
        email = email.orEmpty(),
        displayName = userMetadata?.get("full_name")?.toString()?.trim('"'),
        avatarUrl = userMetadata?.get("avatar_url")?.toString()?.trim('"')
    )
