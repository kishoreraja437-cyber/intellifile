package com.simats.intellifile.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.simats.intellifile.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Secure App Lock Manager for IntelliFile.
 * Manages PIN creation, confirmation, cryptographic verification,
 * Android BiometricPrompt fingerprint authentication, and foreground/background lifecycle state.
 *
 * Security:
 * - Backed by Android Keystore and EncryptedSharedPreferences (AES256-GCM).
 * - PINs are NEVER stored as plain text.
 * - PIN verification uses salted PBKDF2 cryptographic hashing with constant-time equality checks.
 * - No auto-lock or background timers are used.
 */
class AppLockManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = createEncryptedPreferences(context)
    private val sessionManager by lazy { SessionManager(context) }

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _biometricTrigger = MutableStateFlow(0L)
    val biometricTrigger: StateFlow<Long> = _biometricTrigger.asStateFlow()

    @Volatile
    var isBiometricPromptShowing: Boolean = false

    private var isProcessLifecycleAttached = false

    companion object {
        private const val PREFS_FILE = "intellifile_secure_app_lock"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_FINGERPRINT_ENABLED = "fingerprint_enabled"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_LENGTH = "pin_length"

        private const val PBKDF2_ITERATIONS = 10000
        private const val KEY_LENGTH_BITS = 256

        @Volatile
        private var instance: AppLockManager? = null

        /**
         * In-memory session unlock state.
         * Set to true when user unlocks via PIN or Fingerprint.
         * Reset to false when the app moves to background or process restarts.
         */
        @Volatile
        var isAppUnlocked: Boolean = false

        fun getInstance(context: Context): AppLockManager {
            return instance ?: synchronized(this) {
                instance ?: AppLockManager(context.applicationContext).also { instance = it }
            }
        }

        private fun createEncryptedPreferences(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                // Graceful fallback to private SharedPreferences if OEM keystore has an issue
                context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            }
        }
    }

    /**
     * Attaches process-wide lifecycle observer using ProcessLifecycleOwner.
     */
    fun attachToProcessLifecycle() {
        if (isProcessLifecycleAttached) return
        isProcessLifecycleAttached = true
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    onAppForegrounded()
                }

                override fun onStop(owner: LifecycleOwner) {
                    onAppBackgrounded()
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("AppLockManager", "Failed to attach ProcessLifecycleOwner observer", e)
        }
    }

    /**
     * Called when the app moves to background (user pressed Home, switched apps, etc.).
     */
    fun onAppBackgrounded() {
        if (isAppLockEnabled() && sessionManager.isLoggedIn()) {
            isAppUnlocked = false
            _isLocked.value = true
        }
    }

    /**
     * Called when the app returns to foreground.
     */
    fun onAppForegrounded() {
        if (isAppLockEnabled() && sessionManager.isLoggedIn() && !isAppUnlocked) {
            _isLocked.value = true
            _biometricTrigger.value = System.currentTimeMillis()
        }
    }

    /**
     * Immediately marks the app as locked (e.g. on splash completion when App Lock is active).
     */
    fun lockApp() {
        if (isAppLockEnabled() && sessionManager.isLoggedIn()) {
            isAppUnlocked = false
            _isLocked.value = true
            _biometricTrigger.value = System.currentTimeMillis()
        }
    }

    /**
     * Unlocks the app after successful PIN or fingerprint authentication.
     */
    fun unlock() {
        isAppUnlocked = true
        _isLocked.value = false
    }

    /**
     * Checks if App Lock is currently enabled by the user.
     */
    fun isAppLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false) && hasPin()
    }

    /**
     * Enables or disables App Lock.
     */
    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        if (!enabled) {
            unlock()
        }
    }

    /**
     * Checks if Fingerprint Unlock is enabled by the user.
     */
    fun isFingerprintEnabled(): Boolean {
        return prefs.getBoolean(KEY_FINGERPRINT_ENABLED, false) && canUseBiometrics()
    }

    /**
     * Enables or disables Fingerprint Unlock.
     */
    fun setFingerprintEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FINGERPRINT_ENABLED, enabled).apply()
    }

    /**
     * Checks whether biometric (fingerprint) hardware is available and enrolled on the device.
     */
    fun canUseBiometrics(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Checks if a PIN has been set and stored securely.
     */
    fun hasPin(): Boolean {
        val hash = prefs.getString(KEY_PIN_HASH, null)
        val salt = prefs.getString(KEY_PIN_SALT, null)
        return !hash.isNullOrBlank() && !salt.isNullOrBlank()
    }

    /**
     * Gets the configured PIN length (4 or 6 digits).
     */
    fun getPinLength(): Int {
        return prefs.getInt(KEY_PIN_LENGTH, 4)
    }

    /**
     * Sets preferred PIN length (4 or 6).
     */
    fun setPinLength(length: Int) {
        val valid = if (length == 6) 6 else 4
        prefs.edit().putInt(KEY_PIN_LENGTH, valid).apply()
    }

    /**
     * Securely stores a new PIN using a cryptographically random salt
     * and PBKDF2-HMAC-SHA256 key derivation.
     * The plain-text PIN is never persisted.
     */
    fun savePin(pin: String): Boolean {
        if (pin.length != 4 && pin.length != 6) return false
        return try {
            val saltBytes = ByteArray(16)
            SecureRandom().nextBytes(saltBytes)
            val saltBase64 = Base64.encodeToString(saltBytes, Base64.NO_WRAP)

            val hashBase64 = computePbkdf2Hash(pin, saltBytes)

            prefs.edit()
                .putString(KEY_PIN_SALT, saltBase64)
                .putString(KEY_PIN_HASH, hashBase64)
                .putInt(KEY_PIN_LENGTH, pin.length)
                .putBoolean(KEY_APP_LOCK_ENABLED, true)
                .apply()

            isAppUnlocked = true
            _isLocked.value = false
            true
        } catch (e: Exception) {
            android.util.Log.e("AppLockManager", "Error saving PIN", e)
            false
        }
    }

    /**
     * Verifies the entered PIN against the securely stored salted hash.
     * Uses constant-time comparison to prevent timing attacks.
     */
    fun verifyPin(enteredPin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val storedSalt = prefs.getString(KEY_PIN_SALT, null) ?: return false

        return try {
            val saltBytes = Base64.decode(storedSalt, Base64.NO_WRAP)
            val computedHash = computePbkdf2Hash(enteredPin, saltBytes)

            // Constant-time comparison
            MessageDigest.isEqual(
                computedHash.toByteArray(Charsets.UTF_8),
                storedHash.toByteArray(Charsets.UTF_8)
            )
        } catch (e: Exception) {
            android.util.Log.e("AppLockManager", "Error verifying PIN", e)
            false
        }
    }

    /**
     * Clears all PIN and App Lock settings.
     */
    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_APP_LOCK_ENABLED, false)
            .putBoolean(KEY_FINGERPRINT_ENABLED, false)
            .apply()
        unlock()
    }

    /**
     * Prompts Android BiometricPrompt for fingerprint authentication.
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "IntelliFile App Lock",
        subtitle: String = "Confirm fingerprint to unlock",
        negativeButtonText: String = "Use PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        if (!canUseBiometrics()) {
            onError("Biometric hardware not available or no fingerprints enrolled")
            return
        }

        if (isBiometricPromptShowing) {
            android.util.Log.d("AppLockManager", "Biometric prompt already displaying")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isBiometricPromptShowing = false
                    unlock()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isBiometricPromptShowing = false
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        onCancel()
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Fingerprint not recognized. Please try again.")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        try {
            isBiometricPromptShowing = true
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            isBiometricPromptShowing = false
            android.util.Log.e("AppLockManager", "Error launching BiometricPrompt", e)
            onError(e.message ?: "Authentication error")
        }
    }

    private fun computePbkdf2Hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}
