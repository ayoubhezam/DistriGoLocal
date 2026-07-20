package com.distrigo.app.ui.purchases

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.ui.products.*
import com.distrigo.app.ui.products.ProductViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.ui.components.ReceiptPreviewSheet
import com.distrigo.app.ui.components.ShareOptionsSheet
import com.distrigo.app.ui.components.toReceiptData
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.asImageBitmap
@Composable
fun PurchaseOrderDetailScreen(
    order      : PurchaseOrder,
    onBack     : () -> Unit,
    onReceived : () -> Unit,
    viewModel  : PurchaseViewModel,
    productViewModel: ProductViewModel = viewModel(),
    onFullScreenChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val selectedOrder by viewModel.selectedOrder.collectAsState()
    val displayOrder  = selectedOrder ?: order
    val isReceived    = displayOrder.status == "received"
    var isLoading     by remember { mutableStateOf(false) }
    var showReceiveDialog by remember { mutableStateOf(false) }
    var receiveUserName   by remember { mutableStateOf("") }
    var showReopenDialog  by remember { mutableStateOf(false) }
    var showEditScreen    by remember { mutableStateOf(false) }
    var showDeleteDialog  by remember { mutableStateOf(false) }
    var showReceiptPreview by remember { mutableStateOf(false) }
    var showShareOptions    by remember { mutableStateOf(false) }
    var deleteError       by remember { mutableStateOf("") }

    BackHandler { onBack() }
    LaunchedEffect(Unit) {
        productViewModel.loadProducts()
    }

    if (showReopenDialog) {
        AlertDialog(
            onDismissRequest = { showReopenDialog = false },
            title = { Text("Rouvrir le bon ?") },
            text  = {
                Text(
                    "Ce bon a déjà été reçu. Le rouvrir annulera la mise à jour du stock. Voulez-vous continuer ?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReopenDialog = false
                    isLoading = true
                    viewModel.reopenOrder(
                        id        = displayOrder.id,
                        onSuccess = {
                            isLoading     = false
                            showEditScreen = true
                        },
                        onError   = { isLoading = false }
                    )
                }) {
                    Text("Rouvrir", color = Color(0xFFE65100), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReopenDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showEditScreen) {
        BackHandler {
            showEditScreen = false
            onFullScreenChange(false)
        }
        onFullScreenChange(true)
        PurchaseFormScreen(
            order             = displayOrder,
            onBack            = {
                showEditScreen = false
                onFullScreenChange(false)
            },
            onSaved           = {
                showEditScreen = false
                onFullScreenChange(false)
                viewModel.loadOrderDetail(displayOrder.id)
                viewModel.loadOrders()
                productViewModel.loadProducts()
            },
            purchaseViewModel = viewModel
        )
        return
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; deleteError = "" },
            title = { Text("Supprimer le bon ?") },
            text  = {
                Column {
                    Text("Voulez-vous supprimer le bon #${displayOrder.id} ?")
                    if (deleteError.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(deleteError, fontSize = 13.sp, color = DestructiveRed)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteOrder(
                        id        = displayOrder.id,
                        onSuccess = {
                            showDeleteDialog = false
                            productViewModel.loadProducts()
                            onBack()
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
                TextButton(onClick = { showDeleteDialog = false; deleteError = "" }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showReceiptPreview) {
        ReceiptPreviewSheet(
            receipt          = displayOrder.toReceiptData(context),
            onDismiss        = { showReceiptPreview = false },
            onShareRequested = {
                showReceiptPreview = false
                showShareOptions    = true
            }
        )
    }

    if (showShareOptions) {
        ShareOptionsSheet(
            receipt   = displayOrder.toReceiptData(context),
            onDismiss = { showShareOptions = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ── Header ──
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        )
        {
            // زر الحذف
            IconButton(
                onClick  = { showDeleteDialog = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RedLight)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Supprimer",
                    tint     = DestructiveRed,
                    modifier = Modifier.size(18.dp)
                )
            }

            // زر التعديل
            IconButton(
                onClick  = {
                    if (displayOrder.status == "received") {
                        showReopenDialog = true
                    } else {
                        showEditScreen = true
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlueLight)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Modifier",
                    tint     = PrimaryBlue,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Status badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isReceived) GreenLight else Color(0xFFFFF3E0))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    if (isReceived) "Reçu" else "En attente",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isReceived) AccentGreen else Color(0xFFE65100)
                )
            }
        }

        LazyColumn(
            contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier            = Modifier.weight(1f)
        ) {
            // ── Supplier Card ──
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
                ) {
                    Row(
                        modifier          = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Business, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(displayOrder.supplier_name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                            Text(displayOrder.date, fontSize = 12.sp, color = TextMuted)
                        }
                    }
                }
            }

            // ── Items ──
            item {
                Text("Articles", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
            }

            displayOrder.items?.let { itemsList ->
                items(itemsList) { item ->
                    val products by productViewModel.products.collectAsState()
                    val matchedProduct = remember(products, item.product_id) { products.find { it.id == item.product_id } }

                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp),
                        border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
                    ) {
                        Row(
                            modifier              = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier              = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier         = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BlueLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val bitmap = remember(matchedProduct?.image_uri) {
                                        matchedProduct?.image_uri?.let { uri ->
                                            val imageBytes = android.util.Base64.decode(uri.substringAfter("base64,"), android.util.Base64.NO_WRAP)
                                            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                        }
                                    }
                                    if (bitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bitmap.asImageBitmap(), contentDescription = null,
                                            modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Column {
                                    Text(item.product_name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Text("${item.quantity} ${item.unit_type} × ${"%.2f".format(item.unit_cost)} DA", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                            Text("${"%.2f".format(item.total_cost)} DA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                    }
                }
            }


// ── Total ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BlueLight)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Total", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        Text("${"%.2f".format(displayOrder.total)} DA", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryBlue)
                    }

                    val montantPaye = displayOrder.montant_paye ?: 0.0
                    val reste       = displayOrder.total - montantPaye

                    HorizontalDivider(color = PrimaryBlue.copy(alpha = 0.2f), thickness = 0.5.dp)

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Montant payé", fontSize = 13.sp, color = AccentGreen, fontWeight = FontWeight.Medium)
                        Text("${"%.2f".format(montantPaye)} DA", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                    }

                    if (reste > 0) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Reste (Impayé)", fontSize = 13.sp, color = DestructiveRed, fontWeight = FontWeight.Medium)
                            Text("${"%.2f".format(reste)} DA", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DestructiveRed)
                        }
                    } else {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Statut", fontSize = 13.sp, color = AccentGreen, fontWeight = FontWeight.Medium)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(GreenLight)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("Réglé ✓", fontSize = 12.sp, color = AccentGreen, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── Note ──
            displayOrder.note?.let { note ->
                item {
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = CardDefaults.cardColors(containerColor = MutedGray),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Notes, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            Text(note, fontSize = 13.sp, color = TextPrimary)
                        }
                    }
                }
            }

            // ── Actions: Aperçu & Imprimer / Partager ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick  = { showReceiptPreview = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Aperçu & Imprimer", fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick  = { showShareOptions = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Partager", fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── Receive Button ──
            if (!isReceived) {
                item {
                    Button(
                        onClick  = { showReceiveDialog = true },
                        enabled  = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(16.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Marquer comme reçu", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }



        }
        if (showReceiveDialog) {
            AlertDialog(
                onDismissRequest = { showReceiveDialog = false },
                title = { Text("Confirmer la réception", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Le stock du dépôt sera mis à jour pour tous les articles de ce bon.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value         = receiveUserName,
                            onValueChange = { receiveUserName = it },
                            placeholder   = { Text("Effectué par (optionnel)") },
                            leadingIcon   = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showReceiveDialog = false
                            isLoading = true
                            viewModel.receiveOrder(
                                id        = displayOrder.id,
                                userName  = receiveUserName.trim().ifEmpty { null },
                                onSuccess = {
                                    productViewModel.loadProducts()
                                    onReceived()
                                },
                                onError   = { isLoading = false }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) { Text("Confirmer") }
                },
                dismissButton = {
                    TextButton(onClick = { showReceiveDialog = false }) { Text("Annuler") }
                }
            )
        }
    }
}