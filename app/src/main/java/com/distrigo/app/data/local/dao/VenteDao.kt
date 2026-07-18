package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.VenteEntity
import com.distrigo.app.data.local.entity.VenteItemEntity

@Dao
interface VenteDao {

    @Insert
    suspend fun insertVente(vente: VenteEntity): Long

    @Query("SELECT * FROM ventes ORDER BY id DESC")
    suspend fun getAllVentes(): List<VenteEntity>

    @Query("SELECT * FROM ventes WHERE client_id = :clientId ORDER BY id DESC")
    suspend fun getVentesForClient(clientId: Int): List<VenteEntity>

    @Query("SELECT * FROM ventes WHERE tournee_id = :tourneeId ORDER BY id DESC")
    suspend fun getVentesForTournee(tourneeId: Int): List<VenteEntity>

    @Query("SELECT * FROM ventes WHERE id = :id")
    suspend fun getVenteById(id: Int): VenteEntity?

    @Query("UPDATE ventes SET status = :status WHERE id = :id")
    suspend fun updateVenteStatus(id: Int, status: String)

    @Query("""
        UPDATE ventes
        SET note = :note, montant_paye = :montantPaye, total = :total
        WHERE id = :id
    """)
    suspend fun updateVenteFields(id: Int, note: String?, montantPaye: Double, total: Double)

    @Query("DELETE FROM ventes WHERE id = :id")
    suspend fun deleteVenteById(id: Int)

    @Insert
    suspend fun insertItems(items: List<VenteItemEntity>)

    @Query("SELECT * FROM vente_items WHERE vente_id = :venteId")
    suspend fun getItemsForVente(venteId: Int): List<VenteItemEntity>

    @Query("DELETE FROM vente_items WHERE vente_id = :venteId")
    suspend fun deleteItemsForVente(venteId: Int)

    @Query("SELECT COUNT(*) FROM vente_items WHERE vente_id = :venteId")
    suspend fun getItemsCountForVente(venteId: Int): Int

    @Query("""
    SELECT * FROM ventes 
    WHERE source = :source AND created_at >= :start AND created_at < :end 
    ORDER BY created_at ASC
""")
    suspend fun getVentesBySourceBetween(source: String, start: String, end: String): List<VenteEntity>
}