package com.distrigo.app.ui.mouvements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * What the Mouvements list is narrowed by, as a bottom sheet — the same shape as the price history's,
 * so a filter behaves the same wherever it is met.
 *
 * ### The draft
 *
 * The sheet edits a copy and applies it on confirmation, so a half-made choice never moves the list
 * behind it. The button says how many movements the draft would leave, asked of the database, and
 * refuses a period that ends before it starts rather than quietly returning nothing.
 *
 * ### The party, in two steps
 *
 * A movement is with a client or with a supplier, never both: the document behind it says which. So
 * the first step picks the kind, and only then does the list of that kind appear — and only those
 * this product actually moved with. Changing the kind drops the chosen one, an id from the other
 * book meaning nothing here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MovementFiltersSheet(
    filters   : MovementFilters,
    clients   : List<PartyOption>,
    suppliers : List<PartyOption>,
    countOf   : suspend (MovementFilters) -> Int,
    onApply   : (MovementFilters) -> Unit,
    onDismiss : () -> Unit,
) {
    var draft by remember { mutableStateOf(filters) }
    var showFrom by remember { mutableStateOf(false) }
    var showTo   by remember { mutableStateOf(false) }
    var partyMenu by remember { mutableStateOf(false) }

    // Counted in the database on every change of the draft; the list on screen holds the applied
    // filters, not this one.
    var count by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(draft) {
        count = if (draft.periodIsBackwards) null else countOf(draft)
    }

    if (showFrom) {
        DayPickerDialog(draft.dateFrom, onPicked = { draft = draft.copy(dateFrom = it); showFrom = false }) { showFrom = false }
    }
    if (showTo) {
        DayPickerDialog(draft.dateTo, onPicked = { draft = draft.copy(dateTo = it); showTo = false }) { showTo = false }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DsColors.Surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = DsSpacing.lg)
                .padding(bottom = DsSpacing.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filtres", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = { draft = MovementFilters() }) {
                    Text("Réinitialiser", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Période ──
            FilterGroup("Période") {
                DayField("Du", draft.dateFrom, Modifier.weight(1f)) { showFrom = true }
                DayField("Au", draft.dateTo, Modifier.weight(1f)) { showTo = true }
            }
            if (draft.periodIsBackwards) {
                Text(
                    "La date « Du » est après la date « Au ».",
                    fontSize = DsTextSize.caption, color = DsColors.Danger,
                    modifier = Modifier.padding(top = DsSpacing.xs)
                )
            }

            // ── Sens ──
            FilterGroupWrapped("Sens") {
                FilterPill("Tous", draft.direction == null) { draft = draft.copy(direction = null) }
                FilterPill("Entrées", draft.direction == "entree") { draft = draft.copy(direction = "entree") }
                FilterPill("Sorties", draft.direction == "sortie") { draft = draft.copy(direction = "sortie") }
            }

            // ── Type de mouvement ──
            FilterGroupWrapped("Type de mouvement") {
                FilterPill("Tous", draft.types.isEmpty()) { draft = draft.copy(types = emptySet()) }
                MovementType.entries.forEach { type ->
                    FilterPill(type.label, type in draft.types) {
                        // Several kinds at once: a ledger question is usually "achats et retours",
                        // not one kind alone.
                        val next = if (type in draft.types) draft.types - type else draft.types + type
                        draft = draft.copy(types = next)
                    }
                }
            }

            // ── Emplacement ──
            FilterGroupWrapped("Emplacement") {
                FilterPill("Tous", draft.emplacement == null) { draft = draft.copy(emplacement = null) }
                FilterPill("Dépôt", draft.emplacement == "depot") { draft = draft.copy(emplacement = "depot") }
                FilterPill("Camion", draft.emplacement == "camion") { draft = draft.copy(emplacement = "camion") }
            }

            // ── Avec qui ──
            FilterGroupWrapped("Avec") {
                FilterPill("Tous", draft.party == null) { draft = draft.copy(party = null, partyId = null) }
                MovementParty.entries.forEach { party ->
                    FilterPill(party.label, draft.party == party) {
                        // The chosen party goes with the kind: a client's id means nothing among suppliers.
                        draft = draft.copy(party = party, partyId = null)
                    }
                }
            }

            draft.party?.let { party ->
                val options = if (party == MovementParty.CLIENT) clients else suppliers
                val chosen  = options.firstOrNull { it.id == draft.partyId }
                val empty   = if (party == MovementParty.CLIENT) "Aucun client pour ce produit" else "Aucun fournisseur pour ce produit"
                Box(Modifier.fillMaxWidth().padding(top = DsSpacing.xs)) {
                    FieldBox(
                        text    = chosen?.name ?: if (options.isEmpty()) empty else "Tous les ${party.label.lowercase()}s",
                        muted   = chosen == null,
                        enabled = options.isNotEmpty(),
                        trailing = Icons.Default.KeyboardArrowDown,
                        onClick = { partyMenu = true }
                    )
                    DropdownMenu(
                        expanded         = partyMenu,
                        onDismissRequest = { partyMenu = false },
                        modifier         = Modifier.fillMaxWidth(0.85f)
                    ) {
                        DropdownMenuItem(
                            text    = { Text("Tous les ${party.label.lowercase()}s") },
                            onClick = { draft = draft.copy(partyId = null); partyMenu = false }
                        )
                        options.forEach { option ->
                            DropdownMenuItem(
                                text    = { Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { draft = draft.copy(partyId = option.id); partyMenu = false }
                            )
                        }
                    }
                }
            }

            Button(
                onClick  = { onApply(draft) },
                enabled  = !draft.periodIsBackwards && count != 0,
                modifier = Modifier.fillMaxWidth().padding(top = DsSpacing.lg).height(48.dp),
                shape    = DsShapes.medium,
                colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary, contentColor = DsColors.Surface)
            ) {
                Text(
                    when {
                        draft.periodIsBackwards -> "Période invalide"
                        count == null           -> "Afficher les mouvements"
                        count == 0              -> "Aucun résultat"
                        else                    -> "Afficher $count mouvement" + if (count!! > 1) "s" else ""
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Pieces ───────────────────────────────────────────────────────────────────

@Composable
private fun FilterGroup(title: String, content: @Composable RowScope.() -> Unit) {
    Text(title, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, modifier = Modifier.padding(top = DsSpacing.md, bottom = DsSpacing.xs))
    Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm), content = content)
}

/** A group whose chips wrap, so a long label moves down a line instead of being squeezed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroupWrapped(title: String, content: @Composable () -> Unit) {
    Text(title, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, modifier = Modifier.padding(top = DsSpacing.md, bottom = DsSpacing.xs))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs),
        verticalArrangement   = Arrangement.spacedBy(DsSpacing.xs)
    ) { content() }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold,
        color = if (selected) DsColors.Primary else DsColors.TextSecondary,
        modifier = Modifier
            .clip(DsShapes.pill)
            .background(if (selected) DsColors.PrimaryLight else DsColors.Surface)
            .border(1.dp, if (selected) DsColors.Primary else DsColors.Border, DsShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

/**
 * A day, picked from a calendar.
 *
 * A real clickable box rather than a disabled text field: a disabled field does not reliably pass a
 * click on, and is skipped by accessibility altogether — which is how the old screen's date fields
 * and source dropdown were built.
 */
@Composable
private fun DayField(label: String, day: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier) {
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 2.dp))
        FieldBox(
            text     = day?.let { readableDay(it) } ?: "Choisir",
            muted    = day == null,
            enabled  = true,
            trailing = Icons.Default.CalendarMonth,
            onClick  = onClick
        )
    }
}

@Composable
private fun FieldBox(
    text     : String,
    muted    : Boolean,
    enabled  : Boolean,
    trailing : androidx.compose.ui.graphics.vector.ImageVector,
    onClick  : () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = DsSpacing.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            fontSize = DsTextSize.body,
            color    = if (muted) DsColors.TextSecondary else DsColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(trailing, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(day: String?, onPicked: (String) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = day?.let {
            runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    ) { DatePicker(state = state) }
}

/** `2026-09-21` as `21/09/2026`, the way every other date on this screen reads. */
private fun readableDay(day: String): String =
    runCatching { LocalDate.parse(day).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(day)
