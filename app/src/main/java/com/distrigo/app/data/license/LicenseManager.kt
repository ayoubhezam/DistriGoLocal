package com.distrigo.app.data.license

import android.content.Context
import com.distrigo.app.data.device.DeviceIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The license, as the rest of the app sees it: one [state], re-evaluated on demand.
 *
 * Phase L1 (docs/license_architecture.md §8): everything here works offline and nothing calls it yet.
 * Check-in (L4) hands a fresh token to [install]; the screens and the write gate (L5) read [state].
 *
 * Every method does file and Keystore I/O: call them off the main thread. They are serialized, so an
 * evaluation never reads a license half-installed.
 */
class LicenseManager internal constructor(
    private val store: LicenseStore,
    private val verifier: LicenseVerifier,
    private val clock: TrustedClock,
    private val deviceKeyHash: () -> String?,
    private val installationId: () -> String,
    private val packageName: String,
) {
    private val lock = Any()

    private val _state = MutableStateFlow<LicenseState>(LicenseState.Unchecked)
    val state: StateFlow<LicenseState> = _state.asStateFlow()

    /** Reads the stored license and the time, and publishes what they allow. */
    fun evaluate(): LicenseState = synchronized(lock) { publish(judge()) }

    /**
     * A token fresh from the server. If it is this phone's and newer than every license accepted before, it
     * is stored and its signed time becomes the clock's anchor; otherwise nothing changes.
     */
    fun install(token: String): LicenseCheck = synchronized(lock) {
        val hash = deviceKeyHash() ?: return LicenseCheck.Rejected(Rejection.NO_DEVICE_KEY)
        // Strictly newer: a replayed answer must not move the clock back to its time.
        val minSeq = store.load()?.let { it.maxSeq + 1 } ?: 0L
        val check = verifier.verify(token, binding(hash, minSeq))
        if (check is LicenseCheck.Valid) {
            // The token first: a kill before the anchor leaves a newer license on an older anchor, which is
            // accepted. The other order would leave an anchor whose seq refuses the stored token.
            store.writeToken(token)
            store.setRevoked(false)
            clock.anchor(check.license.issuedAt, check.license.seq)
        }
        publish(judge())
        check
    }

    /** The server said this phone no longer holds the subscription. Lasts until a newer license is installed. */
    fun revoke(): LicenseState = synchronized(lock) {
        store.setRevoked(true)
        publish(judge())
    }

    /** Forgets the license and the clock: a sign-out. An update never calls this. */
    fun clear(): LicenseState = synchronized(lock) {
        store.clear()
        publish(judge())
    }

    private fun judge(): LicenseState =
        try {
            when {
                store.isRevoked() -> LicenseState.Revoked
                else -> judgeStored()
            }
        } catch (e: Exception) {
            // Keystore or file trouble: fail closed, and let a check-in repair it.
            LicenseState.CheckInRequired(null, CheckInReason.UNREADABLE, e)
        }

    private fun judgeStored(): LicenseState {
        val token = store.readToken() ?: return LicenseState.NoLicense()
        val hash = deviceKeyHash() ?: return LicenseState.NoLicense(Rejection.NO_DEVICE_KEY)
        val time = clock.read()
        val minSeq = (time as? TrustedTime.Known)?.anchor?.maxSeq ?: 0L
        return when (val check = verifier.verify(token, binding(hash, minSeq))) {
            is LicenseCheck.Rejected -> LicenseState.NoLicense(check.why)
            is LicenseCheck.Valid -> LicenseRules.judge(check.license, time)
        }
    }

    private fun binding(deviceKeyHash: String, minSeq: Long) =
        LicenseBinding(packageName, installationId(), deviceKeyHash, minSeq)

    private fun publish(state: LicenseState): LicenseState = state.also { _state.value = it }

    companion object {
        fun forApp(context: Context): LicenseManager {
            val app = context.applicationContext
            val store = LicenseStore.forApp(app)
            val deviceKey = DeviceKey()
            return LicenseManager(
                store = store,
                verifier = LicenseVerifier(LicenseKeys.trusted()),
                clock = TrustedClock(store, AndroidClocks(app)),
                deviceKeyHash = deviceKey::publicKeyHash,
                installationId = { DeviceIdentity.id(app) },
                packageName = app.packageName,
            )
        }
    }
}
