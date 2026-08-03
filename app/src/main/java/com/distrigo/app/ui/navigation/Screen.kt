package com.distrigo.app.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")

    // ── Pertes ──
    data object PertesGraph : Screen("pertes_graph")
    data object PertesHome  : Screen("pertes_home")
    data object PertesList  : Screen("pertes_list/{typeId}") {
        fun createRoute(typeId: Int) = "pertes_list/$typeId"
    }
    data object PertesForm  : Screen("pertes_form/{typeId}?perteId={perteId}") {
        fun createRoute(typeId: Int, perteId: Int? = null) =
            "pertes_form/$typeId" + if (perteId != null) "?perteId=$perteId" else ""
    }

    // ── Charges ──
    data object ChargesGraph    : Screen("charges_graph")
    data object ChargesHome     : Screen("charges_home")
    data object ChargesSubTypes : Screen("charges_subtypes/{typeId}") {
        fun createRoute(typeId: Int) = "charges_subtypes/$typeId"
    }
    data object ChargesList     : Screen("charges_list/{subtypeId}") {
        fun createRoute(subtypeId: Int) = "charges_list/$subtypeId"
    }
    data object ChargesForm     : Screen("charges_form/{subtypeId}?chargeId={chargeId}") {
        fun createRoute(subtypeId: Int, chargeId: Int? = null) =
            "charges_form/$subtypeId" + if (chargeId != null) "?chargeId=$chargeId" else ""
    }
}