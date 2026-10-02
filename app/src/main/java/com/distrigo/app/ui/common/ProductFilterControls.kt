package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.distrigo.app.core.format.MoneyFormatter
import com.distrigo.app.data.model.Category
import com.distrigo.app.data.model.Marque
import com.distrigo.app.data.model.ProductUnit
import com.distrigo.app.data.model.SousCategorie
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.purchases.ProductListFilters
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect

// Step 02's filtering controls, shared by Achats, Dépôt Vente and Tournée Vente so the three read as
// one tool: the count / Filtres row, and a removable chip per criterion applied.

/** Height shared by the chips of Step 02's count / Filtres (/ Nouveau produit) row. */
internal val Step2ChipHeight = 32.dp

/**
 * One chip of Step 02's count / Filtres / Nouveau produit row.
 *
 * All three are drawn by this one function so they cannot drift apart: one height, one corner, one
 * padding, one icon size, one type size. What tells them apart is colour alone — the count is
 * information, Filtres is a control, Nouveau produit is the action.
 */
@Composable
internal fun Step2Chip(
    icon      : ImageVector,
    label     : String,
    container : Color,
    content   : Color,
    modifier  : Modifier = Modifier,
    dot       : Boolean = false,
    onClick   : (() -> Unit)? = null
) {
    Box(modifier = modifier) {
    Row(
        modifier = Modifier
            .height(Step2ChipHeight)
            .clip(DsShapes.medium)
            .background(container)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = DsSpacing.sm),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(
            label,
            fontSize   = DsTextSize.caption,
            fontWeight = FontWeight.SemiBold,
            color      = content,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
    }
    // The same 6dp dot the Produits "Filtres" button shows while a filter is on. Overlaid in the
    // corner rather than placed in the row, so switching it on does not widen the chip.
    if (dot) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 5.dp, end = 5.dp)
                .size(6.dp)
                .clip(DsShapes.pill)
                .background(DsColors.Primary)
        )
    }
    }
}

/**
 * The count and the "Filtres" button, as Achats has always laid them out. The count takes whatever
 * width the buttons leave and is the one that ellipsizes; [trailing] is room for a further action
 * (Achats' "Nouveau produit").
 */
@Composable
internal fun ProductCountAndFilters(
    count         : Int?,
    filtersActive : Boolean,
    onOpenFilters : () -> Unit,
    modifier      : Modifier = Modifier,
    trailing      : @Composable () -> Unit = {},
) {
    Row(
        modifier          = modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Step2Chip(
                icon      = Icons.Default.Inventory2,
                label     = "${count?.toString() ?: "…"} produit(s)",
                container = DsColors.SurfaceSunken,
                content   = DsColors.TextSecondary
            )
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Step2Chip(
            icon      = Icons.Default.FilterList,
            label     = "Filtres",
            // Blue with a dot once it narrows anything, as the Produits button is. Not "Filtres · 2":
            // a count in the label widens the chip the moment a filter is set and squeezes the
            // product count beside it into an ellipsis — the one figure a user filters in order to
            // read. The chips below already say exactly what is applied.
            container = DsColors.SurfaceSunken,
            content   = if (filtersActive) DsColors.Primary else DsColors.TextSecondary,
            dot       = filtersActive,
            onClick   = onOpenFilters
        )
        trailing()
    }
}

/** One removable chip per active criterion, each carrying the filters without it. */
internal fun productFilterChips(
    filters        : ProductListFilters,
    categories     : List<Category>,
    sousCategories : List<SousCategorie>,
    marques        : List<Marque>,
    suppliers      : List<Supplier>,
    priceLabel     : String,
    money          : MoneyFormatter,
): List<Pair<String, ProductListFilters>> = buildList {
    filters.categoryId?.let { id ->
        add("Catégorie : ${categories.find { it.id == id }?.name ?: "—"}" to filters.copy(categoryId = null, sousCategorieId = null))
    }
    filters.sousCategorieId?.let { id ->
        add("Sous-catégorie : ${sousCategories.find { it.id == id }?.name ?: "—"}" to filters.copy(sousCategorieId = null))
    }
    filters.marqueId?.let { id ->
        add("Marque : ${marques.find { it.id == id }?.name ?: "—"}" to filters.copy(marqueId = null))
    }
    filters.supplierId?.let { id ->
        add("Fournisseur : ${suppliers.find { it.id == id }?.name ?: "—"}" to filters.copy(supplierId = null))
    }
    filters.unitType?.let { unit ->
        add(ProductUnit.label(unit) to filters.copy(unitType = null))
    }
    filters.stockLevel?.let { level ->
        val label = when (level) {
            "in_stock"  -> "En stock"
            "low_stock" -> "Stock faible"
            else        -> "Rupture de stock"
        }
        add(label to filters.copy(stockLevel = null))
    }
    val priceMin = filters.priceMin.toDoubleOrNull()
    val priceMax = filters.priceMax.toDoubleOrNull()
    if (priceMin != null || priceMax != null) {
        val range = when {
            priceMin != null && priceMax != null -> "${money.amount(priceMin)}–${money.da(priceMax)}"
            priceMin != null                     -> "≥ ${money.da(priceMin)}"
            else                                 -> "≤ ${money.da(priceMax!!)}"
        }
        add("$priceLabel : $range" to filters.copy(priceMin = "", priceMax = ""))
    }
    if (filters.expiringSoon) add("Bientôt périmé" to filters.copy(expiringSoon = false))
}

/** What is narrowing the list, each removable on its own — the whole chip is the target, not a 14dp cross. */
@Composable
internal fun ActiveProductFilterChips(
    chips    : List<Pair<String, ProductListFilters>>,
    onChange : (ProductListFilters) -> Unit,
) {
    if (chips.isEmpty()) return
    // A chip added before the first one shown would otherwise leave the row anchored on the old
    // first chip, cutting the new one off at the left edge: every change starts the row over.
    val rowState = rememberLazyListState()
    LaunchedEffect(chips.map { it.first }) { rowState.scrollToItem(0) }
    LazyRow(
        state                 = rowState,
        contentPadding        = PaddingValues(horizontal = DsSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
        modifier              = Modifier.padding(bottom = DsSpacing.sm)
    ) {
        items(chips, key = { it.first }) { (label, withoutIt) ->
            Row(
                modifier = Modifier
                    .height(Step2ChipHeight)
                    .clip(DsShapes.pill)
                    .background(DsColors.PrimaryLight)
                    .clickable { onChange(withoutIt) }
                    .padding(horizontal = DsSpacing.md),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
            ) {
                Text(label, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Primary, maxLines = 1)
                Icon(Icons.Default.Close, contentDescription = "Retirer", tint = DsColors.Primary, modifier = Modifier.size(14.dp))
            }
        }
        item(key = "clear-all") {
            Box(
                modifier = Modifier
                    .height(Step2ChipHeight)
                    .clip(DsShapes.pill)
                    .clickable { onChange(ProductListFilters()) }
                    .padding(horizontal = DsSpacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text("Tout effacer", fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
            }
        }
    }
}
