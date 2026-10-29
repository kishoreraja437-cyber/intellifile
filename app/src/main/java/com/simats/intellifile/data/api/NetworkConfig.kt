package com.simats.intellifile.data.api

import android.content.Context
import android.os.Build

object NetworkConfig {
    private const val PREF_NAME = "intellifile_network"
    private const val KEY_CUSTOM_BASE_URL = "custom_base_url"

    /**
     * Common Universal Base URL:
     * Accessible by both physical Android phones (via Wi-Fi / cellular data) and Android Emulators.
     */
    const val COMMON_BASE_URL = "https://tn89blc0-80.inc1.devtunnels.ms/intellifile_backend/"

    /**
     * Local LAN Base URL:
     * Use when your physical Android phone and PC are connected to the same Wi-Fi network.
     */
    const val LOCAL_LAN_URL = "http://172.23.176.96/intellifile_backend/"

    /**
     * Android Emulator loopback URL (10.0.2.2 maps to 127.0.0.1 on the host machine).
     */
    const val EMULATOR_LOOPBACK_URL = "http://10.0.2.2/intellifile_backend/"

    /**
     * Default base URL used across all network calls.
     * Uses COMMON_BASE_URL so it works seamlessly on both real phones and emulators.
     */
    const val DEFAULT_BASE_URL = COMMON_BASE_URL

    @Deprecated("Use COMMON_BASE_URL or DEFAULT_BASE_URL")
    const val DEFAULT_EMULATOR_URL = COMMON_BASE_URL

    /**
     * Resolves the active base URL for backend network requests.
     * Prefers custom configured URL if saved in settings, otherwise defaults to COMMON_BASE_URL.
     */
    fun getBaseUrl(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val custom = prefs.getString(KEY_CUSTOM_BASE_URL, null)
        if (!custom.isNullOrBlank()) {
            return if (custom.endsWith("/")) custom else "$custom/"
        }
        return DEFAULT_BASE_URL
    }

    /**
     * Saves a custom server base URL (e.g. for switching between LAN IP or public tunnel).
     */
    fun setCustomBaseUrl(context: Context, url: String?) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_BASE_URL, url?.trim()).apply()
        RetrofitClient.resetClient()
    }

    /**
     * Resets active base URL back to the universal COMMON_BASE_URL.
     */
    fun resetToDefaultBaseUrl(context: Context) {
        setCustomBaseUrl(context, null)
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
