package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.distrigo.app.data.local.entity.TourneeEntity

@Dao
interface TourneeDao {

    @Insert
    suspend fun insertTournee(tournee: TourneeEntity): Long

    @Query("SELECT * FROM tournees ORDER BY id DESC")
    suspend fun getAllTournees(): List<TourneeEntity>

    @Query("SELECT * FROM tournees WHERE id = :id")
    suspend fun getTourneeById(id: Int): TourneeEntity?

    @Query("SELECT * FROM tournees WHERE status = 'ouverte' LIMIT 1")
    suspend fun getOpenTournee(): TourneeEntity?

    /**
     * Every tournée with the four figures its card shows, in one query.
     *
     * The list used to be built by loading every sale of every tournée — and, per sale, counting its
     * lines and looking up its client — only to reduce them to these four numbers: 1 + 2T + 2V
     * queries, grown by the whole sales history, re-run whenever the hub opens and after every van
     * sale. This is one GROUP BY over the ventes(tournee_id) index, however long the history.
     *
     * The figures: the clients visited (see [TourneeVisits] — not only those who bought), the number
     * of sales, their total, and what is still unpaid on them. LEFT JOIN, so a tournée with no sales
     * comes back with zeros rather than not at all. Same order as [getAllTournees].
     */
    @Query("""
        SELECT t.*,
               (SELECT COUNT(*) FROM tournee_clients tc
                 WHERE tc.tournee_id = t.id
                   AND (tc.status = 'visite'
                        OR tc.client_id IN (SELECT v2.client_id FROM ventes v2 WHERE v2.tournee_id = t.id)))
             + (SELECT COUNT(DISTINCT v3.client_id) FROM ventes v3
                 WHERE v3.tournee_id = t.id
                   AND v3.client_id NOT IN (SELECT tc2.client_id FROM tournee_clients tc2 WHERE tc2.tournee_id = t.id))
                                                            AS clients_count,
               COUNT(v.id)                                  AS ventes_count,
               COALESCE(SUM(v.total), 0.0)                  AS total_ventes,
               COALESCE(SUM(v.total - v.montant_paye), 0.0) AS reste_total
        FROM tournees t
        LEFT JOIN ventes v ON v.tournee_id = t.id
        GROUP BY t.id
        ORDER BY t.id DESC
    """)
    suspend fun getAllTourneeSummaries(): List<TourneeSummaryRow>

    /** [getAllTourneeSummaries] for one tournée — the open-tournée banner needs no sales either. */
    @Query("""
        SELECT t.*,
               (SELECT COUNT(*) FROM tournee_clients tc
                 WHERE tc.tournee_id = t.id
                   AND (tc.status = 'visite'
                        OR tc.client_id IN (SELECT v2.client_id FROM ventes v2 WHERE v2.tournee_id = t.id)))
             + (SELECT COUNT(DISTINCT v3.client_id) FROM ventes v3
                 WHERE v3.tournee_id = t.id
                   AND v3.client_id NOT IN (SELECT tc2.client_id FROM tournee_clients tc2 WHERE tc2.tournee_id = t.id))
                                                            AS clients_count,
               COUNT(v.id)                                  AS ventes_count,
               COALESCE(SUM(v.total), 0.0)                  AS total_ventes,
               COALESCE(SUM(v.total - v.montant_paye), 0.0) AS reste_total
        FROM tournees t
        LEFT JOIN ventes v ON v.tournee_id = t.id
        WHERE t.id = :id
        GROUP BY t.id
    """)
    suspend fun getTourneeSummary(id: Int): TourneeSummaryRow?

    @Query("UPDATE tournees SET status = :status, date_fin = :dateFin WHERE id = :id")
    suspend fun updateTourneeStatus(id: Int, status: String, dateFin: String?)

    @Query("""
        UPDATE tournees
        SET nom = :nom, wilaya_name = :wilayaName, commune_name = :communeName, note = :note
        WHERE id = :id
    """)
    suspend fun updateTourneeFields(
        id: Int, nom: String, wilayaName: String?, communeName: String?, note: String?
    )

    @Query("DELETE FROM tournees WHERE id = :id")
    suspend fun deleteTourneeById(id: Int)
}

/**
 * A tournée's « client(s) visité(s) », as the Dashboard counts them: a client is visited once marked « visite » or
 * once they bought on the tournée, so a visit without a purchase counts. It used to be the clients who bought.
 */
object TourneeVisits {
    fun count(visitedOnList: Collection<Int>, buyers: Collection<Int>): Int = (visitedOnList + buyers).toSet().size
}

/** A tournée row with the counters [TourneeDao.getAllTourneeSummaries] computes alongside it. */
data class TourneeSummaryRow(
    @Embedded val tournee: TourneeEntity,
    val clients_count: Int,
    val ventes_count: Int,
    val total_ventes: Double,
    val reste_total: Double
)
