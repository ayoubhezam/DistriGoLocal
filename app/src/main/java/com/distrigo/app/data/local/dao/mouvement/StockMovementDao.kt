package com.distrigo.app.data.local.dao.mouvement

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.distrigo.app.data.local.entity.mouvement.StockMovementEntity
import kotlinx.coroutines.flow.Flow

/** The Mouvements screen's three figures: how many movements, what came in, what went out. */
data class MovementTotals(val count: Int, val entrees: Double, val sorties: Double)

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

    // The Mouvements list's filtering is built per request (MovementListSql): one clause per filter
    // that is set, where this file held one fixed query with `(:x IS NULL OR …)` for every filter.

    /** A page (or all) of a Mouvements list — see `MovementListSql`, built per request. */
    @RawQuery
    suspend fun pageMovements(query: SupportSQLiteQuery): List<StockMovementEntity>

    /** A `MovementListSql.totals` query, re-run whenever a movement is written. */
    @RawQuery(observedEntities = [StockMovementEntity::class])
    fun observeMovementTotals(query: SupportSQLiteQuery): Flow<MovementTotals>

    /** A `MovementListSql.count` query, once — the filter sheet's button. */
    @RawQuery
    suspend fun countMovements(query: SupportSQLiteQuery): Int

    /** A product's latest [limit] movements, newest first: the product detail's short list. */
    @Query("SELECT * FROM stock_movements WHERE product_id = :productId ORDER BY created_at DESC, id DESC LIMIT :limit")
    suspend fun getRecentMovementsForProduct(productId: Int, limit: Int): List<StockMovementEntity>


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