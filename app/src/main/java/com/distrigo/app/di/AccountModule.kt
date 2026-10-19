package com.distrigo.app.di

import android.content.Context
import com.distrigo.app.data.auth.ActivationCodeStore
import com.distrigo.app.data.auth.AuthApi
import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.auth.Backend
import com.distrigo.app.data.auth.SessionStore
import com.distrigo.app.data.local.database.AppDatabase
import androidx.work.WorkManager
import com.distrigo.app.data.license.AndroidDeviceFacts
import com.distrigo.app.data.license.AppMetaDataBusiness
import com.distrigo.app.data.license.AppMetaWriteGate
import com.distrigo.app.data.license.BusinessProfileStore
import com.distrigo.app.data.license.CheckInAlerts
import com.distrigo.app.data.license.CheckInLog
import com.distrigo.app.data.license.CheckInNotifications
import com.distrigo.app.data.license.CodesAdminClient
import com.distrigo.app.data.license.DeviceKey
import com.distrigo.app.data.license.DeviceKeys
import com.distrigo.app.data.license.LicenseCheckIn
import com.distrigo.app.data.license.LicenseCheckInScheduler
import com.distrigo.app.data.license.LicenseInFront
import com.distrigo.app.data.license.LicenseClient
import com.distrigo.app.data.license.LicenseManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** The account and the license (docs/license_architecture.md): one of each for the whole app. */
@Module
@InstallIn(SingletonComponent::class)
object AccountModule {

    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideAuthRepository(@ApplicationContext context: Context, http: OkHttpClient): AuthRepository =
        AuthRepository(AuthApi(http, Backend.DISTRIGO), SessionStore.forApp(context))

    /** The business key an activation code gave this phone (docs/activation_codes.md): one copy for the app. */
    @Provides
    @Singleton
    fun provideActivationCodeStore(@ApplicationContext context: Context): ActivationCodeStore =
        ActivationCodeStore.forApp(context)

    /** The last business profile the server gave, for Compte without Internet. */
    @Provides
    @Singleton
    fun provideBusinessProfileStore(@ApplicationContext context: Context): BusinessProfileStore =
        BusinessProfileStore.forApp(context)

    /** The owner's « Administration » screen and codes-admin (C4); the server refuses anyone else. */
    @Provides
    @Singleton
    fun provideCodesAdminClient(http: OkHttpClient, auth: AuthRepository): CodesAdminClient =
        CodesAdminClient(http, Backend.DISTRIGO, auth)

    /** One device key for the license and its server calls, so both always see the same key. */
    @Provides
    @Singleton
    fun provideDeviceKeys(): DeviceKeys = DeviceKey()

    /**
     * The database only when the license is first read (off the main thread): building it waits for a pending restore.
     * Every state it publishes opens or closes the database's write gate (docs/license_l5_enforcement.md §3).
     */
    @Provides
    @Singleton
    fun provideLicenseManager(
        @ApplicationContext context: Context,
        keys: DeviceKeys,
        database: dagger.Lazy<AppDatabase>,
    ): LicenseManager = LicenseManager.forApp(context, keys, AppMetaDataBusiness { database.get() }, AppMetaWriteGate { database.get() })

    @Provides
    @Singleton
    fun provideLicenseClient(
        @ApplicationContext context: Context,
        http: OkHttpClient,
        auth: AuthRepository,
        keys: DeviceKeys,
        license: LicenseManager,
    ): LicenseClient = LicenseClient(http, Backend.DISTRIGO, auth, keys, license, AndroidDeviceFacts(context))

    @Provides
    @Singleton
    fun provideCheckInLog(@ApplicationContext context: Context): CheckInLog = CheckInLog.forApp(context)

    /** One check-in at a time for the background job and the app in front (docs/license_l4_checkin.md §5.1). */
    @Provides
    @Singleton
    fun provideLicenseCheckIn(
        auth: AuthRepository,
        license: LicenseManager,
        client: LicenseClient,
        log: CheckInLog,
    ): LicenseCheckIn = LicenseCheckIn(auth, license, client, log)

    @Provides
    @Singleton
    fun provideCheckInAlerts(@ApplicationContext context: Context): CheckInAlerts = CheckInNotifications(context)

    @Provides
    @Singleton
    fun provideLicenseCheckInScheduler(
        @ApplicationContext context: Context,
        auth: AuthRepository,
        license: LicenseManager,
        log: CheckInLog,
        alerts: CheckInAlerts,
    ): LicenseCheckInScheduler =
        LicenseCheckInScheduler({ WorkManager.getInstance(context) }, auth.state, license.state, log, alerts)

    @Provides
    @Singleton
    fun provideLicenseInFront(
        @ApplicationContext context: Context,
        auth: AuthRepository,
        license: LicenseManager,
        scheduler: LicenseCheckInScheduler,
    ): LicenseInFront = LicenseInFront(context, auth, license, scheduler)
}
