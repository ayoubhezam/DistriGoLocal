package com.distrigo.app.data.importer

import com.distrigo.app.R
import com.distrigo.app.data.model.Quantity
import com.distrigo.app.data.local.entity.ClientEntity
import com.distrigo.app.data.local.entity.MAX_BARCODES_PER_PRODUCT
import com.distrigo.app.data.local.entity.ProductEntity
import com.distrigo.app.data.repository.TOO_MANY_BARCODES
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
 * inventory or an adjustment, so the stock cells of an existing product's row are never read. A new product takes
 * its stock as an opening adjustment, as the form does; below zero only while « Autoriser le stock négatif » is on.
 */
/** An import message in the app's language, written when the plan is made: the preview shows it as it is. */
internal fun importText(@androidx.annotation.StringRes id: Int, vararg args: Any): String =
    com.distrigo.app.core.text.AppStrings.write(com.distrigo.app.core.text.UiText.Res(id, args.toList()))

class ImportPlanner(private val snapshot: ImportSnapshot, private val geo: ImportGeo) {

    /**
     * Plans [workbook]'s sheets — only [only]'s when given: Produits imports products and Clients clients, whatever
     * else the file holds (2026-10-21).
     */
    fun plan(workbook: XlsxWorkbook, only: ImportSheet? = null): ImportPlan {
        val notes = mutableListOf<String>()
        val lookups = Lookups()
        val sheets = ImportSheet.entries.filter { only == null || it == only }.mapNotNull { sheet ->
            val found = workbook.sheets.firstOrNull { ImportSheet.named(it.name) == sheet } ?: return@mapNotNull null
            when (sheet) {
                ImportSheet.PRODUITS -> planProducts(found, lookups, notes)
                ImportSheet.CLIENTS -> planClients(found, lookups, notes)
            }
        }
        if (sheets.isEmpty() && only != null) {
            val other = ImportSheet.entries.firstOrNull { other -> other != only && workbook.sheets.any { ImportSheet.named(it.name) == other } }
            throw ImportPlanException(
                if (other != null) importText(if (other == ImportSheet.CLIENTS) R.string.import_file_has_clients else R.string.import_file_has_products)
                else importText(if (only == ImportSheet.CLIENTS) R.string.import_no_clients_sheet else R.string.import_no_products_sheet)
            )
        }
        if (sheets.isEmpty()) {
            throw ImportPlanException(
                importText(R.string.import_no_sheet)
            )
        }
        return ImportPlan(sheets, lookups.toCreate(), notes.distinct())
    }

    // ── Produits ──

    /** A product's codes, primary first, its `barcode` mirror included. */
    private fun codesOf(product: ProductEntity): List<String> =
        (listOfNotNull(product.barcode) + snapshot.barcodes[product.id].orEmpty())
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** The codes a cell holds: one, or several separated by « ; », « , » or a line break. */
    private fun splitCodes(cell: String?): List<String> =
        cell.orEmpty().split(';', ',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun planProducts(sheet: XlsxSheet, lookups: Lookups, notes: MutableList<String>): SheetPlan {
        val columns = Columns(sheet, PRODUCT_HEADERS)
        if (columns.missing("nom")) return SheetPlan(ImportSheet.PRODUITS, columns.ignored, listOf(noHeader(ImportSheet.PRODUITS, sheet)))
        if (columns.has("stock", "stock depot", "stock camion")) {
            notes += importText(R.string.import_note_stock)
        }

        // Every code of every product, the primary mirror included, to the products that have it.
        val byBarcode = snapshot.products
            .flatMap { product -> codesOf(product).map { it to product } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, owners) -> owners.distinctBy { it.id } }
        val byName = snapshot.products.groupBy { ImportText.key(it.name) }
        val seen = mutableSetOf<String>()
        val seenBarcodes = mutableSetOf<String>()

        val rows = columns.dataRows.map { row ->
            val cells = columns.cells(row)
            outcome { planProduct(cells, byBarcode, byName, seen, seenBarcodes, lookups) }
                .let { PlannedRow(ImportSheet.PRODUITS, row.number, cells.text("nom") ?: importText(R.string.import_no_name), it) }
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
        val name = cells.text("nom") ?: return RowOutcome.Refused(importText(R.string.import_name_required))
        // One code, or several separated by « ; » as the export writes them; the first is the primary.
        val codes = splitCodes(cells.text("code barres"))
        val barcode = codes.firstOrNull()
        val nameKey = ImportText.key(name)

        codes.firstOrNull { byBarcode[it].let { owners -> owners != null && owners.size > 1 } }?.let {
            return RowOutcome.Refused(importText(R.string.import_barcode_shared, it))
        }
        // Which product this row is about: the one its codes belong to, else the one with its name.
        val owners = codes.flatMap { byBarcode[it].orEmpty() }.distinctBy { it.id }
        if (owners.size > 1) {
            return RowOutcome.Refused(importText(R.string.import_barcodes_several, owners.joinToString { "« ${it.name} »" }))
        }
        val existing = owners.singleOrNull() ?: byName[nameKey]?.let { same ->
            if (same.size > 1) return RowOutcome.Refused(importText(R.string.import_name_shared_products, name))
            same.singleOrNull()
        }
        // A name that differs only by case or accents is the same name, kept as the app spells it.
        val renamed = existing != null && ImportText.key(existing.name) != nameKey
        val keptName = if (existing != null && !renamed) existing.name else name
        // The name this row would take must not be another product's. Its codes cannot be: any code another
        // product has made that product the one this row is about.
        if (renamed || existing == null) {
            byName[nameKey]?.firstOrNull { it.id != existing?.id }?.let { return RowOutcome.Refused(importText(R.string.import_name_taken, it.barcode ?: "—")) }
        }
        // The file's codes are added to the product's; the others it had are kept.
        val existingCodes = existing?.let(::codesOf).orEmpty()
        val allCodes = codes + (existingCodes - codes.toSet())
        if (allCodes.size > MAX_BARCODES_PER_PRODUCT) return RowOutcome.Refused(TOO_MANY_BARCODES)
        // The same product twice in one file would apply twice; only the first row that passes counts.
        if (!seen.add(existing?.id?.toString() ?: "new:$nameKey")) return RowOutcome.Refused(importText(R.string.import_already_above, name))
        if (existing == null) {
            codes.firstOrNull { !seenBarcodes.add(it) }?.let { return RowOutcome.Refused(importText(R.string.import_barcode_above, it)) }
        }

        val unitType = cells.text("unite")?.let { UNITS[ImportText.key(it)] ?: return RowOutcome.Refused(importText(R.string.import_unknown_unit, it)) }
        val packSize = cells.int("unites par colis")?.let { if (it < 0) return RowOutcome.Refused(importText(R.string.import_pack_negative)) else it }
        val purchase = cells.amount("prix d achat") ?: cells.amount("prix achat")
        val selling = cells.amount("prix de vente") ?: cells.amount("prix vente")
        if (purchase != null && purchase < 0) return RowOutcome.Refused(importText(R.string.import_purchase_negative))
        if (selling != null && selling < 0) return RowOutcome.Refused(importText(R.string.import_selling_negative))
        val minStock = cells.quantity("stock minimum")?.let { if (it < 0) return RowOutcome.Refused(importText(R.string.import_min_negative)) else it }
        // Read for a new product only: an existing product's stock comes from its movements, whatever the file says
        val stock = if (existing != null) null else cells.quantity("stock depot") ?: cells.quantity("stock")
        if (stock != null && stock < 0 && !snapshot.allowNegativeStock) return RowOutcome.Refused(NEGATIVE_OPENING_STOCK)
        val expiry = cells.date("date de peremption")

        val category = cells.text("categorie")
        val sousCategorie = cells.text("sous categorie")
        val marque = cells.text("marque")
        val supplier = cells.text("fournisseur")
        // A sub-category lives under a category: the row's, or the product's own when the row leaves it.
        val categoryForSub = category ?: existing?.category_name
        if (sousCategorie != null && categoryForSub == null) return RowOutcome.Refused(importText(R.string.import_sub_needs_category, sousCategorie))

        val values = ProductValues(
            name = keptName, barcode = barcode, category = category, sousCategorie = sousCategorie, marque = marque,
            supplier = supplier, unitType = unitType, packSize = packSize, purchasePrice = purchase, sellingPrice = selling,
            initialStock = stock, minStock = minStock, expiry = expiry, barcodes = allCodes,
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
        if (renamed) changes += Change(importText(R.string.import_col_name), existing.name, name)
        if (codes.isNotEmpty() && allCodes != existingCodes) {
            changes += Change(importText(R.string.import_col_barcode), existingCodes.joinToString("; ").ifEmpty { null }, allCodes.joinToString("; "))
        }
        changedName(importText(R.string.import_col_category), existing.category_name, category)
        changedName(importText(R.string.import_col_subcategory), existing.sous_categorie_name, sousCategorie)
        changedName(importText(R.string.import_col_brand), existing.marque_name, marque)
        changedName(importText(R.string.import_col_supplier), existing.supplier_name, supplier)
        changed(importText(R.string.import_col_unit), existing.unit_type, unitType)
        changed(importText(R.string.import_col_pack), existing.pack_size.toString(), packSize?.toString())
        changed(importText(R.string.import_col_purchase), money(existing.purchase_price), purchase?.let(::money))
        changed(importText(R.string.import_col_selling), money(existing.selling_price), selling?.let(::money))
        changed(importText(R.string.import_col_min_stock), Quantity.format(existing.min_stock), minStock?.let(Quantity::format))
        changed(importText(R.string.import_col_expiry), existing.expiry_date?.takeIf { existing.has_expiry == 1 }?.let(::day), expiry?.let { day(it.toString()) })
        return if (changes.isEmpty()) RowOutcome.Unchanged(existing.id) else RowOutcome.UpdateProduct(existing.id, values.copy(initialStock = null), changes)
    }

    // ── Clients ──

    private fun planClients(sheet: XlsxSheet, lookups: Lookups, notes: MutableList<String>): SheetPlan {
        val columns = Columns(sheet, CLIENT_HEADERS)
        if (columns.missing("nom")) return SheetPlan(ImportSheet.CLIENTS, columns.ignored, listOf(noHeader(ImportSheet.CLIENTS, sheet)))
        if (columns.has("solde")) notes += importText(R.string.import_note_balance)

        val byName = snapshot.clients.groupBy { ImportText.key(it.name) }
        val seen = mutableSetOf<String>()
        val rows = columns.dataRows.map { row ->
            val cells = columns.cells(row)
            outcome { planClient(cells, byName, seen, lookups) }.let { PlannedRow(ImportSheet.CLIENTS, row.number, cells.text("nom") ?: importText(R.string.import_no_name), it) }
        }
        return SheetPlan(ImportSheet.CLIENTS, columns.ignored, rows)
    }

    private fun planClient(cells: Cells, byName: Map<String, List<ClientEntity>>, seen: MutableSet<String>, lookups: Lookups): RowOutcome {
        val name = cells.text("nom") ?: return RowOutcome.Refused(importText(R.string.import_name_required))
        val key = ImportText.key(name)
        val same = byName[key].orEmpty()
        if (same.size > 1) return RowOutcome.Refused(importText(R.string.import_name_shared_clients, name))
        if (!seen.add(key)) return RowOutcome.Refused(importText(R.string.import_already_above, name))
        val existing = same.singleOrNull()

        val type = cells.text("type")?.let { CLIENT_TYPES[ImportText.key(it)] ?: return RowOutcome.Refused(importText(R.string.import_unknown_type, it)) }
        val wilaya = cells.text("wilaya")?.let { geo.wilaya(it) ?: return RowOutcome.Refused(importText(R.string.import_unknown_wilaya, it)) }
        val wilayaForCommune = wilaya ?: existing?.wilaya_name
        val commune = cells.text("commune")?.let { typed ->
            if (wilayaForCommune == null) return RowOutcome.Refused(importText(R.string.import_commune_needs_wilaya, typed))
            geo.commune(wilayaForCommune, typed) ?: return RowOutcome.Refused(importText(R.string.import_commune_not_in_wilaya, typed, wilayaForCommune))
        }
        val communeForSecteur = commune ?: existing?.commune_name?.takeIf { wilaya == null || ImportText.key(existing.wilaya_name ?: "") == ImportText.key(wilaya) }
        val secteur = cells.text("secteur")?.let { typed ->
            if (communeForSecteur == null) return RowOutcome.Refused(importText(R.string.import_secteur_needs_commune, typed))
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
        changed(importText(R.string.import_col_phone), existing.phone, values.phone)
        changed(importText(R.string.import_col_type), existing.customer_type, type)
        changedName(importText(R.string.import_col_secteur), existing.secteur_name, secteur)
        changedName(importText(R.string.import_col_wilaya), existing.wilaya_name, wilaya)
        changedName(importText(R.string.import_col_commune), existing.commune_name, commune)
        changed(importText(R.string.import_col_address), existing.address, values.address)
        changed(importText(R.string.import_col_note), existing.note, values.note)
        return if (changes.isEmpty()) RowOutcome.Unchanged(existing.id) else RowOutcome.UpdateClient(existing.id, values, changes)
    }

    // ── Reading a sheet ──

    private fun noHeader(sheet: ImportSheet, found: XlsxSheet) = PlannedRow(
        sheet, found.rows.firstOrNull()?.number ?: 1, sheet.title,
        RowOutcome.Refused(importText(R.string.import_no_header)),
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
            if (it != kotlin.math.floor(it) || it > Int.MAX_VALUE || it < Int.MIN_VALUE) throw Invalid(importText(R.string.import_not_whole, text(field) ?: ""))
            it.toInt()
        }

        fun number(field: String): Double? = when (val v = value(field)) {
            null -> null
            is XlsxValue.Number -> v.value
            is XlsxValue.Text -> v.value.trim().takeIf { it.isNotEmpty() }?.let { typed ->
                // `3 450,00` as French Excel shows it, `3,450.00` as an English one does — see ImportNumber.
                when (val reading = ImportNumber.read(typed)) {
                    is ImportNumber.Reading.Value -> reading.value
                    ImportNumber.Reading.Ambiguous -> throw Invalid(
                        importText(R.string.import_ambiguous_number, typed)
                    )
                    ImportNumber.Reading.NotANumber -> throw Invalid(importText(R.string.import_not_a_number, typed))
                }
            }
            is XlsxValue.Date -> throw Invalid(importText(R.string.import_date_for_number))
            is XlsxValue.Bool -> throw Invalid(importText(R.string.import_bool_for_number))
            is XlsxValue.Error -> throw Invalid(importText(R.string.import_formula_error, v.code))
        }

        fun date(field: String): LocalDate? = when (val v = value(field)) {
            null -> null
            is XlsxValue.Date -> v.moment.toLocalDate()
            is XlsxValue.Text -> v.value.trim().takeIf { it.isNotEmpty() }?.let { typed ->
                DATE_FORMATS.firstNotNullOfOrNull { format -> try { LocalDate.parse(typed, format) } catch (e: DateTimeParseException) { null } }
                    ?: throw Invalid(importText(R.string.import_not_a_date, typed))
            }
            is XlsxValue.Number -> throw Invalid(importText(R.string.import_number_for_date, v.plain))
            is XlsxValue.Bool -> throw Invalid(importText(R.string.import_bool_for_date))
            is XlsxValue.Error -> throw Invalid(importText(R.string.import_formula_error, v.code))
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
        val NEGATIVE_OPENING_STOCK: String
            get() = importText(R.string.import_negative_off)

        private val PRODUCT_HEADERS: Map<String, String> = mapOf(
            "nom" to "nom", "produit" to "nom", "designation" to "nom",
            "code barres" to "code barres", "code barre" to "code barres", "codebarre" to "code barres", "ean" to "code barres", "barcode" to "code barres",
            "categorie" to "categorie", "sous categorie" to "sous categorie", "marque" to "marque", "fournisseur" to "fournisseur",
            "unite" to "unite", "unites par colis" to "unites par colis", "unite par colis" to "unites par colis",
            "prix d achat" to "prix d achat", "prix achat" to "prix d achat", "pa" to "prix d achat",
            "prix de vente" to "prix de vente", "prix vente" to "prix de vente", "pv" to "prix de vente", "prix" to "prix de vente",
            "stock total" to "stock", "stock" to "stock", "stock depot" to "stock depot", "stock camion" to "stock camion",
            "stock initial" to "stock depot", "stock de depart" to "stock depot",
            "stock minimum" to "stock minimum", "stock min" to "stock minimum",
            "date de peremption" to "date de peremption", "peremption" to "date de peremption", "date d expiration" to "date de peremption",
        )
        private val CLIENT_HEADERS: Map<String, String> = mapOf(
            "nom" to "nom", "client" to "nom",
            "telephone" to "telephone", "tel" to "telephone", "phone" to "telephone",
            "type" to "type", "type de client" to "type",
            "secteur" to "secteur", "wilaya" to "wilaya", "commune" to "commune", "adresse" to "adresse", "solde" to "solde", "note" to "note",
        )
        private val UNITS = mapOf(
            "carton" to "carton", "cartons" to "carton", "piece" to "pièce", "pieces" to "pièce", "unite" to "pièce", "unites" to "pièce",
            "kg" to "kg", "kgs" to "kg", "kilo" to "kg", "kilos" to "kg", "kilogramme" to "kg", "kilogrammes" to "kg",
        )
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
            is XlsxValue.Bool -> importText(if (value) R.string.import_yes else R.string.import_no)
            is XlsxValue.Date -> moment.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))
            is XlsxValue.Error -> null
        }
    }
}
