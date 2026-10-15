package com.distrigo.app.data.license

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.distrigo.app.R
import com.distrigo.app.data.backup.auto.AutoBackupNotifications
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tells the user the phone must check in soon (decision 3). */
interface CheckInAlerts {
    /** The phone must check in before [offlineUntil]. */
    fun show(offlineUntil: Long)

    fun clear()
}

/**
 * The reminder as a notification, on the backups' channel (decision 3). One notification, shown once; removed when
 * a newer license comes or the account leaves. Without the notification permission nothing is shown; Compte still
 * says it.
 */
class CheckInNotifications(private val context: Context) : CheckInAlerts {

    override fun show(offlineUntil: Long) {
        if (!AutoBackupNotifications.canNotify(context)) return
        AutoBackupNotifications.ensureChannel(context)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        }
        val text = "Connectez ce téléphone à Internet avant le ${DAY.format(Instant.ofEpochMilli(offlineUntil).atZone(ZoneId.systemDefault()))} " +
            "pour continuer à enregistrer."
        val notification = NotificationCompat.Builder(context, AutoBackupNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_license)
            .setContentTitle("Vérification de l'abonnement")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // The permission was withdrawn between the check and here
        }
    }

    override fun clear() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val NOTIFICATION_ID = 4102
        private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)
    }
}
