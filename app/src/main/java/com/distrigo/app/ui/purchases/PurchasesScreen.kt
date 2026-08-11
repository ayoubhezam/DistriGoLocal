package com.distrigo.app.ui.purchases


import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material.icons.filled.CalendarMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(
    viewModel   : PurchaseViewModel = viewModel(),
    modifier    : Modifier = Modifier,
    onFullScreenChange : (Boolean) -> Unit = {},
    onAddOrder  : () -> Unit = {},
    onEditOrder : (Int) -> Unit = {},
    onOrderClick: (Int) -> Unit = {}
) {
    val orders by viewModel.orders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var longPressOrder by remember { mutableStateOf<PurchaseOrder?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf("") }
    // ── Filter state ──
    var search              by remember { mutableStateOf("") }
    var showFilterSheet     by remember { mutableStateOf(false) }
    var filterReceptionStatus by remember { mutableStateOf<String?>(null) } // null=Tous, "pending", "received"
    var filterPaymentStatus   by remember { mutableStateOf<String?>(null) } // null=Tous, "paye", "impaye", "partiel"
    var filterSupplierId      by remember { mutableStateOf<Int?>(null) }
    var filterDateFrom        by remember { mutableStateOf<String?>(null) }  // "yyyy-MM-dd"
    var filterDateTo          by remember { mutableStateOf<String?>(null) }  // "yyyy-MM-dd"
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker   by remember { mutableStateOf(false) }
    val dateFromState = rememberDatePickerState()
    val dateToState   = rememberDatePickerState()
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { viewModel.loadOrders() }

    // ── Suppliers list (مستخرجة من الأوردرات الموجودة) ──
    val suppliers = remember(orders) {
        orders.map { it.supplier_id to it.supplier_name }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    val hasActiveFilters = filterReceptionStatus != null || filterPaymentStatus != null ||
            filterSupplierId != null || filterDateFrom != null || filterDateTo != null

    val filteredOrders = remember(orders, search, filterReceptionStatus, filterPaymentStatus, filterSupplierId, filterDateFrom, filterDateTo) {
        orders.filter { order ->
            // بحث نصي
            val matchSearch = search.isBlank() ||
                    order.supplier_name.contains(search, ignoreCase = true) ||
                    order.id.toString().contains(search)

            // فلتر حالة الوصل
            val matchReception = filterReceptionStatus == null || order.status == filterReceptionStatus

            // فلتر حالة الدفع
            val matchPayment = when (filterPaymentStatus) {
                "paye"    -> (order.montant_paye ?: 0.0) >= order.total && order.total > 0
                "impaye"  -> (order.montant_paye ?: 0.0) <= 0.0
                "partiel" -> (order.montant_paye ?: 0.0) > 0.0 && (order.montant_paye ?: 0.0) < order.total
                else      -> true
            }

            // فلتر المورد
            val matchSupplier = filterSupplierId == null || order.supplier_id == filterSupplierId

            // فلتر التاريخ
            val orderDate = order.created_at?.take(10) ?: order.date.take(10)
            val matchDateFrom = filterDateFrom == null || orderDate >= filterDateFrom!!
            val matchDateTo   = filterDateTo   == null || orderDate <= filterDateTo!!

            matchSearch && matchReception && matchPayment && matchSupplier && matchDateFrom && matchDateTo
        }
    }

    fun clearAllFilters() {
        filterReceptionStatus = null
        filterPaymentStatus   = null
        filterSupplierId      = null
        filterDateFrom        = null
        filterDateTo          = null
    }



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
                        Text("Voulez-vous supprimer le bon #${order.id} de ${order.supplier_name} ?")
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
                                longPressOrder = null
                                deleteError = ""
                                viewModel.loadOrders()
                            },
                            onError = { error ->
                                deleteError = error
                            }
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
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { longPressOrder = null },
                title = { Text("Bon #${order.id}") },
                confirmButton = {},
                dismissButton = {},
                icon = null,
                properties = androidx.compose.ui.window.DialogProperties(),
                shape = DsShapes.large,
                containerColor = DsColors.Surface,
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = DsColors.Primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Modifier le bon",
                                fontSize = DsTextSize.body,
                                color = DsColors.Primary,
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = DsColors.Danger,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Supprimer le bon",
                                fontSize = DsTextSize.body,
                                color = DsColors.Danger,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
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
                        filterDateFrom = java.time.Instant.ofEpochMilli(millis)
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
                        filterDateTo = java.time.Instant.ofEpochMilli(millis)
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
    @OptIn(ExperimentalMaterial3Api::class)
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
                    listOf<Pair<String?, String>>(null to "Tous", "pending" to "En attente", "received" to "Reçu").forEach { (value, label) ->
                        val active = filterReceptionStatus == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(DsShapes.medium)
                                .background(if (active) DsColors.Primary else DsColors.Surface)
                                .border(1.dp, if (active) DsColors.Primary else DsColors.Border, DsShapes.medium)
                                .clickable { filterReceptionStatus = value }
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
                    listOf<Pair<String?, String>>(null to "Tous", "paye" to "Payé", "impaye" to "Impayé", "partiel" to "Partiel").forEach { (value, label) ->
                        val active = filterPaymentStatus == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(DsShapes.medium)
                                .background(if (active) DsColors.Primary else DsColors.Surface)
                                .border(1.dp, if (active) DsColors.Primary else DsColors.Border, DsShapes.medium)
                                .clickable { filterPaymentStatus = value }
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
                        value         = suppliers.find { it.first == filterSupplierId }?.second ?: "Tous les fournisseurs",
                        onValueChange = {},
                        readOnly      = true,
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supplierExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape         = DsShapes.medium,
                        colors        = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )
                    ExposedDropdownMenu(expanded = supplierExpanded, onDismissRequest = { supplierExpanded = false }) {
                        DropdownMenuItem(
                            text    = { Text("Tous les fournisseurs", color = DsColors.TextSecondary) },
                            onClick = { filterSupplierId = null; supplierExpanded = false }
                        )
                        suppliers.forEach { (id, name) ->
                            DropdownMenuItem(
                                text    = { Text(name) },
                                onClick = { filterSupplierId = id; supplierExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(DsSpacing.md))

                // ── التاريخ ──
                Text("Période", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    OutlinedTextField(
                        value         = filterDateFrom ?: "",
                        onValueChange = {},
                        readOnly      = true,
                        placeholder   = { Text("Du", fontSize = DsTextSize.bodySmall) },
                        trailingIcon  = {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = if (filterDateFrom != null) DsColors.Primary else DsColors.TextSecondary
                            )
                        },
                        modifier      = Modifier.weight(1f).clickable { showDateFromPicker = true },
                        shape         = DsShapes.medium,
                        enabled       = false,
                        colors        = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor    = if (filterDateFrom != null) DsColors.Primary else DsColors.Border,
                            disabledTextColor      = DsColors.TextPrimary,
                            disabledPlaceholderColor = DsColors.TextSecondary,
                            disabledTrailingIconColor = if (filterDateFrom != null) DsColors.Primary else DsColors.TextSecondary
                        )
                    )
                    OutlinedTextField(
                        value         = filterDateTo ?: "",
                        onValueChange = {},
                        readOnly      = true,
                        placeholder   = { Text("Au", fontSize = DsTextSize.bodySmall) },
                        trailingIcon  = {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = if (filterDateTo != null) DsColors.Primary else DsColors.TextSecondary
                            )
                        },
                        modifier      = Modifier.weight(1f).clickable { showDateToPicker = true },
                        shape         = DsShapes.medium,
                        enabled       = false,
                        colors        = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor    = if (filterDateTo != null) DsColors.Primary else DsColors.Border,
                            disabledTextColor      = DsColors.TextPrimary,
                            disabledPlaceholderColor = DsColors.TextSecondary,
                            disabledTrailingIconColor = if (filterDateTo != null) DsColors.Primary else DsColors.TextSecondary
                        )
                    )
                }
                Spacer(Modifier.height(DsSpacing.lg))

                // ── أزرار التطبيق والإعادة ──
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    OutlinedButton(
                        onClick  = { clearAllFilters() },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = DsShapes.medium,
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.TextPrimary),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                    ) {
                        Text("Réinitialiser", fontSize = DsTextSize.body, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick  = { showFilterSheet = false },
                        modifier = Modifier.weight(1f).height(48.dp),
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
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Achats", fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            }

// ── Search bar ──
            OutlinedTextField(
                value         = search,
                onValueChange = { search = it },
                placeholder   = { Text("Rechercher un fournisseur ou n° de bon...", fontSize = DsTextSize.bodySmall) },
                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, tint = DsColors.TextSecondary) },
                trailingIcon  = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer", tint = DsColors.TextSecondary)
                        }
                    }
                },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpacing.lg)
                    .clip(DsShapes.large),
                shape         = DsShapes.large,
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
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
                // عداد النتائج
                Row(
                    modifier = Modifier
                        .clip(DsShapes.medium)
                        .background(DsColors.SurfaceSunken)
                        .padding(horizontal = DsSpacing.sm, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
                            if (hasActiveFilters) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(14.dp))
                            } else {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    // نقطة حمراء عند وجود فلتر نشط
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
                            Icons.Default.ShoppingCart, contentDescription = null,
                            tint = DsColors.Primary.copy(alpha = 0.3f), modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(DsSpacing.md))
                        Text(
                            "Aucun bon de commande",
                            color = DsColors.TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Appuyez sur + pour créer", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                }
            } else {
                val groupedOrders = remember(filteredOrders) {
                    filteredOrders.groupBy { order -> order.created_at?.take(10) ?: order.date.take(10) }
                }
                // ── List ──
                LazyColumn(
                    state               = listState,
                    contentPadding      = PaddingValues(start = DsSpacing.lg, top = DsSpacing.xs, end = DsSpacing.lg, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.xs),
                    modifier            = Modifier.weight(1f)
                ) {
                    groupedOrders.forEach { (date, dayOrders) ->
                        item {
                            Text(
                                text = formatOrderDate(date),
                                fontSize = DsTextSize.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = DsColors.TextSecondary,
                                modifier = Modifier.padding(vertical = DsSpacing.sm)
                            )
                        }
                        items(dayOrders) { order ->
                            PurchaseOrderCard(
                                order = order,
                                onClick = {
                                    onOrderClick(order.id)
                                    viewModel.loadOrderDetail(order.id)
                                },
                                onLongClick = {
                                    longPressOrder = order
                                }
                            )
                        }
                    }
                }

            }
        }

        FloatingActionButton(
            onClick        = onAddOrder,
            containerColor = DsColors.Primary,
            contentColor   = Color.White,
            modifier       = Modifier
                .align(Alignment.BottomEnd)
                .padding(DsSpacing.lg)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nouveau bon")
        }
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PurchaseOrderCard(order: PurchaseOrder, onClick: () -> Unit, onLongClick: () -> Unit) {
    val isReceived = order.status == "received"

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
            Box(
                modifier         = Modifier
                    .size(42.dp)
                    .clip(DsShapes.medium)
                    .background(if (isReceived) DsColors.SuccessLight else DsColors.WarningLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint     = if (isReceived) DsColors.Success else DsColors.Warning,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(DsSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                // السطر 1: Bon # + الوقت
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "Bon #${order.id}",
                        fontSize   = DsTextSize.caption,
                        fontWeight = FontWeight.Medium,
                        color      = DsColors.TextSecondary
                    )
                    Text(
                        formatOrderTime(order.created_at),
                        fontSize   = DsTextSize.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color      = DsColors.TextSecondary
                    )
                }
                Spacer(Modifier.height(2.dp))
                // السطر 2: اسم المورد + badge
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        order.supplier_name,
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
                            .background(if (isReceived) DsColors.SuccessLight else DsColors.WarningLight)
                            .padding(horizontal = DsSpacing.sm, vertical = 2.dp)
                    ) {
                        Text(
                            if (isReceived) "Reçu" else "En attente",
                            fontSize   = DsTextSize.caption,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (isReceived) DsColors.Success else DsColors.Warning
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                // السطر 3: المبلغ + عدد المنتجات
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "${"%.2f".format(order.total)} DA",
                        fontSize   = DsTextSize.body,
                        fontWeight = FontWeight.Bold,
                        color      = DsColors.Primary
                    )
                    Text(
                        "· ${order.items_count ?: order.items?.size ?: 0} article(s)",
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
fun formatOrderDate(dateStr: String): String {
    return try {
        val date      = java.time.LocalDate.parse(dateStr.take(10))
        val today     = java.time.LocalDate.now()
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
