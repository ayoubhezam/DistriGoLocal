package com.distrigo.app.ui.common

import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.data.model.Vente
import com.distrigo.app.ui.products.ProductListFilters
import java.time.LocalDate
import kotlin.random.Random

/**
 * Randomized list-screen data for the C6 equivalence and timing tests.
 *
 * Deliberately full of what breaks a careless rewrite: case folding that is not one-to-one (İ, ı,
 * ß, ǅ, ﬁ), accents, Arabic, full-width letters, whitespace that is not ASCII, empty and padded
 * names, names that differ only in case (so the name sorts meet ties), nullable fields left null,
 * payments above and below the total, and dates that are short, malformed or absent.
 */
internal object ListFilterFixtures {

    const val ROWS = 10_000

    val WORDS = listOf(
        "brilex", "Brilex", "BRILEX", "candia", "Candia", "lait", "Lait", "LAIT", "café", "Café", "CAFÉ",
        "cafe", "crème", "Crème", "épicerie", "ÉPICERIE", "İstanbul", "ıspanak", "Straße", "STRASSE",
        "ß", "ǅ", "ﬁlet", "Ω", "ω", "منافع", "حليب", "ماء", "Ｆｕｌｌ", "savon", "liquide", "400ml",
        "5l", "1.25l", "a", "A", "z", "Z", "0", "12", "#12", "x-y", "x_y",
        "nb\u00A0sp", "em\u2003sp", "tab\tname"
    )

    val QUERIES = listOf(
        "", " ", "   ", "\t", "\u00A0", "\u2003", "\n", "a", "A", "bri", "BRI", "brilex liquide",
        "bri   liq", " candia ", "café", "CAFÉ", "cafe", "é", "É", "crème", "i", "İ", "ı", "ss", "ß",
        "strasse", "ǅ", "ǆ", "ﬁ", "fi", "Ω", "ω", "منافع", "ما", "Ｆ", "0", "12", "#12", "5l", "1.25",
        "zzz", "nb\u00A0sp", "tab\tname", "x-y", "a b c", "l", "L", "0555", "05 55", "1", "3"
    )

    val CUSTOMER_TYPES = listOf("all", "retail", "wholesale", "business", "other", "")

    private fun <T> pick(r: Random, vararg options: T): T = options[r.nextInt(options.size)]

    private fun name(r: Random): String = when (r.nextInt(20)) {
        0    -> ""
        1    -> "  " + WORDS.random(r) + "  "
        else -> List(1 + r.nextInt(4)) { WORDS.random(r) }.joinToString(if (r.nextInt(8) == 0) "  " else " ")
    }

    private fun phone(r: Random): String? = pick(r, null, null, "", "0555 12 34 56", "0661234567", WORDS.random(r))

    private fun dateLike(r: Random): String? = pick(
        r,
        null, "", "2026-08-12T10:31:00Z", "2026-09-13 12:00:00", "2026-09-0", "2026-9-1", "9999-12-31",
        "2025-12-31T23:59:59Z",
        LocalDate.of(2026, 1 + r.nextInt(12), 1 + r.nextInt(28)).toString() + "T08:00:00Z"
    )

    fun products(seed: Int, n: Int = ROWS): List<Product> {
        val r = Random(seed)
        val today = LocalDate.now()
        return List(n) { i ->
            Product(
                id             = i + 1,
                name           = name(r),
                barcode        = pick(r, null, null, "", (100_000_000_000L + r.nextLong(899_999_999_999L)).toString(), WORDS.random(r), "0000000000012"),
                selling_price  = pick(r, 0.0, 10.0, 50.5, 100.0, 140.0, 140.0, 999.99, -1.0),
                purchase_price = 0.0,
                stock          = pick(r, -5.0, 0.0, 0.5, 1.0, 2.0, 3.0, 10.0, 99.5),
                min_stock      = pick(r, 0, 1, 2, 5, 10),
                unit_type      = pick(r, "pièce", "carton", "kg", "litre", ""),
                packages       = 0,
                pack_size      = 0,
                has_expiry     = pick(r, 0, 1, 1),
                expiry_date    = pick(r, null, "", "2020-01-01", "31/12/2026", "2026-13-45", today.plusDays((r.nextInt(50) - 5).toLong()).toString()),
                image_uri      = null,
                category_name  = null,
                category_id    = pick(r, null, 1, 2, 3),
                supplier_name  = null,
                supplier_id    = pick(r, null, 1, 2, 3),
                sous_categorie_id = pick(r, null, 1, 2),
                marque_id      = pick(r, null, 1, 2)
            )
        }
    }

    fun productFilters(r: Random) = ProductListFilters(
        categoryId      = pick(r, null, null, 1, 2, 9),
        sousCategorieId = pick(r, null, null, 1, 2),
        marqueId        = pick(r, null, null, 1, 2),
        supplierId      = pick(r, null, null, 1, 3),
        unitType        = pick(r, null, null, "pièce", "kg", ""),
        stockLevel      = pick(r, null, null, "in_stock", "low_stock", "out_of_stock", "weird"),
        priceMin        = pick(r, "", "", "0", "100", "50.5", "abc", "-5"),
        priceMax        = pick(r, "", "", "140", "999.99", "xyz"),
        expiringSoon    = pick(r, false, false, true)
    )

    fun clients(seed: Int, n: Int = ROWS): List<Client> {
        val r = Random(seed)
        return List(n) { i ->
            Client(
                id = i + 1, name = name(r), phone = phone(r),
                wilaya_id = null, commune_id = null, wilaya_name = null, commune_name = null,
                secteur_id = null, secteur_name = null, address = null, note = null,
                balance       = pick(r, -50.0, 0.0, 0.0, 0.01, 140.0, 3890.0),
                customer_type = pick(r, "retail", "wholesale", "business", "other", ""),
                image_uri = null, latitude = null, longitude = null
            )
        }
    }

    fun suppliers(seed: Int, n: Int = ROWS): List<Supplier> {
        val r = Random(seed)
        return List(n) { i ->
            Supplier(
                id = i + 1, name = name(r), phone = phone(r), address = null, note = null,
                balance = pick(r, -50.0, 0.0, 0.0, 0.01, 120.0, 1420.0, 3580.0),
                latitude = null, longitude = null
            )
        }
    }

    fun ventes(seed: Int, n: Int = ROWS): List<Vente> {
        val r = Random(seed)
        val ids = (1..n).shuffled(r)
        return List(n) { i ->
            val total = pick(r, 0.0, 100.0, 270.0, 19640.0, -10.0)
            Vente(
                id           = ids[i],
                client_id    = 1 + r.nextInt(40),
                client_name  = name(r),
                tournee_id   = null,
                source       = pick(r, "depot", "depot", "camion", "other"),
                total        = total,
                montant_paye = pick(r, null, 0.0, 50.0, total, total + 1.0, -1.0),
                status       = pick(r, "pending", "delivered", "livree", "cancelled"),
                note         = null,
                created_at   = dateLike(r)
            )
        }
    }

    fun venteFilters(r: Random) = VenteListFilters(
        status        = pick(r, null, null, "pending", "delivered", "nope"),
        paymentStatus = pick(r, null, null, "paye", "impaye", "partiel", "weird"),
        clientId      = pick(r, null, null, 1, 5, 40, 999),
        dateFrom      = pick(r, null, null, "", "2026-08-01", "2026-09-10", "9999"),
        dateTo        = pick(r, null, null, "", "2026-08-31", "2026-09-13", "0")
    )

    fun orders(seed: Int, n: Int = ROWS): List<PurchaseOrder> {
        val r = Random(seed)
        val ids = (1..n).shuffled(r)
        return List(n) { i ->
            val total = pick(r, 0.0, 420.0, 1200.0, 23132.0, -10.0)
            PurchaseOrder(
                id            = ids[i],
                date          = pick(r, "2026-08-12", "2026-8-1", "", "2026-09-13T00:00:00Z", "2026-01-05 08:00"),
                total         = total,
                status        = pick(r, "pending", "received", "other"),
                note          = null,
                supplier_id   = 1 + r.nextInt(40),
                supplier_name = name(r),
                created_at    = dateLike(r),
                montant_paye  = pick(r, null, 0.0, 50.0, total, total + 1.0, -1.0)
            )
        }
    }

    fun orderFilters(r: Random) = OrderListFilters(
        receptionStatus = pick(r, null, null, "pending", "received", "nope"),
        paymentStatus   = pick(r, null, null, "paye", "impaye", "partiel", "weird"),
        supplierId      = pick(r, null, null, 1, 7, 40, 999),
        dateFrom        = pick(r, null, null, "", "2026-08-01", "2026-09-10", "9999"),
        dateTo          = pick(r, null, null, "", "2026-08-31", "2026-09-13", "0")
    )

    /** A query made readable in an assertion message: its whitespace spelled out. */
    fun show(query: String) = "\"" + query
        .replace("\t", "\\t").replace("\n", "\\n")
        .replace("\u00A0", "\\u00A0").replace("\u2003", "\\u2003") + "\""
}
