package com.distrigo.app.data.license

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The license while the app is in front (docs/license_l4_checkin.md §4, §5.4): read again at once, then every
 * [EVALUATE_EVERY] and on a date or time change, so a date crossed or set back shows without a restart; and a
 * check-in, through WorkManager, when one is due ([dueInFront]). Offline, that check-in waits for the network.
 */
class LicenseInFront(
    private val context: Context,
    private val auth: AuthRepository,
    private val license: LicenseManager,
    private val scheduler: LicenseCheckInScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** From ON_START until cancelled at ON_STOP (repeatOnLifecycle). */
    suspend fun whileInFront() = coroutineScope {
        val timeChanged = Channel<Unit>(Channel.CONFLATED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                timeChanged.trySend(Unit)
            }
        }
        // System broadcasts reach a receiver that isn't exported
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_TIME_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            launch { for (change in timeChanged) evaluate() }
            withContext(Dispatchers.IO) {
                auth.restore()
                if (dueInFront(auth.state.value, license.evaluate(), clock())) scheduler.checkInNow()
            }
            while (true) {
                delay(EVALUATE_EVERY)
                evaluate()
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private suspend fun evaluate() {
        withContext(Dispatchers.IO) { license.evaluate() }
    }

    companion object {
        const val EVALUATE_EVERY = 15 * 60_000L

        /** Decision 2: in front, a check-in when the last license is older than this. */
        const val IN_FRONT_AFTER = 6 * 3_600_000L

        /**
         * Whether coming to the front calls for a check-in: the last license is more than [IN_FRONT_AFTER] old
         * (its issue time, whoever got it: the background, « Vérifier », a code, an activation), or the state asks
         * for one. Only while the background check-ins would run too.
         */
        fun dueInFront(account: AccountState, state: LicenseState, now: Long): Boolean {
            if (LicenseCheckInScheduler.wanted(account, state) != true) return false
            val held = state.heldLicense ?: return false
            return state is LicenseState.CheckInSoon ||
                state is LicenseState.CheckInRequired ||
                state is LicenseState.Suspended ||
                now - held.issuedAt > IN_FRONT_AFTER
        }
    }
}
