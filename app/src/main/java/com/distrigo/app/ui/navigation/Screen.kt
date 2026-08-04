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

    // ── Produits ──
    data object ProduitsGraph     : Screen("produits_graph")
    data object ProduitsHome      : Screen("produits_home")
    data object ProduitsForm      : Screen("produits_form?productId={productId}") {
        fun createRoute(productId: Int? = null) =
            "produits_form" + if (productId != null) "?productId=$productId" else ""
    }
    data object ProduitsDetail    : Screen("produits_detail/{productId}") {
        fun createRoute(productId: Int) = "produits_detail/$productId"
    }
    data object ProduitsInfoGenerales : Screen("produits_info_generales/{productId}") {
        fun createRoute(productId: Int) = "produits_info_generales/$productId"
    }
    data object ProduitsStockPrix : Screen("produits_stock_prix/{productId}") {
        fun createRoute(productId: Int) = "produits_stock_prix/$productId"
    }

    // ── Produits · Mouvements (رسم فرعي متداخل) ──
    data object ProduitsMovementsGraph  : Screen("produits_movements_graph/{productId}") {
        fun createRoute(productId: Int) = "produits_movements_graph/$productId"
    }
    data object ProduitsMovementsList   : Screen("produits_movements_list")
    data object ProduitsMovementFilters : Screen("produits_movement_filters")
    data object ProduitsMovementDetail  : Screen("produits_movement_detail/{movementId}") {
        fun createRoute(movementId: Int) = "produits_movement_detail/$movementId"
    }
}