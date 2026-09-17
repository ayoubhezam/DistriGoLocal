package com.distrigo.app.data.backup.auto

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.distrigo.app.R

/** Tells the user when automatic backups keep failing. */
interface AutoBackupAlerts {
    /** Shows, updates or removes the alert to match [state]. */
    fun update(state: AutoBackupState)

    companion object {
        /** Failures in a row before the user is told: one bad night can fix itself, two in a row will not. */
        const val FAILURES_BEFORE_ALERT = 2

        fun shouldAlert(state: AutoBackupState): Boolean =
            state.enabled && state.problemStreak >= FAILURES_BEFORE_ALERT && state.lastProblem != null
    }
}

/**
 * The alert as a notification. It stays one notification however many runs fail — later failures update it
 * silently — and it is removed after the next good run. Without the notification permission nothing is shown;
 * the screen still shows the problem.
 */
class AutoBackupNotifications(private val context: Context) : AutoBackupAlerts {

    override fun update(state: AutoBackupState) {
        val manager = NotificationManagerCompat.from(context)
        if (!AutoBackupAlerts.shouldAlert(state)) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!canNotify()) return
        ensureChannel()

        val problem = state.lastProblem ?: return
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        }
        val text = problem.message + " Ouvrez DistriGo, Paramètres, Données et sauvegarde."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_backup)
            .setContentTitle("Sauvegardes automatiques en échec")
            .setContentText(problem.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Sauvegardes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avertit quand les sauvegardes automatiques échouent"
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "backups"
        const val NOTIFICATION_ID = 4101
    }
}
