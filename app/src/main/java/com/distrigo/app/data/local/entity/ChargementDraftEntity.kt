package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An unfinished stock movement between the dépôt and the camion.
 *
 * The fourth table of its shape, after [PurchaseDraftEntity], [VenteDraftEntity] and
 * [TourneeVenteDraftEntity], and detached from `chargements` for the same reasons: no foreign key,
 * no shared status column, no draft rows anywhere a stock report can see them.
 *
 * ### One table, two kinds
 *
 * [single_product_id] is the discriminator, and it earns the unique index on it:
 *
 *  - `null` — an ordinary Brouillon from "Nouveau chargement". Listed on the Brouillons screen,
 *    counted by the chip, resumable from the FAB sheet. Any number of these can coexist, because
 *    SQLite treats NULLs as distinct in a unique index.
 *  - set — the private editing state of the single-product "Modifier" card. **Never listed and
 *    never counted.** It exists only so that a crash, a flat battery or a swipe-away cannot lose
 *    what someone typed into that card. The unique index is what makes "at most one pending edit
 *    per product" a fact about the database rather than a convention the UI has to remember.
 *
 * Both kinds are written by the same autosave and cleared by the same commit, which is why they
 * share a table rather than living in two that would differ only in a name.
 *
 * Lines live in [items_json] rather than a child table because a draft is rewritten every few
 * hundred milliseconds while someone works a quantity stepper — a child table would mean
 * delete-all + re-insert N rows per keystroke, where this is a single-row upsert.
 */
@Entity(
    tableName = "chargement_drafts",
    indices = [Index(value = ["single_product_id"], unique = true)]
)
data class ChargementDraftEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** See the class KDoc: null is a listed Brouillon, set is one product's invisible edit state. */
    val single_product_id: Int?,

    val items_json : String,    // Gson: List<ChargementDraftLine>
    val note       : String,
    /** "Effectué par". Written onto the movement, so the draft is the only place it can survive. */
    val user_name  : String,

    val item_count : Int,       // card + "Brouillons (N)" without parsing the JSON
    val created_at : String,
    val updated_at : String     // drives "il y a 12 min", and orders the list
)
