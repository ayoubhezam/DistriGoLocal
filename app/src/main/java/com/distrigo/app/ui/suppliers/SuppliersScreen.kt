package com.distrigo.app.ui.suppliers

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey

import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val error     by viewModel.error.collectAsStateWithLifecycle()

    var showFilterSheet by remember { mutableStateOf(false) }
    val filters = viewModel.listFilters

    // Searched, filtered, counted and paged in SQL once typing pauses (PERF-01, see SupplierViewModel.pagedSuppliers)
    val shown = viewModel.pagedSuppliers.collectAsLazyPagingItems()
    val shownCount by viewModel.shownCount.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    // Leaving the list clears the search, as it did when the box's text lived in this screen.
    DisposableEffect(Unit) { onDispose { viewModel.listSearch = "" } }

    // The sheet offers only places the suppliers actually have, so no choice can empty the list. Read when it opens.
    val wilayas  by produceState(emptyList<String>(), showFilterSheet) { if (showFilterSheet) value = viewModel.places("wilaya_name") }
    val communes by produceState(emptyList<String>(), showFilterSheet, filters.wilaya) {
        value = if (showFilterSheet && filters.wilaya != null) viewModel.places("commune_name", filters.wilaya) else emptyList()
    }
    val filterChips: List<Pair<String, () -> Unit>> = buildList {
        filters.balance?.let { b -> add(stringResource(b.label) to { viewModel.listFilters = filters.copy(balance = null) }) }
        filters.wilaya?.let { w -> add(stringResource(R.string.common_chip_value, stringResource(R.string.common_label_wilaya), com.distrigo.app.ui.common.wilayaShownName(w).orEmpty()) to { viewModel.listFilters = filters.copy(wilaya = null, commune = null) }) }
        filters.commune?.let { c -> add(stringResource(R.string.common_chip_value, stringResource(R.string.common_label_commune), com.distrigo.app.ui.common.communeShownName(filters.wilaya, c).orEmpty()) to { viewModel.listFilters = filters.copy(commune = null) }) }
    }

    if (showFilterSheet) {
        PartyFilterSheet(
            resultCount = shownCount ?: 0,
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

    val totalDebt = summary?.owingTotal ?: 0.0



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
                        value         = viewModel.listSearch,
                        onValueChange = { viewModel.listSearch = it },
                        placeholder   = stringResource(R.string.purchases_search_supplier),
                        modifier      = Modifier.padding(horizontal = DsSpacing.lg)
                    )

                    Spacer(Modifier.height(DsSpacing.sm))

                    // ── Count · Filtres ── where the supplier is, and its solde.
                    CountAndFiltersRow(
                        label         = (shownCount ?: 0).let { pluralStringResource(R.plurals.suppliers_count, it, it) },
                        filtersActive = filters.isActive,
                        onOpenFilters = { showFilterSheet = true }
                    )
                    RemovableFilterChips(filterChips, onClearAll = { viewModel.listFilters = SupplierListFilters() })

                    Spacer(Modifier.height(DsSpacing.xs))
                }
            }

            items(count = shown.itemCount, key = shown.itemKey { it.id }, contentType = { "supplier" }) { index ->
                val supplier = shown[index]
                Box(modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs)) {
                    if (supplier == null) com.distrigo.app.ui.common.PartyRowPlaceholder()
                    else SupplierCard(
                        supplier = supplier,
                        onClick  = { onSupplierClick(supplier.id) }
                    )
                }
            }
            if (shownCount == 0) {
                // Under the search and filters, so they stay at hand; the hint only when there are no suppliers at all.
                // Not while counting: the list starts empty and would flash « Aucun fournisseur ».
                item(key = "empty", contentType = "empty") {
                    val noSuppliers = summary?.total == 0
                    Box(Modifier.fillMaxWidth().fillParentMaxHeight(0.7f), contentAlignment = Alignment.Center) {
                        com.distrigo.app.ui.common.EmptyState(
                            illustration = R.drawable.empty_fournisseurs,
                            title        = stringResource(if (noSuppliers) R.string.empty_suppliers_title else R.string.empty_suppliers_no_match),
                            hint         = stringResource(R.string.empty_hint_suppliers).takeIf { noSuppliers },
                        )
                    }
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
