package com.simats.intellifile.data.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.simats.intellifile.data.manager.DocumentDownloadObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background Service to maintain active document download monitoring
 * and periodic fallback polling while IntelliFile is alive or in background.
 */
class DocumentMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var pollJob: Job? = null

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, DocumentMonitorService::class.java)
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        DocumentDownloadObserver.startWatching(applicationContext)

        // Periodic polling loop every 3 seconds to catch downloads with zero latency
        pollJob = serviceScope.launch {
            while (isActive) {
                delay(3000)
                try {
                    DocumentDownloadObserver.getInstance(applicationContext).checkForNewDocuments()
                } catch (_: Exception) {}
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DocumentDownloadObserver.getInstance(applicationContext).checkForNewDocuments()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        pollJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

