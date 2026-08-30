package com.distrigo.app.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.distrigo.app.ui.designsystem.DsStepBadge
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.retours.RETOUR_FOURNISSEUR_MOTIFS
import com.distrigo.app.ui.retours.RetourCartItem
import com.distrigo.app.ui.retours.RetourFournisseurViewModel
import com.distrigo.app.ui.retours.RetourProductRow
import com.distrigo.app.ui.retours.RetourSummaryRow
import com.distrigo.app.ui.retours.formatRetourQty
import com.distrigo.app.ui.suppliers.SupplierViewModel
import java.time.Instant
import java.time.ZoneOffset

// Mirrors retourClientFormGraph: form state (date/motif/cart) lives on the SuppliersGraph-scoped
// RetourFournisseurViewModel instance — the same one SupplierDetailScreen/
// RetourFournisseurListScreen already share. See RetourFournisseurViewModel.formDate/formMotif/
// formCartItems.
//
// Simpler than retourClientFormGraph: there's no "Supplier" picker step at all — supplierId is
// always known upfront by both callers (SupplierDetailScreen's "Nouveau retour" button, and
// RetourFournisseurListScreen's own "+"), so the graph starts directly at Products, matching the
// original's lack of a client-selection step. supplierName isn't exposed by
// RetourFournisseurViewModel (unlike RetourClientViewModel, which has `clients`), so it's looked
// up via a separate supplierViewModel provider instead.
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.retourFournisseurFormGraph(
    navController     : NavHostController,
    graphRoute        : String,
    viewModel         : @Composable () -> RetourFournisseurViewModel,
    supplierViewModel : @Composable () -> SupplierViewModel,
    onBack  : () -> Unit,
    onSaved : () -> Unit
) {
    navigation(
        startDestination = Screen.SuppliersRetourFormProducts.route,
        route = graphRoute,
        arguments = listOf(navArgument("supplierId") { type = NavType.IntType })
    ) {
        composable(Screen.SuppliersRetourFormProducts.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val supplierViewModel = supplierViewModel()
            val supplierIdArg = parentEntry.arguments!!.getInt("supplierId")
            val suppliers by supplierViewModel.suppliers.collectAsState()
            val supplierName = suppliers.find { it.id == supplierIdArg }?.name ?: ""
            val returnableProducts by viewModel.returnableProducts.collectAsState()
            val isLoading by viewModel.isLoading.collectAsState()
            val cartItems by viewModel.formCartItems.collectAsState()
            val formDate by viewModel.formDate.collectAsState()
            val formMotif by viewModel.formMotif.collectAsState()
            var search by remember { mutableStateOf("") }
            var showDatePicker by remember { mutableStateOf(false) }
            var motifExpanded by remember { mutableStateOf(false) }
            var showScanner by remember { mutableStateOf(false) }

            var initialized by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!initialized) {
                    viewModel.resetRetourForm()
                    viewModel.loadReturnableProducts(supplierIdArg)
                    initialized = true
                }
            }

            if (showScanner) {
                BackHandler { showScanner = false }
                com.distrigo.app.ui.scanner.BarcodeScannerScreen(
                    onBarcodeScanned = { code -> search = code; showScanner = false },
                    onClose          = { showScanner = false }
                )
                return@composable
            }

            BackHandler { onBack() }

            val filteredProducts = returnableProducts.filter { rp ->
                val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                tokens.isEmpty() || tokens.all { token ->
                    rp.product.name.contains(token, ignoreCase = true) || (rp.product.barcode?.contains(token, ignoreCase = true) == true)
                }
            }

            Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
                DsTopAppBar(
                    title         = "Nouveau retour",
                    subtitle      = supplierName,
                    subtitleColor = DsColors.Primary,
                    leading       = DsTopBarLeading.Back(onBack)
                ) {
                    DsStepBadge("Produits", 1, 2)
                    Spacer(Modifier.width(DsSpacing.xs))
                }
                HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (!isLoading && returnableProducts.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = DsColors.Success.copy(alpha = 0.3f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("Aucun produit acheté auprès de ce fournisseur", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier            = Modifier.weight(1f),
                            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                        ) {
                            item(key = "date_motif") {
                                Column(
                                    modifier            = Modifier.padding(vertical = DsSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
                                ) {
                                    Column {
                                        Text("Date", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                                        Surface(shape = DsShapes.medium, color = DsColors.SurfaceSunken, modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }) {
                                            Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(DsSpacing.sm))
                                                    Text(formDate.toString(), fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
                                                }
                                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    Column {
                                        Text("Motif", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                                        Box {
                                            Surface(shape = DsShapes.medium, color = DsColors.SurfaceSunken, modifier = Modifier.fillMaxWidth().clickable { motifExpanded = true }) {
                                                Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Notes, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(DsSpacing.sm))
                                                        Text(
                                                            formMotif ?: "Sélectionner un motif",
                                                            fontSize = DsTextSize.body,
                                                            color    = if (formMotif != null) DsColors.TextPrimary else DsColors.TextTertiary
                                                        )
                                                    }
                                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            DropdownMenu(expanded = motifExpanded, onDismissRequest = { motifExpanded = false }) {
                                                RETOUR_FOURNISSEUR_MOTIFS.forEach { m ->
                                                    DropdownMenuItem(text = { Text(m) }, onClick = { viewModel.setFormMotif(m); motifExpanded = false })
                                                }
                                            }
                                        }
                                    }

                                    Text("Ajouter des produits", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                                }
                            }

                            stickyHeader(key = "search_header") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DsColors.Surface)
                                        .padding(vertical = DsSpacing.xs)
                                ) {
                                    OutlinedTextField(
                                        value         = search,
                                        onValueChange = { search = it },
                                        placeholder   = { Text("Rechercher par nom ou code-barres…", fontSize = DsTextSize.body) },
                                        leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                                        trailingIcon  = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (search.isNotEmpty()) {
                                                    IconButton(onClick = { search = "" }) {
                                                        Icon(Icons.Default.Close, contentDescription = "Effacer", tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                                IconButton(onClick = { showScanner = true }) {
                                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner un code-barres", tint = DsColors.Primary)
                                                }
                                            }
                                        },
                                        modifier   = Modifier.fillMaxWidth(),
                                        shape      = DsShapes.large,
                                        singleLine = true,
                                        colors     = dsTextFieldColors(
                                            unfocusedBorderColor = DsColors.Border,
                                            focusedBorderColor = DsColors.Primary
                                        )
                                    )
                                    Spacer(Modifier.height(DsSpacing.xs))
                                    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
                                }
                            }

                            items(filteredProducts, key = { it.product.id }) { rp ->
                                val cartItem = cartItems.find { it.product.id == rp.product.id }
                                RetourProductRow(
                                    product     = rp.product,
                                    quantity    = cartItem?.quantity,
                                    maxQuantity = rp.maxQuantity,
                                    onAdd       = { viewModel.setFormCartItems(cartItems + RetourCartItem(product = rp.product, quantity = 1.0, maxQuantity = rp.maxQuantity)) },
                                    onQuantityChange = { newQty ->
                                        viewModel.setFormCartItems(
                                            if (newQty <= 0) cartItems.filter { it.product.id != rp.product.id }
                                            else cartItems.map { if (it.product.id == rp.product.id) it.copy(quantity = newQty) else it }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(DsShapes.medium)
                                .background(if (cartItems.isNotEmpty()) DsColors.SuccessLight else DsColors.SurfaceSunken)
                                .clickable(enabled = cartItems.isNotEmpty()) { navController.navigate(Screen.SuppliersRetourFormCart.route) }
                                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(20.dp).clip(DsShapes.pill)
                                    .background(if (cartItems.isNotEmpty()) DsColors.Success else DsColors.TextTertiary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${cartItems.size}", color = Color.White, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(DsSpacing.sm))
                            Text(
                                "Voir la sélection", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold,
                                color = if (cartItems.isNotEmpty()) DsColors.Success else DsColors.TextTertiary
                            )
                            if (cartItems.isNotEmpty()) {
                                Spacer(Modifier.width(DsSpacing.xs))
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = formDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let {
                                viewModel.setFormDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                            }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annuler") } }
                ) { DatePicker(state = datePickerState) }
            }
        }

        composable(Screen.SuppliersRetourFormCart.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val cartItems by viewModel.formCartItems.collectAsState()
            val formMotif by viewModel.formMotif.collectAsState()
            val context = LocalContext.current

            BackHandler { navController.popBackStack() }

            Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                DsTopAppBar(
                    title    = "Ma sélection",
                    subtitle = "${cartItems.size} produit(s)",
                    leading  = DsTopBarLeading.Back({ navController.popBackStack() })
                )
                HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(cartItems, key = { it.product.id }) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface)
                                .border(1.dp, DsColors.Border, DsShapes.large).padding(DsSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.product.name, fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary, maxLines = 1)
                                Text("${"%.2f".format(item.product.purchase_price)} DA / ${item.product.unit_type}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                Text("Max : ${formatRetourQty(item.maxQuantity)}", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val newQty = item.quantity - 1
                                        viewModel.setFormCartItems(
                                            if (newQty <= 0) cartItems.filter { it.product.id != item.product.id }
                                            else cartItems.map { if (it.product.id == item.product.id) it.copy(quantity = newQty) else it }
                                        )
                                    },
                                    modifier = Modifier.size(32.dp).clip(DsShapes.pill).background(DsColors.SurfaceMuted)
                                ) { Icon(Icons.Default.Remove, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(15.dp)) }
                                Text(
                                    formatRetourQty(item.quantity), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold,
                                    color = DsColors.Success, modifier = Modifier.widthIn(min = 28.dp),
                                    textAlign = TextAlign.Center
                                )
                                val atMax = item.quantity >= item.maxQuantity
                                IconButton(
                                    onClick = {
                                        viewModel.setFormCartItems(cartItems.map {
                                            if (it.product.id == item.product.id) it.copy(quantity = (it.quantity + 1).coerceAtMost(it.maxQuantity)) else it
                                        })
                                    },
                                    enabled = !atMax,
                                    modifier = Modifier.size(32.dp).alpha(if (atMax) 0.4f else 1f).clip(DsShapes.pill).background(DsColors.SurfaceMuted)
                                ) { Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(15.dp)) }
                            }
                        }
                    }
                }

                // popUpTo(Products, inclusive=false) removes Cart from the back stack when
                // advancing, so back-from-Summary lands directly on Products — matching the
                // original monolith, where Cart was a modal overlay on step 1 rather than a real
                // intermediate step, and jumping to step 2 always bypassed it.
                Column(Modifier.fillMaxWidth().padding(DsSpacing.lg)) {
                    Button(
                        onClick = {
                            if (formMotif == null) {
                                Toast.makeText(context, "Veuillez sélectionner un motif", Toast.LENGTH_SHORT).show()
                            } else {
                                navController.navigate(Screen.SuppliersRetourFormSummary.route) {
                                    popUpTo(Screen.SuppliersRetourFormProducts.route) { inclusive = false }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = DsShapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = DsColors.Success)
                    ) { Text("Suivant →", color = Color.White, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        composable(Screen.SuppliersRetourFormSummary.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val supplierViewModel = supplierViewModel()
            val supplierIdArg = parentEntry.arguments!!.getInt("supplierId")
            val suppliers by supplierViewModel.suppliers.collectAsState()
            val supplierName = suppliers.find { it.id == supplierIdArg }?.name ?: ""
            val cartItems by viewModel.formCartItems.collectAsState()
            val formMotif by viewModel.formMotif.collectAsState()
            val formDate by viewModel.formDate.collectAsState()
            val context = LocalContext.current
            var isSaving by remember { mutableStateOf(false) }
            val totalQuantity = cartItems.sumOf { it.quantity }
            val totalValue = cartItems.sumOf { it.quantity * it.product.purchase_price }

            fun doSave() {
                isSaving = true
                val items = cartItems.map { ci -> mapOf("product_id" to ci.product.id, "quantity" to ci.quantity) }
                viewModel.createRetour(
                    supplierId = supplierIdArg,
                    date       = formDate.toString(),
                    motif      = formMotif,
                    note       = null,
                    items      = items,
                    onSuccess  = onSaved,
                    onError    = { msg -> isSaving = false; Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                )
            }

            BackHandler { navController.popBackStack() }

            Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
                DsTopAppBar(
                    title         = "Nouveau retour",
                    subtitle      = supplierName,
                    subtitleColor = DsColors.Primary,
                    leading       = DsTopBarLeading.Back({ navController.popBackStack() })
                ) {
                    DsStepBadge("Résumé", 2, 2)
                    Spacer(Modifier.width(DsSpacing.xs))
                }
                HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    LazyColumn(
                        modifier            = Modifier.weight(1f),
                        contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                    ) {
                        item { Text("Vérifiez les informations", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary) }

                        items(cartItems, key = { it.product.id }) { item ->
                            Row(
                                modifier              = Modifier.fillMaxWidth().padding(vertical = DsSpacing.xs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.product.name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary, maxLines = 1)
                                    Text("${formatRetourQty(item.quantity)} ${item.product.unit_type} × ${"%.2f".format(item.product.purchase_price)} DA", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                                }
                                Text("${"%.2f".format(item.quantity * item.product.purchase_price)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                            }
                            HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
                        }

                        item {
                            Spacer(Modifier.height(DsSpacing.sm))
                            Column(
                                modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.SuccessLight).padding(DsSpacing.lg)
                            ) {
                                Text("Résumé du retour", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Success)
                                Spacer(Modifier.height(DsSpacing.sm))
                                RetourSummaryRow("Motif", formMotif ?: "—")
                                RetourSummaryRow("Produits", "${cartItems.size}")
                                RetourSummaryRow("Cartons", formatRetourQty(totalQuantity))
                                RetourSummaryRow("Valeur totale (DA)", "${"%,.2f".format(totalValue)} DA", highlight = true)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                    ) {
                        OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.weight(1f).height(52.dp), shape = DsShapes.medium) {
                            Text("Modifier")
                        }
                        Button(
                            onClick  = { doSave() },
                            enabled  = !isSaving,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape    = DsShapes.medium,
                            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                        ) {
                            if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            else Text("Valider le retour", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
