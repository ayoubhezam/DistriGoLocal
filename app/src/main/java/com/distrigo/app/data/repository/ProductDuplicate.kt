package com.distrigo.app.data.repository

/** Which of a product's identifying fields another live product already has. */
enum class ProductDuplicate(val message: String) {
    NAME("Ce nom de produit est déjà enregistré."),
    BARCODE("Ce code-barres est déjà enregistré."),
}

/** Why a write with more than MAX_BARCODES_PER_PRODUCT codes is refused. */
const val TOO_MANY_BARCODES = "Un produit ne peut pas avoir plus de 30 codes-barres."
