package com.distrigo.app.ui.purchases

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.ui.products.*
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
                            Spacer(Modifier.height(8.dp))
                            Text(deleteError, fontSize = 13.sp, color = DestructiveRed)
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
                                productViewModel.loadProducts()
                            },
                            onError = { error ->
                                deleteError = error
                            }
                        )
                    }) {
                        Text("Supprimer", color = DestructiveRed, fontWeight = FontWeight.SemiBold)
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
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // زر التعديل
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BlueLight)
                                .clickable {
                                    longPressOrder = null
                                    viewModel.loadOrderDetail(order.id)
                                    editingOrderId = order.id
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Modifier le bon",
                                fontSize = 14.sp,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        // زر الحذف
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(RedLight)
                                .clickable { showDeleteDialog = true }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = DestructiveRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Supprimer le bon",
                                fontSize = 14.sp,
                                color = DestructiveRed,
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
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Header (ثابت دائمًا) ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Achats", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            // ── Stats (تختفي/تظهر مع التمرير) ──
            CollapsibleHeader(collapsed = collapsed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PrimaryBlue)
                        .padding(vertical = 12.dp, horizontal = 16.dp),
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

            Spacer(Modifier.height(12.dp))

            Text(
                "Historique des achats",
                fontSize = 12.sp,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            // ── Loading ──
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else if (error != null) {
                // ── Error ──
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error ?: "", color = DestructiveRed)
                }
            } else if (orders.isEmpty()) {
                // ── Empty State ──
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ShoppingCart, contentDescription = null,
                            tint = PrimaryBlue.copy(alpha = 0.3f), modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Aucun bon de commande",
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Appuyez sur + pour créer", fontSize = 12.sp, color = TextMuted)
                    }
                }
            } else {
                val groupedOrders = remember(orders) {
                    orders.groupBy { order -> order.created_at?.take(10) ?: order.date.take(10) }
                }
                // ── List ──
                LazyColumn(
                    state               = listState,
                    contentPadding      = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier            = Modifier.weight(1f)
                ) {
                    groupedOrders.forEach { (date, dayOrders) ->
                        item {
                            Text(
                                text = formatOrderDate(date),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted,
                                modifier = Modifier.padding(vertical = 8.dp)
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
                .padding(end = 16.dp, bottom = fabBottomPadding)
        )
    }
}


@Composable
fun UnifiedStatColumn(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(1.dp))
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f))
    }
}
@Composable
fun StatBox(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = TextMuted)
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
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isReceived) GreenLight else Color(0xFFFFF3E0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint     = if (isReceived) AccentGreen else Color(0xFFE65100),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // السطر 1: Bon # + الوقت
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "Bon #${order.id}",
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color      = TextMuted
                    )
                    Text(
                        formatOrderTime(order.created_at),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color      = TextMuted
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
                        fontSize   = 15.sp,
                        color      = TextPrimary,
                        maxLines   = 1,
                        modifier   = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isReceived) GreenLight else Color(0xFFFFF3E0))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (isReceived) "Reçu" else "En attente",
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (isReceived) AccentGreen else Color(0xFFE65100)
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
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color      = PrimaryBlue
                    )
                    Text(
                        "· ${order.items_count ?: order.items?.size ?: 0} article(s)",
                        fontSize = 11.sp,
                        color    = TextMuted
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
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