package com.distrigo.app.data.backup

import java.util.concurrent.CountDownLatch

/**
 * Runs a waiting restore's install on its own thread at startup, and lets the rest of the app wait for it.
 *
 * The install moves the database and the photo folder and checks the restored database: file work that
 * has no place on the main thread. `DistriGoApplication.onCreate` starts it here; `AppDatabase.getDatabase`
 * waits for it before opening anything, so no code ever sees the half-replaced data; and `MainActivity`
 * shows a waiting screen instead of blocking its first frame.
 *
 * Even *whether* a restore waits is found out on that thread: building the installer resolves three folders
 * and the check reads two marker files, which StrictMode flagged on the main thread at every start. So the
 * thread always runs; on a start with nothing waiting it is done in about a millisecond, and the database's
 * first open waits for that at most.
 */
object RestoreStartup {

    private val lock = Any()
    @Volatile private var gate: CountDownLatch? = null

    /** How the install ended, once it has. */
    @Volatile var result: RestoreResult? = null
        private set

    val inProgress: Boolean get() = gate?.let { it.count > 0 } ?: false

    /** Whether [begin] ran in this process: from then on the database waits for it, not for its own check. */
    val started: Boolean get() = gate != null

    /**
     * On a thread of its own: builds the installer, and installs whatever it has waiting — a restore, or an
     * install a kill interrupted; with nothing waiting it only clears what an earlier one left. [onDone] runs
     * on that thread once it ends, with how a restore ended, or null when none was installed.
     */
    fun begin(installer: () -> RestoreInstaller, onDone: (RestoreResult?) -> Unit = {}) = synchronized(lock) {
        if (gate != null) return
        val latch = CountDownLatch(1)
        gate = latch
        Thread({
            try {
                result = installer().installPending()
            } finally {
                latch.countDown()
                onDone(result)
            }
        }, "restore-install").start()
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
