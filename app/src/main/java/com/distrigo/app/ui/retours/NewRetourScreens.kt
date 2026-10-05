package com.distrigo.app.ui.retours

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.distrigo.app.data.model.Quantity
import com.distrigo.app.ui.common.CartStatusLine
import com.distrigo.app.ui.common.CartStatusTone
import com.distrigo.app.ui.common.DsCompactSearchAction
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.common.SelectionCartCard
import com.distrigo.app.ui.common.formatQty
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.inventory.InventoryDateField
import com.distrigo.app.ui.products.ProductCard
import com.distrigo.app.ui.scanner.BarcodeScannerScreen
import kotlinx.coroutines.launch

/**
 * A new return: only what can come back from this client or to this supplier, the Inventaire's way —
 * search and scanner, Produits' rows with the most that may be returned on the right. A tap, or a
 * scanned code (which skips the list), opens [RetourDialog]; once saved the product leaves the list for
 * the selection the bar at the bottom opens.
 */
@Composable
fun NewRetourListScreen(viewModel: NewRetourViewModel, onBack: () -> Unit, onOpenCart: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val eligible by viewModel.eligible.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val scope = rememberCoroutineScope()
    var showScanner by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ReturnableProduct?>(null) }
    var message by remember { mutableStateOf("") }

    if (showScanner) {
        BackHandler { showScanner = false }
        BarcodeScannerScreen(
            onBarcodeScanned = { code ->
                showScanner = false
                scope.launch {
                    val (match, error) = viewModel.byBarcode(code)
                    message = error ?: ""
                    if (match != null) editing = match
                }
            },
            onClose = { showScanner = false }
        )
        return
    }
    BackHandler(onBack = onBack)

    val all = eligible
    val shown = all?.let { viewModel.visible(it, cart, viewModel.search) }
    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = viewModel.title, subtitle = viewModel.partyName, leading = DsTopBarLeading.Back(onBack))
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

        DsCompactSearchField(
            value         = viewModel.search,
            onValueChange = { viewModel.search = it },
            placeholder   = "Rechercher un produit",
            modifier      = Modifier.padding(horizontal = DsSpacing.lg).padding(top = DsSpacing.md)
        ) {
            DsCompactSearchAction(
                icon = Icons.Default.QrCodeScanner, contentDescription = "Scanner un code-barres",
                tint = DsColors.Primary, onClick = { showScanner = true }
            )
        }
        Text(
            if (shown == null) "…" else "${shown.size} produit(s) retournable(s)",
            fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
            modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm)
        )
        if (message.isNotEmpty()) {
            Text(message, fontSize = DsTextSize.bodySmall, color = DsColors.Danger, modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                shown == null -> CircularProgressIndicator(color = DsColors.Primary, modifier = Modifier.align(Alignment.Center))
                shown.isEmpty() -> Text(
                    when {
                        all.isNullOrEmpty() -> "Aucun produit à retourner : rien n'a été livré par ici, ou tout a déjà été retourné."
                        viewModel.search.isNotBlank() -> "Aucun produit ne correspond."
                        else -> "Tous les produits retournables sont dans la sélection."
                    },
                    fontSize = DsTextSize.body, color = DsColors.TextSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(DsSpacing.xl)
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs, bottom = DsSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(shown, key = { it.product.id }) { r ->
                        ProductCard(
                            product = r.product, onClick = { message = ""; editing = r },
                            stock = r.maxQuantity, lowStock = false, stockPrefix = "max"
                        )
                    }
                }
            }
        }

        val tint = if (cart.isNotEmpty()) DsColors.Primary else DsColors.TextTertiary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.xl, vertical = DsSpacing.sm)
                .clip(DsShapes.large)
                .background(if (cart.isNotEmpty()) DsColors.PrimaryLight else DsColors.SurfaceSunken)
                .clickable(enabled = cart.isNotEmpty(), onClick = onOpenCart)
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            Box(Modifier.size(20.dp).clip(DsShapes.pill).background(tint), contentAlignment = Alignment.Center) {
                Text("${cart.size}", color = Color.White, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold)
            }
            Text("Ma sélection", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = tint)
            Spacer(Modifier.weight(1f))
            Text(money.da(viewModel.total(cart)), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = tint)
        }
    }

    editing?.let { r ->
        val existing = viewModel.inCart(r.product.id)
        RetourDialog(
            productName = r.product.name, unit = r.product.unit_type, max = r.maxQuantity,
            motifs = viewModel.motifs, initialMotif = existing?.motif, initialQty = existing?.quantity,
            onSave = { qty, motif -> viewModel.put(r, qty, motif); editing = null },
            onDismiss = { editing = null }
        )
    }
}

/**
 * A product's return, in a dialog centred on the screen: its motif, the most that may come back
 * (read-only), and the quantity. More than the maximum says so and "Enregistrer" stays off.
 */
@Composable
fun RetourDialog(
    productName : String,
    unit        : String,
    max         : Double,
    motifs      : List<String>,
    initialMotif: String?,
    initialQty  : Double?,
    onSave      : (quantity: Double, motif: String) -> Unit,
    onDismiss   : () -> Unit,
) {
    var motif by remember { mutableStateOf(initialMotif) }
    var text by remember { mutableStateOf(initialQty?.let { formatQty(it) } ?: "") }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (initialQty == null) {
            kotlinx.coroutines.delay(150)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }
    val quantity = Quantity.parse(text)
    val problem = if (text.isBlank()) null else retourQuantityError(quantity, max, unit)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface).padding(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.AssignmentReturn, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(DsSpacing.sm))
                Text(productName, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            }

            Box {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.medium)
                        .border(1.dp, DsColors.Border, DsShapes.medium)
                        .clickable(role = Role.DropdownList) { menu = true }
                        .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm)
                ) {
                    Text("Motif", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(motif ?: "Choisir un motif", fontSize = DsTextSize.body, fontWeight = FontWeight.Medium,
                            color = if (motif != null) DsColors.TextPrimary else DsColors.TextTertiary, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DsColors.TextSecondary)
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(DsColors.Surface)) {
                    motifs.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, fontWeight = if (m == motif) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (m == motif) DsColors.Primary else DsColors.TextPrimary) },
                            onClick = { motif = m; menu = false }
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.SurfaceMuted).padding(DsSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Maximum retournable", fontSize = DsTextSize.body, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
                Text("${formatQty(max)} $unit", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = Quantity.sanitizeInput(it, Quantity.allowsFractions(unit)) },
                label = { Text("Quantité retournée") },
                suffix = { Text(unit) },
                singleLine = true,
                isError = problem != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = DsShapes.medium,
                colors = dsTextFieldColors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            problem?.let { Text(it, fontSize = DsTextSize.bodySmall, color = DsColors.Danger) }

            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = DsShapes.medium) { Text("Annuler") }
                Button(
                    onClick = { val m = motif; if (m != null && quantity != null) onSave(quantity, m) },
                    enabled = motif != null && text.isNotBlank() && problem == null,
                    modifier = Modifier.weight(1f).height(48.dp), shape = DsShapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) { Text("Enregistrer", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** The return's selection, as the Inventaire's and the pertes' carts: a card per product, unfolding to change or remove it. */
@Composable
fun NewRetourCartScreen(viewModel: NewRetourViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val cart by viewModel.cart.collectAsState()
    var expandedId by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<RetourCartLine?>(null) }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = "Ma sélection", subtitle = "${cart.size} produit(s)", leading = DsTopBarLeading.Back(onBack))
        Spacer(Modifier.height(DsSpacing.sm))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (cart.isEmpty()) {
                Text("Aucun produit", color = DsColors.TextSecondary, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
                ) {
                    items(cart, key = { it.product.id }) { line ->
                        val expanded = expandedId == line.product.id
                        SelectionCartCard(
                            avatarIcon      = Icons.AutoMirrored.Filled.AssignmentReturn,
                            title           = line.product.name,
                            metaLine        = "${formatQty(line.quantity)} ${line.product.unit_type} · ${line.motif}",
                            totalPriceLabel = money.da(line.quantity * viewModel.priceOf(line.product)),
                            isExpanded      = expanded,
                            onToggleExpand  = { expandedId = if (expanded) null else line.product.id },
                            statusLine      = {
                                CartStatusLine(
                                    icon = Icons.Default.Inventory2,
                                    text = "Maximum retournable : ${formatQty(line.returnable.maxQuantity)} ${line.product.unit_type}",
                                    tone = CartStatusTone.NEUTRAL
                                )
                            },
                            expandedContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { viewModel.remove(line.product.id); expandedId = null }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Retirer", color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Button(onClick = { editing = line }, shape = DsShapes.medium,
                                        colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(DsSpacing.xs))
                                        Text("Modifier")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(52.dp), shape = DsShapes.medium) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text("Retour", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onNext, enabled = cart.isNotEmpty(),
                modifier = Modifier.weight(1f).height(52.dp), shape = DsShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
            ) { Text("Suivant →", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold) }
        }
    }

    editing?.let { line ->
        RetourDialog(
            productName = line.product.name, unit = line.product.unit_type, max = line.returnable.maxQuantity,
            motifs = viewModel.motifs, initialMotif = line.motif, initialQty = line.quantity,
            onSave = { qty, motif -> viewModel.put(line.returnable, qty, motif); editing = null; expandedId = null },
            onDismiss = { editing = null }
        )
    }
}

/** Résumé du retour: its date — "Aujourd'hui" unless another day is picked — its total and lines, and "Confirmer". */
@Composable
fun NewRetourSummaryScreen(viewModel: NewRetourViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val live by viewModel.cart.collectAsState()
    var confirmed by remember { mutableStateOf<List<RetourCartLine>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val lines = confirmed ?: live
    BackHandler { if (confirmed != null) onDone() else onBack() }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(
            title = "Résumé du retour", subtitle = viewModel.partyName,
            leading = DsTopBarLeading.Back { if (confirmed != null) onDone() else onBack() }
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            item { InventoryDateField(date = viewModel.date, enabled = confirmed == null, onDateChange = { viewModel.date = it }) }
            item {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    StatCard(Icons.Default.Inventory2, "${lines.size}", "Produits retournés", DsColors.Primary, Modifier.weight(1f))
                    StatCard(Icons.Default.Receipt, money.da(viewModel.total(lines)), "Total du retour", DsColors.Success, Modifier.weight(1f))
                }
            }
            if (confirmed != null) item {
                Surface(shape = DsShapes.medium, color = DsColors.SuccessLight, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DsColors.Success)
                        Spacer(Modifier.width(DsSpacing.sm))
                        Text("Retour enregistré · stock et solde mis à jour", fontSize = DsTextSize.bodySmall,
                            fontWeight = FontWeight.SemiBold, color = DsColors.Success)
                    }
                }
            }
            items(lines, key = { it.product.id }) { line ->
                Row(
                    Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.SurfaceMuted).padding(DsSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(line.product.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                        Text("${formatQty(line.quantity)} ${line.product.unit_type} · ${line.motif}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                    Text(money.da(line.quantity * viewModel.priceOf(line.product)), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                }
            }
            if (error.isNotEmpty()) item { Text(error, color = DsColors.Danger, fontSize = DsTextSize.bodySmall) }
        }
        Button(
            onClick = {
                if (confirmed != null) onDone() else {
                    saving = true; error = ""
                    val snapshot = live
                    viewModel.confirm(onSuccess = { saving = false; confirmed = snapshot }, onError = { saving = false; error = it })
                }
            },
            enabled = !saving && lines.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape = DsShapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            else Text(if (confirmed != null) "Terminer" else "Confirmer", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, value: String, label: String, color: Color, modifier: Modifier) {
    Surface(shape = DsShapes.medium, color = color.copy(alpha = 0.08f), modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(DsSpacing.md)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(DsSpacing.xs))
            FitText(value, fontSize = DsTextSize.title, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
    }
}
