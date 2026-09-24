package com.distrigo.app.data.debug

import androidx.room.withTransaction
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.ClientEntity
import com.distrigo.app.data.local.entity.ProductEntity
import com.distrigo.app.data.local.entity.PurchaseOrderEntity
import com.distrigo.app.data.local.entity.PurchaseOrderItemEntity
import com.distrigo.app.data.local.entity.SupplierEntity
import com.distrigo.app.data.local.entity.VenteEntity
import com.distrigo.app.data.local.entity.VenteItemEntity
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Fills the database with a year of made-up business, for stress-testing the lists, search and
 * printing. **Debug builds only** — the only caller is a button that exists when `BuildConfig.DEBUG`.
 *
 * Everything goes through the DAOs and therefore through the real triggers: the stock ledger computes
 * every product's stock, the numbering triggers number every document, and the balances are
 * recomputed at the end. The data is what the app itself would have produced, only more of it.
 *
 * Shaped like a real distributor's year rather than spread evenly:
 * - a hundred best-sellers take most of the lines, so their movement history is long — which is what
 *   the stock ledger and the Mouvements screen have to cope with;
 * - product names are Arabic, French, and mixed — an Arabic name with a Latin brand and size — plus a
 *   few far too long, which are what break a layout;
 * - a handful of sales run to 80–100 lines, for printing a receipt that goes on and on.
 *
 * Runs in batches, one transaction each, so a cancelled run leaves whole documents behind, never half
 * of one.
 */
class StressDataGenerator(private val db: AppDatabase) {

    data class Progress(val step: String, val fraction: Float)

    suspend fun generate(onProgress: (Progress) -> Unit) = withContext(Dispatchers.Default) {
        val random = Random(SEED)
        val start = Instant.now().minusSeconds(365L * DAY_SECONDS)

        onProgress(Progress("Fournisseurs et clients…", 0f))
        val supplierIds = insertSuppliers()
        val clients = insertClients(random)

        val products = mutableListOf<ProductEntity>()
        for (batch in 0 until PRODUCTS step BATCH) {
            products += insertProducts(batch, minOf(batch + BATCH, PRODUCTS), random, supplierIds)
            onProgress(Progress("Produits : ${products.size} / $PRODUCTS", 0.1f * products.size / PRODUCTS))
        }

        // Purchases first and generously, so the stock the sales take out was there to take.
        for (batch in 0 until PURCHASES step PURCHASE_BATCH) {
            insertPurchases(batch, minOf(batch + PURCHASE_BATCH, PURCHASES), random, start, supplierIds, products)
            onProgress(Progress("Achats : ${minOf(batch + PURCHASE_BATCH, PURCHASES)} / $PURCHASES",
                0.1f + 0.2f * (batch + PURCHASE_BATCH) / PURCHASES))
        }

        for (batch in 0 until SALES step SALE_BATCH) {
            insertSales(batch, minOf(batch + SALE_BATCH, SALES), random, start, clients, products)
            onProgress(Progress("Ventes : ${minOf(batch + SALE_BATCH, SALES)} / $SALES",
                0.3f + 0.65f * (batch + SALE_BATCH) / SALES))
        }

        onProgress(Progress("Soldes…", 0.97f))
        db.withTransaction {
            clients.forEach { db.clientDao().recomputeBalance(it.id) }
            supplierIds.forEach { db.supplierDao().recomputeBalance(it) }
        }
        onProgress(Progress("Terminé", 1f))
    }

    // ───────────────────────────── parties ─────────────────────────────

    private suspend fun insertSuppliers(): List<Int> = db.withTransaction {
        SUPPLIERS.map { name ->
            db.supplierDao().insertSupplier(
                SupplierEntity(
                    name = name, phone = "0550 00 00 00", address = null, note = TAG, balance = 0.0,
                    latitude = null, longitude = null, wilaya_name = "Alger", commune_name = null,
                )
            ).toInt()
        }
    }

    private suspend fun insertClients(random: Random): List<ClientEntity> = db.withTransaction {
        (0 until CLIENTS).map { i ->
            val name = if (i % 2 == 0) {
                "${SHOPS_AR[i % SHOPS_AR.size]} ${FIRST_AR[(i / 2) % FIRST_AR.size]} ${LAST_AR[(i / 7) % LAST_AR.size]}"
            } else {
                "${SHOPS_FR[i % SHOPS_FR.size]} ${FIRST_FR[(i / 2) % FIRST_FR.size]} ${LAST_FR[(i / 7) % LAST_FR.size]}"
            } + " ${i + 1}"
            val entity = ClientEntity(
                name = name, phone = "0${5 + i % 3}${random.nextInt(10_000_000, 99_999_999)}",
                wilaya_name = "Alger", commune_name = null, secteur_id = null, secteur_name = null,
                address = null, note = TAG, image_uri = null, latitude = null, longitude = null,
            )
            entity.copy(id = db.clientDao().insertClient(entity).toInt())
        }
    }

    // ───────────────────────────── products ─────────────────────────────

    private suspend fun insertProducts(from: Int, until: Int, random: Random, suppliers: List<Int>): List<ProductEntity> =
        db.withTransaction {
            (from until until).map { i ->
                val purchase = random.nextInt(40, 4_000).toDouble()
                val supplier = suppliers[i % suppliers.size]
                val entity = ProductEntity(
                    name = productName(i), barcode = null,
                    selling_price = (purchase * 1.2).roundCentimes(), purchase_price = purchase,
                    stock = 0.0, min_stock = 10,
                    unit_type = if (i % 4 == 0) "carton" else "pièce",
                    packages = 0, pack_size = if (i % 4 == 0) 12 else 0,
                    has_expiry = 0, expiry_date = null, image_uri = null,
                    category_name = null, category_id = null,
                    supplier_name = SUPPLIERS[i % SUPPLIERS.size], supplier_id = supplier,
                )
                entity.copy(id = db.productDao().insertProduct(entity).toInt())
            }
        }

    /**
     * A unique name for product [i]: Arabic, French, or Arabic with a Latin brand and size, in turn.
     * Every 50th is far too long for any column, on purpose.
     */
    private fun productName(i: Int): String {
        val j = i / 3
        val base = j % BASES_FR.size
        val brand = BRANDS[(j / BASES_FR.size) % BRANDS.size]
        val size = SIZES[(j / (BASES_FR.size * BRANDS.size)) % SIZES.size]
        val name = when (i % 3) {
            0    -> "${BASES_AR[base]} $brand $size"
            1    -> "${BASES_FR[base]} $brand $size"
            else -> {
                // Fewer Arabic brands and sizes than Latin ones: 20 × 8 × 8 = 1,280 names, so the
                // second round is marked "ممتاز" (premium) to stay unique.
                val ar = j % AR_COMBINATIONS
                "${BASES_AR[ar % BASES_AR.size]} ${SIZES_AR[(ar / (BASES_AR.size * BRANDS_AR.size)) % SIZES_AR.size]} " +
                    BRANDS_AR[(ar / BASES_AR.size) % BRANDS_AR.size] + if (j >= AR_COMBINATIONS) " ممتاز" else ""
            }
        }
        return if (i % 50 == 0) "$name — format familial économique, édition spéciale promotion" else name
    }

    // ───────────────────────────── documents ─────────────────────────────

    private suspend fun insertPurchases(
        from: Int, until: Int, random: Random, start: Instant,
        suppliers: List<Int>, products: List<ProductEntity>,
    ) = db.withTransaction {
        for (n in from until until) {
            // The first quarter of the year, so the stock is in before most of the selling.
            val at = start.plusSeconds((n.toLong() * 90 * DAY_SECONDS) / PURCHASES)
            val supplierIndex = n % suppliers.size
            val lines = (0 until random.nextInt(8, 25)).map { pickProduct(random, products) }.distinctBy { it.id }
            val quantities = lines.map { if (it.id % 100 < 5) random.nextInt(400, 1_500) else random.nextInt(20, 200) }
            val total = lines.indices.sumOf { quantities[it] * lines[it].purchase_price }
            val orderId = db.purchaseDao().insertOrder(
                PurchaseOrderEntity(
                    supplier_id = suppliers[supplierIndex], date = at.atOffset(ZoneOffset.UTC).toLocalDate().toString(),
                    total = total, status = "received", note = TAG, montant_paye = total * (n % 3) / 2,
                    created_at = at.toString(), supplier_name = SUPPLIERS[supplierIndex],
                )
            ).toInt()
            db.purchaseDao().insertItems(lines.mapIndexed { k, p ->
                PurchaseOrderItemEntity(
                    purchase_order_id = orderId, product_id = p.id, quantity = quantities[k].toDouble(),
                    unit_cost = p.purchase_price, total_cost = quantities[k] * p.purchase_price,
                    product_name = p.name, unit_type = p.unit_type, created_at = at.toString(),
                )
            })
            db.stockMovementDao().insertAll(lines.mapIndexed { k, p ->
                StockMovementEntity(
                    product_id = p.id, product_name = p.name, type = "achat", direction = "entree",
                    quantity = quantities[k].toDouble(), emplacement = "depot",
                    source_label = SUPPLIERS[supplierIndex], source_type = "purchase_order", source_id = orderId,
                    unit_price = p.purchase_price, total_value = quantities[k] * p.purchase_price,
                    user_name = USER, note = TAG, created_at = at.toString(),
                )
            })
        }
    }

    private suspend fun insertSales(
        from: Int, until: Int, random: Random, start: Instant,
        clients: List<ClientEntity>, products: List<ProductEntity>,
    ) = db.withTransaction {
        for (n in from until until) {
            val at = start.plusSeconds((n.toLong() * 365 * DAY_SECONDS) / SALES)
            val client = clients[random.nextInt(clients.size)]
            // The long receipts: evenly through the year, so some are recent enough to find.
            val long = n % (SALES / LONG_RECEIPTS) == 0
            val count = if (long) random.nextInt(80, 101) else random.nextInt(1, 10)
            val lines = (0 until count).map { if (long) products[random.nextInt(products.size)] else pickProduct(random, products) }
                .distinctBy { it.id }
            val quantities = lines.map { random.nextInt(1, 13).toDouble() }
            val total = lines.indices.sumOf { quantities[it] * lines[it].selling_price }.roundCentimes()
            val paid = when (n % 4) { 0 -> 0.0; 1 -> (total / 2).roundCentimes(); else -> total }
            val venteId = db.venteDao().insertVente(
                VenteEntity(
                    client_id = client.id, tournee_id = null, source = "depot", total = total, montant_paye = paid,
                    status = "delivered", note = if (long) "Reçu de test long ($count lignes)" else TAG,
                    created_at = at.toString(), client_name = client.name, user_name = USER,
                )
            ).toInt()
            db.venteDao().insertItems(lines.mapIndexed { k, p ->
                VenteItemEntity(
                    vente_id = venteId, product_id = p.id, product_name = p.name, unit_type = p.unit_type,
                    quantity = quantities[k], unit_price = p.selling_price, total_price = quantities[k] * p.selling_price,
                    created_at = at.toString(),
                )
            })
            db.stockMovementDao().insertAll(lines.mapIndexed { k, p ->
                StockMovementEntity(
                    product_id = p.id, product_name = p.name, type = "vente", direction = "sortie",
                    quantity = quantities[k], emplacement = "depot",
                    source_label = client.name, source_type = "vente", source_id = venteId,
                    unit_price = p.selling_price, total_value = quantities[k] * p.selling_price,
                    user_name = USER, note = null, created_at = at.toString(),
                )
            })
        }
    }

    /** Seven lines in ten from the hundred best-sellers; the rest from anywhere in the catalogue. */
    private fun pickProduct(random: Random, products: List<ProductEntity>): ProductEntity =
        if (random.nextInt(10) < 7) products[random.nextInt(minOf(100, products.size))]
        else products[random.nextInt(products.size)]

    private fun Double.roundCentimes() = Math.round(this * 100) / 100.0

    private companion object {
        const val SEED = 20260924
        const val PRODUCTS = 5_000
        const val CLIENTS = 500
        const val PURCHASES = 600
        const val SALES = 20_000
        const val LONG_RECEIPTS = 10
        const val BATCH = 500
        const val PURCHASE_BATCH = 50
        const val SALE_BATCH = 250
        const val DAY_SECONDS = 86_400L
        const val AR_COMBINATIONS = 20 * 8 * 8
        const val TAG = "Données de test"
        const val USER = "Test"

        val SUPPLIERS = listOf(
            "Cevital Distribution", "Groupe Hamoud", "Soummam Lait", "Rouiba SPA", "Ifri Eaux",
            "Tchin-Tchin Boissons", "Sim Semoulerie", "Metidji Pâtes", "مؤسسة الأمل للتوزيع", "شركة النور",
            "Candia Algérie", "Safina Import", "Isis Café", "Amor Benamor", "Lesieur Cristal",
            "Danone Djurdjura", "Nestlé Algérie", "مطاحن الجنوب", "La Belle", "Bimo",
        )
        val BASES_AR = listOf(
            "زيت زيتون", "حليب", "سكر أبيض", "دقيق", "سميد رقيق", "عجائن", "طماطم مصبرة", "قهوة مطحونة",
            "شاي أخضر", "ماء معدني", "مشروب غازي", "عصير برتقال", "جبن ذائب", "ياغورت طبيعي", "صابون",
            "مسحوق غسيل", "أرز", "عدس", "حمص", "فاصوليا بيضاء",
        )
        val BASES_FR = listOf(
            "Huile de tournesol", "Lait UHT", "Sucre blanc", "Farine", "Semoule fine", "Pâtes spaghetti",
            "Concentré de tomate", "Café moulu", "Thé vert", "Eau minérale", "Boisson gazeuse",
            "Jus d'orange", "Fromage fondu", "Yaourt nature", "Savon", "Lessive", "Riz", "Lentilles",
            "Pois chiches", "Haricots blancs",
        )
        val BRANDS = listOf(
            "Cevital", "Elio", "Candia", "Soummam", "Ifri", "Hamoud", "Rouiba", "Tchin-Tchin",
            "Safina", "Isis", "Amor Benamor", "Sim", "Metidji", "Lesieur", "Danone", "Nestlé",
        )
        val BRANDS_AR = listOf("الأصيل", "النخلة", "الريف", "السنبلة", "الوادي", "الفجر", "الهضاب", "الساحل")
        val SIZES = listOf("250g", "500g", "1kg", "2kg", "5kg", "25cl", "33cl", "1L", "1.5L", "2L", "5L")
        val SIZES_AR = listOf("250 غرام", "500 غرام", "1 كلغ", "2 كلغ", "5 كلغ", "1 لتر", "2 لتر", "5 لتر")
        val SHOPS_AR = listOf("مواد غذائية", "بقالة", "سوبيرات")
        val SHOPS_FR = listOf("Superette", "Alimentation générale", "Épicerie")
        val FIRST_AR = listOf("محمد", "أحمد", "كريم", "ياسين", "سمير", "نادية", "أمينة", "فريد", "سفيان", "رشيد")
        val LAST_AR = listOf("بن علي", "بوزيد", "حداد", "مرابط", "زروقي", "بلقاسم", "عمراني")
        val FIRST_FR = listOf("Mohamed", "Ahmed", "Karim", "Yacine", "Samir", "Nadia", "Amina", "Farid", "Sofiane", "Rachid")
        val LAST_FR = listOf("Benali", "Bouzid", "Haddad", "Merabet", "Zerrouki", "Belkacem", "Amrani")
    }
}
