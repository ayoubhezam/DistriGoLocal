package com.distrigo.app.ui.pertes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.Perte
import com.distrigo.app.data.model.PerteType
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportPeriod
import com.distrigo.app.data.repository.PerteRepository
import com.distrigo.app.data.time.BusinessDates
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.formatQty
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsTopBarOverflowMenu
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.purchases.formatOrderDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The periods the history can be narrowed to; null is all time. */
private val PERIODS: List<Pair<String, ReportPeriod?>> = listOf(
    "Tout" to null, "Ce mois" to ReportPeriod.CE_MOIS, "Mois dernier" to ReportPeriod.MOIS_DERNIER, "Cette année" to ReportPeriod.CETTE_ANNEE,
)

/** What the history is narrowed by: a type (null for all) and a period (null for all time). */
data class PerteHistoryFilters(val typeId: Int? = null, val period: ReportPeriod? = null) {
    val isActive: Boolean get() = typeId != null || period != null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PerteHistoryViewModel @Inject constructor(private val repository: PerteRepository) : ViewModel() {
    val search = MutableStateFlow("")
    val filters = MutableStateFlow(PerteHistoryFilters())

    private val _types = MutableStateFlow<List<PerteType>>(emptyList())
    val types: StateFlow<List<PerteType>> = _types

    /** The pertes, newest first, live: a new one, an edit or a deletion shows at once. */
    val pertes: StateFlow<List<Perte>?> = combine(search, filters) { s, f -> s to f }
        .flatMapLatest { (s, f) ->
            val range = f.period?.let { ReportFilter(it).resolve() }
            repository.observeHistory(f.typeId, range?.start, range?.end, s)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            repository.seedDefaultPerteTypesIfNeeded()
            _types.value = repository.getPerteTypes()
        }
    }
}

/**
 * Pertes: the history of every loss recorded — searchable, filtered by type and period — with "+"
 * for a new one. Each opens its detail. The types themselves are managed from the ⋮ menu.
 */
@Composable
fun PerteHistoryScreen(
    onBack     : (() -> Unit)?,
    onNew      : () -> Unit,
    onOpen     : (Perte) -> Unit,
    onTypes    : () -> Unit,
    viewModel  : PerteHistoryViewModel = hiltViewModel(),
) {
    val money = LocalMoneyFormatter.current
    val search by viewModel.search.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val pertes by viewModel.pertes.collectAsState()
    val types by viewModel.types.collectAsState()
    var sheet by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title = "Historique des pertes",
            leading = onBack?.let { DsTopBarLeading.Back(it) } ?: DsTopBarLeading.None,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(DsShapes.pill).background(DsColors.Primary).clickable(role = Role.Button, onClick = onNew),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Add, contentDescription = "Nouvelle perte", tint = Color.White) }
            DsTopBarOverflowMenu("Types de perte" to onTypes)
        }
        Column(Modifier.fillMaxWidth().background(DsColors.Surface).padding(bottom = DsSpacing.sm)) {
            DsCompactSearchField(
                value = search, onValueChange = { viewModel.search.value = it },
                placeholder = "Rechercher une perte",
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Text("${pertes?.size?.toString() ?: "…"} perte(s)", fontSize = DsTextSize.caption, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
                com.distrigo.app.ui.common.Step2Chip(
                    icon = Icons.Default.FilterList, label = "Filtres",
                    container = DsColors.SurfaceSunken,
                    content = if (filters.isActive) DsColors.Primary else DsColors.TextSecondary,
                    dot = filters.isActive, onClick = { sheet = true }
                )
            }
        }
        val list = pertes
        // A row added above the one in view — a new perte, the newest — would stay out of sight, the
        // list keeping its place by key: when the newest changes, show it.
        val listState = rememberLazyListState()
        val newest = list?.firstOrNull()?.id
        LaunchedEffect(newest) { if (newest != null) listState.scrollToItem(0) }
        when {
            list == null -> Unit
            list.isEmpty() -> Text(
                if (search.isBlank() && !filters.isActive) "Aucune perte enregistrée. « + » pour en ajouter une." else "Aucune perte ne correspond.",
                fontSize = DsTextSize.body, color = DsColors.TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(DsSpacing.xl)
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                items(list, key = { it.id }) { perte ->
                    Row(
                        Modifier
                            .padding(horizontal = DsSpacing.lg)
                            .fillMaxWidth()
                            .clip(DsShapes.medium)
                            .background(DsColors.Surface)
                            .clickable(role = Role.Button) { onOpen(perte) }
                            .padding(DsSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(perte.product_name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium,
                                color = DsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${perte.type_name} · ${formatOrderDate(BusinessDates.localDay(perte.date_time))} · ${if (perte.source == "camion") "Camion" else "Dépôt"}",
                                fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(DsSpacing.sm))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("− ${formatQty(perte.quantity)} ${perte.unit}", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.Danger)
                            Text(money.da(perte.valeur_totale), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }

    if (sheet) PerteFiltersSheet(filters, types, onApply = { viewModel.filters.value = it; sheet = false }, onDismiss = { sheet = false })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PerteFiltersSheet(
    filters: PerteHistoryFilters,
    types: List<PerteType>,
    onApply: (PerteHistoryFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(filters) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DsColors.Surface
    ) {
        Column(Modifier.padding(horizontal = DsSpacing.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filtres", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = { draft = PerteHistoryFilters() }) { Text("Réinitialiser") }
            }
            Text("Type de perte", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Chip("Tous", draft.typeId == null) { draft = draft.copy(typeId = null) }
                types.forEach { t -> Chip(t.name, draft.typeId == t.id) { draft = draft.copy(typeId = t.id) } }
            }
            Text("Période", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                PERIODS.forEach { (label, p) -> Chip(label, draft.period == p) { draft = draft.copy(period = p) } }
            }
            Button(
                onClick = { onApply(draft) },
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = DsShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
            ) { Text("Appliquer", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(DsSpacing.md))
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = DsTextSize.bodySmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) DsColors.Primary else DsColors.TextSecondary,
        modifier = Modifier
            .clip(DsShapes.pill)
            .background(if (selected) DsColors.PrimaryLight else DsColors.SurfaceSunken)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm)
    )
}
