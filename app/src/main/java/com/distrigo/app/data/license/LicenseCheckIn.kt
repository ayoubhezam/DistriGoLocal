package com.distrigo.app.data.license

import com.distrigo.app.data.auth.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One check-in at a time for the whole app (docs/license_l4_checkin.md §5.1). The background job, the app coming
 * to the front (L4c) and « Vérifier » all ask through [run]. A request made while one is under way waits for its
 * answer instead of starting another.
 *
 * A code's renewal checks in through LicenseClient directly: it needs a check-in made after the code was
 * redeemed, not one that may have started before.
 */
class LicenseCheckIn(
    private val auth: AuthRepository,
    private val license: LicenseManager,
    private val client: LicenseClient,
    private val log: CheckInLog,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // Its own scope: a caller that goes away (a screen closed, a job stopped) leaves the check-in to finish for
    // whoever else waits on it
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val starting = Mutex()
    private var current: Deferred<CheckIn>? = null

    suspend fun run(source: CheckInSource): CheckIn {
        val checkIn = starting.withLock {
            current?.takeIf { it.isActive } ?: scope.async { checkIn(source) }.also { current = it }
        }
        return checkIn.await()
    }

    private suspend fun checkIn(source: CheckInSource): CheckIn {
        val result = try {
            // The process may have just started for this job: the session and the license are read first
            auth.restore()
            license.evaluate()
            client.checkIn()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The Keystore refusing to sign, say: not worth retrying within the hour; the next period tries again
            CheckIn.Failed("La vérification a échoué sur ce téléphone (${e.javaClass.simpleName}).")
        }
        try {
            log.record(source, result, clock())
        } catch (e: Exception) {
            // The log only informs: a full disk must not turn a check-in into a failure
        }
        return result
    }
}
