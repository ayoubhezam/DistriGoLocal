package com.distrigo.app.data.backup

import android.app.Activity
import android.content.Intent

/**
 * Restarts the app in a new process, so a scheduled restore is installed before anything opens the data.
 *
 * The launch is handed to the system first, then this process ends; the system starts a fresh one for
 * the launch. If the system declines to relaunch, the restore still waits and is installed the next time
 * the user opens the app.
 */
object AppRestart {

    fun restart(activity: Activity) {
        val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launch != null) activity.startActivity(launch)
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }
}
