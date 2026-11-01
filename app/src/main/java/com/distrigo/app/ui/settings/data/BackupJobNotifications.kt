package com.distrigo.app.ui.settings.data

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import com.distrigo.app.R
import com.distrigo.app.ui.settings.settingsText
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.distrigo.app.data.backup.BackupJobNotifier
import com.distrigo.app.data.backup.BackupJobResult
import com.distrigo.app.data.backup.auto.AutoBackupNotifications

/**
 * On the backups' channel, one notification that the job updates: silent while it runs, then « Sauvegarde
 * enregistrée » or « Sauvegarde impossible », opening DistriGo when tapped. Without the notification permission
 * nothing is shown; the screen still shows the job, and a failure stays on its card.
 */
class BackupJobNotifications(private val context: Context) : BackupJobNotifier {

    override fun progressNotification(step: String, toFolder: Boolean): Notification {
        AutoBackupNotifications.ensureChannel(context)
        return builder()
            .setContentTitle(settingsText(if (toFolder) R.string.backup_notif_to_folder else R.string.backup_notif_running))
            .setContentText(step)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun progress(step: String, toFolder: Boolean) {
        if (!AutoBackupNotifications.canNotify(context)) return
        NotificationManagerCompat.from(context).notify(BackupJobNotifier.NOTIFICATION_ID, progressNotification(step, toFolder))
    }

    override fun finished(result: BackupJobResult, toFolder: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        if (!AutoBackupNotifications.canNotify(context)) return
        val (title, text) = when (result) {
            is BackupJobResult.Saved -> settingsText(R.string.backup_saved) to listOfNotNull(
                result.fileName,
                DataBackupFormatting.size(result.size),
                result.folderName?.let { settingsText(R.string.backup_notif_in_folder, it) },
            ).joinToString(" · ")
            BackupJobResult.Unchanged -> settingsText(R.string.backup_notif_up_to_date) to settingsText(R.string.backup_notif_nothing_changed)
            is BackupJobResult.Failed -> settingsText(R.string.backup_notif_failed) to result.message
        }
        AutoBackupNotifications.ensureChannel(context)
        val notification = builder()
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        manager.notify(BackupJobNotifier.NOTIFICATION_ID, notification)
    }

    private fun builder(): NotificationCompat.Builder {
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(context, AutoBackupNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_backup)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }
}
