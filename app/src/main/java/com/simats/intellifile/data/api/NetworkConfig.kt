package com.simats.intellifile.data.api

import android.content.Context
import android.os.Build

object NetworkConfig {
    private const val PREF_NAME = "intellifile_network"
    private const val KEY_CUSTOM_BASE_URL = "custom_base_url"

    // Default base URL for Android Emulator
    const val DEFAULT_EMULATOR_URL = "http://10.0.2.2/intellifile_backend/"

    /**
     * Resolves the active base URL for backend network requests.
     * Prefers custom configured IP if provided, otherwise defaults to 10.0.2.2 for emulator.
     */
    fun getBaseUrl(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val custom = prefs.getString(KEY_CUSTOM_BASE_URL, null)
        if (!custom.isNullOrBlank()) {
            return if (custom.endsWith("/")) custom else "$custom/"
        }
        return DEFAULT_EMULATOR_URL
    }

    /**
     * Saves a custom server base URL (e.g. for physical devices on local Wi-Fi: http://192.168.1.5/intellifile_backend/).
     */
    fun setCustomBaseUrl(context: Context, url: String?) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_BASE_URL, url?.trim()).apply()
    }

    /**
     * Helper to detect if running on an Android Emulator.
     */
    fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }
}
