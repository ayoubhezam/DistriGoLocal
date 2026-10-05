package com.distrigo.app.ui.charges

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.distrigo.app.data.local.dao.ChargeHistoryRow
import com.distrigo.app.data.model.ChargeSubType
import com.distrigo.app.data.model.ChargeType
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportPeriod
import com.distrigo.app.data.repository.ChargeRepository
import com.distrigo.app.data.time.BusinessDates
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** The periods the history can be narrowed to; null is all time. */
private val PERIODS: List<Pair<String, ReportPeriod?>> = listOf(
    "Tout" to null, "Ce mois" to ReportPeriod.CE_MOIS, "Mois dernier" to ReportPeriod.MOIS_DERNIER, "Cette année" to ReportPeriod.CETTE_ANNEE,
)

/** What the history is narrowed by: a type, one of its sub-types, a period — null for all. */
data class ChargeHistoryFilters(val typeId: Int? = null, val subtypeId: Int? = null, val period: ReportPeriod? = null) {
    val isActive: Boolean get() = typeId != null || subtypeId != null || period != null
}

/** [date] at the time it is saved — or, editing, at the time the charge already had — as an instant. */
internal fun chargeInstant(date: LocalDate, timeOf: String? = null): String {
    val zone = ZoneId.systemDefault()
    val time = timeOf?.let { runCatching { Instant.parse(it).atZone(zone).toLocalTime() }.getOrNull() } ?: LocalTime.now()
    return date.atTime(time).atZone(zone).toInstant().toString()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChargeHistoryViewModel @Inject constructor(private val repository: ChargeRepository) : ViewModel() {
    val search = MutableStateFlow("")
    val filters = MutableStateFlow(ChargeHistoryFilters())

    private val _types = MutableStateFlow<List<ChargeType>>(emptyList())
    val types: StateFlow<List<ChargeType>> = _types
    private val _subTypes = MutableStateFlow<List<ChargeSubType>>(emptyList())
    val subTypes: StateFlow<List<ChargeSubType>> = _subTypes

    /** The charges, newest first, live: a new one, an edit or a deletion shows at once. */
    val charges: StateFlow<List<ChargeHistoryRow>?> = combine(search, filters) { s, f -> s to f }
        .flatMapLatest { (s, f) ->
            val range = f.period?.let { ReportFilter(it).resolve() }
            repository.observeHistory(f.typeId, f.subtypeId, range?.start, range?.end, s)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The amount of the charge just added, for its "Annuler"; null once answered. */
    private val _justAdded = MutableStateFlow<Pair<Int, Double>?>(null)
    val justAdded: StateFlow<Pair<Int, Double>?> = _justAdded

    init {
        viewModelScope.launch {
            repository.seedDefaultChargeTypesIfNeeded()
            reloadTypes()
        }
    }

    private suspend fun reloadTypes() {
        _types.value = repository.getChargeTypes()
        _subTypes.value = repository.getAllSubTypes()
    }

    fun add(input: ChargeInput, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val error = try {
                val id = repository.addCharge(input.subtypeId, input.montant, chargeInstant(input.date), input.fournisseur, input.note)
                _justAdded.value = id to input.montant
                null
            } catch (e: Exception) { e.message ?: "Enregistrement impossible" }
            onDone(error)
        }
    }

    fun addSubType(typeId: Int, name: String, hasFournisseur: Boolean, onDone: (ChargeSubType?, String?) -> Unit) {
        viewModelScope.launch {
            val (created, error) = repository.createSubType(typeId, name, hasFournisseur)
            if (created != null) reloadTypes()
            onDone(created, error)
        }
    }

    /** The snackbar's "Annuler": the charge just added is deleted again. */
    fun undoAdd() {
        val (id, _) = _justAdded.value ?: return
        _justAdded.value = null
        viewModelScope.launch { repository.deleteCharge(id) }
    }

    fun dismissAdded() { _justAdded.value = null }
}

/**
 * Charges: every expense recorded — searchable, filtered by type, sub-type and period, under a header
 * per day — with "+" for a new one, entered in [ChargeDialog] and saved at once ("Annuler" in the
 * snackbar takes it back). Each opens its detail.
 */
@Composable
fun ChargeHistoryScreen(
    onBack    : (() -> Unit)?,
    onOpen    : (ChargeHistoryRow) -> Unit,
    viewModel : ChargeHistoryViewModel = hiltViewModel(),
) {
    val money = LocalMoneyFormatter.current
    val search by viewModel.search.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val charges by viewModel.charges.collectAsState()
    val types by viewModel.types.collectAsState()
    val subTypes by viewModel.subTypes.collectAsState()
    val justAdded by viewModel.justAdded.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(justAdded) {
        val (_, amount) = justAdded ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("Dépense de ${money.da(amount)} enregistrée", actionLabel = "Annuler", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoAdd() else viewModel.dismissAdded()
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title = "Historique des charges",
            leading = onBack?.let { DsTopBarLeading.Back(it) } ?: DsTopBarLeading.None,
        ) {
            Box(
                modifier = Modifier.padding(end = DsSpacing.md).size(40.dp).clip(DsShapes.pill).background(DsColors.Primary)
                    .clickable(role = Role.Button) { saveError = ""; adding = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Add, contentDescription = "Nouvelle charge", tint = Color.White) }
        }
        Column(Modifier.fillMaxWidth().background(DsColors.Surface).padding(bottom = DsSpacing.sm)) {
            DsCompactSearchField(
                value = search, onValueChange = { viewModel.search.value = it },
                placeholder = "Rechercher une charge",
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                val list = charges
                Text(
                    if (list == null) "…" else "${list.size} charge(s) · ${money.da(list.sumOf { it.montant })}",
                    fontSize = DsTextSize.caption, color = DsColors.TextSecondary, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                com.distrigo.app.ui.common.Step2Chip(
                    icon = Icons.Default.FilterList, label = "Filtres",
                    container = DsColors.SurfaceSunken,
                    content = if (filters.isActive) DsColors.Primary else DsColors.TextSecondary,
                    dot = filters.isActive, onClick = { sheet = true }
                )
            }
        }
        val list = charges
        // A row added above the one in view — a new charge, the newest — would stay out of sight.
        val listState = rememberLazyListState()
        val newest = list?.firstOrNull()?.id
        LaunchedEffect(newest) { if (newest != null) listState.scrollToItem(0) }
        when {
            list == null -> Unit
            list.isEmpty() -> Text(
                if (search.isBlank() && !filters.isActive) "Aucune charge enregistrée. « + » pour en ajouter une." else "Aucune charge ne correspond.",
                fontSize = DsTextSize.body, color = DsColors.TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(DsSpacing.xl)
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = DsSpacing.xs, bottom = DsSpacing.xxxl + 56.dp),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.xs)
            ) {
                // A header before each local day's charges, as Achats, Ventes and Pertes date their lists.
                list.groupBy { BusinessDates.localDay(it.date_time) }.forEach { (day, ofDay) ->
                    item(key = "date_$day", contentType = "header") {
                        Text(
                            text = formatOrderDate(day), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold,
                            color = DsColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm)
                        )
                    }
                    items(ofDay, key = { it.id }, contentType = { "charge" }) { charge ->
                        ChargeRow(charge, money.da(charge.montant)) { onOpen(charge) }
                    }
                }
            }
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(DsSpacing.lg))
    }

    if (adding) {
        ChargeDialog(
            title = "Nouvelle charge", types = types, subTypes = subTypes, initial = null,
            isSaving = saving, error = saveError,
            onSave = { input ->
                saving = true; saveError = ""
                viewModel.add(input) { error -> saving = false; if (error == null) adding = false else saveError = error }
            },
            onAddSubType = viewModel::addSubType,
            onDismiss = { adding = false }
        )
    }
    if (sheet) ChargeFiltersSheet(filters, types, subTypes, onApply = { viewModel.filters.value = it; sheet = false }, onDismiss = { sheet = false })
}

/** A charge in the history: its sub-type's icon in its type's colour, the sub-type over the type, the amount in Primary. */
@Composable
private fun ChargeRow(charge: ChargeHistoryRow, amount: String, onClick: () -> Unit) {
    val tint = charge.color_hex?.let { ChargeIconMapper.colorFor(it) } ?: DsColors.Primary
    Row(
        Modifier
            .padding(horizontal = DsSpacing.lg)
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(DsShapes.small).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(ChargeIconMapper.iconFor(charge.icon ?: ""), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(charge.subtype_name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium,
                color = DsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(charge.type_name, fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Text(amount, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Primary, maxLines = 1, softWrap = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChargeFiltersSheet(
    filters: ChargeHistoryFilters,
    types: List<ChargeType>,
    subTypes: List<ChargeSubType>,
    onApply: (ChargeHistoryFilters) -> Unit,
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
                TextButton(onClick = { draft = ChargeHistoryFilters() }) { Text("Réinitialiser") }
            }
            Text("Type", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Chip("Tous", draft.typeId == null) { draft = draft.copy(typeId = null, subtypeId = null) }
                types.forEach { t -> Chip(t.name, draft.typeId == t.id) { draft = draft.copy(typeId = t.id, subtypeId = null) } }
            }
            // A type's sub-types, once a type is chosen.
            draft.typeId?.let { typeId ->
                Text("Sous-type", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    Chip("Tous", draft.subtypeId == null) { draft = draft.copy(subtypeId = null) }
                    subTypes.filter { it.type_id == typeId }.forEach { s -> Chip(s.name, draft.subtypeId == s.id) { draft = draft.copy(subtypeId = s.id) } }
                }
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
