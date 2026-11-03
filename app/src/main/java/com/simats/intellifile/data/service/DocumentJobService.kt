package com.simats.intellifile.data.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.simats.intellifile.data.manager.DocumentDownloadObserver

/**
 * System JobService triggered by Android OS whenever MediaStore downloads or documents change,
 * even if the app process has been killed or suspended.
 */
class DocumentJobService : JobService() {

    companion object {
        private const val JOB_ID = 202611

        fun schedule(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                val component = ComponentName(context, DocumentJobService::class.java)

                val builder = JobInfo.Builder(JOB_ID, component)
                    .addTriggerContentUri(
                        JobInfo.TriggerContentUri(
                            MediaStore.Files.getContentUri("external"),
                            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                        )
                    )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.addTriggerContentUri(
                        JobInfo.TriggerContentUri(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                        )
                    )
                }

                // Low delay after trigger (1.5 seconds)
                builder.setTriggerContentUpdateDelay(1500)
                builder.setTriggerContentMaxDelay(4000)

                scheduler.schedule(builder.build())
            } catch (_: Exception) {}
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        // Run check
        try {
            DocumentDownloadObserver.getInstance(applicationContext).checkForNewDocuments()
        } catch (_: Exception) {}

        // Re-schedule for future triggers
        schedule(applicationContext)

        // Job finished
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }
}

