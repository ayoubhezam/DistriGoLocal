package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
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

    /**
     * A tournée's sales for its detail screen, each with its client's current name and its number
     * of lines, in one query.
     *
     * Replaces [getVentesForTournee] followed by a client lookup and an item count for every sale —
     * 1 + 2V queries. The name is read from `clients`, not from the `ventes.client_name` snapshot,
     * because the live name is what the detail screen has always shown; it is aliased so it cannot
     * collide with that snapshot column. Same order as [getVentesForTournee].
     */
    @Query("""
        SELECT v.*,
               c.name AS live_client_name,
               (SELECT COUNT(*) FROM vente_items vi WHERE vi.vente_id = v.id) AS items_count
        FROM ventes v
        LEFT JOIN clients c ON c.id = v.client_id
        WHERE v.tournee_id = :tourneeId
        ORDER BY v.id DESC
    """)
    suspend fun getVentesWithDetailsForTournee(tourneeId: Int): List<TourneeVenteRow>

    @Query("SELECT * FROM ventes WHERE id = :id")
    suspend fun getVenteById(id: Int): VenteEntity?

    @Query("UPDATE ventes SET status = :status WHERE id = :id")
    suspend fun updateVenteStatus(id: Int, status: String)

    @Query("""
        UPDATE ventes
        SET note = :note, montant_paye = :montantPaye, total = :total, user_name = :userName
        WHERE id = :id
    """)
    suspend fun updateVenteFields(id: Int, note: String?, montantPaye: Double, total: Double, userName: String?)

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

    @Query("""
    SELECT * FROM ventes 
    WHERE created_at >= :start AND created_at < :end 
    ORDER BY created_at ASC
""")
    suspend fun getVentesBetween(start: String, end: String): List<VenteEntity>



    @Query("SELECT client_id, created_at FROM ventes WHERE client_id IS NOT NULL")
    suspend fun getAllVenteClientDates(): List<VenteClientDate>

    @Query("""
        SELECT vi.product_id AS product_id, SUM(vi.quantity) AS total_quantity
        FROM vente_items vi
        INNER JOIN ventes v ON v.id = vi.vente_id
        WHERE v.client_id = :clientId AND v.status = :deliveredStatus
        GROUP BY vi.product_id
    """)
    suspend fun getSoldQuantitiesForClient(clientId: Int, deliveredStatus: String): List<ProductQuantitySum>

    // ── Historique paginé (Factures & Paiements — Client Detail) ──
    @Query("""
        SELECT * FROM ventes
        WHERE client_id = :clientId
        AND (:cursorCreatedAt IS NULL OR created_at < :cursorCreatedAt)
        AND (:search = '' OR ('#' || CAST(id AS TEXT)) LIKE '%' || :search || '%' OR note LIKE '%' || :search || '%')
        AND (
            :statusFilter = 'TOUTES'
            OR (:statusFilter = 'PAYEE' AND montant_paye >= total AND total > 0)
            OR (:statusFilter = 'PARTIELLE' AND montant_paye > 0 AND montant_paye < total)
            OR (:statusFilter = 'IMPAYEE' AND montant_paye <= 0)
        )
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
    """)
    suspend fun pageVentesForClient(
        clientId: Int,
        cursorCreatedAt: String?,
        search: String,
        statusFilter: String,
        limit: Int
    ): List<VenteEntity>

    @Query("""
        SELECT COUNT(*) FROM ventes
        WHERE client_id = :clientId
        AND (:search = '' OR ('#' || CAST(id AS TEXT)) LIKE '%' || :search || '%' OR note LIKE '%' || :search || '%')
        AND (
            :statusFilter = 'TOUTES'
            OR (:statusFilter = 'PAYEE' AND montant_paye >= total AND total > 0)
            OR (:statusFilter = 'PARTIELLE' AND montant_paye > 0 AND montant_paye < total)
            OR (:statusFilter = 'IMPAYEE' AND montant_paye <= 0)
        )
    """)
    suspend fun countVentesForClient(clientId: Int, search: String, statusFilter: String): Int
}

data class VenteClientDate(
    val client_id: Int,
    val created_at: String
)

/** A sale with the two things its tournée detail row shows that the `ventes` table does not hold. */
data class TourneeVenteRow(
    @Embedded val vente: VenteEntity,
    val live_client_name: String?,
    val items_count: Int
)