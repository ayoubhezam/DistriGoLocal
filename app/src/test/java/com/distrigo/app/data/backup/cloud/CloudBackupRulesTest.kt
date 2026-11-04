package com.distrigo.app.data.backup.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Retention edge cases, the state file, and when the user is told. */
class CloudBackupRulesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Algiers")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun entry(id: String, at: Instant) =
        CloudBackupEntry(id, 10, null, CloudBackupMeta("biz-1", "phone-A", at.toEpochMilli(), 64, "1.4.0", 10, "k"))

    // ---- retention ----

    @Test
    fun theNewestIsKeptHoweverOld() {
        val old = entry("old", at("2025-01-01T03:30"))
        assertTrue(CloudRetention.toDelete(listOf(old), at("2026-11-03T03:30"), zone).isEmpty())
    }

    @Test
    fun severalOnOneDayKeepOnlyTheNewestOfThatDay() {
        val morning = entry("morning", at("2026-11-02T03:30"))
        val noon = entry("noon", at("2026-11-02T12:00"))
        val evening = entry("evening", at("2026-11-02T19:00"))
        val today = entry("today", at("2026-11-03T03:30"))
        val deleted = CloudRetention.toDelete(listOf(morning, noon, evening, today), at("2026-11-03T04:00"), zone)
        assertEquals(setOf("morning", "noon"), deleted.map { it.id }.toSet())
    }

    @Test
    fun theBackupJustSentIsNeverDeletedEvenOnATie() {
        val time = at("2026-11-03T03:30")
        for (order in listOf(listOf("a", "b"), listOf("b", "a"))) {
            val entries = order.map { entry(it, time) }
            assertEquals(listOf("a"), CloudRetention.toDelete(entries, at("2026-11-03T04:00"), zone, justSent = "b").map { it.id })
        }
    }

    @Test
    fun aLostPhonesLastBackupSurvivesANewPhoneOnTheSameDay() {
        // The old phone's real backup this morning; the new phone, linked at noon, backs up its empty data.
        val oldPhone = CloudBackupEntry("old", 10, null, CloudBackupMeta("biz-1", "phone-A", at("2026-11-03T03:30").toEpochMilli(), 64, "1.4.0", 10, "k"))
        val newPhone = CloudBackupEntry("new", 10, null, CloudBackupMeta("biz-1", "phone-B", at("2026-11-03T12:00").toEpochMilli(), 64, "1.4.0", 10, "k"))
        assertTrue(CloudRetention.toDelete(listOf(oldPhone, newPhone), at("2026-11-03T12:01"), zone, justSent = "new").isEmpty())
        // Months later, still kept: it is that phone's newest.
        val later = CloudBackupEntry("later", 10, null, CloudBackupMeta("biz-1", "phone-B", at("2027-03-01T03:30").toEpochMilli(), 64, "1.4.0", 10, "k"))
        assertTrue(CloudRetention.toDelete(listOf(oldPhone, newPhone, later), at("2027-03-01T04:00"), zone, justSent = "later").none { it.id == "old" })
    }

    @Test
    fun aPhonesOlderBackupsStillAgeOut() {
        val older = CloudBackupEntry("older", 10, null, CloudBackupMeta("biz-1", "phone-A", at("2026-09-01T03:30").toEpochMilli(), 64, "1.4.0", 10, "k"))
        val newest = CloudBackupEntry("newest", 10, null, CloudBackupMeta("biz-1", "phone-A", at("2026-11-03T03:30").toEpochMilli(), 64, "1.4.0", 10, "k"))
        assertEquals(listOf("older"), CloudRetention.toDelete(listOf(older, newest), at("2026-11-03T04:00"), zone).map { it.id })
    }

    @Test
    fun dayBoundariesFollowThePhonesZone() {
        // 23:30 UTC on 2 Nov is 00:30 on 3 Nov in Algiers: the same day as the morning backup.
        val lateUtc = entry("late", Instant.parse("2026-11-02T23:30:00Z"))
        val morning = entry("morning", at("2026-11-03T03:30"))
        val deleted = CloudRetention.toDelete(listOf(lateUtc, morning), at("2026-11-03T04:00"), zone)
        assertEquals(listOf("late"), deleted.map { it.id })
    }

    @Test
    fun backupsDatedInTheFutureAreKept() {
        val future = entry("future", at("2027-01-01T03:30"))
        val now = entry("now", at("2026-11-03T03:30"))
        assertTrue(CloudRetention.toDelete(listOf(future, now), at("2026-11-03T04:00"), zone).isEmpty())
    }

    @Test
    fun olderThanFourWeeksGoesUnlessNewest() {
        val today = entry("today", at("2026-11-03T03:30"))
        val fiveWeeks = entry("five-weeks", at("2026-09-28T03:30"))
        assertEquals(listOf("five-weeks"), CloudRetention.toDelete(listOf(today, fiveWeeks), at("2026-11-03T04:00"), zone).map { it.id })
    }

    @Test
    fun weeksStartOnSunday() {
        // Tuesday 3 Nov 2026 → Sunday 1 Nov.
        assertEquals(java.time.LocalDate.parse("2026-11-01"), CloudRetention.weekOf(java.time.LocalDate.parse("2026-11-03")))
        assertEquals(java.time.LocalDate.parse("2026-11-01"), CloudRetention.weekOf(java.time.LocalDate.parse("2026-11-01")))
        assertEquals(java.time.LocalDate.parse("2026-10-25"), CloudRetention.weekOf(java.time.LocalDate.parse("2026-10-31")))
    }

    // ---- state file ----

    @Test
    fun stateSurvivesTheFile() {
        val store = CloudBackupStore(File(folder.root, "state"))
        val pending = PendingUpload("backup-1.dgenc", "fp", 123, 64, "phone-A", 999, "key", "https://upload/s1", listOf("aa11", "bb22"))
        val written = store.update {
            it.copy(
                linked = true, providerId = "gdrive", account = "owner@gmail.com", businessId = "biz-1",
                linkedAt = Instant.parse("2026-11-03T10:00:00Z"), enabled = false, allowMobileData = true, includePhotos = false,
                lastFingerprint = "fp0", lastAt = Instant.parse("2026-11-02T02:30:00Z"), lastEntryId = "f1", lastSize = 4_000_000_000,
                lastAttemptAt = Instant.parse("2026-11-03T02:30:00Z"), lastScheduledRunAt = Instant.parse("2026-11-03T02:30:00Z"),
                problemStreak = 3, lastProblem = CloudBackupProblem.QUOTA_EXCEEDED, pending = pending,
            )
        }
        assertEquals(written, CloudBackupStore(File(folder.root, "state")).read())
        assertEquals(null, store.update { it.copy(pending = null) }.pending)
    }

    @Test
    fun defaultsAreWifiOnlyWithPhotosAndAutomatic() {
        val state = CloudBackupStore(File(folder.root, "none")).read()
        assertFalse(state.linked)
        assertTrue(state.enabled)
        assertFalse(state.allowMobileData)
        assertTrue(state.includePhotos)
    }

    @Test
    fun aDamagedStateFileReadsAsDefaults() {
        val dir = File(folder.root, "bad").apply { mkdirs() }
        File(dir, "state.json").writeText("{ not json")
        assertEquals(CloudBackupState(), CloudBackupStore(dir).read())
    }

    // ---- photo names and index ----

    @Test
    fun photoNamesAreStablePerKeyAndDifferAcrossKeys() {
        val one = BackupKey.generate()
        val two = BackupKey.generate()
        val hash = "a".repeat(64)
        assertEquals(PhotoNames.name(PhotoNames.nameKey(one), hash), PhotoNames.name(PhotoNames.nameKey(one), hash.uppercase()))
        assertFalse(PhotoNames.name(PhotoNames.nameKey(one), hash) == PhotoNames.name(PhotoNames.nameKey(two), hash))
        assertEquals(43, PhotoNames.name(PhotoNames.nameKey(one), hash).length)
    }

    @Test
    fun photoIndexBelongsToItsKey() {
        val index = CloudPhotoIndex(File(folder.root, "index"))
        index.update("key-1") { it + ("b1" to setOf("n1", "n2")) }
        assertEquals(mapOf("b1" to setOf("n1", "n2")), CloudPhotoIndex(File(folder.root, "index")).read("key-1"))
        assertTrue("a new key starts empty", index.read("key-2").isEmpty())
        index.update("key-2") { it + ("b9" to emptySet()) }
        assertTrue(index.read("key-1").isEmpty())
    }

    // ---- alerts ----

    private fun linked(problem: CloudBackupProblem?, streak: Int, enabled: Boolean = true) =
        CloudBackupState(linked = true, enabled = enabled, lastProblem = problem, problemStreak = streak)

    @Test
    fun problemsNeedingTheUserAlertAtOnce() {
        for (problem in listOf(CloudBackupProblem.QUOTA_EXCEEDED, CloudBackupProblem.NEEDS_CONSENT, CloudBackupProblem.NEEDS_PASSWORD)) {
            assertTrue(problem.name, CloudBackupAlerts.shouldAlert(linked(problem, 1)))
        }
    }

    @Test
    fun passingProblemsAlertOnTheSecondRun() {
        assertFalse(CloudBackupAlerts.shouldAlert(linked(CloudBackupProblem.NO_CONNECTION, 1)))
        assertTrue(CloudBackupAlerts.shouldAlert(linked(CloudBackupProblem.NO_CONNECTION, 2)))
    }

    @Test
    fun noAlertWhenFineOrNotLinked() {
        assertFalse(CloudBackupAlerts.shouldAlert(linked(null, 0)))
        assertFalse(CloudBackupAlerts.shouldAlert(CloudBackupState(lastProblem = CloudBackupProblem.QUOTA_EXCEEDED, problemStreak = 5)))
    }
}
