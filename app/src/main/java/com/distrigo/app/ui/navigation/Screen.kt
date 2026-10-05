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
    data object PlusClients : Screen("plus_clients") {
        fun createRoute() = route
    }

    // ── Drill-down: a record opened from anywhere, on top of the screen that asked (see DrillDown) ──
    sealed class Drill(kind: String) : Screen("drill/$kind/{$DRILL_ID}") {
        private val prefix = "drill/$kind/"
        fun createRoute(id: Int) = "$prefix$id"
    }
    data object DrillClient   : Drill("client")
    data object DrillSupplier : Drill("supplier")
    data object DrillBon      : Drill("bon")
    data object DrillVente    : Drill("vente")
    data object DrillPerte    : Drill("perte")
    data object DrillInventaire : Drill("inventaire")
    data object DrillRetoursClient : Drill("retours_client")
    data object DrillRetoursFournisseur : Drill("retours_fournisseur")
    data object PlusFournisseurs : Screen("plus_fournisseurs")
    data object PlusCharges      : Screen("plus_charges")
    data object PlusPertes       : Screen("plus_pertes")
    data object PlusInventaire   : Screen("plus_inventaire")
    data object PlusRapports     : Screen("plus_rapports")
    data object PlusParametres   : Screen("plus_parametres")

    // ── Paramètres (PlusParametres content): its own NavHost, one destination per settings screen ──
    data object SettingsGraph      : Screen("settings_graph")
    data object SettingsHome       : Screen("settings_home")
    data object SettingsReceiptPrint : Screen("settings_receipt_print")
    data object SettingsPrinters     : Screen("settings_printers")
    data object SettingsCommission : Screen("settings_commission")
    data object SettingsData       : Screen("settings_data")
    data object SettingsExport     : Screen("settings_data_export")
    data object SettingsTrash      : Screen("settings_trash")
    data object SettingsDiagnostics : Screen("settings_diagnostics")

    // ── Ventes Hub (TabVentes content): Dépôt Vente / Tournées / Stock Camion ──
    data object VentesHubGraph       : Screen("ventes_hub_graph")
    data object VentesHubMenu        : Screen("ventes_hub_menu")
    data object VentesHubDepotVente  : Screen("ventes_hub_depot_vente")
    data object VentesHubTournees    : Screen("ventes_hub_tournees")
    data object VentesHubStockCamion : Screen("ventes_hub_stock_camion")

    // ── Chargement Form (self-contained 2-step wizard: Produits ↔ Panier) ──
    data object ChargementFormGraph    : Screen("chargement_form_graph")
    data object ChargementFormProducts : Screen("chargement_form_products")
    data object ChargementFormCart     : Screen("chargement_form_cart")

    // ── Pertes ──
    data object PertesGraph : Screen("pertes_graph")
    data object PertesHome  : Screen("pertes_home")
    data object PertesTypes : Screen("pertes_types")
    // ── A new perte: list, selection, summary ──
    data object PertesNewGraph   : Screen("pertes_new_graph")
    data object PertesNewList    : Screen("pertes_new_list")
    data object PertesNewCart    : Screen("pertes_new_cart")
    data object PertesNewSummary : Screen("pertes_new_summary")
    data object PertesList  : Screen("pertes_list/{typeId}") {
        fun createRoute(typeId: Int) = "pertes_list/$typeId"
    }
    // ── Perte details (read-only) ──
    data object PertesDetail : Screen("pertes_detail/{perteId}") {
        fun createRoute(perteId: Int) = "pertes_detail/$perteId"
    }

    // ── Rapports ──
    data object RapportsGraph  : Screen("rapports_graph")
    data object RapportsHome   : Screen("rapports_home")
    data object RapportsVentes : Screen("rapports_ventes")
    data object RapportsDettes : Screen("rapports_dettes")
    data object RapportsDebiteurs : Screen("rapports_debiteurs")
    /** One local day's sales, from the Ventes report's "Détail par jour". */
    data object RapportsVentesJour : Screen("rapports_ventes_jour/{day}") {
        fun createRoute(day: java.time.LocalDate) = "rapports_ventes_jour/$day"
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
    // ── Charge details (read-only) ──
    data object ChargesDetail : Screen("charges_detail/{chargeId}") {
        fun createRoute(chargeId: Int) = "charges_detail/$chargeId"
    }
    // ── Charges Form (one screen) ──
    /** No subtype: the quick entry, which picks it on the form. A charge: editing it. */
    data object ChargesForm : Screen("charges_form?subtypeId={subtypeId}&chargeId={chargeId}") {
        fun createRoute(subtypeId: Int? = null, chargeId: Int? = null) =
            "charges_form?subtypeId=${subtypeId ?: -1}&chargeId=${chargeId ?: -1}"
    }

    // ── Produits ──
    data object ProduitsGraph     : Screen("produits_graph")
    data object ProduitsHome      : Screen("produits_home")
    data object ProduitsImport     : Screen("produits_import")
    data object ProduitsForm      : Screen("produits_form?productId={productId}") {
        fun createRoute(productId: Int? = null) =
            "produits_form" + if (productId != null) "?productId=$productId" else ""
    }
    data object ProduitsDetail    : Screen("produits_detail/{productId}") {
        fun createRoute(productId: Int) = "produits_detail/$productId"
    }
    data object ProduitsPriceHistory : Screen("produits_price_history/{productId}") {
        fun createRoute(productId: Int) = "produits_price_history/$productId"
    }
    data object ProduitsPriceMovements : Screen("produits_price_movements/{productId}") {
        fun createRoute(productId: Int) = "produits_price_movements/$productId"
    }

    // ── Produits · Mouvements (رسم فرعي متداخل) ──
    data object ProduitsMovementsGraph  : Screen("produits_movements_graph/{productId}") {
        fun createRoute(productId: Int) = "produits_movements_graph/$productId"
    }
    data object ProduitsMovementsList   : Screen("produits_movements_list")
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
    data object InventaireSessionReview        : Screen("inventaire_session_review")
    data object InventaireSessionSummary       : Screen("inventaire_session_summary")

    // ── Clients ──
    data object ClientsGraph          : Screen("clients_graph")
    data object ClientsHome           : Screen("clients_home")
    data object ClientsImport      : Screen("clients_import")
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
    data object AchatsBrouillons : Screen("achats_brouillons")
    data object AchatsDetail : Screen("achats_detail/{orderId}") {
        fun createRoute(orderId: Int) = "achats_detail/$orderId"
    }

    // ── Purchase Form (multi-step nested graph, shared by Achats tab and Supplier "Nouvel achat") ──
    data object PurchaseFormGraph : Screen("purchase_form_graph?orderId={orderId}&supplierId={supplierId}&draftId={draftId}") {
        /**
         * [draftId] carries the resume decision into the graph. It is made *before* the graph is
         * entered — at the FAB, or by tapping a Brouillon card — which is what keeps a
         * process-death return from ever prompting: that path re-enters the graph without passing
         * through either.
         */
        fun createRoute(orderId: Int? = null, supplierId: Int? = null, draftId: Int? = null): String {
            val params = buildList {
                if (orderId != null) add("orderId=$orderId")
                if (supplierId != null) add("supplierId=$supplierId")
                if (draftId != null) add("draftId=$draftId")
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
    data object VentesBrouillons : Screen("ventes_brouillons")
    data object VentesDetail : Screen("ventes_detail/{venteId}") {
        fun createRoute(venteId: Int) = "ventes_detail/$venteId"
    }

    // ── Vente Form (multi-step nested graph, shared by Ventes tab and Client "Nouvelle facture") ──
    // Two entry points share the same step composables (see venteFormGraph in VenteFormNavGraph.kt):
    //  - VenteFormGraph: client/vente unknown → starts at the "choose a client" step.
    //  - VenteFormGraphDirect: client or vente already known (edit, or preselected client) → starts
    //    straight at Products, so the client-picker step is never navigated to, composed, or
    //    animated in that case.
    data object VenteFormGraph : Screen("vente_form_graph?venteId={venteId}&clientId={clientId}&draftId={draftId}") {
        /**
         * [draftId] carries the resume decision into the graph. It is made *before* the graph is
         * entered — at the FAB, or by tapping a Brouillon card — which is what keeps a
         * process-death return from ever prompting: that path re-enters the graph without passing
         * through either.
         */
        fun createRoute(venteId: Int? = null, clientId: Int? = null, draftId: Int? = null): String {
            val params = buildList {
                if (venteId != null) add("venteId=$venteId")
                if (clientId != null) add("clientId=$clientId")
                if (draftId != null) add("draftId=$draftId")
            }
            return "vente_form_graph" + if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        }
    }
    data object VenteFormGraphDirect : Screen("vente_form_graph_direct?venteId={venteId}&clientId={clientId}&draftId={draftId}&clientName={clientName}") {
        /**
         * See [VenteFormGraph.createRoute] — same arguments, entered straight at Products. [clientName],
         * when the caller has it, is what step 02's subtitle shows on its first frame, before the
         * client itself is read.
         */
        fun createRoute(venteId: Int? = null, clientId: Int? = null, draftId: Int? = null, clientName: String? = null): String {
            val params = buildList {
                if (venteId != null) add("venteId=$venteId")
                if (clientId != null) add("clientId=$clientId")
                if (draftId != null) add("draftId=$draftId")
                if (clientName != null) add("clientName=${android.net.Uri.encode(clientName)}")
            }
            return "vente_form_graph_direct" + if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        }
    }

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
    // Same direct-entry split as VenteFormGraph above — see tourneeVenteFormGraph in
    // TourneeVenteFormNavGraph.kt.
    data object TourneeVenteFormGraph : Screen("tournee_vente_form_graph/{tourneeId}?clientId={clientId}&draftId={draftId}") {
        fun createRoute(tourneeId: Int, clientId: Int? = null, draftId: Int? = null) =
            "tournee_vente_form_graph/$tourneeId" +
                listOfNotNull(
                    clientId?.let { "clientId=$it" },
                    draftId?.let { "draftId=$it" }
                ).joinToString("&").let { if (it.isEmpty()) "" else "?$it" }
    }

    /** One tournée's unfinished van sales. Scoped: a draft belongs to the round it was made on. */
    data object TourneeVenteBrouillons : Screen("tournee_vente_brouillons/{tourneeId}") {
        fun createRoute(tourneeId: Int) = "tournee_vente_brouillons/$tourneeId"
    }
    data object TourneeVenteFormGraphDirect : Screen("tournee_vente_form_graph_direct/{tourneeId}?clientId={clientId}&clientName={clientName}") {
        /** [clientName] is what step 02's subtitle shows on its first frame, before the client is read. */
        fun createRoute(tourneeId: Int, clientId: Int? = null, clientName: String? = null) =
            "tournee_vente_form_graph_direct/$tourneeId" +
                listOfNotNull(
                    clientId?.let { "clientId=$it" },
                    clientName?.let { "clientName=${android.net.Uri.encode(it)}" }
                ).joinToString("&").let { if (it.isEmpty()) "" else "?$it" }
    }
    data object TourneesVenteDetail : Screen("tournees_vente_detail/{tourneeId}/{venteId}") {
        fun createRoute(tourneeId: Int, venteId: Int) = "tournees_vente_detail/$tourneeId/$venteId"
    }
}