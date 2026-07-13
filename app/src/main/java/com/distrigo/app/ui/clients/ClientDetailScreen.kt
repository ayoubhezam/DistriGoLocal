package com.distrigo.app.ui.clients

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Client
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClientDetailScreen(
    client    : Client,
    onBack    : () -> Unit,
    onEdit    : () -> Unit,
    onDelete  : () -> Unit,
    viewModel : ClientViewModel = viewModel()
) {
    val currentClient = viewModel.clients.collectAsState().value
        .find { it.id == client.id } ?: client

    val balanceStatus = when {
        currentClient.balance > 0    -> "due"
        currentClient.balance == 0.0 -> "settled"
        else                          -> "credit"
    }

    val typeColors = when (currentClient.customer_type) {
        "wholesale" -> DsColors.TagWholesale
        "business"  -> DsColors.TagBusiness
        else        -> DsColors.TagRetail
    }
    val typeLabel = when (currentClient.customer_type) {
        "wholesale" -> "Gros"
        "business"  -> "Société"
        else        -> "Détail"
    }
    val initials = currentClient.name.split(" ").take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

    var selectedTab       by remember { mutableStateOf(0) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var payAmount         by remember { mutableStateOf("") }
    var payNote           by remember { mutableStateOf("") }
    var payError          by remember { mutableStateOf("") }
    var longPressPayment  by remember { mutableStateOf<com.distrigo.app.data.model.ClientTransaction?>(null) }
    var showDeletePayment by remember { mutableStateOf(false) }
    var showEditPayment   by remember { mutableStateOf(false) }
    var editPaymentAmount by remember { mutableStateOf("") }
    var editError         by remember { mutableStateOf("") }
    val clientTransactions by viewModel.transactions.collectAsState()

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            viewModel.loadTransactions(currentClient.id)
        }
    }

    // ── Payment Dialog ──
    if (showPaymentDialog) {
        AlertDialog(
            onDismissRequest = {
                showPaymentDialog = false
                payAmount = ""; payNote = ""; payError = ""
            },
            title = { Text("Enregistrer un paiement", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.medium)
                            .background(DsColors.DangerLight)
                            .padding(DsSpacing.md)
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Solde restant", fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                            Text(
                                "${"%.2f".format(currentClient.balance)} DA",
                                fontSize   = DsTextSize.body,
                                fontWeight = FontWeight.Bold,
                                color      = DsColors.Danger
                            )
                        }
                    }

                    OutlinedTextField(
                        value           = payAmount,
                        onValueChange   = { payAmount = it; payError = "" },
                        label           = { Text("Montant (DA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier   = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError    = payError.isNotEmpty(),
                        shape      = DsShapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )

                    OutlinedTextField(
                        value         = payNote,
                        onValueChange = { payNote = it },
                        label         = { Text("Note (optionnel)") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        shape         = DsShapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1000.0, 2000.0, 5000.0).forEach { quick ->
                            OutlinedButton(
                                onClick        = { payAmount = quick.toInt().toString() },
                                modifier       = Modifier.weight(1f),
                                shape          = DsShapes.small,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("${quick.toInt()}", fontSize = DsTextSize.caption)
                            }
                        }
                        OutlinedButton(
                            onClick        = { payAmount = currentClient.balance.toString() },
                            modifier       = Modifier.weight(1.5f),
                            shape          = DsShapes.small,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("Tout régler", fontSize = DsTextSize.caption)
                        }
                    }

                    if (payError.isNotEmpty()) {
                        Text(payError, color = DsColors.Danger, fontSize = DsTextSize.caption)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = payAmount.toDoubleOrNull()
                        if (amount == null || amount <= 0) {
                            payError = "Montant invalide"
                            return@Button
                        }
                        viewModel.addPayment(
                            clientId  = currentClient.id,
                            amount    = amount,
                            note      = payNote.ifEmpty { null },
                            onSuccess = {
                                showPaymentDialog = false
                                payAmount = ""; payNote = ""
                            },
                            onError = { payError = it }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.Danger)
                ) {
                    Text("Confirmer", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPaymentDialog = false
                    payAmount = ""; payNote = ""; payError = ""
                }) {
                    Text("Annuler")
                }
            }
        )
    }

    // ── Long-press action dialog ──
    if (longPressPayment != null && !showDeletePayment && !showEditPayment) {
        val payment = longPressPayment!!
        AlertDialog(
            onDismissRequest = { longPressPayment = null },
            title = { Text("Options du paiement", fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = {
                    editPaymentAmount = (payment.amount ?: 0.0).toString()
                    showEditPayment = true
                }) {
                    Text("Modifier le montant", color = DsColors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePayment = true }) {
                    Text("Supprimer", color = DsColors.Danger)
                }
            }
        )
    }

    // ── Delete confirmation dialog ──
    if (longPressPayment != null && showDeletePayment) {
        val payment = longPressPayment!!
        AlertDialog(
            onDismissRequest = { showDeletePayment = false; longPressPayment = null },
            title = { Text("Supprimer le paiement", fontWeight = FontWeight.Bold) },
            text  = { Text("Êtes-vous sûr de vouloir supprimer ce paiement ?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePayment(
                            clientId  = currentClient.id,
                            paymentId = payment.id,
                            onSuccess = { showDeletePayment = false; longPressPayment = null },
                            onError   = {}
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.Danger)
                ) {
                    Text("Supprimer", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePayment = false; longPressPayment = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // ── Edit amount dialog ──
    if (longPressPayment != null && showEditPayment) {
        val payment = longPressPayment!!
        AlertDialog(
            onDismissRequest = { showEditPayment = false; longPressPayment = null; editError = "" },
            title = { Text("Modifier le paiement", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    OutlinedTextField(
                        value           = editPaymentAmount,
                        onValueChange   = { editPaymentAmount = it; editError = "" },
                        label           = { Text("Montant (DA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier        = Modifier.fillMaxWidth(),
                        singleLine      = true,
                        isError         = editError.isNotEmpty(),
                        shape           = DsShapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )
                    if (editError.isNotEmpty()) {
                        Text(editError, color = DsColors.Danger, fontSize = DsTextSize.caption)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = editPaymentAmount.toDoubleOrNull()
                        if (amount == null || amount <= 0) {
                            editError = "Montant invalide"
                            return@Button
                        }
                        viewModel.updatePayment(
                            clientId  = currentClient.id,
                            paymentId = payment.id,
                            amount    = amount,
                            onSuccess = { showEditPayment = false; longPressPayment = null; editError = "" },
                            onError   = { editError = it }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    Text("Confirmer", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPayment = false; longPressPayment = null; editError = "" }) {
                    Text("Annuler")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        // ── Header ──
        Row(
            modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
                }
                Spacer(Modifier.width(DsSpacing.xs))
                Text("Client", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                IconButton(
                    onClick  = onDelete,
                    modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.DangerLight)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = DsColors.Danger, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick  = onEdit,
                    modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                }
            }
        }

        LazyColumn(
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            // ── Identity card ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(DsColors.Surface)
                        .border(1.dp, DsColors.Border, DsShapes.large)
                        .padding(DsSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(DsShapes.pill)
                            .background(typeColors.second),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentClient.image_uri != null) {
                            val imageBytes = Base64.decode(currentClient.image_uri.substringAfter("base64,"), Base64.NO_WRAP)
                            val bitmap     = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            bitmap?.let {
                                Image(
                                    bitmap             = it.asImageBitmap(),
                                    contentDescription = null,
                                    modifier           = Modifier.fillMaxSize().clip(DsShapes.pill),
                                    contentScale       = ContentScale.Crop
                                )
                            }
                        } else {
                            Text(initials, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = typeColors.first)
                        }
                    }

                    Spacer(Modifier.width(DsSpacing.md))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(currentClient.name, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)

                        Spacer(Modifier.height(4.dp))

                        Box(
                            modifier = Modifier
                                .clip(DsShapes.pill)
                                .background(typeColors.second)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(typeLabel, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = typeColors.first)
                        }

                        if (!currentClient.phone.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(12.dp))
                                Text(currentClient.phone, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                            }
                        }

                        if (!currentClient.commune_name.isNullOrBlank() || !currentClient.wilaya_name.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            val location = listOfNotNull(
                                currentClient.commune_name?.takeIf { it.isNotBlank() },
                                currentClient.wilaya_name?.takeIf { it.isNotBlank() }
                            ).joinToString(", ")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(12.dp))
                                Text(location, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // ── Balance card ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.large)
                        .background(
                            when (balanceStatus) {
                                "due"     -> DsColors.DangerLight
                                "settled" -> DsColors.SuccessLight
                                else      -> DsColors.PrimaryLight
                            }
                        )
                        .padding(DsSpacing.lg)
                ) {
                    val statusColor = when (balanceStatus) {
                        "due"     -> DsColors.Danger
                        "settled" -> DsColors.Success
                        else      -> DsColors.Primary
                    }
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(DsShapes.pill)
                                .background(statusColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(
                                when (balanceStatus) {
                                    "due"     -> "Montant dû"
                                    "settled" -> "Réglé"
                                    else      -> "Avance"
                                },
                                fontSize   = DsTextSize.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color      = statusColor
                            )
                            Text(
                                "${"%.2f".format(kotlin.math.abs(currentClient.balance))} DA",
                                fontSize   = DsTextSize.headline,
                                fontWeight = FontWeight.ExtraBold,
                                color      = statusColor
                            )
                        }
                    }

                    if (balanceStatus == "due") {
                        Spacer(Modifier.height(DsSpacing.md))
                        Button(
                            onClick        = { showPaymentDialog = true },
                            modifier       = Modifier.fillMaxWidth(),
                            shape          = DsShapes.medium,
                            colors         = ButtonDefaults.buttonColors(containerColor = DsColors.Danger),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Payer", color = Color.White, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── Tabs ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.medium)
                        .background(DsColors.SurfaceSunken)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(
                        Pair("Infos", Icons.Default.Info),
                        Pair("Factures &\nPaiements", Icons.Default.Receipt)
                    ).forEachIndexed { index, (label, icon) ->
                        val active = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(DsShapes.small)
                                .background(if (active) DsColors.Primary else Color.Transparent)
                                .clickable(
                                    indication        = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { selectedTab = index }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint     = if (active) Color.White else DsColors.TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    label,
                                    fontSize   = DsTextSize.caption,
                                    fontWeight = FontWeight.Medium,
                                    color      = if (active) Color.White else DsColors.TextSecondary,
                                    textAlign  = TextAlign.Center,
                                    lineHeight = DsTextSize.caption
                                )
                            }
                        }
                    }
                }
            }

            // ── Tab 0: Infos ──
            if (selectedTab == 0) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.large)
                            .background(DsColors.Surface)
                            .border(1.dp, DsColors.Border, DsShapes.large)
                            .padding(DsSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
                    ) {
                        ClientInfoRow(icon = Icons.Default.Phone, label = "Téléphone", value = currentClient.phone?.takeIf { it.isNotBlank() } ?: "Non renseigné")
                        ClientInfoRow(icon = Icons.Default.LocationOn, label = "Wilaya", value = currentClient.wilaya_name?.takeIf { it.isNotBlank() } ?: "Non renseignée")
                        ClientInfoRow(icon = Icons.Default.Map, label = "Commune", value = currentClient.commune_name?.takeIf { it.isNotBlank() } ?: "Non renseignée")
                        ClientInfoRow(icon = Icons.Default.Home, label = "Adresse", value = currentClient.address?.takeIf { it.isNotBlank() } ?: "Non renseignée")
                        if (!currentClient.note.isNullOrBlank()) {
                            ClientInfoRow(icon = Icons.Default.Notes, label = "Note", value = currentClient.note)
                        }
                    }
                }
            }

            // ── Tab 1: Factures & Paiements ──
            if (selectedTab == 1) {
                item {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Historique", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary)
                        Button(
                            onClick        = { showPaymentDialog = true },
                            shape          = DsShapes.pill,
                            colors         = ButtonDefaults.buttonColors(containerColor = DsColors.Success),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Versement", fontSize = DsTextSize.caption, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }


                if (clientTransactions.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(DsSpacing.sm))
                                Text("Aucune transaction", fontSize = DsTextSize.body, color = DsColors.TextSecondary)
                            }
                        }
                    }
                } else {
                    val grouped = clientTransactions.groupBy { it.created_at.take(10) }
                    grouped.forEach { (date, dayTransactions) ->
                        item {
                            Text(
                                text       = formatOrderDate(date),
                                fontSize   = DsTextSize.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = DsColors.TextSecondary,
                                modifier   = Modifier.padding(vertical = DsSpacing.sm)
                            )
                        }
                        items(dayTransactions) { transaction ->
                            if (transaction.type == "vente") {
                                val montantPaye = transaction.montant_paye ?: 0.0
                                val total       = transaction.total ?: 0.0
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
                                        .padding(DsSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier         = Modifier.size(38.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PointOfSale, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(DsSpacing.md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Vente #${transaction.id} · ${formatOrderTime(transaction.created_at)}",
                                            fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary
                                        )
                                        if (statut == "Partiel") {
                                            Text("Payé: ${"%.2f".format(montantPaye)} DA", fontSize = DsTextSize.caption, color = DsColors.Warning)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${"%.2f".format(total)} DA", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Primary)
                                        Spacer(Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier.clip(DsShapes.pill).background(statusColorLight).padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(statut, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = statusColor)
                                        }
                                    }
                                }
                            } else if (transaction.type == "paiement") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(DsShapes.large)
                                        .background(DsColors.Surface)
                                        .border(1.dp, DsColors.Border, DsShapes.large)
                                        .combinedClickable(
                                            onClick     = {},
                                            onLongClick = { longPressPayment = transaction }
                                        )
                                        .padding(DsSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier         = Modifier.size(38.dp).clip(DsShapes.medium).background(DsColors.SuccessLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(DsSpacing.md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Paiement · ${formatOrderTime(transaction.created_at)}",
                                            fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary
                                        )
                                        transaction.note?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                        }
                                    }
                                    Text(
                                        "+${"%.2f".format(transaction.amount ?: 0.0)} DA",
                                        fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Success
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
        Box(
            modifier         = Modifier.size(32.dp).clip(DsShapes.small).background(DsColors.PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            Text(value, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
        }
    }
}
