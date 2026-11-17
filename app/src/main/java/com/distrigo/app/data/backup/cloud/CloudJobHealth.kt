package com.distrigo.app.data.backup.cloud

import android.app.job.JobScheduler
import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Whether the daily cloud job is really planned. On 2026-11-06 Android stopped the job 1.5 s after starting it and
 * Samsung froze DistriGo before WorkManager could plan it again: WorkManager kept it as RUNNING, Android's job list no
 * longer had it, and no night ran until the app was opened. KEEP and UPDATE both leave a RUNNING job alone, so the
 * mismatch is looked for here and repaired by replacing the job.
 */
object CloudJobHealth {

    /** The daily job as WorkManager has it. */
    data class Work(val id: UUID, val state: WorkInfo.State, val nextRunAt: Long? = null)

    sealed class Verdict {
        /** Planned, or running in this process right now. */
        data object Healthy : Verdict()

        /** WorkManager has no unfinished daily job: plan one. */
        data object Missing : Verdict()

        /** WorkManager has it, Android doesn't: replace it. [wasRunning]: a run was cut and never recorded. */
        data class Lost(val wasRunning: Boolean) : Verdict()
    }

    /**
     * [works]: the unique daily work's entries; [plannedIds]: the work ids Android's job list holds for this app (a
     * job stays there while it runs); [runningHere]: a cloud worker is running in this process.
     */
    fun verdict(works: List<Work>, plannedIds: Set<String>, runningHere: Boolean): Verdict {
        val open = works.filter { !it.state.isFinished }
        if (open.isEmpty()) return Verdict.Missing
        if (runningHere) return Verdict.Healthy
        if (open.any { it.id.toString() in plannedIds }) return Verdict.Healthy
        // BLOCKED waits on another work, never on Android: only an ENQUEUED or RUNNING job must be in its list.
        if (open.none { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }) return Verdict.Healthy
        return Verdict.Lost(wasRunning = open.any { it.state == WorkInfo.State.RUNNING })
    }

    /**
     * The planned job's next run has slipped more than [SLIP] past the next 3:30 ([anchor]): a 24-hour job that once
     * ran late keeps running late. A run still due tonight (planned before [anchor]) never counts.
     */
    fun slipped(works: List<Work>, anchor: Long): Boolean {
        val next = works.firstOrNull { it.state == WorkInfo.State.ENQUEUED }?.nextRunAt ?: return false
        return next != Long.MAX_VALUE && next - anchor > SLIP_MILLIS
    }

    const val SLIP_MILLIS = 2 * 3_600_000L

    /** Cloud workers running in this process (the daily one and « Sauvegarder maintenant »). */
    internal val running = AtomicInteger(0)

    /** What [verdict] reads. Blocking: not on the main thread. */
    interface Probe {
        fun works(name: String): List<Work>
        fun plannedIds(): Set<String>
        fun runningHere(): Boolean
    }

    /** [Probe] from WorkManager and from Android's job list. */
    class AndroidProbe(private val context: Context, private val workManager: () -> WorkManager) : Probe {

        override fun works(name: String): List<Work> =
            workManager().getWorkInfosForUniqueWork(name).get().map { Work(it.id, it.state, it.nextScheduleTimeMillis) }

        override fun plannedIds(): Set<String> {
            val jobs = context.getSystemService(JobScheduler::class.java)?.allPendingJobs.orEmpty()
            return jobs.mapNotNull { it.extras.getString(EXTRA_WORK_SPEC_ID) }.toSet()
        }

        override fun runningHere(): Boolean = running.get() > 0
    }

    /** The key WorkManager (SystemJobInfoConverter) stores a job's work id under. */
    private const val EXTRA_WORK_SPEC_ID = "EXTRA_WORK_SPEC_ID"
}
