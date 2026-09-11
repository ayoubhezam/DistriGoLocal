package com.distrigo.app.ui.tournees

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.Tournee
import com.distrigo.app.data.model.Vente
import com.distrigo.app.ui.common.bidiIsolate
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.purchases.CornerRibbon
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime
import com.distrigo.app.ui.ventes.VenteViewModel
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.GridView
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsTopBarSize
import com.distrigo.app.ui.common.EntityImage

// ═══ LEVEL 1 — Tournées list (Navigation Compose destination: Screen.TourneesHome) ═══
@Composable
fun TourneesScreen(
    viewModel      : TourneeViewModel = hiltViewModel(),
    modifier       : Modifier = Modifier,
    onBack         : (() -> Unit)? = null,
    onAddTournee   : () -> Unit = {},
    onTourneeClick : (Int) -> Unit = {}
) {
    val tournees     by viewModel.tournees.collectAsState()
    val isLoading    by viewModel.isLoading.collectAsState()
    val error        by viewModel.error.collectAsState()
    val openTournee  by viewModel.openTournee.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadOpenTournee() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        // ── Header ──
        // Pushed from the Ventes hub, so it takes a back affordance; null-safe because the
        // screen is still usable as a root, where None is right.
        DsTopAppBar(
            title   = "Tournées",
            leading = onBack?.let { DsTopBarLeading.Back(it) } ?: DsTopBarLeading.None,
            size    = DsTopBarSize.Large
        ) {
            FloatingActionButton(
                onClick        = onAddTournee,
                containerColor = DsColors.Primary,
                contentColor   = Color.White,
                modifier       = Modifier.size(40.dp),
                shape          = DsShapes.pill
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nouvelle tournée")
            }
        }

        // ── Open Tournée Banner ──
        openTournee?.let { open ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.lg)
                    .padding(bottom = DsSpacing.md)
                    .clip(DsShapes.large)
                    .background(DsColors.PrimaryLight)
                    .clickable { onTourneeClick(open.id) }
                    .padding(DsSpacing.lg),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(28.dp))
                Column {
                    Text("${open.nom} — en cours", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Primary)
                    Text("Tapez pour continuer", fontSize = DsTextSize.caption, color = DsColors.Primary)
                }
            }
        }

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            }
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error ?: "", color = DsColors.Danger)
                }
            }
            tournees.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint     = DsColors.TextTertiary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text("Aucune tournée", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(tournees, key = { it.id }) { tournee ->
                        TourneeCard(
                            tournee = tournee,
                            onClick = { onTourneeClick(tournee.id) }
                        )
                    }
                }
            }
        }
    }
}

// ═══ LEVEL 2 — Tournée detail (Navigation Compose destination: Screen.TourneesDetail) ═══
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TourneeDetailScreen(
    tourneeId              : Int,
    viewModel              : TourneeViewModel = hiltViewModel(),
    modifier                : Modifier = Modifier,
    venteViewModel          : VenteViewModel = hiltViewModel(),
    productViewModel        : com.distrigo.app.ui.products.ProductViewModel = hiltViewModel(),
    onBack                  : () -> Unit = {},
    onEditTournee           : (Tournee) -> Unit = {},
    onAddClients            : () -> Unit = {},
    onCreateVente           : (Int?) -> Unit = {},
    onOpenVente             : (Vente) -> Unit = {},
    onOpenBrouillons        : () -> Unit = {},
    onNavigateToChargement  : () -> Unit = {}
) {
    val tournee by viewModel.selectedTournee.collectAsState()
    val current = tournee
    val tourneeClients by viewModel.tourneeClients.collectAsState()
    val products by productViewModel.products.collectAsState()

    LaunchedEffect(tourneeId) {
        viewModel.loadTourneeDetail(tourneeId)
        viewModel.loadTourneeClients(tourneeId)
        viewModel.observeVenteDrafts(tourneeId)
    }
    val venteDrafts by viewModel.venteDrafts.collectAsState()

    var showCloseDialog         by remember { mutableStateOf<Tournee?>(null) }
    var showReopenDialog        by remember { mutableStateOf<Tournee?>(null) }
    var actionError             by remember { mutableStateOf("") }
    var showDeleteTourneeDialog by remember { mutableStateOf<Tournee?>(null) }
    var showTourneeMenu         by remember { mutableStateOf(false) }
    var deleteTourneeError      by remember { mutableStateOf("") }
    var longPressVenteInTournee by remember { mutableStateOf<Vente?>(null) }
    var showDeleteVenteInTournee by remember { mutableStateOf(false) }
    var deleteVenteError        by remember { mutableStateOf("") }
    var confirmReopenSaleClient by remember { mutableStateOf<com.distrigo.app.data.model.TourneeClientInfo?>(null) }
    var venteQuery              by remember { mutableStateOf("") }
    // Screen state, not ViewModel state, exactly like venteQuery above. Achats and Dépôt
    // Vente hang their filters off a shared ViewModel because those lists are one screen each;
    // this one is per-tournée, and a filter left over from the last tournée you opened would
    // be a puzzle rather than a convenience.
    var filterStatus            by remember { mutableStateOf<String?>(null) }
    var filterPaymentStatus     by remember { mutableStateOf<String?>(null) }
    var filterClientId          by remember { mutableStateOf<Int?>(null) }
    var showFilterSheet         by remember { mutableStateOf(false) }
    var confirmRemoveClient     by remember { mutableStateOf<com.distrigo.app.data.model.TourneeClientInfo?>(null) }
    var removeClientError       by remember { mutableStateOf("") }


    // ── Close Confirmation Dialog ──
    showCloseDialog?.let { tournee ->
        AlertDialog(
            onDismissRequest = { showCloseDialog = null; actionError = "" },
            title = { Text("Fermer cette tournée ?", fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    Text("Ceci enregistre l'heure de fin et empêche l'ajout de nouvelles ventes à cette tournée.")
                    if (actionError.isNotEmpty()) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text(actionError, color = DsColors.Danger, fontSize = DsTextSize.caption)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.closeTournee(
                            id        = tournee.id,
                            onSuccess = {
                                showCloseDialog = null
                                viewModel.loadTourneeDetail(tournee.id)
                            },
                            onError = { actionError = it }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.Danger)
                ) {
                    Text("cloturé", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseDialog = null; actionError = "" }) {
                    Text("Annuler")
                }
            },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    confirmRemoveClient?.let { info ->
        AlertDialog(
            onDismissRequest = { confirmRemoveClient = null; removeClientError = "" },
            title = { Text("Retirer ce client ?", fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    Text(
                        "« ${bidiIsolate(info.client.name)} » sera retiré de la liste de cette " +
                        "tournée. Les ventes déjà enregistrées pour ce client ne sont pas supprimées."
                    )
                    if (removeClientError.isNotEmpty()) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text(removeClientError, color = DsColors.Danger, fontSize = DsTextSize.caption)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeClientFromTournee(
                        tourneeId = tourneeId,
                        clientId  = info.client.id,
                        onSuccess = { confirmRemoveClient = null; removeClientError = "" },
                        onError   = { removeClientError = it }
                    )
                }) {
                    Text("Retirer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveClient = null; removeClientError = "" }) {
                    Text("Annuler")
                }
            },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    confirmReopenSaleClient?.let { info ->
        AlertDialog(
            onDismissRequest = { confirmReopenSaleClient = null },
            title = { Text("Créer une vente ?") },
            text  = { Text("Voulez-vous effectuer une vente pour ${info.client.name} ?") },
            confirmButton = {
                TextButton(onClick = {
                    val cid = info.client.id
                    confirmReopenSaleClient = null
                    onCreateVente(cid)
                }) {
                    Text("Oui", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReopenSaleClient = null }) {
                    Text("Annuler")
                }
            },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }
    showDeleteTourneeDialog?.let { target ->
        AlertDialog(
            onDismissRequest = { showDeleteTourneeDialog = null; deleteTourneeError = "" },
            title = { Text("Supprimer la tournée ?") },
            text  = {
                Column {
                    Text("Voulez-vous supprimer la tournée #${target.id} ? Cette action est irréversible.")
                    if (deleteTourneeError.isNotEmpty()) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text(deleteTourneeError, fontSize = DsTextSize.caption, color = DsColors.Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTournee(
                        id        = target.id,
                        onSuccess = {
                            showDeleteTourneeDialog = null
                            deleteTourneeError      = ""
                            onBack()
                        },
                        onError = { error -> deleteTourneeError = error }
                    )
                }) {
                    Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTourneeDialog = null; deleteTourneeError = "" }) {
                    Text("Annuler")
                }
            },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    // ── Reopen Confirmation Dialog ──
    showReopenDialog?.let { tournee ->
        AlertDialog(
            onDismissRequest = { showReopenDialog = null; actionError = "" },
            title = { Text("Rouvrir cette tournée ?", fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    Text("Ceci remet la tournée en cours et permet d'y attacher de nouvelles ventes.")
                    if (actionError.isNotEmpty()) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text(actionError, color = DsColors.Danger, fontSize = DsTextSize.caption)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reopenTournee(
                            id        = tournee.id,
                            onSuccess = {
                                showReopenDialog = null
                                viewModel.loadTourneeDetail(tournee.id)
                            },
                            onError = { actionError = it }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    Text("Rouvrir", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReopenDialog = null; actionError = "" }) {
                    Text("Annuler")
                }
            },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (current == null || current.id != tourneeId) {
            Box(
                Modifier.fillMaxSize().background(DsColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = DsColors.Primary)
            }
        } else {
            longPressVenteInTournee?.let { vente ->
                if (showDeleteVenteInTournee) {
                    AlertDialog(
                        onDismissRequest = { showDeleteVenteInTournee = false; longPressVenteInTournee = null; deleteVenteError = "" },
                        title = { Text("Supprimer ce reçu ?") },
                        text  = {
                            Column {
                                Text("Cette action est irréversible. Les quantités vendues seront remises en stock.")
                                if (deleteVenteError.isNotEmpty()) {
                                    Spacer(Modifier.height(DsSpacing.sm))
                                    Text(deleteVenteError, fontSize = DsTextSize.caption, color = DsColors.Danger)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                venteViewModel.deleteVente(
                                    id        = vente.id,
                                    onSuccess = {
                                        showDeleteVenteInTournee = false
                                        longPressVenteInTournee  = null
                                        deleteVenteError         = ""
                                        viewModel.loadTourneeDetail(tourneeId)
                                        viewModel.refreshAfterVenteChange(tourneeId)

                                    },
                                    onError = { error -> deleteVenteError = error }
                                )
                            }) {
                                Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteVenteInTournee = false; longPressVenteInTournee = null; deleteVenteError = "" }) {
                                Text("Annuler")
                            }
                        },
                        containerColor    = DsColors.Surface,
                        titleContentColor = DsColors.TextPrimary,
                        textContentColor  = DsColors.TextSecondary
                    )
                } else {
                    AlertDialog(
                        onDismissRequest = { longPressVenteInTournee = null },
                        title = { Text("Vente #${vente.id}") },
                        confirmButton = {},
                        dismissButton = {},
                        text = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(DsShapes.medium)
                                    .background(DsColors.DangerLight)
                                    .clickable { showDeleteVenteInTournee = true }
                                    .padding(DsSpacing.md),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(20.dp))
                                Text("Supprimer la vente", fontSize = DsTextSize.body, color = DsColors.Danger, fontWeight = FontWeight.Medium)
                            }
                        },
                        containerColor    = DsColors.Surface,
                        titleContentColor = DsColors.TextPrimary,
                        textContentColor  = DsColors.TextSecondary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DsColors.Surface)
            ) {
                // The bar is always fully visible — it never fades or translates; only the stats
                // banner inside the list below it collapses.
                DsTopAppBar(
                    title   = current.nom,
                    leading = DsTopBarLeading.Back(onBack)
                ) {
                    Box {
                        IconButton(onClick = { showTourneeMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Plus d'options", tint = DsColors.TextSecondary)
                        }
                        // Both of the screen's former in-body actions live here now. The body
                        // is the list of clients and bons; adding a client and closing the tournée
                        // are things you do to the tournée, not things you read off it.
                        DropdownMenu(expanded = showTourneeMenu, onDismissRequest = { showTourneeMenu = false }) {
                            if (current.status == "ouverte") {
                                DropdownMenuItem(
                                    text        = { Text("Ajouter un client") },
                                    leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = DsColors.Primary) },
                                    onClick     = { showTourneeMenu = false; onAddClients() }
                                )
                            }
                            DropdownMenuItem(
                                text        = { Text("Modifier les informations") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = DsColors.Primary) },
                                onClick     = { showTourneeMenu = false; onEditTournee(current) }
                            )
                            if (current.status == "ouverte") {
                                DropdownMenuItem(
                                    text        = { Text("Clôturer la tournée") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = DsColors.Danger) },
                                    onClick     = { showTourneeMenu = false; showCloseDialog = current }
                                )
                            } else {
                                // Not asked for, but it shared the block that was removed, and
                                // dropping it would leave a closed tournée with no way to reopen.
                                DropdownMenuItem(
                                    text        = { Text("Rouvrir") },
                                    leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null, tint = DsColors.Primary) },
                                    onClick     = { showTourneeMenu = false; showReopenDialog = current }
                                )
                            }
                            if ((current.ventes_count ?: 0) == 0) {
                                DropdownMenuItem(
                                    text        = { Text("Supprimer la tournée") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger) },
                                    onClick     = { showTourneeMenu = false; showDeleteTourneeDialog = current }
                                )
                            }
                        }
                    }
                    // A 32dp overflow button, not a 48dp icon button, so it makes up the
                    // difference to the bar's standard end margin itself.
                    Spacer(Modifier.width(DsSpacing.md))
                }

                val ventes = current.ventes ?: emptyList()
                val hasActiveFilters = filterStatus != null || filterPaymentStatus != null || filterClientId != null

                // Search and filters narrow the same list, in the order Achats and Dépôt Vente
                // apply them: the text first, then each axis, all of them ANDed.
                val shownVentes = remember(ventes, venteQuery, filterStatus, filterPaymentStatus, filterClientId) {
                    val q = venteQuery.trim()
                    ventes.filter { v ->
                        // Matched on the two things written on a row: who it is for, and its number.
                        val matchSearch = q.isEmpty() ||
                            v.client_name.contains(q, ignoreCase = true) || v.id.toString().contains(q)
                        val matchStatus = filterStatus == null || v.status == filterStatus
                        val paye = v.montant_paye ?: 0.0
                        val matchPayment = when (filterPaymentStatus) {
                            "paye"    -> paye >= v.total && v.total > 0
                            "impaye"  -> paye <= 0.0
                            "partiel" -> paye > 0.0 && paye < v.total
                            else      -> true
                        }
                        val matchClient = filterClientId == null || v.client_id == filterClientId
                        matchSearch && matchStatus && matchPayment && matchClient
                    }
                }

                // Only the clients this tournée actually sold to. The full client list would be
                // mostly options that filter to nothing, which is the same rule the Brouillons
                // party filter follows.
                val filterClients = remember(ventes) {
                    ventes.map { it.client_id to it.client_name }.distinct().sortedBy { it.second }
                }

                // ── Fixed above the list: progress, search, clients, count ──
                //
                // None of this scrolls, and none of it is a stickyHeader any more. A sticky header
                // pins only until the next one arrives, so making "Bons" sticky in turn would have
                // pushed the clients out of the way exactly when the list started moving. Outside
                // the LazyColumn the whole block simply stays, and the list is the only thing left
                // that can scroll.
                TourneeProgressHeader(tourneeClients = tourneeClients)

                Spacer(Modifier.height(DsSpacing.md))

                OutlinedTextField(
                    value         = venteQuery,
                    onValueChange = { venteQuery = it },
                    placeholder   = { Text("Rechercher un client ou n° de bon...", fontSize = DsTextSize.bodySmall) },
                    leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, tint = DsColors.TextSecondary) },
                    trailingIcon  = {
                        if (venteQuery.isNotEmpty()) {
                            IconButton(onClick = { venteQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer", tint = DsColors.TextSecondary)
                            }
                        }
                    },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DsSpacing.lg)
                        .clip(DsShapes.large),
                    shape      = DsShapes.large,
                    singleLine = true,
                    colors     = dsTextFieldColors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )

                Spacer(Modifier.height(DsSpacing.sm))

                TourneeClientStrip(
                    tourneeClients      = tourneeClients,
                    // The unfiltered list on purpose: the avatars report what actually happened on
                    // the tournée, which is not a function of what is typed in the search box.
                    tourneeVentes       = ventes,
                    isOpen              = current.status == "ouverte",
                    onCreateSale        = { cid -> onCreateVente(cid) },
                    onMarkVisitedNoSale = { cid -> viewModel.markTourneeClientVisited(tourneeId, cid, onSuccess = {}, onError = {}) },
                    onAddClient         = { onAddClients() },
                    onRemoveClient      = { cid ->
                        confirmRemoveClient = tourneeClients.find { it.client.id == cid }
                    },
                    onReopenSaleForVisited = { cid ->
                        confirmReopenSaleClient = tourneeClients.find { it.client.id == cid }
                    }
                )

                Spacer(Modifier.height(DsSpacing.sm))

                // The counter chip Achats and Dépôt Vente head their lists with — sunken pill,
                // 14dp receipt — and like theirs it counts what the search actually left.
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(DsShapes.medium)
                            .background(DsColors.SurfaceSunken)
                            .padding(horizontal = DsSpacing.sm, vertical = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(14.dp))
                        Text(
                            "${shownVentes.size} bons",
                            fontSize   = DsTextSize.caption,
                            fontWeight = FontWeight.SemiBold,
                            color      = DsColors.TextSecondary
                        )
                    }

                    // Brouillons live beside the bons count rather than in a tab strip: they are
                    // a distinct list with its own screen, but they are not a peer view of the
                    // same data. Same placement the other two flows give theirs.
                    if (venteDrafts.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(DsShapes.medium)
                                .background(DsColors.PrimaryLight)
                                .clickable { onOpenBrouillons() }
                                .padding(horizontal = DsSpacing.sm, vertical = 6.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(14.dp))
                            Text(
                                "Brouillons (${venteDrafts.size})",
                                fontSize   = DsTextSize.caption,
                                fontWeight = FontWeight.SemiBold,
                                color      = DsColors.Primary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(DsShapes.medium)
                            .background(DsColors.SurfaceSunken)
                            .clickable { showFilterSheet = true }
                            .padding(horizontal = DsSpacing.sm, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filtres",
                                tint     = if (hasActiveFilters) DsColors.Primary else DsColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Filtres",
                                fontSize = DsTextSize.caption,
                                color    = if (hasActiveFilters) DsColors.Primary else DsColors.TextSecondary
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint     = if (hasActiveFilters) DsColors.Primary else DsColors.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (showFilterSheet) {
                    TourneeVenteFilterSheet(
                        clients             = filterClients,
                        filterStatus        = filterStatus,
                        filterPaymentStatus = filterPaymentStatus,
                        filterClientId      = filterClientId,
                        resultCount         = shownVentes.size,
                        onStatus            = { filterStatus = it },
                        onPaymentStatus     = { filterPaymentStatus = it },
                        onClient            = { filterClientId = it },
                        onReset             = {
                            filterStatus = null; filterPaymentStatus = null; filterClientId = null
                        },
                        onDismiss           = { showFilterSheet = false }
                    )
                }

                Spacer(Modifier.height(DsSpacing.sm))

                // ── The only thing that scrolls ──
                LazyColumn(
                    modifier       = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = DsSpacing.lg)
                ) {
                    // Not tournée information and not a statistic — a block on selling from an
                    // empty truck, carrying the way to fix it. It rides with the list rather than
                    // the fixed block above, which is already as tall as it should get.
                    if (current.status == "ouverte") {
                        val totalCamionStock = products.sumOf { it.camion_stock }
                        if (totalCamionStock <= 0) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = DsSpacing.lg)
                                        .padding(bottom = DsSpacing.md)
                                        .clip(DsShapes.large)
                                        .background(DsColors.DangerLight)
                                        .padding(DsSpacing.lg)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(DsSpacing.sm))
                                        Text("Camion vide", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Danger)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Aucun stock disponible dans le camion. Rendez-vous au chargement avant de créer une vente.",
                                        fontSize = DsTextSize.caption,
                                        color    = DsColors.Danger
                                    )
                                    Spacer(Modifier.height(DsSpacing.sm))
                                    Button(
                                        onClick  = { onNavigateToChargement() },
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        shape    = DsShapes.medium,
                                        colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Danger)
                                    ) {
                                        Text("Aller au chargement", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    if (shownVentes.isNotEmpty()) {
                        items(shownVentes, key = { it.id }) { vente ->
                            Box(modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs.div(2))) {
                                TourneeVenteRow(
                                    vente       = vente,
                                    onClick     = { onOpenVente(vente) },
                                    onLongClick = { longPressVenteInTournee = vente }
                                )
                            }
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = DsSpacing.xxl),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    // Two different nothings: a tournée with no sales yet, and a
                                    // search that matched none of the sales it does have.
                                    if (ventes.isEmpty()) "Aucune vente enregistrée dans cette tournée pour le moment."
                                    else                  "Aucun bon ne correspond à cette recherche.",
                                    fontSize  = DsTextSize.bodySmall,
                                    color     = DsColors.TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier  = Modifier.padding(horizontal = DsSpacing.xxl)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Everything a client circle can do, in one sheet.
 *
 * Replaces an inline card that expanded under the strip and pushed the whole list down, plus a
 * long-press on the same circle that did something different again. One tap, one surface, every
 * action visible at once — including the two the card had no room for.
 *
 * Rows are not hidden when their prerequisite is missing: a client with no phone still shows
 * "Appeler", and says why when tapped. A row that vanishes teaches nothing; a row that explains
 * itself sends the user to the client's file to fix it.
 *
 * Those explanations are Toasts rather than the screen's own transient banner, which draws
 * inside the screen's Box and therefore *under* this sheet's window — it showed nothing at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TourneeClientActionsSheet(
    info          : com.distrigo.app.data.model.TourneeClientInfo,
    hasVente      : Boolean,
    isOpen        : Boolean,
    onCreateSale  : () -> Unit,
    onMarkVisited : () -> Unit,
    onRemove      : () -> Unit,
    onDismiss     : () -> Unit
) {
    val client      = info.client
    val context     = androidx.compose.ui.platform.LocalContext.current
    val hasLocation = client.latitude != null && client.longitude != null
    val hasPhone    = !client.phone.isNullOrBlank()
    val isVisited   = info.status == "visite"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = DsColors.Surface
    ) {
        Column(Modifier.padding(bottom = DsSpacing.xxxl)) {
            Column(Modifier.padding(horizontal = DsSpacing.lg)) {
                Text(
                    client.name,
                    fontSize   = DsTextSize.headline,
                    fontWeight = FontWeight.Bold,
                    color      = DsColors.TextPrimary
                )
                // The address if there is one, the customer type otherwise: the sheet covers the
                // client's own circle, so it has to say which client it is talking about.
                val subtitle = listOfNotNull(client.address, client.commune_name)
                    .joinToString(", ")
                    .ifEmpty {
                        when (client.customer_type) {
                            "wholesale" -> "Gros"
                            "business"  -> "Société"
                            else        -> "Détail"
                        }
                    }
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
            }

            Spacer(Modifier.height(DsSpacing.md))

            if (isOpen) {
                TourneeClientActionRow(
                    icon     = Icons.Default.ShoppingCart,
                    tint     = DsColors.Primary,
                    bg       = DsColors.PrimaryLight,
                    title    = "Créer une vente",
                    subtitle = "Nouvelle vente pour ce client",
                    onClick  = onCreateSale
                )
            }

            TourneeClientActionRow(
                icon     = Icons.Default.Navigation,
                tint     = DsColors.Success,
                bg       = DsColors.SuccessLight,
                title    = "Naviguer",
                subtitle = "Ouvrir l'itinéraire dans la carte",
                onClick  = {
                    if (!hasLocation) {
                        toast(context, "Aucune position enregistrée pour ce client")
                    } else try {
                        val uri = android.net.Uri.parse("google.navigation:q=${client.latitude},${client.longitude}")
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                .apply { setPackage("com.google.android.apps.maps") }
                        )
                    } catch (e: android.content.ActivityNotFoundException) {
                        toast(context, "Google Maps n'est pas installé sur cet appareil")
                    }
                }
            )

            TourneeClientActionRow(
                icon     = Icons.Default.Call,
                tint     = DsColors.Primary,
                bg       = DsColors.PrimaryLight,
                title    = "Appeler",
                subtitle = "Appeler ce client",
                onClick  = {
                    if (!hasPhone) {
                        toast(context, "Ce client n'a pas de numéro de téléphone enregistré")
                    } else {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_DIAL,
                                android.net.Uri.parse("tel:${client.phone}")
                            )
                        )
                    }
                }
            )

            // Nothing to mark on a closed tournée, and nothing to mark on a client already
            // visited — the row would be a control that cannot change anything.
            if (isOpen && !isVisited) {
                TourneeClientActionRow(
                    icon     = Icons.Default.RemoveShoppingCart,
                    tint     = DsColors.Warning,
                    bg       = DsColors.WarningLight,
                    title    = "Marquer sans vente",
                    subtitle = "Visité, mais aucune vente réalisée",
                    onClick  = onMarkVisited
                )
            }

            if (isOpen) {
                TourneeClientActionRow(
                    icon     = Icons.Default.Delete,
                    tint     = DsColors.Danger,
                    bg       = DsColors.DangerLight,
                    title    = "Supprimer de la tournée",
                    subtitle = if (hasVente) "Ses ventes restent enregistrées"
                               else          "Retirer ce client de la liste",
                    danger   = true,
                    onClick  = onRemove
                )
            }

            Spacer(Modifier.height(DsSpacing.md))

            OutlinedButton(
                onClick  = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.lg)
                    .heightIn(min = 50.dp),
                shape    = DsShapes.large,
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.Primary),
                border   = androidx.compose.foundation.BorderStroke(1.5.dp, DsColors.Border)
            ) {
                Text("Annuler", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun toast(context: android.content.Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
private fun TourneeClientActionRow(
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    tint     : Color,
    bg       : Color,
    title    : String,
    subtitle : String,
    danger   : Boolean = false,
    onClick  : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        Box(
            modifier         = Modifier.size(40.dp).clip(DsShapes.medium).background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize   = DsTextSize.body,
                fontWeight = FontWeight.SemiBold,
                color      = if (danger) DsColors.Danger else DsColors.TextPrimary
            )
            Text(subtitle, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint     = DsColors.TextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TourneeClientAvatarItem(
    info        : com.distrigo.app.data.model.TourneeClientInfo,
    hasVente    : Boolean,
    enabled     : Boolean,
    onTap       : () -> Unit,
    onLongTap   : () -> Unit
) {
    val client = info.client
    val isPending = info.status == "a_visiter"
    val isCurrent = info.status == "en_cours"

    val badgeColor: Color? = when {
        info.status == "visite" && hasVente  -> DsColors.Success
        info.status == "visite" && !hasVente -> DsColors.Warning
        else -> null
    }
    val badgeIcon = when {
        info.status == "visite" && hasVente  -> Icons.Default.Check
        info.status == "visite" && !hasVente -> Icons.Default.ShoppingCart
        else -> null
    }
    val ringColor = when {
        isCurrent -> DsColors.Primary
        isPending -> DsColors.Border
        else      -> Color.Transparent
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(58.dp)
            .combinedClickable(
                enabled     = enabled,
                onClick     = onTap,
                onLongClick = onLongTap
            )
    )  {
        Box {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(DsShapes.pill)
                    .background(if (isPending) DsColors.SurfaceMuted else DsColors.SurfaceMuted)
                    .border(2.dp, ringColor, DsShapes.pill),
                contentAlignment = Alignment.Center
            ) {
                EntityImage(
                    ref                = client.image_uri,
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize().clip(DsShapes.pill).alpha(if (isPending) 0.55f else 1f)
                ) {
                    val initials = client.name.split(" ").take(2)
                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                    Text(
                        initials, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold,
                        color = if (isPending) DsColors.TextTertiary else DsColors.TextSecondary
                    )
                }
            }
            if (badgeColor != null && badgeIcon != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(DsShapes.pill)
                        .background(Color.White)
                        .padding(2.dp)
                        .clip(DsShapes.pill)
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(badgeIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
            } else if (isPending) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(DsShapes.pill)
                        .background(Color.White)
                        .padding(2.dp)
                        .clip(DsShapes.pill)
                        .background(DsColors.TextTertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            client.name.uppercase(),
            fontSize = DsTextSize.caption,
            fontWeight = FontWeight.Bold,
            color = if (isPending) DsColors.TextTertiary else DsColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TourneeAvatarLegendDot(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier.size(16.dp).clip(DsShapes.pill).background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
        }
        Text(label, fontSize = 10.sp, color = DsColors.TextSecondary)
    }
}

@Composable
private fun TourneeProgressHeader(
    tourneeClients : List<com.distrigo.app.data.model.TourneeClientInfo>
) {
    val visited = tourneeClients.filter { it.status == "visite" }
    val total   = tourneeClients.size
    var showStatusLegend by remember { mutableStateOf(false) }
    if (total == 0) return
    val percent = (visited.size * 100) / total

    // The three visit statuses used to sit in a permanent bar under the avatars, spending a row
    // of the screen on something you need once — when you first meet the colours — and never
    // again. On demand instead, from the line the circles hang under.
    if (showStatusLegend) {
        AlertDialog(
            onDismissRequest = { showStatusLegend = false },
            title = { Text("Statuts de visite", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                    TourneeAvatarLegendDot(color = DsColors.Success,      icon = Icons.Default.Check,        label = "Visité + vente")
                    TourneeAvatarLegendDot(color = DsColors.Warning,      icon = Icons.Default.ShoppingCart, label = "Visité, sans vente")
                    TourneeAvatarLegendDot(color = DsColors.TextTertiary, icon = Icons.Default.Schedule,     label = "Pas encore visité")
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatusLegend = false }) {
                    Text("Compris", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    Column(modifier = Modifier.padding(horizontal = DsSpacing.lg)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("${visited.size}/$total clients visités", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$percent%", fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = DsColors.Primary)
                IconButton(onClick = { showStatusLegend = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Statuts de visite",
                        tint               = DsColors.TextSecondary,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(DsShapes.pill).background(DsColors.Border)) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(percent / 100f).clip(DsShapes.pill).background(DsColors.Primary))
        }
    }
}

/**
 * The client circles, and the sheet a tap on one opens.
 *
 * Sized so five fit across: on the 360dp screen this is built for, the strip has 328dp between
 * the screen's own margins, and 5 × 58 + 4 × 8 = 322. At the old 72dp item and 12dp gap only
 * four fitted, and the fifth client — on a tournée that routinely has eighteen — was behind a
 * scroll before you knew there were more.
 *
 * Unlike the progress header this renders with no clients at all, because the first circle is
 * how you get some.
 */
@Composable
private fun TourneeClientStrip(
    tourneeClients      : List<com.distrigo.app.data.model.TourneeClientInfo>,
    tourneeVentes       : List<Vente>,
    isOpen              : Boolean,
    onCreateSale        : (Int) -> Unit,
    onMarkVisitedNoSale : (Int) -> Unit,
    onAddClient         : () -> Unit,
    onRemoveClient      : (Int) -> Unit,
    onReopenSaleForVisited : (Int) -> Unit
) {
    var sheetClientId by remember { mutableStateOf<Int?>(null) }
    val clientIdsWithVente = tourneeVentes.map { it.client_id }.toSet()

    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
        contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = 4.dp)
    ) {
        // Leads the strip rather than trailing it: at the end it sat past however many clients
        // the tournée has and needed a scroll to reach, while here it is always in the same place.
        if (isOpen) {
            item(key = "add_client") {
                TourneeAddClientAvatarItem(onClick = onAddClient)
            }
        }

        items(tourneeClients, key = { it.client.id }) { info ->
            TourneeClientAvatarItem(
                info      = info,
                hasVente  = info.client.id in clientIdsWithVente,
                enabled   = isOpen,
                onTap     = { sheetClientId = info.client.id },
                onLongTap = { sheetClientId = info.client.id }
            )
        }
    }

    sheetClientId?.let { cid ->
        val sheetInfo = tourneeClients.find { it.client.id == cid }
        if (sheetInfo != null) {
            TourneeClientActionsSheet(
                info          = sheetInfo,
                hasVente      = cid in clientIdsWithVente,
                isOpen        = isOpen,
                // Same guard the inline card had: a client already served asks before a
                // second sale is opened for them, rather than silently starting one.
                onCreateSale  = {
                    sheetClientId = null
                    if (cid in clientIdsWithVente) onReopenSaleForVisited(cid) else onCreateSale(cid)
                },
                onMarkVisited = { sheetClientId = null; onMarkVisitedNoSale(cid) },
                onRemove      = { sheetClientId = null; onRemoveClient(cid) },
                onDismiss     = { sheetClientId = null }
            )
        }
    }
}

@Composable
private fun TourneeAddClientAvatarItem(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(58.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(DsShapes.pill)
                .background(DsColors.PrimaryLight)
                .border(1.5.dp, DsColors.Primary, DsShapes.pill),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Ajouter un client",
            fontSize   = DsTextSize.caption,
            fontWeight = FontWeight.Bold,
            color      = DsColors.Primary,
            maxLines   = 2,
            textAlign  = TextAlign.Center,
            lineHeight = 12.sp
        )
    }
}

@Composable
private fun TourneeCard(tournee: Tournee, onClick: () -> Unit) {
    val isOpen = tournee.status == "ouverte"
    val accentColor = if (isOpen) DsColors.Success else DsColors.TextTertiary
    val badgeColor = if (isOpen) DsColors.Success else DsColors.TextSecondary
    val badgeBg = if (isOpen) DsColors.SuccessLight else DsColors.SurfaceMuted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .clickable { onClick() }
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accentColor)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(DsSpacing.md)
        ) {
            // Status badge row + chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(DsShapes.pill)
                            .background(accentColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isOpen) "En cours" else "Fermée",
                        fontSize = DsTextSize.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeColor
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(6.dp))

            // Tournée name
            Text(
                tournee.nom,
                fontSize = DsTextSize.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = DsColors.TextPrimary,
                maxLines = 1
            )

            Spacer(Modifier.height(6.dp))

            // Date + chauffeur row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    formatOrderDate(tournee.date_debut?.take(10) ?: ""),
                    fontSize = DsTextSize.caption,
                    color = DsColors.TextSecondary
                )

            }

            if (tournee.wilaya_name != null || tournee.commune_name != null) {
                Spacer(Modifier.height(4.dp))
                val location = listOfNotNull(tournee.commune_name, tournee.wilaya_name).joinToString(", ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(location, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                }
            }

            Spacer(Modifier.height(DsSpacing.sm))
            HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
            Spacer(Modifier.height(DsSpacing.sm))

            // Bottom row: clients count + total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${tournee.clients_count ?: 0} client(s) visité(s)",
                    fontSize = DsTextSize.caption,
                    color = DsColors.TextSecondary
                )
                Text(
                    "${"%.2f".format(tournee.total_ventes ?: 0.0)} DA",
                    fontSize = DsTextSize.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = DsColors.Primary
                )
            }
        }
    }
}

@Composable
private fun TourneeStatusBadge(status: String) {
    val isOpen = status == "ouverte"
    val bg     = if (isOpen) DsColors.PrimaryLight else DsColors.SuccessLight
    val fg     = if (isOpen) DsColors.Primary else DsColors.Success
    val label  = if (isOpen) "Ouverte" else "Fermée"

    Box(
        modifier = Modifier
            .clip(DsShapes.pill)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
private fun TourneeStatBox(modifier: Modifier = Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .background(DsColors.Surface)
            .padding(DsSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.Primary)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
    }
}

/**
 * The "Filtres avancés" sheet Achats and Dépôt Vente already use, on this list's own terms.
 *
 * Same three closed axes and the same footer — Réinitialiser beside Appliquer with the resulting
 * count on it, so the button says what it will leave you looking at before you commit to it.
 *
 * No date range, which those two screens do offer. A tournée is one day's driving; every bon on
 * it shares a date, so a period filter here could only ever return all of them or none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TourneeVenteFilterSheet(
    clients             : List<Pair<Int, String>>,
    filterStatus        : String?,
    filterPaymentStatus : String?,
    filterClientId      : Int?,
    resultCount         : Int,
    onStatus            : (String?) -> Unit,
    onPaymentStatus     : (String?) -> Unit,
    onClient            : (Int?) -> Unit,
    onReset             : () -> Unit,
    onDismiss           : () -> Unit
) {
    var clientExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = DsColors.Surface
    ) {
        Column(
            modifier = Modifier
                .padding(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs, bottom = DsSpacing.xxxl)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Filtres avancés", fontWeight = FontWeight.Bold, fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", tint = DsColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(DsSpacing.md))

            Text("Statut du bon", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
            TourneeFilterSegments(
                options  = listOf(null to "Tous", "pending" to "En attente", "delivered" to "Livré"),
                selected = filterStatus,
                onSelect = onStatus
            )
            Spacer(Modifier.height(DsSpacing.md))

            Text("Statut du paiement", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
            TourneeFilterSegments(
                options  = listOf(null to "Tous", "paye" to "Payé", "impaye" to "Impayé", "partiel" to "Partiel"),
                selected = filterPaymentStatus,
                onSelect = onPaymentStatus
            )
            Spacer(Modifier.height(DsSpacing.md))

            Text("Client", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
            ExposedDropdownMenuBox(
                expanded         = clientExpanded,
                onExpandedChange = { clientExpanded = it }
            ) {
                OutlinedTextField(
                    value         = clients.find { it.first == filterClientId }?.second ?: "Tous les clients",
                    onValueChange = {},
                    readOnly      = true,
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clientExpanded) },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape         = DsShapes.medium,
                    colors        = dsTextFieldColors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )
                ExposedDropdownMenu(expanded = clientExpanded, onDismissRequest = { clientExpanded = false }) {
                    DropdownMenuItem(
                        text    = { Text("Tous les clients", color = DsColors.TextSecondary) },
                        onClick = { onClient(null); clientExpanded = false }
                    )
                    clients.forEach { (id, name) ->
                        DropdownMenuItem(
                            text    = { Text(name) },
                            onClick = { onClient(id); clientExpanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(DsSpacing.lg))

            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                OutlinedButton(
                    onClick  = onReset,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.TextPrimary),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                ) {
                    Text("Réinitialiser", fontSize = DsTextSize.body, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick  = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    Text("Appliquer ($resultCount)", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

/** One row of mutually exclusive pills, as both other filter sheets draw their closed sets. */
@Composable
private fun <T> TourneeFilterSegments(
    options  : List<Pair<T?, String>>,
    selected : T?,
    onSelect : (T?) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        options.forEach { (value, label) ->
            val active = selected == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(DsShapes.medium)
                    .background(if (active) DsColors.Primary else DsColors.Surface)
                    .border(1.dp, if (active) DsColors.Primary else DsColors.Border, DsShapes.medium)
                    .clickable { onSelect(value) }
                    .padding(vertical = DsSpacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (active) Color.White else DsColors.TextPrimary
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TourneeVenteRow(
    vente        : Vente,
    onClick      : () -> Unit,
    onLongClick  : () -> Unit
) {
    val total       = vente.total
    val montantPaye = vente.montant_paye ?: 0.0
    val statut = when {
        montantPaye >= total && total > 0      -> "Payé"
        montantPaye > 0 && montantPaye < total -> "Partiel"
        else                                    -> "Impayé"
    }
    val statusColor = when (statut) {
        "Payé"    -> DsColors.Success
        "Partiel" -> DsColors.Warning
        else      -> DsColors.Danger
    }
    val statusColorLight = when (statut) {
        "Payé"    -> DsColors.SuccessLight
        "Partiel" -> DsColors.WarningLight
        else      -> DsColors.DangerLight
    }

    // Corner ribbon shows delivery status, distinct from the payment-status pill kept below.
    val isDelivered = vente.status == "delivered"
    val (ribbonLabel, ribbonColor) = if (isDelivered) {
        "LIVRÉ" to DsColors.Success
    } else {
        "EN ATTENTE" to DsColors.Warning
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .combinedClickable(
                onClick     = onClick,
                onLongClick = onLongClick
            )
    ) {
      Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
            // The number a bon is actually referred to by, in the caption/Medium/secondary the
            // Dépôt Vente list already gives it — the two lists show the same records, so they
            // should name them the same way.
            Text(
                "Vente #${vente.id}",
                fontSize   = DsTextSize.caption,
                fontWeight = FontWeight.Medium,
                color      = DsColors.TextSecondary
            )
            Text(vente.client_name, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text(
                "${vente.items_count ?: vente.items?.size ?: 0} article(s) · ${formatOrderTime(vente.created_at)}",
                fontSize = DsTextSize.caption,
                color    = DsColors.TextSecondary
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier            = Modifier.padding(end = 28.dp)
        ) {
            Text("${"%.2f".format(total)} DA", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Primary)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(DsShapes.pill)
                    .background(statusColorLight)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(statut, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = statusColor)
            }
        }
      }
        CornerRibbon(
            label    = ribbonLabel,
            color    = ribbonColor,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}
