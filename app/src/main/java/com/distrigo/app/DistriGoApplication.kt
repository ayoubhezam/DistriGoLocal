package com.distrigo.app

import android.app.Application
import android.widget.Toast
import com.distrigo.app.data.backup.BackupMessages
import com.distrigo.app.data.backup.RestoreInstaller
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DistriGoApplication : Application() {

    override fun onCreate() {
        // Before Hilt and before anything can open the database or read a photo: a restore scheduled before
        // the restart replaces both here. A no-op when none is waiting.
        val restored = RestoreInstaller.forApp(this).installPending()
        super.onCreate()
        restored?.let { Toast.makeText(this, BackupMessages.of(it), Toast.LENGTH_LONG).show() }
    }
}
