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
    // ── Inventaire ──
    data object InventaireGraph   : Screen("inventaire_graph")
    data object InventaireHome    : Screen("inventaire_home")
    data object InventaireDetail  : Screen("inventaire_detail/{sessionId}") {
        fun createRoute(sessionId: Int) = "inventaire_detail/$sessionId"
    }
    data object InventaireSessionGraph         : Screen("inventaire_session_graph")
    data object InventaireSessionScan          : Screen("inventaire_session_scan")
    data object InventaireSessionQuantity      : Screen("inventaire_session_quantity/{productId}") {
        fun createRoute(productId: Int) = "inventaire_session_quantity/$productId"
    }
    data object InventaireSessionConfirmed     : Screen("inventaire_session_confirmed")
    data object InventaireSessionReview        : Screen("inventaire_session_review")
    data object InventaireSessionReadyToFinish : Screen("inventaire_session_ready")
    data object InventaireSessionSummary       : Screen("inventaire_session_summary")

    // ── Clients ──
    data object ClientsGraph          : Screen("clients_graph")
    data object ClientsHome           : Screen("clients_home")
    data object ClientsForm           : Screen("clients_form?clientId={clientId}") {
        fun createRoute(clientId: Int? = null) =
            "clients_form" + if (clientId != null) "?clientId=$clientId" else ""
    }
    data object ClientsDetail         : Screen("clients_detail/{clientId}") {
        fun createRoute(clientId: Int) = "clients_detail/$clientId"
    }
    data object ClientsRetourForm     : Screen("clients_retour_form/{clientId}") {
        fun createRoute(clientId: Int) = "clients_retour_form/$clientId"
    }
    data object ClientsRetourHistory  : Screen("clients_retour_history/{clientId}") {
        fun createRoute(clientId: Int) = "clients_retour_history/$clientId"
    }
    data object ClientsFactureHistory : Screen("clients_facture_history/{clientId}") {
        fun createRoute(clientId: Int) = "clients_facture_history/$clientId"
    }

    // ── Fournisseurs ──
    data object SuppliersGraph         : Screen("suppliers_graph")
    data object SuppliersHome          : Screen("suppliers_home")
    data object SuppliersForm          : Screen("suppliers_form?supplierId={supplierId}") {
        fun createRoute(supplierId: Int? = null) =
            "suppliers_form" + if (supplierId != null) "?supplierId=$supplierId" else ""
    }
    data object SuppliersDetail        : Screen("suppliers_detail/{supplierId}") {
        fun createRoute(supplierId: Int) = "suppliers_detail/$supplierId"
    }
    data object SuppliersNewAchat      : Screen("suppliers_new_achat/{supplierId}") {
        fun createRoute(supplierId: Int) = "suppliers_new_achat/$supplierId"
    }
    data object SuppliersRetourForm    : Screen("suppliers_retour_form/{supplierId}") {
        fun createRoute(supplierId: Int) = "suppliers_retour_form/$supplierId"
    }
    data object SuppliersRetourHistory : Screen("suppliers_retour_history/{supplierId}") {
        fun createRoute(supplierId: Int) = "suppliers_retour_history/$supplierId"
    }
    data object SuppliersAchatHistory  : Screen("suppliers_achat_history/{supplierId}") {
        fun createRoute(supplierId: Int) = "suppliers_achat_history/$supplierId"
    }

    // ── Achats ──
    data object AchatsGraph  : Screen("achats_graph")
    data object AchatsHome   : Screen("achats_home")
    data object AchatsForm   : Screen("achats_form?orderId={orderId}") {
        fun createRoute(orderId: Int? = null) =
            "achats_form" + if (orderId != null) "?orderId=$orderId" else ""
    }
    data object AchatsDetail : Screen("achats_detail/{orderId}") {
        fun createRoute(orderId: Int) = "achats_detail/$orderId"
    }

    // ── Ventes (Dépôt) ──
    data object VentesGraph  : Screen("ventes_graph")
    data object VentesHome   : Screen("ventes_home")
    data object VentesDetail : Screen("ventes_detail/{venteId}") {
        fun createRoute(venteId: Int) = "ventes_detail/$venteId"
    }

    // ── Vente Form (multi-step nested graph, shared by Ventes tab and Client "Nouvelle facture") ──
    data object VenteFormGraph : Screen("vente_form_graph?venteId={venteId}&clientId={clientId}") {
        fun createRoute(venteId: Int? = null, clientId: Int? = null): String {
            val params = buildList {
                if (venteId != null) add("venteId=$venteId")
                if (clientId != null) add("clientId=$clientId")
            }
            return "vente_form_graph" + if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        }
    }
    data object VenteFormClient       : Screen("vente_form_client")
    data object VenteFormClientPicker : Screen("vente_form_client_picker")
    data object VenteFormProducts     : Screen("vente_form_products")
    data object VenteFormCart         : Screen("vente_form_cart")
    data object VenteFormValidation   : Screen("vente_form_validation")
}