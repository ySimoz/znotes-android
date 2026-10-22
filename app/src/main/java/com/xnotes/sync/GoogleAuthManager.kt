package com.xnotes.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

object GoogleAuthManager {
    private const val PREFS_NAME = "xnotes_google_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_CODE_VERIFIER = "code_verifier"

    const val DRIVE_SCOPE_STR = "https://www.googleapis.com/auth/drive.file"
    val DRIVE_SCOPE = Scope(DRIVE_SCOPE_STR)

    const val REDIRECT_URI = "com.xnotes://oauth2redirect"
    const val SCOPES = "$DRIVE_SCOPE_STR https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile"

    private fun getPrefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSignInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(DRIVE_SCOPE)
            .build()

    fun getSignInClient(context: Context): GoogleSignInClient =
        GoogleSignIn.getClient(context, getSignInOptions())

    fun getSignInIntent(context: Context): Intent =
        getSignInClient(context).signInIntent

    fun isConnected(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && GoogleSignIn.hasPermissions(account, DRIVE_SCOPE)) {
            return true
        }
        val prefs = getPrefs(context)
        return !prefs.getString(KEY_REFRESH_TOKEN, null).isNullOrEmpty()
    }

    fun getUserEmail(context: Context): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) return account.email
        return getPrefs(context).getString(KEY_USER_EMAIL, null)
    }

    fun getUserName(context: Context): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) return account.displayName ?: account.email
        return getPrefs(context).getString(KEY_USER_NAME, null)
    }

    suspend fun handleSignInResult(context: Context, intent: Intent?): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(ApiException::class.java)
            val email = account.email ?: "Google Account"
            val name = account.displayName ?: email
            getPrefs(context).edit().apply {
                putString(KEY_USER_EMAIL, email)
                putString(KEY_USER_NAME, name)
            }.apply()
            Result.success(email to name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getValidAccessToken(context: Context, customClientId: String? = null): String? = withContext(Dispatchers.IO) {
        // 1. Try Google Play Services native token retrieval
        val nativeAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (nativeAccount != null && GoogleSignIn.hasPermissions(nativeAccount, DRIVE_SCOPE)) {
            val gAccount = nativeAccount.account ?: nativeAccount.email?.let { android.accounts.Account(it, "com.google") }
            if (gAccount != null) {
                try {
                    val token = GoogleAuthUtil.getToken(context, gAccount, "oauth2:$DRIVE_SCOPE_STR")
                    if (!token.isNullOrEmpty()) return@withContext token
                } catch (e: Exception) {
                    android.util.Log.e("GoogleAuthManager", "GoogleAuthUtil.getToken failed", e)
                }
            } else {
                android.util.Log.e("GoogleAuthManager", "Both nativeAccount.account and nativeAccount.email are null")
            }
        } else {
            android.util.Log.e("GoogleAuthManager", "nativeAccount is null or lacks permissions: $nativeAccount")
        }

        // 2. Fall back to manual OAuth SharedPreferences cache
        val prefs = getPrefs(context)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val currentToken = prefs.getString(KEY_ACCESS_TOKEN, null)

        if (!currentToken.isNullOrEmpty() && System.currentTimeMillis() + 60_000L < expiresAt) {
            return@withContext currentToken
        }

        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return@withContext null
        if (customClientId.isNullOrEmpty()) return@withContext null

        try {
            val params = mapOf(
                "client_id" to customClientId,
                "refresh_token" to refreshToken,
                "grant_type" to "refresh_token",
            )
            val tokenResp = postForm("https://oauth2.googleapis.com/token", params)
            val json = JSONObject(tokenResp)
            val newAccessToken = json.getString("access_token")
            val expiresIn = json.getLong("expires_in")
            val newExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

            prefs.edit().apply {
                putString(KEY_ACCESS_TOKEN, newAccessToken)
                putLong(KEY_EXPIRES_AT, newExpiresAt)
            }.apply()

            newAccessToken
        } catch (e: Exception) {
            null
        }
    }

    fun invalidateToken(context: Context, token: String) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (_: Exception) {}
        getPrefs(context).edit().remove(KEY_ACCESS_TOKEN).apply()
    }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        try {
            getSignInClient(context).signOut()
        } catch (_: Exception) {}
        val prefs = getPrefs(context)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        if (!token.isNullOrEmpty()) {
            try {
                postForm("https://oauth2.googleapis.com/revoke", mapOf("token" to token))
            } catch (_: Exception) {}
        }
        prefs.edit().clear().apply()
    }

    suspend fun handleRedirect(context: Context, uri: Uri, customClientId: String? = null): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val code = uri.getQueryParameter("code")
                ?: return@withContext Result.failure(IllegalArgumentException(uri.getQueryParameter("error") ?: "Missing auth code"))

            val prefs = getPrefs(context)
            val verifier = prefs.getString(KEY_CODE_VERIFIER, null)
                ?: return@withContext Result.failure(IllegalStateException("No code verifier found"))

            val clientId = customClientId?.ifEmpty { null }
                ?: return@withContext Result.failure(IllegalStateException("Custom client ID required for browser flow"))

            val params = mapOf(
                "client_id" to clientId,
                "code" to code,
                "code_verifier" to verifier,
                "grant_type" to "authorization_code",
                "redirect_uri" to REDIRECT_URI,
            )

            val tokenResp = postForm("https://oauth2.googleapis.com/token", params)
            val json = JSONObject(tokenResp)

            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token")
            val expiresIn = json.getLong("expires_in")
            val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

            val userInfo = fetchUserInfo(accessToken)

            prefs.edit().apply {
                putString(KEY_ACCESS_TOKEN, accessToken)
                if (refreshToken.isNotEmpty()) putString(KEY_REFRESH_TOKEN, refreshToken)
                putLong(KEY_EXPIRES_AT, expiresAt)
                putString(KEY_USER_EMAIL, userInfo.first)
                putString(KEY_USER_NAME, userInfo.second)
                remove(KEY_CODE_VERIFIER)
            }.apply()

            Result.success(userInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchUserInfo(accessToken: String): Pair<String, String> {
        val conn = URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        val email = json.optString("email", "Google Account")
        val name = json.optString("name", email)
        return email to name
    }

    private fun postForm(urlStr: String, params: Map<String, String>): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.connectTimeout = 12_000
        conn.readTimeout = 12_000

        val body = params.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body) }

        if (conn.responseCode in 200..299) {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            throw IllegalStateException("HTTP ${conn.responseCode}: $err")
        }
    }
}
