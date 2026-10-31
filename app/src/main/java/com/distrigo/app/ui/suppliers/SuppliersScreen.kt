package com.distrigo.app.ui.suppliers

import com.distrigo.app.core.format.westernDigits
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsTopBarSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.ExperimentalFoundationApi
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.filterSuppliers
import com.distrigo.app.ui.common.supplierDebtTotal
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.common.PartyFilterSheet
import com.distrigo.app.ui.common.CountAndFiltersRow
import com.distrigo.app.ui.common.RemovableFilterChips
import com.distrigo.app.ui.common.SupplierListFilters
import com.distrigo.app.ui.common.placesOf
import com.distrigo.app.data.model.Amount
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R
import androidx.compose.ui.res.pluralStringResource
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SuppliersScreen(
    viewModel       : SupplierViewModel = hiltViewModel(),
    modifier        : Modifier = Modifier,
    onBack          : (() -> Unit)? = null,
    onAddSupplier   : () -> Unit = {},
    onSupplierClick : (Int) -> Unit = {}
) {
    val money = LocalMoneyFormatter.current
    val suppliers by viewModel.suppliers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error     by viewModel.error.collectAsState()

    var search by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }
    val filters = viewModel.listFilters

    // Recomputed only when the list or the search changes, not on every recomposition. The
    // search regex used to be compiled inside the predicate: once per supplier, per keystroke.
    val filtered  = remember(suppliers, search, filters) { filterSuppliers(suppliers, search, filters) }

    // The sheet offers only places the suppliers actually have, so no choice can empty the list.
    val wilayas  = remember(suppliers) { placesOf(suppliers.map { it.wilaya_name }) }
    val communes = remember(suppliers, filters.wilaya) {
        placesOf(suppliers.filter { it.wilaya_name?.trim().equals(filters.wilaya, ignoreCase = true) }.map { it.commune_name })
    }
    val filterChips: List<Pair<String, () -> Unit>> = buildList {
        filters.balance?.let { b -> add(stringResource(b.label) to { viewModel.listFilters = filters.copy(balance = null) }) }
        filters.wilaya?.let { w -> add(stringResource(R.string.common_chip_value, stringResource(R.string.common_label_wilaya), w) to { viewModel.listFilters = filters.copy(wilaya = null, commune = null) }) }
        filters.commune?.let { c -> add(stringResource(R.string.common_chip_value, stringResource(R.string.common_label_commune), c) to { viewModel.listFilters = filters.copy(commune = null) }) }
    }

    if (showFilterSheet) {
        PartyFilterSheet(
            resultCount = filtered.size,
            wilayas     = wilayas,
            wilaya      = filters.wilaya,
            onWilaya    = { viewModel.listFilters = filters.copy(wilaya = it, commune = null) },
            communes    = communes,
            commune     = filters.commune,
            onCommune   = { viewModel.listFilters = filters.copy(commune = it) },
            balance     = filters.balance,
            onBalance   = { viewModel.listFilters = filters.copy(balance = it) },
            onReset     = { viewModel.listFilters = SupplierListFilters() },
            onDismiss   = { showFilterSheet = false }
        )
    }

    val totalDebt = remember(suppliers) { supplierDebtTotal(suppliers) }



    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.SurfaceMuted)
    ) {
        // Pushed from the Plus drawer over whichever tab was showing, so it takes a back
        // affordance; null-safe because the screen is still usable as a root.
        DsTopAppBar(
            title   = stringResource(R.string.common_label_suppliers),
            licenseBanner = true,
            leading = onBack?.let { DsTopBarLeading.Back(it) } ?: DsTopBarLeading.None,
            size    = DsTopBarSize.Large
        ) {
            FloatingActionButton(
                onClick        = { onAddSupplier() },
                containerColor = DsColors.PrimaryDark,
                contentColor   = Color.White,
                modifier       = Modifier.size(40.dp),
                shape          = DsShapes.pill
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.common_action_add))
            }
        }

        // Pinned bar, so the list below it carries only the list — see ClientsScreen.
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (totalDebt > 0) {
                item {
                    Column {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DsSpacing.lg)
                                .clip(DsShapes.large)
                                .background(DsColors.DangerLight)
                                .padding(DsSpacing.lg)
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.suppliers_total_debt), fontSize = DsTextSize.bodySmall, color = DsColors.Danger, fontWeight = FontWeight.Medium)
                            }
                            Text(
                                money.da(totalDebt),
                                fontSize   = DsTextSize.bodyLarge,
                                color      = DsColors.Danger,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(DsSpacing.md))
                    }
                }
            }

            stickyHeader {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DsColors.SurfaceMuted)
                ) {
                    // ── Search ──
                    DsCompactSearchField(
                        value         = search,
                        onValueChange = { search = it },
                        placeholder   = stringResource(R.string.purchases_search_supplier),
                        modifier      = Modifier.padding(horizontal = DsSpacing.lg)
                    )

                    Spacer(Modifier.height(DsSpacing.sm))

                    // ── Count · Filtres ── where the supplier is, and its solde.
                    CountAndFiltersRow(
                        label         = pluralStringResource(R.plurals.suppliers_count, filtered.size, filtered.size),
                        filtersActive = filters.isActive,
                        onOpenFilters = { showFilterSheet = true }
                    )
                    RemovableFilterChips(filterChips, onClearAll = { viewModel.listFilters = SupplierListFilters() })

                    Spacer(Modifier.height(DsSpacing.xs))
                }
            }

            items(filtered, key = { it.id }) { supplier ->
                Box(modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs)) {
                    SupplierCard(
                        supplier = supplier,
                        onClick  = { onSupplierClick(supplier.id) }
                    )
                }
            }
        }
    }
}


@Composable
fun SupplierCard(supplier: Supplier, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
        val isDebt = Amount.owing(supplier.balance)
        val colors = listOf(0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A, 0xFFC62828, 0xFFE65100, 0xFF00695C)
        val color = Color(colors[supplier.name[0].code % colors.size])
        val initials = supplier.name.split(" ").take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

        Card(
            modifier  = Modifier.fillMaxWidth().clickable { onClick() },
            shape     = DsShapes.large,
            colors    = CardDefaults.cardColors(containerColor = DsColors.Surface),
            elevation = CardDefaults.cardElevation(0.dp),
            border    = androidx.compose.foundation.BorderStroke(
                1.dp, if (isDebt) DsColors.Danger else DsColors.Border
            )
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier         = Modifier.size(42.dp).clip(DsShapes.pill),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.13f)))
                    Text(initials, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = color)
                }
                Spacer(Modifier.width(DsSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(supplier.name, fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(11.dp))
                        Text(westernDigits(supplier.phone ?: ""), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                    }
                }
                Spacer(Modifier.width(DsSpacing.sm))
                if (isDebt) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.suppliers_owed), fontSize = DsTextSize.caption, color = DsColors.Danger, fontWeight = FontWeight.Medium)
                        Text(money.da(supplier.balance), fontSize = DsTextSize.bodySmall, color = DsColors.Danger, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(DsShapes.pill)
                            .background(DsColors.SuccessLight)
                            .padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(11.dp))
                            Text(stringResource(R.string.suppliers_settled), fontSize = DsTextSize.caption, color = DsColors.Success, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.width(DsSpacing.sm))
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
