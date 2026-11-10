package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.RetourClientEntity
import com.distrigo.app.data.local.entity.RetourClientItemEntity

@Dao
interface RetourClientDao {

    @Insert
    suspend fun insertRetour(retour: RetourClientEntity): Long

    @Query("SELECT * FROM retour_client WHERE client_id = :clientId ORDER BY date DESC, id DESC")
    suspend fun getRetoursForClient(clientId: Int): List<RetourClientEntity>

    /**
     * The client's [limit] latest returns for its detail screen: newest created first, and for the
     * same instant in the order the returns list uses (date, then id, newest first).
     */
    @Query("""
        SELECT * FROM retour_client
        WHERE client_id = :clientId
        ORDER BY created_at DESC, date DESC, id DESC
        LIMIT :limit
    """)
    suspend fun getLatestRetoursForClient(clientId: Int, limit: Int): List<RetourClientEntity>

    /** The client's returns counted and summed: its detail screen's "Retours" figures. */
    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(total), 0.0) AS total FROM retour_client WHERE client_id = :clientId")
    suspend fun getRetourTotalsForClient(clientId: Int): RetourTotals

    /** Line counts for the given returns, in one query. Returns without lines have no row. */
    @Query("SELECT retour_id, COUNT(*) AS count FROM retour_client_items WHERE retour_id IN (:retourIds) GROUP BY retour_id")
    suspend fun getItemCountsForRetours(retourIds: List<Int>): List<RetourItemCount>

    @Query("SELECT * FROM retour_client WHERE id = :id")
    suspend fun getRetourById(id: Int): RetourClientEntity?

    @Query("DELETE FROM retour_client WHERE id = :id")
    suspend fun deleteRetourById(id: Int)

    @Insert
    suspend fun insertItems(items: List<RetourClientItemEntity>)

    @Query("SELECT * FROM retour_client_items WHERE retour_id = :retourId")
    suspend fun getItemsForRetour(retourId: Int): List<RetourClientItemEntity>

    @Query("DELETE FROM retour_client_items WHERE retour_id = :retourId")
    suspend fun deleteItemsForRetour(retourId: Int)

    @Query("""
        SELECT rci.product_id AS product_id, SUM(rci.quantity) AS total_quantity
        FROM retour_client_items rci
        INNER JOIN retour_client rc ON rc.id = rci.retour_id
        WHERE rc.client_id = :clientId
        GROUP BY rci.product_id
    """)
    suspend fun getReturnedQuantitiesForClient(clientId: Int): List<ProductQuantitySum>

    /** The number printed on it, or null if it has none — see DocumentNumberTriggers. */
    @Query("SELECT numero FROM retour_client WHERE id = :id")
    suspend fun getNumero(id: Int): String?

    /**
     * The client's sales a return can still be made from, newest first: every sale whatever its status (UX-01) with
     * at least one line not yet returned in full. [ReturnableSaleRow.lines] counts those lines.
     */
    @Query("""
        SELECT v.id AS id, v.numero AS numero, v.created_at AS created_at, v.source AS source, v.total AS total,
               COUNT(vi.id) AS lines
        FROM ventes v JOIN vente_items vi ON vi.vente_id = v.id
        WHERE v.client_id = :clientId
          AND vi.quantity - COALESCE((SELECT SUM(ri.quantity) FROM retour_client_items ri WHERE ri.vente_item_id = vi.id), 0) > 0.0005
        GROUP BY v.id
        ORDER BY v.created_at DESC, v.id DESC
    """)
    suspend fun getReturnableSales(clientId: Int): List<ReturnableSaleRow>

    /** Every line of a sale, with what was already returned of it (FIN-02: the cap is sold less returned, per line). */
    @Query("""
        SELECT vi.id AS vente_item_id, vi.vente_id AS vente_id, vi.product_id AS product_id, vi.quantity AS sold,
               vi.unit_price AS unit_price, vi.purchase_price_snapshot AS purchase_price_snapshot,
               COALESCE((SELECT SUM(ri.quantity) FROM retour_client_items ri WHERE ri.vente_item_id = vi.id), 0) AS returned
        FROM vente_items vi
        WHERE vi.vente_id = :venteId
        ORDER BY vi.id
    """)
    suspend fun getSaleLinesWithReturned(venteId: Int): List<SaleLineReturned>

    /** The sale's number, or null when it has none or is gone. */
    @Query("SELECT numero FROM ventes WHERE id = :venteId")
    suspend fun getVenteNumero(venteId: Int): String?
}

/** A sale a client return can be made from: its number, when, from where, its total, and how many lines are left. */
data class ReturnableSaleRow(
    val id: Int,
    val numero: String?,
    val created_at: String,
    val source: String,
    val total: Double,
    val lines: Int,
)

/** A sale line as a return reads it: what it sold, at what price and cost, and how much of it already came back. */
data class SaleLineReturned(
    val vente_item_id: Int,
    val vente_id: Int,
    val product_id: Int,
    val sold: Double,
    val unit_price: Double,
    val purchase_price_snapshot: Double,
    val returned: Double,
) {
    /** What may still come back of this line. */
    val left: Double get() = com.distrigo.app.data.model.Quantity.normalize(sold - returned)
}