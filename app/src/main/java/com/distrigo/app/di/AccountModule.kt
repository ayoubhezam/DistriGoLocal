package com.distrigo.app.di

import android.content.Context
import com.distrigo.app.data.auth.AuthApi
import com.distrigo.app.data.auth.AuthRepository
import com.distrigo.app.data.auth.Backend
import com.distrigo.app.data.auth.SessionStore
import com.distrigo.app.data.license.AndroidDeviceFacts
import com.distrigo.app.data.license.DeviceKey
import com.distrigo.app.data.license.DeviceKeys
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

    /** One device key for the license and its server calls, so both always see the same key. */
    @Provides
    @Singleton
    fun provideDeviceKeys(): DeviceKeys = DeviceKey()

    @Provides
    @Singleton
    fun provideLicenseManager(@ApplicationContext context: Context, keys: DeviceKeys): LicenseManager =
        LicenseManager.forApp(context, keys)

    @Provides
    @Singleton
    fun provideLicenseClient(
        @ApplicationContext context: Context,
        http: OkHttpClient,
        auth: AuthRepository,
        keys: DeviceKeys,
        license: LicenseManager,
    ): LicenseClient = LicenseClient(http, Backend.DISTRIGO, auth, keys, license, AndroidDeviceFacts(context))
}
