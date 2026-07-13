package com.distrigo.app.data.api

import com.distrigo.app.data.model.Product
import retrofit2.http.*
import com.distrigo.app.data.model.Category
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.data.model.SupplierProduct
import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.data.model.PurchaseOrderItem
import com.distrigo.app.data.model.PriceHistory
import com.distrigo.app.data.model.SupplierTransaction
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.Chargement
import com.distrigo.app.data.model.ChargementSession
import com.distrigo.app.data.model.Vente
import com.distrigo.app.data.model.ClientTransaction
import com.distrigo.app.data.model.Tournee
import com.distrigo.app.data.model.Wilaya
import com.distrigo.app.data.model.Commune
interface ApiService {

    @GET("api/products")
    suspend fun getProducts(): List<Product>

    @POST("api/products")
    suspend fun addProduct(@Body product: Map<String, @JvmSuppressWildcards Any?>): Map<String, Any>
    @DELETE("api/products/{id}")
    suspend fun deleteProduct(@Path("id") id: Int): Map<String, Any>
    @PUT("api/products/{id}")
    suspend fun updateProduct(
        @Path("id") id: Int,
        @Body product: Map<String, @JvmSuppressWildcards Any?>
    ): Map<String, Any>

    // Categories
    @GET("api/categories")
    suspend fun getCategories(): List<Category>

    @POST("api/categories")
    suspend fun addCategory(@Body category: Map<String, @JvmSuppressWildcards Any?>): Map<String, Any>

    @PUT("api/categories/{id}")
    suspend fun updateCategory(
        @Path("id") id: Int,
        @Body category: Map<String, @JvmSuppressWildcards Any?>
    ): Map<String, Any>

    @DELETE("api/categories/{id}")
    suspend fun deleteCategory(@Path("id") id: Int): Map<String, Any>

    // Suppliers
    @GET("api/suppliers")
    suspend fun getSuppliers(): List<Supplier>

    @POST("api/suppliers")
    suspend fun addSupplier(@Body supplier: Map<String, @JvmSuppressWildcards Any?>): Map<String, Any>

    @PUT("api/suppliers/{id}")
    suspend fun updateSupplier(
        @Path("id") id: Int,
        @Body supplier: Map<String, @JvmSuppressWildcards Any?>
    ): Map<String, Any>

    @DELETE("api/suppliers/{id}")
    suspend fun deleteSupplier(@Path("id") id: Int): Map<String, Any>

    @GET("api/suppliers/{id}/products")
    suspend fun getSupplierProducts(@Path("id") id: Int): List<SupplierProduct>

    @POST("api/suppliers/{id}/products")
    suspend fun linkProductToSupplier(
        @Path("id") supplierId: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Map<String, Any>


    // Clients
    @GET("api/clients")
    suspend fun getClients(): List<Client>

    @POST("api/clients")
    suspend fun addClient(@Body client: Map<String, @JvmSuppressWildcards Any?>): Map<String, Any>

    @PUT("api/clients/{id}")
    suspend fun updateClient(
        @Path("id") id: Int,
        @Body client: Map<String, @JvmSuppressWildcards Any?>
    ): Map<String, Any>

    @DELETE("api/clients/{id}")
    suspend fun deleteClient(@Path("id") id: Int): Map<String, Any>

    // Chargements
    @GET("api/chargements")
    suspend fun getChargements(): List<Chargement>

    @GET("api/chargements/{id}")
    suspend fun getChargement(@Path("id") id: Int): Chargement

    @POST("api/chargements")
    suspend fun createChargement(@Body body: Map<String, @JvmSuppressWildcards Any?>): Map<String, Any>

    @DELETE("api/chargements/{id}")
    suspend fun deleteChargement(@Path("id") id: Int): Map<String, Any>

    @GET("api/chargements/sessions")
    suspend fun getChargementSessions(): List<ChargementSession>

    @GET("api/chargements/sessions/{id}")
    suspend fun getChargementSession(@Path("id") id: Int): ChargementSession

    @PUT("api/chargements/sessions/{id}")
    suspend fun updateChargementSession(
        @Path("id") id: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Map<String, Any>

    // Purchases
    @GET("api/purchases")
    suspend fun getPurchaseOrders(): List<PurchaseOrder>

    @GET("api/purchases/{id}")
    suspend fun getPurchaseOrder(@Path("id") id: Int): PurchaseOrder

    @POST("api/purchases")
    suspend fun createPurchaseOrder(@Body order: Map<String, @JvmSuppressWildcards Any?>): Map<String, Any>

    @PUT("api/purchases/{id}/receive")
    suspend fun receivePurchaseOrder(@Path("id") id: Int): Map<String, Any>

    @PUT("api/purchases/{id}")
    suspend fun updatePurchaseOrder(
        @Path("id") id: Int,
        @Body order: Map<String, @JvmSuppressWildcards Any?>
    ): Map<String, Any>

    @PUT("api/purchases/{id}/reopen")
    suspend fun reopenPurchaseOrder(@Path("id") id: Int): Map<String, Any>

    @DELETE("api/purchases/{id}")
    suspend fun deletePurchaseOrder(@Path("id") id: Int): Map<String, Any>

    @GET("api/products/{id}/price-history")
    suspend fun getProductPriceHistory(@Path("id") id: Int): List<PriceHistory>


    // Supplier transactions
    @GET("api/suppliers/{id}/transactions")
    suspend fun getSupplierTransactions(@Path("id") id: Int): List<SupplierTransaction>

    @POST("api/suppliers/{id}/payments")
    suspend fun addSupplierPayment(
        @Path("id") id: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Map<String, Any>

    @DELETE("api/products/{id}/suppliers")
    suspend fun unlinkProductFromAllSuppliers(@Path("id") id: Int): Map<String, Any>


    @DELETE("api/suppliers/{supplierId}/payments/{paymentId}")
    suspend fun deleteSupplierPayment(
        @Path("supplierId") supplierId: Int,
        @Path("paymentId")  paymentId: Int
    ): Map<String, Any>

    @PUT("api/suppliers/{supplierId}/payments/{paymentId}")
    suspend fun updateSupplierPayment(
        @Path("supplierId") supplierId: Int,
        @Path("paymentId")  paymentId: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Map<String, Any>


    // Ventes
    @GET("api/ventes")
    suspend fun getVentes(@Query("client_id") clientId: Int? = null): List<Vente>

    @GET("api/ventes/{id}")
    suspend fun getVente(@Path("id") id: Int): Vente

    @POST("api/ventes")
    suspend fun createVente(@Body body: Map<String, @JvmSuppressWildcards Any?>): Map<String, Any>

    @PUT("api/ventes/{id}")
    suspend fun updateVente(
        @Path("id") id: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Map<String, Any>

    @PUT("api/ventes/{id}/deliver")
    suspend fun deliverVente(@Path("id") id: Int): Map<String, Any>

    @DELETE("api/ventes/{id}")
    suspend fun deleteVente(@Path("id") id: Int): Map<String, Any>

    // Client transactions
    @GET("api/clients/{id}/transactions")
    suspend fun getClientTransactions(@Path("id") id: Int): List<ClientTransaction>

    @POST("api/clients/{id}/payments")
    suspend fun addClientPayment(
        @Path("id") id: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Map<String, Any>

    @DELETE("api/clients/{clientId}/payments/{paymentId}")
    suspend fun deleteClientPayment(
        @Path("clientId") clientId: Int,
        @Path("paymentId") paymentId: Int
    ): Map<String, Any>

    @PUT("api/clients/{clientId}/payments/{paymentId}")
    suspend fun updateClientPayment(
        @Path("clientId") clientId: Int,
        @Path("paymentId") paymentId: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Map<String, Any>

    // Tournées
    @GET("api/tournees")
    suspend fun getTournees(): List<Tournee>

    @GET("api/tournees/{id}")
    suspend fun getTournee(@Path("id") id: Int): Tournee

    @GET("api/tournees/status/open")
    suspend fun getOpenTournee(): Tournee?

    @POST("api/tournees")
    suspend fun createTournee(@Body body: Map<String, @JvmSuppressWildcards Any?>): Map<String, Any>

    @PUT("api/tournees/{id}/close")
    suspend fun closeTournee(@Path("id") id: Int): Map<String, Any>

    @PUT("api/tournees/{id}/reopen")
    suspend fun reopenTournee(@Path("id") id: Int): Map<String, Any>

    @PUT("api/tournees/{id}")
    suspend fun updateTournee(
        @Path("id") id: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Map<String, Any>

    @DELETE("api/tournees/{id}")
    suspend fun deleteTournee(@Path("id") id: Int): Map<String, Any>

    // Geo
    @GET("api/geo/wilayas")
    suspend fun getWilayas(): List<Wilaya>

    @GET("api/geo/communes")
    suspend fun getCommunes(@Query("wilaya_id") wilayaId: Int): List<Commune>

}

