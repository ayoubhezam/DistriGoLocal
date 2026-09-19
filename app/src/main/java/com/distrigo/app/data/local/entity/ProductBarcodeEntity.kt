package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One barcode of one product.
 *
 * ### Why a table, and the primary code
 *
 * A product answers to several codes — a supplier's new packaging, a second EAN printed on the same
 * unit — so the codes are an ordered, variable-length list, kept here the way [ProductImageEntity]
 * keeps photos. `position` is dense and zero-based within a product, and **position 0 is the primary
 * code**: the one `products.barcode` mirrors, and the one every single-code surface (the product rows,
 * the Inventaire list, the Corbeille, the export's first code) keeps reading. ProductRepository keeps
 * the two equal inside the transaction of every write.
 *
 * ### Uniqueness
 *
 * A code belongs to at most one live product. That is checked in the repository, not by a unique
 * index: a product in the bin keeps its rows so a restore brings them back, and an index cannot tell a
 * binned product's code from a live one's. [code] is stored trimmed; comparisons are exact.
 *
 * ### `units`
 *
 * How many of the product's units one scan of this code stands for. Always 1 today — every code is an
 * alias of the unit. It exists so that a carton's own code (a DUN-14 standing for twelve units) can be
 * recorded later without another migration; nothing reads it yet.
 */
@Entity(
    tableName = "product_barcodes",
    indices = [
        Index(value = ["product_id", "position"]),
        Index(value = ["code"]),
        Index(value = ["uuid"], unique = true),
    ]
)
data class ProductBarcodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val product_id: Int,
    val code: String,
    /** Dense, zero-based, unique within a product. 0 is the primary code. */
    val position: Int,
    @ColumnInfo(defaultValue = "1")
    val units: Int = 1,
    val created_at: String = java.time.Instant.now().toString(),
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis()
)

/**
 * The ceiling on codes per product. A product decision, enforced in the repository so that no caller —
 * the form, the import — can exceed it.
 */
const val MAX_BARCODES_PER_PRODUCT = 30
