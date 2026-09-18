package com.distrigo.app.data.repository

/** Which of a product's identifying fields another live product already has. */
enum class ProductDuplicate(val message: String) {
    NAME("Ce nom de produit est déjà enregistré."),
    BARCODE("Ce code-barres est déjà enregistré."),
}
