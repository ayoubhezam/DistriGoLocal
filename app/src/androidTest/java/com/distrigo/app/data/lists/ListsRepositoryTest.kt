package com.distrigo.app.data.lists

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.runBlocking
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

    private fun quartier(id: Long, secteurId: Long, nom: String) =
        exec("INSERT INTO quartiers (id, nom, secteur_id, uuid) VALUES (?, ?, ?, ?)", id, nom, secteurId, uuid())

    /** The secteur form: name, commune and description saved; a rename reaches the copies; the commune stays while used. */
    @Test
    fun aSecteurFormSavesItsFieldsAndKeepsAUsedSecteursCommune() {
        assertEquals(ListOutcome.Done, lists.add(ListKind.SECTEURS, " Nord ", commune = "Sedrata", wilaya = "Souk Ahras", description = " Cité du nord "))
        val id = text("SELECT id FROM secteurs WHERE nom = 'Nord'")!!.toLong()
        assertEquals("Cité du nord", text("SELECT description FROM secteurs WHERE id = ?", id))

        // Unused: everything changes
        assertEquals(ListOutcome.Done, lists.updateSecteur(id, "Nord-Est", "Souk Ahras", "Souk Ahras", ""))
        assertEquals("Souk Ahras", text("SELECT commune_name FROM secteurs WHERE id = ?", id))
        assertNull(text("SELECT description FROM secteurs WHERE id = ?", id))

        // Used by a client: the name and the description change, the commune does not
        client(10, id, "Nord-Est")
        assertEquals(
            ListOutcome.Refused("La commune ne peut pas être modifiée : le secteur est utilisé par 1 client."),
            lists.updateSecteur(id, "Nord-Est", "Sedrata", "Souk Ahras", ""),
        )
        assertEquals(ListOutcome.Done, lists.updateSecteur(id, "Nord", "souk ahras ", "Souk Ahras", "Près du marché"))
        assertEquals("Nord", text("SELECT secteur_name FROM clients WHERE id = 10"))
        assertEquals("Près du marché", text("SELECT description FROM secteurs WHERE id = ?", id))

        // The same name in another commune is fine; in the same one it is not
        secteur(2, "Gare", "Souk Ahras")
        assertEquals(ListOutcome.Refused("Le secteur « gare » existe déjà dans cette commune."), lists.updateSecteur(id, "gare", "souk ahras", "Souk Ahras", ""))
        assertEquals(ListOutcome.Done, lists.add(ListKind.SECTEURS, "Gare", commune = "Sedrata", wilaya = "Souk Ahras"))
    }

    /** A quartier, like a client or a tournée, keeps its secteur's commune and stops its delete; the counts show it. */
    @Test
    fun quartiersCountAndHoldTheirSecteur() {
        secteur(1, "Centre", "Souk Ahras")
        quartier(5, 1, "Skanska")
        quartier(6, 1, "400 Logements")
        client(10, 1, "Centre")

        val detail = lists.secteur(1)!!
        assertEquals(2, detail.quartiers)
        assertEquals("2 quartiers et 1 client", detail.usedBy)
        assertEquals(listOf(SecteurSummary(1, "Centre", "Souk Ahras", null, quartiers = 2, clients = 1)), lists.secteurs())
        assertEquals(
            ListOutcome.Refused("La commune ne peut pas être modifiée : le secteur est utilisé par 2 quartiers et 1 client."),
            lists.updateSecteur(1, "Centre", "Sedrata", null, ""),
        )
        assertTrue(lists.delete(ListKind.SECTEURS, 1) is ListOutcome.Refused)
    }

    /** A quartier's name is unique in its secteur only; a rename reaches its clients' copy; it stays while a client is in it. */
    @Test
    fun aQuartierIsUniqueInItsSecteurAndStaysWhileUsed() {
        secteur(1, "Centre", "Souk Ahras")
        secteur(2, "Gare", "Souk Ahras")
        assertEquals(ListOutcome.Done, lists.addQuartier(1, " Skanska "))
        assertEquals(ListOutcome.Refused("Ce quartier existe déjà dans le secteur « Centre »."), lists.addQuartier(1, "skanska"))
        assertEquals(ListOutcome.Done, lists.addQuartier(2, "Skanska"))
        assertEquals(ListOutcome.Refused("Saisissez le nom du quartier."), lists.addQuartier(1, "  "))

        val id = text("SELECT id FROM quartiers WHERE secteur_id = 1")!!.toLong()
        exec("INSERT INTO clients (id, name, balance, customer_type, secteur_id, secteur_name, quartier_id, quartier_name, uuid) " +
            "VALUES (10, 'C10', 0, 'retail', 1, 'Centre', ?, 'Skanska', ?)", id, uuid())
        assertEquals(ListOutcome.Done, lists.renameQuartier(id, "Cité Skanska"))
        assertEquals("Cité Skanska", text("SELECT quartier_name FROM clients WHERE id = 10"))
        assertEquals(listOf(QuartierSummary(id, "Cité Skanska", 1)), lists.quartiers(1))

        assertEquals(
            ListOutcome.Refused("Impossible de supprimer le quartier « Cité Skanska » : il est utilisé par 1 client."),
            lists.deleteQuartier(id),
        )
        exec("UPDATE clients SET deleted_at = 1 WHERE id = 10")
        assertEquals(ListOutcome.Done, lists.deleteQuartier(id))
        assertNull(lists.quartier(id))
    }

    /**
     * « Ajouter des clients »: one transaction puts every client in the quartier with its secteur's place; one already
     * there is left as it is; one from another quartier is counted as moved; a deleted one is skipped.
     */
    @Test
    fun clientsGoToAQuartierWithItsSecteursPlace() {
        exec("INSERT INTO secteurs (id, nom, commune_name, wilaya_name, created_at, uuid) VALUES (1, 'Centre', 'Sedrata', 'Souk Ahras', '2026-10-01', ?)", uuid())
        secteur(2, "Gare", "Souk Ahras")
        quartier(5, 1, "Skanska")
        quartier(6, 2, "400 Logements")
        exec("INSERT INTO clients (id, name, balance, customer_type, wilaya_name, commune_name, uuid) VALUES (10, 'Libre', 0, 'retail', 'Guelma', 'Guelma', ?)", uuid())
        exec("INSERT INTO clients (id, name, balance, customer_type, secteur_id, secteur_name, quartier_id, quartier_name, commune_name, uuid) " +
            "VALUES (11, 'Ailleurs', 0, 'retail', 2, 'Gare', 6, '400 Logements', 'Souk Ahras', ?)", uuid())
        exec("INSERT INTO clients (id, name, balance, customer_type, secteur_id, secteur_name, quartier_id, quartier_name, uuid) " +
            "VALUES (12, 'Déjà là', 0, 'retail', 1, 'Centre', 5, 'Skanska', ?)", uuid())
        exec("INSERT INTO clients (id, name, balance, customer_type, deleted_at, uuid) VALUES (13, 'Supprimé', 0, 'retail', 1, ?)", uuid())

        assertEquals(AssignOutcome.Done(added = 2, moved = 1), lists.assignClientsToQuartier(5, listOf(10, 11, 12, 13)))
        for (id in listOf(10, 11)) {
            assertEquals("5|Skanska|1|Centre|Souk Ahras|Sedrata",
                text("SELECT quartier_id || '|' || quartier_name || '|' || secteur_id || '|' || secteur_name || '|' || wilaya_name || '|' || commune_name FROM clients WHERE id = ?", id))
        }
        assertNull(text("SELECT quartier_id FROM clients WHERE id = 13"))
        assertEquals(listOf("Ailleurs", "Déjà là", "Libre"), lists.clientsIn(5).map { it.name })
        assertTrue(lists.clientsIn(6).isEmpty())
        assertEquals(AssignOutcome.Refused("Le quartier n'existe plus : il a peut-être été supprimé."), lists.assignClientsToQuartier(99, listOf(10)))
    }

    /** `updated_at|version` of client [id]. */
    private fun stamp(id: Int) = text("SELECT updated_at || '|' || version FROM clients WHERE id = ?", id)

    private fun clientIn(id: Int, name: String, secteurId: Long?, secteurName: String?, quartierId: Long?, quartierName: String?, deleted: Boolean = false) = exec(
        "INSERT INTO clients (id, name, balance, customer_type, secteur_id, secteur_name, quartier_id, quartier_name, deleted_at, updated_at, version, uuid) " +
            "VALUES (?, ?, 0, 'retail', ?, ?, ?, ?, ?, 1000, 1, ?)",
        id, name, secteurId, secteurName, quartierId, quartierName, if (deleted) 1 else null, uuid(),
    )

    /**
     * A quartier's rename reaches every client's copy of its name in the same transaction, deleted clients too, and
     * stamps each of them as an edit; a client of another quartier keeps its own name and stamp.
     */
    @Test
    fun aQuartierRenameReachesEveryClient() {
        secteur(1, "Centre", "Sedrata")
        quartier(5, 1, "Skanska")
        quartier(6, 1, "Gare")
        for (id in 10..12) clientIn(id, "C$id", 1, "Centre", 5, "Skanska")
        clientIn(13, "Supprimé", 1, "Centre", 5, "Skanska", deleted = true)
        clientIn(14, "Voisin", 1, "Centre", 6, "Gare")

        assertEquals(ListOutcome.Done, lists.renameQuartier(5, "Cité Skanska"))
        for (id in 10..13) {
            assertEquals("Cité Skanska", text("SELECT quartier_name FROM clients WHERE id = ?", id))
            assertEquals("2", text("SELECT version FROM clients WHERE id = ?", id))
            assertTrue(text("SELECT updated_at FROM clients WHERE id = ?", id)!!.toLong() > 1000)
        }
        assertEquals("Gare", text("SELECT quartier_name FROM clients WHERE id = 14"))
        assertEquals("1000|1", stamp(14))
    }

    /**
     * A client moved to a quartier is stamped as any edit is — `updated_at` later, `version` one up — whether it moves
     * inside its secteur (only the quartier columns change) or from another one; a skipped client is not touched.
     */
    @Test
    fun aMovedClientIsStampedAndASkippedOneIsNot() {
        secteur(1, "Centre", "Sedrata")
        secteur(2, "Gare", "Sedrata")
        quartier(5, 1, "Skanska")
        quartier(6, 1, "Cité")
        quartier(7, 2, "400 Logements")
        clientIn(10, "Même secteur", 1, "Centre", 6, "Cité")
        clientIn(11, "Autre secteur", 2, "Gare", 7, "400 Logements")
        clientIn(12, "Déjà là", 1, "Centre", 5, "Skanska")
        clientIn(13, "Supprimé", null, null, null, null, deleted = true)

        assertEquals(AssignOutcome.Done(added = 2, moved = 2), lists.assignClientsToQuartier(5, listOf(10, 11, 12, 13)))
        for (id in listOf(10, 11)) {
            assertEquals("5", text("SELECT quartier_id FROM clients WHERE id = ?", id))
            assertEquals("2", text("SELECT version FROM clients WHERE id = ?", id))
            assertTrue(text("SELECT updated_at FROM clients WHERE id = ?", id)!!.toLong() > 1000)
        }
        assertEquals("1000|1", stamp(12))
        assertEquals("1000|1", stamp(13))
    }

    /** « Ajouter un nouveau client » from a quartier: the client is stored in it, named from the quartier; a gone quartier refuses. */
    @Test
    fun aClientCreatedFromAQuartierIsStoredInIt() = runBlocking {
        exec("INSERT INTO secteurs (id, nom, commune_name, wilaya_name, created_at, uuid) VALUES (1, 'Centre', 'Sedrata', 'Souk Ahras', '2026-10-01', ?)", uuid())
        quartier(5, 1, "Skanska")
        assertEquals(ClientPlacement(5, "Skanska", 1, "Centre", "Souk Ahras", "Sedrata"), lists.placement(5))
        assertNull(lists.placement(99))

        val products = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        val base = mapOf("name" to "Nouveau", "wilaya_name" to "Souk Ahras", "commune_name" to "Sedrata", "secteur_id" to 1, "secteur_name" to "Centre")
        val id = (products.addClient(base + ("quartier_id" to 5L))["id"] as Double).toInt()
        assertEquals("5|Skanska|1", text("SELECT quartier_id || '|' || quartier_name || '|' || secteur_id FROM clients WHERE id = ?", id))
        assertEquals(listOf("Nouveau"), lists.clientsIn(5).map { it.name })

        val gone = runCatching { products.addClient(base + ("name" to "Perdu") + ("quartier_id" to 99L)) }
        assertEquals("Le quartier n'existe plus : il a peut-être été supprimé.", gone.exceptionOrNull()?.message)
        assertNull(text("SELECT id FROM clients WHERE name = 'Perdu'"))
    }

    /**
     * The client form's quartier picker, on an existing client: a quartier of its secteur is saved with the quartier's own
     * name; one of another secteur is refused and nothing changes; « Aucun quartier » takes it out; another secteur
     * without a quartier leaves it too.
     */
    @Test
    fun aClientsQuartierIsChosenInsideItsSecteur() = runBlocking {
        secteur(1, "Centre", "Sedrata")
        secteur(2, "Gare", "Sedrata")
        quartier(5, 1, "Skanska")
        quartier(7, 2, "400 Logements")
        clientIn(10, "Client", 1, "Centre", null, null)
        val products = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        fun place() = text("SELECT ifnull(quartier_id, '-') || '|' || ifnull(quartier_name, '-') || '|' || secteur_id FROM clients WHERE id = 10")

        products.updateClient(10, mapOf("secteur_id" to 1, "secteur_name" to "Centre", "quartier_id" to 5L))
        assertEquals("5|Skanska|1", place())

        val refused = runCatching { products.updateClient(10, mapOf("secteur_id" to 1, "secteur_name" to "Centre", "quartier_id" to 7L)) }
        assertEquals("Le quartier n'existe plus : il a peut-être été supprimé.", refused.exceptionOrNull()?.message)
        assertEquals("5|Skanska|1", place())

        products.updateClient(10, mapOf("name" to "Client renommé"))
        assertEquals("5|Skanska|1", place())

        products.updateClient(10, mapOf("secteur_id" to 1, "secteur_name" to "Centre", "quartier_id" to null))
        assertEquals("-|-|1", place())

        products.updateClient(10, mapOf("secteur_id" to 1, "secteur_name" to "Centre", "quartier_id" to 5L))
        products.updateClient(10, mapOf("secteur_id" to 2, "secteur_name" to "Gare"))
        assertEquals("-|-|2", place())
    }
}
