package com.distrigo.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.distrigo.app.data.model.Category
import com.distrigo.app.data.model.Marque
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.Quantity
import com.distrigo.app.data.model.SousCategorie
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.ui.common.ActiveProductFilterChips
import com.distrigo.app.ui.common.DsCompactSearchAction
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.ProductCountAndFilters
import com.distrigo.app.ui.common.formatQty
import com.distrigo.app.ui.common.productFilterChips
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.products.ProductCard
import com.distrigo.app.ui.purchases.ProductListFilters
import com.distrigo.app.ui.purchases.PurchaseProductFilterSheet

/**
 * Inventaire: the products still to count, the way the Ventes and Achats product steps list theirs —
 * Produits' search bar with its scanner, the count and "Filtres", and Produits' own rows, stock on the
 * right. A tap on a row, or a scanned code, opens [InventoryCountDialog] on that product; once counted
 * it leaves the list for the count's selection, which the bar at the bottom opens.
 */
@Composable
fun InventoryCountScreen(
    numero          : String,
    products        : LazyPagingItems<Product>,
    remaining       : Int?,
    search          : String,
    onSearchChange  : (String) -> Unit,
    filters         : ProductListFilters,
    onFiltersChange : (ProductListFilters) -> Unit,
    categories      : List<Category>,
    sousCategories  : List<SousCategorie>,
    marques         : List<Marque>,
    suppliers       : List<Supplier>,
    countedCount    : Int,
    ecartsValue     : Double,
    message         : String,
    onBack          : () -> Unit,
    onScan          : () -> Unit,
    onProductClick  : (Product) -> Unit,
    onOpenSelection : () -> Unit,
) {
    val money = LocalMoneyFormatter.current
    var showFilterSheet by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = "Inventaire", subtitle = numero, leading = DsTopBarLeading.Back(onBack))
        HorizontalDivider(color = DsColors.Border, thickness = 1.dp)

        DsCompactSearchField(
            value         = search,
            onValueChange = onSearchChange,
            placeholder   = "Rechercher un produit",
            modifier      = Modifier.padding(horizontal = DsSpacing.lg).padding(top = DsSpacing.md)
        ) {
            DsCompactSearchAction(
                icon               = Icons.Default.QrCodeScanner,
                contentDescription = "Scanner un code-barres",
                tint               = DsColors.Primary,
                onClick            = onScan
            )
        }

        ProductCountAndFilters(
            count         = remaining,
            filtersActive = filters.isActive,
            onOpenFilters = { showFilterSheet = true },
            countLabel    = "à inventorier",
        )
        ActiveProductFilterChips(
            productFilterChips(filters, categories, sousCategories, marques, suppliers, "Prix de vente", money),
            onChange = onFiltersChange
        )

        if (message.isNotEmpty()) {
            Text(
                message, fontSize = DsTextSize.bodySmall, color = DsColors.Danger,
                modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs)
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (products.itemCount == 0) {
                Text(
                    if (remaining == 0 && search.isBlank() && !filters.isActive) "Tous les produits en stock ont été inventoriés."
                    else "Aucun produit à inventorier ne correspond.",
                    fontSize = DsTextSize.body, color = DsColors.TextSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(DsSpacing.xl)
                )
            } else {
                LazyColumn(
                    contentPadding      = PaddingValues(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs, bottom = DsSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(count = products.itemCount, key = products.itemKey { it.id }) { index ->
                        val product = products[index] ?: return@items
                        ProductCard(product = product, onClick = { onProductClick(product) })
                    }
                }
            }
        }

        // The count's selection, as the Ventes and Achats product steps carry theirs.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.xl, vertical = DsSpacing.sm)
                .clip(DsShapes.large)
                .background(if (countedCount > 0) DsColors.PrimaryLight else DsColors.SurfaceSunken)
                .clickable(enabled = countedCount > 0, onClick = onOpenSelection)
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            val tint = if (countedCount > 0) DsColors.Primary else DsColors.TextTertiary
            Box(
                modifier = Modifier.size(20.dp).clip(DsShapes.pill).background(tint),
                contentAlignment = Alignment.Center
            ) {
                Text("$countedCount", color = Color.White, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold)
            }
            Text("Ma sélection", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = tint)
            Spacer(Modifier.weight(1f))
            Text("Écarts ${money.da(ecartsValue)}", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = tint)
        }
    }

    if (showFilterSheet) {
        PurchaseProductFilterSheet(
            filters        = filters,
            categories     = categories,
            sousCategories = sousCategories,
            marques        = marques,
            suppliers      = suppliers,
            resultCount    = remaining ?: 0,
            onChange       = onFiltersChange,
            onDismiss      = { showFilterSheet = false },
            priceLabel     = "Prix de vente",
            // The list is what is in stock already; a stock band would only narrow it to nothing.
            showStockLevel = false,
        )
    }
}

/**
 * The count of one product, in a dialog centred on the screen: its system quantity, read-only, and the
 * quantity found on the shelf. [onSave] records it; [error] is the save's refusal, if any.
 */
@Composable
fun InventoryCountDialog(
    product   : Product,
    isSaving  : Boolean,
    error     : String,
    onSave    : (Double) -> Unit,
    onDismiss : () -> Unit,
) {
    var text by remember(product.id) { mutableStateOf("") }
    var invalid by remember(product.id) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(product.id) { focus.requestFocus() }

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(DsShapes.large)
                .background(DsColors.Surface)
                .padding(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Inventory2, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(DsSpacing.sm))
                Text(product.name, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            }

            Row(
                Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.SurfaceMuted).padding(DsSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Qté système", fontSize = DsTextSize.body, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
                Text(
                    "${formatQty(product.stock)} ${product.unit_type}",
                    fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary
                )
            }

            OutlinedTextField(
                value = text,
                // ',' or '.': the French keyboard's decimal key types ','. Whole units for a pièce product.
                onValueChange = { text = Quantity.sanitizeInput(it, Quantity.allowsFractions(product.unit_type)); invalid = false },
                label = { Text("Qté physique") },
                suffix = { Text(product.unit_type) },
                singleLine = true,
                isError = invalid || error.isNotEmpty(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = DsShapes.medium,
                colors = dsTextFieldColors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            val shown = if (invalid) "Quantité invalide" else error
            if (shown.isNotEmpty()) Text(shown, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)

            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                OutlinedButton(
                    onClick = onDismiss, enabled = !isSaving,
                    modifier = Modifier.weight(1f).height(48.dp), shape = DsShapes.medium
                ) { Text("Annuler") }
                Button(
                    onClick = {
                        val qte = Quantity.parse(text)
                        if (qte == null || qte < 0) invalid = true else onSave(qte)
                    },
                    enabled = !isSaving && text.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp), shape = DsShapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                    else Text("Enregistrer", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
