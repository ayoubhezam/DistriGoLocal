package com.distrigo.app.ui.purchases

import com.distrigo.app.core.format.westernNumber
import com.distrigo.app.data.model.ProductUnit
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.Category
import com.distrigo.app.data.model.Marque
import com.distrigo.app.data.model.SousCategorie
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

/**
 * Step 02's filter sheet — Achats, Dépôt Vente and Tournée Vente — laid out like the Produits one so
 * they all read as the same tool.
 *
 * [priceLabel] names the price the list shows ("Prix d'achat" for a bon, "Prix de vente" for a sale).
 * [showStockLevel] is off for a tournée: its list is the camion's, and the stock-level bands read
 * the dépôt's stock, which says nothing about what the camion can sell.
 *
 * Changes apply as they are made, as in Produits: the list behind the sheet is already narrowed
 * when it closes, and "Appliquer" only closes it. "Réinitialiser" clears the criteria and leaves the
 * search box alone — it is not a filter, and Produits' reset leaves it alone too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PurchaseProductFilterSheet(
    filters        : ProductListFilters,
    categories     : List<Category>,
    sousCategories : List<SousCategorie>,
    marques        : List<Marque>,
    suppliers      : List<Supplier>,
    resultCount    : Int,
    onChange       : (ProductListFilters) -> Unit,
    onDismiss      : () -> Unit,
    priceLabel     : String = stringResource(R.string.common_label_purchase_price),
    showStockLevel : Boolean = true,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = DsColors.Surface
    ) {
        Column(
            modifier = Modifier
                .padding(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs, bottom = DsSpacing.xxl)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.common_label_filters), fontWeight = FontWeight.Bold, fontSize = DsTextSize.bodyLarge, color = DsColors.PrimaryDark)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_action_close), tint = DsColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(DsSpacing.sm))

            FilterDropdown(
                label    = stringResource(R.string.common_label_category),
                allLabel = stringResource(R.string.products_all_categories),
                options  = categories,
                selected = filters.categoryId,
                keyOf    = { it.id },
                nameOf   = { it.name },
                // A sous-catégorie belongs to one catégorie, so a different catégorie drops it.
                onSelect = { id ->
                    if (id != filters.categoryId) onChange(filters.copy(categoryId = id, sousCategorieId = null))
                }
            )

            FilterDropdown(
                label    = stringResource(R.string.products_label_subcategory),
                allLabel = stringResource(R.string.products_all_subcategories),
                options  = sousCategories.filter { it.category_id == filters.categoryId },
                selected = filters.sousCategorieId,
                keyOf    = { it.id },
                nameOf   = { it.name },
                enabled  = filters.categoryId != null,
                onSelect = { id -> onChange(filters.copy(sousCategorieId = id)) }
            )

            FilterDropdown(
                label    = stringResource(R.string.common_label_brand),
                allLabel = stringResource(R.string.products_all_brands),
                options  = marques,
                selected = filters.marqueId,
                keyOf    = { it.id },
                nameOf   = { it.name },
                onSelect = { id -> onChange(filters.copy(marqueId = id)) }
            )

            FilterDropdown(
                label    = stringResource(R.string.purchases_filter_product_supplier),
                allLabel = stringResource(R.string.common_all_suppliers),
                options  = suppliers,
                selected = filters.supplierId,
                keyOf    = { it.id },
                nameOf   = { it.name },
                onSelect = { id -> onChange(filters.copy(supplierId = id)) }
            )

            Text(stringResource(R.string.products_form_storage_unit), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                (listOf<Pair<String?, String>>(null to stringResource(R.string.common_filter_all_f)) + ProductUnit.ALL.map { it to stringResource(ProductUnit.labelRes(it)) }).forEach { (value, label) ->
                    val active = filters.unitType == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(DsShapes.medium)
                            .background(if (active) DsColors.PrimaryDark else DsColors.Surface)
                            .border(1.dp, if (active) DsColors.PrimaryDark else DsColors.Border, DsShapes.medium)
                            .clickable { onChange(filters.copy(unitType = value)) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize   = DsTextSize.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color      = if (active) Color.White else DsColors.TextPrimary
                        )
                    }
                }
            }
            Spacer(Modifier.height(DsSpacing.md))

            if (showStockLevel) {
                FilterDropdown(
                    label    = stringResource(R.string.products_filter_stock_level),
                    allLabel = stringResource(R.string.common_filter_all),
                    options  = listOf("in_stock" to stringResource(R.string.common_stock_in), "low_stock" to stringResource(R.string.common_stock_low), "out_of_stock" to stringResource(R.string.common_stock_out)),
                    selected = filters.stockLevel,
                    keyOf    = { it.first },
                    nameOf   = { it.second },
                    onSelect = { value -> onChange(filters.copy(stockLevel = value)) }
                )
            }

            Text(stringResource(R.string.purchases_filter_price_da, priceLabel), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                PriceBoundField(
                    value         = filters.priceMin,
                    placeholder   = stringResource(R.string.products_filter_min),
                    onValueChange = { onChange(filters.copy(priceMin = it)) },
                    modifier      = Modifier.weight(1f)
                )
                PriceBoundField(
                    value         = filters.priceMax,
                    placeholder   = stringResource(R.string.products_filter_max),
                    onValueChange = { onChange(filters.copy(priceMax = it)) },
                    modifier      = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(DsSpacing.md))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.products_filter_expiring), fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary)
                    Text(stringResource(R.string.products_filter_expiring_hint), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                }
                Switch(
                    checked         = filters.expiringSoon,
                    onCheckedChange = { onChange(filters.copy(expiringSoon = it)) },
                    colors          = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DsColors.Primary)
                )
            }
            Spacer(Modifier.height(DsSpacing.lg))

            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                OutlinedButton(
                    onClick  = { onChange(ProductListFilters()); onDismiss() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.TextPrimary),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
                ) {
                    Text(stringResource(R.string.common_action_reset), fontSize = DsTextSize.body, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick  = onDismiss,
                    modifier = Modifier.weight(1.4f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = DsSpacing.md),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
                ) {
                    Text(stringResource(R.string.common_action_apply_count, resultCount), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** A read-only dropdown with an "all" entry first, as every select in the Produits sheet has. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T, K> FilterDropdown(
    label    : String,
    allLabel : String,
    options  : List<T>,
    selected : K?,
    keyOf    : (T) -> K,
    nameOf   : (T) -> String,
    onSelect : (K?) -> Unit,
    enabled  : Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Text(
        label,
        fontSize = DsTextSize.bodySmall,
        color    = if (enabled) DsColors.TextSecondary else DsColors.TextSecondary.copy(alpha = 0.5f),
        modifier = Modifier.padding(bottom = DsSpacing.xs)
    )
    ExposedDropdownMenuBox(
        expanded         = enabled && expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value         = options.firstOrNull { keyOf(it) == selected }?.let(nameOf) ?: allLabel,
            onValueChange = {},
            readOnly      = true,
            enabled       = enabled,
            singleLine    = true,
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = enabled && expanded) },
            modifier      = Modifier.fillMaxWidth().menuAnchor(),
            shape         = DsShapes.medium,
            colors        = dsTextFieldColors(
                unfocusedBorderColor = DsColors.Border,
                focusedBorderColor   = DsColors.Primary,
                disabledBorderColor  = DsColors.Border.copy(alpha = 0.5f),
                disabledTextColor    = DsColors.TextSecondary.copy(alpha = 0.5f)
            )
        )
        ExposedDropdownMenu(expanded = enabled && expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text    = { Text(allLabel, color = DsColors.TextSecondary) },
                onClick = { onSelect(null); expanded = false }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text    = { Text(nameOf(option)) },
                    onClick = { onSelect(keyOf(option)); expanded = false }
                )
            }
        }
    }
    Spacer(Modifier.height(DsSpacing.md))
}

@Composable
private fun PriceBoundField(
    value         : String,
    placeholder   : String,
    onValueChange : (String) -> Unit,
    modifier      : Modifier = Modifier
) {
    OutlinedTextField(
        value           = value,
        // Digits and one decimal point: the value is read back with toDoubleOrNull().
        onValueChange   = { raw ->
            val filtered = westernNumber(raw).replace(',', '.').filter { it.isDigit() || it == '.' }
            if (filtered.count { it == '.' } <= 1) onValueChange(filtered)
        },
        placeholder     = { Text(placeholder) },
        singleLine      = true,
        modifier        = modifier,
        shape           = DsShapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors          = dsTextFieldColors(
            unfocusedBorderColor = DsColors.Border,
            focusedBorderColor   = DsColors.Primary
        )
    )
}
