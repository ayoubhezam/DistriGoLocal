package com.distrigo.app.data.importer

import com.distrigo.app.data.local.entity.ClientEntity
import com.distrigo.app.data.local.entity.ProductEntity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Decides what an import would do, row by row, without writing anything.
 *
 * The rules are the forms': a name is required, a product's barcode is generated when blank, and a row that
 * would duplicate a name or barcode is refused. A blank cell means "leave it" for an existing row and "the
 * default" for a new one, so a file with only the columns to change is enough — a name and a new price.
 * Categories, sub-categories, brands, suppliers and secteurs the file names and the app lacks are created;
 * a wilaya or a commune must be one the app knows, as the client form only offers those.
 *
 * Stock is not imported into an existing product: it comes from movements, and is corrected with an
 * inventory or an adjustment. A new product takes its stock as an opening adjustment, as the form does.
 */
class ImportPlanner(private val snapshot: ImportSnapshot, private val geo: ImportGeo) {

    fun plan(workbook: XlsxWorkbook): ImportPlan {
        val notes = mutableListOf<String>()
        val lookups = Lookups()
        val sheets = ImportSheet.entries.mapNotNull { sheet ->
            val found = workbook.sheets.firstOrNull { ImportSheet.named(it.name) == sheet } ?: return@mapNotNull null
            when (sheet) {
                ImportSheet.PRODUITS -> planProducts(found, lookups, notes)
                ImportSheet.CLIENTS -> planClients(found, lookups, notes)
            }
        }
        if (sheets.isEmpty()) {
            throw ImportPlanException(
                "Le fichier n'a pas de feuille « Produits » ni « Clients ». Exportez d'abord les données pour obtenir le modèle."
            )
        }
        return ImportPlan(sheets, lookups.toCreate(), notes.distinct())
    }

    // ── Produits ──

    private fun planProducts(sheet: XlsxSheet, lookups: Lookups, notes: MutableList<String>): SheetPlan {
        val columns = Columns(sheet, PRODUCT_HEADERS)
        if (columns.missing("nom")) return SheetPlan(ImportSheet.PRODUITS, columns.ignored, listOf(noHeader(ImportSheet.PRODUITS, sheet)))
        if (columns.has("stock", "stock depot", "stock camion")) {
            notes += "Le stock d'un produit existant n'est pas modifié par l'import : corrigez-le par un inventaire ou un ajustement."
        }

        val byBarcode = snapshot.products.filter { it.barcode != null }.groupBy { it.barcode!!.trim() }
        val byName = snapshot.products.groupBy { ImportText.key(it.name) }
        val seen = mutableSetOf<String>()
        val seenBarcodes = mutableSetOf<String>()

        val rows = columns.dataRows.map { row ->
            val cells = columns.cells(row)
            outcome { planProduct(cells, byBarcode, byName, seen, seenBarcodes, lookups) }
                .let { PlannedRow(ImportSheet.PRODUITS, row.number, cells.text("nom") ?: "(sans nom)", it) }
        }
        return SheetPlan(ImportSheet.PRODUITS, columns.ignored, rows)
    }

    private fun planProduct(
        cells: Cells,
        byBarcode: Map<String, List<ProductEntity>>,
        byName: Map<String, List<ProductEntity>>,
        seen: MutableSet<String>,
        seenBarcodes: MutableSet<String>,
        lookups: Lookups,
    ): RowOutcome {
        val name = cells.text("nom") ?: return RowOutcome.Refused("Le nom est obligatoire.")
        val barcode = cells.text("code barres")
        val nameKey = ImportText.key(name)

        // Which product this row is about: the one with its barcode, else the one with its name.
        val existing = barcode?.let { byBarcode[it]?.singleOrNull() } ?: byName[nameKey]?.let { same ->
            if (same.size > 1) return RowOutcome.Refused("Plusieurs produits portent le nom « $name » : précisez le code-barres.")
            same.singleOrNull()
        }
        if (barcode != null && byBarcode[barcode].let { it != null && it.size > 1 }) {
            return RowOutcome.Refused("Plusieurs produits portent le code-barres $barcode.")
        }
        // A name that differs only by case or accents is the same name, kept as the app spells it.
        val renamed = existing != null && ImportText.key(existing.name) != nameKey
        val keptName = if (existing != null && !renamed) existing.name else name
        // The name or the barcode this row would take must not be another product's.
        if (renamed || existing == null) {
            byName[nameKey]?.firstOrNull { it.id != existing?.id }?.let { return RowOutcome.Refused("Ce nom de produit est déjà enregistré (code-barres ${it.barcode ?: "—"}).") }
        }
        barcode?.let { code -> byBarcode[code]?.firstOrNull { it.id != existing?.id }?.let { return RowOutcome.Refused("Ce code-barres est déjà enregistré (« ${it.name} »).") } }
        // The same product twice in one file would apply twice; only the first row that passes counts.
        if (!seen.add(existing?.id?.toString() ?: "new:$nameKey")) return RowOutcome.Refused("« $name » est déjà sur une ligne plus haut.")
        if (barcode != null && existing == null && !seenBarcodes.add(barcode)) return RowOutcome.Refused("Le code-barres $barcode est déjà sur une ligne plus haut.")

        val unitType = cells.text("unite")?.let { UNITS[ImportText.key(it)] ?: return RowOutcome.Refused("L'unité « $it » n'existe pas : carton ou pièce.") }
        val packSize = cells.int("unites par colis")?.let { if (it < 0) return RowOutcome.Refused("Les unités par colis ne peuvent pas être négatives.") else it }
        val purchase = cells.amount("prix d achat") ?: cells.amount("prix achat")
        val selling = cells.amount("prix de vente") ?: cells.amount("prix vente")
        if (purchase != null && purchase < 0) return RowOutcome.Refused("Le prix d'achat ne peut pas être négatif.")
        if (selling != null && selling < 0) return RowOutcome.Refused("Le prix de vente ne peut pas être négatif.")
        val minStock = cells.int("stock minimum")?.let { if (it < 0) return RowOutcome.Refused("Le stock minimum ne peut pas être négatif.") else it }
        val stock = cells.quantity("stock depot") ?: cells.quantity("stock")
        if (stock != null && stock < 0) return RowOutcome.Refused("Le stock ne peut pas être négatif.")
        val expiry = cells.date("date de peremption")

        val category = cells.text("categorie")
        val sousCategorie = cells.text("sous categorie")
        val marque = cells.text("marque")
        val supplier = cells.text("fournisseur")
        // A sub-category lives under a category: the row's, or the product's own when the row leaves it.
        val categoryForSub = category ?: existing?.category_name
        if (sousCategorie != null && categoryForSub == null) return RowOutcome.Refused("La sous-catégorie « $sousCategorie » a besoin d'une catégorie.")

        val values = ProductValues(
            name = keptName, barcode = barcode, category = category, sousCategorie = sousCategorie, marque = marque,
            supplier = supplier, unitType = unitType, packSize = packSize, purchasePrice = purchase, sellingPrice = selling,
            initialStock = stock, minStock = minStock, expiry = expiry,
        )
        category?.let { lookups.category(it) }
        sousCategorie?.let { lookups.sousCategorie(categoryForSub!!, it) }
        marque?.let { lookups.marque(it) }
        supplier?.let { lookups.supplier(it) }

        if (existing == null) return RowOutcome.CreateProduct(values)

        val changes = mutableListOf<Change>()
        fun changed(label: String, from: String?, to: String?) {
            if (to != null && (from ?: "") != to) changes += Change(label, from, to)
        }
        fun changedName(label: String, from: String?, to: String?) {
            if (to != null && ImportText.key(from ?: "") != ImportText.key(to)) changes += Change(label, from, to)
        }
        if (renamed) changes += Change("Nom", existing.name, name)
        changed("Code-barres", existing.barcode, barcode)
        changedName("Catégorie", existing.category_name, category)
        changedName("Sous-catégorie", existing.sous_categorie_name, sousCategorie)
        changedName("Marque", existing.marque_name, marque)
        changedName("Fournisseur", existing.supplier_name, supplier)
        changed("Unité", existing.unit_type, unitType)
        changed("Unités par colis", existing.pack_size.toString(), packSize?.toString())
        changed("Prix d'achat", money(existing.purchase_price), purchase?.let(::money))
        changed("Prix de vente", money(existing.selling_price), selling?.let(::money))
        changed("Stock minimum", existing.min_stock.toString(), minStock?.toString())
        changed("Date de péremption", existing.expiry_date?.takeIf { existing.has_expiry == 1 }?.let(::day), expiry?.let { day(it.toString()) })
        return if (changes.isEmpty()) RowOutcome.Unchanged(existing.id) else RowOutcome.UpdateProduct(existing.id, values.copy(initialStock = null), changes)
    }

    // ── Clients ──

    private fun planClients(sheet: XlsxSheet, lookups: Lookups, notes: MutableList<String>): SheetPlan {
        val columns = Columns(sheet, CLIENT_HEADERS)
        if (columns.missing("nom")) return SheetPlan(ImportSheet.CLIENTS, columns.ignored, listOf(noHeader(ImportSheet.CLIENTS, sheet)))
        if (columns.has("solde")) notes += "Le solde d'un client vient de ses ventes et de ses paiements : l'import ne le modifie pas."

        val byName = snapshot.clients.groupBy { ImportText.key(it.name) }
        val seen = mutableSetOf<String>()
        val rows = columns.dataRows.map { row ->
            val cells = columns.cells(row)
            outcome { planClient(cells, byName, seen, lookups) }.let { PlannedRow(ImportSheet.CLIENTS, row.number, cells.text("nom") ?: "(sans nom)", it) }
        }
        return SheetPlan(ImportSheet.CLIENTS, columns.ignored, rows)
    }

    private fun planClient(cells: Cells, byName: Map<String, List<ClientEntity>>, seen: MutableSet<String>, lookups: Lookups): RowOutcome {
        val name = cells.text("nom") ?: return RowOutcome.Refused("Le nom est obligatoire.")
        val key = ImportText.key(name)
        val same = byName[key].orEmpty()
        if (same.size > 1) return RowOutcome.Refused("Plusieurs clients portent le nom « $name » : modifiez-les dans l'application.")
        if (!seen.add(key)) return RowOutcome.Refused("« $name » est déjà sur une ligne plus haut.")
        val existing = same.singleOrNull()

        val type = cells.text("type")?.let { CLIENT_TYPES[ImportText.key(it)] ?: return RowOutcome.Refused("Le type « $it » n'existe pas : Détail, Gros ou Société.") }
        val wilaya = cells.text("wilaya")?.let { geo.wilaya(it) ?: return RowOutcome.Refused("La wilaya « $it » n'existe pas.") }
        val wilayaForCommune = wilaya ?: existing?.wilaya_name
        val commune = cells.text("commune")?.let { typed ->
            if (wilayaForCommune == null) return RowOutcome.Refused("La commune « $typed » a besoin d'une wilaya.")
            geo.commune(wilayaForCommune, typed) ?: return RowOutcome.Refused("La commune « $typed » n'est pas dans la wilaya de $wilayaForCommune.")
        }
        val communeForSecteur = commune ?: existing?.commune_name?.takeIf { wilaya == null || ImportText.key(existing.wilaya_name ?: "") == ImportText.key(wilaya) }
        val secteur = cells.text("secteur")?.let { typed ->
            if (communeForSecteur == null) return RowOutcome.Refused("Le secteur « $typed » a besoin d'une commune.")
            typed
        }

        val values = ClientValues(
            name = existing?.name ?: name, phone = cells.text("telephone"), customerType = type, secteur = secteur, wilaya = wilaya, commune = commune,
            address = cells.text("adresse"), note = cells.text("note"),
        )
        secteur?.let { lookups.secteur(communeForSecteur!!, wilayaForCommune, it) }

        if (existing == null) return RowOutcome.CreateClient(values)

        val changes = mutableListOf<Change>()
        fun changed(label: String, from: String?, to: String?) {
            if (to != null && (from ?: "") != to) changes += Change(label, from, to)
        }
        fun changedName(label: String, from: String?, to: String?) {
            if (to != null && ImportText.key(from ?: "") != ImportText.key(to)) changes += Change(label, from, to)
        }
        changed("Téléphone", existing.phone, values.phone)
        changed("Type", existing.customer_type, type)
        changedName("Secteur", existing.secteur_name, secteur)
        changedName("Wilaya", existing.wilaya_name, wilaya)
        changedName("Commune", existing.commune_name, commune)
        changed("Adresse", existing.address, values.address)
        changed("Note", existing.note, values.note)
        return if (changes.isEmpty()) RowOutcome.Unchanged(existing.id) else RowOutcome.UpdateClient(existing.id, values, changes)
    }

    // ── Reading a sheet ──

    private fun noHeader(sheet: ImportSheet, found: XlsxSheet) = PlannedRow(
        sheet, found.rows.firstOrNull()?.number ?: 1, sheet.title,
        RowOutcome.Refused("La première ligne doit être l'en-tête, avec au moins une colonne « Nom »."),
    )

    /** The headers of a sheet, keyed as [ImportText.headerKey] makes them, and the rows under them. */
    private class Columns(sheet: XlsxSheet, known: Map<String, String>) {
        private val header: XlsxRow? = sheet.rows.firstOrNull()
        /** Field key to column index, for headers the import reads. */
        private val fields: Map<String, Int>
        val ignored: List<String>
        val dataRows: List<XlsxRow> = sheet.rows.drop(1)

        init {
            val fields = mutableMapOf<String, Int>()
            val ignored = mutableListOf<String>()
            header?.cells?.forEach { (column, value) ->
                val text = value.asText() ?: return@forEach
                val field = known[ImportText.headerKey(text)]
                if (field != null && field !in fields) fields[field] = column else ignored += text.trim()
            }
            this.fields = fields
            this.ignored = ignored
        }

        fun missing(field: String) = field !in fields
        fun has(vararg field: String) = field.any { it in fields }
        fun cells(row: XlsxRow) = Cells(row, fields)
    }

    /** One row's cells by field, each read in the type the field wants. */
    private class Cells(private val row: XlsxRow, private val fields: Map<String, Int>) {
        private fun value(field: String): XlsxValue? = fields[field]?.let { row[it] }

        fun text(field: String): String? = value(field)?.asText()?.trim()?.takeIf { it.isNotEmpty() }

        fun amount(field: String): Double? = number(field)?.let { BigDecimal.valueOf(it).setScale(2, java.math.RoundingMode.HALF_UP).toDouble() }
        fun quantity(field: String): Double? = number(field)?.let { BigDecimal.valueOf(it).setScale(3, java.math.RoundingMode.HALF_UP).toDouble() }

        fun int(field: String): Int? = number(field)?.let {
            if (it != kotlin.math.floor(it) || it > Int.MAX_VALUE || it < Int.MIN_VALUE) throw Invalid("« ${text(field)} » n'est pas un nombre entier.")
            it.toInt()
        }

        fun number(field: String): Double? = when (val v = value(field)) {
            null -> null
            is XlsxValue.Number -> v.value
            is XlsxValue.Text -> v.value.trim().takeIf { it.isNotEmpty() }?.let { typed ->
                // `3 450,00` as French Excel shows it, `3450.00` as it is typed elsewhere.
                typed.replace(" ", "").replace(" ", "").replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
                    ?: throw Invalid("« $typed » n'est pas un nombre.")
            }
            is XlsxValue.Date -> throw Invalid("Une date se trouve là où un nombre est attendu.")
            is XlsxValue.Bool -> throw Invalid("Oui/Non se trouve là où un nombre est attendu.")
            is XlsxValue.Error -> throw Invalid("La cellule contient une erreur de formule (${v.code}).")
        }

        fun date(field: String): LocalDate? = when (val v = value(field)) {
            null -> null
            is XlsxValue.Date -> v.moment.toLocalDate()
            is XlsxValue.Text -> v.value.trim().takeIf { it.isNotEmpty() }?.let { typed ->
                DATE_FORMATS.firstNotNullOfOrNull { format -> try { LocalDate.parse(typed, format) } catch (e: DateTimeParseException) { null } }
                    ?: throw Invalid("« $typed » n'est pas une date (jj/mm/aaaa).")
            }
            is XlsxValue.Number -> throw Invalid("« ${v.plain} » n'est pas une date : donnez à la cellule le format Date.")
            is XlsxValue.Bool -> throw Invalid("Oui/Non se trouve là où une date est attendue.")
            is XlsxValue.Error -> throw Invalid("La cellule contient une erreur de formule (${v.code}).")
        }
    }

    /** A cell that cannot be read as its field wants; the row is refused with this message. */
    private class Invalid(message: String) : Exception(message)

    /** Decides a row, turning a cell that cannot be read into a refusal. */
    private inline fun outcome(decide: () -> RowOutcome): RowOutcome = try {
        decide()
    } catch (e: Invalid) {
        RowOutcome.Refused(e.message!!)
    }

    // ── The lookups the file names ──

    /** Collects the names the file uses, and which of them the app does not have. */
    private inner class Lookups {
        private val categories = linkedMapOf<String, String>()
        private val sousCategories = linkedMapOf<String, LinkedHashMap<String, String>>()
        private val marques = linkedMapOf<String, String>()
        private val suppliers = linkedMapOf<String, String>()
        private val secteurs = linkedMapOf<String, LinkedHashMap<String, String>>()

        fun category(name: String) {
            if (snapshot.categories.none { ImportText.key(it.name) == ImportText.key(name) }) categories.putIfAbsent(ImportText.key(name), name)
        }

        fun sousCategorie(category: String, name: String) {
            val parent = snapshot.categories.firstOrNull { ImportText.key(it.name) == ImportText.key(category) }
            val exists = parent != null && snapshot.sousCategories.any { it.category_id == parent.id && ImportText.key(it.name) == ImportText.key(name) }
            if (!exists) sousCategories.getOrPut(ImportText.key(category)) { linkedMapOf() }.putIfAbsent(ImportText.key(name), name)
            if (parent == null) category(category)
        }

        fun marque(name: String) {
            if (snapshot.marques.none { ImportText.key(it.name) == ImportText.key(name) }) marques.putIfAbsent(ImportText.key(name), name)
        }

        fun supplier(name: String) {
            if (snapshot.suppliers.none { ImportText.key(it.name) == ImportText.key(name) }) suppliers.putIfAbsent(ImportText.key(name), name)
        }

        fun secteur(commune: String, wilaya: String?, name: String) {
            val exists = snapshot.secteurs.any { ImportText.key(it.commune_name) == ImportText.key(commune) && ImportText.key(it.nom) == ImportText.key(name) }
            if (!exists) secteurs.getOrPut("$commune|${wilaya.orEmpty()}") { linkedMapOf() }.putIfAbsent(ImportText.key(name), name)
        }

        fun toCreate() = LookupsToCreate(
            categories = categories.values.toList(),
            sousCategories = sousCategories.mapKeys { (key, _) -> categories[key] ?: snapshot.categories.first { ImportText.key(it.name) == key }.name }
                .mapValues { it.value.values.toList() },
            marques = marques.values.toList(),
            suppliers = suppliers.values.toList(),
            secteurs = secteurs.mapValues { it.value.values.toList() },
        )
    }

    companion object {
        /** Header keys to field keys: the export's headers, and the ways a person might shorten them. */
        private val PRODUCT_HEADERS: Map<String, String> = mapOf(
            "nom" to "nom", "produit" to "nom", "designation" to "nom",
            "code barres" to "code barres", "code barre" to "code barres", "codebarre" to "code barres", "ean" to "code barres", "barcode" to "code barres",
            "categorie" to "categorie", "sous categorie" to "sous categorie", "marque" to "marque", "fournisseur" to "fournisseur",
            "unite" to "unite", "unites par colis" to "unites par colis", "unite par colis" to "unites par colis",
            "prix d achat" to "prix d achat", "prix achat" to "prix d achat", "pa" to "prix d achat",
            "prix de vente" to "prix de vente", "prix vente" to "prix de vente", "pv" to "prix de vente", "prix" to "prix de vente",
            "stock total" to "stock", "stock" to "stock", "stock depot" to "stock depot", "stock camion" to "stock camion",
            "stock minimum" to "stock minimum", "stock min" to "stock minimum",
            "date de peremption" to "date de peremption", "peremption" to "date de peremption", "date d expiration" to "date de peremption",
        )
        private val CLIENT_HEADERS: Map<String, String> = mapOf(
            "nom" to "nom", "client" to "nom",
            "telephone" to "telephone", "tel" to "telephone", "phone" to "telephone",
            "type" to "type", "type de client" to "type",
            "secteur" to "secteur", "wilaya" to "wilaya", "commune" to "commune", "adresse" to "adresse", "solde" to "solde", "note" to "note",
        )
        private val UNITS = mapOf("carton" to "carton", "cartons" to "carton", "piece" to "pièce", "pieces" to "pièce", "unite" to "pièce", "unites" to "pièce")
        private val CLIENT_TYPES = mapOf(
            "detail" to "retail", "retail" to "retail", "gros" to "wholesale", "wholesale" to "wholesale",
            "societe" to "business", "business" to "business", "entreprise" to "business",
        )
        private val DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("uuuu-M-d"), DateTimeFormatter.ofPattern("d-M-uuuu"), DateTimeFormatter.ofPattern("d.M.uuuu"),
        )

        private fun money(value: Double): String = BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',')
        /** `2026-12-31` as `31/12/2026`. */
        private fun day(iso: String): String = try {
            LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))
        } catch (e: DateTimeParseException) {
            iso
        }

        private fun XlsxValue.asText(): String? = when (this) {
            is XlsxValue.Text -> value
            is XlsxValue.Number -> plain
            is XlsxValue.Bool -> if (value) "Oui" else "Non"
            is XlsxValue.Date -> moment.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))
            is XlsxValue.Error -> null
        }
    }
}
