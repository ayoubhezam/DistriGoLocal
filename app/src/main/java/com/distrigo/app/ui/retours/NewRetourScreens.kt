package com.distrigo.app.ui.retours

import com.distrigo.app.ui.designsystem.LocalLicense

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.res.stringResource
import com.distrigo.app.ui.common.BuiltInNames
import com.distrigo.app.ui.common.asString
import com.distrigo.app.ui.common.quantityText
import com.distrigo.app.R
import com.distrigo.app.data.model.numberLabel
import androidx.compose.ui.res.pluralStringResource
import com.distrigo.app.ui.navigation.DrillSection
import com.distrigo.app.ui.navigation.LocalDrillDown

/**
 * A new return: only what can come back from this client or to this supplier, the Inventaire's way —
 * search and scanner, Produits' rows with the most that may be returned on the right. A tap, or a
 * scanned code (which skips the list), opens [RetourDialog]; what it saves goes to the selection the bar at the
 * bottom opens, and the list keeps the product with what is left of it (8, 2 selected: 6), until nothing is.
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
    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = stringResource(viewModel.title), subtitle = viewModel.subtitle, leading = DsTopBarLeading.Back(onBack))
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

        if (viewModel.refusedGoodsHint) RefusedGoodsHint()

        DsCompactSearchField(
            value         = viewModel.search,
            onValueChange = { viewModel.search = it },
            placeholder   = stringResource(R.string.common_search_product),
            modifier      = Modifier.padding(horizontal = DsSpacing.lg).padding(top = DsSpacing.md)
        ) {
            DsCompactSearchAction(
                icon = Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.common_cd_scan_barcode),
                tint = DsColors.PrimaryDark, onClick = { showScanner = true }
            )
        }
        Text(
            if (shown == null) "…" else pluralStringResource(R.plurals.retours_returnable_n, shown.size, shown.size),
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
                        all.isNullOrEmpty() -> stringResource(R.string.retours_none_to_return)
                        viewModel.search.isNotBlank() -> stringResource(R.string.pertes_no_product_match)
                        else -> stringResource(R.string.retours_all_in_selection)
                    },
                    fontSize = DsTextSize.body, color = DsColors.TextSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(DsSpacing.xl)
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs, bottom = DsSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(shown, key = { it.lineId }) { r ->
                        ProductCard(
                            // A client's line shows the price it was sold at, which is what it is credited at (FIN-02)
                            product = if (viewModel.pricedBySale) r.product.copy(selling_price = r.unitPrice) else r.product,
                            onClick = { message = ""; editing = r },
                            stock = r.maxQuantity, lowStock = false, stockPrefix = stringResource(R.string.retours_max_prefix)
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
                .background(if (cart.isNotEmpty()) DsColors.PrimaryLight else DsColors.Surface)
                .border(1.dp, if (cart.isNotEmpty()) Color.Transparent else DsColors.Border, DsShapes.large)
                .clickable(enabled = cart.isNotEmpty(), onClick = onOpenCart)
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            Box(Modifier.size(20.dp).clip(DsShapes.pill).background(tint), contentAlignment = Alignment.Center) {
                Text("${cart.size}", color = Color.White, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.common_my_selection), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = tint)
            Spacer(Modifier.weight(1f))
            Text(money.da(viewModel.total(cart)), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = tint)
        }
    }

    // [r] carries what is left of it as its maximum; a line already selected is changed from the selection
    editing?.let { r ->
        RetourDialog(
            productName = r.product.name, unit = r.product.unit_type, max = r.maxQuantity,
            motifs = viewModel.motifs, initialMotif = null, initialQty = null,
            onSave = { qty, motif -> viewModel.add(r.lineId, qty, motif); editing = null },
            onDismiss = { editing = null }
        )
    }
}

/**
 * Above a supplier's list (FIN-03): goods the supplier refused are not a return but a perte. A tap opens a new perte on
 * top of the return, which Back, or saving the perte, comes back to.
 */
@Composable
private fun RefusedGoodsHint() {
    val drill = LocalDrillDown.current
    val license = LocalLicense.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DsSpacing.lg)
            .padding(top = DsSpacing.md)
            .clip(DsShapes.medium)
            .background(DsColors.PrimaryLight)
            .clickable(role = Role.Button, onClick = license.guarded { drill.open(DrillSection.NEW_PERTE) })
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(DsSpacing.sm))
        Text(
            stringResource(R.string.retours_supplier_refused_hint),
            fontSize = DsTextSize.caption, color = DsColors.PrimaryDark, modifier = Modifier.weight(1f)
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
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
                    Text(stringResource(R.string.pertes_motif), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(motif?.let(BuiltInNames::motif) ?: stringResource(R.string.retours_choose_motif), fontSize = DsTextSize.body, fontWeight = FontWeight.Medium,
                            color = if (motif != null) DsColors.TextPrimary else DsColors.TextTertiary, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DsColors.TextSecondary)
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(DsColors.Surface)) {
                    motifs.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(BuiltInNames.motif(m), fontWeight = if (m == motif) FontWeight.SemiBold else FontWeight.Normal,
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
                Text(stringResource(R.string.retours_max), fontSize = DsTextSize.body, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
                Text(quantityText(max, unit).asString(), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = Quantity.sanitizeInput(it, Quantity.allowsFractions(unit)) },
                label = { Text(stringResource(R.string.retours_qty_returned)) },
                suffix = { Text(com.distrigo.app.ui.common.unitWordText(1.0, unit).asString()) },
                singleLine = true,
                isError = problem != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = DsShapes.medium,
                colors = dsTextFieldColors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            problem?.let { Text(it, fontSize = DsTextSize.bodySmall, color = DsColors.Danger) }

            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = DsShapes.medium) { Text(stringResource(R.string.common_action_cancel)) }
                Button(
                    onClick = { val m = motif; if (m != null && quantity != null) onSave(quantity, m) },
                    enabled = motif != null && text.isNotBlank() && problem == null,
                    modifier = Modifier.weight(1f).height(48.dp), shape = DsShapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
                ) { Text(stringResource(R.string.common_action_save), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** The return's selection, as the Inventaire's and the pertes' carts: a card per product, unfolding to change or remove it. */
@Composable
fun NewRetourCartScreen(viewModel: NewRetourViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val cart by viewModel.cart.collectAsState()
    var expandedId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<RetourCartLine?>(null) }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = stringResource(R.string.common_my_selection), subtitle = cart.distinctBy { it.returnable.lineId }.size.let { pluralStringResource(R.plurals.common_products_count, it, it) }, leading = DsTopBarLeading.Back(onBack))
        Spacer(Modifier.height(DsSpacing.sm))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (cart.isEmpty()) {
                Text(stringResource(R.string.pertes_no_product), color = DsColors.TextSecondary, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
                ) {
                    items(cart, key = { it.key }) { line ->
                        val expanded = expandedId == line.key
                        SelectionCartCard(
                            avatarIcon      = Icons.AutoMirrored.Filled.AssignmentReturn,
                            title           = line.product.name,
                            metaLine        = quantityText(line.quantity, line.product.unit_type).asString() + " · " + BuiltInNames.motif(line.motif),
                            totalPriceLabel = money.da(line.total),
                            isExpanded      = expanded,
                            onToggleExpand  = { expandedId = if (expanded) null else line.key },
                            statusLine      = {
                                CartStatusLine(
                                    icon = Icons.Default.Inventory2,
                                    text = stringResource(R.string.retours_max_value, quantityText(line.returnable.maxQuantity, line.product.unit_type).asString()),
                                    tone = CartStatusTone.NEUTRAL
                                )
                            },
                            expandedContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { viewModel.remove(line); expandedId = null }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.common_action_remove), color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Button(onClick = { editing = line }, shape = DsShapes.medium,
                                        colors = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(DsSpacing.xs))
                                        Text(stringResource(R.string.common_action_edit))
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
                Text(stringResource(R.string.common_action_back), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onNext, enabled = cart.isNotEmpty(),
                modifier = Modifier.weight(1f).height(52.dp), shape = DsShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
            ) { Text(stringResource(R.string.common_action_next), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold) }
        }
    }

    editing?.let { line ->
        RetourDialog(
            // At most this line and what no other line of the product takes
            productName = line.product.name, unit = line.product.unit_type, max = viewModel.left(line.returnable, cart, except = line),
            motifs = viewModel.motifs, initialMotif = line.motif, initialQty = line.quantity,
            onSave = { qty, motif -> viewModel.change(line, qty, motif); editing = null; expandedId = null },
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
    // A blocked write explains itself instead (docs/license_l5_enforcement.md §2)
    val license = LocalLicense.current
    var error by remember { mutableStateOf("") }
    val lines = confirmed ?: live
    BackHandler { if (confirmed != null) onDone() else onBack() }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title = stringResource(R.string.retours_summary_title), subtitle = viewModel.subtitle,
            leading = DsTopBarLeading.Back { if (confirmed != null) onDone() else onBack() }
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            item { InventoryDateField(date = viewModel.date, enabled = confirmed == null, onDateChange = { viewModel.date = it }) }
            viewModel.location?.let { where ->
                item { RetourLocationField(location = where, enabled = confirmed == null, onChange = { viewModel.location = it }) }
            }
            item {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    StatCard(Icons.Default.Inventory2, "${lines.size}", stringResource(R.string.retours_stat_products), DsColors.Primary, Modifier.weight(1f))
                    StatCard(Icons.Default.Receipt, money.da(viewModel.total(lines)), stringResource(R.string.retours_stat_total), DsColors.Success, Modifier.weight(1f))
                }
            }
            if (confirmed != null) item {
                Surface(shape = DsShapes.medium, color = DsColors.SuccessLight, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DsColors.Success)
                        Spacer(Modifier.width(DsSpacing.sm))
                        Text(stringResource(R.string.retours_saved), fontSize = DsTextSize.bodySmall,
                            fontWeight = FontWeight.SemiBold, color = DsColors.Success)
                    }
                }
            }
            items(lines, key = { it.key }) { line ->
                Row(
                    Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.medium).padding(DsSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(line.product.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                        Text(quantityText(line.quantity, line.product.unit_type).asString() + " · " + BuiltInNames.motif(line.motif), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                    Text(money.da(line.total), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark)
                }
            }
            if (error.isNotEmpty()) item { Text(error, color = DsColors.Danger, fontSize = DsTextSize.bodySmall) }
        }
        Button(
            onClick = {
                if (confirmed != null) onDone() else if (!license.refuses()) {
                    saving = true; error = ""
                    val snapshot = live
                    viewModel.confirm(onSuccess = { saving = false; confirmed = snapshot }, onError = { saving = false; error = it })
                }
            },
            enabled = !saving && lines.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape = DsShapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
        ) {
            if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            else Text(stringResource(if (confirmed != null) R.string.retours_finish else R.string.common_action_confirm), fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Where a client's return puts the goods back: the dépôt or the camion (STK-02). Starts on the sale's source; the
 * user may pick the other, as when goods sold from the truck are brought back to the warehouse.
 */
@Composable
private fun RetourLocationField(location: String, enabled: Boolean, onChange: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.medium).padding(DsSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
    ) {
        Text(stringResource(R.string.retours_location), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            listOf("depot" to R.string.common_source_depot, "camion" to R.string.common_source_truck).forEach { (value, label) ->
                val selected = value == location
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(DsShapes.medium)
                        .background(if (selected) DsColors.PrimaryLight else DsColors.Surface)
                        .border(1.dp, if (selected) DsColors.Primary else DsColors.Border, DsShapes.medium)
                        .clickable(enabled = enabled && !selected, role = Role.RadioButton) { onChange(value) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(label), fontSize = DsTextSize.body,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) DsColors.Primary else DsColors.TextPrimary
                    )
                }
            }
        }
        Text(stringResource(R.string.retours_location_hint), fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
    }
}

/**
 * The first step of a client's return: the sale it is made from (FIN-02). The client's sales with something left to
 * return, newest first — any status (UX-01) — each with its number, when it was made, from where, its total and how
 * many of its products can still come back.
 */
@Composable
fun NewRetourSaleScreen(viewModel: NewRetourClientViewModel, onBack: () -> Unit, onPicked: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val sales by viewModel.sales.collectAsState()
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = stringResource(R.string.retours_choose_sale), subtitle = viewModel.partyName, leading = DsTopBarLeading.Back(onBack))
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)
        val list = sales
        if (list != null && list.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.retours_sales_n, list.size, list.size),
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
                modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm)
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                list == null -> CircularProgressIndicator(color = DsColors.Primary, modifier = Modifier.align(Alignment.Center))
                list.isEmpty() -> Text(
                    stringResource(R.string.retours_no_sale), fontSize = DsTextSize.body, color = DsColors.TextSecondary,
                    textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(DsSpacing.xl)
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs, bottom = DsSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(list, key = { it.id }) { sale ->
                        val selected = viewModel.sale?.id == sale.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.large)
                                .background(DsColors.Surface)
                                .border(1.dp, if (selected) DsColors.Primary else DsColors.Border, DsShapes.large)
                                .clickable { viewModel.pickSale(sale); onPicked() }
                                .padding(DsSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(DsSpacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(numberLabel(sale.numero, sale.id), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                                Text(
                                    listOf(
                                        saleMoment(sale.created_at),
                                        stringResource(if (sale.source == "camion") R.string.common_source_truck else R.string.common_source_depot)
                                    ).filter { it.isNotEmpty() }.joinToString(" · "),
                                    fontSize = DsTextSize.caption, color = DsColors.TextSecondary
                                )
                                Text(
                                    pluralStringResource(R.plurals.retours_sale_lines_n, sale.lines, sale.lines),
                                    fontSize = DsTextSize.caption, color = DsColors.TextTertiary
                                )
                            }
                            Spacer(Modifier.width(DsSpacing.sm))
                            Text(money.da(sale.total), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark)
                        }
                    }
                }
            }
        }
    }
}

/** « 6 nov. 2026 · 21:20 », in the app's language: when a sale was made, from its stored instant. */
private fun saleMoment(createdAt: String): String =
    runCatching {
        val at = java.time.Instant.parse(createdAt).atZone(java.time.ZoneId.systemDefault())
        at.format(com.distrigo.app.core.format.AppDates.formatter("d MMM yyyy")) + " · " +
            at.format(com.distrigo.app.core.format.AppDates.formatter("HH:mm"))
    }.getOrDefault(createdAt.take(10))

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
