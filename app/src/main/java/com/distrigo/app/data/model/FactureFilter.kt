package com.distrigo.app.data.model

enum class FactureFilter(val label: String) {
    TOUTES("Toutes"),
    PAYEE("Payées"),
    PARTIELLE("Partielles"),
    IMPAYEE("Impayées"),
    VERSEMENT("Versements")
}