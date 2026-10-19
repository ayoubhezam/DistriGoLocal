package com.distrigo.app.data.license

import android.content.Context
import com.distrigo.app.data.device.DeviceIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The license, as the rest of the app sees it: one [state], re-evaluated on demand.
 *
 * Phase L1 (docs/license_architecture.md §8): everything here works offline.
 * Check-in (L4) hands a fresh token to [install]; the screens (L5b) read [state], and every state published is
 * written to the database's write gate (L5a), which refuses writes while [LicenseState.canRecord] is false.
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
    /** The business the data belongs to (§7 of docs/license_l4_checkin.md); the app's is `app_meta.business_id`. */
    private val data: DataBusiness = NoDataBusiness,
    /** Where what the license allows is written for the database (docs/license_l5_enforcement.md §3). */
    private val gate: WriteGate = NoWriteGate,
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
            store.setSuspended(false)
            clock.anchor(check.license.issuedAt, check.license.seq)
            // The first license over the data binds it to its business; a later one is compared with it
            if (dataBusiness() == null) bindData(check.license.businessId)
        }
        publish(judge())
        check
    }

    /** The server said this phone no longer holds the subscription. Lasts until a newer license is installed. */
    fun revoke(): LicenseState = synchronized(lock) {
        store.setRevoked(true)
        publish(judge())
    }

    /**
     * The server said the business's subscription is suspended. The license and the key stay; the mark lasts until
     * a newer license is installed, which the first check-in after a reactivation brings.
     */
    fun suspend(): LicenseState = synchronized(lock) {
        store.setSuspended(true)
        publish(judge())
    }

    /**
     * « Garder les données pour … »: the data on this phone now belongs to the license's business — an owner who moved
     * to another account, say. Nothing changes unless the state was OtherBusiness.
     */
    fun keepData(): LicenseState = synchronized(lock) {
        (judge() as? LicenseState.OtherBusiness)?.let { bindData(it.license.businessId) }
        publish(judge())
    }

    /** The server's id for this phone, from its activation: what « Libérer cet appareil » names. */
    fun deviceId(): String? = synchronized(lock) { store.readDeviceId() }

    fun rememberDevice(id: String) = synchronized(lock) { store.writeDeviceId(id) }

    /** Forgets the license and the clock: a sign-out. An update never calls this. */
    fun clear(): LicenseState = synchronized(lock) {
        store.clear()
        publish(judge())
    }

    private fun judge(): LicenseState =
        try {
            if (store.isRevoked()) {
                LicenseState.Revoked
            } else {
                val stored = judgeStored()
                // Only a license this phone still holds is suspended or compared with the data: without its key, an
                // activation is needed anyway
                val held = stored.heldLicense
                when {
                    held == null -> stored
                    store.isSuspended() -> LicenseState.Suspended(held)
                    else -> dataBusiness()?.takeIf { it != held.businessId }?.let { LicenseState.OtherBusiness(held, it) } ?: stored
                }
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

    /** The database's trouble is not the license's: unreadable, the data is taken as not bound yet. */
    private fun dataBusiness(): String? =
        try {
            data.read()
        } catch (e: Exception) {
            null
        }

    private fun bindData(businessId: String) {
        try {
            data.bind(businessId)
        } catch (e: Exception) {
            // Bound at the next license instead
        }
    }

    private fun binding(deviceKeyHash: String, minSeq: Long) =
        LicenseBinding(packageName, installationId(), deviceKeyHash, minSeq)

    /** Under the lock, so the gate is written in the order the states are published. */
    private fun publish(state: LicenseState): LicenseState = state.also {
        _state.value = it
        gate.follow(it.canRecord)
    }

    companion object {
        fun forApp(
            context: Context,
            deviceKey: DeviceKeys = DeviceKey(),
            data: DataBusiness = NoDataBusiness,
            gate: WriteGate = NoWriteGate,
        ): LicenseManager {
            val app = context.applicationContext
            val store = LicenseStore.forApp(app)
            return LicenseManager(
                store = store,
                verifier = LicenseVerifier(LicenseKeys.trusted()),
                clock = TrustedClock(store, AndroidClocks(app)),
                deviceKeyHash = deviceKey::publicKeyHash,
                installationId = { DeviceIdentity.id(app) },
                packageName = app.packageName,
                data = data,
                gate = gate,
            )
        }
    }
}

/** No data to bind: the license alone, as in the license's own tests. */
object NoDataBusiness : DataBusiness {
    override fun read(): String? = null
    override fun bind(businessId: String) = Unit
}
