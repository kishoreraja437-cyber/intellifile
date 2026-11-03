package com.simats.intellifile.data.manager

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.simats.intellifile.MainActivity
import com.simats.intellifile.R
import com.simats.intellifile.data.session.SessionManager
import java.util.Calendar

object NotificationHelper {

    const val CHANNEL_IMPORTANT_FILES = "channel_important_files"
    const val CHANNEL_DEADLINE_REMINDERS = "channel_deadline_reminders"
    const val CHANNEL_NEW_DOCUMENTS = "channel_new_documents_v2"

    private const val NOTIF_ID_IMPORTANT = 1001
    private const val NOTIF_ID_DEADLINE = 1002
    private const val NOTIF_ID_TEST = 1004

    /**
     * Initializes notification channels for Android 8.0 (API 26) and above.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channelImportant = NotificationChannel(
                CHANNEL_IMPORTANT_FILES,
                "Important File Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts for files marked with High Priority or required review."
                enableVibration(true)
                setSound(defaultSoundUri, audioAttributes)
            }

            val channelDeadlines = NotificationChannel(
                CHANNEL_DEADLINE_REMINDERS,
                "Deadline Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for document deadlines and scheduled milestones."
                enableVibration(true)
                setSound(defaultSoundUri, audioAttributes)
            }

            val channelNewDocs = NotificationChannel(
                CHANNEL_NEW_DOCUMENTS,
                "New Document Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instant alerts when new documents are downloaded from Browser or received via WhatsApp."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                setSound(defaultSoundUri, audioAttributes)
            }

            manager.createNotificationChannel(channelImportant)
            manager.createNotificationChannel(channelDeadlines)
            manager.createNotificationChannel(channelNewDocs)
        }
    }

    /**
     * Checks if current time is within Quiet Hours (10:00 PM to 07:00 AM).
     */
    fun isInQuietHours(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 7
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Dispatches an alert when a document is marked as High Priority / Important.
     */
    fun sendImportantFileAlert(context: Context, fileName: String, note: String = "Marked as High Priority") {
        val session = SessionManager(context)
        if (!session.isImportantAlertsEnabled()) return
        if (session.isQuietHoursEnabled() && isInQuietHours()) return
        if (!hasNotificationPermission(context)) return

        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIF_ID_IMPORTANT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_IMPORTANT_FILES)
            .setSmallIcon(R.drawable.mainlogo)
            .setContentTitle("Important File: $fileName")
            .setContentText(note)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_IMPORTANT, notification)
        } catch (_: SecurityException) {}
    }

    /**
     * Dispatches a deadline reminder notification.
     */
    fun sendDeadlineReminder(context: Context, fileName: String, deadlineText: String) {
        val session = SessionManager(context)
        if (!session.isDeadlineRemindersEnabled()) return
        if (session.isQuietHoursEnabled() && isInQuietHours()) return
        if (!hasNotificationPermission(context)) return

        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIF_ID_DEADLINE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DEADLINE_REMINDERS)
            .setSmallIcon(R.drawable.mainlogo)
            .setContentTitle("Deadline Reminder: $fileName")
            .setContentText("Due soon: $deadlineText")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_DEADLINE, notification)
        } catch (_: SecurityException) {}
    }

    /**
     * Dispatches an alert when new files are scanned or added.
     */
    fun sendNewDocumentAlert(
        context: Context,
        fileName: String,
        source: String = "Downloaded from Browser / WhatsApp",
        filePath: String = ""
    ) {
        val session = SessionManager(context)
        if (!session.isNewDocAlertsEnabled()) return
        if (session.isQuietHoursEnabled() && isInQuietHours()) return
        if (!hasNotificationPermission(context)) return

        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_file_name", fileName)
            putExtra("open_file_path", filePath)
        }
        val notifId = (System.currentTimeMillis() % 100000).toInt()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "New Document Downloaded"
        val body = "$fileName\n$source • Tap to view and organize."

        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_DOCUMENTS)
            .setSmallIcon(R.drawable.mainlogo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {}
    }

    /**
     * Sends a test notification to verify channel functionality.
     */
    fun sendTestNotification(context: Context, alertType: String = "New Document") {
        createNotificationChannels(context)

        val channelId = when (alertType) {
            "Deadline" -> CHANNEL_DEADLINE_REMINDERS
            "Important File" -> CHANNEL_IMPORTANT_FILES
            else -> CHANNEL_NEW_DOCUMENTS
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("test_notification", true)
        }
        val notifId = (System.currentTimeMillis() % 100000).toInt()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "New Document: assignment_report.pdf"
        val body = "Downloaded from Browser • Tap to view and organize in IntelliFile."

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.mainlogo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {}
    }
}
