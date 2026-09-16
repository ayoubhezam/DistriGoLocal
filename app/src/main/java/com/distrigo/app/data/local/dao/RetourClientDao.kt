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
}