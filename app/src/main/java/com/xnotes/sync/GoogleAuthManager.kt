package com.xnotes.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
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

    const val REDIRECT_URI = "com.xnotes://oauth2redirect"
    const val SCOPES = "https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile"

    // Default OAuth client ID for zNotes; users can optionally supply their own in Settings
    const val DEFAULT_CLIENT_ID = "615967008128-4n3k6p5t7q7v8a9b1c2d3e4f5g6h7i8j.apps.googleusercontent.com"

    private fun getPrefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isConnected(context: Context): Boolean {
        val prefs = getPrefs(context)
        return !prefs.getString(KEY_REFRESH_TOKEN, null).isNullOrEmpty()
    }

    fun getUserEmail(context: Context): String? = getPrefs(context).getString(KEY_USER_EMAIL, null)
    fun getUserName(context: Context): String? = getPrefs(context).getString(KEY_USER_NAME, null)

    fun createAuthIntent(context: Context, customClientId: String? = null): Intent {
        val clientId = customClientId?.ifEmpty { null } ?: DEFAULT_CLIENT_ID
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)

        getPrefs(context).edit().putString(KEY_CODE_VERIFIER, verifier).apply()

        val authUrl = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()

        return Intent(Intent.ACTION_VIEW, authUrl).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun handleRedirect(context: Context, uri: Uri, customClientId: String? = null): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val code = uri.getQueryParameter("code")
                ?: return@withContext Result.failure(IllegalArgumentException(uri.getQueryParameter("error") ?: "Missing auth code"))

            val prefs = getPrefs(context)
            val verifier = prefs.getString(KEY_CODE_VERIFIER, null)
                ?: return@withContext Result.failure(IllegalStateException("No code verifier found"))

            val clientId = customClientId?.ifEmpty { null } ?: DEFAULT_CLIENT_ID

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

    suspend fun getValidAccessToken(context: Context, customClientId: String? = null): String? = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val currentToken = prefs.getString(KEY_ACCESS_TOKEN, null)

        // Return current token if it has at least 60 seconds of validity remaining
        if (!currentToken.isNullOrEmpty() && System.currentTimeMillis() + 60_000L < expiresAt) {
            return@withContext currentToken
        }

        // Token expired; attempt refresh
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return@withContext null
        val clientId = customClientId?.ifEmpty { null } ?: DEFAULT_CLIENT_ID

        try {
            val params = mapOf(
                "client_id" to clientId,
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

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        if (!token.isNullOrEmpty()) {
            try {
                postForm("https://oauth2.googleapis.com/revoke", mapOf("token" to token))
            } catch (_: Exception) { }
        }
        prefs.edit().clear().apply()
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

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
