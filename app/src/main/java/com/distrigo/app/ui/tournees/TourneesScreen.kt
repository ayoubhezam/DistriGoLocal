package com.distrigo.app.ui.tournees

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Tournee
import com.distrigo.app.data.model.Vente
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime
import com.distrigo.app.ui.ventes.VenteDetailScreen
import com.distrigo.app.ui.ventes.VenteViewModel
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.GridView

@Composable
fun TourneesScreen(
    viewModel             : TourneeViewModel = viewModel(),
    modifier              : Modifier = Modifier,
    onFullScreenChange    : (Boolean) -> Unit = {},
    venteViewModel        : VenteViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    productViewModel      : com.distrigo.app.ui.products.ProductViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToChargement: () -> Unit = {}
) {
    val tournees     by viewModel.tournees.collectAsState()
    val isLoading    by viewModel.isLoading.collectAsState()
    val error        by viewModel.error.collectAsState()
    val openTournee  by viewModel.openTournee.collectAsState()

    var selectedTourneeId    by remember { mutableStateOf<Int?>(null) }
    var showTourneeForm      by remember { mutableStateOf(false) }
    var editingTournee       by remember { mutableStateOf<Tournee?>(null) }
    var showCloseDialog      by remember { mutableStateOf<Tournee?>(null) }
    var showReopenDialog     by remember { mutableStateOf<Tournee?>(null) }
    var actionError          by remember { mutableStateOf("") }
    var showDeleteTourneeDialog by remember { mutableStateOf<Tournee?>(null) }
    var deleteTourneeError      by remember { mutableStateOf("") }
    var selectedVenteInTournee  by remember { mutableStateOf<Vente?>(null) }
    var longPressVenteInTournee by remember { mutableStateOf<Vente?>(null) }
    var showDeleteVenteInTournee by remember { mutableStateOf(false) }
    var deleteVenteError        by remember { mutableStateOf("") }
    var showNewTourneeVente     by remember { mutableStateOf(false) }
    var showAddClientsScreen    by remember { mutableStateOf(false) }
    var pendingSaleClientId     by remember { mutableStateOf<Int?>(null) }
    var confirmReopenSaleClient by remember { mutableStateOf<com.distrigo.app.data.model.TourneeClientInfo?>(null) }
    var transientMessage        by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadOpenTournee() }
    LaunchedEffect(Unit) { productViewModel.loadProducts() }

    LaunchedEffect(transientMessage) {
        if (transientMessage != null) {
            kotlinx.coroutines.delay(2000)
            transientMessage = null
        }
    }

    // ── Tournée Form Sub-screen ──
    if (showTourneeForm) {
        onFullScreenChange(true)
        TourneeFormScreen(
            tournee = editingTournee,
            onBack  = { showTourneeForm = false; editingTournee = null; onFullScreenChange(false) },
            onSaved = {
                showTourneeForm = false
                editingTournee = null
                onFullScreenChange(false)
                viewModel.loadTournees()
            }
        )
        return
    }

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
            }
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
                    pendingSaleClientId = cid
                    showNewTourneeVente = true
                }) {
                    Text("Oui", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReopenSaleClient = null }) {
                    Text("Annuler")
                }
            }
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
                            selectedTourneeId       = null
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
            }
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
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        if (selectedTourneeId == null) {
            // ═══ LEVEL 1 — Main list ═══

            // ── Header ──
            Row(
                modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Tournées", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.TextPrimary)
                FloatingActionButton(
                    onClick        = { editingTournee = null; showTourneeForm = true },
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
                        .clickable {
                            selectedTourneeId = open.id
                            viewModel.loadTourneeDetail(open.id)
                            viewModel.loadTourneeClients(open.id)
                        }
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
                                onClick = {
                                    selectedTourneeId = tournee.id
                                    viewModel.loadTourneeDetail(tournee.id)
                                    viewModel.loadTourneeClients(tournee.id)
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // ═══ LEVEL 2 — Tournée detail ═══
            val id = selectedTourneeId!!
            BackHandler { selectedTourneeId = null }

            val tournee   by viewModel.selectedTournee.collectAsState()
            val current   = tournee
            val tourneeClients by viewModel.tourneeClients.collectAsState()
            val products by productViewModel.products.collectAsState()

            if (current == null || current.id != id) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            } else {
                if (showAddClientsScreen) {
                    onFullScreenChange(true)
                    AddClientsToTourneeScreen(
                        tourneeId         = id,
                        tourneeNom        = current.nom,
                        existingClientIds = tourneeClients.map { it.client.id }.toSet(),
                        onBack  = { showAddClientsScreen = false; onFullScreenChange(false) },
                        onSaved = { showAddClientsScreen = false; onFullScreenChange(false) }
                    )
                    return
                }
                if (showNewTourneeVente) {
                    onFullScreenChange(true)
                    TourneeVenteFormScreen(
                        tourneeId           = id,
                        preSelectedClientId = pendingSaleClientId,
                        onBack    = { showNewTourneeVente = false; pendingSaleClientId = null; onFullScreenChange(false) },
                        onSaved   = {
                            val cid = pendingSaleClientId
                            showNewTourneeVente = false
                            pendingSaleClientId = null
                            onFullScreenChange(false)
                            viewModel.refreshAfterVenteChange(id)
                            if (cid != null) {
                                viewModel.markTourneeClientVisited(
                                    id, cid,
                                    onSuccess = { viewModel.refreshAfterVenteChange(id) },
                                    onError   = {}
                                )
                            } else {
                                viewModel.loadTourneeClients(id)
                            }
                        }
                    )
                    return
                }



                selectedVenteInTournee?.let { vente ->
                    onFullScreenChange(true)
                    VenteDetailScreen(
                        vente       = vente,
                        onBack      = { selectedVenteInTournee = null; onFullScreenChange(false) },
                        viewModel   = venteViewModel,
                        onDelivered = {
                            selectedVenteInTournee = null
                            onFullScreenChange(false)
                            viewModel.loadTourneeDetail(id)
                            viewModel.refreshAfterVenteChange(id)
                        },
                        onDeleted = {
                            selectedVenteInTournee = null
                            onFullScreenChange(false)
                            viewModel.loadTourneeDetail(id)
                            viewModel.refreshAfterVenteChange(id)
                        }
                    )
                    return
                }
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
                                            viewModel.loadTourneeDetail(id)
                                            viewModel.refreshAfterVenteChange(id)

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
                            }
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
                            }
                        )
                    }
                }

                // ── Full scrollable detail body ──
                LazyColumn(
                    modifier            = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding      = PaddingValues(bottom = DsSpacing.lg)
                ) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { selectedTourneeId = null }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
                                }
                                Spacer(Modifier.width(DsSpacing.xs))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(current.nom, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                                    Spacer(Modifier.height(2.dp))
                                    TourneeStatusBadge(status = current.status)
                                    val details = listOfNotNull(
                                        current.secteur_name,
                                        listOfNotNull(current.commune_name, current.wilaya_name).joinToString(", ").ifEmpty { null }
                                    ).joinToString(" · ")
                                    if (details.isNotEmpty()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(details, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                    }
                                }
                            }

                            Spacer(Modifier.height(DsSpacing.md))

                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                            ) {
                                if ((current.ventes_count ?: 0) == 0) {
                                    IconButton(
                                        onClick  = { showDeleteTourneeDialog = current },
                                        modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.DangerLight)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = DsColors.Danger, modifier = Modifier.size(18.dp))
                                    }
                                }

                                IconButton(
                                    onClick  = { editingTournee = current; showTourneeForm = true },
                                    modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                                }

                                Spacer(Modifier.weight(1f))

                                if (current.status == "ouverte") {
                                    Button(
                                        onClick        = { showCloseDialog = current },
                                        shape          = DsShapes.medium,
                                        colors         = ButtonDefaults.buttonColors(containerColor = DsColors.Danger),
                                        contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = 10.dp)
                                    ) {
                                        Text("Clôturer la tournée", color = Color.White, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    Button(
                                        onClick        = { showReopenDialog = current },
                                        shape          = DsShapes.medium,
                                        colors         = ButtonDefaults.buttonColors(containerColor = DsColors.Primary),
                                        contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = 10.dp)
                                    ) {
                                        Text("Rouvrir", color = Color.White, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    if (current.status == "ouverte") {
                        val totalCamionStock = products.sumOf { it.camion_stock }
                        if (totalCamionStock <= 0) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = DsSpacing.lg)
                                        .clip(DsShapes.large)
                                        .background(DsColors.DangerLight)
                                        .padding(DsSpacing.lg)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(DsSpacing.sm))
                                        Text(
                                            "Camion vide",
                                            fontSize = DsTextSize.body,
                                            fontWeight = FontWeight.Bold,
                                            color = DsColors.Danger
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Aucun stock disponible dans le camion. Rendez-vous au chargement avant de créer une vente.",
                                        fontSize = DsTextSize.caption,
                                        color = DsColors.Danger
                                    )
                                    Spacer(Modifier.height(DsSpacing.sm))
                                    Button(
                                        onClick = { onNavigateToChargement() },
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        shape = DsShapes.medium,
                                        colors = ButtonDefaults.buttonColors(containerColor = DsColors.Danger)
                                    ) {
                                        Text("Aller au chargement", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }
                                }
                                Spacer(Modifier.height(DsSpacing.md))
                            }
                        }
                    }


                    if (current.status == "ouverte") {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = DsSpacing.lg)
                                    .clip(DsShapes.large)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors = listOf(DsColors.Primary, DsColors.Primary.copy(alpha = 0.75f))
                                        )
                                    )
                                    .clickable { showAddClientsScreen = true }
                                    .padding(DsSpacing.lg),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                            ) {
                                Box(
                                    modifier         = Modifier.size(40.dp).clip(DsShapes.medium).background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Ajouter des clients", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Sélectionner les clients à visiter", fontSize = DsTextSize.caption, color = Color.White.copy(alpha = 0.85f))
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(DsSpacing.md))
                        }
                    }

                    item {
                        TourneeStatsCarousel(current = current, tourneeClients = tourneeClients)
                        Spacer(Modifier.height(DsSpacing.md))
                    }

                    item {
                        TourneeTrackingSection(
                            tourneeClients      = tourneeClients,
                            tourneeVentes       = current.ventes ?: emptyList(),
                            isOpen              = current.status == "ouverte",
                            onCreateSale        = { cid -> pendingSaleClientId = cid; showNewTourneeVente = true },
                            onMarkVisitedNoSale = { cid -> viewModel.markTourneeClientVisited(id, cid, onSuccess = {}, onError = {}) },
                            onNavigateToVente   = { vente ->
                                selectedVenteInTournee = vente
                                venteViewModel.loadVenteDetail(vente.id)
                            },
                            onNoVenteTap        = { msg -> transientMessage = msg },
                            onAddClient         = { showAddClientsScreen = true },
                            onReopenSaleForVisited = { cid ->
                                confirmReopenSaleClient = tourneeClients.find { it.client.id == cid }
                            }
                        )
                        Spacer(Modifier.height(DsSpacing.md))
                    }

                    val ventes = current.ventes ?: emptyList()

                    if (ventes.isNotEmpty()) {
                        item {
                            Row(
                                modifier              = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Tickets de vente", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary)
                            }
                            Spacer(Modifier.height(DsSpacing.sm))
                        }
                        items(ventes, key = { it.id }) { vente ->
                            Box(modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs.div(2))) {
                                TourneeVenteRow(
                                    vente       = vente,
                                    onClick     = {
                                        selectedVenteInTournee = vente
                                        venteViewModel.loadVenteDetail(vente.id)
                                    },
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
                                    "Aucune vente enregistrée dans cette tournée pour le moment.",
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

    AnimatedVisibility(
        visible = transientMessage != null,
        modifier = Modifier.align(Alignment.Center)
    ) {
        Box(
            modifier = Modifier
                .clip(DsShapes.medium)
                .background(DsColors.TextPrimary)
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
        ) {
            Text(
                transientMessage ?: "",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
    }
}


//TourneeClientQuickActionsCard بطاقة البيع الجديدة
@Composable
private fun TourneeClientQuickActionsCard(
    info          : com.distrigo.app.data.model.TourneeClientInfo,
    hasVente      : Boolean,
    isOpen        : Boolean,
    onCreateSale  : () -> Unit,
    onMarkVisited : () -> Unit,
    onOpenVente   : () -> Unit,
    onShowMessage : (String) -> Unit
) {
    val client = info.client
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasLocation = client.latitude != null && client.longitude != null
    val hasPhone = !client.phone.isNullOrBlank()

    val isPending      = info.status == "a_visiter"
    val isVisitedNoSale = info.status == "visite" && !hasVente
    val isVisitedWithSale = info.status == "visite" && hasVente

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Primary, DsShapes.large)
            .padding(DsSpacing.lg)
    ) {
        Text(client.name, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        val address = listOfNotNull(client.address, client.commune_name, client.wilaya_name).joinToString(", ")
        if (address.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(address, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        }

        Spacer(Modifier.height(DsSpacing.md))

        // ── Naviguer + Appeler (communes aux 3 cas) ──
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            if (hasLocation) {
                Button(
                    onClick = {
                        try {
                            val uri = android.net.Uri.parse("google.navigation:q=${client.latitude},${client.longitude}")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            context.startActivity(intent)
                        } catch (e: android.content.ActivityNotFoundException) {
                            onShowMessage("Google Maps n'est pas installé sur cet appareil")
                        }
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Naviguer", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            OutlinedButton(
                onClick = {
                    if (hasPhone) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${client.phone}"))
                        context.startActivity(intent)
                    } else {
                        onShowMessage("Ce client n'a pas de numéro de téléphone enregistré")
                    }
                },
                modifier = Modifier.weight(1f).height(44.dp),
                shape    = DsShapes.medium
            ) {
                Icon(Icons.Default.Call, contentDescription = null, tint = DsColors.TextPrimary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Appeler", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            }
        }

        Spacer(Modifier.height(DsSpacing.sm))

        when {
            // ── Cas 1 : Pas encore visité ──
            isPending -> {
                Button(
                    onClick  = onCreateSale,
                    enabled  = isOpen,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    Text("Créer une vente", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Spacer(Modifier.height(DsSpacing.xs))
                TextButton(
                    onClick  = onMarkVisited,
                    enabled  = isOpen,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Marquer visité (sans vente)", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                }
            }

            // ── Cas 2 : Visité, sans vente ──
            isVisitedNoSale -> {
                Button(
                    onClick  = onCreateSale,
                    enabled  = isOpen,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.TextPrimary)
                ) {
                    Text("Créer une vente", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }

            // ── Cas 3 : Visité + vente ──
            isVisitedWithSale -> {
                OutlinedButton(
                    onClick  = onOpenVente,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = DsShapes.medium
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Présenter le reçu de vente", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
                }
            }
        }
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

    val bitmap = remember(client.image_uri) {
        client.image_uri?.let { uri ->
            try {
                val imageBytes = android.util.Base64.decode(uri.substringAfter("base64,"), android.util.Base64.NO_WRAP)
                android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } catch (e: Exception) { null }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .combinedClickable(
                enabled     = enabled,
                onClick     = onTap,
                onLongClick = onLongTap
            )
    )  {
        Box {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(DsShapes.pill)
                    .background(if (isPending) DsColors.SurfaceMuted else DsColors.SurfaceMuted)
                    .border(2.dp, ringColor, DsShapes.pill),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(DsShapes.pill).alpha(if (isPending) 0.55f else 1f),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    val initials = client.name.split(" ").take(2)
                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                    Text(
                        initials, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold,
                        color = if (isPending) DsColors.TextTertiary else DsColors.TextSecondary
                    )
                }
            }
            if (badgeColor != null && badgeIcon != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(DsShapes.pill)
                        .background(Color.White)
                        .padding(2.dp)
                        .clip(DsShapes.pill)
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(badgeIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            } else if (isPending) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(DsShapes.pill)
                        .background(Color.White)
                        .padding(2.dp)
                        .clip(DsShapes.pill)
                        .background(DsColors.TextTertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
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
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TourneeAddClientAvatarItem(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(DsShapes.pill)
                .background(DsColors.PrimaryLight)
                .border(1.5.dp, DsColors.Primary, DsShapes.pill),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Ajouter un client",
            fontSize = DsTextSize.caption,
            fontWeight = FontWeight.Bold,
            color = DsColors.Primary,
            maxLines = 2,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp
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
private fun TourneeTrackingSection(
    tourneeClients      : List<com.distrigo.app.data.model.TourneeClientInfo>,
    tourneeVentes       : List<Vente>,
    isOpen              : Boolean,
    onCreateSale        : (Int) -> Unit,
    onMarkVisitedNoSale : (Int) -> Unit,
    onNavigateToVente   : (Vente) -> Unit,
    onNoVenteTap        : (String) -> Unit,
    onAddClient         : () -> Unit,
    onReopenSaleForVisited : (Int) -> Unit
) {
    val visited = tourneeClients.filter { it.status == "visite" }
    val total   = tourneeClients.size
    var expandedClientId by remember { mutableStateOf<Int?>(null) }
    if (total == 0) return
    val percent = (visited.size * 100) / total

    Column(modifier = Modifier.padding(horizontal = DsSpacing.lg)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${visited.size}/$total clients visités", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            Text("$percent%", fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = DsColors.Primary)
        }
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(DsShapes.pill).background(DsColors.Border)) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(percent / 100f).clip(DsShapes.pill).background(DsColors.Primary))
        }
        Spacer(Modifier.height(DsSpacing.md))

        val clientIdsWithVente = tourneeVentes.map { it.client_id }.toSet()

        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.md),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(tourneeClients, key = { it.client.id }) { info ->
                TourneeClientAvatarItem(
                    info      = info,
                    hasVente  = info.client.id in clientIdsWithVente,
                    enabled   = isOpen,
                    onTap     = {
                        expandedClientId = if (expandedClientId == info.client.id) null else info.client.id
                    },
                    onLongTap = {
                        val hasVenteAlready = info.client.id in clientIdsWithVente
                        when {
                            info.status == "visite" && hasVenteAlready -> {
                                val vente = tourneeVentes.filter { it.client_id == info.client.id }
                                    .maxByOrNull { it.created_at ?: "" }
                                if (vente != null) onNavigateToVente(vente)
                            }
                            info.status == "visite" && !hasVenteAlready -> {
                                onNoVenteTap("${info.client.name} : aucune vente enregistrée")
                            }
                            else -> Unit
                        }
                    }
                )
            }
            if (isOpen) {
                item(key = "add_client") {
                    TourneeAddClientAvatarItem(onClick = onAddClient)
                }
            }
        }

        Spacer(Modifier.height(DsSpacing.sm))

        expandedClientId?.let { expId ->
            val expandedInfo = tourneeClients.find { it.client.id == expId }
            if (expandedInfo != null) {
                Spacer(Modifier.height(DsSpacing.md))
                val hasVenteAlready = expId in clientIdsWithVente
                TourneeClientQuickActionsCard(
                    info          = expandedInfo,
                    hasVente      = hasVenteAlready,
                    isOpen        = isOpen,
                    onCreateSale  = { onCreateSale(expId) },
                    onMarkVisited = { onMarkVisitedNoSale(expId); expandedClientId = null },
                    onOpenVente   = {
                        val vente = tourneeVentes.filter { it.client_id == expId }
                            .maxByOrNull { it.created_at ?: "" }
                        if (vente != null) onNavigateToVente(vente)
                    },
                    onShowMessage = onNoVenteTap
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DsShapes.medium)
                .background(DsColors.SurfaceMuted)
                .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TourneeAvatarLegendDot(color = DsColors.Success, icon = Icons.Default.Check, label = "Visité + vente")
            TourneeAvatarLegendDot(color = DsColors.Warning, icon = Icons.Default.ShoppingCart, label = "Visité, sans vente")
            TourneeAvatarLegendDot(color = DsColors.TextTertiary, icon = Icons.Default.Schedule, label = "Pas encore visité")
        }

        Spacer(Modifier.height(DsSpacing.md))

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
                if (!tournee.secteur_name.isNullOrBlank()) {
                    Spacer(Modifier.width(DsSpacing.md))
                    Icon(Icons.Default.GridView, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tournee.secteur_name, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                }
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

@Composable
private fun TourneeIconStatBox(
    modifier : Modifier = Modifier,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    iconBg   : Color,
    iconTint : Color,
    value    : String,
    label    : String
) {
    Column(
        modifier = modifier
            .clip(DsShapes.large)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .background(DsColors.Surface)
            .padding(DsSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier         = Modifier.size(36.dp).clip(DsShapes.medium).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(DsSpacing.sm))
        Text(value, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.ExtraBold, color = DsColors.TextPrimary, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TourneeStatsCarousel(
    current        : Tournee,
    tourneeClients : List<com.distrigo.app.data.model.TourneeClientInfo>
) {
    val visited = tourneeClients.filter { it.status == "visite" }
    val pending = tourneeClients.filter { it.status == "a_visiter" }
    val total   = tourneeClients.size
    val percent = if (total > 0) (visited.size * 100) / total else 0

    val clientIdsWithVente = (current.ventes ?: emptyList()).map { it.client_id }.toSet()
    val avecVente = visited.count { it.client.id in clientIdsWithVente }
    val sansVente = visited.size - avecVente

    val pagerState = rememberPagerState(pageCount = { 2 })

    Column {
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                if (page == 0) {
                    TourneeIconStatBox(
                        modifier = Modifier.weight(1f),
                        icon     = Icons.Default.TrendingUp,
                        iconBg   = DsColors.PrimaryLight,
                        iconTint = DsColors.Primary,
                        value    = "$percent%",
                        label    = "Progression"
                    )
                    TourneeIconStatBox(
                        modifier = Modifier.weight(1f),
                        icon     = Icons.Default.Payments,
                        iconBg   = DsColors.WarningLight,
                        iconTint = DsColors.Warning,
                        value    = "${"%.0f".format(current.total_ventes ?: 0.0)} DA",
                        label    = "Vente totale"
                    )
                    TourneeIconStatBox(
                        modifier = Modifier.weight(1f),
                        icon     = Icons.Default.AccountBalanceWallet,
                        iconBg   = if ((current.reste_total ?: 0.0) > 0) DsColors.DangerLight else DsColors.SuccessLight,
                        iconTint = if ((current.reste_total ?: 0.0) > 0) DsColors.Danger else DsColors.Success,
                        value    = "${"%.0f".format(current.reste_total ?: 0.0)} DA",
                        label    = "Le reste"
                    )
                } else {
                    TourneeIconStatBox(
                        modifier = Modifier.weight(1f),
                        icon     = Icons.Default.Check,
                        iconBg   = DsColors.SuccessLight,
                        iconTint = DsColors.Success,
                        value    = "$avecVente",
                        label    = "Avec vente"
                    )
                    TourneeIconStatBox(
                        modifier = Modifier.weight(1f),
                        icon     = Icons.Default.ShoppingCart,
                        iconBg   = DsColors.WarningLight,
                        iconTint = DsColors.Warning,
                        value    = "$sansVente",
                        label    = "Sans vente"
                    )
                    TourneeIconStatBox(
                        modifier = Modifier.weight(1f),
                        icon     = Icons.Default.Schedule,
                        iconBg   = DsColors.SurfaceMuted,
                        iconTint = DsColors.TextTertiary,
                        value    = "${pending.size}",
                        label    = "À visiter"
                    )
                }
            }
        }

        Spacer(Modifier.height(DsSpacing.sm))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(2) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(DsShapes.pill)
                        .background(if (selected) DsColors.Primary else DsColors.Border)
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .combinedClickable(
                onClick     = onClick,
                onLongClick = onLongClick
            )
            .padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(20.dp))

        Spacer(Modifier.width(DsSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(vente.client_name, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text(
                "${vente.items_count ?: vente.items?.size ?: 0} article(s) · ${formatOrderTime(vente.created_at)}",
                fontSize = DsTextSize.caption,
                color    = DsColors.TextSecondary
            )
        }

        Column(horizontalAlignment = Alignment.End) {
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
}
