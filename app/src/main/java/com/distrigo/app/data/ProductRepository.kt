package com.distrigo.app.data.repository

import androidx.room.withTransaction
import com.distrigo.app.data.api.RetrofitClient
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.dao.*
import com.distrigo.app.data.model.*
import com.distrigo.app.data.local.entity.*



class ProductRepository(
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val supplierDao: SupplierDao,
    private val db: AppDatabase,
    private val clientDao: ClientDao = db.clientDao()


) {
    private val api = RetrofitClient.api

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
            camion_stock = this.camion_stock
        )
    }
    private fun CategoryEntity.toCategory(): Category {
        return Category(id = this.id, name = this.name, sort_order = this.sort_order)
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
            commune_name = this.commune_name
        )
    }
    private fun ClientEntity.toClient(): Client {
        return Client(
            id = this.id, name = this.name, phone = this.phone,
            wilaya_id = null, commune_id = null,
            wilaya_name = this.wilaya_name, commune_name = this.commune_name,
            address = this.address, note = this.note, balance = this.balance,
            customer_type = this.customer_type, image_uri = this.image_uri,
            latitude = this.latitude, longitude = this.longitude
        )
    }
    private suspend fun recalculateClientBalance(clientId: Int) {
        val client = clientDao.getClientById(clientId) ?: return
        val ventes = db.venteDao().getVentesForClient(clientId)
        val ventesTotal = ventes.sumOf { it.total }
        val ventesPaid = ventes.sumOf { it.montant_paye }
        val separatePayments = db.clientPaymentDao().getPaymentsForClient(clientId).sumOf { it.amount }

        val newBalance = ventesTotal - ventesPaid - separatePayments
        clientDao.updateClient(client.copy(balance = newBalance))
    }
    private suspend fun applyStockDelta(source: String, productId: Int, delta: Int) {
        val product = productDao.getProductById(productId) ?: return
        val updated = if (source == "depot")
            product.copy(stock = product.stock + delta)
        else
            product.copy(camion_stock = product.camion_stock + delta)
        productDao.updateProduct(updated)
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

    private suspend fun VenteEntity.toVente(items: List<VenteItem>? = null): Vente {
        val clientName = clientDao.getClientById(this.client_id)?.name ?: ""
        return Vente(
            id = this.id, client_id = this.client_id, client_name = clientName,
            tournee_id = this.tournee_id, source = this.source, total = this.total,
            montant_paye = this.montant_paye, status = this.status, note = this.note,
            created_at = this.created_at, items_count = items?.size, items = items
        )
    }
    private suspend fun TourneeEntity.toTournee(): Tournee {
        val ventesEntities = db.venteDao().getVentesForTournee(this.id)
        val ventes = ventesEntities.map { entity ->
            val count = db.venteDao().getItemsCountForVente(entity.id)
            entity.toVente().copy(items_count = count)
        }
        val clientsCount = ventesEntities.map { it.client_id }.distinct().size
        val totalVentes = ventesEntities.sumOf { it.total }
        val resteTotal = ventesEntities.sumOf { it.total - it.montant_paye }
        return Tournee(
            id = this.id, session_id = 0, status = this.status,
            date_debut = this.date_debut, date_fin = this.date_fin, note = this.note,
            nom = this.nom, wilaya_id = null, commune_id = null,
            wilaya_name = this.wilaya_name, commune_name = this.commune_name,
            chauffeur = this.chauffeur, vehicule = this.vehicule,
            clients_count = clientsCount, ventes_count = ventesEntities.size,
            total_ventes = totalVentes, reste_total = resteTotal, ventes = ventes
        )
    }

    private fun PurchaseOrderItemEntity.toItem() = PurchaseOrderItem(
        id = this.id, quantity = this.quantity, unit_cost = this.unit_cost,
        total_cost = this.total_cost, product_id = this.product_id,
        product_name = this.product_name, unit_type = this.unit_type,
        nb_colis = this.nb_colis, unite_par_colis = this.unite_par_colis
    )

    private fun PurchaseOrderEntity.toOrder(items: List<PurchaseOrderItem>, supplierName: String) = PurchaseOrder(
        id = this.id, date = this.date, total = this.total, status = this.status,
        note = this.note, supplier_id = this.supplier_id, supplier_name = supplierName,
        items_count = items.size, created_at = this.created_at, items = items,
        montant_paye = this.montant_paye
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

    suspend fun getProducts(): List<Product> {
        return productDao.getAllProducts().map { it.toProduct() }
    }

    suspend fun addProduct(product: Map<String, Any?>): Map<String, Any> {
        val catId = (product["category_id"] as? Number)?.toInt()

        // جلب اسم الصنف فوراً من جدول الفئات
        val catName = catId?.let { categoryDao.getCategoryById(it)?.name }

        val entity = ProductEntity(
            name = product["name"] as? String ?: "",
            barcode = product["barcode"] as? String,
            selling_price = (product["selling_price"] as? Number)?.toDouble() ?: 0.0,
            purchase_price = (product["purchase_price"] as? Number)?.toDouble() ?: 0.0,
            stock = (product["stock"] as? Number)?.toInt() ?: 0,
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
            supplier_name = null
        )

        val newId = productDao.insertProduct(entity)
        return mapOf("id" to newId.toDouble(), "message" to "Product added successfully")
    }

    suspend fun updateProduct(id: Int, product: Map<String, Any?>): Map<String, Any> {
        val existing = productDao.getProductById(id) ?: return mapOf("error" to "Product not found")

        val newCatId = if (product.containsKey("category_id")) (product["category_id"] as? Number)?.toInt() else existing.category_id

        // 🔥 التعديل هنا: سنجلب اسم الصنف دائماً من قاعدة البيانات، حتى لو لم يتم تغييره، لتصحيح أي خطأ سابق
        val newCatName = newCatId?.let { categoryDao.getCategoryById(it)?.name }

        val updatedEntity = existing.copy(
            name = product["name"] as? String ?: existing.name,
            barcode = if (product.containsKey("barcode")) product["barcode"] as? String else existing.barcode,
            selling_price = (product["selling_price"] as? Number)?.toDouble() ?: existing.selling_price,
            purchase_price = (product["purchase_price"] as? Number)?.toDouble() ?: existing.purchase_price,
            stock = if (product.containsKey("stock")) (product["stock"] as? Number)?.toInt() ?: existing.stock else existing.stock,
            min_stock = if (product.containsKey("min_stock")) (product["min_stock"] as? Number)?.toInt() ?: existing.min_stock else existing.min_stock,
            unit_type = product["unit_type"] as? String ?: existing.unit_type,
            packages = if (product.containsKey("packages")) (product["packages"] as? Number)?.toInt() ?: existing.packages else existing.packages,
            pack_size = if (product.containsKey("pack_size")) (product["pack_size"] as? Number)?.toInt() ?: existing.pack_size else existing.pack_size,
            has_expiry = if (product.containsKey("has_expiry")) (product["has_expiry"] as? Number)?.toInt() ?: existing.has_expiry else existing.has_expiry,
            expiry_date = if (product.containsKey("expiry_date")) product["expiry_date"] as? String else existing.expiry_date,
            image_uri = if (product.containsKey("image_uri")) product["image_uri"] as? String else existing.image_uri,
            category_id = newCatId,
            category_name = newCatName // وضع الاسم الجديد المصحح
        )

        productDao.updateProduct(updatedEntity)
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
        productDao.deleteProductById(id)
        return mapOf("message" to "Product deleted successfully")
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
        categoryDao.deleteCategoryById(id)
        return mapOf("message" to "Category deleted successfully")
    }

    suspend fun getSuppliers(): List<Supplier> {
        return supplierDao.getAllSuppliers().map { it.toSupplier() }
    }

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
            commune_name = supplier["commune_name"] as? String
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
            commune_name = if (supplier.containsKey("commune_name")) supplier["commune_name"] as? String else existing.commune_name
        )
        supplierDao.updateSupplier(updatedEntity)
        recalculateSupplierBalance(id)
        return mapOf("message" to "Supplier updated successfully")
    }

    suspend fun deleteSupplier(id: Int): Map<String, Any> {
        supplierDao.deleteSupplierById(id)
        return mapOf("message" to "Supplier deleted successfully")
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
            val supplierName = supplierDao.getSupplierById(order.supplier_id)?.name ?: ""
            order.toOrder(items, supplierName)
        }
    }

    suspend fun getPurchaseOrder(id: Int): PurchaseOrder {
        val order = db.purchaseDao().getOrderById(id)
            ?: throw IllegalStateException("Bon introuvable: $id")
        val items = db.purchaseDao().getItemsForOrder(id).map { it.toItem() }
        val supplierName = supplierDao.getSupplierById(order.supplier_id)?.name ?: ""
        return order.toOrder(items, supplierName)
    }

    suspend fun createPurchaseOrder(order: Map<String, Any?>): Map<String, Any> {
        db.withTransaction {
            val supplierId = (order["supplier_id"] as Number).toInt()
            val date = order["date"] as? String ?: java.time.LocalDate.now().toString()
            val note = order["note"] as? String
            val montantPaye = (order["montant_paye"] as? Number)?.toDouble() ?: 0.0
            @Suppress("UNCHECKED_CAST")
            val itemsList = order["items"] as List<Map<String, Any?>>

            val now = java.time.Instant.now().toString()
            val total = itemsList.sumOf { (it["quantity"] as Number).toInt() * (it["unit_cost"] as Number).toDouble() }

            val orderId = db.purchaseDao().insertOrder(
                PurchaseOrderEntity(
                    supplier_id = supplierId, date = date, total = total, status = "pending",
                    note = note, montant_paye = montantPaye, created_at = now
                )
            ).toInt()

            val supplierName = supplierDao.getSupplierById(supplierId)?.name ?: ""
            val itemEntities = mutableListOf<PurchaseOrderItemEntity>()
            val historyEntities = mutableListOf<PriceHistoryEntity>()

            for (map in itemsList) {
                val productId = (map["product_id"] as Number).toInt()
                val quantity = (map["quantity"] as Number).toInt()
                val unitCost = (map["unit_cost"] as Number).toDouble()
                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Produit introuvable: $productId")

                itemEntities.add(
                    PurchaseOrderItemEntity(
                        purchase_order_id = orderId, product_id = productId, quantity = quantity,
                        unit_cost = unitCost, total_cost = quantity * unitCost,
                        product_name = product.name, unit_type = product.unit_type,
                        nb_colis = (map["nb_colis"] as? Number)?.toInt() ?: 1,
                        unite_par_colis = (map["unite_par_colis"] as? Number)?.toInt() ?: 1
                    )
                )
                historyEntities.add(
                    PriceHistoryEntity(
                        product_id = productId, unit_cost = unitCost,
                        date = date, created_at = now, supplier_name = supplierName
                    )
                )
            }
            db.purchaseDao().insertItems(itemEntities)
            db.purchaseDao().insertPriceHistory(historyEntities)
            recalculateSupplierBalance(supplierId)
        }
        return mapOf("message" to "Bon créé avec succès")
    }

    suspend fun receivePurchaseOrder(id: Int): Map<String, Any> {
        db.withTransaction {
            val order = db.purchaseDao().getOrderById(id)
                ?: throw IllegalStateException("Bon introuvable: $id")
            if (order.status == "received") return@withTransaction

            val items = db.purchaseDao().getItemsForOrder(id)
            for (item in items) {
                val product = productDao.getProductById(item.product_id) ?: continue
                productDao.updateProduct(product.copy(stock = product.stock + item.quantity))
            }
            db.purchaseDao().updateOrderStatus(id, "received")
        }
        return mapOf("message" to "Bon marqué comme reçu")
    }

    suspend fun updatePurchaseOrder(id: Int, order: Map<String, Any?>): Map<String, Any> {
        db.withTransaction {
            val note = order["note"] as? String
            val montantPaye = (order["montant_paye"] as? Number)?.toDouble() ?: 0.0
            @Suppress("UNCHECKED_CAST")
            val itemsList = order["items"] as List<Map<String, Any?>>

            val existing = db.purchaseDao().getOrderById(id)
                ?: throw IllegalStateException("Bon introuvable: $id")
            val supplierName = supplierDao.getSupplierById(existing.supplier_id)?.name ?: ""

            db.purchaseDao().deleteItemsForOrder(id)
            val total = itemsList.sumOf { (it["quantity"] as Number).toInt() * (it["unit_cost"] as Number).toDouble() }

            val itemEntities = itemsList.map { map ->
                val productId = (map["product_id"] as Number).toInt()
                val quantity = (map["quantity"] as Number).toInt()
                val unitCost = (map["unit_cost"] as Number).toDouble()
                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Produit introuvable: $productId")
                PurchaseOrderItemEntity(
                    purchase_order_id = id, product_id = productId, quantity = quantity,
                    unit_cost = unitCost, total_cost = quantity * unitCost,
                    product_name = product.name, unit_type = product.unit_type,
                    nb_colis = (map["nb_colis"] as? Number)?.toInt() ?: 1,
                    unite_par_colis = (map["unite_par_colis"] as? Number)?.toInt() ?: 1
                )
            }
            db.purchaseDao().insertItems(itemEntities)
            db.purchaseDao().updateOrderFields(id, note, montantPaye, total)

            recalculateSupplierBalance(existing.supplier_id)
        }
        return mapOf("message" to "Bon mis à jour avec succès")
    }

    suspend fun reopenPurchaseOrder(id: Int): Map<String, Any> {
        db.withTransaction {
            val order = db.purchaseDao().getOrderById(id)
                ?: throw IllegalStateException("Bon introuvable: $id")
            if (order.status != "received") return@withTransaction

            val items = db.purchaseDao().getItemsForOrder(id)
            for (item in items) {
                val product = productDao.getProductById(item.product_id) ?: continue
                productDao.updateProduct(product.copy(stock = product.stock - item.quantity))
            }
            db.purchaseDao().updateOrderStatus(id, "pending")
        }
        return mapOf("message" to "Bon rouvert avec succès")
    }

    suspend fun deletePurchaseOrder(id: Int): Map<String, Any> {
        db.withTransaction {
            val order = db.purchaseDao().getOrderById(id)
                ?: throw IllegalStateException("Bon introuvable: $id")

            if (order.status == "received") {
                val items = db.purchaseDao().getItemsForOrder(id)
                for (item in items) {
                    val product = productDao.getProductById(item.product_id) ?: continue
                    productDao.updateProduct(product.copy(stock = product.stock - item.quantity))
                }
            }
            db.purchaseDao().deleteItemsForOrder(id)
            db.purchaseDao().deleteOrderById(id)
            recalculateSupplierBalance(order.supplier_id)
        }
        return mapOf("message" to "Bon supprimé avec succès")
    }

    suspend fun getProductPriceHistory(id: Int): List<PriceHistory> {
        return db.purchaseDao().getPriceHistoryForProduct(id).map { it.toPriceHistory() }
    }

    private suspend fun recalculateSupplierBalance(supplierId: Int) {
        val supplier = supplierDao.getSupplierById(supplierId) ?: return
        val orders = db.purchaseDao().getAllOrders().filter { it.supplier_id == supplierId }
        val ordersTotal = orders.sumOf { it.total }
        val ordersPaidAtCreation = orders.sumOf { it.montant_paye }
        val separatePayments = db.supplierPaymentDao().getPaymentsForSupplier(supplierId).sumOf { it.amount }

        val newBalance = supplier.initial_balance + ordersTotal - ordersPaidAtCreation - separatePayments
        supplierDao.updateSupplier(supplier.copy(balance = newBalance))
    }

// ── Ventes (محلي بالكامل) ──

    suspend fun getVentes(clientId: Int? = null): List<Vente> {
        val entities = if (clientId != null) db.venteDao().getVentesForClient(clientId)
        else db.venteDao().getAllVentes()
        return entities.map { entity ->
            val count = db.venteDao().getItemsCountForVente(entity.id)
            entity.toVente().copy(items_count = count)
        }
    }

    suspend fun getVente(id: Int): Vente {
        val entity = db.venteDao().getVenteById(id)
            ?: throw IllegalStateException("Vente introuvable: $id")
        val items = db.venteDao().getItemsForVente(id).map { it.toItem() }
        return entity.toVente(items)
    }

    suspend fun createVente(
        clientId: Int, tourneeId: Int?, source: String,
        items: List<Map<String, Any?>>, note: String?, montantPaye: Double
    ): Map<String, Any> {
        db.withTransaction {
            val total = items.sumOf { (it["quantity"] as Number).toInt() * (it["unit_price"] as Number).toDouble() }
            val now = java.time.Instant.now().toString()

// ── تحقق مسبق: فقط للبيع من الشاحنة (Tournée) — Dépôt يسمح بمخزون سالب ──
            if (source == "camion") {
                for (map in items) {
                    val productId = (map["product_id"] as Number).toInt()
                    val quantity = (map["quantity"] as Number).toInt()
                    val product = productDao.getProductById(productId)
                        ?: throw IllegalStateException("Produit introuvable: $productId")

                    if (quantity > product.camion_stock) {
                        throw IllegalStateException(
                            "Stock insuffisant pour ${product.name} : disponible ${product.camion_stock}, demandé $quantity"
                        )
                    }
                }
            }

            val venteId = db.venteDao().insertVente(
                VenteEntity(
                    client_id = clientId, tournee_id = tourneeId, source = source,
                    total = total, montant_paye = montantPaye, status = "pending",
                    note = note, created_at = now
                )
            ).toInt()

            val itemEntities = items.map { map ->
                val productId = (map["product_id"] as Number).toInt()
                val quantity = (map["quantity"] as Number).toInt()
                val unitPrice = (map["unit_price"] as Number).toDouble()
                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Produit introuvable: $productId")

                applyStockDelta(source, productId, -quantity)

                VenteItemEntity(
                    vente_id = venteId, product_id = productId, product_name = product.name,
                    unit_type = product.unit_type, quantity = quantity,
                    unit_price = unitPrice, total_price = quantity * unitPrice
                )
            }
            db.venteDao().insertItems(itemEntities)
            recalculateClientBalance(clientId)
        }
        return mapOf("message" to "Vente créée avec succès")
    }

    suspend fun updateVente(
        id: Int, clientId: Int, items: List<Map<String, Any?>>,
        note: String?, montantPaye: Double
    ): Map<String, Any> {
        db.withTransaction {
            val existing = db.venteDao().getVenteById(id)
                ?: throw IllegalStateException("Vente introuvable: $id")

            // عكس تأثير العناصر القديمة على المخزون
            val oldItems = db.venteDao().getItemsForVente(id)
            for (item in oldItems) {
                applyStockDelta(existing.source, item.product_id, item.quantity)
            }
            // ── تحقق مسبق ──
            // ── تحقق مسبق: فقط للبيع من الشاحنة (Tournée) ──
            if (existing.source == "camion") {
                for (map in items) {
                    val productId = (map["product_id"] as Number).toInt()
                    val quantity = (map["quantity"] as Number).toInt()
                    val product = productDao.getProductById(productId)
                        ?: throw IllegalStateException("Produit introuvable: $productId")

                    if (quantity > product.camion_stock) {
                        throw IllegalStateException(
                            "Stock insuffisant pour ${product.name} : disponible ${product.camion_stock}, demandé $quantity"
                        )
                    }
                }
            }
            db.venteDao().deleteItemsForVente(id)

            val total = items.sumOf { (it["quantity"] as Number).toInt() * (it["unit_price"] as Number).toDouble() }
            val itemEntities = items.map { map ->
                val productId = (map["product_id"] as Number).toInt()
                val quantity = (map["quantity"] as Number).toInt()
                val unitPrice = (map["unit_price"] as Number).toDouble()
                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Produit introuvable: $productId")

                applyStockDelta(existing.source, productId, -quantity)

                VenteItemEntity(
                    vente_id = id, product_id = productId, product_name = product.name,
                    unit_type = product.unit_type, quantity = quantity,
                    unit_price = unitPrice, total_price = quantity * unitPrice
                )
            }
            db.venteDao().insertItems(itemEntities)
            db.venteDao().updateVenteFields(id, note, montantPaye, total)
            recalculateClientBalance(clientId)
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
            val items = db.venteDao().getItemsForVente(id)
            for (item in items) {
                applyStockDelta(existing.source, item.product_id, item.quantity)
            }
            db.venteDao().deleteItemsForVente(id)
            db.venteDao().deleteVenteById(id)
            recalculateClientBalance(existing.client_id)
        }
        return mapOf("message" to "Vente supprimée avec succès")
    }

    // ── Rapports Tournée (Tableau de bord) ──

    suspend fun getTourneeVentesStats(
        startIso: String, endIso: String,
        previousStartIso: String, previousEndIso: String
    ): com.distrigo.app.data.model.report.TourneeVentesStats {
        val currentVentes = db.venteDao().getVentesBySourceBetween("camion", startIso, endIso)
        val previousVentes = db.venteDao().getVentesBySourceBetween("camion", previousStartIso, previousEndIso)

        val totalVentes = currentVentes.sumOf { it.total }
        val ticketsCount = currentVentes.size
        val panierMoyen = if (ticketsCount == 0) 0.0 else totalVentes / ticketsCount

        val previousTotalVentes = previousVentes.sumOf { it.total }
        val previousTicketsCount = previousVentes.size
        val previousPanierMoyen = if (previousTicketsCount == 0) 0.0 else previousTotalVentes / previousTicketsCount

        val dailyBreakdown = currentVentes
            .groupBy { it.created_at.substring(0, 10) } // "YYYY-MM-DD"
            .map { (date, ventesDuJour) ->
                com.distrigo.app.data.model.report.DailySalesAmount(date, ventesDuJour.sumOf { it.total })
            }
            .sortedBy { it.dateIso }

        return com.distrigo.app.data.model.report.TourneeVentesStats(
            totalVentes = totalVentes,
            ticketsCount = ticketsCount,
            panierMoyen = panierMoyen,
            previousTotalVentes = previousTotalVentes,
            previousTicketsCount = previousTicketsCount,
            previousPanierMoyen = previousPanierMoyen,
            dailyBreakdown = dailyBreakdown
        )
    }

    // Supplier transactions
    // ── Supplier transactions (محلي بالكامل) ──

    suspend fun getSupplierTransactions(id: Int): List<SupplierTransaction> {
        val supplier = supplierDao.getSupplierById(id) ?: return emptyList()

        val factureTx = db.purchaseDao().getAllOrders()
            .filter { it.supplier_id == id }
            .map { order ->
                SupplierTransaction(
                    type = "facture", id = order.id, amount = order.total,
                    montant_paye = order.montant_paye, status = order.status,
                    note = order.note, created_at = order.created_at
                )
            }

        val paiementTx = db.supplierPaymentDao().getPaymentsForSupplier(id)
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

        return (factureTx + paiementTx + soldeInitialTx).sortedByDescending { it.created_at }
    }

    suspend fun addSupplierPayment(id: Int, amount: Double, note: String?): Map<String, Any> {
        db.withTransaction {
            db.supplierPaymentDao().insertPayment(
                SupplierPaymentEntity(
                    supplier_id = id, amount = amount, note = note,
                    created_at = java.time.Instant.now().toString()
                )
            )
            recalculateSupplierBalance(id)
        }
        return mapOf("message" to "Paiement enregistré")
    }

    suspend fun deleteSupplierPayment(supplierId: Int, paymentId: Int): Map<String, Any> {
        db.withTransaction {
            db.supplierPaymentDao().deletePaymentById(paymentId)
            recalculateSupplierBalance(supplierId)
        }
        return mapOf("message" to "Paiement supprimé")
    }

    suspend fun updateSupplierPayment(supplierId: Int, paymentId: Int, amount: Double): Map<String, Any> {
        db.withTransaction {
            db.supplierPaymentDao().updatePaymentAmount(paymentId, amount)
            recalculateSupplierBalance(supplierId)
        }
        return mapOf("message" to "Paiement mis à jour")
    }

// ── Clients (محلي بالكامل للبيانات الأساسية) ──

    suspend fun getClients(): List<Client> {
        return clientDao.getAllClients().map { it.toClient() }
    }

    suspend fun addClient(client: Map<String, Any?>): Map<String, Any> {
        val entity = ClientEntity(
            name = client["name"] as? String ?: "",
            phone = client["phone"] as? String,
            wilaya_name = client["wilaya_name"] as? String,
            commune_name = client["commune_name"] as? String,
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

    suspend fun deleteClient(id: Int): Map<String, Any> {
        clientDao.deleteClientById(id)
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

    suspend fun createChargement(note: String?, items: List<Map<String, Any?>>): Map<String, Any> {
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
                val quantity  = map["quantity"] as Int
                val direction = map["direction"] as String

                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Produit introuvable: $productId")

                val updatedProduct = if (direction == "vers_camion") {
                    product.copy(
                        stock        = product.stock - quantity,
                        camion_stock = product.camion_stock + quantity
                    )
                } else {
                    product.copy(
                        stock        = product.stock + quantity,
                        camion_stock = product.camion_stock - quantity
                    )
                }
                productDao.updateProduct(updatedProduct)

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
        }
        return mapOf("message" to "Chargement créé avec succès")
    }

    suspend fun deleteChargement(id: Int): Map<String, Any> {
        db.withTransaction {
            val items = db.chargementDao().getItemsForChargement(id)
            for (item in items) {
                val product = productDao.getProductById(item.product_id) ?: continue
                // عكس التأثير: عودة الكمية لمصدرها الأصلي
                val reverted = if (item.direction == "vers_camion") {
                    product.copy(
                        stock        = product.stock + item.quantity,
                        camion_stock = product.camion_stock - item.quantity
                    )
                } else {
                    product.copy(
                        stock        = product.stock - item.quantity,
                        camion_stock = product.camion_stock + item.quantity
                    )
                }
                productDao.updateProduct(reverted)
            }
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





// ── Client transactions (محلي بالكامل) ──

    suspend fun getClientTransactions(id: Int): List<ClientTransaction> {
        val venteTx = db.venteDao().getVentesForClient(id).map { vente ->
            ClientTransaction(
                type = "vente", id = vente.id, amount = null,
                total = vente.total, montant_paye = vente.montant_paye,
                status = vente.status, note = vente.note, created_at = vente.created_at
            )
        }

        val paiementTx = db.clientPaymentDao().getPaymentsForClient(id).map { payment ->
            ClientTransaction(
                type = "paiement", id = payment.id, amount = payment.amount,
                total = null, montant_paye = null, status = null,
                note = payment.note, created_at = payment.created_at
            )
        }

        return (venteTx + paiementTx).sortedByDescending { it.created_at }
    }

    suspend fun addClientPayment(id: Int, amount: Double, note: String?): Map<String, Any> {
        db.withTransaction {
            db.clientPaymentDao().insertPayment(
                ClientPaymentEntity(
                    client_id = id, amount = amount, note = note,
                    created_at = java.time.Instant.now().toString()
                )
            )
            recalculateClientBalance(id)
        }
        return mapOf("message" to "Paiement enregistré")
    }

    suspend fun deleteClientPayment(clientId: Int, paymentId: Int): Map<String, Any> {
        db.withTransaction {
            db.clientPaymentDao().deletePaymentById(paymentId)
            recalculateClientBalance(clientId)
        }
        return mapOf("message" to "Paiement supprimé")
    }

    suspend fun updateClientPayment(clientId: Int, paymentId: Int, amount: Double): Map<String, Any> {
        db.withTransaction {
            db.clientPaymentDao().updatePaymentAmount(paymentId, amount)
            recalculateClientBalance(clientId)
        }
        return mapOf("message" to "Paiement mis à jour")
    }

    // ── Tournées (محلي بالكامل) ──

    suspend fun getTournees(): List<Tournee> = db.tourneeDao().getAllTournees().map { it.toTournee() }

    suspend fun getTournee(id: Int): Tournee {
        val entity = db.tourneeDao().getTourneeById(id)
            ?: throw IllegalStateException("Tournée introuvable: $id")
        return entity.toTournee()
    }

    suspend fun getOpenTournee(): Tournee? = db.tourneeDao().getOpenTournee()?.toTournee()

    suspend fun createTournee(
        nom: String, wilayaName: String?, communeName: String?,
        chauffeur: String?, vehicule: String?, note: String?
    ): Map<String, Any> {
        val now = java.time.Instant.now().toString()
        db.tourneeDao().insertTournee(
            TourneeEntity(
                status = "ouverte", date_debut = now, date_fin = null, note = note,
                nom = nom, wilaya_name = wilayaName, commune_name = communeName,
                chauffeur = chauffeur, vehicule = vehicule, created_at = now
            )
        )
        return mapOf("message" to "Tournée créée avec succès")
    }

    suspend fun closeTournee(id: Int): Map<String, Any> {
        db.tourneeDao().updateTourneeStatus(id, "fermée", java.time.Instant.now().toString())
        return mapOf("message" to "Tournée fermée avec succès")
    }

    suspend fun reopenTournee(id: Int): Map<String, Any> {
        db.tourneeDao().updateTourneeStatus(id, "ouverte", null)
        return mapOf("message" to "Tournée rouverte avec succès")
    }

    suspend fun updateTournee(
        id: Int, nom: String, wilayaName: String?, communeName: String?,
        chauffeur: String?, vehicule: String?, note: String?
    ): Map<String, Any> {
        db.tourneeDao().updateTourneeFields(id, nom, wilayaName, communeName, chauffeur, vehicule, note)
        return mapOf("message" to "Tournée mise à jour avec succès")
    }

    suspend fun deleteTournee(id: Int): Map<String, Any> {
        val linkedVentes = db.venteDao().getVentesForTournee(id)
        if (linkedVentes.isNotEmpty()) {
            return mapOf("error" to "Impossible de supprimer : des ventes sont liées à cette tournée")
        }
        db.tourneeDao().deleteTourneeById(id)
        return mapOf("message" to "Tournée supprimée avec succès")
    }

    suspend fun addClientsToTournee(tourneeId: Int, clientIds: List<Int>): Map<String, Any> {
        val existing = db.tourneeClientDao().getClientIdsForTournee(tourneeId).toSet()
        val toAdd = clientIds.filter { it !in existing }
        val startIndex = existing.size
        val entities = toAdd.mapIndexed { i, clientId ->
            TourneeClientEntity(
                tournee_id = tourneeId, client_id = clientId,
                status = "a_visiter", order_index = startIndex + i, visited_at = null
            )
        }
        if (entities.isNotEmpty()) db.tourneeClientDao().insertAll(entities)
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