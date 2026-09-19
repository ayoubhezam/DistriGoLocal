package com.distrigo.app.data.model

data class Product(
    val id          : Int,
    val name        : String,
    val barcode     : String?,
    val selling_price  : Double,
    val purchase_price : Double,
    val stock       : Double,
    val min_stock   : Int,
    val unit_type   : String,
    val packages    : Int,
    val pack_size   : Int,
    val has_expiry  : Int,
    val expiry_date : String?,
    val image_uri   : String?,
    val category_name : String?,
    val category_id    : Int?,
    val supplier_name  : String?,
    val supplier_id    : Int?,
    val camion_stock: Double = 0.0,

    val sous_categorie_id   : Int?    = null,
    val sous_categorie_name : String? = null,
    val marque_id           : Int?    = null,
    val marque_name         : String? = null,

    /** Every code of the product, the primary first; [barcode] is that primary. See ProductBarcodeEntity. */
    val barcodes            : List<String> = listOfNotNull(barcode)
)

/** True when one of the product's barcodes contains [token], ignoring case: the search fields' rule. */
fun Product.barcodeContains(token: String): Boolean =
    barcodes.any { it.contains(token, ignoreCase = true) }

/** True when one of the product's barcodes is exactly [code], as a scan reads it. */
fun Product.hasBarcode(code: String): Boolean =
    code.trim().let { scanned -> barcodes.any { it.equals(scanned, ignoreCase = true) } }