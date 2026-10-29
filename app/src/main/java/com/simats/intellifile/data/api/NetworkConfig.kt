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
     */
    fun getBaseUrl(context: Context? = null): String {
        if (context != null) {
            val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val custom = prefs.getString(KEY_CUSTOM_BASE_URL, null)
            if (!custom.isNullOrBlank()) {
                return if (custom.endsWith("/")) custom else "$custom/"
            }
        }
        return if (BASE_URL.endsWith("/")) BASE_URL else "$BASE_URL/"
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
