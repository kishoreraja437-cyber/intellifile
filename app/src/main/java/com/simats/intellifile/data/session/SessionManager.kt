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
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_IMPORTANT_ALERTS = "important_alerts"
        private const val KEY_DEADLINE_REMINDERS = "deadline_reminders"
        private const val KEY_NEW_DOC_ALERTS = "new_doc_alerts"
        private const val KEY_QUIET_HOURS = "quiet_hours"
    }

    fun saveUserSession(token: String, user: UserDto) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putInt(KEY_USER_ID, user.id)
            putString(KEY_FULL_NAME, user.full_name)
            putString(KEY_EMAIL, user.email)
            if (!user.profile_image.isNullOrBlank()) {
                putString(KEY_PROFILE_IMAGE, user.profile_image)
            }
            putBoolean(KEY_IS_LOGGED_IN, true)
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
            apply()
        }
    }

    fun isImportantAlertsEnabled(): Boolean = prefs.getBoolean(KEY_IMPORTANT_ALERTS, true)
    fun isDeadlineRemindersEnabled(): Boolean = prefs.getBoolean(KEY_DEADLINE_REMINDERS, true)
    fun isNewDocAlertsEnabled(): Boolean = prefs.getBoolean(KEY_NEW_DOC_ALERTS, true)
    fun isQuietHoursEnabled(): Boolean = prefs.getBoolean(KEY_QUIET_HOURS, false)

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
