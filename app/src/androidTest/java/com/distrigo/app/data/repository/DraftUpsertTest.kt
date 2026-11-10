package com.distrigo.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.VenteDraftSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DATA-01 on the real tables: a draft the save deleted is never written again, so no Brouillon of a sale already
 * saved can come back to be saved a second time.
 */
@RunWith(AndroidJUnit4::class)
class DraftUpsertTest {

    private lateinit var db: AppDatabase
    private lateinit var drafts: VenteDraftRepository

    private fun snapshot(note: String) = VenteDraftSnapshot(
        clientId = 1, clientName = "AHMED", lines = emptyList(), note = note, montantPaye = "", userName = "",
        lastStep = "validation",
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        drafts = VenteDraftRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aDeletedDraftIsNotWrittenAgain() = runBlocking {
        val id = drafts.upsert(null, snapshot("first"))
        assertNotNull(id)
        assertEquals(id, drafts.upsert(id, snapshot("second")))
        assertEquals(1, db.venteDraftDao().count())

        db.venteDraftDao().deleteById(id!!)            // what the sale's transaction does
        assertNull(drafts.upsert(id, snapshot("late write")))
        assertEquals(0, db.venteDraftDao().count())
    }

    @Test
    fun aNewDraftIsStillCreated() = runBlocking {
        assertNotNull(drafts.upsert(null, snapshot("new")))
        assertEquals(1, db.venteDraftDao().count())
    }
}
