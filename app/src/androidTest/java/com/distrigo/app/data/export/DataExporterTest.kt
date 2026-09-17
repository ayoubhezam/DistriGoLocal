package com.distrigo.app.data.export

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Every dataset, exported from an in-memory database with the app's triggers, read back as a spreadsheet would.
 * Times are in Algeria, where 23:30 UTC is 00:30 the next day.
 */
@RunWith(AndroidJUnit4::class)
class DataExporterTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val algiers = ZoneId.of("Africa/Algiers")
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var exporter: DataExporter

    private val the17th = ExportPeriod(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 17))

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .withChangeTracking()
            .withDeviceIdentity { "6ded79d5-0000-4000-8000-000000000011" }
            .build()
        sql = db.openHelper.writableDatabase
        exporter = DataExporter(sql, algiers)
        seed()
    }

    @After
    fun close() {
        db.close()
    }

    private fun insert(statement: String, vararg args: Any?) = sql.execSQL(statement, args)
    private fun uuid() = UUID.randomUUID().toString()

    private fun seed() {
        insert("INSERT INTO clients (id, name, phone, balance, customer_type, secteur_name, wilaya_name, uuid) VALUES (1, 'Épicerie El Amel', '0555123456', 2450.5, 'retail', 'Centre', 'Souk Ahras', ?)", uuid())
        insert("INSERT INTO clients (id, name, balance, customer_type, uuid) VALUES (2, '=HYPERLINK(\"http://x\")', -300, 'wholesale', ?)", uuid())
        insert("INSERT INTO clients (id, name, balance, customer_type, deleted_at, uuid) VALUES (3, 'Ancien client', 0, 'retail', 1789600000000, ?)", uuid())
        insert("INSERT INTO suppliers (id, name, balance, initial_balance, created_at, uuid) VALUES (1, 'Laiterie Soummam', 0, 0, '2026-09-01T08:00:00Z', ?)", uuid())
        insert(
            "INSERT INTO products (id, name, barcode, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, has_expiry, expiry_date, camion_stock, category_name, uuid) " +
                "VALUES (1, 'Lait Candia 1L', '0613000000017', 120, 100, 0, 10, 'pièce', 0, 12, 1, '2026-12-31', 0, 'Laitiers', ?)", uuid()
        )
        insert("INSERT INTO products (id, name, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, has_expiry, camion_stock, deleted_at, uuid) VALUES (2, 'Produit supprimé', 1, 1, 0, 0, 'pièce', 0, 1, 0, 0, 1789600000000, ?)", uuid())

        // Sales: 00:30 on the 17th (UTC the 16th), 23:30 on the 17th, 00:10 on the 18th, and an old one numbered 26.
        insert("INSERT INTO ventes (id, client_id, client_name, source, total, montant_paye, status, created_at, uuid) VALUES (10, 1, 'Épicerie El Amel', 'depot', 3450, 1000, 'pending', '2026-09-16T23:30:00Z', ?)", uuid())
        insert("INSERT INTO ventes (id, client_id, client_name, source, total, montant_paye, status, created_at, note, uuid) VALUES (11, 2, '=HYPERLINK(\"http://x\")', 'camion', 800, 800, 'delivered', '2026-09-17T22:30:00Z', 'Livré; payé', ?)", uuid())
        insert("INSERT INTO ventes (id, client_id, client_name, source, total, montant_paye, status, created_at, uuid) VALUES (12, 1, 'Épicerie El Amel', 'depot', 50, 0, 'pending', '2026-09-17T23:10:00Z', ?)", uuid())
        insert("INSERT INTO ventes (id, client_id, client_name, source, total, montant_paye, status, created_at, numero, uuid) VALUES (13, 1, 'Épicerie El Amel', 'depot', 10, 10, 'delivered', '2026-09-17T12:00:00Z', '26', ?)", uuid())
        insert("INSERT INTO vente_items (vente_id, product_id, product_name, unit_type, quantity, unit_price, total_price, uuid) VALUES (10, 1, 'Lait Candia 1L', 'pièce', 12.5, 120, 1500, ?)", uuid())
        insert("INSERT INTO vente_items (vente_id, product_id, product_name, unit_type, quantity, unit_price, total_price, uuid) VALUES (12, 1, 'Lait Candia 1L', 'pièce', 1, 50, 50, ?)", uuid())

        // Purchases: one dated by its creation instant, an older one by its calendar date only.
        insert("INSERT INTO purchase_orders (id, supplier_id, supplier_name, date, total, montant_paye, status, created_at, uuid) VALUES (20, 1, 'Laiterie Soummam', '2026-09-17', 12000, 5000, 'received', '2026-09-17T08:00:00Z', ?)", uuid())
        insert("INSERT INTO purchase_orders (id, supplier_id, supplier_name, date, total, montant_paye, status, created_at, uuid) VALUES (21, 1, 'Laiterie Soummam', '2026-09-17', 700, 0, 'pending', '', ?)", uuid())
        insert("INSERT INTO purchase_orders (id, supplier_id, supplier_name, date, total, montant_paye, status, created_at, uuid) VALUES (22, 1, 'Laiterie Soummam', '2026-09-18', 1, 0, 'pending', '', ?)", uuid())

        insert("INSERT INTO client_payments (client_id, amount, note, created_at, uuid) VALUES (1, 500, 'Espèces', '2026-09-17T10:00:00Z', ?)", uuid())
        insert("INSERT INTO client_payments (client_id, amount, created_at, uuid) VALUES (3, 90, '2026-09-17T11:00:00Z', ?)", uuid())
        insert("INSERT INTO supplier_payments (supplier_id, amount, created_at, uuid) VALUES (1, 5000, '2026-09-17T09:00:00Z', ?)", uuid())
        insert(
            "INSERT INTO stock_movements (product_id, product_name, type, direction, quantity, emplacement, source_label, source_type, source_id, unit_price, total_value, created_at, uuid) " +
                "VALUES (1, 'Lait Candia 1L', 'retour_client', 'entree', 2.25, 'camion', 'Retour client #3', 'retour_client', 3, 100, 225, '2026-09-17T15:00:00Z', ?)", uuid()
        )
        insert(
            "INSERT INTO stock_movements (product_id, product_name, type, direction, quantity, emplacement, source_label, source_type, source_id, unit_price, total_value, created_at, uuid) " +
                "VALUES (1, 'Lait Candia 1L', 'achat', 'entree', 48, 'depot', 'Bon #1', 'purchase_order', 20, 100, 4800, '2026-09-18T08:00:00Z', ?)", uuid()
        )
        insert("INSERT INTO charges (type_id, type_name, subtype_id, subtype_name, montant, date_time, fournisseur, created_at, uuid) VALUES (1, 'Véhicule', 1, 'Carburant', 2500, '2026-09-16T23:15:00Z', 'Naftal', '2026-09-16T23:15:00Z', ?)", uuid())
        insert(
            "INSERT INTO pertes (type_id, type_name, product_id, product_name, quantity, unit, source, purchase_price_snapshot, valeur_totale, date_time, motif, created_at, uuid) " +
                "VALUES (1, 'Casse', 1, 'Lait Candia 1L', 3, 'pièce', 'depot', 100, 300, '2026-09-17T09:30:00Z', '-cassé au chargement', '2026-09-17T09:30:00Z', ?)", uuid()
        )
    }

    private fun export(dataset: ExportDataset, period: ExportPeriod = the17th): List<List<String>> {
        val out = ByteArrayOutputStream()
        val count = exporter.export(listOf(dataset), period, ExportFormat.CSV, out).getValue(dataset)
        val bytes = out.toByteArray()
        assertTrue("$dataset starts with the byte-order mark", bytes.toString(Charsets.UTF_8).startsWith(CsvWriter.BOM))
        return CsvReader.parse(bytes).also { assertEquals("$dataset reports its rows", it.size - 1, count) }
    }

    @Test
    fun ventesAreTheLocalDaysSalesWithTheirNumbersAndLabels() {
        val rows = export(ExportDataset.VENTES)
        assertEquals(listOf("N°", "Date", "Client", "Origine", "Statut", "Total (DA)", "Payé (DA)", "Reste (DA)", "Note", "Vendeur"), rows[0])
        assertEquals(listOf("V-6DED-000001", "17/09/2026 00:30", "Épicerie El Amel", "Dépôt", "En attente", "3450,00", "1000,00", "2450,00", "", ""), rows[1])
        assertEquals(listOf("#26", "17/09/2026 13:00"), rows[2].take(2))
        assertEquals(listOf("V-6DED-000002", "17/09/2026 23:30", "'=HYPERLINK(\"http://x\")", "Camion", "Livré", "800,00", "800,00", "0,00", "Livré; payé", ""), rows[3])
        assertEquals("00:10 on the 18th is not in the 17th", 4, rows.size)
    }

    @Test
    fun saleLinesFollowTheirSalesDate() {
        val rows = export(ExportDataset.LIGNES_VENTE)
        assertEquals(listOf("N° vente", "Date", "Client", "Produit", "Unité", "Quantité", "Prix unitaire (DA)", "Total (DA)"), rows[0])
        assertEquals(listOf("V-6DED-000001", "17/09/2026 00:30", "Épicerie El Amel", "Lait Candia 1L", "pièce", "12,5", "120,00", "1500,00"), rows[1])
        assertEquals("the line of the sale on the 18th is left out", 2, rows.size)
    }

    @Test
    fun achatsAreDatedByTheirInstantOrTheirCalendarDate() {
        val rows = export(ExportDataset.ACHATS)
        assertEquals(listOf("N°", "Date", "Fournisseur", "Statut", "Total (DA)", "Payé (DA)", "Reste (DA)", "Note"), rows[0])
        val byNumber = rows.drop(1).associateBy { it[0] }
        assertEquals(2, byNumber.size)
        assertEquals(listOf("17/09/2026 09:00", "Laiterie Soummam", "Reçu", "12000,00", "5000,00", "7000,00", ""), byNumber.getValue("BA-6DED-000001").drop(1))
        assertEquals(listOf("17/09/2026", "Laiterie Soummam", "En attente", "700,00", "0,00", "700,00", ""), byNumber.getValue("BA-6DED-000002").drop(1))
    }

    @Test
    fun paymentsNameTheirClientOrSupplierEvenOneInTheBin() {
        assertEquals(
            listOf(
                listOf("Date", "Client", "Montant (DA)", "Note"),
                listOf("17/09/2026 11:00", "Épicerie El Amel", "500,00", "Espèces"),
                listOf("17/09/2026 12:00", "Ancien client", "90,00", ""),
            ),
            export(ExportDataset.PAIEMENTS_CLIENTS)
        )
        assertEquals(
            listOf(listOf("Date", "Fournisseur", "Montant (DA)", "Note"), listOf("17/09/2026 10:00", "Laiterie Soummam", "5000,00", "")),
            export(ExportDataset.PAIEMENTS_FOURNISSEURS)
        )
    }

    @Test
    fun clientsAndProductsAreAsTheyAreNowWithoutTheBin() {
        val clients = export(ExportDataset.CLIENTS, ExportPeriod.ALL)
        assertEquals(listOf("Nom", "Téléphone", "Type", "Secteur", "Wilaya", "Commune", "Adresse", "Solde (DA)", "Note"), clients[0])
        assertEquals(
            listOf(
                listOf("'=HYPERLINK(\"http://x\")", "", "Gros", "", "", "", "", "-300,00", ""),
                listOf("Épicerie El Amel", "0555123456", "Détail", "Centre", "Souk Ahras", "", "", "2450,50", ""),
            ),
            clients.drop(1)
        )

        val products = export(ExportDataset.PRODUITS, ExportPeriod.ALL)
        assertEquals(2, products.size)
        assertEquals(listOf("Stock total", "Stock dépôt", "Stock camion"), products[0].subList(10, 13))
        assertEquals(
            listOf("Lait Candia 1L", "0613000000017", "Laitiers", "", "", "", "pièce", "12", "100,00", "120,00", "50,25", "48", "2,25", "10", "31/12/2026"),
            products[1]
        )
    }

    @Test
    fun movementsChargesAndPertesUseTheAppsWords() {
        assertEquals(
            listOf("17/09/2026 16:00", "Lait Candia 1L", "Retour client", "Entrée", "Camion", "2,25", "100,00", "225,00", "Retour client #3", "", ""),
            export(ExportDataset.MOUVEMENTS_STOCK)[1]
        )
        assertEquals(
            listOf(listOf("Date", "Type", "Sous-type", "Montant (DA)", "Fournisseur", "Note"), listOf("17/09/2026 00:15", "Véhicule", "Carburant", "2500,00", "Naftal", "")),
            export(ExportDataset.CHARGES)
        )
        assertEquals(
            listOf("17/09/2026 10:30", "Casse", "Lait Candia 1L", "3", "pièce", "Dépôt", "300,00", "'-cassé au chargement"),
            export(ExportDataset.PERTES)[1]
        )
    }

    @Test
    fun noPeriodExportsEverythingAndAnOpenEndedPeriodEverythingFrom() {
        assertEquals(5, export(ExportDataset.VENTES, ExportPeriod.ALL).size)
        assertEquals("from the 18th on", 2, export(ExportDataset.VENTES, ExportPeriod(LocalDate.of(2026, 9, 18), null)).size)
        assertEquals("until the 16th", 1, export(ExportDataset.VENTES, ExportPeriod(null, LocalDate.of(2026, 9, 16))).size)
        assertEquals(4, export(ExportDataset.ACHATS, ExportPeriod.ALL).size)
    }

    @Test
    fun severalDatasetsGoInOneZipOfCsvFiles() {
        val out = ByteArrayOutputStream()
        val counts = exporter.export(listOf(ExportDataset.VENTES, ExportDataset.CLIENTS, ExportDataset.VENTES), the17th, ExportFormat.CSV, out)

        assertEquals(mapOf(ExportDataset.VENTES to 3, ExportDataset.CLIENTS to 2), counts)
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        assertEquals(setOf("ventes.csv", "clients.csv"), entries.keys)
        assertEquals(4, CsvReader.parse(entries.getValue("ventes.csv")).size)
        assertTrue(entries.getValue("clients.csv").toString(Charsets.UTF_8).startsWith(CsvWriter.BOM))
    }

    @Test
    fun everyDatasetExportsFromAnEmptyDatabaseWithJustItsHeader() {
        val empty = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        try {
            for (dataset in ExportDataset.entries) {
                val out = ByteArrayOutputStream()
                assertEquals(dataset.name, 0, DataExporter(empty.openHelper.writableDatabase, algiers).export(listOf(dataset), ExportPeriod.ALL, ExportFormat.CSV, out).getValue(dataset))
                assertEquals(dataset.name, 1, CsvReader.parse(out.toByteArray()).size)
            }
        } finally {
            empty.close()
        }
    }

    // ── Excel workbooks ──

    /** The parts of a workbook the exporter writes, by name, with the rows each dataset contributed. */
    private fun workbook(datasets: List<ExportDataset>, period: ExportPeriod = the17th): Pair<Map<String, String>, Map<ExportDataset, Int>> {
        val out = ByteArrayOutputStream()
        val counts = exporter.export(datasets, period, ExportFormat.XLSX, out)
        val parts = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return parts to counts
    }

    @Test
    fun anExcelWorkbookHasOneSheetPerDatasetWithItsRows() {
        val (parts, counts) = workbook(listOf(ExportDataset.VENTES, ExportDataset.CLIENTS))

        assertEquals(mapOf(ExportDataset.VENTES to 3, ExportDataset.CLIENTS to 2), counts)
        assertTrue(parts.keys.containsAll(setOf("xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml")))
        assertTrue(parts.getValue("xl/workbook.xml").contains("name=\"Ventes\""))
        assertTrue(parts.getValue("xl/workbook.xml").contains("name=\"Clients\""))
        assertEquals("the header and one row per sale", 4, Regex("<row ").findAll(parts.getValue("xl/worksheets/sheet1.xml")).count())
    }

    /** What CSV cannot do: a total stays a number, a date a date, and a name that looks like a formula stays text. */
    @Test
    fun aWorkbookKeepsTypesAndNeedsNoNeutralising() {
        val sheet = workbook(listOf(ExportDataset.VENTES)).first.getValue("xl/worksheets/sheet1.xml")

        assertTrue("the total is a number", sheet.contains("<v>3450</v>"))
        assertTrue(
            "the client that looks like a formula is text, with no apostrophe",
            sheet.contains("<t xml:space=\"preserve\">=HYPERLINK(&quot;http://x&quot;)</t>")
        )
        assertFalse(sheet.contains("&apos;=HYPERLINK"))
        // 00:30 on the 17th in Algeria, as a day count with a fraction of a day.
        val serial = java.time.LocalDate.of(2026, 9, 17).toEpochDay() + 25569
        assertTrue("the date is a date", sheet.contains("<v>$serial.02"))
    }
}

/** Reads CSV as the writer writes it, to check exports as a spreadsheet would read them. */
object CsvReader {
    fun parse(bytes: ByteArray): List<List<String>> {
        val text = bytes.toString(Charsets.UTF_8).removePrefix(CsvWriter.BOM).removePrefix(CsvWriter.SEPARATOR_HINT + CsvWriter.LINE_END)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ';' -> { row.add(field.toString()); field.clear() }
                !quoted && c == '\r' && text.getOrNull(i + 1) == '\n' -> {
                    row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf(); i++
                }
                else -> field.append(c)
            }
            i++
        }
        return rows
    }
}
