package com.distrigo.app.ui.purchases

import androidx.activity.compose.BackHandler
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
import com.distrigo.app.ui.products.ProductViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import com.distrigo.app.ui.components.rememberScrollCollapsed
import com.distrigo.app.ui.components.CollapsibleHeader
import com.distrigo.app.ui.components.rememberFabBottomPadding
import com.distrigo.app.ui.components.ScrollAwareFab

@Composable
fun PurchasesScreen(
    viewModel : PurchaseViewModel = viewModel(),
    modifier  : Modifier = Modifier,
    onFullScreenChange: (Boolean) -> Unit = {}
) {
    val orders by viewModel.orders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var showNewOrder by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<PurchaseOrder?>(null) }
    var longPressOrder by remember { mutableStateOf<PurchaseOrder?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val productViewModel: ProductViewModel = viewModel()
    var editingOrderId by remember { mutableStateOf<Int?>(null) }
    var deleteError by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val rawCollapsed by rememberScrollCollapsed(listState)
    val collapsed = rawCollapsed && orders.size >= 10
    val fabBottomPadding by rememberFabBottomPadding(collapsed)

    LaunchedEffect(Unit) { viewModel.loadOrders() }
    // ── New Order Screen ──
    if (showNewOrder) {
        onFullScreenChange(true)
        BackHandler {
            showNewOrder = false
            onFullScreenChange(false)
        }
        PurchaseFormScreen(
            onBack = {
                showNewOrder = false
                onFullScreenChange(false)
            },
            onSaved = {
                showNewOrder = false
                onFullScreenChange(false)
                viewModel.loadOrders()
            }
        )
        return
    }

    // ── Edit Order Screen ──
    editingOrderId?.let { id ->
        val fullOrderState by viewModel.selectedOrder.collectAsState()
        val fullOrder = fullOrderState
        if (fullOrder != null && fullOrder.id == id) {
            onFullScreenChange(true)
            BackHandler {
                editingOrderId = null
                onFullScreenChange(false)
            }
            PurchaseFormScreen(
                order = fullOrder,
                onBack = {
                    editingOrderId = null
                    onFullScreenChange(false)
                },
                onSaved = {
                    editingOrderId = null
                    onFullScreenChange(false)
                    viewModel.loadOrders()
                }
            )
            return
        }
    }

    // ── Detail Screen ──
    selectedOrder?.let { order ->
        BackHandler { selectedOrder = null }
        PurchaseOrderDetailScreen(
            order = order,
            onBack = { selectedOrder = null },
            viewModel = viewModel,
            onReceived = {
                selectedOrder = null
                viewModel.loadOrders()
            },
            onFullScreenChange = onFullScreenChange
        )
        return
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
                                    viewModel.loadOrderDetail(order.id)
                                    editingOrderId = order.id
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
    LaunchedEffect(collapsed) {
        onFullScreenChange(collapsed)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Header (ثابت دائمًا) ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Achats", fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            }

            // ── Stats (تختفي/تظهر مع التمرير) ──
            CollapsibleHeader(collapsed = collapsed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DsSpacing.lg)
                        .clip(DsShapes.large)
                        .background(DsColors.Primary)
                        .padding(vertical = DsSpacing.md, horizontal = DsSpacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween
                )  {
                    UnifiedStatColumn(
                        icon = Icons.Default.Receipt,
                        value = orders.size.toString(),
                        label = "Total bons"
                    )
                    UnifiedStatColumn(
                        icon = Icons.Default.Schedule,
                        value = orders.count { it.status == "pending" }.toString(),
                        label = "En attente"
                    )
                    UnifiedStatColumn(
                        icon = Icons.Default.CheckCircle,
                        value = orders.count { it.status == "received" }.toString(),
                        label = "Reçus"
                    )
                }
            }

            Spacer(Modifier.height(DsSpacing.md))

            Text(
                "Historique des achats",
                fontSize = DsTextSize.caption,
                color = DsColors.TextSecondary,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )

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
            } else if (orders.isEmpty()) {
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
                val groupedOrders = remember(orders) {
                    orders.groupBy { order -> order.created_at?.take(10) ?: order.date.take(10) }
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
                                    selectedOrder = order
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

        ScrollAwareFab(
            onClick = { showNewOrder = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = DsSpacing.lg, bottom = fabBottomPadding)
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
