package com.distrigo.app.ui.rapports

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.dao.DaySale
import com.distrigo.app.data.model.numberLabel
import com.distrigo.app.data.model.report.ReportFilterStore
import com.distrigo.app.data.model.report.ReportSource
import com.distrigo.app.data.repository.ReportRepository
import com.distrigo.app.data.time.BusinessDates
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.navigation.DrillTarget
import com.distrigo.app.ui.navigation.LocalDrillDown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** One day of the Ventes report: its sales, from the report's source (Tout, Dépôt or Camion). */
@HiltViewModel
class DaySalesViewModel @Inject constructor(
    repository: ReportRepository,
    filterStore: ReportFilterStore,
    savedState: SavedStateHandle,
) : ViewModel() {
    val day: LocalDate = LocalDate.parse(savedState.get<String>("day")!!)
    val source: ReportSource = filterStore.filter.value.source

    private val _sales = MutableStateFlow<List<DaySale>?>(null)
    val sales: StateFlow<List<DaySale>?> = _sales

    init {
        viewModelScope.launch { _sales.value = repository.salesOfDay(day, source.key) }
    }
}

private val TITLE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Rapports › Ventes › a day: that day's sales, newest first, each opening the sale itself — the bridge
 * from a figure in the report to the documents behind it.
 */
@Composable
fun DaySalesScreen(onBack: () -> Unit, viewModel: DaySalesViewModel = hiltViewModel()) {
    val sales by viewModel.sales.collectAsState()
    val money = LocalMoneyFormatter.current
    val drill = LocalDrillDown.current
    val title = viewModel.day.format(TITLE).replaceFirstChar { it.titlecase(Locale.FRENCH) }
    val list = sales

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title = title,
            subtitle = list?.let { s ->
                val scope = if (viewModel.source == ReportSource.TOUT) "" else " · ${viewModel.source.label}"
                "${plural(s.size, "vente", "ventes")} · ${money.da(s.sumOf { it.total })}$scope"
            },
            leading = DsTopBarLeading.Back(onBack),
        )
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.Primary)
            }
            list.isEmpty() -> ReportMessage("Aucune vente ce jour-là.")
            else -> LazyColumn(
                contentPadding = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
            ) {
                items(list, key = { it.id }) { sale -> SaleRow(sale) { drill(DrillTarget.Vente(sale.id)) } }
            }
        }
    }
}

@Composable
private fun SaleRow(sale: DaySale, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val paid = minOf(sale.montant_paye, sale.total)
    val (status, color) = when {
        sale.total > 0 && paid >= sale.total -> "Payé" to DsColors.Success
        paid > 0                             -> "Partiel" to DsColors.Warning
        else                                 -> "Impayé" to DsColors.Danger
    }
    val time = runCatching {
        Instant.parse(sale.created_at).atZone(ZoneId.systemDefault()).format(TIME)
    }.getOrDefault(BusinessDates.localDay(sale.created_at))
    Row(
        Modifier
            .padding(horizontal = DsSpacing.lg)
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                sale.client_name ?: "—", fontSize = DsTextSize.body, fontWeight = FontWeight.Medium,
                color = DsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${numberLabel(sale.numero, sale.id)} · $time · ${if (sale.source == "camion") "Camion" else "Dépôt"}",
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
            )
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(money.da(sale.total), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text(
                status, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = color,
                modifier = Modifier.clip(DsShapes.pill).background(color.copy(alpha = 0.12f)).padding(horizontal = DsSpacing.sm, vertical = 2.dp),
            )
        }
    }
}
