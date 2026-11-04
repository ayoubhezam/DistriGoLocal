package com.distrigo.app.data.backup.cloud

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.distrigo.app.R
import com.distrigo.app.data.backup.auto.AutoBackupNotifications
import com.distrigo.app.data.backup.backupText

/** Tells the user when the cloud backup needs them, or keeps failing. */
interface CloudBackupAlerts {
    /** Shows, updates or removes the alert to match [state]. */
    fun update(state: CloudBackupState)

    companion object {
        /** Failures in a row before a passing problem (no connection…) is told: as for the local backup. */
        const val FAILURES_BEFORE_ALERT = 2

        /** A problem that needs the user is told at once; others when they last. Only while linked and on. */
        fun shouldAlert(state: CloudBackupState): Boolean {
            val problem = state.lastProblem ?: return false
            if (!state.linked) return false
            return problem.needsTheUser || (state.enabled && state.problemStreak >= FAILURES_BEFORE_ALERT)
        }
    }
}

/**
 * The cloud alert as a notification on the « Sauvegardes » channel, one at a time, apart from the local backup's alert
 * so each clears on its own good run.
 */
class CloudBackupNotifications(private val context: Context) : CloudBackupAlerts {

    override fun update(state: CloudBackupState) {
        val manager = NotificationManagerCompat.from(context)
        if (!CloudBackupAlerts.shouldAlert(state)) {
            manager.cancel(ALERT_ID)
            return
        }
        if (!AutoBackupNotifications.canNotify(context)) return
        AutoBackupNotifications.ensureChannel(context)
        val problem = state.lastProblem ?: return
        val notification = NotificationCompat.Builder(context, AutoBackupNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_backup)
            .setContentTitle(backupText(R.string.cloud_backup_alert_title))
            .setContentText(problem.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(backupText(R.string.backup_alert_open, problem.message)))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(ALERT_ID, notification)
    }

    companion object {
        const val ALERT_ID = 4105
        const val PROGRESS_ID = 4106

        /** The expedited job's notification on Android 11 and older. */
        fun progress(context: Context): Notification {
            AutoBackupNotifications.ensureChannel(context)
            return NotificationCompat.Builder(context, AutoBackupNotifications.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_backup)
                .setContentTitle(backupText(R.string.cloud_backup_running))
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setSilent(true)
                .build()
        }

        private fun openApp(context: Context): PendingIntent? =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 0, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
            }
    }
}
