package com.distrigo.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

// ═══════════════════════════════════════════════════════════════════════════
// FILTER MODEL
//
// Shared by both Brouillons screens. What differs between Achats and Dépôt Vente is only the
// wording ("Fournisseur" vs "Client") and which facts a given draft reports — never the shape of
// the filter, so the shape lives here once.
// ═══════════════════════════════════════════════════════════════════════════

/** Whether a draft creates a new record or edits an existing one. */
enum class DraftTypeFilter(val label: String) {
    ALL ("Tous"),
    NEW ("Nouveaux"),
    EDIT("Modifications")
}

/** Whether the record a draft edits can still receive it. */
enum class DraftStateFilter(val label: String) {
    ALL     ("Tous"),
    ACTIVE  ("Actifs"),
    BLOCKED ("Obsolètes")
}

/**
 * The three facts a draft has to report for the filter to judge it.
 *
 * Kept as a plain snapshot rather than a lambda per axis so a screen states its mapping once, and
 * so adding a fourth axis is a change in one place instead of at both call sites.
 */
data class DraftFilterFacts(
    val isEdit   : Boolean,
    val blocked  : Boolean,
    val partyName: String?
)

/**
 * The filter as the user has set it. Immutable, and cheap enough to recompute the visible list
 * from on every change.
 *
 * [partyName] filters by name rather than by id because that is what a draft actually stores — a
 * draft's supplier or client may have been renamed, or the draft may carry no id at all before its
 * first step is complete, and the name is the thing the card shows either way.
 */
data class DraftFilterState(
    val type     : DraftTypeFilter  = DraftTypeFilter.ALL,
    val state    : DraftStateFilter = DraftStateFilter.ALL,
    val partyName: String?          = null
) {
    val activeCount: Int
        get() = listOf(
            type      != DraftTypeFilter.ALL,
            state     != DraftStateFilter.ALL,
            partyName != null
        ).count { it }

    val isActive: Boolean get() = activeCount > 0

    fun accepts(facts: DraftFilterFacts): Boolean {
        val typeOk = when (type) {
            DraftTypeFilter.ALL  -> true
            DraftTypeFilter.NEW  -> !facts.isEdit
            DraftTypeFilter.EDIT -> facts.isEdit
        }
        val stateOk = when (state) {
            DraftStateFilter.ALL     -> true
            DraftStateFilter.ACTIVE  -> !facts.blocked
            DraftStateFilter.BLOCKED -> facts.blocked
        }
        return typeOk && stateOk && (partyName == null || facts.partyName == partyName)
    }

    companion object {
        /**
         * Survives rotation and process death. A `data class` is not `Parcelable`, and the default
         * `autoSaver` cannot put one in a `Bundle`, so the three fields go across as strings.
         */
        val Saver: Saver<DraftFilterState, Any> = listSaver(
            save    = { listOf(it.type.name, it.state.name, it.partyName ?: "") },
            restore = {
                DraftFilterState(
                    type      = DraftTypeFilter.valueOf(it[0]),
                    state     = DraftStateFilter.valueOf(it[1]),
                    partyName = it[2].ifEmpty { null }
                )
            }
        )
    }
}

/** The words that differ per flow. */
data class DraftFilterCopy(
    /** "Fournisseur" / "Client". */
    val partyLabel: String,
    /** "Tous les fournisseurs" / "Tous les clients". */
    val partyAll  : String
)

// ═══════════════════════════════════════════════════════════════════════════
// FILTER BAR
// ═══════════════════════════════════════════════════════════════════════════

/**
 * The control strip under the top bar: filter on the left, selection mode on the right.
 *
 * Modelled on the chip row the Achats and Ventes lists already carry, so the two screens read as
 * the same family — same `SurfaceSunken` pill, same dot when a filter is on, same "Filtres ▾"
 * wording. The strip is hidden entirely while selecting, because none of it applies then.
 */
@Composable
fun DraftFilterBar(
    filter          : DraftFilterState,
    shownCount      : Int,
    totalCount      : Int,
    onOpenFilters   : () -> Unit,
    onClearFilters  : () -> Unit,
    onEnterSelection: () -> Unit,
    modifier        : Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DsColors.Surface)
            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .clip(DsShapes.medium)
                    .background(DsColors.SurfaceSunken)
                    .clickable(onClick = onOpenFilters)
                    .padding(horizontal = DsSpacing.sm, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tint = if (filter.isActive) DsColors.Primary else DsColors.TextSecondary
                Icon(Icons.Default.FilterList, "Filtres", tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Filtres", fontSize = DsTextSize.caption, color = tint)
                Icon(Icons.Default.KeyboardArrowDown, null, tint = tint, modifier = Modifier.size(14.dp))
            }
            if (filter.isActive) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(6.dp)
                        .clip(DsShapes.pill)
                        .background(DsColors.Primary)
                )
            }
        }

        // Says what the filter did, so a short list never reads as "the drafts are gone".
        if (filter.isActive) {
            Text(
                "$shownCount sur $totalCount",
                fontSize = DsTextSize.caption,
                color    = DsColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Réinitialiser",
                fontSize   = DsTextSize.caption,
                fontWeight = FontWeight.SemiBold,
                color      = DsColors.Primary,
                modifier   = Modifier
                    .clip(DsShapes.pill)
                    .clickable(onClick = onClearFilters)
                    .padding(horizontal = DsSpacing.xs, vertical = 2.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            "Sélectionner",
            fontSize   = DsTextSize.caption,
            fontWeight = FontWeight.SemiBold,
            color      = DsColors.Primary,
            modifier   = Modifier
                .clip(DsShapes.medium)
                .clickable(onClick = onEnterSelection)
                .padding(horizontal = DsSpacing.sm, vertical = 6.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// FILTER SHEET
// ═══════════════════════════════════════════════════════════════════════════

/**
 * "Filtres avancés", in the same shape the Achats and Ventes lists use: segmented rows for the
 * closed sets, a dropdown for the open one, and Réinitialiser / Appliquer at the foot.
 *
 * The Appliquer button carries the resulting count, so the effect of a choice is visible before
 * the sheet closes rather than after.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftFilterSheet(
    filter     : DraftFilterState,
    onChange   : (DraftFilterState) -> Unit,
    onReset    : () -> Unit,
    onDismiss  : () -> Unit,
    partyNames : List<String>,
    matchCount : Int,
    copy       : DraftFilterCopy,
    /**
     * Whether "Type de brouillon" and "État" are worth offering.
     *
     * False for a flow with no source record to edit and nothing that can block a resume: every
     * draft in such a list is new and active, so both axes would return either everything or
     * nothing, whatever the user picked. A control that cannot partition its list is not a filter,
     * it is a trap — Tournée Vente hides them and keeps the party axis, which does partition.
     */
    showRecordAxes : Boolean = true
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var partyExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = DsColors.Surface
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = DsSpacing.lg, end = DsSpacing.lg, bottom = DsSpacing.xxxl)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Filtres avancés",
                    fontWeight = FontWeight.Bold,
                    fontSize   = DsTextSize.bodyLarge,
                    color      = DsColors.TextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Fermer", tint = DsColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(DsSpacing.md))

            if (showRecordAxes) {
                FilterFieldLabel("Type de brouillon")
                SegmentedRow(
                    options  = DraftTypeFilter.entries.map { it to it.label },
                    selected = filter.type,
                    onSelect = { onChange(filter.copy(type = it)) }
                )
                Spacer(Modifier.height(DsSpacing.md))

                FilterFieldLabel("État")
                SegmentedRow(
                    options  = DraftStateFilter.entries.map { it to it.label },
                    selected = filter.state,
                    onSelect = { onChange(filter.copy(state = it)) }
                )
                Spacer(Modifier.height(DsSpacing.md))
            }

            // Only the names actually present among the drafts are offered — a picker listing every
            // supplier in the database would be mostly dead options that filter to nothing.
            FilterFieldLabel(copy.partyLabel)
            ExposedDropdownMenuBox(
                expanded         = partyExpanded,
                onExpandedChange = { partyExpanded = it }
            ) {
                OutlinedTextField(
                    value         = filter.partyName ?: copy.partyAll,
                    onValueChange = {},
                    readOnly      = true,
                    singleLine    = true,
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partyExpanded) },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape         = DsShapes.medium,
                    colors        = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary,
                        unfocusedTextColor   = DsColors.TextPrimary,
                        focusedTextColor     = DsColors.TextPrimary
                    )
                )
                ExposedDropdownMenu(
                    expanded         = partyExpanded,
                    onDismissRequest = { partyExpanded = false }
                ) {
                    DropdownMenuItem(
                        text    = { Text(copy.partyAll, color = DsColors.TextSecondary) },
                        onClick = { onChange(filter.copy(partyName = null)); partyExpanded = false }
                    )
                    partyNames.forEach { name ->
                        DropdownMenuItem(
                            text    = { Text(bidiIsolate(name), color = DsColors.TextPrimary) },
                            onClick = { onChange(filter.copy(partyName = name)); partyExpanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(DsSpacing.lg))

            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                OutlinedButton(
                    onClick  = onReset,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.TextPrimary),
                    border   = BorderStroke(1.dp, DsColors.Border)
                ) {
                    Text("Réinitialiser", fontSize = DsTextSize.body, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick  = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    Text(
                        "Appliquer ($matchCount)",
                        fontSize   = DsTextSize.body,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterFieldLabel(text: String) = Text(
    text,
    fontSize = DsTextSize.bodySmall,
    color    = DsColors.TextSecondary,
    modifier = Modifier.padding(bottom = DsSpacing.xs)
)

/** The segmented control the other filter sheets draw by hand, extracted so all three agree. */
@Composable
private fun <T> SegmentedRow(
    options : List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(DsShapes.medium)
                    .background(if (active) DsColors.Primary else DsColors.Surface)
                    .border(1.dp, if (active) DsColors.Primary else DsColors.Border, DsShapes.medium)
                    .clickable { onSelect(value) }
                    .padding(vertical = DsSpacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (active) Color.White else DsColors.TextPrimary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
            }
        }
    }
}
