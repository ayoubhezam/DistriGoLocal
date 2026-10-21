package com.distrigo.app.data.backup

import android.app.Notification

/** A [BackupJob]'s notification: its step while it runs, then how it ended. */
interface BackupJobNotifier {
    fun progressNotification(step: String, toFolder: Boolean): Notification
    fun progress(step: String, toFolder: Boolean)
    fun finished(result: BackupJobResult, toFolder: Boolean)

    companion object {
        const val NOTIFICATION_ID = 4104
    }
}
