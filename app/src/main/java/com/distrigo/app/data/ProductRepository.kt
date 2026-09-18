package com.distrigo.app.data.repository

import com.distrigo.app.data.time.BusinessDates
import androidx.room.withTransaction
import com.distrigo.app.data.model.numberLabel
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.dao.*
import com.distrigo.app.data.model.*
import com.distrigo.app.data.local.entity.*
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity
import com.distrigo.app.data.local.entity.ProductImageEntity
import com.distrigo.app.data.local.entity.MAX_IMAGES_PER_PRODUCT
import com.distrigo.app.data.model.ProductImage
import com.distrigo.app.data.local.entity.SecteurEntity
import com.distrigo.app.data.local.entity.TourneeSecteurEntity
import com.distrigo.app.data.model.Secteur
import com.distrigo.app.data.model.TourneeSecteur
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class ProductRepository(
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val supplierDao: SupplierDao,
    private val db: AppDatabase,
    private val clientDao: ClientDao = db.clientDao(),
    private val sousCategorieDao: SousCategorieDao = db.sousCategorieDao(),
    private val marqueDao: MarqueDao = db.marqueDao()


) {

    private fun ProductEntity.toProduct(): Product {
        return Product(
            id = this.id,
            name = this.name,
            barcode = this.barcode,
            selling_price = this.selling_price,
            purchase_price = this.purchase_price,
            stock = this.stock,
            min_stock = this.min_stock,
            unit_type = this.unit_type,
            packages = this.packages,
            pack_size = this.pack_size,
            has_expiry = this.has_expiry,
            expiry_date = this.expiry_date,
            image_uri = this.image_uri,
            category_name = this.category_name,
            category_id = this.category_id,
            supplier_name = this.supplier_name,
            supplier_id = this.supplier_id,
            camion_stock = this.camion_stock,
            sous_categorie_id = this.sous_categorie_id,
            sous_categorie_name = this.sous_categorie_name,
            marque_id = this.marque_id,
            marque_name = this.marque_name
        )
    }
    private fun CategoryEntity.toCategory(): Category {
        return Category(id = this.id, name = this.name, sort_order = this.sort_order)
    }

    private fun SousCategorieEntity.toSousCategorie(): SousCategorie {
        return SousCategorie(id = this.id, category_id = this.category_id, name = this.name, sort_order = this.sort_order)
    }

    private fun MarqueEntity.toMarque(): Marque {
        return Marque(id = this.id, name = this.name, sort_order = this.sort_order)
    }

    private fun SupplierEntity.toSupplier(): Supplier {
        return Supplier(
            id = this.id,
            name = this.name,
            phone = this.phone,
            address = this.address,
            note = this.note,
            balance = this.balance,
            initial_balance = this.initial_balance,
            latitude = this.latitude,
            longitude = this.longitude,
            wilaya_name = this.wilaya_name,
            commune_name = this.commune_name,
            image_uri = this.image_uri
        )
    }
    private fun ClientEntity.toClient(): Client {
        return Client(
            id = this.id, name = this.name, phone = this.phone,
            wilaya_id = null, commune_id = null,
            wilaya_name = this.wilaya_name, commune_name = this.commune_name,
            secteur_id = this.secteur_id, secteur_name = this.secteur_name,
            address = this.address, note = this.note, balance = this.balance,
            customer_type = this.customer_type, image_uri = this.image_uri,
            latitude = this.latitude, longitude = this.longitude
        )
    }

    /**
     * Records a change of stock that has no document behind it — a product created with stock, or a
     * stock typed over — as an `ajustement` at the dépôt. The movement is what changes the stock: the
     * ledger triggers recompute the product from it (see StockLedger.kt).
     */
    private suspend fun recordStockAdjustment(product: ProductEntity, delta: Double, label: String) {
        if (delta == 0.0) return
        db.stockMovementDao().insert(
            StockMovementEntity(
                product_id   = product.id,
                product_name = product.name,
                type         = "ajustement",
                direction    = if (delta > 0) "entree" else "sortie",
                quantity     = kotlin.math.abs(delta),
                emplacement  = "depot",
                source_label = label,
                source_type  = "product",
                source_id    = product.id,
                unit_price   = product.purchase_price,
                total_value  = kotlin.math.abs(delta) * product.purchase_price,
                user_name    = null,
                note         = null,
                created_at   = java.time.Instant.now().toString()
            )
        )
    }

    private fun ChargementItemEntity.toChargementItem() = ChargementItem(
        id           = this.id,
        product_id   = this.product_id,
        quantity     = this.quantity,
        direction    = this.direction,
        product_name = this.product_name,
        unit_type    = this.unit_type
    )
    private fun VenteItemEntity.toItem() = VenteItem(
        id = this.id, product_id = this.product_id, product_name = this.product_name,
        unit_type = this.unit_type, quantity = this.quantity,
        unit_price = this.unit_price, total_price = this.total_price
    )

    private suspend fun VenteEntity.toVente(items: List<VenteItem>? = null): Vente =
        toVenteWith(
            clientName = clientDao.getClientById(this.client_id)?.name ?: "",
            itemsCount = items?.size,
            items      = items
        )

    /**
     * The one place a VenteEntity becomes a Vente. [toVente] looks the client up itself; the tournée
     * detail path already has the live name and the item count from its query and passes them in,
     * so the two paths cannot drift apart field by field.
     */
    private fun VenteEntity.toVenteWith(clientName: String, itemsCount: Int?, items: List<VenteItem>?) = Vente(
        id = this.id, client_id = this.client_id, client_name = clientName,
        tournee_id = this.tournee_id, source = this.source, total = this.total,
        montant_paye = this.montant_paye, status = this.status, note = this.note,
        created_at = this.created_at, items_count = itemsCount, items = items,
        client_image_uri = this.client_image_uri,  // ← جديد
        user_name = this.user_name,
        numero = this.numero
    )
    /**
     * The one place a tournée row becomes a Tournee. The callers supply the counters, and the sales
     * only for the detail screen, because they arrive in different shapes: the list's from one
     * aggregate query, the detail's from that tournée's own sales rows.
     */
    private fun TourneeEntity.toTournee(
        secteurs     : List<TourneeSecteurEntity>,
        clientsCount : Int,
        ventesCount  : Int,
        totalVentes  : Double,
        resteTotal   : Double,
        ventes       : List<Vente>?
    ) = Tournee(
        id = this.id, session_id = 0, status = this.status,
        date_debut = this.date_debut, date_fin = this.date_fin, note = this.note,
        nom = this.nom, wilaya_id = null, commune_id = null,
        wilaya_name = this.wilaya_name, commune_name = this.commune_name,
        secteurs = secteurs.map { TourneeSecteur(secteurId = it.secteur_id, nom = it.secteur_name) },
        clients_count = clientsCount, ventes_count = ventesCount,
        total_ventes = totalVentes, reste_total = resteTotal, ventes = ventes
    )

    /** A list-shaped Tournee: its four counters and no sales. See TourneeDao.getAllTourneeSummaries. */
    private fun TourneeSummaryRow.toTournee(secteurs: List<TourneeSecteurEntity>) = tournee.toTournee(
        secteurs, clients_count, ventes_count, total_ventes, reste_total, ventes = null
    )

    private fun PurchaseOrderItemEntity.toItem() = PurchaseOrderItem(
        id = this.id, quantity = this.quantity, unit_cost = this.unit_cost,
        total_cost = this.total_cost, product_id = this.product_id,
        product_name = this.product_name, unit_type = this.unit_type,
        nb_colis = this.nb_colis, unite_par_colis = this.unite_par_colis,
        has_expiry = this.has_expiry, expiry_date = this.expiry_date
    )

    private fun PurchaseOrderEntity.toOrder(items: List<PurchaseOrderItem>, supplierName: String) = PurchaseOrder(
        id = this.id, date = this.date, total = this.total, status = this.status,
        note = this.note, supplier_id = this.supplier_id, supplier_name = supplierName,
        items_count = items.size, created_at = this.created_at, items = items,
        montant_paye = this.montant_paye,
        supplier_image_uri = this.supplier_image_uri,
        numero = this.numero
    )

    private fun PriceHistoryEntity.toPriceHistory() = PriceHistory(
        unit_cost = this.unit_cost, date = this.date,
        created_at = this.created_at, supplier_name = this.supplier_name
    )

    private fun ChargementEntity.toChargement(items: List<ChargementItem>) = Chargement(
        id         = this.id,
        note       = this.note,
        created_at = this.created_at,
        session_id = this.session_id,
        items      = items
    )

    private fun ChargementSessionEntity.toChargementSession(chargements: List<Chargement>) = ChargementSession(
        id           = this.id,
        session_date = this.session_date,
        note         = this.note,
        created_at   = this.created_at,
        chargements  = chargements
    )

    /**
     * How a document is shown when all a screen holds is its id — a form title, a draft card, a stock
     * movement's source: "#26" for a document from before numbering, "V-6DED-000027" for a new one.
     * [sourceType] is the name stock movements use (`vente`, `purchase_order`, `retour_client`,
     * `retour_fournisseur`); anything else has no printed number and keeps "#id".
     */
    suspend fun documentLabel(sourceType: String, id: Int): String {
        val numero = when (sourceType) {
            "vente"              -> db.venteDao().getNumero(id)
            "purchase_order"     -> db.purchaseDao().getNumero(id)
            "retour_client"      -> db.retourClientDao().getNumero(id)
            "retour_fournisseur" -> db.retourFournisseurDao().getNumero(id)
            else                 -> null
        }
        return numberLabel(numero, id)
    }

    suspend fun getProducts(): List<Product> {
        return productDao.getAllProducts().map { it.toProduct() }
    }

    // Source of truth réactive : émet automatiquement à chaque écriture sur la table products,
    // quel que soit l'écran ou le repository à l'origine de la modification.
    fun observeProducts(): Flow<List<Product>> =
        productDao.observeAllProducts().map { list -> list.map { it.toProduct() } }

    suspend fun addProduct(product: Map<String, Any?>): Map<String, Any> {
        val catId = (product["category_id"] as? Number)?.toInt()

        // جلب اسم الصنف فوراً من جدول الفئات
        val catName = catId?.let { categoryDao.getCategoryById(it)?.name }

        val sousCatId = (product["sous_categorie_id"] as? Number)?.toInt()
        val sousCatName = sousCatId?.let { sousCategorieDao.getSousCategorieById(it)?.name }

        val marqueId = (product["marque_id"] as? Number)?.toInt()
        val marqueName = marqueId?.let { marqueDao.getMarqueById(it)?.name }

        val entity = ProductEntity(
            name = product["name"] as? String ?: "",
            barcode = product["barcode"] as? String,
            selling_price = (product["selling_price"] as? Number)?.toDouble() ?: 0.0,
            purchase_price = (product["purchase_price"] as? Number)?.toDouble() ?: 0.0,
            stock = (product["stock"] as? Number)?.toDouble() ?: 0.0,
            min_stock = (product["min_stock"] as? Number)?.toInt() ?: 10,
            unit_type = product["unit_type"] as? String ?: "pièce",
            packages = (product["packages"] as? Number)?.toInt() ?: 0,
            pack_size = (product["pack_size"] as? Number)?.toInt() ?: 0,
            has_expiry = (product["has_expiry"] as? Number)?.toInt() ?: 0,
            expiry_date = product["expiry_date"] as? String,
            image_uri = product["image_uri"] as? String,
            category_id = catId,
            category_name = catName, // سيتم تخزين الاسم الفعلي هنا
            supplier_id = null,
            supplier_name = null,
            sous_categorie_id = sousCatId,
            sous_categorie_name = sousCatName,
            marque_id = marqueId,
            marque_name = marqueName
        )

        // Inserted at zero whatever the map says: stock only ever comes from movements, so a product
        // created with some gets it as a "Stock initial" adjustment, in the same transaction.
        val newId = db.withTransaction {
            val id = productDao.insertProduct(entity.copy(stock = 0.0, camion_stock = 0.0))
            recordStockAdjustment(entity.copy(id = id.toInt()), entity.stock, "Stock initial")
            id
        }
        // A product created with a photo starts its gallery with that photo as the cover, so the
        // form's single-image field and the gallery never disagree about what the product looks
        // like. See adoptCoverImage.
        entity.image_uri?.let { adoptCoverImage(newId.toInt(), it) }
        return mapOf("id" to newId.toDouble(), "message" to "Product added successfully")
    }

    suspend fun updateProduct(id: Int, product: Map<String, Any?>): Map<String, Any> {
        val existing = productDao.getProductById(id) ?: return mapOf("error" to "Product not found")

        val newCatId = if (product.containsKey("category_id")) (product["category_id"] as? Number)?.toInt() else existing.category_id

        // 🔥 التعديل هنا: سنجلب اسم الصنف دائماً من قاعدة البيانات، حتى لو لم يتم تغييره، لتصحيح أي خطأ سابق
        val newCatName = newCatId?.let { categoryDao.getCategoryById(it)?.name }

        val newSousCatId = if (product.containsKey("sous_categorie_id")) (product["sous_categorie_id"] as? Number)?.toInt() else existing.sous_categorie_id
        val newSousCatName = newSousCatId?.let { sousCategorieDao.getSousCategorieById(it)?.name }

        val newMarqueId = if (product.containsKey("marque_id")) (product["marque_id"] as? Number)?.toInt() else existing.marque_id
        val newMarqueName = newMarqueId?.let { marqueDao.getMarqueById(it)?.name }

        val updatedEntity = existing.copy(
            name = product["name"] as? String ?: existing.name,
            barcode = if (product.containsKey("barcode")) product["barcode"] as? String else existing.barcode,
            selling_price = (product["selling_price"] as? Number)?.toDouble() ?: existing.selling_price,
            purchase_price = (product["purchase_price"] as? Number)?.toDouble() ?: existing.purchase_price,
            min_stock = if (product.containsKey("min_stock")) (product["min_stock"] as? Number)?.toInt() ?: existing.min_stock else existing.min_stock,
            unit_type = product["unit_type"] as? String ?: existing.unit_type,
            packages = if (product.containsKey("packages")) (product["packages"] as? Number)?.toInt() ?: existing.packages else existing.packages,
            pack_size = if (product.containsKey("pack_size")) (product["pack_size"] as? Number)?.toInt() ?: existing.pack_size else existing.pack_size,
            has_expiry = if (product.containsKey("has_expiry")) (product["has_expiry"] as? Number)?.toInt() ?: existing.has_expiry else existing.has_expiry,
            expiry_date = if (product.containsKey("expiry_date")) product["expiry_date"] as? String else existing.expiry_date,
            image_uri = if (product.containsKey("image_uri")) product["image_uri"] as? String else existing.image_uri,
            category_id = newCatId,
            category_name = newCatName, // وضع الاسم الجديد المصحح
            sous_categorie_id = newSousCatId,
            sous_categorie_name = newSousCatName,
            marque_id = newMarqueId,
            marque_name = newMarqueName
        )

        // A stock typed over is not written to the column: the difference becomes an adjustment
        // movement, and the ledger triggers bring `stock` to it. The edit form does not send one
        // today; this keeps the ledger whole for any caller that does.
        val typedStock = if (product.containsKey("stock")) (product["stock"] as? Number)?.toDouble() else null
        db.withTransaction {
            productDao.updateProduct(updatedEntity)
            if (typedStock != null) {
                val current = productDao.getProductById(id)?.stock ?: existing.stock
                recordStockAdjustment(updatedEntity, typedStock - current, "Ajustement manuel")
            }
        }
        // The edit form still carries a single image field. Picking a photo there means "this is
        // the cover", so it is adopted into the gallery rather than left to contradict it.
        if (product.containsKey("image_uri")) {
            (product["image_uri"] as? String)?.let { adoptCoverImage(id, it) }
        }
        return mapOf("message" to "Product updated successfully")
    }

    suspend fun linkProductToSupplier(supplierId: Int, productId: Int, purchasePrice: Double): Map<String, Any> {
        // جلب المنتج والمورد من القاعدة المحلية
        val product = productDao.getProductById(productId) ?: return mapOf("error" to "Product not found")
        val supplier = supplierDao.getSupplierById(supplierId)

        // تحديث المنتج بمعلومات المورد الجديد
        val updatedProduct = product.copy(
            supplier_id = supplierId,
            supplier_name = supplier?.name,
            purchase_price = purchasePrice
        )

        productDao.updateProduct(updatedProduct)
        return mapOf("message" to "Product linked to supplier successfully")
    }

    suspend fun unlinkProductFromAllSuppliers(productId: Int): Map<String, Any> {
        val product = productDao.getProductById(productId) ?: return mapOf("error" to "Product not found")

        // إزالة ارتباط المورد من المنتج
        val updatedProduct = product.copy(
            supplier_id = null,
            supplier_name = null
        )

        productDao.updateProduct(updatedProduct)
        return mapOf("message" to "Product unlinked successfully")
    }

    suspend fun deleteProduct(id: Int): Map<String, Any> {
        productDao.softDeleteProductById(id)
        return mapOf("message" to "Product deleted successfully")
    }

    // -- Product images (gallery) -------------------------------------------------
    //
    // `product_images` holds the gallery; `products.image_uri` holds the cover. Every mutation below
    // runs in one transaction and leaves two invariants true:
    //
    //   1. positions are dense and zero-based within the product, and
    //   2. `products.image_uri` equals the reference at position 0, or null when there are none.
    //
    // The second is what lets every single-image surface in the app -- the product rows, the cart
    // lines, the pickers, and the denormalised snapshots on ventes, purchase_orders, inventory_items
    // and pertes -- carry on reading one column and know nothing about galleries.

    private fun ProductImageEntity.toProductImage() = ProductImage(
        id = this.id, productId = this.product_id, ref = this.image_ref, position = this.position
    )

    /** The product's photos, cover first. Re-emits on every write to the gallery. */
    fun observeProductImages(productId: Int): Flow<List<ProductImage>> =
        db.productImageDao().observeForProduct(productId)
            .map { rows -> rows.map { it.toProductImage() } }

    suspend fun getProductImages(productId: Int): List<ProductImage> =
        db.productImageDao().getForProduct(productId).map { it.toProductImage() }

    /**
     * Appends [ref] to the product's gallery.
     *
     * Refuses past [MAX_IMAGES_PER_PRODUCT]. Adding a photo the product already has is reported
     * rather than duplicated -- with content addressing two identical photos are the same
     * reference, and a gallery showing one picture twice is never what was meant.
     */
    suspend fun addProductImage(productId: Int, ref: String): Map<String, Any> {
        if (ref.isBlank()) return mapOf("error" to "Image invalide")
        var result: Map<String, Any> = mapOf("message" to "Photo ajoutée")
        db.withTransaction {
            val dao = db.productImageDao()
            if (dao.findByRef(productId, ref) != null) {
                result = mapOf("error" to "Cette photo est déjà dans la galerie")
                return@withTransaction
            }
            val existing = dao.getForProduct(productId)
            if (existing.size >= MAX_IMAGES_PER_PRODUCT) {
                result = mapOf("error" to "Maximum " + MAX_IMAGES_PER_PRODUCT + " photos par produit")
                return@withTransaction
            }
            dao.insert(
                ProductImageEntity(
                    product_id = productId,
                    image_ref  = ref,
                    position   = existing.size,
                    created_at = java.time.Instant.now().toString()
                )
            )
            syncCover(productId)
        }
        return result
    }

    /**
     * Removes one photo and closes the gap its position left.
     *
     * Deleting the cover promotes whatever was next; deleting the last photo leaves the product
     * with none and nulls `products.image_uri`, which every caller already draws as a placeholder.
     *
     * The file itself is deliberately not deleted. It is content-addressed, so another product --
     * or a sale, or a loss, recording what this product looked like at the time -- may still point
     * at it.
     */
    suspend fun deleteProductImage(imageId: Int): Map<String, Any> {
        db.withTransaction {
            val dao = db.productImageDao()
            val image = dao.getById(imageId) ?: return@withTransaction
            dao.deleteById(imageId)
            renumber(image.product_id)
            syncCover(image.product_id)
        }
        return mapOf("message" to "Photo supprimée")
    }

    /** Moves one photo to position 0, keeping the order of the rest. */
    suspend fun setPrimaryProductImage(imageId: Int): Map<String, Any> {
        db.withTransaction {
            val dao = db.productImageDao()
            val image = dao.getById(imageId) ?: return@withTransaction
            val ordered = dao.getForProduct(image.product_id)
                .sortedBy { if (it.id == imageId) -1 else it.position }
            ordered.forEachIndexed { index, row -> dao.setPosition(row.id, index) }
            syncCover(image.product_id)
        }
        return mapOf("message" to "Photo principale mise à jour")
    }

    /**
     * Takes a reference arriving from the single-image edit form and makes it the cover.
     *
     * Already in the gallery -- promote it. Not there and there is room -- insert it at the front.
     * Not there and the gallery is full -- replace the current cover, because the form's field *is*
     * the cover, and silently ignoring the user's pick would be worse than dropping the photo it
     * replaces.
     */
    private suspend fun adoptCoverImage(productId: Int, ref: String) {
        if (ref.isBlank()) return
        db.withTransaction {
            val dao = db.productImageDao()
            val existing = dao.findByRef(productId, ref)
            if (existing != null) {
                val ordered = dao.getForProduct(productId)
                    .sortedBy { if (it.id == existing.id) -1 else it.position }
                ordered.forEachIndexed { index, row -> dao.setPosition(row.id, index) }
            } else {
                val all = dao.getForProduct(productId)
                if (all.size >= MAX_IMAGES_PER_PRODUCT) all.firstOrNull()?.let { dao.deleteById(it.id) }
                dao.insert(
                    ProductImageEntity(
                        product_id = productId,
                        image_ref  = ref,
                        position   = -1, // renumber() sorts it to the front
                        created_at = java.time.Instant.now().toString()
                    )
                )
                renumber(productId)
            }
            syncCover(productId)
        }
    }

    /** Rewrites positions to 0..n-1 in their current order. */
    private suspend fun renumber(productId: Int) {
        val dao = db.productImageDao()
        dao.getForProduct(productId).forEachIndexed { index, row ->
            if (row.position != index) dao.setPosition(row.id, index)
        }
    }

    /** Points `products.image_uri` at position 0, or null when the gallery is empty. */
    private suspend fun syncCover(productId: Int) {
        val cover = db.productImageDao().getCover(productId)?.image_ref
        val product = productDao.getProductById(productId) ?: return
        if (product.image_uri != cover) productDao.updateProduct(product.copy(image_uri = cover))
    }

    // 3. استبدل دوال الفئات الأربعة القديمة بهذه:
    suspend fun getCategories(): List<Category> {
        return categoryDao.getAllCategories().map { it.toCategory() }
    }

    suspend fun addCategory(category: Map<String, Any?>): Map<String, Any> {
        val entity = CategoryEntity(
            name = category["name"] as? String ?: "",
            sort_order = (category["sort_order"] as? Number)?.toInt() ?: 0
        )
        val newId = categoryDao.insertCategory(entity)
        return mapOf("id" to newId.toDouble(), "message" to "Category added successfully")
    }

    suspend fun updateCategory(id: Int, category: Map<String, Any?>): Map<String, Any> {
        val existing = categoryDao.getCategoryById(id) ?: return mapOf("error" to "Category not found")
        val updatedEntity = existing.copy(
            name = category["name"] as? String ?: existing.name,
            sort_order = if (category.containsKey("sort_order")) (category["sort_order"] as? Number)?.toInt() ?: existing.sort_order else existing.sort_order
        )
        categoryDao.updateCategory(updatedEntity)
        return mapOf("message" to "Category updated successfully")
    }

    suspend fun deleteCategory(id: Int): Map<String, Any> {
        categoryDao.softDeleteCategoryById(id)
        return mapOf("message" to "Category deleted successfully")
    }

    // ── Sous-catégories ──
    suspend fun getSousCategories(): List<SousCategorie> {
        return sousCategorieDao.getAllSousCategories().map { it.toSousCategorie() }
    }

    suspend fun getSousCategoriesForCategory(categoryId: Int): List<SousCategorie> {
        return sousCategorieDao.getSousCategoriesForCategory(categoryId).map { it.toSousCategorie() }
    }

    suspend fun addSousCategorie(sousCategorie: Map<String, Any?>): Map<String, Any> {
        val entity = SousCategorieEntity(
            category_id = (sousCategorie["category_id"] as Number).toInt(),
            name = sousCategorie["name"] as? String ?: "",
            sort_order = (sousCategorie["sort_order"] as? Number)?.toInt() ?: 0
        )
        val newId = sousCategorieDao.insertSousCategorie(entity)
        return mapOf("id" to newId.toDouble(), "message" to "SousCategorie added successfully")
    }

    suspend fun updateSousCategorie(id: Int, sousCategorie: Map<String, Any?>): Map<String, Any> {
        val existing = sousCategorieDao.getSousCategorieById(id) ?: return mapOf("error" to "SousCategorie not found")
        val updatedEntity = existing.copy(
            category_id = (sousCategorie["category_id"] as? Number)?.toInt() ?: existing.category_id,
            name = sousCategorie["name"] as? String ?: existing.name,
            sort_order = if (sousCategorie.containsKey("sort_order")) (sousCategorie["sort_order"] as? Number)?.toInt() ?: existing.sort_order else existing.sort_order
        )
        sousCategorieDao.updateSousCategorie(updatedEntity)
        return mapOf("message" to "SousCategorie updated successfully")
    }

    suspend fun deleteSousCategorie(id: Int): Map<String, Any> {
        sousCategorieDao.softDeleteSousCategorieById(id)
        return mapOf("message" to "SousCategorie deleted successfully")
    }

    // ── Marques ──
    suspend fun getMarques(): List<Marque> {
        return marqueDao.getAllMarques().map { it.toMarque() }
    }

    suspend fun addMarque(marque: Map<String, Any?>): Map<String, Any> {
        val entity = MarqueEntity(
            name = marque["name"] as? String ?: "",
            sort_order = (marque["sort_order"] as? Number)?.toInt() ?: 0
        )
        val newId = marqueDao.insertMarque(entity)
        return mapOf("id" to newId.toDouble(), "message" to "Marque added successfully")
    }

    suspend fun updateMarque(id: Int, marque: Map<String, Any?>): Map<String, Any> {
        val existing = marqueDao.getMarqueById(id) ?: return mapOf("error" to "Marque not found")
        val updatedEntity = existing.copy(
            name = marque["name"] as? String ?: existing.name,
            sort_order = if (marque.containsKey("sort_order")) (marque["sort_order"] as? Number)?.toInt() ?: existing.sort_order else existing.sort_order
        )
        marqueDao.updateMarque(updatedEntity)
        return mapOf("message" to "Marque updated successfully")
    }

    suspend fun deleteMarque(id: Int): Map<String, Any> {
        marqueDao.softDeleteMarqueById(id)
        return mapOf("message" to "Marque deleted successfully")
    }

    suspend fun getSuppliers(): List<Supplier> {
        return supplierDao.getAllSuppliers().map { it.toSupplier() }
    }

    // Source of truth réactive : émet automatiquement à chaque écriture sur la table suppliers
    // (création, modification, recalcul de solde après achat/paiement…), quel que soit l'écran.
    fun observeSuppliers(): Flow<List<Supplier>> =
        supplierDao.observeAllSuppliers().map { list -> list.map { it.toSupplier() } }

    suspend fun addSupplier(supplier: Map<String, Any?>): Map<String, Any> {
        val entity = SupplierEntity(
            name = supplier["name"] as? String ?: "",
            phone = supplier["phone"] as? String,
            address = supplier["address"] as? String,
            note = supplier["note"] as? String,
            balance = (supplier["balance"] as? Number)?.toDouble() ?: 0.0,
            initial_balance = (supplier["initial_balance"] as? Number)?.toDouble() ?: 0.0,
            latitude = (supplier["latitude"] as? Number)?.toDouble(),
            longitude = (supplier["longitude"] as? Number)?.toDouble(),
            wilaya_name = supplier["wilaya_name"] as? String,
            commune_name = supplier["commune_name"] as? String,
            image_uri = supplier["image_uri"] as? String
        )
        val newId = supplierDao.insertSupplier(entity)
        return mapOf("id" to newId.toDouble(), "message" to "Supplier added successfully")
    }

    suspend fun updateSupplier(id: Int, supplier: Map<String, Any?>): Map<String, Any> {
        val existing = supplierDao.getSupplierById(id) ?: return mapOf("error" to "Supplier not found")
        val updatedEntity = existing.copy(
            name = supplier["name"] as? String ?: existing.name,
            phone = if (supplier.containsKey("phone")) supplier["phone"] as? String else existing.phone,
            address = if (supplier.containsKey("address")) supplier["address"] as? String else existing.address,
            note = if (supplier.containsKey("note")) supplier["note"] as? String else existing.note,
            initial_balance = if (supplier.containsKey("initial_balance")) (supplier["initial_balance"] as? Number)?.toDouble() ?: existing.initial_balance else existing.initial_balance,
            latitude = if (supplier.containsKey("latitude")) (supplier["latitude"] as? Number)?.toDouble() else existing.latitude,
            longitude = if (supplier.containsKey("longitude")) (supplier["longitude"] as? Number)?.toDouble() else existing.longitude,
            wilaya_name = if (supplier.containsKey("wilaya_name")) supplier["wilaya_name"] as? String else existing.wilaya_name,
            commune_name = if (supplier.containsKey("commune_name")) supplier["commune_name"] as? String else existing.commune_name,
            image_uri = if (supplier.containsKey("image_uri")) supplier["image_uri"] as? String else existing.image_uri
        )
        // One transaction, like every other write that touches a balance: the edit and the
        // recompute that follows it land together, or neither does.
        db.withTransaction {
            supplierDao.updateSupplier(updatedEntity)
            supplierDao.recomputeBalance(id)
        }
        return mapOf("message" to "Supplier updated successfully")
    }

    /** A supplier goes to the bin only once its account is settled: a hidden debt or advance is not one. */
    suspend fun deleteSupplier(id: Int): Map<String, Any> {
        db.withTransaction {
            val supplier = supplierDao.getSupplierById(id) ?: return@withTransaction
            requireSettled(supplier.balance)
            supplierDao.softDeleteSupplierById(id)
        }
        return mapOf("message" to "Supplier deleted successfully")
    }

    private fun requireSettled(balance: Double) {
        if (kotlin.math.abs(balance) >= 0.005) {
            throw IllegalStateException("Impossible de supprimer : le solde n'est pas nul (${"%.2f".format(balance)} DA).")
        }
    }


    // ✅ الكود الجديد الذي يقرأ من Room ويحول البيانات لـ SupplierProduct:
    suspend fun getSupplierProducts(id: Int): List<SupplierProduct> {
        return productDao.getProductsBySupplier(id).map { entity ->
            SupplierProduct(
                id = entity.id,
                name = entity.name,
                stock = entity.stock,
                unit_type = entity.unit_type,
                purchase_price = entity.purchase_price,
                is_default = 1 // نعتبره 1 لأننا ربطناه محلياً بهذا المورد
            )
        }
    }

// ── Purchases (محلي بالكامل) ──

    suspend fun getPurchaseOrders(): List<PurchaseOrder> {
        return db.purchaseDao().getAllOrders().map { order ->
            val items = db.purchaseDao().getItemsForOrder(order.id).map { it.toItem() }
            val supplierName = order.supplier_name ?: supplierDao.getSupplierById(order.supplier_id)?.name ?: "Fournisseur supprimé"
            order.toOrder(items, supplierName)
        }
    }

    suspend fun getPurchaseOrder(id: Int): PurchaseOrder {
        val order = db.purchaseDao().getOrderById(id)
            ?: throw IllegalStateException("Bon introuvable: $id")
        val items = db.purchaseDao().getItemsForOrder(id).map { it.toItem() }
        val supplierName = order.supplier_name ?: supplierDao.getSupplierById(order.supplier_id)?.name ?: "Fournisseur supprimé"
        return order.toOrder(items, supplierName)
    }

    /**
     * [draftId] — the Brouillon this bon was composed in, if any. It is deleted as the final
     * statement *inside* the transaction, so the draft outlives any failure: if the insert, the
     * price history or the balance recalculation throws, the rollback takes the delete with it and
     * the user's work is still there. "Deleted only after the purchase commits" is enforced by
     * SQLite rather than by callback ordering.
     */
    suspend fun createPurchaseOrder(order: Map<String, Any?>, draftId: Int? = null): Map<String, Any> {
        db.withTransaction {
            val supplierId = (order["supplier_id"] as Number).toInt()
            val date = order["date"] as? String ?: java.time.LocalDate.now().toString()
            val note = order["note"] as? String
            val montantPaye = (order["montant_paye"] as? Number)?.toDouble() ?: 0.0
            @Suppress("UNCHECKED_CAST")
            val itemsList = order["items"] as List<Map<String, Any?>>

            val now = java.time.Instant.now().toString()
            val total = itemsList.sumOf { (it["quantity"] as Number).toDouble() * (it["unit_cost"] as Number).toDouble() }
            requireDocumentAmounts(itemsList, "unit_cost", montantPaye, total)
            val supplierEntity = supplierDao.getSupplierById(supplierId)
            val supplierName   = supplierEntity?.name
            val supplierImageUri = supplierEntity?.image_uri

            val orderId = db.purchaseDao().insertOrder(
                PurchaseOrderEntity(
                    supplier_id = supplierId, date = date, total = total, status = "pending",
                    note = note, montant_paye = montantPaye, created_at = now,
                    supplier_name = supplierName,
                    supplier_image_uri = supplierImageUri  // ← جديد
                )
            ).toInt()

            val itemEntities = mutableListOf<PurchaseOrderItemEntity>()
            val historyEntities = mutableListOf<PriceHistoryEntity>()

            for (map in itemsList) {
                val productId = (map["product_id"] as Number).toInt()
                val quantity = (map["quantity"] as Number).toDouble()
                val unitCost = (map["unit_cost"] as Number).toDouble()
                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Produit introuvable: $productId")

                itemEntities.add(
                    PurchaseOrderItemEntity(
                        purchase_order_id = orderId, product_id = productId, quantity = quantity,
                        unit_cost = unitCost, total_cost = quantity * unitCost,
                        product_name = product.name, unit_type = product.unit_type,
                        nb_colis = (map["nb_colis"] as? Number)?.toDouble() ?: 1.0,
                        unite_par_colis = (map["unite_par_colis"] as? Number)?.toInt() ?: 1,
                        has_expiry = (map["has_expiry"] as? Boolean) ?: false,
                        expiry_date = map["expiry_date"] as? String
                    )
                )
                historyEntities.add(
                    PriceHistoryEntity(
                        product_id = productId, unit_cost = unitCost,
                        date = date, created_at = now, supplier_name = supplierName ?: ""
                    )
                )
            }
            db.purchaseDao().insertItems(itemEntities)
            db.purchaseDao().insertPriceHistory(historyEntities)
            supplierDao.recomputeBalance(supplierId)
            draftId?.let { db.purchaseDraftDao().deleteById(it) }
        }
        return mapOf("message" to "Bon créé avec succès")
    }

    suspend fun receivePurchaseOrder(id: Int, userName: String? = null): Map<String, Any> {
        db.withTransaction {
            val order = db.purchaseDao().getOrderById(id)
                ?: throw IllegalStateException("Bon introuvable: $id")
            if (order.status == "received") return@withTransaction

            val supplierName = order.supplier_name ?: supplierDao.getSupplierById(order.supplier_id)?.name ?: "Fournisseur supprimé"
            val now = java.time.Instant.now().toString()
            val items = db.purchaseDao().getItemsForOrder(id)
            val movementEntities = mutableListOf<StockMovementEntity>()

            for (item in items) {
                // A product moved to the bin since the bon was made still receives its goods — the stock is real
                // and comes back with the product. Only a product that no longer exists at all stops the receipt,
                // rather than marking the bon received with its goods never counted.
                val product = productDao.getProductByIdIncludingBin(item.product_id)
                    ?: throw IllegalStateException("Produit introuvable pour « ${item.product_name} » : le bon ne peut pas être reçu.")

                // ── Date d'expiration : on garde toujours la plus proche (la plus urgente à vendre) ──
                val shouldUpdateExpiry = item.has_expiry && item.expiry_date != null && (
                        product.has_expiry == 0 ||
                                product.expiry_date.isNullOrEmpty() ||
                                item.expiry_date < product.expiry_date
                        )
                if (shouldUpdateExpiry) {
                    productDao.updateProduct(product.copy(has_expiry = 1, expiry_date = item.expiry_date))
                }
                // The stock itself comes from the movement below.

                movementEntities += StockMovementEntity(
                    product_id   = item.product_id,
                    product_name = item.product_name,
                    type         = "achat",
                    direction    = "entree",
                    quantity     = item.quantity,
                    emplacement  = "depot",
                    source_label = supplierName,
                    source_type  = "purchase_order",
                    source_id    = id,
                    unit_price   = item.unit_cost,
                    total_value  = item.total_cost,
                    user_name    = userName,
                    note         = order.note,
                    created_at   = now
                )
            }
            db.stockMovementDao().insertAll(movementEntities)
            db.purchaseDao().updateOrderStatus(id, "received")
        }
        return mapOf("message" to "Bon marqué comme reçu")
    }

    /**
     * [draftId] — the Brouillon this bon was composed in, if any. It is deleted as the final
     * statement *inside* the transaction, so the draft outlives any failure: if the insert, the
     * price history or the balance recalculation throws, the rollback takes the delete with it and
     * the user's work is still there. "Deleted only after the purchase commits" is enforced by
     * SQLite rather than by callback ordering.
     */
    suspend fun updatePurchaseOrder(id: Int, order: Map<String, Any?>, draftId: Int? = null): Map<String, Any> {
        db.withTransaction {
            val note = order["note"] as? String
            val montantPaye = (order["montant_paye"] as? Number)?.toDouble() ?: 0.0
            @Suppress("UNCHECKED_CAST")
            val itemsList = order["items"] as List<Map<String, Any?>>

            val existing = db.purchaseDao().getOrderById(id)
                ?: throw IllegalStateException("Bon introuvable: $id")
            val supplierName = existing.supplier_name ?: supplierDao.getSupplierById(existing.supplier_id)?.name ?: "Fournisseur supprimé"

            val total = itemsList.sumOf { (it["quantity"] as Number).toDouble() * (it["unit_cost"] as Number).toDouble() }
            requireDocumentAmounts(itemsList, "unit_cost", montantPaye, total)
            db.purchaseDao().deleteItemsForOrder(id)

            val now = java.time.Instant.now().toString()
            val itemEntities = mutableListOf<PurchaseOrderItemEntity>()
            val historyEntities = mutableListOf<PriceHistoryEntity>()

            for (map in itemsList) {
                val productId = (map["product_id"] as Number).toInt()
                val quantity = (map["quantity"] as Number).toDouble()
                val unitCost = (map["unit_cost"] as Number).toDouble()
                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Produit introuvable: $productId")

                itemEntities.add(
                    PurchaseOrderItemEntity(
                        purchase_order_id = id, product_id = productId, quantity = quantity,
                        unit_cost = unitCost, total_cost = quantity * unitCost,
                        product_name = product.name, unit_type = product.unit_type,
                        nb_colis = (map["nb_colis"] as? Number)?.toDouble() ?: 1.0,
                        unite_par_colis = (map["unite_par_colis"] as? Number)?.toInt() ?: 1,
                        has_expiry = (map["has_expiry"] as? Boolean) ?: false,
                        expiry_date = map["expiry_date"] as? String
                    )
                )
                historyEntities.add(
                    PriceHistoryEntity(
                        product_id = productId, unit_cost = unitCost,
                        date = existing.date, created_at = now, supplier_name = supplierName
                    )
                )
            }

            db.purchaseDao().insertItems(itemEntities)
            db.purchaseDao().insertPriceHistory(historyEntities)
            db.purchaseDao().updateOrderFields(id, note, montantPaye, total)

            supplierDao.recomputeBalance(existing.supplier_id)
            draftId?.let { db.purchaseDraftDao().deleteById(it) }
        }
        return mapOf("message" to "Bon mis à jour avec succès")
    }

    suspend fun reopenPurchaseOrder(id: Int): Map<String, Any> {
        db.withTransaction {
            val order = db.purchaseDao().getOrderById(id)
                ?: throw IllegalStateException("Bon introuvable: $id")
            if (order.status != "received") return@withTransaction

            db.purchaseDao().updateOrderStatus(id, "pending")
            // Removing the reception's movements takes their quantities back out of stock.
            db.stockMovementDao().deleteBySource("purchase_order", id)
        }
        return mapOf("message" to "Bon rouvert avec succès")
    }

    suspend fun deletePurchaseOrder(id: Int): Map<String, Any> {
        db.withTransaction {
            val order = db.purchaseDao().getOrderById(id)
                ?: throw IllegalStateException("Bon introuvable: $id")

            // A pending bon has no movements; a received one's take their quantities back out of stock.
            db.stockMovementDao().deleteBySource("purchase_order", id)
            db.purchaseDao().deleteItemsForOrder(id)
            db.purchaseDao().deleteOrderById(id)
            supplierDao.recomputeBalance(order.supplier_id)
        }
        return mapOf("message" to "Bon supprimé avec succès")
    }

    suspend fun getProductPriceHistory(id: Int): List<PriceHistory> {
        return db.purchaseDao().getPriceHistoryForProduct(id).map { it.toPriceHistory() }
    }

// ── Ventes (محلي بالكامل) ──

    // One query for the whole list. This used to run an item count and a client lookup for every
    // sale — the lookup hidden inside toVente() — so the Ventes screen cost 1 + 2V queries every
    // time it opened or a sale was saved or deleted.
    suspend fun getVentes(clientId: Int? = null): List<Vente> =
        db.venteDao().getVentesWithDetails(clientId).map { row ->
            row.vente.toVenteWith(
                clientName = row.live_client_name ?: "",
                itemsCount = row.items_count,
                items      = null
            )
        }

    suspend fun getVente(id: Int): Vente {
        val entity = db.venteDao().getVenteById(id)
            ?: throw IllegalStateException("Vente introuvable: $id")
        val items = db.venteDao().getItemsForVente(id).map { it.toItem() }
        return entity.toVente(items)
    }

    /**
     * [draftId] — the Brouillon this vente was composed in, if any. It is deleted as the final
     * statement *inside* the transaction, so the draft outlives any failure: if the insert, the
     * stock deltas or the balance recalculation throw, the whole thing rolls back and the user's
     * unsaved work is still there to resume.
     */
    /**
     * @param draftId          a `vente_drafts` row to delete on success — a Dépôt Vente draft.
     * @param tourneeDraftId   a `tournee_vente_drafts` row to delete on success — a van-sale draft.
     *
     * Two parameters rather than one because they name rows in two different tables, and a single
     * id would have to be told which. At most one is ever set: a sale is composed in one form.
     */
    // ── What a document must satisfy before it is written ──

    /**
     * A sale from the camion cannot take more of a product than the camion holds — all its lines together,
     * so two lines of the same product are checked as one. The product may be in the bin: a sale being
     * edited already names it.
     */
    private suspend fun requireCamionStock(items: List<Map<String, Any?>>) {
        val wanted = items.groupBy { (it["product_id"] as Number).toInt() }
            .mapValues { (_, lines) -> lines.sumOf { (it["quantity"] as Number).toDouble() } }
        for ((productId, quantity) in wanted) {
            val product = productDao.getProductByIdIncludingBin(productId)
                ?: throw IllegalStateException("Produit introuvable: $productId")
            if (quantity > product.camion_stock + AMOUNT_EPSILON) {
                throw IllegalStateException(
                    "Stock insuffisant pour ${product.name} : disponible ${product.camion_stock}, demandé $quantity"
                )
            }
        }
    }

    /**
     * Lines with a quantity above zero and a price that is not negative, and a paid amount that is not negative.
     * A paid amount above the total is allowed: the excess is an advance, and the party's solde shows it as one.
     */
    private fun requireDocumentAmounts(items: List<Map<String, Any?>>, priceKey: String, montantPaye: Double, total: Double) {
        for (line in items) {
            val quantity = (line["quantity"] as Number).toDouble()
            val price = (line[priceKey] as Number).toDouble()
            if (!(quantity > 0)) throw IllegalStateException("La quantité doit être supérieure à zéro.")
            if (!(price >= 0)) throw IllegalStateException("Le prix ne peut pas être négatif.")
        }
        if (!(montantPaye >= 0)) throw IllegalStateException("Le montant payé ne peut pas être négatif.")
    }

    private fun requirePayment(amount: Double) {
        if (!(amount > 0)) throw IllegalStateException("Le montant doit être supérieur à zéro.")
    }

    suspend fun createVente(
        clientId: Int, tourneeId: Int?, source: String,
        items: List<Map<String, Any?>>, note: String?, montantPaye: Double,
        userName: String? = null,
        draftId: Int? = null,
        tourneeDraftId: Int? = null
    ): Map<String, Any> {
        db.withTransaction {
            val total = items.sumOf { (it["quantity"] as Number).toDouble() * (it["unit_price"] as Number).toDouble() }
            val now = java.time.Instant.now().toString()
            requireDocumentAmounts(items, "unit_price", montantPaye, total)

            // ── تحقق مسبق: فقط للبيع من الشاحنة (Tournée) — Dépôt يسمح بمخزون سالب ──
            if (source == "camion") requireCamionStock(items)

            val clientEntity   = clientDao.getClientById(clientId)
            val clientName     = clientEntity?.name ?: "Client inconnu"
            val clientImageUri = clientEntity?.image_uri

            val venteId = db.venteDao().insertVente(
                VenteEntity(
                    client_id = clientId, tournee_id = tourneeId, source = source,
                    total = total, montant_paye = montantPaye, status = "pending",
                    note = note, created_at = now, client_name = clientName,
                    client_image_uri = clientImageUri,  // ← جديد
                    user_name = userName
                )
            ).toInt()

            val movementEntities = mutableListOf<StockMovementEntity>()

            val itemEntities = items.map { map ->
                val productId = (map["product_id"] as Number).toInt()
                val quantity = (map["quantity"] as Number).toDouble()
                val unitPrice = (map["unit_price"] as Number).toDouble()
                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Produit introuvable: $productId")

                movementEntities += StockMovementEntity(
                    product_id   = productId,
                    product_name = product.name,
                    type         = "vente",
                    direction    = "sortie",
                    quantity     = quantity,
                    emplacement  = source,
                    source_label = clientName,
                    source_type  = "vente",
                    source_id    = venteId,
                    unit_price   = unitPrice,
                    total_value  = quantity * unitPrice,
                    user_name    = userName,
                    note         = note,
                    created_at   = now
                )

                VenteItemEntity(
                    vente_id = venteId, product_id = productId, product_name = product.name,
                    unit_type = product.unit_type, quantity = quantity,
                    unit_price = unitPrice, total_price = quantity * unitPrice
                )
            }
            db.venteDao().insertItems(itemEntities)
            db.stockMovementDao().insertAll(movementEntities)
            clientDao.recomputeBalance(clientId)
            draftId?.let { db.venteDraftDao().deleteById(it) }
            // Inside the same transaction as the sale, for the same reason: if anything
            // above throws, the draft is still there to resume.
            tourneeDraftId?.let { db.tourneeVenteDraftDao().deleteById(it) }
        }
        return mapOf("message" to "Vente créée avec succès")
    }

    /**
     * [draftId] — the Brouillon this vente was composed in, if any. It is deleted as the final
     * statement *inside* the transaction, so the draft outlives any failure: if the insert, the
     * stock deltas or the balance recalculation throw, the whole thing rolls back and the user's
     * unsaved work is still there to resume.
     */
    suspend fun updateVente(
        id: Int, clientId: Int, items: List<Map<String, Any?>>,
        note: String?, montantPaye: Double,
        userName: String? = null,
        draftId: Int? = null
    ): Map<String, Any> {
        db.withTransaction {
            val existing = db.venteDao().getVenteById(id)
                ?: throw IllegalStateException("Vente introuvable: $id")

            // عكس تأثير العناصر القديمة على المخزون — removing the sale's movements puts their
            // quantities back, so the camion check below sees the stock as it was before this sale.
            db.stockMovementDao().deleteBySource("vente", id)
            // ── تحقق مسبق: فقط للبيع من الشاحنة (Tournée) ──
            if (existing.source == "camion") requireCamionStock(items)
            db.venteDao().deleteItemsForVente(id)

            val now = java.time.Instant.now().toString()
            val clientName = clientDao.getClientById(clientId)?.name ?: "Client inconnu"
            val movementEntities = mutableListOf<StockMovementEntity>()

            val total = items.sumOf { (it["quantity"] as Number).toDouble() * (it["unit_price"] as Number).toDouble() }
            requireDocumentAmounts(items, "unit_price", montantPaye, total)
            val itemEntities = items.map { map ->
                val productId = (map["product_id"] as Number).toInt()
                val quantity = (map["quantity"] as Number).toDouble()
                val unitPrice = (map["unit_price"] as Number).toDouble()
                // The sale already names the product: it stays editable after the product went to the bin.
                val product = productDao.getProductByIdIncludingBin(productId)
                    ?: throw IllegalStateException("Produit introuvable: $productId")

                movementEntities += StockMovementEntity(
                    product_id   = productId,
                    product_name = product.name,
                    type         = "vente",
                    direction    = "sortie",
                    quantity     = quantity,
                    emplacement  = existing.source,
                    source_label = clientName,
                    source_type  = "vente",
                    source_id    = id,
                    unit_price   = unitPrice,
                    total_value  = quantity * unitPrice,
                    user_name    = userName,
                    note         = note,
                    created_at   = now
                )

                VenteItemEntity(
                    vente_id = id, product_id = productId, product_name = product.name,
                    unit_type = product.unit_type, quantity = quantity,
                    unit_price = unitPrice, total_price = quantity * unitPrice
                )
            }
            db.venteDao().insertItems(itemEntities)
            db.stockMovementDao().insertAll(movementEntities)
            db.venteDao().updateVenteFields(id, note, montantPaye, total, userName)
            clientDao.recomputeBalance(clientId)
            draftId?.let { db.venteDraftDao().deleteById(it) }
        }
        return mapOf("message" to "Vente mise à jour avec succès")
    }

    suspend fun deliverVente(id: Int): Map<String, Any> {
        db.venteDao().updateVenteStatus(id, "delivered")
        return mapOf("message" to "Vente marquée comme livrée")
    }

    suspend fun deleteVente(id: Int): Map<String, Any> {
        db.withTransaction {
            val existing = db.venteDao().getVenteById(id)
                ?: throw IllegalStateException("Vente introuvable: $id")
            // Removing the sale's movements puts its quantities back in stock.
            db.stockMovementDao().deleteBySource("vente", id)
            db.venteDao().deleteItemsForVente(id)
            db.venteDao().deleteVenteById(id)
            clientDao.recomputeBalance(existing.client_id)
        }
        return mapOf("message" to "Vente supprimée avec succès")
    }

    // Supplier transactions
    // ── Supplier transactions (محلي بالكامل) ──

    /**
     * A supplier's detail-screen ledger: figures over all its purchase orders and payments, how many
     * entries there are, and only the [limit] latest.
     *
     * This replaces a function that loaded every purchase order in the database, filtered them down
     * to this supplier in Kotlin, added all its payments and sorted the lot, to show four rows and two
     * sums. The figures now come from COUNT/SUM queries and the rows from the first page of the
     * ledger's own queries. What the screen shows is unchanged: Total facturé is the orders' totals,
     * Total payé their amounts paid plus the payments; the count includes the "Solde initial" entry,
     * which the list always carried, even at 0; and the latest entries are ordered as before —
     * newest created first, orders, then payments, then the opening balance at the same instant.
     */
    suspend fun getSupplierLedgerPreview(id: Int, limit: Int): SupplierLedgerPreview = withContext(Dispatchers.Default) {
        val supplier = supplierDao.getSupplierById(id) ?: return@withContext SupplierLedgerPreview()
        val orders   = db.purchaseDao().getInvoiceTotalsForSupplier(id)
        val payments = db.supplierPaymentDao().getPaymentTotalsForSupplier(id)

        val factureTx = db.purchaseDao().pageOrdersForSupplier(id, null, "", "TOUTES", limit)
            .map { order ->
                SupplierTransaction(
                    type = "facture", id = order.id, amount = order.total,
                    montant_paye = order.montant_paye, status = order.status,
                    note = order.note, created_at = order.created_at, numero = order.numero
                )
            }

        val paiementTx = db.supplierPaymentDao().pagePaymentsForSupplier(id, null, "", limit)
            .map { payment ->
                SupplierTransaction(
                    type = "paiement", id = payment.id, amount = payment.amount,
                    montant_paye = null, status = null,
                    note = payment.note, created_at = payment.created_at
                )
            }

        val soldeInitialTx = listOf(
            SupplierTransaction(
                type = "solde_initial", id = supplier.id, amount = supplier.initial_balance,
                montant_paye = null, status = null, note = null, created_at = supplier.created_at
            )
        )

        SupplierLedgerPreview(
            totalFacture = orders.total,
            totalPaye    = orders.paid + payments.total,
            count        = orders.count + payments.count + 1,
            // Each part is newest first already, so the [limit] latest of the whole history are among them.
            latest       = (factureTx + paiementTx + soldeInitialTx).sortedByDescending { it.created_at }.take(limit)
        )
    }

    suspend fun countSupplierLedger(supplierId: Int, filter: AchatFilter, search: String): Int {
        val statusFilter = if (filter == AchatFilter.VERSEMENT) "TOUTES" else filter.name
        val ordersCount = if (filter != AchatFilter.VERSEMENT)
            db.purchaseDao().countOrdersForSupplier(supplierId, search, statusFilter) else 0
        val paiementsCount = if (filter == AchatFilter.TOUTES || filter == AchatFilter.VERSEMENT)
            db.supplierPaymentDao().countPaymentsForSupplier(supplierId, search) else 0
        val soldeInitialCount = if (filter == AchatFilter.TOUTES && search.isBlank()) {
            val supplier = supplierDao.getSupplierById(supplierId)
            if (supplier != null && supplier.initial_balance != 0.0) 1 else 0
        } else 0
        return ordersCount + paiementsCount + soldeInitialCount
    }

    fun getSupplierLedgerPaged(
        supplierId: Int,
        filter: AchatFilter,
        search: String
    ): kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<SupplierTransaction>> =
        com.distrigo.app.core.paging.pagedFlow {
            com.distrigo.app.data.local.paging.SupplierLedgerPagingSource(
                purchaseDao = db.purchaseDao(),
                paymentDao = db.supplierPaymentDao(),
                supplierDao = supplierDao,
                supplierId = supplierId,
                filter = filter,
                search = search
            )
        }


    suspend fun addSupplierPayment(id: Int, amount: Double, note: String?): Map<String, Any> {
        requirePayment(amount)
        db.withTransaction {
            db.supplierPaymentDao().insertPayment(
                SupplierPaymentEntity(
                    supplier_id = id, amount = amount, note = note,
                    created_at = java.time.Instant.now().toString()
                )
            )
            supplierDao.recomputeBalance(id)
        }
        return mapOf("message" to "Paiement enregistré")
    }

    suspend fun deleteSupplierPayment(supplierId: Int, paymentId: Int): Map<String, Any> {
        db.withTransaction {
            db.supplierPaymentDao().deletePaymentById(paymentId)
            supplierDao.recomputeBalance(supplierId)
        }
        return mapOf("message" to "Paiement supprimé")
    }

    suspend fun updateSupplierPayment(supplierId: Int, paymentId: Int, amount: Double): Map<String, Any> {
        requirePayment(amount)
        db.withTransaction {
            db.supplierPaymentDao().updatePaymentAmount(paymentId, amount)
            supplierDao.recomputeBalance(supplierId)
        }
        return mapOf("message" to "Paiement mis à jour")
    }

// ── Clients (محلي بالكامل للبيانات الأساسية) ──

    /** The wilaya to prefill when adding a client — see [ClientDao.getMostCommonWilaya]. */
    suspend fun getMostCommonClientWilaya(): String? = clientDao.getMostCommonWilaya()

    suspend fun getClients(): List<Client> {
        return clientDao.getAllClients().map { it.toClient() }
    }

    // Source of truth réactive : émet automatiquement à chaque écriture sur la table clients
    // (création, modification, recalcul de solde après vente/paiement…), quel que soit l'écran.
    fun observeClients(): Flow<List<Client>> =
        clientDao.observeAllClients().map { list -> list.map { it.toClient() } }

    suspend fun addClient(client: Map<String, Any?>): Map<String, Any> {
        val entity = ClientEntity(
            name = client["name"] as? String ?: "",
            phone = client["phone"] as? String,
            wilaya_name = client["wilaya_name"] as? String,
            commune_name = client["commune_name"] as? String,
            secteur_id = (client["secteur_id"] as? Number)?.toInt(),
            secteur_name = client["secteur_name"] as? String,
            address = client["address"] as? String,
            note = client["note"] as? String,
            balance = (client["balance"] as? Number)?.toDouble() ?: 0.0,
            customer_type = client["customer_type"] as? String ?: "retail",
            image_uri = client["image_uri"] as? String,
            latitude = (client["latitude"] as? Number)?.toDouble(),
            longitude = (client["longitude"] as? Number)?.toDouble()
        )
        val newId = clientDao.insertClient(entity)
        return mapOf("id" to newId.toDouble(), "message" to "Client added successfully")
    }

    suspend fun updateClient(id: Int, client: Map<String, Any?>): Map<String, Any> {
        val existing = clientDao.getClientById(id) ?: return mapOf("error" to "Client not found")
        val updatedEntity = existing.copy(
            name = client["name"] as? String ?: existing.name,
            phone = if (client.containsKey("phone")) client["phone"] as? String else existing.phone,
            wilaya_name = if (client.containsKey("wilaya_name")) client["wilaya_name"] as? String else existing.wilaya_name,
            commune_name = if (client.containsKey("commune_name")) client["commune_name"] as? String else existing.commune_name,
            secteur_id = if (client.containsKey("secteur_id")) (client["secteur_id"] as? Number)?.toInt() else existing.secteur_id,
            secteur_name = if (client.containsKey("secteur_name")) client["secteur_name"] as? String else existing.secteur_name,
            address = if (client.containsKey("address")) client["address"] as? String else existing.address,
            note = if (client.containsKey("note")) client["note"] as? String else existing.note,
            customer_type = client["customer_type"] as? String ?: existing.customer_type,
            image_uri = if (client.containsKey("image_uri")) client["image_uri"] as? String else existing.image_uri,
            latitude = if (client.containsKey("latitude")) (client["latitude"] as? Number)?.toDouble() else existing.latitude,
            longitude = if (client.containsKey("longitude")) (client["longitude"] as? Number)?.toDouble() else existing.longitude
        )
        clientDao.updateClient(updatedEntity)
        return mapOf("message" to "Client updated successfully")
    }

    /** A client goes to the bin only once its account is settled: a hidden debt or advance is not one. */
    suspend fun deleteClient(id: Int): Map<String, Any> {
        db.withTransaction {
            val client = clientDao.getClientById(id) ?: return@withTransaction
            requireSettled(client.balance)
            clientDao.softDeleteClientById(id)
        }
        return mapOf("message" to "Client deleted successfully")
    }

// ── Chargements (محلي بالكامل عبر Room) ──

    suspend fun getChargements(): List<Chargement> {
        return db.chargementDao().getAllChargements().map { entity ->
            val items = db.chargementDao().getItemsForChargement(entity.id).map { it.toChargementItem() }
            entity.toChargement(items)
        }
    }

    suspend fun getChargement(id: Int): Chargement {
        val entity = db.chargementDao().getChargementById(id)
            ?: throw IllegalStateException("Chargement introuvable: $id")
        val items = db.chargementDao().getItemsForChargement(entity.id).map { it.toChargementItem() }
        return entity.toChargement(items)
    }

    /**
     * @param draftId a `chargement_drafts` row to delete on success — either a listed Brouillon or
     *   the single-product card's private editing state, since both live in that table and both
     *   are finalised the same way.
     */
    suspend fun createChargement(
        note: String?,
        items: List<Map<String, Any?>>,
        userName: String? = null,
        draftId: Int? = null
    ): Map<String, Any> {
        db.withTransaction {
            val today = java.time.LocalDate.now().toString()
            val now   = java.time.Instant.now().toString()

            val sessionId = db.chargementDao().getSessionByDate(today)?.id
                ?: db.chargementDao().insertSession(
                    ChargementSessionEntity(session_date = today, note = null, created_at = now)
                ).toInt()

            val chargementId = db.chargementDao().insertChargement(
                ChargementEntity(session_id = sessionId, note = note, created_at = now)
            ).toInt()
            val itemEntities = mutableListOf<ChargementItemEntity>()

            for (map in items) {
                val productId = map["product_id"] as Int
                val quantity  = (map["quantity"] as Number).toDouble()
                val direction = map["direction"] as String

                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Produit introuvable: $productId")

                // Chargement = transfert interne pur (dépôt ↔ camion) : ne touche jamais au total (stock)
                // Ne génère volontairement aucun StockMovementEntity : ce n'est pas un mouvement
                // du stock global, seulement une répartition interne dépôt ↔ camion.
                // The line itself is the transfer's record: inserting it moves `camion_stock`
                // (see StockLedger.kt).
                itemEntities.add(
                    ChargementItemEntity(
                        chargement_id = chargementId,
                        product_id    = productId,
                        quantity      = quantity,
                        direction     = direction,
                        product_name  = product.name,
                        unit_type     = product.unit_type
                    )
                )
            }
            db.chargementDao().insertItems(itemEntities)
            // Last, and inside the transaction: if anything above throws, the whole thing rolls
            // back and the draft is still there to resume.
            draftId?.let { db.chargementDraftDao().deleteById(it) }
        }
        return mapOf("message" to "Chargement créé avec succès")
    }
    suspend fun deleteChargement(id: Int): Map<String, Any> {
        db.withTransaction {
            // عكس التأثير: removing the transfer lines returns their quantities to where they came from.
            db.chargementDao().deleteItemsForChargement(id)
            db.chargementDao().deleteChargementById(id)
        }
        return mapOf("message" to "Chargement supprimé avec succès")
    }

    suspend fun getChargementSessions(): List<ChargementSession> {
        return db.chargementDao().getAllSessions().map { session ->
            val chargements = db.chargementDao().getChargementsBySession(session.id).map { c ->
                val items = db.chargementDao().getItemsForChargement(c.id).map { it.toChargementItem() }
                c.toChargement(items)
            }
            session.toChargementSession(chargements)
        }
    }

    suspend fun getChargementSession(id: Int): ChargementSession {
        val session = db.chargementDao().getSessionById(id)
            ?: throw IllegalStateException("Session introuvable: $id")
        val chargements = db.chargementDao().getChargementsBySession(session.id).map { c ->
            val items = db.chargementDao().getItemsForChargement(c.id).map { it.toChargementItem() }
            c.toChargement(items)
        }
        return session.toChargementSession(chargements)
    }

    suspend fun updateChargementSessionNote(id: Int, note: String?): Map<String, Any> {
        db.chargementDao().updateSessionNote(id, note)
        return mapOf("message" to "Note mise à jour")
    }

    // ── Mouvements de stock (lecture) ──
    private fun StockMovementEntity.toStockMovement() = StockMovement(
        id = this.id, product_id = this.product_id, product_name = this.product_name,
        type = this.type, direction = this.direction, quantity = this.quantity,
        emplacement = this.emplacement, source_label = this.source_label,
        source_type = this.source_type, source_id = this.source_id,
        unit_price = this.unit_price, total_value = this.total_value,
        user_name = this.user_name, note = this.note, created_at = this.created_at
    )

    suspend fun getMovementsForProduct(productId: Int): List<StockMovement> {
        return db.stockMovementDao().getMovementsForProduct(productId).map { it.toStockMovement() }
    }

    suspend fun getMovementById(id: Int): StockMovement? {
        return db.stockMovementDao().getMovementById(id)?.toStockMovement()
    }

    suspend fun getFilteredMovements(
        productId: Int? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        direction: String? = null,
        sourceLabel: String? = null
    ): List<StockMovement> {
        // The picked days are local and both included: from the first moment of [dateFrom] to the
        // first moment after [dateTo]. Compared as strings with created_at, the days themselves used
        // to leave out the whole last day, and read the others in UTC.
        val (start, end) = BusinessDates.dayRangeBounds(dateFrom, dateTo)
        return db.stockMovementDao()
            .getFilteredMovements(productId, start, end, direction, sourceLabel)
            .map { it.toStockMovement() }
    }

    suspend fun getDistinctSourcesForProduct(productId: Int): List<String> {
        return db.stockMovementDao().getDistinctSourcesForProduct(productId)
    }





// ── Client transactions (محلي بالكامل) ──

    /**
     * A client's detail-screen ledger: figures over all its sales and payments, how many there are,
     * and only the [limit] latest entries.
     *
     * This replaces a function that loaded every sale and every payment the client ever had, merged
     * and sorted them, to show four rows and two sums. The figures now come from COUNT/SUM queries and
     * the rows from the first page of the ledger's own queries. What the screen shows is unchanged:
     * Total facturé is the sales' totals, Total payé their amounts paid plus the payments, and the
     * latest entries are ordered as before — newest created first, sales before payments at the same
     * instant.
     */
    suspend fun getClientLedgerPreview(id: Int, limit: Int): ClientLedgerPreview = withContext(Dispatchers.Default) {
        val ventes   = db.venteDao().getInvoiceTotalsForClient(id)
        val payments = db.clientPaymentDao().getPaymentTotalsForClient(id)

        val venteTx = db.venteDao().pageVentesForClient(id, null, "", "TOUTES", limit).map { vente ->
            ClientTransaction(
                type = "vente", id = vente.id, amount = null,
                total = vente.total, montant_paye = vente.montant_paye,
                status = vente.status, note = vente.note, created_at = vente.created_at,
                numero = vente.numero
            )
        }

        val paiementTx = db.clientPaymentDao().pagePaymentsForClient(id, null, "", limit).map { payment ->
            ClientTransaction(
                type = "paiement", id = payment.id, amount = payment.amount,
                total = null, montant_paye = null, status = null,
                note = payment.note, created_at = payment.created_at
            )
        }

        ClientLedgerPreview(
            totalFacture = ventes.total,
            totalPaye    = ventes.paid + payments.total,
            count        = ventes.count + payments.count,
            // Each part is newest first already, so the [limit] latest of the whole history are among them.
            latest       = (venteTx + paiementTx).sortedByDescending { it.created_at }.take(limit)
        )
    }

    suspend fun countClientLedger(clientId: Int, filter: FactureFilter, search: String): Int {
        val statusFilter = if (filter == FactureFilter.VERSEMENT) "TOUTES" else filter.name
        val ventesCount = if (filter != FactureFilter.VERSEMENT)
            db.venteDao().countVentesForClient(clientId, search, statusFilter) else 0
        val paiementsCount = if (filter == FactureFilter.TOUTES || filter == FactureFilter.VERSEMENT)
            db.clientPaymentDao().countPaymentsForClient(clientId, search) else 0
        return ventesCount + paiementsCount
    }

    fun getClientLedgerPaged(
        clientId: Int,
        filter: FactureFilter,
        search: String
    ): kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<ClientTransaction>> =
        com.distrigo.app.core.paging.pagedFlow {
            com.distrigo.app.data.local.paging.ClientLedgerPagingSource(
                venteDao = db.venteDao(),
                paymentDao = db.clientPaymentDao(),
                clientId = clientId,
                filter = filter,
                search = search
            )
        }

    suspend fun addClientPayment(id: Int, amount: Double, note: String?): Map<String, Any> {
        requirePayment(amount)
        db.withTransaction {
            db.clientPaymentDao().insertPayment(
                ClientPaymentEntity(
                    client_id = id, amount = amount, note = note,
                    created_at = java.time.Instant.now().toString()
                )
            )
            clientDao.recomputeBalance(id)
        }
        return mapOf("message" to "Paiement enregistré")
    }

    suspend fun deleteClientPayment(clientId: Int, paymentId: Int): Map<String, Any> {
        db.withTransaction {
            db.clientPaymentDao().deletePaymentById(paymentId)
            clientDao.recomputeBalance(clientId)
        }
        return mapOf("message" to "Paiement supprimé")
    }

    suspend fun updateClientPayment(clientId: Int, paymentId: Int, amount: Double): Map<String, Any> {
        requirePayment(amount)
        db.withTransaction {
            db.clientPaymentDao().updatePaymentAmount(paymentId, amount)
            clientDao.recomputeBalance(clientId)
        }
        return mapOf("message" to "Paiement mis à jour")
    }

    // ── Tournées (محلي بالكامل) ──

    // Two queries for the whole list, however many tournées and sales there are: one aggregate for
    // the counters every card shows, one for all their secteurs. No sales are loaded — nothing that
    // shows the list reads them. Only getTournee, behind the detail screen, does.
    suspend fun getTournees(): List<Tournee> {
        val secteurs = db.tourneeSecteurDao().getAll().groupBy { it.tournee_id }
        return db.tourneeDao().getAllTourneeSummaries()
            .map { it.toTournee(secteurs[it.tournee.id].orEmpty()) }
    }

    // The detail screen is the one reader of a tournée's sales, so this is the one path that loads
    // them — in a single query that also brings each sale's live client name and item count. The
    // counters come from those same rows, computed exactly as they were before.
    suspend fun getTournee(id: Int): Tournee {
        val entity = db.tourneeDao().getTourneeById(id)
            ?: throw IllegalStateException("Tournée introuvable: $id")
        val rows = db.venteDao().getVentesWithDetailsForTournee(id)
        return entity.toTournee(
            secteurs     = db.tourneeSecteurDao().getForTournee(id),
            clientsCount = rows.map { it.vente.client_id }.distinct().size,
            ventesCount  = rows.size,
            totalVentes  = rows.sumOf { it.vente.total },
            resteTotal   = rows.sumOf { it.vente.total - it.vente.montant_paye },
            ventes       = rows.map {
                it.vente.toVenteWith(clientName = it.live_client_name ?: "", itemsCount = it.items_count, items = null)
            }
        )
    }

    // The banner shows the open tournée's name and nothing else. Same selection as before — the
    // existing getOpenTournee query — then its counters, without its sales.
    suspend fun getOpenTournee(): Tournee? {
        val open = db.tourneeDao().getOpenTournee() ?: return null
        val summary = db.tourneeDao().getTourneeSummary(open.id) ?: return null
        return summary.toTournee(db.tourneeSecteurDao().getForTournee(open.id))
    }

    suspend fun createTournee(
        nom: String, wilayaName: String?, communeName: String?, note: String?,
        secteurs: List<TourneeSecteur> = emptyList()
    ): Map<String, Any> {
        val now = java.time.Instant.now().toString()
        db.withTransaction {
            requireNoOtherOpenTournee(except = null)
            val newId = db.tourneeDao().insertTournee(
                TourneeEntity(
                    status = "ouverte", date_debut = now, date_fin = null, note = note,
                    nom = nom, wilaya_name = wilayaName, commune_name = communeName, created_at = now
                )
            ).toInt()
            replaceTourneeSecteurs(newId, secteurs)
        }
        return mapOf("message" to "Tournée créée avec succès")
    }

    /**
     * The camion is one: its stock is a single figure, loaded by chargements that name no tournée. Two open
     * tournées would sell from the same load without either knowing, so only one is open at a time.
     */
    private suspend fun requireNoOtherOpenTournee(except: Int?) {
        val open = db.tourneeDao().getOpenTournee() ?: return
        if (open.id == except) return
        throw IllegalStateException("Une tournée est déjà ouverte (« ${open.nom} ») : clôturez-la avant d'en ouvrir une autre.")
    }

    suspend fun closeTournee(id: Int): Map<String, Any> {
        db.tourneeDao().updateTourneeStatus(id, "fermée", java.time.Instant.now().toString())
        return mapOf("message" to "Tournée fermée avec succès")
    }

    suspend fun reopenTournee(id: Int): Map<String, Any> {
        db.withTransaction {
            requireNoOtherOpenTournee(except = id)
            db.tourneeDao().updateTourneeStatus(id, "ouverte", null)
        }
        return mapOf("message" to "Tournée rouverte avec succès")
    }

    suspend fun updateTournee(
        id: Int, nom: String, wilayaName: String?, communeName: String?, note: String?,
        secteurs: List<TourneeSecteur> = emptyList()
    ): Map<String, Any> {
        db.withTransaction {
            db.tourneeDao().updateTourneeFields(id, nom, wilayaName, communeName, note)
            replaceTourneeSecteurs(id, secteurs)
        }
        return mapOf("message" to "Tournée mise à jour avec succès")
    }

    /**
     * The tournée's secteurs are edited as a set, not one at a time, so the whole list is rewritten
     * rather than diffed — the rows carry no state of their own that a delete could lose, and the
     * rewrite is what keeps `order_index` equal to the order shown in the form.
     */
    private suspend fun replaceTourneeSecteurs(tourneeId: Int, secteurs: List<TourneeSecteur>) {
        db.tourneeSecteurDao().deleteForTournee(tourneeId)
        if (secteurs.isEmpty()) return
        db.tourneeSecteurDao().insertAll(
            secteurs.mapIndexed { index, secteur ->
                TourneeSecteurEntity(
                    tournee_id   = tourneeId,
                    secteur_id   = secteur.secteurId,
                    secteur_name = secteur.nom,
                    order_index  = index
                )
            }
        )
    }

    suspend fun deleteTournee(id: Int): Map<String, Any> {
        // The check and the delete in one transaction, so no sale can attach in between.
        val linked = db.withTransaction {
            val linkedVentes = db.venteDao().getVentesForTournee(id)
            if (linkedVentes.isNotEmpty()) return@withTransaction true
            db.tourneeSecteurDao().deleteForTournee(id)
            db.tourneeDao().deleteTourneeById(id)
            false
        }
        if (linked) return mapOf("error" to "Impossible de supprimer : des ventes sont liées à cette tournée")
        return mapOf("message" to "Tournée supprimée avec succès")
    }

    suspend fun getSecteursForCommune(communeName: String): List<Secteur> =
        db.secteurDao().getSecteursForCommune(communeName).map { it.toSecteur() }

    suspend fun createSecteur(nom: String, communeName: String, wilayaName: String?): Secteur {
        val now = java.time.Instant.now().toString()
        val entity = SecteurEntity(
            nom = nom, commune_name = communeName, wilaya_name = wilayaName, created_at = now
        )
        val newId = db.secteurDao().insertSecteur(entity)
        return entity.copy(id = newId.toInt()).toSecteur()
    }

    private fun SecteurEntity.toSecteur() = Secteur(
        id = this.id, nom = this.nom, communeName = this.commune_name, wilayaName = this.wilaya_name
    )

    /**
     * Detaches a client from a tournée's plan.
     *
     * The client record and any ventes already made to them are untouched: this removes a planned
     * visit, not history. A vente made to this client stays on the tournée and keeps counting
     * towards its total, which is why nothing else needs reversing here.
     */
    suspend fun removeClientFromTournee(tourneeId: Int, clientId: Int): Map<String, Any> {
        db.tourneeClientDao().remove(tourneeId, clientId)
        return mapOf("message" to "Client retiré de la tournée")
    }

    /**
     * Adds clients to a tournée's plan, skipping any already on it — and any repeated in [clientIds].
     *
     * The read of who is already planned and the insert are one transaction, so two calls cannot both
     * decide a client is missing. The unique index on (tournee_id, client_id) is what guarantees a
     * client appears once; this keeps a repeat from reaching it as a constraint error.
     */
    suspend fun addClientsToTournee(tourneeId: Int, clientIds: List<Int>): Map<String, Any> {
        db.withTransaction {
            val existing = db.tourneeClientDao().getClientIdsForTournee(tourneeId).toSet()
            val toAdd = clientIds.distinct().filter { it !in existing }
            val startIndex = existing.size
            val entities = toAdd.mapIndexed { i, clientId ->
                TourneeClientEntity(
                    tournee_id = tourneeId, client_id = clientId,
                    status = "a_visiter", order_index = startIndex + i, visited_at = null
                )
            }
            if (entities.isNotEmpty()) db.tourneeClientDao().insertAll(entities)
        }
        return mapOf("message" to "Clients ajoutés avec succès")
    }

    suspend fun setCurrentTourneeClient(tourneeId: Int, clientId: Int): Map<String, Any> {
        db.tourneeClientDao().clearCurrent(tourneeId)
        db.tourneeClientDao().updateStatus(tourneeId, clientId, "en_cours", null)
        return mapOf("message" to "Client courant défini")
    }

    suspend fun markTourneeClientVisited(tourneeId: Int, clientId: Int): Map<String, Any> {
        db.tourneeClientDao().updateStatus(
            tourneeId, clientId, "visite", java.time.Instant.now().toString()
        )
        return mapOf("message" to "Client marqué comme visité")
    }

    suspend fun getTourneeClientsWithDetails(tourneeId: Int): List<TourneeClientInfo> {
        return db.tourneeClientDao().getForTournee(tourneeId).mapNotNull { tc ->
            clientDao.getClientById(tc.client_id)?.let { entity ->
                TourneeClientInfo(client = entity.toClient(), status = tc.status, visitedAt = tc.visited_at)
            }
        }
    }
}

/** Slack for sums of decimals, so a quantity typed as 0.3 is not refused against a stock summed as 0.30000000000000004. */
private const val AMOUNT_EPSILON = 1e-6
