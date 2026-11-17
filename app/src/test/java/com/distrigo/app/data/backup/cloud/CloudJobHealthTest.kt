package com.distrigo.app.data.backup.cloud

import androidx.work.WorkInfo.State
import com.distrigo.app.data.backup.cloud.CloudJobHealth.Verdict
import com.distrigo.app.data.backup.cloud.CloudJobHealth.Work
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** [CloudJobHealth.verdict]: when the daily cloud job is lost between WorkManager and Android's job list. */
class CloudJobHealthTest {

    private val id = UUID.fromString("5a62e26a-0000-4000-8000-000000000059")
    private val other = UUID.fromString("1da97760-0000-4000-8000-000000000068")

    @Test
    fun theNightOf6NovemberIsSeenAsALostRun() {
        // WorkManager: RUNNING; Android: no job; nothing running in this process.
        assertEquals(Verdict.Lost(wasRunning = true), CloudJobHealth.verdict(listOf(Work(id, State.RUNNING)), emptySet(), runningHere = false))
    }

    @Test
    fun aRunningJobAndroidStillHoldsIsHealthy() {
        // A job stays in Android's list while it runs: the app starting for it must not replace it.
        assertEquals(Verdict.Healthy, CloudJobHealth.verdict(listOf(Work(id, State.RUNNING)), setOf(id.toString()), runningHere = false))
    }

    @Test
    fun aRunInThisProcessIsHealthyWhateverTheList() {
        assertEquals(Verdict.Healthy, CloudJobHealth.verdict(listOf(Work(id, State.RUNNING)), emptySet(), runningHere = true))
    }

    @Test
    fun aPlannedJobIsHealthy() {
        assertEquals(Verdict.Healthy, CloudJobHealth.verdict(listOf(Work(id, State.ENQUEUED)), setOf(id.toString(), other.toString()), runningHere = false))
    }

    @Test
    fun anEnqueuedJobMissingFromAndroidIsLostButNoRunWasCut() {
        assertEquals(Verdict.Lost(wasRunning = false), CloudJobHealth.verdict(listOf(Work(id, State.ENQUEUED)), setOf(other.toString()), runningHere = false))
    }

    @Test
    fun noUnfinishedWorkIsMissing() {
        assertEquals(Verdict.Missing, CloudJobHealth.verdict(emptyList(), emptySet(), runningHere = false))
        assertEquals(Verdict.Missing, CloudJobHealth.verdict(listOf(Work(id, State.CANCELLED), Work(other, State.SUCCEEDED)), emptySet(), runningHere = false))
    }

    @Test
    fun aFinishedOldEntryBesideAPlannedOneIsHealthy() {
        val works = listOf(Work(other, State.CANCELLED), Work(id, State.ENQUEUED))
        assertEquals(Verdict.Healthy, CloudJobHealth.verdict(works, setOf(id.toString()), runningHere = false))
    }

    private val anchor = java.time.Instant.parse("2026-11-07T02:30:00Z").toEpochMilli()

    @Test
    fun aJobPushedToTheMorningHasSlipped() {
        // After the 7:25 catch-up of 6 November: next run 7:25 on the 7th, the anchor 3:30.
        val next = java.time.Instant.parse("2026-11-07T06:25:28Z").toEpochMilli()
        assertEquals(true, CloudJobHealth.slipped(listOf(Work(id, State.ENQUEUED, next)), anchor))
    }

    @Test
    fun aFewMinutesLateOrStillDueTonightIsNotSlipped() {
        val late = java.time.Instant.parse("2026-11-07T02:52:00Z").toEpochMilli()
        assertEquals(false, CloudJobHealth.slipped(listOf(Work(id, State.ENQUEUED, late)), anchor))
        // 3:40, tonight's run not yet done: planned before the next anchor.
        val dueTonight = java.time.Instant.parse("2026-11-06T02:30:00Z").toEpochMilli()
        assertEquals(false, CloudJobHealth.slipped(listOf(Work(id, State.ENQUEUED, dueTonight)), anchor))
        // Running or unknown: nothing to say.
        assertEquals(false, CloudJobHealth.slipped(listOf(Work(id, State.RUNNING, late + 9 * 3_600_000L)), anchor))
        assertEquals(false, CloudJobHealth.slipped(listOf(Work(id, State.ENQUEUED, null)), anchor))
    }

    @Test
    fun blockedWorkWaitsOnWorkNotOnAndroid() {
        assertEquals(Verdict.Healthy, CloudJobHealth.verdict(listOf(Work(id, State.BLOCKED)), emptySet(), runningHere = false))
    }
}
