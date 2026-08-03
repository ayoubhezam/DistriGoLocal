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
}