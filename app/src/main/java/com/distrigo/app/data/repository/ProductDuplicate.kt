package com.distrigo.app.data.repository

import com.distrigo.app.R

/** Which of a product's identifying fields another live product already has, and the words that say so. */
enum class ProductDuplicate(@androidx.annotation.StringRes val text: Int) {
    NAME(com.distrigo.app.R.string.error_product_name_taken),
    BARCODE(com.distrigo.app.R.string.error_barcode_taken_generic),
}

/** Why a write with more than MAX_BARCODES_PER_PRODUCT codes is refused, as the import still writes it (French until its text moves). */
const val TOO_MANY_BARCODES = "Un produit ne peut pas avoir plus de 30 codes-barres."
