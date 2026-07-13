package com.distrigo.app.ui.ventes

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
import com.distrigo.app.data.model.Vente
import com.distrigo.app.ui.products.*
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime
import com.distrigo.app.ui.purchases.UnifiedStatColumn
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.distrigo.app.ui.products.ProductViewModel
import com.distrigo.app.ui.clients.ClientViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import com.distrigo.app.ui.components.ReceiptPreviewSheet
import com.distrigo.app.ui.components.ShareOptionsSheet
import com.distrigo.app.ui.components.toReceiptData

@Composable
fun VentesScreen(
    viewModel         : VenteViewModel   = viewModel(),
    modifier          : Modifier         = Modifier,
    onFullScreenChange: (Boolean) -> Unit = {},
    productViewModel  : ProductViewModel = viewModel(),
    clientViewModel   : ClientViewModel  = viewModel()
){
    val ventes    by viewModel.ventes.collectAsState()
    val depotVentes = ventes.filter { it.source == "depot" }
    val isLoading by viewModel.isLoading.collectAsState()
    val error     by viewModel.error.collectAsState()

    var showNewVente     by remember { mutableStateOf(false) }
    var selectedVente    by remember { mutableStateOf<Vente?>(null) }
    var longPressVente   by remember { mutableStateOf<Vente?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editingVenteId   by remember { mutableStateOf<Int?>(null) }
    var deleteError      by remember { mutableStateOf("") }

    // ── New Vente Screen ──
    if (showNewVente) {
        onFullScreenChange(true)
        BackHandler {
            showNewVente = false
            onFullScreenChange(false)
        }
        VenteFormScreen(
            onBack  = {
                showNewVente = false
                onFullScreenChange(false)
            },
            onSaved = {
                showNewVente = false
                onFullScreenChange(false)
                viewModel.loadVentes()
            }
        )
        return
    }

    // ── Edit Vente Screen ──
    editingVenteId?.let { id ->
        val fullVenteState by viewModel.selectedVente.collectAsState()
        val fullVente = fullVenteState
        if (fullVente != null && fullVente.id == id) {
            onFullScreenChange(true)
            BackHandler {
                editingVenteId = null
                onFullScreenChange(false)
            }
            // NOTE: VenteFormScreen doesn't support an edit mode yet — wired up as
            // navigation structure only; will show the "new vente" form for now.
            VenteFormScreen(
                onBack  = {
                    editingVenteId = null
                    onFullScreenChange(false)
                },
                onSaved = {
                    editingVenteId = null
                    onFullScreenChange(false)
                    viewModel.loadVentes()
                }
            )
            return
        }
    }

    // ── Detail Screen ──
    selectedVente?.let { vente ->
        VenteDetailScreen(
            vente             = vente,
            onBack            = { selectedVente = null },
            viewModel         = viewModel,
            productViewModel  = productViewModel,
            clientViewModel   = clientViewModel,
            onDelivered       = { selectedVente = null; viewModel.loadVentes() },
            onDeleted         = { selectedVente = null; viewModel.loadVentes() }
        )
        return
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
                            Spacer(Modifier.height(8.dp))
                            Text(deleteError, fontSize = 13.sp, color = DestructiveRed)
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
                                productViewModel.loadProducts()
                                clientViewModel.loadClients()
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
                    TextButton(onClick = { showDeleteDialog = false; longPressVente = null; deleteError = "" }) {
                        Text("Annuler")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { longPressVente = null },
                title = { Text("Vente #${vente.id}") },
                confirmButton = {},
                dismissButton = {},
                icon = null,
                properties = androidx.compose.ui.window.DialogProperties(),
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BlueLight)
                                .clickable {
                                    longPressVente = null
                                    viewModel.loadVenteDetail(vente.id)
                                    editingVenteId = vente.id
                                }
                                .padding(14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                            Text("Modifier la vente", fontSize = 14.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(RedLight)
                                .clickable { showDeleteDialog = true }
                                .padding(14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = DestructiveRed, modifier = Modifier.size(20.dp))
                            Text("Supprimer la vente", fontSize = 14.sp, color = DestructiveRed, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ── Header ──
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Ventes", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Button(
                onClick = { showNewVente = true },
                shape   = RoundedCornerShape(12.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Nouveau bon", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Stats ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PrimaryBlue)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            UnifiedStatColumn(icon = Icons.Default.Receipt,     value = depotVentes.size.toString(), label = "Total bons")
            UnifiedStatColumn(icon = Icons.Default.Schedule,    value = depotVentes.count { it.status == "pending"   }.toString(), label = "En attente")
            UnifiedStatColumn(icon = Icons.Default.CheckCircle, value = depotVentes.count { it.status == "delivered" }.toString(), label = "Reçus")
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Historique des ventes",
            fontSize = 12.sp,
            color    = TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(8.dp))

        // ── Loading ──
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
            return
        }

        // ── Error ──
        error?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(it, color = DestructiveRed)
            }
            return
        }

        // ── Empty State ──
        if (depotVentes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PointOfSale, contentDescription = null,
                        tint = PrimaryBlue.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Aucune vente", color = TextMuted, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("Appuyez sur + pour créer", fontSize = 12.sp, color = TextMuted)
                }
            }
            return
        }

        val groupedVentes = depotVentes.groupBy { vente -> vente.created_at?.take(10) ?: "" }

        // ── List ──
        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier            = Modifier.weight(1f)
        ) {
            groupedVentes.forEach { (date, dayVentes) ->
                // ── Date Header ──
                item {
                    Text(
                        text       = formatOrderDate(date),
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = TextMuted,
                        modifier   = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(dayVentes) { vente ->
                    VenteCard(
                        vente       = vente,
                        onClick     = {
                            selectedVente = vente
                            viewModel.loadVenteDetail(vente.id)
                        },
                        onLongClick = {
                            longPressVente = vente
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BlueLight)
                .padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
            Text(
                "Appuyez sur une vente pour voir les détails complets et les articles.",
                fontSize = 12.sp,
                color    = PrimaryBlue
            )
        }
    }
}

@Composable
fun VenteDetailScreen(
    vente             : Vente,
    onBack            : () -> Unit,
    viewModel         : VenteViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    productViewModel  : com.distrigo.app.ui.products.ProductViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
    clientViewModel   : com.distrigo.app.ui.clients.ClientViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
    onDelivered       : () -> Unit = {},
    onDeleted         : () -> Unit = {}
) {
    BackHandler { onBack() }


    val fullVenteState by viewModel.selectedVente.collectAsState()
    val vente = fullVenteState?.takeIf { it.id == vente.id } ?: vente
    val isDelivered = vente.status == "delivered"
    var isDelivering by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf("") }
    val context = LocalContext.current
    var showReceiptPreview by remember { mutableStateOf(false) }
    var showShareOptions    by remember { mutableStateOf(false) }

    if (showReceiptPreview) {
        ReceiptPreviewSheet(
            receipt          = vente.toReceiptData(context),
            onDismiss        = { showReceiptPreview = false },
            onShareRequested = {
                showReceiptPreview = false
                showShareOptions    = true
            }
        )
    }

    if (showShareOptions) {
        ShareOptionsSheet(
            receipt   = vente.toReceiptData(context),
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
                        Spacer(Modifier.height(8.dp))
                        Text(deleteError, fontSize = 13.sp, color = DestructiveRed)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleting = true
                        viewModel.deleteVente(
                            id        = vente.id,
                            onSuccess = {
                                isDeleting = false
                                showDeleteDialog = false
                                viewModel.loadVentes()
                                productViewModel.loadProducts()
                                clientViewModel.loadClients()
                                onDeleted()
                            },
                            onError = { error -> isDeleting = false; deleteError = error }
                        )
                    },
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(color = DestructiveRed, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Supprimer", color = DestructiveRed, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; deleteError = "" }) {
                    Text("Annuler")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ── Header ──
        Row(
            modifier          = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Vente #${vente.id}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(vente.client_name, fontSize = 12.sp, color = TextMuted)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDelivered) GreenLight else Color(0xFFFFF3E0))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    if (isDelivered) "Livré" else "En attente",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isDelivered) AccentGreen else Color(0xFFE65100)
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick  = { showDeleteDialog = true },
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(RedLight)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = DestructiveRed, modifier = Modifier.size(18.dp))
            }
        }

        LazyColumn(
            modifier            = Modifier.weight(1f),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Articles (${vente.items?.size ?: vente.items_count ?: 0})",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextMuted,
                    modifier   = Modifier.padding(top = 4.dp)
                )
            }

            vente.items?.let { itemsList ->
                items(itemsList, key = { it.id }) { item ->
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp),
                        border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.product_name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
                                Text(
                                    "${item.quantity} ${item.unit_type} × ${"%.2f".format(item.unit_price)} DA",
                                    fontSize = 11.sp,
                                    color    = TextMuted
                                )
                            }
                            Text(
                                "${"%.2f".format(item.total_price)} DA",
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color      = PrimaryBlue
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BlueLight)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Total", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    Text("${"%.2f".format(vente.total)} DA", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryBlue)
                }
            }

            item {
                val montantPaye = vente.montant_paye ?: 0.0
                val reste       = vente.total - montantPaye
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MutedGray)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Montant payé", fontSize = 13.sp, color = TextPrimary)
                        Text("${"%.2f".format(montantPaye)} DA", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    if (reste > 0) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Reste", fontSize = 13.sp, color = DestructiveRed)
                            Text("${"%.2f".format(reste)} DA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DestructiveRed)
                        }
                    }
                }
            }

            vente.note?.takeIf { it.isNotBlank() }?.let { note ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MutedGray)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Notes, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        Text(note, fontSize = 13.sp, color = TextPrimary)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick  = { showReceiptPreview = true },
                modifier = Modifier.weight(1f).height(48.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Aperçu & Imprimer", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick  = { showShareOptions = true },
                modifier = Modifier.weight(1f).height(48.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Partager", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        if (!isDelivered) {
            Button(
                onClick = {
                    isDelivering = true
                    viewModel.deliverVente(
                        id        = vente.id,
                        onSuccess = {
                            isDelivering  = false
                            viewModel.loadVentes()
                            productViewModel.loadProducts()
                            clientViewModel.loadClients()
                            onDelivered()
                        },
                        onError = { isDelivering = false }
                    )
                },
                enabled  = !isDelivering,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(52.dp),
                shape  = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                if (isDelivering) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Marquer comme livré", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
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
                    .background(if (isDelivered) GreenLight else Color(0xFFFFF3E0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PointOfSale,
                    contentDescription = null,
                    tint     = if (isDelivered) AccentGreen else Color(0xFFE65100),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Ligne 1 : Vente # + heure
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "Vente #${vente.id}",
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color      = TextMuted
                    )
                    Text(
                        formatOrderTime(vente.created_at),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color      = TextMuted
                    )
                }
                Spacer(Modifier.height(2.dp))
                // Ligne 2 : nom du client + badge
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        vente.client_name,
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
                            .background(if (isDelivered) GreenLight else Color(0xFFFFF3E0))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (isDelivered) "Livré" else "En attente",
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (isDelivered) AccentGreen else Color(0xFFE65100)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                // Ligne 3 : montant + nombre d'articles
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "${"%.2f".format(vente.total)} DA",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color      = PrimaryBlue
                    )
                    Text(
                        "· ${vente.items_count ?: vente.items?.size ?: 0} article(s)",
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
