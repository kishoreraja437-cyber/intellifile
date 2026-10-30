package com.simats.intellifile.data.api

import android.content.Context

object NetworkConfig {
    /**
     * Single unified Base URL used across the entire application.
     * Edit this single line whenever your server or devtunnel URL changes.
     */
    const val BASE_URL = "https://tn89blc0-80.inc1.devtunnels.ms/intellifile_backend/"

    // Backwards-compatible aliases
    const val COMMON_BASE_URL = BASE_URL
    const val DEFAULT_BASE_URL = BASE_URL
    const val LOCAL_LAN_URL = BASE_URL
    const val EMULATOR_LOOPBACK_URL = BASE_URL

    private const val PREF_NAME = "intellifile_network"
    private const val KEY_CUSTOM_BASE_URL = "custom_base_url"

    /**
     * Resolves the active base URL for backend network requests.
     * Guaranteed single base URL across the entire app.
     */
    fun getBaseUrl(context: Context? = null): String {
        return if (BASE_URL.endsWith("/")) BASE_URL else "$BASE_URL/"
    }

    /**
     * Resolves relative or localhost media/photo paths into accessible full URLs.
     */
    fun resolveMediaUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val cleaned = rawUrl.trim()
        if (cleaned.contains("localhost") || cleaned.contains("127.0.0.1") || cleaned.contains("10.0.2.2") || cleaned.contains("172.23.176.96")) {
            val path = cleaned.substringAfter("/intellifile_backend/", "").ifBlank {
                cleaned.substringAfter("uploads/", "uploads/")
            }.trimStart('/')
            val relPath = if (path.startsWith("uploads/")) path else "uploads/$path"
            return getBaseUrl() + relPath
        }
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            return cleaned
        }
        val relPath = cleaned.trimStart('/')
        return getBaseUrl() + relPath
    }

    /**
     * Saves a custom server base URL if dynamically changed.
     */
    fun setCustomBaseUrl(context: Context, url: String?) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_BASE_URL, url?.trim()).apply()
        RetrofitClient.resetClient()
    }

    /**
     * Resets active base URL back to default BASE_URL.
     */
    fun resetToDefaultBaseUrl(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CUSTOM_BASE_URL).apply()
        RetrofitClient.resetClient()
    }
}
