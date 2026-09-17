package com.distrigo.app.ui.purchases

import com.distrigo.app.data.time.BusinessDates
import com.distrigo.app.data.model.numberLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.PurchaseDraft
import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.ui.common.EntityAvatar
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsTopBarRootActions
import com.distrigo.app.ui.designsystem.DsTopBarSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.OrderListFilters
import com.distrigo.app.ui.common.filterOrders
import com.distrigo.app.ui.common.groupOrdersByDay
import com.distrigo.app.ui.common.suppliersOfOrders
import com.distrigo.app.ui.common.KeepIndexScrollPosition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(
    viewModel          : PurchaseViewModel = hiltViewModel(),
    onOpenMenu           : (() -> Unit)? = null,
    onNotificationsClick : () -> Unit = {},
    onProfileClick       : () -> Unit = {},

    modifier           : Modifier = Modifier,
    onFullScreenChange : (Boolean) -> Unit = {},
    onAddOrder         : () -> Unit = {},
    onEditOrder        : (Int) -> Unit = {},
    onOrderClick       : (Int) -> Unit = {},
    onResumeDraft      : (PurchaseDraft) -> Unit = {},
    onOpenBrouillons   : () -> Unit = {}
) {
    val orders    by viewModel.orders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error     by viewModel.error.collectAsState()
    val drafts    by viewModel.drafts.collectAsState()

    var showDraftsSheet by remember { mutableStateOf(false) }

    var longPressOrder   by remember { mutableStateOf<PurchaseOrder?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteError      by remember { mutableStateOf("") }

    // ── Local UI state (لا تُحفظ في ViewModel) ──
    var showFilterSheet    by remember { mutableStateOf(false) }
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker   by remember { mutableStateOf(false) }
    val dateFromState      = rememberDatePickerState()
    val dateToState        = rememberDatePickerState()
    val listState          = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.loadOrders() }

    // ── Suppliers list (مستخرجة من الأوردرات الموجودة) ──
    val suppliers = remember(orders) { suppliersOfOrders(orders) }

    val hasActiveFilters = viewModel.filterReceptionStatus != null ||
            viewModel.filterPaymentStatus != null ||
            viewModel.filterSupplierId != null ||
            viewModel.filterDateFrom != null ||
            viewModel.filterDateTo != null

    // Recomputed only when the orders, the search or a filter changes; see filterOrders.
    val filteredOrders = remember(
        orders,
        viewModel.searchQuery,
        viewModel.filterReceptionStatus,
        viewModel.filterPaymentStatus,
        viewModel.filterSupplierId,
        viewModel.filterDateFrom,
        viewModel.filterDateTo
    ) {
        filterOrders(
            orders  = orders,
            query   = viewModel.searchQuery,
            filters = OrderListFilters(
                receptionStatus = viewModel.filterReceptionStatus,
                paymentStatus   = viewModel.filterPaymentStatus,
                supplierId      = viewModel.filterSupplierId,
                dateFrom        = viewModel.filterDateFrom,
                dateTo          = viewModel.filterDateTo
            )
        )
    }

    // Keyed rows would keep the first visible bon in view when a search or filter changes the list;
    // the list keeps its place by index, as it always has. Beside listState rather than the list,
    // because the state outlives the list while a search shows nothing.
    KeepIndexScrollPosition(listState, filteredOrders)

    // ── Long Press Dialog ──
    longPressOrder?.let { order ->
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false; longPressOrder = null; deleteError = ""
                },
                title = { Text("Supprimer le bon ?") },
                text = {
                    Column {
                        Text("Voulez-vous supprimer le bon ${order.numberLabel} de ${order.supplier_name} ?")
                        if (deleteError.isNotEmpty()) {
                            Spacer(Modifier.height(DsSpacing.sm))
                            Text(deleteError, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteOrder(
                            id = order.id,
                            onSuccess = {
                                showDeleteDialog = false
                                longPressOrder   = null
                                deleteError      = ""
                                viewModel.loadOrders()
                            },
                            onError = { err -> deleteError = err }
                        )
                    }) {
                        Text("Supprimer", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false; longPressOrder = null; deleteError = ""
                    }) {
                        Text("Annuler")
                    }
                },
                containerColor    = DsColors.Surface,
                titleContentColor = DsColors.TextPrimary,
                textContentColor  = DsColors.TextSecondary
            )
        } else {
            AlertDialog(
                onDismissRequest = { longPressOrder = null },
                title            = { Text("Bon ${order.numberLabel}") },
                confirmButton    = {},
                dismissButton    = {},
                icon             = null,
                properties       = androidx.compose.ui.window.DialogProperties(),
                shape            = DsShapes.large,
                containerColor   = DsColors.Surface,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                        // زر التعديل
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.medium)
                                .background(DsColors.PrimaryLight)
                                .clickable {
                                    longPressOrder = null
                                    onEditOrder(order.id)
                                }
                                .padding(14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint     = DsColors.Primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Modifier le bon",
                                fontSize   = DsTextSize.body,
                                color      = DsColors.Primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        // زر الحذف
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
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint     = DsColors.Danger,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Supprimer le bon",
                                fontSize   = DsTextSize.body,
                                color      = DsColors.Danger,
                                fontWeight = FontWeight.Medium
                            )
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
            var supplierExpanded by remember { mutableStateOf(false) }

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
                        "received" to "Reçu"
                    ).forEach { (value, label) ->
                        val active = viewModel.filterReceptionStatus == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(DsShapes.medium)
                                .background(if (active) DsColors.Primary else DsColors.Surface)
                                .border(1.dp, if (active) DsColors.Primary else DsColors.Border, DsShapes.medium)
                                .clickable { viewModel.filterReceptionStatus = value }
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

                // ── المورد ──
                Text("Fournisseur", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                ExposedDropdownMenuBox(
                    expanded         = supplierExpanded,
                    onExpandedChange = { supplierExpanded = it }
                ) {
                    OutlinedTextField(
                        value         = suppliers.find { it.first == viewModel.filterSupplierId }?.second ?: "Tous les fournisseurs",
                        onValueChange = {},
                        readOnly      = true,
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supplierExpanded) },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape         = DsShapes.medium,
                        colors        = dsTextFieldColors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )
                    ExposedDropdownMenu(expanded = supplierExpanded, onDismissRequest = { supplierExpanded = false }) {
                        DropdownMenuItem(
                            text    = { Text("Tous les fournisseurs", color = DsColors.TextSecondary) },
                            onClick = { viewModel.filterSupplierId = null; supplierExpanded = false }
                        )
                        suppliers.forEach { (id, name) ->
                            DropdownMenuItem(
                                text    = { Text(name) },
                                onClick = { viewModel.filterSupplierId = id; supplierExpanded = false }
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

                // ── أزرار التطبيق والإعادة ──
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
                        Text("Appliquer (${filteredOrders.size})", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = Color.White)
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
            // The tab roots are the only screens with no back arrow, so the menu takes the
            // leading slot and the global controls take the trailing one.
            DsTopAppBar(
                title   = "Achats",
                leading = onOpenMenu?.let { DsTopBarLeading.Menu(it) } ?: DsTopBarLeading.None,
                size    = DsTopBarSize.Large
            ) {
                DsTopBarRootActions(
                    onNotificationsClick = onNotificationsClick,
                    onProfileClick       = onProfileClick
                )
            }

            // ── Search bar ──
            DsCompactSearchField(
                value         = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                placeholder   = "Rechercher un bon",
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
                // عداد النتائج
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
                        "${filteredOrders.size} bons",
                        fontSize   = DsTextSize.caption,
                        fontWeight = FontWeight.SemiBold,
                        color      = DsColors.TextSecondary
                    )
                }

                // Brouillons live beside the Bons count rather than in a tab strip: they are a
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

                // زر الفلترة
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
                    // نقطة عند وجود فلتر نشط
                    if (hasActiveFilters) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(DsColors.Primary)
                        )
                    }
                }
            }

            Spacer(Modifier.height(DsSpacing.sm))

            // ── Loading ──
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            } else if (error != null) {
                // ── Error ──
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error ?: "", color = DsColors.Danger)
                }
            } else if (filteredOrders.isEmpty()) {
                // ── Empty State ──
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint     = DsColors.Primary.copy(alpha = 0.3f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(DsSpacing.md))
                        Text(
                            "Aucun bon de commande",
                            color      = DsColors.TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Appuyez sur + pour créer", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                }
            } else {
                val groupedOrders = remember(filteredOrders) { groupOrdersByDay(filteredOrders) }
                // ── List ──
                LazyColumn(
                    state               = listState,
                    contentPadding      = PaddingValues(start = DsSpacing.lg, top = DsSpacing.xs, end = DsSpacing.lg, bottom = DsSpacing.fabBottomClearance + 56.dp),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.xs),
                    modifier            = Modifier.weight(1f)
                ) {
                    groupedOrders.forEach { (date, dayOrders) ->
                        // Keyed, like the rows under it, so a new bon at the top does not shift
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
                        items(dayOrders, key = { it.id }) { order ->
                            PurchaseOrderCard(
                                order   = order,
                                onClick = {
                                    onOrderClick(order.id)
                                    viewModel.loadOrderDetail(order.id)
                                },
                                onLongClick = { longPressOrder = order }
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
            onClick        = { if (drafts.isEmpty()) onAddOrder() else showDraftsSheet = true },
            containerColor = DsColors.Primary,
            contentColor   = Color.White,
            modifier       = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = DsSpacing.lg, bottom = DsSpacing.fabBottomClearance)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nouveau bon")
        }
    }

    if (showDraftsSheet) {
        BrouillonsSheet(
            drafts     = drafts,
            onResume   = { showDraftsSheet = false; onResumeDraft(it) },
            // Starting a new bon leaves every existing draft exactly where it is.
            onStartNew = { showDraftsSheet = false; onAddOrder() },
            onSeeAll   = { showDraftsSheet = false; onOpenBrouillons() },
            onDismiss  = { showDraftsSheet = false }
        )
    }
}

@Composable
fun UnifiedStatColumn(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(1.dp))
        Text(label, fontSize = DsTextSize.caption, color = Color.White.copy(alpha = 0.85f))
    }
}

/** Payment status derived the same way as the "Filtres" payment filter, for the corner ribbon. */
private fun paymentStatusOf(order: PurchaseOrder): String {
    val montantPaye = order.montant_paye ?: 0.0
    return when {
        montantPaye >= order.total && order.total > 0 -> "paye"
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
fun PurchaseOrderCard(order: PurchaseOrder, onClick: () -> Unit, onLongClick: () -> Unit) {
    val isReceived = order.status == "received"
    val (ribbonLabel, ribbonColor) =
        if (isReceived) "REÇU" to DsColors.Success else "EN ATTENTE" to DsColors.Warning
    val (statut, statusColor, statusColorLight) = when (paymentStatusOf(order)) {
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
                name     = order.supplier_name,
                imageUri = order.supplier_image_uri,
                size     = 42.dp
            )
            Spacer(Modifier.width(DsSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Bon ${order.numberLabel}",
                    fontSize   = DsTextSize.caption,
                    fontWeight = FontWeight.Medium,
                    color      = DsColors.TextSecondary
                )
                Text(
                    order.supplier_name,
                    fontSize   = DsTextSize.body,
                    fontWeight = FontWeight.SemiBold,
                    color      = DsColors.TextPrimary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    "${order.items_count ?: order.items?.size ?: 0} article(s) · ${formatOrderTime(order.created_at)}",
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
                    "${"%.2f".format(order.total)} DA",
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

/**
 * Diagonal corner ribbon (à la e-commerce "SALE" badge) for a status flag on a card/row — payment
 * status on the Achats and Dépôt Vente cards, delivery status on the Tournée Vente rows, etc.
 *
 * [edgeReach] is how far the flag reaches along the container's top and right edges — the only
 * knob that controls how compact it looks. The strip is centered on the midpoint of that (top,
 * right) pair, i.e. exactly on the line the flag's hypotenuse should follow, but drawn far longer
 * than [edgeReach] so both of its ends bleed well past the container's actual edges instead of
 * stopping a hair short of them — it's the container's own rounded-shape clip (a Card's Material3
 * Surface, or a plain `Modifier.clip(...)`, both draw with clip = true) that trims the overshoot
 * back down, flush with the straight edges and rounded off at the tip. `wrapContentSize(unbounded
 * = true)` is required so the strip's `.width(90.dp)` actually measures at 90dp instead of being
 * clamped to the [edgeReach] (40dp) box it's centered in — a plain Box passes its own tight
 * constraints down to children, so without this the strip's true ends land barely past the edges
 * (sub-dp), which reads as a floating diamond rather than a ribbon anchored to the corner.
 *
 * Caller must place this as a child of a Box/Card that both clips to a rounded shape and aligns
 * this composable to `Alignment.TopEnd`, so the corner it's centered on is the container's own.
 */
@Composable
fun CornerRibbon(label: String, color: Color, modifier: Modifier = Modifier) {
    val edgeReach = 40.dp
    Box(modifier = modifier.size(edgeReach), contentAlignment = Alignment.Center) {
        Text(
            text       = label,
            color      = Color.White,
            fontSize   = 7.sp,
            fontWeight = FontWeight.Bold,
            maxLines   = 1,
            textAlign  = TextAlign.Center,
            modifier   = Modifier
                .wrapContentSize(unbounded = true)
                .graphicsLayer { rotationZ = 45f }
                .width(90.dp)
                .background(color)
                .padding(vertical = 2.dp)
        )
    }
}

/**
 * "Aujourd'hui", "Hier", or the day in full. Takes a calendar date or a stored instant; an instant
 * is read in the local zone, so a sale at 00:30 is today's, not yesterday's.
 */
fun formatOrderDate(dateStr: String, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    return try {
        val date      = java.time.LocalDate.parse(BusinessDates.localDay(dateStr, zone))
        val today     = java.time.LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        when (date) {
            today     -> "Aujourd'hui"
            yesterday -> "Hier"
            else      -> date.format(
                java.time.format.DateTimeFormatter.ofPattern(
                    "EEEE, d MMMM yyyy",
                    java.util.Locale.FRENCH
                )
            )
        }
    } catch (e: Exception) { dateStr }
}

fun formatOrderTime(createdAt: String?): String {
    return try {
        if (createdAt.isNullOrEmpty()) return ""
        val instant = java.time.Instant.parse(createdAt)
        val zoneId  = java.time.ZoneId.of("Africa/Algiers")
        val local   = instant.atZone(zoneId).toLocalTime()
        local.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) { "" }
}