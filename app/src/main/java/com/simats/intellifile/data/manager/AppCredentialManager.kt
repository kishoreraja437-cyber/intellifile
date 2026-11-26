package com.simats.intellifile.data.manager

import android.app.Activity
import android.util.Log
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException

data class SavedAccountCredential(
    val username: String,
    val password: String
)

/**
 * Helper to manage Google Password Manager and Android Credential Manager operations.
 * Handles saving and retrieving credentials for IntelliFile securely.
 */
object AppCredentialManager {
    private const val TAG = "AppCredentialManager"

    /**
     * Saves user credentials (email & password) to Google Password Manager / Android Credential Manager.
     * Triggers the official system bottom sheet / dialog asking the user if they want to save their password.
     * Returns true if saved, or false if the user declined or an error occurred.
     */
    suspend fun savePasswordCredentials(
        activity: Activity,
        username: String,
        pass: String
    ): Boolean {
        if (username.isBlank() || pass.isBlank()) {
            Log.d(TAG, "Cannot save blank username or password.")
            return false
        }

        return try {
            val credentialManager = CredentialManager.create(activity)
            val request = CreatePasswordRequest(
                id = username.trim(),
                password = pass
            )
            credentialManager.createCredential(activity, request)
            Log.d(TAG, "Credentials saved successfully to Google Password Manager.")
            true
        } catch (e: CreateCredentialCancellationException) {
            // User explicitly cancelled or declined the prompt - respect their choice
            Log.d(TAG, "User declined saving credentials: ${e.message}")
            false
        } catch (e: CreateCredentialException) {
            Log.w(TAG, "Failed to save credentials: ${e.type} - ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error saving credentials", e)
            false
        }
    }

    /**
     * Retrieves saved credentials from Google Password Manager / Android Credential Manager.
     * Prompts the user with the Google Password Manager account chooser bottom sheet.
     * Returns SavedAccountCredential if user selected an account, or null if cancelled/none found.
     */
    suspend fun getSavedPasswordCredentials(
        activity: Activity
    ): SavedAccountCredential? {
        return try {
            val credentialManager = CredentialManager.create(activity)
            val passwordOption = GetPasswordOption()
            val request = GetCredentialRequest(listOf(passwordOption))

            val response = credentialManager.getCredential(
                request = request,
                context = activity
            )

            val credential = response.credential
            if (credential is PasswordCredential) {
                Log.d(TAG, "Retrieved password credential for user: ${credential.id}")
                SavedAccountCredential(
                    username = credential.id,
                    password = credential.password
                )
            } else {
                Log.d(TAG, "Credential type is not PasswordCredential: ${credential::class.java.name}")
                null
            }
        } catch (e: GetCredentialCancellationException) {
            // User dismissed the bottom sheet
            Log.d(TAG, "User cancelled credential retrieval: ${e.message}")
            null
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No credentials saved in Google Password Manager for this app.")
            null
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Failed to retrieve credentials: ${e.type} - ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error retrieving credentials", e)
            null
        }
    }
}
