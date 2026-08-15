package com.distrigo.app.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")

    // ── Outer shell (MainActivity): single root NavHost — bottom-tab destinations. "Plus" itself
    // is an 80%-width draggable overlay drawer (not a NavHost destination); the routes below are
    // the real destinations its menu items navigate to. ──
    data object TabDashboard : Screen("tab_dashboard")
    data object TabVentes    : Screen("tab_ventes")
    data object TabProduits  : Screen("tab_produits")
    data object TabAchats    : Screen("tab_achats")
    data object PlusClients : Screen("plus_clients?clientId={clientId}") {
        fun createRoute(clientId: Int? = null) =
            "plus_clients" + if (clientId != null) "?clientId=$clientId" else ""
    }
    data object PlusFournisseurs : Screen("plus_fournisseurs")
    data object PlusCharges      : Screen("plus_charges")
    data object PlusPertes       : Screen("plus_pertes")
    data object PlusInventaire   : Screen("plus_inventaire")
    data object PlusRapports     : Screen("plus_rapports")
    data object PlusParametres   : Screen("plus_parametres")

    // ── Ventes Hub (TabVentes content): Dépôt Vente / Tournées / Stock Camion / Rapports ──
    data object VentesHubGraph       : Screen("ventes_hub_graph")
    data object VentesHubMenu        : Screen("ventes_hub_menu")
    data object VentesHubDepotVente  : Screen("ventes_hub_depot_vente")
    data object VentesHubTournees    : Screen("ventes_hub_tournees")
    data object VentesHubStockCamion : Screen("ventes_hub_stock_camion")
    data object VentesHubRapports    : Screen("ventes_hub_rapports")

    // ── Chargement Form (self-contained 2-step wizard: Produits ↔ Panier) ──
    data object ChargementFormGraph    : Screen("chargement_form_graph")
    data object ChargementFormProducts : Screen("chargement_form_products")
    data object ChargementFormCart     : Screen("chargement_form_cart")

    // ── Pertes ──
    data object PertesGraph : Screen("pertes_graph")
    data object PertesHome  : Screen("pertes_home")
    data object PertesList  : Screen("pertes_list/{typeId}") {
        fun createRoute(typeId: Int) = "pertes_list/$typeId"
    }
    // ── Pertes Form (multi-step nested graph) ──
    data object PertesFormGraph : Screen("pertes_form_graph/{typeId}?perteId={perteId}") {
        fun createRoute(typeId: Int, perteId: Int? = null) =
            "pertes_form_graph/$typeId" + if (perteId != null) "?perteId=$perteId" else ""
    }
    data object PertesFormDetails : Screen("pertes_form_details")
    data object PertesFormSummary : Screen("pertes_form_summary")

    // ── Charges ──
    data object ChargesGraph    : Screen("charges_graph")
    data object ChargesHome     : Screen("charges_home")
    data object ChargesSubTypes : Screen("charges_subtypes/{typeId}") {
        fun createRoute(typeId: Int) = "charges_subtypes/$typeId"
    }
    data object ChargesList     : Screen("charges_list/{subtypeId}") {
        fun createRoute(subtypeId: Int) = "charges_list/$subtypeId"
    }
    // ── Charges Form (multi-step nested graph) ──
    data object ChargesFormGraph : Screen("charges_form_graph/{subtypeId}?chargeId={chargeId}") {
        fun createRoute(subtypeId: Int, chargeId: Int? = null) =
            "charges_form_graph/$subtypeId" + if (chargeId != null) "?chargeId=$chargeId" else ""
    }
    data object ChargesFormDetails : Screen("charges_form_details")
    data object ChargesFormSummary : Screen("charges_form_summary")

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
    // ── Client Retour Form (multi-step nested graph) ──
    data object ClientsRetourFormGraph : Screen("clients_retour_form_graph/{clientId}") {
        fun createRoute(clientId: Int) = "clients_retour_form_graph/$clientId"
    }
    data object ClientsRetourFormClient       : Screen("clients_retour_form_client")
    data object ClientsRetourFormClientPicker : Screen("clients_retour_form_client_picker")
    data object ClientsRetourFormProducts     : Screen("clients_retour_form_products")
    data object ClientsRetourFormCart         : Screen("clients_retour_form_cart")
    data object ClientsRetourFormSummary      : Screen("clients_retour_form_summary")

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
    // ── Suppliers Retour Form (multi-step nested graph) ──
    data object SuppliersRetourFormGraph : Screen("suppliers_retour_form_graph/{supplierId}") {
        fun createRoute(supplierId: Int) = "suppliers_retour_form_graph/$supplierId"
    }
    data object SuppliersRetourFormProducts : Screen("suppliers_retour_form_products")
    data object SuppliersRetourFormCart     : Screen("suppliers_retour_form_cart")
    data object SuppliersRetourFormSummary  : Screen("suppliers_retour_form_summary")
    data object SuppliersRetourHistory : Screen("suppliers_retour_history/{supplierId}") {
        fun createRoute(supplierId: Int) = "suppliers_retour_history/$supplierId"
    }
    data object SuppliersAchatHistory  : Screen("suppliers_achat_history/{supplierId}") {
        fun createRoute(supplierId: Int) = "suppliers_achat_history/$supplierId"
    }

    // ── Achats ──
    data object AchatsGraph  : Screen("achats_graph")
    data object AchatsHome   : Screen("achats_home")
    data object AchatsDetail : Screen("achats_detail/{orderId}") {
        fun createRoute(orderId: Int) = "achats_detail/$orderId"
    }

    // ── Purchase Form (multi-step nested graph, shared by Achats tab and Supplier "Nouvel achat") ──
    data object PurchaseFormGraph : Screen("purchase_form_graph?orderId={orderId}&supplierId={supplierId}") {
        fun createRoute(orderId: Int? = null, supplierId: Int? = null): String {
            val params = buildList {
                if (orderId != null) add("orderId=$orderId")
                if (supplierId != null) add("supplierId=$supplierId")
            }
            return "purchase_form_graph" + if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        }
    }
    data object PurchaseFormSupplier       : Screen("purchase_form_supplier")
    data object PurchaseFormSupplierPicker : Screen("purchase_form_supplier_picker")
    data object PurchaseFormProducts       : Screen("purchase_form_products")
    data object PurchaseFormCart           : Screen("purchase_form_cart")
    data object PurchaseFormValidation     : Screen("purchase_form_validation")

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

    // ── Tournées ──
    data object TourneesGraph  : Screen("tournees_graph")
    data object TourneesHome   : Screen("tournees_home")
    data object TourneesDetail : Screen("tournees_detail/{tourneeId}") {
        fun createRoute(tourneeId: Int) = "tournees_detail/$tourneeId"
    }
    data object TourneeForm : Screen("tournee_form?tourneeId={tourneeId}") {
        fun createRoute(tourneeId: Int? = null) =
            "tournee_form" + if (tourneeId != null) "?tourneeId=$tourneeId" else ""
    }
    data object TourneesAddClients : Screen("tournees_add_clients/{tourneeId}") {
        fun createRoute(tourneeId: Int) = "tournees_add_clients/$tourneeId"
    }
    data object TourneeVenteFormGraph : Screen("tournee_vente_form_graph/{tourneeId}?clientId={clientId}") {
        fun createRoute(tourneeId: Int, clientId: Int? = null) =
            "tournee_vente_form_graph/$tourneeId" + if (clientId != null) "?clientId=$clientId" else ""
    }
    data object TourneeVenteFormClient       : Screen("tournee_vente_form_client")
    data object TourneeVenteFormClientPicker : Screen("tournee_vente_form_client_picker")
    data object TourneeVenteFormProducts     : Screen("tournee_vente_form_products")
    data object TourneeVenteFormCart         : Screen("tournee_vente_form_cart")
    data object TourneeVenteFormValidation   : Screen("tournee_vente_form_validation")
    data object TourneesVenteDetail : Screen("tournees_vente_detail/{tourneeId}/{venteId}") {
        fun createRoute(tourneeId: Int, venteId: Int) = "tournees_vente_detail/$tourneeId/$venteId"
    }
}