package com.distrigo.app.ui.settings.data

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.R
import com.distrigo.app.data.backup.cloud.CloudBackupEntry
import com.distrigo.app.data.backup.cloud.CloudBackupMeta
import com.distrigo.app.data.backup.cloud.CloudBackupProblem
import com.distrigo.app.data.backup.cloud.CloudBackupState
import com.distrigo.app.ui.theme.DistriGoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Every state of « Sauvegarde Google Drive », drawn in French and in Arabic (right to left), saved as screenshots for a
 * human to look at: `Android/data/com.distrigo.app/files/cloud-shots/<lang>-<state>.png`. Draws [CloudBackupContent]
 * only, with made-up states: nothing of the app's data, link or Google account is touched.
 */
@RunWith(AndroidJUnit4::class)
class CloudBackupScreenshots {

    @get:Rule
    val compose = createComposeRule()

    private val target: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val out = File(target.getExternalFilesDir(null), "cloud-shots").apply { deleteRecursively(); mkdirs() }

    private val now = Instant.now()
    private val linked = CloudBackupState(
        linked = true, account = "owner@gmail.com", businessId = "biz-1",
        lastAt = now.minus(3, ChronoUnit.HOURS), lastSize = 14_200_000,
    )

    private fun localized(tag: String): Context {
        val config = Configuration(target.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return target.createConfigurationContext(config)
    }

    private fun entry(id: String, hoursAgo: Long, device: String, keyId: String = "k") =
        CloudBackupEntry(id, 15_000_000, null, CloudBackupMeta("biz-1", device, now.minus(hoursAgo, ChronoUnit.HOURS).toEpochMilli(), 64, "1.4.0", 41_000_000, keyId))

    /** The states to draw, each with what [CloudBackupContent] receives. */
    private fun states(text: Context): List<Pair<String, Scene>> = listOf(
        "1-not-linked" to Scene(CloudStatus(loaded = true, businessId = "biz-1")),
        "2-unavailable" to Scene(CloudStatus(loaded = true, available = false)),
        "3-linked-running" to Scene(
            CloudStatus(loaded = true, businessId = "biz-1", state = linked),
            message = CloudMessage(text.getString(R.string.cloud_linked_done), false),
            job = CloudJob(text.getString(R.string.cloud_step_photos, 120, 300), waiting = false),
        ),
        "4-linked-quota" to Scene(CloudStatus(loaded = true, businessId = "biz-1", state = linked.copy(lastProblem = CloudBackupProblem.QUOTA_EXCEEDED, problemStreak = 1, allowMobileData = true))),
        "5-linked-reconnect" to Scene(
            CloudStatus(loaded = true, businessId = "biz-1", state = linked.copy(lastProblem = CloudBackupProblem.NEEDS_CONSENT, problemStreak = 1, includePhotos = false)),
            job = CloudJob(text.getString(R.string.cloud_step_waiting_wifi), waiting = true),
        ),
        "6-create-password" to Scene(
            CloudStatus(loaded = true, businessId = "biz-1"), step = CloudStep.CreatePassword("owner@gmail.com"),
            fieldError = text.getString(R.string.cloud_password_mismatch),
        ),
        "7-recovery-code" to Scene(CloudStatus(loaded = true, businessId = "biz-1"), step = CloudStep.RecoveryCode("7K2M-Q9XD-4HTR-0PZB-WN3E-8CFA")),
        "8-enter-secret" to Scene(CloudStatus(loaded = true, businessId = "biz-1"), step = CloudStep.EnterSecret(SecretPurpose.JOIN), fieldError = text.getString(R.string.cloud_secret_wrong)),
        "9-change-password" to Scene(CloudStatus(loaded = true, businessId = "biz-1", state = linked), step = CloudStep.ChangePassword),
        "10-confirm-unlink" to Scene(CloudStatus(loaded = true, businessId = "biz-1", state = linked), step = CloudStep.ConfirmUnlink),
        "11-choose-backup" to Scene(
            CloudStatus(loaded = true, businessId = "biz-1", state = linked),
            step = CloudStep.ChooseBackup(
                listOf(
                    CloudBackupChoice(entry("a", 5, "this"), openable = true, fromThisPhone = true),
                    CloudBackupChoice(entry("b", 30, "other"), openable = true, fromThisPhone = false),
                    CloudBackupChoice(entry("c", 24 * 40, "other", keyId = "old"), openable = false, fromThisPhone = false),
                ),
            ),
        ),
        "12-restoring" to Scene(CloudStatus(loaded = true, businessId = "biz-1", state = linked), step = CloudStep.Restoring(text.getString(R.string.cloud_downloading, 42))),
    )

    private data class Scene(
        val status: CloudStatus,
        val step: CloudStep? = null,
        val fieldError: String? = null,
        val message: CloudMessage? = null,
        val job: CloudJob? = null,
    )

    @Test
    fun everyStateInFrenchAndArabic() {
        var current by mutableStateOf<Pair<String, Scene>?>(null)
        var language by mutableStateOf("fr")
        compose.setContent {
            val text = localized(language)
            CompositionLocalProvider(
                LocalContext provides text,
                LocalConfiguration provides text.resources.configuration,
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                DistriGoTheme {
                    current?.let { (_, scene) ->
                        CloudBackupContent(scene.status, scene.step, busy = scene.step is CloudStep.Restoring, scene.fieldError, scene.message, scene.job, CloudActions())
                    }
                }
            }
        }
        for (lang in listOf("fr", "ar")) {
            for (state in states(localized(lang))) {
                compose.runOnIdle {
                    language = lang
                    current = state
                }
                compose.waitForIdle()
                Thread.sleep(400) // dialogs animate in
                val shot: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                File(out, "$lang-${state.first}.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        assertTrue(out.listFiles()!!.size == 24)
    }
}
