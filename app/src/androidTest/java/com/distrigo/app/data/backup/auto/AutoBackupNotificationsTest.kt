package com.distrigo.app.data.backup.auto

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real failure notification, shown and removed through the system. It needs the notification permission the
 * app asks for when daily backups are turned on, so it is skipped where that was not granted. It leaves nothing
 * behind: the notification is removed after each test.
 */
@RunWith(AndroidJUnit4::class)
class AutoBackupNotificationsTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notifications = AutoBackupNotifications(context)
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun shown() = manager.activeNotifications.filter { it.id == AutoBackupNotifications.NOTIFICATION_ID }

    private val failing = AutoBackupState(enabled = true, problemStreak = 2, lastProblem = AutoBackupProblem.FOLDER_UNAVAILABLE)

    @After
    fun remove() {
        NotificationManagerCompat.from(context).cancel(AutoBackupNotifications.NOTIFICATION_ID)
    }

    @Test
    fun theSecondFailureInARowIsNotifiedAndAGoodRunRemovesIt() {
        assumeTrue("notifications are not allowed for the app", NotificationManagerCompat.from(context).areNotificationsEnabled())

        notifications.update(failing.copy(problemStreak = 1))
        assertTrue("one failure is not notified", shown().isEmpty())

        notifications.update(failing)
        val notification = shown().single().notification
        assertEquals("Sauvegardes automatiques en échec", notification.extras.getString("android.title"))
        assertEquals(AutoBackupProblem.FOLDER_UNAVAILABLE.message, notification.extras.getCharSequence("android.text").toString())
        assertEquals(AutoBackupNotifications.CHANNEL_ID, notification.channelId)
        assertTrue("tapping it opens the app", notification.contentIntent != null)

        notifications.update(failing.copy(problemStreak = 3, lastProblem = AutoBackupProblem.WRITE_FAILED))
        assertEquals("later failures update the same notification", 1, shown().size)
        assertEquals(AutoBackupProblem.WRITE_FAILED.message, shown().single().notification.extras.getCharSequence("android.text").toString())

        notifications.update(failing.copy(problemStreak = 0, lastProblem = null))
        assertTrue("a good run removes it", shown().isEmpty())
    }

    @Test
    fun turningBackupsOffRemovesTheNotification() {
        assumeTrue(NotificationManagerCompat.from(context).areNotificationsEnabled())
        notifications.update(failing)
        assertEquals(1, shown().size)
        notifications.update(failing.copy(enabled = false))
        assertTrue(shown().isEmpty())
    }
}
