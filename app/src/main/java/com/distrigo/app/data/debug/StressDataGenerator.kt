package com.distrigo.app.data.debug

import androidx.room.withTransaction
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.ChargeEntity
import com.distrigo.app.data.local.entity.ChargementEntity
import com.distrigo.app.data.local.entity.ChargementItemEntity
import com.distrigo.app.data.local.entity.ChargementSessionEntity
import com.distrigo.app.data.local.entity.ClientEntity
import com.distrigo.app.data.local.entity.PerteEntity
import com.distrigo.app.data.local.entity.ProductEntity
import com.distrigo.app.data.local.entity.PurchaseOrderEntity
import com.distrigo.app.data.local.entity.PurchaseOrderItemEntity
import com.distrigo.app.data.local.entity.RetourClientEntity
import com.distrigo.app.data.local.entity.RetourClientItemEntity
import com.distrigo.app.data.local.entity.RetourFournisseurEntity
import com.distrigo.app.data.local.entity.RetourFournisseurItemEntity
import com.distrigo.app.data.local.entity.SupplierEntity
import com.distrigo.app.data.local.entity.TourneeClientEntity
import com.distrigo.app.data.local.entity.TourneeEntity
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
 * - every sale is made on a **tournée**, 200 of them, closed, a hundred sales each: a chargement
 *   loads the camion first, so its stock covers what it sells, and every client visited is on the
 *   tournée's list;
 * - a handful of sales run to 80–100 lines, for printing a receipt that goes on and on;
 * - client and supplier returns, charges and pertes, so those screens and their stock have history.
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

        for (k in 0 until TOURNEES) {
            insertTournee(k, random, start, clients, products)
            onProgress(Progress("Tournées : ${k + 1} / $TOURNEES (${(k + 1) * SALES_PER_TOURNEE} ventes)",
                0.3f + 0.5f * (k + 1) / TOURNEES))
        }

        onProgress(Progress("Retours clients et fournisseurs…", 0.82f))
        insertClientReturns(random, start, clients, products)
        insertSupplierReturns(random, start, supplierIds, products)

        onProgress(Progress("Charges et pertes…", 0.9f))
        insertCharges(random, start)
        insertPertes(random, start, products)

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

    /**
     * One closed tournée: its client list, the chargement that loads the camion, then its sales.
     *
     * The sales are decided first so the chargement can carry exactly what they take: the camion
     * leaves full and comes back empty, so its stock never goes below zero. One client return is
     * taken back on the way.
     */
    private suspend fun insertTournee(
        k: Int, random: Random, start: Instant, clients: List<ClientEntity>, products: List<ProductEntity>,
    ) = db.withTransaction {
        val opens = start.plusSeconds((k.toLong() * 365 * DAY_SECONDS) / TOURNEES + 7 * 3_600)
        val closes = opens.plusSeconds(10 * 3_600)
        val place = PLACES[k % PLACES.size]
        val tourneeId = db.tourneeDao().insertTournee(
            TourneeEntity(
                status = "fermée", date_debut = opens.toString(), date_fin = closes.toString(), note = TAG,
                nom = "$place ${k + 1}", wilaya_name = place, commune_name = place, created_at = opens.toString(),
            )
        ).toInt()

        // A route of about 30 clients; each of the hundred sales goes to one of them.
        val route = (0 until TOURNEE_CLIENTS).map { clients[(k * 17 + it * 7) % clients.size] }.distinctBy { it.id }
        val sales = (0 until SALES_PER_TOURNEE).map { n ->
            val long = n == 0 && k % (TOURNEES / LONG_RECEIPTS) == 0
            val count = if (long) random.nextInt(80, 101) else random.nextInt(1, 10)
            val lines = (0 until count)
                .map { if (long) products[random.nextInt(products.size)] else pickProduct(random, products) }
                .distinctBy { it.id }
            PlannedSale(route[random.nextInt(route.size)], lines, lines.map { random.nextInt(1, 13).toDouble() }, long)
        }
        val visited = sales.map { it.client.id }.toSet()

        db.tourneeClientDao().insertAll(route.mapIndexed { i, c ->
            TourneeClientEntity(
                tournee_id = tourneeId, client_id = c.id, order_index = i,
                status = if (c.id in visited) "visite" else "a_visiter",
                visited_at = if (c.id in visited) opens.plusSeconds(i * 600L).toString() else null,
                created_at = opens.toString(),
            )
        })

        // The morning chargement: everything the day's sales will take, per product.
        val loaded = linkedMapOf<Int, Pair<ProductEntity, Double>>()
        sales.forEach { sale ->
            sale.lines.forEachIndexed { i, p -> loaded[p.id] = p to ((loaded[p.id]?.second ?: 0.0) + sale.quantities[i]) }
        }
        val sessionId = db.chargementDao().insertSession(
            ChargementSessionEntity(
                session_date = opens.atOffset(ZoneOffset.UTC).toLocalDate().toString(), note = TAG, created_at = opens.toString(),
            )
        ).toInt()
        val chargementId = db.chargementDao().insertChargement(
            ChargementEntity(session_id = sessionId, note = TAG, created_at = opens.toString())
        ).toInt()
        db.chargementDao().insertItems(loaded.values.map { (p, quantity) ->
            ChargementItemEntity(
                chargement_id = chargementId, product_id = p.id, quantity = quantity, direction = "vers_camion",
                product_name = p.name, unit_type = p.unit_type, created_at = opens.toString(),
            )
        })

        sales.forEachIndexed { n, sale ->
            val at = opens.plusSeconds(1_800L + n * 300L)
            val total = sale.lines.indices.sumOf { sale.quantities[it] * sale.lines[it].selling_price }.roundCentimes()
            val paid = when (n % 4) { 0 -> 0.0; 1 -> (total / 2).roundCentimes(); else -> total }
            val venteId = db.venteDao().insertVente(
                VenteEntity(
                    client_id = sale.client.id, tournee_id = tourneeId, source = "camion", total = total,
                    montant_paye = paid, status = "delivered",
                    note = if (sale.long) "Reçu de test long (${sale.lines.size} lignes)" else TAG,
                    created_at = at.toString(), client_name = sale.client.name, user_name = USER,
                )
            ).toInt()
            db.venteDao().insertItems(sale.lines.mapIndexed { i, p ->
                VenteItemEntity(
                    vente_id = venteId, product_id = p.id, product_name = p.name, unit_type = p.unit_type,
                    quantity = sale.quantities[i], unit_price = p.selling_price,
                    total_price = sale.quantities[i] * p.selling_price, created_at = at.toString(),
                )
            })
            db.stockMovementDao().insertAll(sale.lines.mapIndexed { i, p ->
                StockMovementEntity(
                    product_id = p.id, product_name = p.name, type = "vente", direction = "sortie",
                    quantity = sale.quantities[i], emplacement = "camion",
                    source_label = sale.client.name, source_type = "vente", source_id = venteId,
                    unit_price = p.selling_price, total_value = sale.quantities[i] * p.selling_price,
                    user_name = USER, note = null, created_at = at.toString(),
                )
            })
        }

        // One return taken back into the camion, from a client who bought today.
        val back = sales[random.nextInt(sales.size)]
        insertClientReturn(back.client, back.lines.take(2), tourneeId, "camion", closes.minusSeconds(1_800), random)
    }

    private class PlannedSale(
        val client: ClientEntity, val lines: List<ProductEntity>, val quantities: List<Double>, val long: Boolean,
    )

    /** Returns at the dépôt, on top of the one each tournée takes back. */
    private suspend fun insertClientReturns(
        random: Random, start: Instant, clients: List<ClientEntity>, products: List<ProductEntity>,
    ) = db.withTransaction {
        repeat(DEPOT_CLIENT_RETURNS) { n ->
            val at = start.plusSeconds(((n + 1).toLong() * 365 * DAY_SECONDS) / (DEPOT_CLIENT_RETURNS + 1))
            val lines = (0 until random.nextInt(1, 4)).map { pickProduct(random, products) }.distinctBy { it.id }
            insertClientReturn(clients[random.nextInt(clients.size)], lines, null, "depot", at, random)
        }
    }

    /** A client return: the document, its lines, and the goods coming back into [emplacement]. */
    private suspend fun insertClientReturn(
        client: ClientEntity, lines: List<ProductEntity>, tourneeId: Int?, emplacement: String, at: Instant, random: Random,
    ) {
        val quantities = lines.map { random.nextInt(1, 4).toDouble() }
        val total = lines.indices.sumOf { quantities[it] * lines[it].selling_price }.roundCentimes()
        val retourId = db.retourClientDao().insertRetour(
            RetourClientEntity(
                client_id = client.id, tournee_id = tourneeId, date = at.atOffset(ZoneOffset.UTC).toLocalDate().toString(),
                motif = RETURN_REASONS[random.nextInt(RETURN_REASONS.size)], note = TAG, total = total,
                created_at = at.toString(),
            )
        ).toInt()
        db.retourClientDao().insertItems(lines.mapIndexed { i, p ->
            RetourClientItemEntity(
                retour_id = retourId, product_id = p.id, product_name = p.name, unit_type = p.unit_type,
                quantity = quantities[i], unit_price = p.selling_price, total_price = quantities[i] * p.selling_price,
                created_at = at.toString(),
            )
        })
        db.stockMovementDao().insertAll(lines.mapIndexed { i, p ->
            StockMovementEntity(
                product_id = p.id, product_name = p.name, type = "retour_client", direction = "entree",
                quantity = quantities[i], emplacement = emplacement, source_label = client.name,
                source_type = "retour_client", source_id = retourId, unit_price = p.selling_price,
                total_value = quantities[i] * p.selling_price, user_name = USER, note = TAG, created_at = at.toString(),
            )
        })
    }

    /** Goods sent back from the dépôt to the supplier they came from. */
    private suspend fun insertSupplierReturns(
        random: Random, start: Instant, suppliers: List<Int>, products: List<ProductEntity>,
    ) = db.withTransaction {
        repeat(SUPPLIER_RETURNS) { n ->
            val at = start.plusSeconds(((n + 1).toLong() * 365 * DAY_SECONDS) / (SUPPLIER_RETURNS + 1))
            val supplierIndex = n % suppliers.size
            val own = products.filter { it.supplier_id == suppliers[supplierIndex] }
            if (own.isEmpty()) return@repeat
            val lines = (0 until random.nextInt(1, 5)).map { own[random.nextInt(minOf(own.size, 50))] }.distinctBy { it.id }
            val quantities = lines.map { random.nextInt(2, 20).toDouble() }
            val total = lines.indices.sumOf { quantities[it] * lines[it].purchase_price }.roundCentimes()
            val retourId = db.retourFournisseurDao().insertRetour(
                RetourFournisseurEntity(
                    supplier_id = suppliers[supplierIndex], date = at.atOffset(ZoneOffset.UTC).toLocalDate().toString(),
                    motif = RETURN_REASONS[random.nextInt(RETURN_REASONS.size)], note = TAG, total = total,
                    created_at = at.toString(),
                )
            ).toInt()
            db.retourFournisseurDao().insertItems(lines.mapIndexed { i, p ->
                RetourFournisseurItemEntity(
                    retour_id = retourId, product_id = p.id, product_name = p.name, unit_type = p.unit_type,
                    quantity = quantities[i], unit_price = p.purchase_price,
                    total_price = quantities[i] * p.purchase_price, created_at = at.toString(),
                )
            })
            db.stockMovementDao().insertAll(lines.mapIndexed { i, p ->
                StockMovementEntity(
                    product_id = p.id, product_name = p.name, type = "retour_fournisseur", direction = "sortie",
                    quantity = quantities[i], emplacement = "depot", source_label = SUPPLIERS[supplierIndex],
                    source_type = "retour_fournisseur", source_id = retourId, unit_price = p.purchase_price,
                    total_value = quantities[i] * p.purchase_price, user_name = USER, note = TAG,
                    created_at = at.toString(),
                )
            })
        }
    }

    /** Expenses across every charge type the database already has, built-in or the user's own. */
    private suspend fun insertCharges(random: Random, start: Instant) = db.withTransaction {
        val types = db.chargeDao().getAllChargeTypes().associateBy { it.id }
        val subtypes = db.chargeDao().getAllSubTypes().filter { it.type_id in types }
        if (subtypes.isEmpty()) return@withTransaction
        repeat(CHARGES) { n ->
            val at = start.plusSeconds(((n + 1).toLong() * 365 * DAY_SECONDS) / (CHARGES + 1))
            val sub = subtypes[random.nextInt(subtypes.size)]
            db.chargeDao().insertCharge(
                ChargeEntity(
                    type_id = sub.type_id, type_name = types.getValue(sub.type_id).name,
                    subtype_id = sub.id, subtype_name = sub.name,
                    montant = random.nextInt(5, 500) * 100.0, date_time = at.toString(),
                    fournisseur = if (sub.has_fournisseur) SUPPLIERS[random.nextInt(SUPPLIERS.size)] else null,
                    note = TAG, created_at = at.toString(),
                )
            )
        }
    }

    /** Losses of every perte type, mostly at the dépôt, some from the camion, each leaving the stock. */
    private suspend fun insertPertes(random: Random, start: Instant, products: List<ProductEntity>) = db.withTransaction {
        val types = db.perteDao().getAllPerteTypes()
        if (types.isEmpty()) return@withTransaction
        repeat(PERTES) { n ->
            val at = start.plusSeconds(((n + 1).toLong() * 365 * DAY_SECONDS) / (PERTES + 1))
            val type = types[random.nextInt(types.size)]
            val p = pickProduct(random, products)
            val quantity = random.nextInt(1, 6).toDouble()
            val source = if (n % 5 == 0) "camion" else "depot"
            val perteId = db.perteDao().insertPerte(
                PerteEntity(
                    type_id = type.id, type_name = type.name, product_id = p.id, product_name = p.name,
                    product_image_uri = null, quantity = quantity, unit = p.unit_type, source = source,
                    purchase_price_snapshot = p.purchase_price, valeur_totale = quantity * p.purchase_price,
                    date_time = at.toString(), motif = TAG, photo_path = null, created_at = at.toString(),
                )
            ).toInt()
            db.stockMovementDao().insert(
                StockMovementEntity(
                    product_id = p.id, product_name = p.name, type = "perte", direction = "sortie",
                    quantity = quantity, emplacement = source, source_label = type.name,
                    source_type = "perte", source_id = perteId, unit_price = p.purchase_price,
                    total_value = quantity * p.purchase_price, user_name = USER, note = TAG, created_at = at.toString(),
                )
            )
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
        const val TOURNEES = 200
        const val SALES_PER_TOURNEE = 100
        const val TOURNEE_CLIENTS = 30
        const val LONG_RECEIPTS = 10
        const val DEPOT_CLIENT_RETURNS = 400
        const val SUPPLIER_RETURNS = 150
        const val CHARGES = 1_500
        const val PERTES = 400
        const val BATCH = 500
        const val PURCHASE_BATCH = 50
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
        val PLACES = listOf(
            "Alger-Centre", "Bab El Oued", "Hussein Dey", "Kouba", "Bir Mourad Raïs", "Chéraga", "Blida",
            "Boufarik", "Tipaza", "Koléa", "Boumerdès", "Rouiba", "Souk Ahras", "Sedrata", "Annaba", "Guelma",
        )
        val RETURN_REASONS = listOf("Produit défectueux", "Date proche", "Erreur de commande", "Emballage abîmé", "منتج تالف")
        val SHOPS_AR = listOf("مواد غذائية", "بقالة", "سوبيرات")
        val SHOPS_FR = listOf("Superette", "Alimentation générale", "Épicerie")
        val FIRST_AR = listOf("محمد", "أحمد", "كريم", "ياسين", "سمير", "نادية", "أمينة", "فريد", "سفيان", "رشيد")
        val LAST_AR = listOf("بن علي", "بوزيد", "حداد", "مرابط", "زروقي", "بلقاسم", "عمراني")
        val FIRST_FR = listOf("Mohamed", "Ahmed", "Karim", "Yacine", "Samir", "Nadia", "Amina", "Farid", "Sofiane", "Rachid")
        val LAST_FR = listOf("Benali", "Bouzid", "Haddad", "Merabet", "Zerrouki", "Belkacem", "Amrani")
    }
}
