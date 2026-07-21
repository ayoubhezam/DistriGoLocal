package com.distrigo.app.ui.suppliers

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.data.model.SupplierTransaction
import com.distrigo.app.ui.products.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

private fun formatQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SupplierDetailScreen(
    supplier          : Supplier,
    onBack            : () -> Unit,
    onEdit            : () -> Unit,
    onDelete          : () -> Unit,
    viewModel         : SupplierViewModel = viewModel(),
    onNavigateToOrder : (Int) -> Unit = {}
) {
    val supplierProducts by viewModel.supplierProducts.collectAsState()
    val transactions     by viewModel.transactions.collectAsState()

    val currentSupplier = viewModel.suppliers.collectAsState().value
        .find { it.id == supplier.id } ?: supplier
    val balanceStatus = when {
        currentSupplier.balance > 0  -> "due"
        currentSupplier.balance == 0.0 -> "settled"
        else                         -> "credit"
    }
    val colors   = listOf(0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A, 0xFFC62828, 0xFFE65100, 0xFF00695C)
    val color    = Color(colors[currentSupplier.name[0].code % colors.size])
    val initials = currentSupplier.name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
    var selectedTab       by remember { mutableStateOf(0) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var payAmount         by remember { mutableStateOf("") }
    var payNote           by remember { mutableStateOf("") }
    var payError          by remember { mutableStateOf("") }
    var longPressPayment    by remember { mutableStateOf<SupplierTransaction?>(null) }
    var showDeletePayment   by remember { mutableStateOf(false) }
    var showEditPayment     by remember { mutableStateOf(false) }
    var editPaymentAmount   by remember { mutableStateOf("") }
    var editError           by remember { mutableStateOf("") }

    LaunchedEffect(supplier.id) {
        viewModel.loadSupplierProducts(supplier.id)
        viewModel.loadTransactions(supplier.id)
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) viewModel.loadTransactions(currentSupplier.id)
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Solde restant banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RedLight)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Solde restant", fontSize = 13.sp, color = DestructiveRed)
                            Text(
                                "${formatDZD(currentSupplier.balance)} DA",
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color      = DestructiveRed
                            )
                        }
                    }

                    OutlinedTextField(
                        value         = payAmount,
                        onValueChange = { payAmount = it; payError = "" },
                        label         = { Text("Montant (DA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        isError       = payError.isNotEmpty()
                    )

                    OutlinedTextField(
                        value         = payNote,
                        onValueChange = { payNote = it },
                        label         = { Text("Note (optionnel)") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )

                    // Quick amount buttons
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1000.0, 2000.0, 5000.0).forEach { quick ->
                            OutlinedButton(
                                onClick          = { payAmount = quick.toInt().toString() },
                                modifier         = Modifier.weight(1f),
                                shape            = RoundedCornerShape(8.dp),
                                contentPadding   = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("${quick.toInt()}", fontSize = 11.sp)
                            }
                        }
                        OutlinedButton(
                            onClick          = { payAmount = currentSupplier.balance.toString() },
                            modifier         = Modifier.weight(1.5f),
                            shape            = RoundedCornerShape(8.dp),
                            contentPadding   = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("Tout régler", fontSize = 11.sp)
                        }
                    }

                    if (payError.isNotEmpty()) {
                        Text(payError, color = DestructiveRed, fontSize = 12.sp)
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
                            supplierId = currentSupplier.id,
                            amount     = amount,
                            note       = payNote.ifEmpty { null },
                            onSuccess = {
                                showPaymentDialog = false
                                payAmount = ""; payNote = ""
                                viewModel.loadSuppliers()
                                viewModel.loadTransactions(currentSupplier.id)
                            },
                            onError = { payError = it }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DestructiveRed)
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
                    Text("Modifier le montant", color = PrimaryBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePayment = true }) {
                    Text("Supprimer", color = DestructiveRed)
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
                            supplierId = currentSupplier.id,
                            paymentId  = payment.id,
                            onSuccess  = { showDeletePayment = false; longPressPayment = null },
                            onError    = {}
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DestructiveRed)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value           = editPaymentAmount,
                        onValueChange   = { editPaymentAmount = it; editError = "" },
                        label           = { Text("Montant (DA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier        = Modifier.fillMaxWidth(),
                        singleLine      = true,
                        isError         = editError.isNotEmpty()
                    )
                    if (editError.isNotEmpty()) {
                        Text(editError, color = DestructiveRed, fontSize = 12.sp)
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
                            supplierId = currentSupplier.id,
                            paymentId  = payment.id,
                            amount     = amount,
                            onSuccess  = { showEditPayment = false; longPressPayment = null; editError = "" },
                            onError    = { editError = it }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
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
            .background(Color.White)
    ) {
        // ── Header ──
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                }
                Spacer(Modifier.width(4.dp))
                Text("Fournisseur", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick  = onDelete,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(RedLight)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = DestructiveRed, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick  = onEdit,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(BlueLight)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                }
            }
        }

        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Identity Card ──
            item {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier         = Modifier.size(52.dp).clip(RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.13f)))
                            Text(initials, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentSupplier.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                                Text(formatPhone(currentSupplier.phone ?: ""), fontSize = 13.sp, color = TextMuted)                            }
                            if (!currentSupplier.address.isNullOrEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                                    Text(currentSupplier.address!!, fontSize = 12.sp, color = TextMuted, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

// ── Balance Card ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when (balanceStatus) {
                                "due"     -> RedLight
                                "settled" -> GreenLight
                                else      -> BlueLight
                            }
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    when (balanceStatus) {
                                        "due"     -> Color(0xFFFFCDD2)
                                        "settled" -> Color(0xFFA5D6A7)
                                        else      -> Color(0xFFBBDEFB)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = when (balanceStatus) {
                                    "due"     -> DestructiveRed
                                    "settled" -> AccentGreen
                                    else      -> PrimaryBlue
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                when (balanceStatus) {
                                    "due"     -> "Montant dû"
                                    "settled" -> "Réglé"
                                    else      -> "Avance"
                                },
                                color = when (balanceStatus) {
                                    "due"     -> DestructiveRed
                                    "settled" -> AccentGreen
                                    else      -> PrimaryBlue
                                },
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${"%.2f".format(kotlin.math.abs(currentSupplier.balance))} DA",
                                fontSize   = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines   = 1,
                                overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color      = when (balanceStatus) {
                                    "due"     -> DestructiveRed
                                    "settled" -> AccentGreen
                                    else      -> PrimaryBlue
                                }
                            )
                        }
                    }
                    if (balanceStatus == "due") {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick        = { showPaymentDialog = true },
                            modifier       = Modifier.fillMaxWidth(),
                            shape          = RoundedCornerShape(10.dp),
                            colors         = ButtonDefaults.buttonColors(containerColor = DestructiveRed),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Payer", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── Tabs ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MutedGray)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(
                        Pair("Infos",               Icons.Default.Info),
                        Pair("Produits",             Icons.Default.ShoppingCart),
                        Pair("Factures &\nPaiements", Icons.Default.Receipt)
                    ).forEachIndexed { index, (label, icon) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selectedTab == index) PrimaryBlue else Color.Transparent)
                                .clickable(
                                    indication       = null,
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                ) { selectedTab = index }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint     = if (selectedTab == index) Color.White else TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    label,
                                    fontSize   = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color      = if (selectedTab == index) Color.White else TextMuted,
                                    textAlign  = TextAlign.Center,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // ── Tab 0: Infos ──
            if (selectedTab == 0) {
                item {
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(16.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp),
                        border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoRow(icon = Icons.Default.Phone,      label = "Téléphone", value = currentSupplier.phone?:"")
                            InfoRow(icon = Icons.Default.LocationOn, label = "Adresse",   value = currentSupplier.address ?: "Non renseignée")
                            if (!currentSupplier.note.isNullOrEmpty()) {
                                InfoRow(icon = Icons.Default.Notes, label = "Note", value = currentSupplier.note!!)
                            }
                        }
                    }
                }

                // Stats row 1: Produits liés + Solde
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            label    = "Produits liés",
                            value    = supplierProducts.size.toString(),
                            color    = PrimaryBlue
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            label    = "Solde",
                            value    = "${formatDZD(currentSupplier.balance)} DA",
                            color = when (balanceStatus) {
                                "due"     -> DestructiveRed
                                "settled" -> AccentGreen
                                else      -> PrimaryBlue
                            }                        )
                    }
                }

                // Stats row 2: Payé + Partiel + Impayé
                item {
                    val factures     = transactions.filter { it.type == "facture" }
                    val payeCount    = factures.count { (it.montant_paye ?: 0.0) >= (it.amount ?: 0.0) && (it.amount ?: 0.0) > 0 }
                    val partialCount = factures.count { (it.montant_paye ?: 0.0) > 0.0 && (it.montant_paye ?: 0.0) < (it.amount ?: 0.0) }
                    val unpaidCount  = factures.count { (it.montant_paye ?: 0.0) == 0.0 }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(modifier = Modifier.weight(1f), label = "Payé",    value = payeCount.toString(),    color = AccentGreen)
                        StatCard(modifier = Modifier.weight(1f), label = "Partiel", value = partialCount.toString(), color = Color(0xFFE65100))
                        StatCard(modifier = Modifier.weight(1f), label = "Impayé",  value = unpaidCount.toString(),  color = DestructiveRed)
                    }
                }
            }

            // ── Tab 1: Produits ──
            if (selectedTab == 1) {
                if (supplierProducts.isEmpty()) {
                    item {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null,
                                    tint = PrimaryBlue.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Aucun produit lié à ce fournisseur", color = TextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    items(supplierProducts) { product ->
                        Card(
                            modifier  = Modifier.fillMaxWidth(),
                            shape     = RoundedCornerShape(14.dp),
                            colors    = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(1.dp),
                            border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier         = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(BlueLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null,
                                        tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(product.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text("Prix achat : ${"%.2f".format(product.purchase_price)} DA",
                                        fontSize = 11.sp, color = TextMuted)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${formatQty(product.stock)} ${product.unit_type}",
                                        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PrimaryBlue)
                                    if (product.is_default == 1) {
                                        Text("Principal", fontSize = 10.sp, color = AccentGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Tab 2: Factures & Paiements ──
            if (selectedTab == 2) {
                item {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            "Historique",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = TextMuted
                        )
                        Button(
                            onClick        = { showPaymentDialog = true },
                            shape          = RoundedCornerShape(20.dp),
                            colors         = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Versement", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (transactions.isEmpty()) {
                    item {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Receipt, contentDescription = null,
                                    tint = PrimaryBlue.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Aucune transaction", color = TextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    val grouped = transactions.groupBy { it.created_at.take(10) }
                    grouped.forEach { (date, dayTransactions) ->
                        item {
                            Text(
                                text       = formatOrderDate(date),
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = TextMuted,
                                modifier   = Modifier.padding(vertical = 6.dp)
                            )
                        }
                        items(dayTransactions) { transaction ->
                            if (transaction.type == "facture") {
                                val montantPaye = transaction.montant_paye ?: 0.0
                                val total       = transaction.amount ?: 0.0
                                val statut = when {
                                    montantPaye >= total && total > 0 -> "Payé"
                                    montantPaye > 0 && montantPaye < total -> "Partiel"
                                    else -> "Impayé"
                                }
                                Card(
                                    modifier  = Modifier.fillMaxWidth().clickable { onNavigateToOrder(transaction.id) },
                                    shape     = RoundedCornerShape(14.dp),
                                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(1.dp),
                                    border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier         = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(BlueLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.ShoppingCart, contentDescription = null,
                                                tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Bon #${transaction.id} · ${formatOrderTime(transaction.created_at)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                            Text("${"%.2f".format(total)} DA", fontSize = 12.sp, color = TextMuted)
                                            if (statut == "Partiel") {
                                                Text("Payé: ${"%.2f".format(montantPaye)} DA", fontSize = 11.sp, color = Color(0xFFE65100))
                                            }
                                        }
                                        val statusBg = when (statut) {
                                            "Payé"    -> GreenLight
                                            "Partiel" -> Color(0xFFFFF3E0)
                                            else      -> RedLight
                                        }
                                        val statusColor = when (statut) {
                                            "Payé"    -> AccentGreen
                                            "Partiel" -> Color(0xFFE65100)
                                            else      -> DestructiveRed
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(statusBg)
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(statut, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
                                        }
                                    }
                                }
                            } else if (transaction.type == "paiement") {
                                Card(
                                    modifier  = Modifier.fillMaxWidth().combinedClickable(
                                        onClick     = {},
                                        onLongClick = { longPressPayment = transaction }
                                    ),
                                    shape     = RoundedCornerShape(14.dp),
                                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(1.dp),
                                    border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier         = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(GreenLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null,
                                                tint = AccentGreen, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Paiement · ${formatOrderTime(transaction.created_at)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                            if (!transaction.note.isNullOrEmpty()) {
                                                Text(transaction.note.orEmpty(), fontSize = 11.sp, color = TextMuted)
                                            }
                                        }
                                        Text(
                                            "+${"%.2f".format(transaction.amount ?: 0.0)} DA",
                                            fontSize   = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color      = AccentGreen
                                        )
                                    }
                                }
                            } else if (transaction.type == "solde_initial") {
                                Card(
                                    modifier  = Modifier.fillMaxWidth(),
                                    shape     = RoundedCornerShape(14.dp),
                                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(1.dp),
                                    border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier         = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF0F0F0)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.AccountBalance, contentDescription = null,
                                                tint = TextMuted, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Solde initial", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                            Text(formatOrderTime(transaction.created_at), fontSize = 11.sp, color = TextMuted)
                                        }
                                        Text(
                                            "${formatDZD(transaction.amount ?: 0.0)} DA",
                                            fontSize   = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color      = TextPrimary
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
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier         = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(BlueLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(label, fontSize = 11.sp, color = TextMuted)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, label: String, value: String, color: Color = TextPrimary) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}
