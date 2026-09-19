package com.distrigo.app.data.export

import android.database.Cursor
import com.distrigo.app.data.model.NUMBER_LABEL_SQL
import com.distrigo.app.data.time.BusinessDates
import java.time.LocalDate
import java.time.ZoneId

/** A row of an export query, read by column name. Null for SQL NULL. */
class ExportRow(private val cursor: Cursor) {
    private fun index(name: String) = cursor.getColumnIndexOrThrow(name)
    fun text(name: String): String? = index(name).let { if (cursor.isNull(it)) null else cursor.getString(it) }
    fun double(name: String): Double? = index(name).let { if (cursor.isNull(it)) null else cursor.getDouble(it) }
    fun long(name: String): Long? = index(name).let { if (cursor.isNull(it)) null else cursor.getLong(it) }
}

/** Local days, both included; a null end leaves that side open. */
data class ExportPeriod(val from: LocalDate?, val to: LocalDate?) {
    init {
        require(from == null || to == null || !from.isAfter(to)) { "a period cannot end before it starts" }
    }

    companion object {
        val ALL = ExportPeriod(null, null)
    }
}

/**
 * What can be exported, one CSV each: what a distributor's accountant or spreadsheet needs, in the words the
 * app's screens use.
 *
 * Documents, payments and movements are chosen by [ExportPeriod] over the local days they happened on (see
 * `docs/data/timestamps.md`); clients and products are exported as they are now. Rows moved to the bin are
 * left out. Document numbers read as the app shows them: `V-6DED-000124`, or `#26` for older documents.
 */
enum class ExportDataset(val fileName: String, val label: String, val byPeriod: Boolean) {
    VENTES("ventes", "Ventes", true),
    LIGNES_VENTE("lignes-de-vente", "Lignes de vente", true),
    ACHATS("achats", "Achats", true),
    PAIEMENTS_CLIENTS("paiements-clients", "Paiements clients", true),
    PAIEMENTS_FOURNISSEURS("paiements-fournisseurs", "Paiements fournisseurs", true),
    CLIENTS("clients", "Clients", false),
    PRODUITS("produits", "Produits", false),
    MOUVEMENTS_STOCK("mouvements-de-stock", "Mouvements de stock", true),
    CHARGES("charges", "Charges", true),
    PERTES("pertes", "Pertes", true);

    /** The SQL and its arguments for [period], rows in the order they are written. */
    internal fun query(period: ExportPeriod, zone: ZoneId): Pair<String, Array<Any?>> {
        val (start, end) = BusinessDates.dayRangeBounds(period.from?.toString(), period.to?.toString(), zone)
        val args = mutableListOf<Any?>()

        /** `AND` conditions keeping instants in [column] within the period. */
        fun instantsIn(column: String): String = buildString {
            start?.let { append(" AND $column >= ?"); args += it }
            end?.let { append(" AND $column < ?"); args += it }
        }

        val sql = when (this) {
            VENTES ->
                "SELECT $NUMBER_LABEL_SQL AS numero_label, * FROM ventes WHERE 1" + instantsIn("created_at") + " ORDER BY created_at, id"
            LIGNES_VENTE ->
                // The number is read in a subquery over ventes alone, where NUMBER_LABEL_SQL's columns are unambiguous.
                "SELECT (SELECT $NUMBER_LABEL_SQL FROM ventes WHERE ventes.id = i.vente_id) AS numero_label, " +
                    "v.created_at AS vente_created_at, v.client_name AS client_name, i.* FROM vente_items i JOIN ventes v ON v.id = i.vente_id " +
                    "WHERE 1" + instantsIn("v.created_at") + " ORDER BY v.created_at, v.id, i.id"
            ACHATS -> {
                // A bon is dated by the instant it was created, or by its calendar date when it has none.
                val datedByInstant = "COALESCE(created_at, '') GLOB '????-??-??T*'"
                val condition = buildString {
                    append(" AND (($datedByInstant")
                    start?.let { append(" AND created_at >= ?"); args += it }
                    end?.let { append(" AND created_at < ?"); args += it }
                    append(") OR (NOT ($datedByInstant)")
                    period.from?.let { append(" AND date >= ?"); args += it.toString() }
                    period.to?.let { append(" AND date <= ?"); args += it.toString() }
                    append("))")
                }
                "SELECT $NUMBER_LABEL_SQL AS numero_label, CASE WHEN $datedByInstant THEN created_at ELSE date END AS dated_at, * " +
                    "FROM purchase_orders WHERE 1" + condition + " ORDER BY dated_at, id"
            }
            PAIEMENTS_CLIENTS ->
                "SELECT p.*, c.name AS client_name FROM client_payments p LEFT JOIN clients c ON c.id = p.client_id " +
                    "WHERE 1" + instantsIn("p.created_at") + " ORDER BY p.created_at, p.id"
            PAIEMENTS_FOURNISSEURS ->
                "SELECT p.*, s.name AS supplier_name FROM supplier_payments p LEFT JOIN suppliers s ON s.id = p.supplier_id " +
                    "WHERE 1" + instantsIn("p.created_at") + " ORDER BY p.created_at, p.id"
            CLIENTS -> "SELECT * FROM clients WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE, id"
            // Every code in one cell, the primary first, separated as the import reads them back.
            PRODUITS ->
                "SELECT p.*, COALESCE((SELECT group_concat(code, '; ') FROM " +
                    "(SELECT b.code FROM product_barcodes b WHERE b.product_id = p.id ORDER BY b.position)), p.barcode) AS all_barcodes " +
                    "FROM products p WHERE p.deleted_at IS NULL ORDER BY p.name COLLATE NOCASE, p.id"
            MOUVEMENTS_STOCK -> "SELECT * FROM stock_movements WHERE 1" + instantsIn("created_at") + " ORDER BY created_at, id"
            CHARGES -> "SELECT * FROM charges WHERE 1" + instantsIn("date_time") + " ORDER BY date_time, id"
            PERTES -> "SELECT * FROM pertes WHERE 1" + instantsIn("date_time") + " ORDER BY date_time, id"
        }
        return sql to args.toTypedArray()
    }

    internal val columns: List<ExportColumn<ExportRow>>
        get() = when (this) {
            VENTES -> listOf(
                text("N°", "numero_label"),
                dateTime("Date", "created_at"),
                text("Client", "client_name"),
                label("Origine", "source", LOCATIONS),
                label("Statut", "status", mapOf("pending" to "En attente", "delivered" to "Livré")),
                amount("Total (DA)", "total"),
                amount("Payé (DA)", "montant_paye"),
                ExportColumn("Reste (DA)") { ExportCell.Number(remaining(it.double("total"), it.double("montant_paye"))) },
                text("Note", "note"),
                text("Vendeur", "user_name"),
            )
            LIGNES_VENTE -> listOf(
                text("N° vente", "numero_label"),
                dateTime("Date", "vente_created_at"),
                text("Client", "client_name"),
                text("Produit", "product_name"),
                text("Unité", "unit_type"),
                quantity("Quantité", "quantity"),
                amount("Prix unitaire (DA)", "unit_price"),
                amount("Total (DA)", "total_price"),
            )
            ACHATS -> listOf(
                text("N°", "numero_label"),
                dateTime("Date", "dated_at"),
                text("Fournisseur", "supplier_name"),
                label("Statut", "status", mapOf("pending" to "En attente", "received" to "Reçu")),
                amount("Total (DA)", "total"),
                amount("Payé (DA)", "montant_paye"),
                ExportColumn("Reste (DA)") { ExportCell.Number(remaining(it.double("total"), it.double("montant_paye"))) },
                text("Note", "note"),
            )
            PAIEMENTS_CLIENTS -> listOf(
                dateTime("Date", "created_at"),
                text("Client", "client_name"),
                amount("Montant (DA)", "amount"),
                text("Note", "note"),
            )
            PAIEMENTS_FOURNISSEURS -> listOf(
                dateTime("Date", "created_at"),
                text("Fournisseur", "supplier_name"),
                amount("Montant (DA)", "amount"),
                text("Note", "note"),
            )
            CLIENTS -> listOf(
                text("Nom", "name"),
                text("Téléphone", "phone"),
                label("Type", "customer_type", mapOf("retail" to "Détail", "wholesale" to "Gros", "business" to "Société")),
                text("Secteur", "secteur_name"),
                text("Wilaya", "wilaya_name"),
                text("Commune", "commune_name"),
                text("Adresse", "address"),
                amount("Solde (DA)", "balance"),
                text("Note", "note"),
            )
            PRODUITS -> listOf(
                text("Nom", "name"),
                text("Code-barres", "all_barcodes"),
                text("Catégorie", "category_name"),
                text("Sous-catégorie", "sous_categorie_name"),
                text("Marque", "marque_name"),
                text("Fournisseur", "supplier_name"),
                text("Unité", "unit_type"),
                ExportColumn("Unités par colis") { ExportCell.Integer(it.long("pack_size")) },
                amount("Prix d'achat (DA)", "purchase_price"),
                amount("Prix de vente (DA)", "selling_price"),
                // `stock` is the total, dépôt and camion together (see StockLedgerTriggers); the dépôt's share is
                // the rest once the camion's is taken out, as the product screen shows it.
                quantity("Stock total", "stock"),
                ExportColumn("Stock dépôt") { row ->
                    val total = row.double("stock")
                    ExportCell.Number(total?.let { it - (row.double("camion_stock") ?: 0.0) }, decimals = 3, trimZeros = true)
                },
                quantity("Stock camion", "camion_stock"),
                ExportColumn("Stock minimum") { ExportCell.Integer(it.long("min_stock")) },
                ExportColumn("Date de péremption") { row -> ExportCell.Date(row.text("expiry_date")?.takeIf { row.long("has_expiry") == 1L }) },
            )
            MOUVEMENTS_STOCK -> listOf(
                dateTime("Date", "created_at"),
                text("Produit", "product_name"),
                label("Type", "type", MOVEMENT_TYPES),
                label("Sens", "direction", mapOf("entree" to "Entrée", "sortie" to "Sortie")),
                label("Emplacement", "emplacement", LOCATIONS),
                quantity("Quantité", "quantity"),
                amount("Prix unitaire (DA)", "unit_price"),
                amount("Valeur (DA)", "total_value"),
                text("Origine", "source_label"),
                text("Note", "note"),
                text("Utilisateur", "user_name"),
            )
            CHARGES -> listOf(
                dateTime("Date", "date_time"),
                text("Type", "type_name"),
                text("Sous-type", "subtype_name"),
                amount("Montant (DA)", "montant"),
                text("Fournisseur", "fournisseur"),
                text("Note", "note"),
            )
            PERTES -> listOf(
                dateTime("Date", "date_time"),
                text("Type", "type_name"),
                text("Produit", "product_name"),
                quantity("Quantité", "quantity"),
                text("Unité", "unit"),
                label("Emplacement", "source", LOCATIONS),
                amount("Valeur (DA)", "valeur_totale"),
                text("Motif", "motif"),
            )
        }

    private companion object {
        val LOCATIONS = mapOf("depot" to "Dépôt", "camion" to "Camion")
        val MOVEMENT_TYPES = mapOf(
            "achat" to "Achat", "vente" to "Vente", "chargement" to "Chargement", "perte" to "Perte",
            "ajustement" to "Ajustement", "retour_client" to "Retour client", "retour_fournisseur" to "Retour fournisseur",
        )

        fun text(header: String, column: String) = ExportColumn<ExportRow>(header) { ExportCell.Text(it.text(column)) }
        fun amount(header: String, column: String) = ExportColumn<ExportRow>(header) { ExportCell.Number(it.double(column)) }
        fun quantity(header: String, column: String) =
            ExportColumn<ExportRow>(header) { ExportCell.Number(it.double(column), decimals = 3, trimZeros = true) }
        fun dateTime(header: String, column: String) = ExportColumn<ExportRow>(header) { ExportCell.DateTime(it.text(column)) }

        /** A stored code as the screens word it; a code the app does not know is written as it is. */
        fun label(header: String, column: String, labels: Map<String, String>) =
            ExportColumn<ExportRow>(header) { row -> ExportCell.Text(row.text(column)?.let { labels[it] ?: it }) }

        fun remaining(total: Double?, paid: Double?): Double? = total?.let { it - (paid ?: 0.0) }
    }
}
