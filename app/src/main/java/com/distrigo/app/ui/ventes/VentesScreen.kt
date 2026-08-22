package com.distrigo.app.ui.ventes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.Vente
import com.distrigo.app.ui.common.EntityAvatar
import com.distrigo.app.ui.components.ReceiptPreviewSheet
import com.distrigo.app.ui.components.ShareOptionsSheet
import com.distrigo.app.ui.components.toReceiptData
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.products.formatQty
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentesScreen(
    viewModel    : VenteViewModel = hiltViewModel(),
    modifier     : Modifier = Modifier,
    onAddVente   : () -> Unit = {},
    onEditVente  : (Int) -> Unit = {},
    onVenteClick : (Int) -> Unit = {}
) {
    val ventes      by viewModel.ventes.collectAsState()
    val depotVentes = ventes.filter { it.source == "depot" }
    val isLoading   by viewModel.isLoading.collectAsState()
    val error       by viewModel.error.collectAsState()

    var longPressVente   by remember { mutableStateOf<Vente?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteError      by remember { mutableStateOf("") }

    // ── Local UI state (لا تُحفظ في ViewModel) ──
    var showFilterSheet    by remember { mutableStateOf(false) }
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker   by remember { mutableStateOf(false) }
    val dateFromState      = rememberDatePickerState()
    val dateToState        = rememberDatePickerState()

    LaunchedEffect(Unit) { viewModel.loadVentes() }

    // ── Clients list (مستخرجة من المبيعات الموجودة) ──
    val clients = remember(depotVentes) {
        depotVentes
            .map { it.client_id to it.client_name }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    val hasActiveFilters = viewModel.filterStatus != null ||
            viewModel.filterPaymentStatus != null ||
            viewModel.filterClientId != null ||
            viewModel.filterDateFrom != null ||
            viewModel.filterDateTo != null

    val filteredVentes = depotVentes.filter { vente ->
        val matchSearch = viewModel.searchQuery.isBlank() || run {
            val tokens = viewModel.searchQuery.trim()
                .split("\\s+".toRegex())
                .filter { it.isNotEmpty() }
            tokens.all { token ->
                vente.client_name.contains(token, ignoreCase = true) ||
                        vente.id.toString().contains(token)
            }
        }

        val matchStatus = viewModel.filterStatus == null || vente.status == viewModel.filterStatus

        val matchPayment = when (viewModel.filterPaymentStatus) {
            "paye"    -> (vente.montant_paye ?: 0.0) >= vente.total && vente.total > 0
            "impaye"  -> (vente.montant_paye ?: 0.0) <= 0.0
            "partiel" -> (vente.montant_paye ?: 0.0) > 0.0 && (vente.montant_paye ?: 0.0) < vente.total
            else      -> true
        }

        val matchClient = viewModel.filterClientId == null || vente.client_id == viewModel.filterClientId

        val venteDate     = vente.created_at?.take(10) ?: ""
        val dateFrom      = viewModel.filterDateFrom
        val dateTo        = viewModel.filterDateTo
        val matchDateFrom = dateFrom == null || venteDate >= dateFrom
        val matchDateTo   = dateTo   == null || venteDate <= dateTo

        matchSearch && matchStatus && matchPayment && matchClient && matchDateFrom && matchDateTo
    }

    // ── Long Press Dialog ──
    longPressVente?.let { vente ->
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false; longPressVente = null; deleteError = "" },
                title = { Text("Supprimer la vente ?") },
                text  = {
                    Column {
                        Text("Voulez-vous supprimer la vente #${vente.id} de ${vente.client_name} ?")
                        if (deleteError.isNotEmpty()) {
                            Spacer(Modifier.height(DsSpacing.sm))
                            Text(deleteError, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteVente(
                            id        = vente.id,
                            onSuccess = {
                                showDeleteDialog = false
                                longPressVente   = null
                                deleteError      = ""
                                viewModel.loadVentes()
                            },
                            onError = { err -> deleteError = err }
                        )
                    }) {
                        Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false; longPressVente = null; deleteError = "" }) {
                        Text("Annuler")
                    }
                },
                containerColor    = DsColors.Surface,
                titleContentColor = DsColors.TextPrimary,
                textContentColor  = DsColors.TextSecondary
            )
        } else {
            AlertDialog(
                onDismissRequest = { longPressVente = null },
                title            = { Text("Vente #${vente.id}") },
                confirmButton    = {},
                dismissButton    = {},
                icon             = null,
                properties       = androidx.compose.ui.window.DialogProperties(),
                shape            = DsShapes.large,
                containerColor   = DsColors.Surface,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.medium)
                                .background(DsColors.PrimaryLight)
                                .clickable {
                                    longPressVente = null
                                    onEditVente(vente.id)
                                }
                                .padding(14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                            Text("Modifier la vente", fontSize = DsTextSize.body, color = DsColors.Primary, fontWeight = FontWeight.Medium)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.medium)
                                .background(DsColors.DangerLight)
                                .clickable { showDeleteDialog = true }
                                .padding(14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(20.dp))
                            Text("Supprimer la vente", fontSize = DsTextSize.body, color = DsColors.Danger, fontWeight = FontWeight.Medium)
                        }
                    }
                },
                titleContentColor = DsColors.TextPrimary,
                textContentColor  = DsColors.TextSecondary
            )
        }
    }

    // ── Date Pickers ──
    if (showDateFromPicker) {
        DatePickerDialog(
            onDismissRequest = { showDateFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateFromState.selectedDateMillis?.let { millis ->
                        viewModel.filterDateFrom = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDateFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDateFromPicker = false }) { Text("Annuler") }
            }
        ) { DatePicker(state = dateFromState) }
    }

    if (showDateToPicker) {
        DatePickerDialog(
            onDismissRequest = { showDateToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateToState.selectedDateMillis?.let { millis ->
                        viewModel.filterDateTo = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDateToPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDateToPicker = false }) { Text("Annuler") }
            }
        ) { DatePicker(state = dateToState) }
    }

    // ── Filter Sheet ──
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState       = filterSheetState,
            containerColor   = DsColors.Surface
        ) {
            var clientExpanded by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .padding(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs, bottom = DsSpacing.xxxl)
                    .verticalScroll(rememberScrollState())
            ) {
                // ── عنوان ──
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Filtres avancés", fontWeight = FontWeight.Bold, fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary)
                    IconButton(onClick = { showFilterSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = DsColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(DsSpacing.md))

                // ── حالة الوصل ──
                Text("Statut du bon", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    listOf<Pair<String?, String>>(
                        null to "Tous",
                        "pending" to "En attente",
                        "delivered" to "Livré"
                    ).forEach { (value, label) ->
                        val active = viewModel.filterStatus == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(DsShapes.medium)
                                .background(if (active) DsColors.Primary else DsColors.Surface)
                                .border(1.dp, if (active) DsColors.Primary else DsColors.Border, DsShapes.medium)
                                .clickable { viewModel.filterStatus = value }
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
                Spacer(Modifier.height(DsSpacing.md))

                // ── حالة الدفع ──
                Text("Statut du paiement", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    listOf<Pair<String?, String>>(
                        null to "Tous",
                        "paye" to "Payé",
                        "impaye" to "Impayé",
                        "partiel" to "Partiel"
                    ).forEach { (value, label) ->
                        val active = viewModel.filterPaymentStatus == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(DsShapes.medium)
                                .background(if (active) DsColors.Primary else DsColors.Surface)
                                .border(1.dp, if (active) DsColors.Primary else DsColors.Border, DsShapes.medium)
                                .clickable { viewModel.filterPaymentStatus = value }
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
                Spacer(Modifier.height(DsSpacing.md))

                // ── العميل ──
                Text("Client", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                ExposedDropdownMenuBox(
                    expanded         = clientExpanded,
                    onExpandedChange = { clientExpanded = it }
                ) {
                    OutlinedTextField(
                        value         = clients.find { it.first == viewModel.filterClientId }?.second ?: "Tous les clients",
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
                            onClick = { viewModel.filterClientId = null; clientExpanded = false }
                        )
                        clients.forEach { (id, name) ->
                            DropdownMenuItem(
                                text    = { Text(name) },
                                onClick = { viewModel.filterClientId = id; clientExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(DsSpacing.md))

                // ── التاريخ ──
                Text("Période", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    OutlinedTextField(
                        value         = viewModel.filterDateFrom ?: "",
                        onValueChange = {},
                        readOnly      = true,
                        placeholder   = { Text("Du", fontSize = DsTextSize.bodySmall) },
                        trailingIcon  = {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = if (viewModel.filterDateFrom != null) DsColors.Primary else DsColors.TextSecondary
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showDateFromPicker = true },
                        shape    = DsShapes.medium,
                        enabled  = false,
                        colors   = dsTextFieldColors(
                            disabledBorderColor       = if (viewModel.filterDateFrom != null) DsColors.Primary else DsColors.Border,
                            disabledTextColor         = DsColors.TextPrimary,
                            disabledPlaceholderColor  = DsColors.TextSecondary,
                            disabledTrailingIconColor = if (viewModel.filterDateFrom != null) DsColors.Primary else DsColors.TextSecondary
                        )
                    )
                    OutlinedTextField(
                        value         = viewModel.filterDateTo ?: "",
                        onValueChange = {},
                        readOnly      = true,
                        placeholder   = { Text("Au", fontSize = DsTextSize.bodySmall) },
                        trailingIcon  = {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = if (viewModel.filterDateTo != null) DsColors.Primary else DsColors.TextSecondary
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showDateToPicker = true },
                        shape    = DsShapes.medium,
                        enabled  = false,
                        colors   = dsTextFieldColors(
                            disabledBorderColor       = if (viewModel.filterDateTo != null) DsColors.Primary else DsColors.Border,
                            disabledTextColor         = DsColors.TextPrimary,
                            disabledPlaceholderColor  = DsColors.TextSecondary,
                            disabledTrailingIconColor = if (viewModel.filterDateTo != null) DsColors.Primary else DsColors.TextSecondary
                        )
                    )
                }
                Spacer(Modifier.height(DsSpacing.lg))

                // ── أزرار ──
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    OutlinedButton(
                        onClick  = { viewModel.clearAllFilters() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape    = DsShapes.medium,
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.TextPrimary),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                    ) {
                        Text("Réinitialiser", fontSize = DsTextSize.body, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick  = { showFilterSheet = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape    = DsShapes.medium,
                        colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                    ) {
                        Text("Appliquer (${filteredVentes.size})", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ──
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(DsSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ventes", fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            }

            // ── Search bar ──
            OutlinedTextField(
                value         = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                placeholder   = { Text("Rechercher un client ou n° de vente...", fontSize = DsTextSize.bodySmall) },
                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, tint = DsColors.TextSecondary) },
                trailingIcon  = {
                    if (viewModel.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery = "" }) {
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

            // ── Counter + Filter button ──
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.lg),
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
                        "${filteredVentes.size} ventes",
                        fontSize   = DsTextSize.caption,
                        fontWeight = FontWeight.SemiBold,
                        color      = DsColors.TextSecondary
                    )
                }
                Box {
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
                    if (hasActiveFilters) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(6.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(DsColors.Primary)
                        )
                    }
                }
            }

            Spacer(Modifier.height(DsSpacing.sm))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            } else if (error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error ?: "", color = DsColors.Danger)
                }
            } else if (filteredVentes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PointOfSale,
                            contentDescription = null,
                            tint     = DsColors.TextTertiary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(DsSpacing.md))
                        Text("Aucune vente", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("Appuyez sur + pour créer", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                }
            } else {
                val groupedVentes = filteredVentes.groupBy { vente -> vente.created_at?.take(10) ?: "" }

                // ── List ──
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.xs),
                    modifier            = Modifier.weight(1f)
                ) {
                    groupedVentes.forEach { (date, dayVentes) ->
                        // ── Date Header ──
                        item {
                            Text(
                                text       = formatOrderDate(date),
                                fontSize   = DsTextSize.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = DsColors.TextSecondary,
                                modifier   = Modifier.padding(vertical = DsSpacing.sm)
                            )
                        }
                        items(dayVentes) { vente ->
                            VenteCard(
                                vente       = vente,
                                onClick     = { onVenteClick(vente.id) },
                                onLongClick = { longPressVente = vente }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick        = onAddVente,
            containerColor = DsColors.Primary,
            contentColor   = Color.White,
            modifier       = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = DsSpacing.lg,
                    bottom = DsSpacing.bottomNavClearance
                )
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nouvelle vente")
        }
    }
}

@Composable
fun VenteDetailScreen(
    vente             : Vente,
    onBack            : () -> Unit,
    viewModel         : VenteViewModel = hiltViewModel(),
    productViewModel  : com.distrigo.app.ui.products.ProductViewModel =
        hiltViewModel(),
    clientViewModel   : com.distrigo.app.ui.clients.ClientViewModel =
        hiltViewModel(),
    onDelivered       : () -> Unit = {},
    onDeleted         : () -> Unit = {}
) {
    BackHandler { onBack() }

    val fullVenteState by viewModel.selectedVente.collectAsState()
    val displayVente = fullVenteState?.takeIf { it.id == vente.id } ?: vente
    val isDelivered  = displayVente.status == "delivered"
    var isDelivering by remember { mutableStateOf(false) }
    var isDeleting   by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteError  by remember { mutableStateOf("") }
    val context = LocalContext.current
    var showReceiptPreview   by remember { mutableStateOf(false) }
    var showShareOptions     by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    if (showReceiptPreview) {
        ReceiptPreviewSheet(
            receipt          = displayVente.toReceiptData(context),
            onDismiss        = { showReceiptPreview = false },
            onShareRequested = {
                showReceiptPreview = false
                showShareOptions   = true
            }
        )
    }

    if (showShareOptions) {
        ShareOptionsSheet(
            receipt   = displayVente.toReceiptData(context),
            onDismiss = { showShareOptions = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; deleteError = "" },
            title = { Text("Supprimer ce reçu ?") },
            text  = {
                Column {
                    Text("Cette action est irréversible. Les quantités vendues seront remises en stock.")
                    if (deleteError.isNotEmpty()) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text(deleteError, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleting = true
                        viewModel.deleteVente(
                            id        = displayVente.id,
                            onSuccess = {
                                isDeleting       = false
                                showDeleteDialog = false
                                viewModel.loadVentes()
                                onDeleted()
                            },
                            onError = { err -> isDeleting = false; deleteError = err }
                        )
                    },
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(color = DsColors.Danger, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; deleteError = "" }) {
                    Text("Annuler")
                }
            },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.SurfaceSunken)
    ) {
        // ── Header (Outside Ticket) ──
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Vente #${displayVente.id}", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text(displayVente.client_name, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(DsShapes.pill)
                    .background(if (isDelivered) DsColors.SurfaceSunken else DsColors.WarningLight)
                    .padding(horizontal = DsSpacing.sm, vertical = 4.dp)
            ) {
                Text(
                    if (isDelivered) "Livré" else "En attente",
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isDelivered) DsColors.TextSecondary else DsColors.Warning
                )
            }
            Spacer(Modifier.width(DsSpacing.sm))

            Box {
                IconButton(onClick = { overflowMenuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded         = overflowMenuExpanded,
                    onDismissRequest = { overflowMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text        = { Text("Supprimer", color = DsColors.Danger) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger) },
                        onClick     = {
                            overflowMenuExpanded = false
                            showDeleteDialog     = true
                        }
                    )
                }
            }
        }

        // ── Ticket Area ──
        LazyColumn(
            modifier       = Modifier
                .weight(1f)
                .padding(horizontal = DsSpacing.lg),
            contentPadding = PaddingValues(top = DsSpacing.xs, bottom = DsSpacing.xxl)
        ) {
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = com.distrigo.app.ui.common.TicketShape(),
                    colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Client Row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(DsShapes.pill)
                                    .background(if (isDelivered) DsColors.SurfaceSunken else DsColors.PrimaryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isDelivered) DsColors.TextTertiary else DsColors.Primary
                                )
                            }
                            Spacer(Modifier.width(DsSpacing.md))
                            Column {
                                Text(displayVente.client_name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                                Text(displayVente.created_at?.take(10) ?: "", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                            }
                        }

                        Spacer(Modifier.height(DsSpacing.md))
                        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
                        Spacer(Modifier.height(DsSpacing.md))

                        Text(
                            "ARTICLES (${displayVente.items?.size ?: displayVente.items_count ?: 0})",
                            fontSize      = DsTextSize.caption,
                            fontWeight    = FontWeight.Bold,
                            color         = DsColors.TextSecondary,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(DsSpacing.sm))

                        displayVente.items?.let { itemsList ->
                            itemsList.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = DsSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(DsShapes.small)
                                            .background(if (isDelivered) DsColors.SurfaceSunken else DsColors.PrimaryLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.ShoppingCart,
                                            contentDescription = null,
                                            tint     = if (isDelivered) DsColors.TextTertiary else DsColors.Primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(DsSpacing.md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.product_name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1)
                                        Text(
                                            "${formatQty(item.quantity)} ${item.unit_type} × ${"%.2f".format(item.unit_price)} DA",
                                            fontSize = DsTextSize.caption,
                                            color    = DsColors.TextSecondary
                                        )
                                    }
                                    Text(
                                        "${"%.2f".format(item.total_price)} DA",
                                        fontSize   = DsTextSize.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color      = DsColors.TextPrimary
                                    )
                                }
                                if (index < itemsList.size - 1) {
                                    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
                                }
                            }
                        }

                        Spacer(Modifier.height(DsSpacing.md))
                        HorizontalDivider(color = DsColors.Border, thickness = 2.dp)
                        Spacer(Modifier.height(DsSpacing.md))

                        // Total Row
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                "TOTAL",
                                fontSize      = DsTextSize.caption,
                                fontWeight    = FontWeight.Bold,
                                color         = if (isDelivered) DsColors.TextPrimary else DsColors.Primary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "${"%.2f".format(displayVente.total)} DA",
                                fontSize   = DsTextSize.headline,
                                fontWeight = FontWeight.ExtraBold,
                                color      = if (isDelivered) DsColors.TextPrimary else DsColors.Primary
                            )
                        }

                        Spacer(Modifier.height(DsSpacing.md))

                        // Payment Rows
                        val montantPaye = displayVente.montant_paye ?: 0.0
                        val reste       = displayVente.total - montantPaye

                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Montant payé", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.Success)
                            Text("${"%.2f".format(montantPaye)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.Success)
                        }

                        Spacer(Modifier.height(DsSpacing.sm))

                        if (reste > 0) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text("Reste", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.Danger)
                                Text("${"%.2f".format(reste)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.Danger)
                            }
                        } else {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                com.distrigo.app.ui.common.PaidStamp()
                            }
                        }

                        displayVente.note?.takeIf { it.isNotBlank() }?.let { note ->
                            Spacer(Modifier.height(DsSpacing.md))
                            HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
                            Spacer(Modifier.height(DsSpacing.sm))
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Notes, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
                                Text(
                                    note,
                                    fontSize  = DsTextSize.caption,
                                    color     = DsColors.TextSecondary,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Bottom Area ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick  = { showReceiptPreview = true },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape    = DsShapes.medium,
                border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, tint = DsColors.TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Aperçu & Imprimer", fontSize = DsTextSize.caption, color = DsColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick  = { showShareOptions = true },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape    = DsShapes.medium,
                border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = DsColors.TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Partager", fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        if (!isDelivered) {
            Button(
                onClick = {
                    isDelivering = true
                    viewModel.deliverVente(
                        id        = displayVente.id,
                        onSuccess = {
                            isDelivering = false
                            viewModel.loadVentes()
                            onDelivered()
                        },
                        onError = { isDelivering = false }
                    )
                },
                enabled  = !isDelivering,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                    .height(52.dp),
                shape  = DsShapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.Success)
            ) {
                if (isDelivering) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text("Marquer comme livré", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Vente livrée et enregistrée", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VenteCard(vente: Vente, onClick: () -> Unit, onLongClick: () -> Unit) {
    val isDelivered = vente.status == "delivered"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick     = { onClick() },
                onLongClick = { onLongClick() }
            ),
        shape     = DsShapes.large,
        colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
        elevation = CardDefaults.cardElevation(1.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(
                name     = vente.client_name,
                imageUri = vente.client_image_uri,
                size     = 42.dp
            )
            Spacer(Modifier.width(DsSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "Vente #${vente.id}",
                        fontSize   = DsTextSize.caption,
                        fontWeight = FontWeight.Medium,
                        color      = DsColors.TextSecondary
                    )
                    Text(
                        formatOrderTime(vente.created_at),
                        fontSize   = DsTextSize.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color      = DsColors.TextSecondary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        vente.client_name,
                        fontWeight = FontWeight.Bold,
                        fontSize   = DsTextSize.bodyLarge,
                        color      = DsColors.TextPrimary,
                        maxLines   = 1,
                        modifier   = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(DsSpacing.sm))
                    Box(
                        modifier = Modifier
                            .clip(DsShapes.pill)
                            .background(if (isDelivered) DsColors.SuccessLight else DsColors.WarningLight)
                            .padding(horizontal = DsSpacing.sm, vertical = 2.dp)
                    ) {
                        Text(
                            if (isDelivered) "Livré" else "En attente",
                            fontSize   = DsTextSize.caption,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (isDelivered) DsColors.Success else DsColors.Warning
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "${"%.2f".format(vente.total)} DA",
                        fontSize   = DsTextSize.body,
                        fontWeight = FontWeight.Bold,
                        color      = DsColors.Primary
                    )
                    Text(
                        "· ${vente.items_count ?: vente.items?.size ?: 0} article(s)",
                        fontSize = DsTextSize.caption,
                        color    = DsColors.TextSecondary
                    )
                }
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(14.dp))
        }
    }
}