package com.simats.intellifile.data.service

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.simats.intellifile.MainActivity
import com.simats.intellifile.R
import com.simats.intellifile.data.manager.NotificationHelper
import com.simats.intellifile.data.repository.AuthRepository
import com.simats.intellifile.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IntelliFileFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val sessionManager = SessionManager(applicationContext)
        sessionManager.saveFcmToken(token)

        if (sessionManager.isLoggedIn()) {
            val authRepo = AuthRepository(applicationContext)
            CoroutineScope(Dispatchers.IO).launch {
                authRepo.updateFcmToken(token)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val sessionManager = SessionManager(applicationContext)

        // Respect Quiet Hours preference
        if (sessionManager.isQuietHoursEnabled() && NotificationHelper.isInQuietHours()) {
            return
        }

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "IntelliFile"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "You have a new update."

        val notifType = remoteMessage.data["type"] ?: "new_document"
        if (notifType == "new_document" && !sessionManager.isNewDocAlertsEnabled()) {
            return
        }

        NotificationHelper.createNotificationChannels(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notif_title", title)
            putExtra("notif_body", body)
        }

        val notifId = (System.currentTimeMillis() % 100000).toInt()
        val pendingIntent = PendingIntent.getActivity(
            this,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_NEW_DOCUMENTS)
            .setSmallIcon(R.drawable.mainlogo)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(notifId, notification)
        } catch (_: SecurityException) {}
    }
}

