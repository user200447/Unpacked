package com.jassemsendi.unpacked

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Hosts a running Import in [ImportService] so Android keeps the process alive while the user is elsewhere, and
 * shows how far the run is in the service's notification. Updates are spaced out, since a small Export finishes
 * hundreds of files in a second, and the last one always goes through.
 */
internal class ForegroundImportHost(
    private val context: Context,
) : ImportHost {
    private val notifications = NotificationManagerCompat.from(context)
    private var lastShownAt = 0L

    override fun started(exportName: String) {
        lastShownAt = 0L
        ContextCompat.startForegroundService(context, ImportService.start(context, exportName))
    }

    override fun progress(
        exportName: String,
        filesDone: Int,
        filesTotal: Int,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (filesDone < filesTotal && now - lastShownAt < PROGRESS_INTERVAL_MILLIS) return
        lastShownAt = now
        if (notifications.areNotificationsEnabled()) {
            notifications.notify(
                ImportService.NOTIFICATION_ID,
                ImportService.notification(context, exportName, filesDone, filesTotal),
            )
        }
    }

    override fun finished() {
        // Sent the way the start was, so the service handles it after the start on its own thread and has called
        // startForeground before it stops: stopping sooner is a crash on Android 8 to 11. Android may refuse a
        // start by then (the data-sync time limit); the plain stop and the cancel cover that.
        try {
            ContextCompat.startForegroundService(context, ImportService.stop(context))
        } catch (_: IllegalStateException) {
            context.stopService(Intent(context, ImportService::class.java))
            notifications.cancel(ImportService.NOTIFICATION_ID)
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MILLIS = 500L
    }
}

/**
 * The foreground service a running Import is hosted in. It does no work of its own: the run is a coroutine in the
 * app's scope, and the service exists so the process is kept in the foreground while it runs. Started with the
 * Export's name when the run starts, stopped when it ends; not restarted after a process death, since the run
 * died with it and the Import stays incomplete (ADR 0007).
 */
internal class ImportService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val exportName = intent?.getStringExtra(EXTRA_EXPORT_NAME) ?: "your Export"
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        // Every start, the stop included, goes through startForeground: that is the contract of being started as
        // a foreground service, and the stop needs it kept before the service leaves.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(this, exportName, 0, null), type)
        if (intent?.action == ACTION_STOP) leaveForeground()
        return START_NOT_STICKY
    }

    // Android 15 ends a data-sync foreground service after six hours; the run goes on in the app's scope without it.
    override fun onTimeout(startId: Int) {
        leaveForeground()
    }

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        leaveForeground()
    }

    private fun leaveForeground() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "imports"
        private const val EXTRA_EXPORT_NAME = "exportName"
        private const val ACTION_STOP = "com.jassemsendi.unpacked.action.STOP_IMPORT"

        fun start(
            context: Context,
            exportName: String,
        ): Intent = Intent(context, ImportService::class.java).putExtra(EXTRA_EXPORT_NAME, exportName)

        fun stop(context: Context): Intent = Intent(context, ImportService::class.java).setAction(ACTION_STOP)

        /** Once per process, before the first notification: the channel the Import notifications are posted on. */
        fun createChannel(context: Context) {
            NotificationManagerCompat.from(context).createNotificationChannel(
                NotificationChannelCompat
                    .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("Imports")
                    .setDescription("Progress of an Import that is running")
                    .build(),
            )
        }

        /** The notification for a run of [exportName]: listing until [filesTotal] is known, then file by file. */
        fun notification(
            context: Context,
            exportName: String,
            filesDone: Int,
            filesTotal: Int?,
        ): Notification {
            val openApp =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val text =
                when {
                    filesTotal == null -> "Listing the files in the Export"
                    filesDone < filesTotal -> "File ${filesDone + 1} of $filesTotal"
                    else -> "Finishing"
                }
            return NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Importing $exportName")
                .setContentText(text)
                .setProgress(filesTotal ?: 0, filesDone, filesTotal == null)
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build()
        }
    }
}
