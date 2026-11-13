package com.distrigo.app.data.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The photo sweep (DB-02): a file goes a day after it is first found unreferenced, and only then. */
class ImageSweepTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val day = ImageSweep.GRACE_MS
    private val t0 = 1_800_000_000_000L

    private fun hash(c: Char) = c.toString().repeat(64)

    private fun setUp(): Pair<File, ImageSweep> {
        val images = tmp.newFolder("images")
        return images to ImageSweep(images, File(tmp.root, "state/unreferenced"))
    }

    private fun File.photo(c: Char) = File(this, "${hash(c)}.jpg").apply { writeText("jpeg $c") }

    @Test
    fun anUnreferencedPhotoGoesOnlyADayAfterItWasFirstSeen() {
        val (images, sweep) = setUp()
        val kept = images.photo('a')
        val orphan = images.photo('b')

        assertEquals(0, sweep.run(setOf(hash('a')), t0).photosDeleted)
        assertTrue(orphan.isFile)
        // Still within the day: waits
        assertEquals(0, sweep.run(setOf(hash('a')), t0 + day - 1).photosDeleted)
        assertTrue(orphan.isFile)
        // A day after it was first seen unreferenced: gone; the referenced one stays
        assertEquals(1, sweep.run(setOf(hash('a')), t0 + day).photosDeleted)
        assertFalse(orphan.exists())
        assertTrue(kept.isFile)
    }

    /** Named again in between — a form saved, a restore installed — it stays, and starts over if it is dropped again. */
    @Test
    fun aPhotoNamedAgainIsKeptAndStartsOver() {
        val (images, sweep) = setUp()
        val photo = images.photo('c')

        sweep.run(emptySet(), t0)
        sweep.run(setOf(hash('c')), t0 + day / 2)
        assertEquals(0, sweep.run(emptySet(), t0 + day).photosDeleted)
        assertTrue(photo.isFile)
        assertEquals(1, sweep.run(emptySet(), t0 + 2 * day).photosDeleted)
    }

    /** A first sweep long after a photo was written still waits a day: a file's age says nothing about who needs it. */
    @Test
    fun theFirstSweepNeverDeletes() {
        val (images, sweep) = setUp()
        images.photo('d').setLastModified(t0 - 365 * day)
        assertEquals(0, sweep.run(emptySet(), t0).photosDeleted)
        assertEquals(1, sweep.run(emptySet(), t0).waiting)
    }

    @Test
    fun leftoverTempFilesGoOnceADayOld() {
        val (images, sweep) = setUp()
        val old = File(images, "put123.tmp").apply { writeText("half"); setLastModified(t0 - day - 1) }
        val cloud = File(images, ".cloud-x.tmp").apply { writeText("half"); setLastModified(t0 - day - 1) }
        val fresh = File(images, "put456.tmp").apply { writeText("being written"); setLastModified(t0 - 60_000) }
        val other = File(images, "notes.txt").apply { writeText("not ours"); setLastModified(t0 - 10 * day) }

        assertEquals(2, sweep.run(emptySet(), t0).tempsDeleted)
        assertFalse(old.exists())
        assertFalse(cloud.exists())
        assertTrue(fresh.isFile)
        assertTrue(other.isFile)
    }

    /** A clock set back does not count the wait as already over. */
    @Test
    fun aClockSetBackStartsTheWaitAgain() {
        val (images, sweep) = setUp()
        val photo = images.photo('e')
        sweep.run(emptySet(), t0 + 10 * day)
        assertEquals(0, sweep.run(emptySet(), t0).photosDeleted)
        assertTrue(photo.isFile)
    }
}
