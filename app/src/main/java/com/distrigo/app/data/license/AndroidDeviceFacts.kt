package com.distrigo.app.data.license

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.distrigo.app.BuildConfig
import com.distrigo.app.data.device.DeviceIdentity

class AndroidDeviceFacts(context: Context) : DeviceFacts {

    private val app = context.applicationContext

    override val installationId: String get() = DeviceIdentity.id(app)

    // Per app signing key, user and phone (API 26+): the same after a reinstall, which is what it is for.
    @get:SuppressLint("HardwareIds")
    override val androidId: String? get() = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)

    override val model: String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
        .ifBlank { "Android" }

    override val osVersion: String = Build.VERSION.RELEASE ?: ""

    override val appVersion: String = BuildConfig.VERSION_NAME
}
