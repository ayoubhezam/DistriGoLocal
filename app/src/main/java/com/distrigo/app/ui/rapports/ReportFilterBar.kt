package com.distrigo.app.ui.rapports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.report.ReportFilter
import com.distrigo.app.data.model.report.ReportPeriod
import com.distrigo.app.data.model.report.ReportSource
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val DAY_SHORT = DateTimeFormatter.ofPattern("dd/MM")

/** The days a filter covers as of [today], as `01/10 – 02/10/2026`, or one date for a single day. */
fun ReportFilter.daysText(today: LocalDate = LocalDate.now()): String {
    val days = days(today)
    return when {
        days.start == days.endInclusive -> days.start.format(DAY)
        days.start.year == days.endInclusive.year -> "${days.start.format(DAY_SHORT)} – ${days.endInclusive.format(DAY)}"
        else -> "${days.start.format(DAY)} – ${days.endInclusive.format(DAY)}"
    }
}

/**
 * What every report is filtered by, at the top of its screen: the period, which opens a sheet, and
 * where the sales were made. [showSource] is false on a report the source does not divide.
 */
@Composable
fun ReportFilterBar(
    filter: ReportFilter,
    onChange: (ReportFilter) -> Unit,
    modifier: Modifier = Modifier,
    showSource: Boolean = true,
) {
    var sheet by remember { mutableStateOf(false) }

    Column(modifier.padding(horizontal = DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DsShapes.medium)
                .border(1.dp, DsColors.Border, DsShapes.medium)
                .clickable(role = Role.Button, onClickLabel = "Changer la période") { sheet = true }
                .padding(horizontal = DsSpacing.md, vertical = DsSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text(filter.period.label, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Spacer(Modifier.width(DsSpacing.sm))
            Text(filter.daysText(), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DsColors.TextSecondary)
        }

        if (showSource) {
            SourceSelector(filter.source) { onChange(filter.copy(source = it)) }
        }
    }

    if (sheet) {
        PeriodSheet(
            filter = filter,
            onApply = { onChange(it); sheet = false },
            onDismiss = { sheet = false },
        )
    }
}

/** Tout · Dépôt · Camion, as one segmented control. */
@Composable
private fun SourceSelector(selected: ReportSource, onSelect: (ReportSource) -> Unit) =
    ReportSegmented(ReportSource.entries, selected, { it.label }, onSelect)

/**
 * A row of mutually exclusive choices in a grey pill, the chosen one raised in white — the control a
 * report uses to switch what it looks at (Tout · Dépôt · Camion, Clients · Fournisseurs).
 */
@Composable
fun <T> ReportSegmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(DsShapes.pill)
            .background(DsColors.SurfaceSunken)
            .padding(3.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Text(
                label(option),
                fontSize = DsTextSize.bodySmall,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                color = if (on) DsColors.Primary else DsColors.TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(DsShapes.pill)
                    .background(if (on) DsColors.Surface else DsColors.SurfaceSunken)
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .padding(vertical = DsSpacing.sm),
            )
        }
    }
}

/**
 * The period, picked from the presets or as two days. Edits a copy and applies it with the button,
 * so the report behind does not reload at every tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSheet(filter: ReportFilter, onApply: (ReportFilter) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(filter) }
    var picking by remember { mutableStateOf<Boolean?>(null) }   // true: the start, false: the end
    val today = LocalDate.now()

    picking?.let { start ->
        val days = draft.days(today)
        DayPickerDialog(
            day = if (start) days.start else days.endInclusive,
            onPicked = { day ->
                draft = if (start) draft.copy(customFrom = day, customTo = draft.customTo ?: day)
                else draft.copy(customFrom = draft.customFrom ?: day, customTo = day)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DsColors.Surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DsSpacing.lg)
                .navigationBarsPadding()
        ) {
            Text("Période", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            Spacer(Modifier.height(DsSpacing.md))

            ReportPeriod.entries.forEach { period ->
                val on = draft.period == period
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.medium)
                        .background(if (on) DsColors.PrimaryLight else DsColors.Surface)
                        .clickable(role = Role.RadioButton) {
                            draft = if (period == ReportPeriod.PERSONNALISE && draft.period != period) {
                                // Starts from the days on screen, so the user adjusts rather than re-enters them.
                                val days = draft.days(today)
                                draft.copy(period = period, customFrom = days.start, customTo = days.endInclusive)
                            } else draft.copy(period = period)
                        }
                        .padding(horizontal = DsSpacing.md, vertical = DsSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            period.label, fontSize = DsTextSize.body,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) DsColors.Primary else DsColors.TextPrimary,
                        )
                        if (period != ReportPeriod.PERSONNALISE) {
                            Text(
                                ReportFilter(period).daysText(today),
                                fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
                            )
                        }
                    }
                    if (on) Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                }
            }

            if (draft.period == ReportPeriod.PERSONNALISE) {
                val days = draft.days(today)
                Spacer(Modifier.height(DsSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    DayField("Du", days.start, Modifier.weight(1f)) { picking = true }
                    DayField("Au", days.endInclusive, Modifier.weight(1f)) { picking = false }
                }
            }

            Spacer(Modifier.height(DsSpacing.lg))
            Button(
                onClick = { onApply(draft) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = DsShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary),
            ) { Text("Appliquer", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(DsSpacing.lg))
        }
    }
}

@Composable
private fun DayField(label: String, day: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm)
    ) {
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        Text(day.format(DAY), fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
    }
}

/** The Material picker works in UTC milliseconds; the day tapped is read back in UTC (timestamps.md rule 5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(day: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    ) { DatePicker(state = state) }
}
