package com.distrigo.app.ui.ventes

import com.distrigo.app.data.model.numberLabel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.distrigo.app.ui.common.KeepIndexScrollPosition
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.ui.purchases.CornerRibbon
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
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsTopBarSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.products.formatQty
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.VenteListFilters
import com.distrigo.app.ui.common.clientsOfVentes
import com.distrigo.app.ui.common.depotVentesOf
import com.distrigo.app.ui.common.filterVentes
import com.distrigo.app.ui.common.groupVentesByDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentesScreen(
    viewModel    : VenteViewModel = hiltViewModel(),
    modifier     : Modifier = Modifier,
    onBack       : (() -> Unit)? = null,
    onAddVente   : () -> Unit = {},
    onEditVente  : (Int) -> Unit = {},
    onVenteClick : (Int) -> Unit = {},
    onResumeDraft    : (com.distrigo.app.data.model.VenteDraft) -> Unit = {},
    onOpenBrouillons : () -> Unit = {}
) {
    val ventes      by viewModel.ventes.collectAsState()
    val drafts      by viewModel.drafts.collectAsState()
    var showDraftsSheet by remember { mutableStateOf(false) }
    // Remembered against `ventes`, which only changes when the list reloads. Built inline, this
    // was a new list on every recomposition, so the remember(depotVentes) below — keyed on it —
    // never once reused its result.
    val depotVentes = remember(ventes) { depotVentesOf(ventes) }
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
    val clients = remember(depotVentes) { clientsOfVentes(depotVentes) }

    val hasActiveFilters = viewModel.filterStatus != null ||
            viewModel.filterPaymentStatus != null ||
            viewModel.filterClientId != null ||
            viewModel.filterDateFrom != null ||
            viewModel.filterDateTo != null

    // Recomputed only when the depot list, the search or a filter changes; see filterVentes.
    val filteredVentes = remember(
        depotVentes,
        viewModel.searchQuery,
        viewModel.filterStatus,
        viewModel.filterPaymentStatus,
        viewModel.filterClientId,
        viewModel.filterDateFrom,
        viewModel.filterDateTo
    ) {
        filterVentes(
            ventes  = depotVentes,
            query   = viewModel.searchQuery,
            filters = VenteListFilters(
                status        = viewModel.filterStatus,
                paymentStatus = viewModel.filterPaymentStatus,
                clientId      = viewModel.filterClientId,
                dateFrom      = viewModel.filterDateFrom,
                dateTo        = viewModel.filterDateTo
            )
        )
    }

    // ── Long Press Dialog ──
    longPressVente?.let { vente ->
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false; longPressVente = null; deleteError = "" },
                title = { Text("Supprimer la vente ?") },
                text  = {
                    Column {
                        Text("Voulez-vous supprimer la vente ${vente.numberLabel} de ${vente.client_name} ?")
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
                title            = { Text("Vente ${vente.numberLabel}") },
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
            // Reached by being pushed from the Ventes hub, so it needs a back affordance —
            // system back already popped the hub, but nothing on screen said so. Null-safe
            // because the screen is still usable as a root, where None is right.
            DsTopAppBar(
                title   = "Ventes",
                leading = onBack?.let { DsTopBarLeading.Back(it) } ?: DsTopBarLeading.None,
                size    = DsTopBarSize.Large
            )

            // ── Search bar ──
            DsCompactSearchField(
                value         = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                placeholder   = "Rechercher une vente",
                modifier      = Modifier.padding(horizontal = DsSpacing.lg)
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

                // Brouillons live beside the ventes count rather than in a tab strip: they are a
                // distinct list with its own screen, but they are not a peer view of the same data.
                if (drafts.isNotEmpty()) {
                    Spacer(Modifier.width(DsSpacing.xs))
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
                            "Brouillons (${drafts.size})",
                            fontSize   = DsTextSize.caption,
                            fontWeight = FontWeight.SemiBold,
                            color      = DsColors.Primary
                        )
                    }
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
                val groupedVentes = remember(filteredVentes) { groupVentesByDay(filteredVentes) }

                // Keyed rows would keep the first visible sale in view when a search or filter
                // changes the list; the list keeps its place by index, as it always has.
                val listState = rememberLazyListState()
                KeepIndexScrollPosition(listState, filteredVentes)

                // ── List ──
                LazyColumn(
                    state               = listState,
                    // Bottom pad clears the raised FAB (clearance + 56dp FAB), so the last card
                    // scrolls out from under it rather than sitting behind the button and nav bar.
                    contentPadding      = PaddingValues(
                        start  = DsSpacing.lg,
                        end    = DsSpacing.lg,
                        top    = DsSpacing.xs,
                        bottom = DsSpacing.fabBottomClearance + 56.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.xs),
                    modifier            = Modifier.weight(1f)
                ) {
                    groupedVentes.forEach { (date, dayVentes) ->
                        // ── Date Header ──
                        // Keyed, like the rows under it, so a new sale at the top does not shift
                        // every row's identity down by one.
                        item(key = "date_$date") {
                            Text(
                                text       = formatOrderDate(date),
                                fontSize   = DsTextSize.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = DsColors.TextSecondary,
                                modifier   = Modifier.padding(vertical = DsSpacing.sm)
                            )
                        }
                        items(dayVentes, key = { it.id }) { vente ->
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
            // The resume decision is made here, before the form graph is entered — which is what
            // keeps a process-death return from ever prompting, since that path re-enters the
            // graph without passing through this button.
            onClick        = { if (drafts.isEmpty()) onAddVente() else showDraftsSheet = true },
            containerColor = DsColors.Primary,
            contentColor   = Color.White,
            modifier       = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = DsSpacing.lg,
                    bottom = DsSpacing.fabBottomClearance
                )
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nouvelle vente")
        }
    }

    if (showDraftsSheet) {
        VenteBrouillonsSheet(
            drafts     = drafts,
            onResume   = { showDraftsSheet = false; onResumeDraft(it) },
            // Starting a new vente leaves every existing draft exactly where it is.
            onStartNew = { showDraftsSheet = false; onAddVente() },
            onSeeAll   = { showDraftsSheet = false; onOpenBrouillons() },
            onDismiss  = { showDraftsSheet = false }
        )
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
    businessViewModel : com.distrigo.app.ui.settings.receipt.BusinessSettingsViewModel =
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

    // The receipt header names the client's type and secteur, which the vente does not carry —
    // it snapshots only the name. Read from the Room-observed list rather than snapshotted onto
    // the sale, so every receipt already in the database gets the fuller header too. Null while
    // the list loads, and for a client that has since been deleted; the header shows "-" then and
    // still names them from the vente's own snapshot.
    val allClients by clientViewModel.clients.collectAsState()
    val receiptClient = remember(allClients, displayVente.client_id) {
        allClients.find { it.id == displayVente.client_id }
    }

    // Units per colis for the receipt's "Unité/colis" column. A vente records what was sold, never
    // how the product is packaged, so it comes from the catalogue — same reasoning, and the same
    // Room-observed list, as the client lookup above.
    val allProducts by productViewModel.products.collectAsState()
    val receiptPackSizes = remember(allProducts) {
        allProducts.filter { it.pack_size > 0 }.associate { it.id to it.pack_size }
    }

    // The business header comes from the database; a receipt opens once it has loaded, a moment after
    // the screen does, rather than printing a placeholder name.
    val business by businessViewModel.settings.collectAsState()

    if (showReceiptPreview) business?.let { business ->
        ReceiptPreviewSheet(
            receipt          = displayVente.toReceiptData(business, receiptClient, receiptPackSizes),
            onDismiss        = { showReceiptPreview = false },
            onShareRequested = {
                showReceiptPreview = false
                showShareOptions   = true
            }
        )
    }

    if (showShareOptions) business?.let { business ->
        ShareOptionsSheet(
            receipt   = displayVente.toReceiptData(business, receiptClient, receiptPackSizes),
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
        DsTopAppBar(
            title          = "Vente ${displayVente.numberLabel}",
            subtitle       = displayVente.client_name,
            leading        = DsTopBarLeading.Back({ onBack() }),
            // This screen is painted on SurfaceSunken, so the bar matches rather than
            // floating on a different white.
            containerColor = DsColors.SurfaceSunken
        ) {
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
                border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border),
                // Both buttons split the row evenly, so ButtonDefaults' 24dp-per-side content
                // padding costs more than the longer label can spare. Trimmed on both, so the
                // two stay identical.
                contentPadding = PaddingValues(horizontal = DsSpacing.sm, vertical = DsSpacing.xs)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, tint = DsColors.TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Aperçu & Imprimer",
                    fontSize   = DsTextSize.bodySmall,
                    color      = DsColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    softWrap   = false
                )
            }
            OutlinedButton(
                onClick  = { showShareOptions = true },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape    = DsShapes.medium,
                border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border),
                // Both buttons split the row evenly, so ButtonDefaults' 24dp-per-side content
                // padding costs more than the longer label can spare. Trimmed on both, so the
                // two stay identical.
                contentPadding = PaddingValues(horizontal = DsSpacing.sm, vertical = DsSpacing.xs)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = DsColors.TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Partager",
                    fontSize   = DsTextSize.bodySmall,
                    color      = DsColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    softWrap   = false
                )
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

/** Payment status derived the same way as the "Filtres" payment filter, for the corner ribbon. */
private fun paymentStatusOf(vente: Vente): String {
    val montantPaye = vente.montant_paye ?: 0.0
    return when {
        montantPaye >= vente.total && vente.total > 0 -> "paye"
        montantPaye > 0.0                              -> "partiel"
        else                                            -> "impaye"
    }
}

/**
 * Same shape as the Tournée's bon rows, line for line.
 *
 * Three lists show the same kind of record and used to lay it out three ways. The Tournée row is
 * the one that reads fastest, so it is the one the other two follow: number, then who, then the
 * quiet meta line; amount and payment on the right, kept clear of the ribbon.
 *
 * The two status signals swap places to match it. The ribbon carries fulfilment — whether the
 * goods have moved — and the pill carries money. Before this the two screens had them the other
 * way round from the Tournée, so the same colour in the same corner meant different things
 * depending on which list you were looking at.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VenteCard(vente: Vente, onClick: () -> Unit, onLongClick: () -> Unit) {
    val isDelivered = vente.status == "delivered"
    val (ribbonLabel, ribbonColor) =
        if (isDelivered) "LIVRÉ" to DsColors.Success else "EN ATTENTE" to DsColors.Warning
    val (statut, statusColor, statusColorLight) = when (paymentStatusOf(vente)) {
        "paye"    -> Triple("Payé",    DsColors.Success, DsColors.SuccessLight)
        "partiel" -> Triple("Partiel", DsColors.Warning, DsColors.WarningLight)
        else      -> Triple("Impayé",  DsColors.Danger,  DsColors.DangerLight)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .combinedClickable(
                onClick     = { onClick() },
                onLongClick = { onLongClick() }
            )
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(DsSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kept from this card's older layout: unlike the Tournée, these lists are scanned by
            // who the record is for across hundreds of rows, and the face is how you find them.
            EntityAvatar(
                name     = vente.client_name,
                imageUri = vente.client_image_uri,
                size     = 42.dp
            )
            Spacer(Modifier.width(DsSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Vente ${vente.numberLabel}",
                    fontSize   = DsTextSize.caption,
                    fontWeight = FontWeight.Medium,
                    color      = DsColors.TextSecondary
                )
                Text(
                    vente.client_name,
                    fontSize   = DsTextSize.body,
                    fontWeight = FontWeight.SemiBold,
                    color      = DsColors.TextPrimary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    "${vente.items_count ?: vente.items?.size ?: 0} article(s) · ${formatOrderTime(vente.created_at)}",
                    fontSize = DsTextSize.caption,
                    color    = DsColors.TextSecondary
                )
            }

            // Inset past the ribbon, which is drawn over this row and would otherwise take the
            // end of the amount with it.
            Column(
                horizontalAlignment = Alignment.End,
                modifier            = Modifier.padding(end = 28.dp)
            ) {
                Text(
                    "${"%.2f".format(vente.total)} DA",
                    fontSize   = DsTextSize.body,
                    fontWeight = FontWeight.Bold,
                    color      = DsColors.Primary
                )
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