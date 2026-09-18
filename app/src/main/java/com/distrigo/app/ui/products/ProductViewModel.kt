package com.distrigo.app.ui.products

import com.distrigo.app.data.repository.ProductDuplicate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.distrigo.app.data.model.Category
import com.distrigo.app.data.model.PriceHistory
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.data.model.SousCategorie
import com.distrigo.app.data.model.Marque
import com.distrigo.app.data.model.ProductImage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val repository: ProductRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Room-observed single source of truth: re-emits on every write to the products table,
    // from any feature (vente, chargement, perte, retour, inventaire…) — no manual refresh.
    val products: StateFlow<List<Product>> = repository.observeProducts()
        .onEach { _isLoading.value = false; _error.value = null }
        .catch { e -> _error.value = e.message; _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    private val _sousCategories = MutableStateFlow<List<SousCategorie>>(emptyList())
    val sousCategories: StateFlow<List<SousCategorie>> = _sousCategories

    private val _marques = MutableStateFlow<List<Marque>>(emptyList())
    val marques: StateFlow<List<Marque>> = _marques

    // Observés depuis Room — mise à jour automatique à chaque écriture sur la table suppliers
    val suppliers: StateFlow<List<Supplier>> = repository.observeSuppliers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _priceHistory = MutableStateFlow<List<PriceHistory>>(emptyList())
    val priceHistory: StateFlow<List<PriceHistory>> = _priceHistory

    // -- Product gallery -----------------------------------------------------
    //
    // Keyed on the product being viewed rather than exposed as one flow, because the detail screen
    // is the only consumer and it only ever shows one product. flatMapLatest means opening a
    // second product cancels the first one's collection instead of leaving it running behind the
    // back stack.

    private val _galleryProductId = MutableStateFlow<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val productImages: StateFlow<List<ProductImage>> =
        _galleryProductId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else repository.observeProductImages(id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Points the gallery at a product. Called by the detail screen as it opens. */
    fun observeGalleryFor(productId: Int) { _galleryProductId.value = productId }

    /**
     * Adds a picked photo to a product's gallery.
     *
     * [onError] carries the repository's own message — the gallery is full, or the product already
     * has this exact photo — so the screen can say which it was rather than just failing.
     */
    fun addProductImage(
        productId : Int,
        ref       : String,
        onError   : (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = repository.addProductImage(productId, ref)
                (result["error"] as? String)?.let(onError)
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun deleteProductImage(imageId: Int, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteProductImage(imageId)
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun setPrimaryProductImage(imageId: Int, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.setPrimaryProductImage(imageId)
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    init {
        loadCategories()
        loadSousCategories()
        loadMarques()
    }

    fun deleteProduct(id: Int) {
        viewModelScope.launch {
            try {
                repository.deleteProduct(id)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    /** Whether another live product already has this name or barcode — asked of the database, not the list. */
    suspend fun duplicateOf(name: String, barcode: String?, excludeId: Int): ProductDuplicate? =
        repository.duplicateOf(name, barcode, excludeId)

    fun addProduct(
        product   : Map<String, Any?>,
        onSuccess : (Map<String, Any>) -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = repository.addProduct(product)
                onSuccess(result)
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun updateProduct(
        id        : Int,
        product   : Map<String, Any?>,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateProduct(id, product)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            try {
                _categories.value = repository.getCategories()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun addCategoryAndRefresh(
        name      : String,
        onSuccess : (Int) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = repository.addCategory(mapOf("name" to name, "sort_order" to 0))
                val newId  = (result["id"] as? Double)?.toInt() ?: 0
                loadCategories()
                onSuccess(newId)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun loadSousCategories() {
        viewModelScope.launch {
            try {
                _sousCategories.value = repository.getSousCategories()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun addSousCategorieAndRefresh(
        name       : String,
        categoryId : Int,
        onSuccess  : (Int) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = repository.addSousCategorie(
                    mapOf("name" to name, "category_id" to categoryId, "sort_order" to 0)
                )
                val newId  = (result["id"] as? Double)?.toInt() ?: 0
                loadSousCategories()
                onSuccess(newId)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun loadMarques() {
        viewModelScope.launch {
            try {
                _marques.value = repository.getMarques()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun addMarqueAndRefresh(
        name      : String,
        onSuccess : (Int) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = repository.addMarque(mapOf("name" to name, "sort_order" to 0))
                val newId  = (result["id"] as? Double)?.toInt() ?: 0
                loadMarques()
                onSuccess(newId)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun linkProductToSupplier(
        supplierId    : Int,
        productId     : Int,
        purchasePrice : Double,
        onSuccess     : () -> Unit,
        onError       : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.linkProductToSupplier(supplierId, productId, purchasePrice)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun unlinkProductFromAllSuppliers(
        productId : Int,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.unlinkProductFromAllSuppliers(productId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun addSupplierAndRefresh(
        name      : String,
        phone     : String?,
        onSuccess : (Int) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = repository.addSupplier(mapOf(
                    "name"    to name,
                    "phone"   to phone,
                    "address" to null,
                    "note"    to null,
                    "balance" to 0.0
                ))
                val newId = (result["id"] as? Double)?.toInt() ?: 0
                onSuccess(newId)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun loadPriceHistory(productId: Int) {
        viewModelScope.launch {
            try {
                _priceHistory.value = repository.getProductPriceHistory(productId)
            } catch (e: Exception) {
                _priceHistory.value = emptyList()
            }
        }
    }

    // ── Filter, sort and view state ──
    // Held here, not in the screen, so the list comes back the way it was left after a detour
    // into a product or another tab — the ViewModel is scoped to the Produits graph, while the
    // screen's own `remember`s die with its composition. clearAllFilters() deliberately leaves
    // the search box, the sort order and the grid toggle alone: none of them is a filter chip.
    var searchQuery           by mutableStateOf("")
    var sortOption            by mutableStateOf(SortOption.NAME_ASC)
    var isGridView            by mutableStateOf(false)
    var filterCategoryId      by mutableStateOf<Int?>(null)
    var filterSousCategorieId by mutableStateOf<Int?>(null)
    var filterMarqueId        by mutableStateOf<Int?>(null)
    var filterSupplierId      by mutableStateOf<Int?>(null)
    var filterUnitType        by mutableStateOf<String?>(null)
    var filterStockLevel      by mutableStateOf<String?>(null)
    var filterPriceMin        by mutableStateOf("")
    var filterPriceMax        by mutableStateOf("")
    var filterExpiringSoon    by mutableStateOf(false)

    fun clearAllFilters() {
        filterCategoryId      = null
        filterSousCategorieId = null
        filterMarqueId        = null
        filterSupplierId      = null
        filterUnitType        = null
        filterStockLevel      = null
        filterPriceMin        = ""
        filterPriceMax        = ""
        filterExpiringSoon    = false
    }
}
