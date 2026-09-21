package com.distrigo.app.data.local.dao.mouvement

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity

/** A client or a supplier a product's movements name. */
data class PartyRow(val id: Int, val name: String)

@Dao
interface StockMovementDao {

    @Insert
    suspend fun insert(movement: StockMovementEntity): Long

    @Insert
    suspend fun insertAll(movements: List<StockMovementEntity>)

    @Query("SELECT * FROM stock_movements WHERE product_id = :productId ORDER BY created_at DESC")
    suspend fun getMovementsForProduct(productId: Int): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements ORDER BY created_at DESC")
    suspend fun getAllMovements(): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements WHERE id = :id")
    suspend fun getMovementById(id: Int): StockMovementEntity?

    @Query("DELETE FROM stock_movements WHERE source_type = :sourceType AND source_id = :sourceId")
    suspend fun deleteBySource(sourceType: String, sourceId: Int)

    // ── Filtrage combiné : produit + période + sens + type + emplacement + partie ──
    //
    // NULL dans un paramètre = filtre ignoré. The period is [dateFrom, dateBefore), instant bounds
    // from BusinessDates.dayRangeBounds.
    //
    // A movement names its document by [source_type] and [source_id]; whom it was with is that
    // document's client or supplier. `party` keeps the kinds that belong to a client or to a
    // supplier, and `partyId` narrows to one of them — a client id can only match a client-side
    // document, so the two conditions never disagree.
    //
    // **[filtered] and [countFiltered] must move together**: the second exists only to say how many
    // rows the first would return, and a predicate in one and not the other would make the sheet's
    // count a lie.
    @Query("""
        SELECT * FROM stock_movements
        WHERE (:productId IS NULL OR product_id = :productId)
          AND (:dateFrom IS NULL OR created_at >= :dateFrom)
          AND (:dateBefore IS NULL OR created_at < :dateBefore)
          AND (:direction IS NULL OR direction = :direction)
          AND (:emplacement IS NULL OR emplacement = :emplacement)
          AND (:allTypes OR type IN (:types))
          AND (:party IS NULL
               OR (:party = 'client' AND source_type IN ('vente', 'retour_client'))
               OR (:party = 'fournisseur' AND source_type IN ('purchase_order', 'retour_fournisseur')))
          AND (:partyId IS NULL OR
               (source_type = 'vente' AND EXISTS (SELECT 1 FROM ventes v WHERE v.id = source_id AND v.client_id = :partyId))
            OR (source_type = 'retour_client' AND EXISTS (SELECT 1 FROM retour_client r WHERE r.id = source_id AND r.client_id = :partyId))
            OR (source_type = 'purchase_order' AND EXISTS (SELECT 1 FROM purchase_orders o WHERE o.id = source_id AND o.supplier_id = :partyId))
            OR (source_type = 'retour_fournisseur' AND EXISTS (SELECT 1 FROM retour_fournisseur f WHERE f.id = source_id AND f.supplier_id = :partyId)))
        ORDER BY created_at DESC
    """)
    suspend fun filtered(
        productId: Int?,
        dateFrom: String?,
        dateBefore: String?,
        direction: String?,
        emplacement: String?,
        allTypes: Boolean,
        types: List<String>,
        party: String?,
        partyId: Int?,
    ): List<StockMovementEntity>

    /** How many rows [filtered] would return — the same predicate, counted. */
    @Query("""
        SELECT COUNT(*) FROM stock_movements
        WHERE (:productId IS NULL OR product_id = :productId)
          AND (:dateFrom IS NULL OR created_at >= :dateFrom)
          AND (:dateBefore IS NULL OR created_at < :dateBefore)
          AND (:direction IS NULL OR direction = :direction)
          AND (:emplacement IS NULL OR emplacement = :emplacement)
          AND (:allTypes OR type IN (:types))
          AND (:party IS NULL
               OR (:party = 'client' AND source_type IN ('vente', 'retour_client'))
               OR (:party = 'fournisseur' AND source_type IN ('purchase_order', 'retour_fournisseur')))
          AND (:partyId IS NULL OR
               (source_type = 'vente' AND EXISTS (SELECT 1 FROM ventes v WHERE v.id = source_id AND v.client_id = :partyId))
            OR (source_type = 'retour_client' AND EXISTS (SELECT 1 FROM retour_client r WHERE r.id = source_id AND r.client_id = :partyId))
            OR (source_type = 'purchase_order' AND EXISTS (SELECT 1 FROM purchase_orders o WHERE o.id = source_id AND o.supplier_id = :partyId))
            OR (source_type = 'retour_fournisseur' AND EXISTS (SELECT 1 FROM retour_fournisseur f WHERE f.id = source_id AND f.supplier_id = :partyId)))
    """)
    suspend fun countFiltered(
        productId: Int?,
        dateFrom: String?,
        dateBefore: String?,
        direction: String?,
        emplacement: String?,
        allTypes: Boolean,
        types: List<String>,
        party: String?,
        partyId: Int?,
    ): Int

    /**
     * The clients this product actually moved with — those on a vente or a retour client of it.
     *
     * Only they are offered in the filter: a list of every client in the book, most of which would
     * return nothing for this product, is a longer list that answers fewer questions.
     */
    @Query("""
        SELECT DISTINCT c.id AS id, c.name AS name FROM stock_movements m
        LEFT JOIN ventes v ON m.source_type = 'vente' AND v.id = m.source_id
        LEFT JOIN retour_client r ON m.source_type = 'retour_client' AND r.id = m.source_id
        JOIN clients c ON c.id = COALESCE(v.client_id, r.client_id)
        WHERE m.product_id = :productId
        ORDER BY c.name COLLATE NOCASE
    """)
    suspend fun clientsForProduct(productId: Int): List<PartyRow>

    /** The suppliers this product moved with — those on a bon d'achat or a retour fournisseur of it. */
    @Query("""
        SELECT DISTINCT s.id AS id, s.name AS name FROM stock_movements m
        LEFT JOIN purchase_orders o ON m.source_type = 'purchase_order' AND o.id = m.source_id
        LEFT JOIN retour_fournisseur f ON m.source_type = 'retour_fournisseur' AND f.id = m.source_id
        JOIN suppliers s ON s.id = COALESCE(o.supplier_id, f.supplier_id)
        WHERE m.product_id = :productId
        ORDER BY s.name COLLATE NOCASE
    """)
    suspend fun suppliersForProduct(productId: Int): List<PartyRow>

    @Query("SELECT DISTINCT source_label FROM stock_movements WHERE product_id = :productId ORDER BY source_label ASC")
    suspend fun getDistinctSourcesForProduct(productId: Int): List<String>
}