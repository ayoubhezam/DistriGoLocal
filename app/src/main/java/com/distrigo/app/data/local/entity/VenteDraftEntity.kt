package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An unfinished Dépôt Vente form, kept so a mid-form interruption is resumable.
 *
 * Shaped after [PurchaseDraftEntity] and detached from [VenteEntity]/[VenteItemEntity] for the
 * same reasons: no foreign key, no shared status column, no draft rows in the real vente tables.
 * Nothing about existing vente queries, client ledgers, dashboards or reports has to know this
 * table exists.
 *
 * A separate table rather than a shared one with `purchase_drafts`: the two forms do not hold the
 * same fields — a bon carries a supplier and a colis/expiry breakdown per line, a vente carries a
 * client and the name of whoever made the sale — and one wide table would be half-null in both
 * directions.
 *
 * Lines live in [items_json] rather than a child table because a draft is rewritten every few
 * hundred milliseconds while someone works a quantity stepper — a child table would mean
 * delete-all + re-insert N rows per keystroke, where this is a single-row upsert. Drafts are never
 * queried by product, never joined and never reported on, so the blob costs nothing.
 *
 * [source_vente_id] distinguishes the two kinds:
 *  - `null` — a new vente that was never committed.
 *  - set    — an unsaved edit of an already-committed vente. [base_fingerprint] records the form as
 *             it was prefilled from that vente, so a resume can tell whether the vente moved
 *             underneath (see VenteFingerprint). The unique index makes a second concurrent edit
 *             draft for the same vente impossible; SQLite treats NULLs as distinct in a unique
 *             index, so new-vente drafts still coexist freely.
 */
@Entity(
    tableName = "vente_drafts",
    indices = [Index(value = ["source_vente_id"], unique = true)]
)
data class VenteDraftEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val client_id  : Int?,      // null — a draft can exist before the client step is finished
    val client_name: String?,   // denormalised for the card, as VenteEntity does
    val items_json : String,    // Gson: List<VenteDraftLine>
    val note       : String,
    val montant_paye: String,   // the raw user string, not a Double — as purchase_drafts does

    /**
     * "Effectué par". Unlike every other field here it has nowhere to be read back from once the
     * vente is committed: `ProductRepository.createVente`/`updateVente` write it onto the stock
     * movements, not onto the `ventes` row. Keeping it on the draft is therefore the only way a
     * resume can give it back.
     */
    val user_name  : String,

    val item_count : Int,       // card + "Brouillons (N)" without parsing the JSON
    val total      : Double,    // card only
    val last_step  : String,    // route of the deepest step reached
    val created_at : String,
    val updated_at : String,    // drives "il y a 12 min", and orders the list

    // ── edit mode ────────────────────────────────────────────────────────────
    val source_vente_id : Int?    = null,
    val base_fingerprint: String? = null,
    val base_captured_at: String? = null
)
