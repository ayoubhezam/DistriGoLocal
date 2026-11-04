package com.distrigo.app.data.backup.cloud

import android.net.Uri
import com.distrigo.app.data.backup.BackupCreator
import com.distrigo.app.data.backup.DataFingerprint
import com.distrigo.app.data.license.DataBusiness
import com.distrigo.app.data.local.database.AppDatabase
import java.io.File

/**
 * The app's own data as a cloud backup source: the same `.distrigo` as a local backup, made by [BackupCreator].
 *
 * Photos are left out of the file (`includePhotos = false`): they go to the cloud once each, by content (plan §3.5,
 * Phase 4). A file without photos restores the data and keeps the phone's photos (`BackupManifest.photosIncluded`).
 * Not recorded as the data's last backup (`record = false`): that line on the Sauvegarde screen is for the local file.
 */
class AppCloudBackupSource(
    private val db: AppDatabase,
    private val creator: BackupCreator,
    private val business: DataBusiness,
) : CloudBackupSource {

    override fun businessId(): String? = business.read()

    override fun fingerprint(): String = DataFingerprint.of(db.openHelper.writableDatabase)

    override fun create(file: File): PlainBackup {
        val created = creator.create(Uri.fromFile(file), record = false, includePhotos = false)
        return PlainBackup(created.fingerprint, created.manifest.schemaVersion, created.manifest.deviceId, created.manifest.createdAt)
    }
}
