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

    private fun shownText() = shown().singleOrNull()?.notification?.extras?.getCharSequence("android.text")?.toString()

    /**
     * Waits for the system to show what was posted. Android rate-limits an app's notification updates, so a post
     * right after another can take a moment to appear; a condition still false after five seconds fails.
     */
    private fun eventually(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError(what)
            Thread.sleep(100)
        }
    }

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
        eventually("the second failure is notified") { shown().size == 1 }
        val notification = shown().single().notification
        assertEquals("Sauvegardes automatiques en échec", notification.extras.getString("android.title"))
        assertEquals(AutoBackupProblem.FOLDER_UNAVAILABLE.message, notification.extras.getCharSequence("android.text").toString())
        assertEquals(AutoBackupNotifications.CHANNEL_ID, notification.channelId)
        assertTrue("tapping it opens the app", notification.contentIntent != null)

        notifications.update(failing.copy(problemStreak = 3, lastProblem = AutoBackupProblem.WRITE_FAILED))
        eventually("later failures update the same notification") { shownText() == AutoBackupProblem.WRITE_FAILED.message }
        assertEquals(1, shown().size)

        notifications.update(failing.copy(problemStreak = 0, lastProblem = null))
        eventually("a good run removes it") { shown().isEmpty() }
    }

    @Test
    fun turningBackupsOffRemovesTheNotification() {
        assumeTrue(NotificationManagerCompat.from(context).areNotificationsEnabled())
        notifications.update(failing)
        eventually("shown") { shown().size == 1 }
        notifications.update(failing.copy(enabled = false))
        eventually("removed when backups are turned off") { shown().isEmpty() }
    }
}
