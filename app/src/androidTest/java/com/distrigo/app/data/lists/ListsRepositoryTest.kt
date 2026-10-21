package com.distrigo.app.data.lists

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** « Listes et catégories » on an in-memory database with the app's triggers: adding, renaming every copy, deleting. */
@RunWith(AndroidJUnit4::class)
class ListsRepositoryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var lists: ListsRepository

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000021" }
            .build()
        sql = db.openHelper.writableDatabase
        lists = ListsRepository(db)
    }

    @After
    fun close() = db.close()

    private fun uuid() = UUID.randomUUID().toString()
    private fun exec(statement: String, vararg args: Any?) = sql.execSQL(statement, args)
    private fun text(query: String, vararg args: Any?): String? = sql.query(query, args).use { if (it.moveToFirst()) it.getString(0) else null }
    private fun deletedAt(table: String, id: Long): Long? =
        sql.query("SELECT deleted_at FROM $table WHERE id = ?", arrayOf(id)).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

    private fun category(id: Long, name: String) = exec("INSERT INTO categories (id, name, sort_order, uuid) VALUES (?, ?, 0, ?)", id, name, uuid())
    private fun sousCategorie(id: Long, categoryId: Long, name: String) =
        exec("INSERT INTO sous_categories (id, category_id, name, sort_order, uuid) VALUES (?, ?, ?, 0, ?)", id, categoryId, name, uuid())
    private fun marque(id: Long, name: String) = exec("INSERT INTO marques (id, name, sort_order, uuid) VALUES (?, ?, 0, ?)", id, name, uuid())

    private fun product(id: Long, categoryId: Long? = null, category: String? = null, marqueId: Long? = null, marque: String? = null, deleted: Boolean = false) = exec(
        "INSERT INTO products (id, name, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, has_expiry, camion_stock, " +
            "category_id, category_name, marque_id, marque_name, uuid, deleted_at) VALUES (?, ?, 120, 100, 0, 0, 'pièce', 0, 1, 0, 0, ?, ?, ?, ?, ?, ?)",
        id, "P$id", categoryId, category, marqueId, marque, uuid(), if (deleted) 1_789_600_000_000L else null
    )

    private fun chargeType(id: Long, name: String, default: Boolean = false) = exec(
        "INSERT INTO charge_types (id, name, icon, color_hex, is_default, created_at, uuid) VALUES (?, ?, 'category', '#98A2B3', ?, '2026-10-01T00:00:00Z', ?)",
        id, name, if (default) 1 else 0, uuid()
    )
    private fun chargeSubtype(id: Long, typeId: Long, name: String) = exec(
        "INSERT INTO charge_subtypes (id, type_id, name, icon, has_fournisseur, is_default, created_at, uuid) VALUES (?, ?, ?, 'category', 0, 0, '2026-10-01T00:00:00Z', ?)",
        id, typeId, name, uuid()
    )
    private fun charge(id: Long, typeId: Long, typeName: String, subtypeId: Long, subtypeName: String) = exec(
        "INSERT INTO charges (id, type_id, type_name, subtype_id, subtype_name, montant, date_time, created_at, uuid) " +
            "VALUES (?, ?, ?, ?, ?, 1500, '2026-10-02T09:00:00Z', '2026-10-02T09:00:00Z', ?)",
        id, typeId, typeName, subtypeId, subtypeName, uuid()
    )

    private fun secteur(id: Long, nom: String, commune: String) =
        exec("INSERT INTO secteurs (id, nom, commune_name, created_at, uuid) VALUES (?, ?, ?, '2026-10-01T00:00:00Z', ?)", id, nom, commune, uuid())
    private fun client(id: Long, secteurId: Long, secteurName: String) = exec(
        "INSERT INTO clients (id, name, balance, customer_type, secteur_id, secteur_name, uuid) VALUES (?, ?, 0, 'retail', ?, ?, ?)",
        id, "C$id", secteurId, secteurName, uuid()
    )

    @Test
    fun aRenameRewritesEveryCopyOfTheName() {
        category(1, "Boissns")
        product(10, categoryId = 1, category = "Boissns")
        product(11, categoryId = 1, category = "Boissns", deleted = true)   // in the bin: its copy is fixed too
        chargeType(2, "Véhicle")
        chargeSubtype(20, 2, "Carburnt")
        charge(200, 2, "Véhicle", 20, "Carburnt")
        secteur(3, "Centre vile", "Souk Ahras")
        client(30, 3, "Centre vile")
        exec("INSERT INTO tournees (id, status, nom, created_at, uuid) VALUES (4, 'fermée', 'T', '2026-10-01T00:00:00Z', ?)", uuid())
        exec("INSERT INTO tournee_secteurs (tournee_id, secteur_id, secteur_name, order_index, uuid) VALUES (4, 3, 'Centre vile', 0, ?)", uuid())

        assertEquals(ListOutcome.Done, lists.rename(ListKind.CATEGORIES, 1, "  Boissons "))
        assertEquals(ListOutcome.Done, lists.rename(ListKind.CHARGE_TYPES, 2, "Véhicule"))
        assertEquals(ListOutcome.Done, lists.rename(ListKind.CHARGE_SUBTYPES, 20, "Carburant"))
        assertEquals(ListOutcome.Done, lists.rename(ListKind.SECTEURS, 3, "Centre-ville"))

        assertEquals("Boissons", text("SELECT name FROM categories WHERE id = 1"))
        assertEquals("Boissons", text("SELECT category_name FROM products WHERE id = 10"))
        assertEquals("Boissons", text("SELECT category_name FROM products WHERE id = 11"))
        assertEquals("Véhicule", text("SELECT type_name FROM charges WHERE id = 200"))
        assertEquals("Carburant", text("SELECT subtype_name FROM charges WHERE id = 200"))
        assertEquals("Centre-ville", text("SELECT nom FROM secteurs WHERE id = 3"))
        assertEquals("Centre-ville", text("SELECT secteur_name FROM clients WHERE id = 30"))
        assertEquals("Centre-ville", text("SELECT secteur_name FROM tournee_secteurs WHERE secteur_id = 3"))
    }

    @Test
    fun aNameIsUniqueInItsPlaceCaseAside() {
        category(1, "Boissons")
        category(2, "Laitiers")
        sousCategorie(10, 1, "Jus")
        secteur(3, "Centre", "Souk Ahras")

        assertEquals(ListOutcome.Refused("La catégorie « boissons » existe déjà."), lists.rename(ListKind.CATEGORIES, 2, "boissons"))
        assertEquals(ListOutcome.Refused("La sous-catégorie « JUS » existe déjà dans cette catégorie."), lists.add(ListKind.SOUS_CATEGORIES, "JUS", parentId = 1))
        // The same name in another category, or another commune, is fine
        assertEquals(ListOutcome.Done, lists.add(ListKind.SOUS_CATEGORIES, "Jus", parentId = 2))
        assertEquals(ListOutcome.Done, lists.add(ListKind.SECTEURS, "Centre", commune = "Sedrata", wilaya = "Souk Ahras"))
        assertEquals(ListOutcome.Refused("Le secteur « Centre » existe déjà dans cette commune."), lists.add(ListKind.SECTEURS, "Centre", commune = "Souk Ahras"))
        // A case-only change of its own name is a rename
        assertEquals(ListOutcome.Done, lists.rename(ListKind.CATEGORIES, 2, "LAITIERS"))
        assertEquals(ListOutcome.Refused("Saisissez un nom."), lists.add(ListKind.MARQUES, "   "))
    }

    @Test
    fun anAddedEntryJoinsItsListWithItsPlace() {
        chargeType(2, "Véhicule")
        assertEquals(ListOutcome.Done, lists.add(ListKind.CHARGE_SUBTYPES, "Péage", parentId = 2, hasFournisseur = true))
        assertEquals(ListOutcome.Done, lists.add(ListKind.MARQUES, " Ifri  Gold "))

        val subtype = lists.entries(ListKind.CHARGE_SUBTYPES, parentId = 2).single()
        assertEquals("Péage", subtype.name)
        assertEquals("1", text("SELECT has_fournisseur FROM charge_subtypes WHERE id = ?", subtype.id))
        assertEquals(listOf("Ifri Gold"), lists.entries(ListKind.MARQUES).map { it.name })
        assertNotNull(text("SELECT uuid FROM marques WHERE name = 'Ifri Gold'"))
        assertEquals(1, lists.entries(ListKind.CHARGE_TYPES).single().children)
    }

    @Test
    fun onlyAnUnusedEntryIsDeletedAndItsChildrenGoWithIt() {
        category(1, "Boissons")
        sousCategorie(10, 1, "Jus")
        category(2, "Laitiers")
        product(20, categoryId = 2, category = "Laitiers")
        marque(3, "Ifri")
        product(30, marqueId = 3, marque = "Ifri", deleted = true)   // only a product in the bin uses it

        val laitiers = lists.entries(ListKind.CATEGORIES).first { it.id == 2L }
        assertEquals("1 produit", laitiers.usages.single().toString())
        assertFalse(laitiers.deletable)
        assertEquals(
            ListOutcome.Refused("Impossible de supprimer la catégorie « Laitiers » : elle est utilisée par 1 produit."),
            lists.delete(ListKind.CATEGORIES, 2),
        )
        assertNull(deletedAt("categories", 2))

        assertEquals(ListOutcome.Done, lists.delete(ListKind.CATEGORIES, 1))
        assertNotNull(deletedAt("categories", 1))
        assertNotNull(deletedAt("sous_categories", 10))
        assertEquals(ListOutcome.Done, lists.delete(ListKind.MARQUES, 3))
        assertEquals(listOf("Laitiers"), lists.entries(ListKind.CATEGORIES).map { it.name })
    }

    @Test
    fun aBuiltInTypeIsNeverDeletedButCanBeRenamed() {
        chargeType(1, "Véhicule", default = true)
        exec("INSERT INTO perte_types (id, name, icon, color_hex, is_default, created_at, uuid) VALUES (2, 'Casse', 'broken_image', '#F04438', 1, '2026-10-01T00:00:00Z', ?)", uuid())

        assertTrue(lists.entries(ListKind.PERTE_TYPES).single().isDefault)
        assertEquals(ListOutcome.Refused(ListsRepository.BUILT_IN), lists.delete(ListKind.PERTE_TYPES, 2))
        assertEquals(ListOutcome.Refused(ListsRepository.BUILT_IN), lists.delete(ListKind.CHARGE_TYPES, 1))
        assertEquals(ListOutcome.Done, lists.rename(ListKind.PERTE_TYPES, 2, "Casse / dommage"))
        assertNull(deletedAt("perte_types", 2))
    }

    @Test
    fun aSecteurUsedByAClientStaysAndAnUnusedOneIsDeletedForGood() {
        secteur(1, "Centre", "Souk Ahras")
        secteur(2, "Gare", "Souk Ahras")
        client(10, 1, "Centre")

        assertEquals(
            ListOutcome.Refused("Impossible de supprimer le secteur « Centre » : il est utilisé par 1 client."),
            lists.delete(ListKind.SECTEURS, 1),
        )
        assertEquals(ListOutcome.Done, lists.delete(ListKind.SECTEURS, 2))
        assertNull(text("SELECT nom FROM secteurs WHERE id = 2"))
        assertEquals(mapOf(ListKind.SECTEURS to 1), lists.counts().filterKeys { it == ListKind.SECTEURS })
    }
}
