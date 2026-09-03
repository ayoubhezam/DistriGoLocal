package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An unfinished Achats form, kept so a mid-form interruption is resumable.
 *
 * Deliberately detached from [PurchaseOrderEntity] and [PurchaseOrderItemEntity]: no foreign key,
 * no shared status column, no draft rows in the real purchase tables. Nothing about existing
 * purchase queries, supplier ledgers, dashboards or reports has to know this table exists.
 *
 * Lines live in [items_json] rather than a child table because a draft is rewritten every few
 * hundred milliseconds while someone works a quantity stepper — a child table would mean
 * delete-all + re-insert N rows per keystroke, where this is a single-row upsert. Drafts are never
 * queried by product, never joined and never reported on, so the blob costs nothing.
 *
 * [source_order_id] distinguishes the two kinds:
 *  - `null` — a new purchase that was never committed.
 *  - set    — an unsaved edit of an already-committed bon. [base_fingerprint] records the form as
 *             it was prefilled from that bon, so a resume can tell whether the bon moved
 *             underneath (see DraftFingerprint). The unique index makes a second concurrent edit
 *             draft for the same bon impossible; SQLite treats NULLs as distinct in a unique
 *             index, so new-purchase drafts still coexist freely.
 */
@Entity(
    tableName = "purchase_drafts",
    indices = [Index(value = ["source_order_id"], unique = true)]
)
data class PurchaseDraftEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val supplier_id  : Int?,      // null — a draft can exist before step 1 is finished
    val supplier_name: String?,   // denormalised for the card, as PurchaseOrderEntity does
    val items_json   : String,    // Gson: List<DraftLine>
    val note         : String,
    val montant_paye : String,    // the raw user string, not a Double — see PurchaseViewModel
    val item_count   : Int,       // card + "Brouillons (N)" without parsing the JSON
    val total        : Double,    // card only
    /**
     * Route of the destination that was on screen at the last write.
     *
     * Recorded for a resume that lands on the step the user left. Nothing reads it yet — a resume
     * opens at the graph's start destination — so it is a stored fact rather than a behaviour.
     * Note it is not the *deepest* step reached: backing out rewrites it with the shallower one.
     */
    val last_step    : String,
    val created_at   : String,
    val updated_at   : String,    // drives "il y a 12 min", and orders the list

    // ── edit mode ────────────────────────────────────────────────────────────
    val source_order_id : Int?    = null,
    val base_fingerprint: String? = null,
    val base_captured_at: String? = null
)
