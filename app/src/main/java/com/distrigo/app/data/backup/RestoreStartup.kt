package com.distrigo.app.data.backup

import java.util.concurrent.CountDownLatch

/**
 * Runs a waiting restore's install on its own thread at startup, and lets the rest of the app wait for it.
 *
 * The install moves the database and the photo folder and checks the restored database: file work that
 * has no place on the main thread. `DistriGoApplication.onCreate` starts it here; `AppDatabase.getDatabase`
 * waits for it before opening anything, so no code ever sees the half-replaced data; and `MainActivity`
 * shows a waiting screen instead of blocking its first frame.
 */
object RestoreStartup {

    private val lock = Any()
    @Volatile private var gate: CountDownLatch? = null

    /** How the install ended, once it has. */
    @Volatile var result: RestoreResult? = null
        private set

    val inProgress: Boolean get() = gate?.let { it.count > 0 } ?: false

    /**
     * Starts installing whatever [installer] has waiting, on a thread of its own. Returns false, and starts
     * nothing, when nothing waits. [onDone] runs on the install thread once it ends.
     */
    fun begin(installer: RestoreInstaller, onDone: (RestoreResult?) -> Unit = {}): Boolean = synchronized(lock) {
        if (gate != null) return true
        if (!installer.needsStart) return false
        val latch = CountDownLatch(1)
        gate = latch
        Thread({
            try {
                result = installer.installPending()
            } finally {
                latch.countDown()
                onDone(result)
            }
        }, "restore-install").start()
        true
    }

    /** Blocks until a running install ends; returns at once when none runs. */
    fun await() {
        gate?.await()
    }

    /** For tests, which reuse the process. */
    internal fun reset() = synchronized(lock) {
        gate = null
        result = null
    }
}
