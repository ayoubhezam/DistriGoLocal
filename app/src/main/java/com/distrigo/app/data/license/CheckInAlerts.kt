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

/** Tells the user the phone must check in soon (decision 3), and that the subscription has entered its grace days. */
interface CheckInAlerts {
    /** The phone must check in before [offlineUntil]. */
    fun show(offlineUntil: Long)

    fun clear()

    /**
     * Decision 6 of docs/license_l5_enforcement.md: the subscription ended at [validTo] and recording continues until
     * [graceUntil]. Shown once for each [validTo], however often it is asked.
     */
    fun showGrace(validTo: Long, graceUntil: Long)

    fun clearGrace()
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

    override fun showGrace(validTo: Long, graceUntil: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_GRACE_SHOWN_FOR, -1) == validTo) return
        if (!AutoBackupNotifications.canNotify(context)) return
        AutoBackupNotifications.ensureChannel(context)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        }
        val text = "Votre abonnement a expiré le ${day(validTo)}. Vous pouvez encore enregistrer jusqu'au ${day(graceUntil)} : " +
            "saisissez un code dans Compte → Abonnement pour continuer."
        val notification = NotificationCompat.Builder(context, AutoBackupNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_license)
            .setContentTitle("Abonnement expiré — période de grâce")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(GRACE_NOTIFICATION_ID, notification)
            prefs.edit().putLong(KEY_GRACE_SHOWN_FOR, validTo).apply()
        } catch (e: SecurityException) {
            // The permission was withdrawn between the check and here
        }
    }

    override fun clearGrace() {
        NotificationManagerCompat.from(context).cancel(GRACE_NOTIFICATION_ID)
    }

    private fun day(millis: Long) = DAY.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    companion object {
        const val NOTIFICATION_ID = 4102
        const val GRACE_NOTIFICATION_ID = 4103
        private const val PREFS = "license_alerts"
        /** The `validTo` whose grace was announced: once per subscription end. */
        private const val KEY_GRACE_SHOWN_FOR = "grace_shown_for"
        private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)
    }
}
