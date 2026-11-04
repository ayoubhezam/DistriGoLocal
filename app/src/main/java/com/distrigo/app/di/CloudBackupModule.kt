package com.distrigo.app.di

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.WorkManager
import com.distrigo.app.data.backup.BackupCreator
import com.distrigo.app.data.backup.cloud.AppCloudBackupSource
import com.distrigo.app.data.backup.cloud.BackupKeyStore
import com.distrigo.app.data.backup.cloud.CloudBackupAlerts
import com.distrigo.app.data.backup.cloud.CloudBackupEngine
import com.distrigo.app.data.backup.cloud.CloudBackupLink
import com.distrigo.app.data.backup.cloud.CloudBackupNotifications
import com.distrigo.app.data.backup.cloud.CloudBackupProvider
import com.distrigo.app.data.backup.cloud.CloudBackupScheduler
import com.distrigo.app.data.backup.cloud.CloudBackupStore
import com.distrigo.app.data.backup.cloud.CloudPhotoIndex
import com.distrigo.app.data.backup.cloud.CloudRestore
import com.distrigo.app.data.image.ImageStore
import com.distrigo.app.data.backup.cloud.DriveAuthorizer
import com.distrigo.app.data.backup.cloud.DriveClient
import com.distrigo.app.data.backup.cloud.GoogleDriveProvider
import com.distrigo.app.data.license.AppMetaDataBusiness
import com.distrigo.app.data.local.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** The account a cloud provider works for: Drive, with the linked Google account's silent tokens. */
fun interface CloudBackupProviders {
    fun forAccount(account: String): CloudBackupProvider
}

/** The Google Drive backup (docs/cloud_backup_drive_plan.md). */
@Module
@InstallIn(SingletonComponent::class)
object CloudBackupModule {

    @Provides
    @Singleton
    fun provideCloudBackupStore(@ApplicationContext context: Context): CloudBackupStore = CloudBackupStore.forApp(context)

    @Provides
    @Singleton
    fun provideBackupKeyStore(@ApplicationContext context: Context): BackupKeyStore = BackupKeyStore.forApp(context)

    @Provides
    @Singleton
    fun provideDriveAuthorizer(@ApplicationContext context: Context): DriveAuthorizer = DriveAuthorizer(context)

    /** Uploads send 8 MB per request: a slow connection needs longer than the app's usual 30 s to write one. */
    @Provides
    @Singleton
    fun provideCloudBackupProviders(http: OkHttpClient, authorizer: DriveAuthorizer): CloudBackupProviders {
        val drive = http.newBuilder().writeTimeout(2, TimeUnit.MINUTES).readTimeout(2, TimeUnit.MINUTES).build()
        return CloudBackupProviders { account -> GoogleDriveProvider(DriveClient(drive, authorizer.tokens(account))) }
    }

    @Provides
    @Singleton
    fun provideCloudBackupEngine(
        @ApplicationContext context: Context,
        db: AppDatabase,
        creator: BackupCreator,
        store: CloudBackupStore,
        keys: BackupKeyStore,
        providers: CloudBackupProviders,
    ): CloudBackupEngine {
        val app = context.applicationContext
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        return CloudBackupEngine(
            store = store,
            keys = keys,
            providerFor = providers::forAccount,
            source = AppCloudBackupSource(db, creator, AppMetaDataBusiness { db }, ImageStore.imagesDir(app)),
            stagingDir = File(app.noBackupFilesDir, "cloud_backup/staging"),
            appVersion = "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})",
            photoIndex = CloudPhotoIndex(File(app.noBackupFilesDir, "cloud_backup")),
        )
    }

    @Provides
    @Singleton
    fun provideCloudBackupLink(store: CloudBackupStore, keys: BackupKeyStore, providers: CloudBackupProviders): CloudBackupLink =
        CloudBackupLink(store, keys, providers::forAccount)

    @Provides
    @Singleton
    fun provideCloudRestore(@ApplicationContext context: Context, providers: CloudBackupProviders): CloudRestore = CloudRestore(
        providers::forAccount,
        File(context.applicationContext.noBackupFilesDir, "cloud_backup/restore"),
        ImageStore.imagesDir(context.applicationContext),
    )

    @Provides
    @Singleton
    fun provideCloudBackupScheduler(@ApplicationContext context: Context, store: CloudBackupStore): CloudBackupScheduler =
        CloudBackupScheduler({ WorkManager.getInstance(context) }, store)

    @Provides
    @Singleton
    fun provideCloudBackupAlerts(@ApplicationContext context: Context): CloudBackupAlerts = CloudBackupNotifications(context)
}
