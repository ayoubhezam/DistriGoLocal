package com.distrigo.app.data.backup

import android.app.Notification
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.distrigo.app.data.backup.auto.AutoBackupRunner
import com.distrigo.app.data.backup.auto.AutoBackupStore
import com.distrigo.app.data.backup.auto.PrivateFolder
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import com.distrigo.app.data.local.database.withMigrationPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * The background backup (2026-10-21), run the way WorkManager runs it, on the test's own database and folders
 * under `cacheDir/[TEST_DIR]`; the notification is recorded instead of shown.
 */
@RunWith(AndroidJUnit4::class)
class BackupNowWorkerTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, TEST_DIR)
    private val saved = File(root, "saved")
    private val picked = File(root, "picked")
    private lateinit var db: AppDatabase
    private lateinit var creator: BackupCreator
    private lateinit var runner: AutoBackupRunner
    private val steps = mutableListOf<String>()
    private val finished = mutableListOf<Pair<BackupJobResult, Boolean>>()

    private val notifier = object : BackupJobNotifier {
        override fun progressNotification(step: String, toFolder: Boolean): Notification = Notification()
        override fun progress(step: String, toFolder: Boolean) {
            steps += step
        }
        override fun finished(result: BackupJobResult, toFolder: Boolean) {
            finished += result to toFolder
        }
    }

    @Before
    fun open() {
        cleanUp()
        saved.mkdirs()
        picked.mkdirs()
        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .withMigrationPolicy()
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000022" }
            .build()
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO products (name, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, " +
                "has_expiry, camion_stock, uuid) VALUES ('Lait Candia 1L', 120.0, 100.0, 0, 0, 'piece', 0, 1, 0, 0, ?)",
            arrayOf(UUID.randomUUID().toString())
        )
        creator = BackupCreator(db, context.getDatabasePath(TEST_DB), File(root, "images"), File(root, "work"), context.contentResolver, "9.9 (99)", "Test Phone")
        val store = AutoBackupStore(File(root, "state")).also { it.update { state -> state.copy(folderUri = "content://test/tree/DistriGo") } }
        runner = AutoBackupRunner(db, creator, store, { PrivateFolder(picked, displayName = "DistriGo") }, PrivateFolder(File(root, "private")))
    }

    @After
    fun cleanUp() {
        if (::db.isInitialized && db.isOpen) db.close()
        context.deleteDatabase(TEST_DB)
        File(context.cacheDir, "$TEST_DB.lck").delete()
        root.deleteRecursively()
    }

    private fun run(destination: Uri?): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<BackupNowWorker>(context)
            .setInputData(workDataOf(BackupJob.KEY_DESTINATION to destination?.toString()))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    BackupNowWorker(appContext, workerParameters, creator, runner, notifier)
            })
            .build()
            .doWork()
    }

    private fun resultOf(outcome: ListenableWorker.Result): BackupJobResult =
        BackupJobResult.of((outcome as ListenableWorker.Result.Success).outputData)

    @Test
    fun aBackupToAPickedFileIsSavedWithItsStepsAndItsNotification() {
        val file = File(saved, "DistriGo-test.distrigo")

        val result = resultOf(run(Uri.fromFile(file)))

        val saved = result as BackupJobResult.Saved
        assertEquals("DistriGo-test.distrigo", saved.fileName)
        assertEquals(file.length(), saved.size)
        assertTrue(file.length() > 0)
        // Each step shown, from the copy to the check of the saved file
        assertEquals(listOf("Copie des données…", "Préparation du fichier…", "Enregistrement…", "Vérification de la sauvegarde…"), steps.distinct())
        assertEquals(listOf(saved to false), finished)
    }

    @Test
    fun aBackupNowGoesToTheFolder() {
        val result = resultOf(run(destination = null)) as BackupJobResult.Saved

        assertEquals("DistriGo", result.folderName)
        assertEquals(false, result.inPrivateStorage)
        assertEquals(1, picked.list()!!.size)
        assertEquals(true, finished.single().second)
    }

    /** A failure is a result to tell, not a crash nor a retry: WorkManager is told the job succeeded. */
    @Test
    fun aFileThatCannotBeWrittenEndsInAFailureToTell() {
        val outcome = run(Uri.fromFile(File(saved, "no-such-folder/DistriGo-test.distrigo")))

        assertTrue(outcome is ListenableWorker.Result.Success)
        val failed = resultOf(outcome) as BackupJobResult.Failed
        assertEquals(BackupMessages.of(BackupFailedException.Reason.WRITE_FAILED), failed.message)
        assertEquals(listOf(failed to false), finished)
    }

    private companion object {
        const val TEST_DIR = "backup-now-worker-test"
        const val TEST_DB = "backup-now-worker-test.db"
    }
}
