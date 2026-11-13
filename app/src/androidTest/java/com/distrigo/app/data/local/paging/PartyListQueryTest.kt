package com.distrigo.app.data.local.paging

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The client and supplier lists searched, counted and paged in SQL (PERF-01): the same clients the old in-memory
 * filters found, in the order the pickers fold them, and a page source that reaches any position.
 */
@RunWith(AndroidJUnit4::class)
class PartyListQueryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ProductRepository

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        repo = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
    }

    @After
    fun close() = db.close()

    private suspend fun client(name: String, phone: String? = null, secteur: String? = null, balance: Double = 0.0,
                               type: String = "retail", wilaya: String? = null, commune: String? = null): Int =
        (repo.addClient(mapOf("name" to name, "phone" to phone, "secteur_name" to secteur, "balance" to balance,
            "customer_type" to type, "wilaya_name" to wilaya, "commune_name" to commune))["id"] as Number).toInt()

    private suspend fun names(query: ClientListQuery): List<String> =
        db.clientDao().pageClients(ClientListSql.page(query, 0, 1000)).map { it.name }

    private fun setBalance(id: Int, balance: Double) =
        db.openHelper.writableDatabase.execSQL("UPDATE clients SET balance = ? WHERE id = ?", arrayOf<Any>(balance, id))

    @Test
    fun theSearchFindsEveryWordInTheNameOrThePhone() = runBlocking {
        client("Mostafa Kaci", phone = "0550123456")
        client("Ahmed Kaci")
        client("Supérette 100%_")

        assertEquals(listOf("Ahmed Kaci", "Mostafa Kaci"), names(ClientListQuery(search = "kaci")))
        assertEquals(listOf("Mostafa Kaci"), names(ClientListQuery(search = "kaci 0550")))
        assertEquals(listOf("Mostafa Kaci"), names(ClientListQuery(search = "  KACI   mostafa ")))
        // % and _ are letters here, not wildcards
        assertEquals(listOf("Supérette 100%_"), names(ClientListQuery(search = "100%_")))
        assertEquals(emptyList<String>(), names(ClientListQuery(search = "10%x")))
    }

    @Test
    fun theFiltersMatchTheSheet() = runBlocking {
        val owing = client("A", type = "wholesale", wilaya = "Alger", commune = "Bab Ezzouar", secteur = "Est")
        val advance = client("B", wilaya = " alger ", secteur = "Ouest")
        val settled = client("C", wilaya = "Oran")
        setBalance(owing, 150.0); setBalance(advance, -20.0); setBalance(settled, 0.004)

        assertEquals(listOf("A"), names(ClientListQuery(type = "wholesale")))
        assertEquals(listOf("B", "A"), names(ClientListQuery(wilaya = "ALGER")))
        assertEquals(listOf("A"), names(ClientListQuery(wilaya = "Alger", commune = "bab ezzouar")))
        assertEquals(listOf("A"), names(ClientListQuery(balance = BalanceBand.OWING)))
        assertEquals(listOf("B"), names(ClientListQuery(balance = BalanceBand.ADVANCE)))
        assertEquals(listOf("C"), names(ClientListQuery(balance = BalanceBand.SETTLED)))
        assertEquals(listOf("A"), names(ClientListQuery(secteur = "est")))
        assertEquals(listOf("C", "A"), names(ClientListQuery(excludeIds = listOf(advance))))

        val summary = repo.observeClientSummary(ClientListQuery()).first()
        assertEquals(PartySummary(total = 3, owingCount = 1, owingTotal = 150.0, withoutQuartier = 3), summary)
    }

    /** By secteur, « Sans secteur » last, newest first in each: the folds' counts line up with the rows. */
    @Test
    fun theFoldsAndTheRowsAgree() = runBlocking {
        val est1 = client("Est 1", secteur = "Est")
        client("Sans 1")
        client("Centre 1", secteur = "Centre")
        client("Est 2", secteur = "Est")
        client("Blank", secteur = "  ")

        val query = ClientListQuery(bySecteur = true)
        assertEquals(listOf("Centre 1", "Est 2", "Est 1", "Blank", "Sans 1"), names(query))
        assertEquals(
            listOf(SecteurCount("Centre", 1), SecteurCount("Est", 2), SecteurCount(null, 2)),
            repo.observeSecteurCounts(query).first(),
        )
        assertEquals(setOf(est1, est1 + 3), repo.clientIdsInSecteur(query, "Est").toSet())
        assertEquals(2, repo.clientIdsInSecteur(query, null).size)
    }

    /** Any position can be read: a fold opened far down the list jumps there, with what is before and after counted. */
    @Test
    fun aPageSourceReachesAnyPosition() = runBlocking {
        repeat(100) { client("C%03d".format(it)) }
        val query = ClientListQuery()
        val source = OffsetPagingSource(
            db, arrayOf("clients"),
            count = { db.clientDao().countClients(ClientListSql.count(query)) },
            rows  = { offset, limit -> db.clientDao().pageClients(ClientListSql.page(query, offset, limit)) },
        )

        val jump = source.load(PagingSource.LoadParams.Refresh(70, 20, true)) as PagingSource.LoadResult.Page
        assertEquals(60, jump.itemsBefore)
        assertEquals(20, jump.itemsAfter)
        assertEquals("C039", jump.data.first().name)     // newest first: position 60 is the 40th created
        assertEquals(60, jump.prevKey)
        assertEquals(80, jump.nextKey)

        val after = source.load(PagingSource.LoadParams.Append(80, 30, true)) as PagingSource.LoadResult.Page
        assertEquals(20, after.data.size)
        assertNull(after.nextKey)
        assertEquals(0, after.itemsAfter)

        val before = source.load(PagingSource.LoadParams.Prepend(60, 30, true)) as PagingSource.LoadResult.Page
        assertEquals(30, before.itemsBefore)
        assertEquals(30, before.data.size)
        assertEquals(30, before.prevKey)

        // Near the end, a refresh still fills its page
        val end = source.load(PagingSource.LoadParams.Refresh(99, 20, true)) as PagingSource.LoadResult.Page
        assertEquals(80, end.itemsBefore)
        assertEquals(20, end.data.size)
    }

    @Test
    fun suppliersAreSearchedByNameOrPhoneInNameOrder() = runBlocking {
        repo.addSupplier(mapOf("name" to "Soummam", "phone" to "0770"))
        repo.addSupplier(mapOf("name" to "Danone", "phone" to "0550"))
        repo.addSupplier(mapOf("name" to "Candia"))
        val all = db.supplierDao().pageSuppliers(SupplierListSql.page(SupplierListQuery(), 0, 10)).map { it.name }
        assertEquals(listOf("Candia", "Danone", "Soummam"), all)
        val found = db.supplierDao().pageSuppliers(SupplierListSql.page(SupplierListQuery(search = "077"), 0, 10)).map { it.name }
        assertEquals(listOf("Soummam"), found)
        assertEquals(3, repo.observeSupplierSummary().first().total)
    }

    @Suppress("unused")
    private fun List<Client>.ids() = map { it.id }
}
