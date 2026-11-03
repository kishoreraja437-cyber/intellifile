package com.simats.intellifile.data.session

import android.content.Context
import android.content.SharedPreferences
import com.simats.intellifile.data.model.UserDto
import com.simats.intellifile.data.model.UserSettingsDto

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "intellifile_session"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PROFILE_IMAGE = "profile_image"
        private const val KEY_PROFILE_IMAGE_PATH = "profile_image_path"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_PROFILE_CACHED = "profile_data_cached"
        private const val KEY_PROFILE_CACHED_AT = "profile_data_cached_at"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_IMPORTANT_ALERTS = "important_alerts"
        private const val KEY_DEADLINE_REMINDERS = "deadline_reminders"
        private const val KEY_NEW_DOC_ALERTS = "new_doc_alerts"
        private const val KEY_QUIET_HOURS = "quiet_hours"
        private const val KEY_SETTINGS_CACHED = "settings_data_cached"
        private const val KEY_FCM_TOKEN = "fcm_token"
    }

    /**
     * Saves user authentication session upon login or signup.
     * Note: Does not mark KEY_PROFILE_CACHED so the first Profile screen visit
     * fetches full profile details from the server, satisfying the requirement:
     * "First profile opening -> API call -> Save locally. Subsequent openings -> Load from cache -> No API call."
     */
    fun saveUserSession(token: String, user: UserDto) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putInt(KEY_USER_ID, user.id)
            putString(KEY_FULL_NAME, user.full_name)
            putString(KEY_EMAIL, user.email)
            if (!user.profile_image.isNullOrBlank()) {
                putString(KEY_PROFILE_IMAGE, user.profile_image)
            }
            if (!user.created_at.isNullOrBlank()) {
                putString(KEY_CREATED_AT, user.created_at)
            }
            putBoolean(KEY_IS_LOGGED_IN, true)
            // Reset profile cached flag on fresh login so first profile opening fetches once
            putBoolean(KEY_PROFILE_CACHED, false)
            apply()
        }
    }

    /**
     * Checks if profile data is already available and cached in local storage.
     */
    fun hasCachedProfile(): Boolean {
        val hasId = getUserId() > 0
        val hasEmail = getEmail().isNotBlank()
        val isMarkedCached = prefs.getBoolean(KEY_PROFILE_CACHED, false)
        return hasId && hasEmail && isMarkedCached
    }

    /**
     * Loads the saved profile data immediately from local storage.
     */
    fun getCachedProfile(): UserDto? {
        val id = getUserId()
        val email = getEmail()
        val name = getFullName()
        if (id > 0 && email.isNotBlank()) {
            return UserDto(
                id = id,
                full_name = name.ifBlank { "User" },
                email = email,
                profile_image = getProfileImage(),
                profile_image_path = prefs.getString(KEY_PROFILE_IMAGE_PATH, null),
                created_at = prefs.getString(KEY_CREATED_AT, null)
            )
        }
        return null
    }

    /**
     * Saves the full profile data locally and marks it as cached.
     */
    fun saveCachedProfile(user: UserDto) {
        val resolvedImage = com.simats.intellifile.data.api.NetworkConfig.resolveMediaUrl(user.profile_image)
        prefs.edit().apply {
            putInt(KEY_USER_ID, user.id)
            putString(KEY_FULL_NAME, user.full_name)
            putString(KEY_EMAIL, user.email)
            if (!resolvedImage.isNullOrBlank()) {
                putString(KEY_PROFILE_IMAGE, resolvedImage)
            } else if (user.profile_image == null) {
                remove(KEY_PROFILE_IMAGE)
            }
            if (!user.profile_image_path.isNullOrBlank()) {
                putString(KEY_PROFILE_IMAGE_PATH, user.profile_image_path)
            } else if (user.profile_image_path == null) {
                remove(KEY_PROFILE_IMAGE_PATH)
            }
            if (!user.created_at.isNullOrBlank()) {
                putString(KEY_CREATED_AT, user.created_at)
            }
            putBoolean(KEY_PROFILE_CACHED, true)
            putLong(KEY_PROFILE_CACHED_AT, System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Invalidates the local profile cache (e.g. for forced refresh).
     */
    fun invalidateProfileCache() {
        prefs.edit().apply {
            putBoolean(KEY_PROFILE_CACHED, false)
            putLong(KEY_PROFILE_CACHED_AT, 0L)
            apply()
        }
    }

    fun updateProfile(name: String, email: String, profileImage: String?) {
        val resolvedImage = com.simats.intellifile.data.api.NetworkConfig.resolveMediaUrl(profileImage)
        prefs.edit().apply {
            putString(KEY_FULL_NAME, name)
            putString(KEY_EMAIL, email)
            if (!resolvedImage.isNullOrBlank()) {
                putString(KEY_PROFILE_IMAGE, resolvedImage)
            } else if (profileImage == null) {
                remove(KEY_PROFILE_IMAGE)
            }
            putBoolean(KEY_PROFILE_CACHED, true)
            putLong(KEY_PROFILE_CACHED_AT, System.currentTimeMillis())
            apply()
        }
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, 0)
    fun getFullName(): String = prefs.getString(KEY_FULL_NAME, "") ?: ""
    fun getEmail(): String = prefs.getString(KEY_EMAIL, "") ?: ""
    fun getProfileImage(): String? {
        val raw = prefs.getString(KEY_PROFILE_IMAGE, null)
        return com.simats.intellifile.data.api.NetworkConfig.resolveMediaUrl(raw)
    }
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false) && !getToken().isNullOrBlank()

    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "LIGHT") ?: "LIGHT"
    fun saveThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun hasCachedSettings(): Boolean = prefs.getBoolean(KEY_SETTINGS_CACHED, false)

    fun markSettingsCached() {
        prefs.edit().putBoolean(KEY_SETTINGS_CACHED, true).apply()
    }

    fun getNotificationSettings(): UserSettingsDto {
        return UserSettingsDto(
            theme_mode = getThemeMode(),
            important_alerts = prefs.getBoolean(KEY_IMPORTANT_ALERTS, true),
            deadline_reminders = prefs.getBoolean(KEY_DEADLINE_REMINDERS, true),
            new_doc_alerts = prefs.getBoolean(KEY_NEW_DOC_ALERTS, true),
            quiet_hours = prefs.getBoolean(KEY_QUIET_HOURS, false)
        )
    }

    fun saveNotificationSettings(
        importantAlerts: Boolean,
        deadlineReminders: Boolean,
        newDocAlerts: Boolean,
        quietHours: Boolean
    ) {
        prefs.edit().apply {
            putBoolean(KEY_IMPORTANT_ALERTS, importantAlerts)
            putBoolean(KEY_DEADLINE_REMINDERS, deadlineReminders)
            putBoolean(KEY_NEW_DOC_ALERTS, newDocAlerts)
            putBoolean(KEY_QUIET_HOURS, quietHours)
            putBoolean(KEY_SETTINGS_CACHED, true)
            apply()
        }
    }

    fun isImportantAlertsEnabled(): Boolean = prefs.getBoolean(KEY_IMPORTANT_ALERTS, true)
    fun isDeadlineRemindersEnabled(): Boolean = prefs.getBoolean(KEY_DEADLINE_REMINDERS, true)
    fun isNewDocAlertsEnabled(): Boolean = prefs.getBoolean(KEY_NEW_DOC_ALERTS, true)
    fun isQuietHoursEnabled(): Boolean = prefs.getBoolean(KEY_QUIET_HOURS, false)

    fun saveFcmToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getFcmToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)


    /**
     * Clears all cached session, profile, and settings data on logout.
     */
    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
