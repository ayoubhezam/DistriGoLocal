package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An unfinished Tournée Vente form, kept so a mid-form interruption is resumable.
 *
 * The third table of its shape, after [PurchaseDraftEntity] and [VenteDraftEntity], and detached
 * from the real vente tables for the same reasons: no foreign key, no shared status column, no
 * draft rows anywhere a ledger, dashboard or report can see them.
 *
 * A third table rather than a column on `vente_drafts`. The two look similar and are not: a Dépôt
 * sale carries "Effectué par" and may be an unsaved edit of a committed vente, while a van sale
 * carries neither and instead belongs to a tournée. Sharing would mean a nullable `tournee_id` and
 * a nullable `user_name` that each mean "this row is the other kind", plus a unique index on
 * `source_vente_id` that has nothing to constrain here.
 *
 * [tournee_id] is not null and is indexed: every list of these is "the drafts of *this* tournée",
 * so the index is the query, not an optimisation. There is no unique index — the tournée form is
 * create-only, so any number of unfinished sales can sit under one tournée at once, exactly as any
 * number of new-bon drafts can under Achats.
 *
 * Lines live in [items_json] rather than a child table because a draft is rewritten every few
 * hundred milliseconds while someone works a quantity stepper — a child table would mean
 * delete-all + re-insert N rows per keystroke, where this is a single-row upsert.
 */
@Entity(
    tableName = "tournee_vente_drafts",
    indices = [Index(value = ["tournee_id"])]
)
data class TourneeVenteDraftEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** The round this sale belongs to. Not null: a van sale without a tournée has no meaning. */
    val tournee_id : Int,

    val client_id  : Int?,      // null — a draft can exist before the client step is finished
    val client_name: String?,   // denormalised for the card, as VenteEntity does
    val items_json : String,    // Gson: List<TourneeVenteDraftLine>
    val note       : String,
    val montant_paye: String,   // the raw user string, not a Double — as the other two drafts do

    val item_count : Int,       // card + "Brouillons (N)" without parsing the JSON
    val total      : Double,    // card only
    /** See [PurchaseDraftEntity.last_step]. Written, never read for navigation. */
    val last_step  : String,
    val created_at : String,
    val updated_at : String     // drives "il y a 12 min", and orders the list
)
