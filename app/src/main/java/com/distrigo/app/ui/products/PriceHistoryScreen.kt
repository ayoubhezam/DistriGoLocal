package com.distrigo.app.ui.products

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.data.model.PriceMovement
import com.distrigo.app.data.model.PriceMovementKind
import com.distrigo.app.ui.designsystem.*
import com.distrigo.app.ui.suppliers.formatDZD
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Every price a product changed hands at: what was paid for it and what it sold for, side by side.
 *
 * One period governs the whole screen — the summaries, the chart and the list agree by construction,
 * which is why the period chips above the chart and the sheet's own « Période » are the same state.
 * The period also lays the chart's axis out: seven days, four weeks or twelve months; see
 * [PricePeriod.slotsOn]. The screen looks back a year at most, which is as far as a price is worth
 * comparing against and keeps every read bounded.
 */
@Composable
fun PriceHistoryScreen(
    productName : String,
    movements   : List<PriceMovement>,
    isLoading   : Boolean,
    error       : String?,
    filters     : PriceHistoryFilters,
    onFilters   : (PriceHistoryFilters) -> Unit,
    onRetry     : () -> Unit,
    onBack      : () -> Unit,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var sheetOpen  by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    BackHandler(enabled = searchOpen) { searchOpen = false; onFilters(filters.copy(query = "")) }

    // Counted with every filter but the segment itself, so the counts beside each segment say what
    // that segment would show.
    val overall = remember(movements, filters) { movements.narrow(filters, includeKind = false) }
    val shown   = remember(movements, filters) { movements.narrow(filters) }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title    = "Historique des prix",
            subtitle = productName,
            leading  = DsTopBarLeading.Back(onBack)
        ) {
            BarAction(Icons.Default.Search, "Rechercher", active = searchOpen) {
                searchOpen = !searchOpen
                if (!searchOpen) onFilters(filters.copy(query = ""))
            }
            Spacer(Modifier.width(DsSpacing.sm))
            BarAction(Icons.Default.Tune, "Filtrer", active = filters.activeCount > 0, badge = filters.activeCount) {
                sheetOpen = true
            }
            Spacer(Modifier.width(DsSpacing.md))
        }

        if (error != null) {
            EmptyState(Icons.Default.ErrorOutline, "Chargement impossible", error, "Réessayer", onRetry)
            return@Column
        }
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DsColors.Primary) }
            return@Column
        }
        if (movements.isEmpty()) {
            EmptyState(
                Icons.Default.History, "Aucun mouvement de prix",
                "Les prix d'achat et de vente apparaîtront ici après les premières opérations."
            )
            return@Column
        }

        if (searchOpen) {
            LaunchedEffect(Unit) { focus.requestFocus() }
            OutlinedTextField(
                value         = filters.query,
                onValueChange = { onFilters(filters.copy(query = it)) },
                placeholder   = { Text("Rechercher un fournisseur ou un client", fontSize = DsTextSize.body) },
                singleLine    = true,
                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, tint = DsColors.TextSecondary) },
                trailingIcon  = {
                    if (filters.query.isNotEmpty()) {
                        IconButton(onClick = { onFilters(filters.copy(query = "")) }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer", tint = DsColors.TextSecondary)
                        }
                    }
                },
                modifier        = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.md, vertical = DsSpacing.xs).focusRequester(focus),
                shape           = DsShapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searchOpen = true }),
                colors          = dsTextFieldColors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary)
            )
        }

        KindSegments(
            filters = filters,
            counts  = mapOf(
                null to overall.size,
                PriceMovementKind.ACHAT to overall.count { it.kind == PriceMovementKind.ACHAT },
                PriceMovementKind.VENTE to overall.count { it.kind == PriceMovementKind.VENTE },
            ),
            onKind  = { onFilters(filters.copy(kind = it)) }
        )

        ActiveChips(filters, onFilters)

        if (shown.isEmpty()) {
            EmptyState(
                Icons.Default.FilterAltOff, "Aucun résultat",
                "Aucun mouvement sur cette période, ou aucun ne correspond à ces filtres.",
                "Réinitialiser les filtres"
            ) { onFilters(PriceHistoryFilters(kind = filters.kind)) }
            return@Column
        }

        val kinds = PriceMovementKind.entries.filter { filters.kind == null || filters.kind == it }
        val today  = remember { LocalDate.now() }
        val slots  = remember(filters.period, today) { filters.period.slotsOn(today) }
        val series = remember(shown, filters.period, today) { shown.chartSeries(filters.period, today) }

        LazyColumn(
            modifier            = Modifier.weight(1f).fillMaxWidth(),
            contentPadding      = PaddingValues(DsSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    kinds.forEach { kind ->
                        SummaryBlock(kind, shown.statsOf(kind), Modifier.weight(1f))
                    }
                }
            }
            if (filters.kind == null) {
                val achat = shown.statsOf(PriceMovementKind.ACHAT)
                val vente = shown.statsOf(PriceMovementKind.VENTE)
                if (achat != null && vente != null) {
                    item { MarginRow(vente.last - achat.last) }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface)
                        .border(1.dp, DsColors.Border, DsShapes.large).padding(DsSpacing.md)
                ) {
                    PeriodChips(filters.period) { onFilters(filters.copy(period = it)) }
                    Spacer(Modifier.height(DsSpacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                        kinds.filter { kind -> shown.any { it.kind == kind } }.forEach { kind ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(DsShapes.pill).background(kind.chartColor()))
                                Spacer(Modifier.width(5.dp))
                                Text(kind.label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                            }
                        }
                    }
                    PriceChart(series, filters.period, slots, Modifier.padding(top = DsSpacing.xs))
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = DsSpacing.sm)) {
                    Text("Mouvements", fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary)
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text(
                        shown.size.toString(),
                        fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary,
                        modifier = Modifier.clip(DsShapes.pill).background(DsColors.SurfaceSunken).padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Grouped by month only when the list runs in date order; by price, a month heading
            // would break the very order the user asked for.
            val grouped = filters.sort == PriceSort.RECENT || filters.sort == PriceSort.OLDEST
            var currentMonth: String? = null
            shown.forEach { movement ->
                val month = movement.day().monthLabel()
                if (grouped && month != currentMonth) {
                    currentMonth = month
                    item(key = "month-$month") {
                        Text(
                            month,
                            fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary,
                            modifier = Modifier.padding(top = DsSpacing.xs)
                        )
                    }
                }
                item(key = "${movement.kind}-${movement.documentId}-${movement.unitPrice}") {
                    MovementRow(movement)
                }
            }
        }
    }

    if (sheetOpen) {
        FilterSheet(
            filters   = filters,
            resultsOf = { candidate -> movements.narrow(candidate).size },
            onApply   = { onFilters(it); sheetOpen = false },
            onDismiss = { sheetOpen = false }
        )
    }
}

// ── Pieces ───────────────────────────────────────────────────────────────────

@Composable
private fun BarAction(icon: ImageVector, description: String, active: Boolean, badge: Int = 0, onClick: () -> Unit) {
    Box {
        IconButton(
            onClick  = onClick,
            modifier = Modifier.clip(DsShapes.medium).background(if (active) DsColors.Primary else DsColors.PrimaryLight)
        ) {
            Icon(icon, contentDescription = description, tint = if (active) DsColors.Surface else DsColors.Primary)
        }
        if (badge > 0) {
            Text(
                badge.toString(),
                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DsColors.Surface,
                modifier = Modifier.align(Alignment.TopEnd).clip(DsShapes.pill).background(DsColors.Danger)
                    .padding(horizontal = 5.dp)
            )
        }
    }
}

@Composable
private fun KindSegments(
    filters : PriceHistoryFilters,
    counts  : Map<PriceMovementKind?, Int>,
    onKind  : (PriceMovementKind?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DsSpacing.md, vertical = DsSpacing.xs)
            .clip(DsShapes.medium).background(DsColors.SurfaceSunken).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(null to "Tous", PriceMovementKind.ACHAT to "Achat", PriceMovementKind.VENTE to "Vente").forEach { (kind, label) ->
            val selected = filters.kind == kind
            Row(
                modifier = Modifier.weight(1f).clip(DsShapes.small)
                    .background(if (selected) DsColors.Primary else Color.Transparent)
                    .clickable { onKind(kind) }
                    .padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold,
                    color = if (selected) DsColors.Surface else DsColors.TextSecondary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    (counts[kind] ?: 0).toString(),
                    fontSize = DsTextSize.caption,
                    color = if (selected) DsColors.Surface.copy(alpha = 0.85f) else DsColors.TextTertiary
                )
            }
        }
    }
}

@Composable
private fun ActiveChips(filters: PriceHistoryFilters, onFilters: (PriceHistoryFilters) -> Unit) {
    val chips = buildList {
        if (filters.period != PriceHistoryFilters().period) add(filters.period.label to filters.copy(period = PriceHistoryFilters().period))
        if (filters.variation != PriceVariation.ALL) add(filters.variation.label to filters.copy(variation = PriceVariation.ALL))
        if (filters.sort != PriceSort.RECENT) add(filters.sort.label to filters.copy(sort = PriceSort.RECENT))
        if (filters.query.isNotBlank()) add("« ${filters.query.trim()} »" to filters.copy(query = ""))
    }
    if (chips.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = DsSpacing.md, vertical = DsSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
    ) {
        chips.forEach { (label, without) ->
            Row(
                modifier = Modifier.clip(DsShapes.pill).background(DsColors.PrimaryLight)
                    .clickable { onFilters(without) }.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.Close, contentDescription = "Retirer le filtre", tint = DsColors.Primary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun PeriodChips(selected: PricePeriod, onSelect: (PricePeriod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)) {
        PricePeriod.entries.forEach { period ->
            val on = period == selected
            Text(
                period.label,
                fontSize   = DsTextSize.caption,
                fontWeight = FontWeight.SemiBold,
                color      = if (on) DsColors.Surface else DsColors.TextSecondary,
                modifier   = Modifier.clip(DsShapes.pill)
                    .background(if (on) DsColors.Primary else DsColors.SurfaceSunken)
                    .clickable { onSelect(period) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SummaryBlock(kind: PriceMovementKind, stats: PriceStats?, modifier: Modifier = Modifier) {
    val colour = kind.chartColor()
    Column(
        modifier.clip(DsShapes.large)
            .background(if (kind == PriceMovementKind.ACHAT) DsColors.SuccessLight else DsColors.PrimaryLight)
            .border(1.dp, colour.copy(alpha = 0.2f), DsShapes.large)
            .padding(DsSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(DsShapes.pill).background(colour), contentAlignment = Alignment.Center) {
                Icon(
                    if (kind == PriceMovementKind.ACHAT) Icons.Default.ShoppingCart else Icons.Default.LocalOffer,
                    contentDescription = null, tint = DsColors.Surface, modifier = Modifier.size(13.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                if (kind == PriceMovementKind.ACHAT) "Prix d'achat" else "Prix de vente",
                fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary
            )
        }
        Text("Dernier prix", fontSize = DsTextSize.caption, color = DsColors.TextSecondary, modifier = Modifier.padding(top = DsSpacing.sm))
        Text(
            stats?.let { "${formatDZD(it.last)} DA" } ?: "—",
            fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = colour
        )
        if (stats == null) {
            Text("Aucune donnée", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        } else {
            SummaryLine("Moyen", "${formatDZD(stats.average)} DA")
            SummaryLine("Min", "${formatDZD(stats.min)} DA")
            SummaryLine("Max", "${formatDZD(stats.max)} DA")
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        Text(value, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
    }
}

@Composable
private fun MarginRow(gap: Double) {
    Row(
        Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.medium).padding(horizontal = DsSpacing.md, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text("Écart vente − achat", fontSize = DsTextSize.body, color = DsColors.TextSecondary)
        Text(
            (if (gap > 0) "+" else if (gap < 0) "−" else "") + "${formatDZD(abs(gap))} DA",
            fontSize = DsTextSize.body, fontWeight = FontWeight.Bold,
            color = if (gap < 0) DsColors.Danger else DsColors.TextPrimary
        )
    }
}

/**
 * One price, and the document it was agreed in.
 *
 * The document is named rather than linked: each tab hosts its own navigation graph, so a bon in
 * Achats cannot be opened from inside Produits — the same reason a stock movement names its source
 * without opening it.
 */
@Composable
private fun MovementRow(movement: PriceMovement) {
    val achat  = movement.kind == PriceMovementKind.ACHAT
    val colour = movement.kind.chartColor()
    Row(
        Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .padding(horizontal = DsSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).clip(DsShapes.pill)
                .background(if (achat) DsColors.SuccessLight else DsColors.PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (achat) Icons.Default.ShoppingCart else Icons.Default.LocalOffer,
                contentDescription = null, tint = colour, modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(movement.party, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row {
                Text(movement.kind.label, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = colour)
                Text(
                    " · ${DateTimeFormatter.ofPattern("dd/MM/yyyy").format(movement.day())} · qté ${formatQty(movement.quantity)}",
                    fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                movement.documentLabel,
                fontSize = DsTextSize.caption, color = DsColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${formatDZD(movement.unitPrice)} DA", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            DeltaPill(movement)
        }
    }
}

/**
 * The change from the previous movement of the same kind, coloured by what it means for the
 * business: a cheaper purchase is good, a dearer sale is good.
 */
@Composable
private fun DeltaPill(movement: PriceMovement) {
    val delta = movement.delta
    val good  = delta != null && (if (movement.kind == PriceMovementKind.ACHAT) delta < 0 else delta > 0)
    val label = when {
        delta == null        -> if (movement.kind == PriceMovementKind.ACHAT) "Premier achat" else "Première vente"
        abs(delta) < 0.005   -> "Stable"
        delta > 0            -> "+${formatDZD(delta)} DA"
        else                 -> "−${formatDZD(-delta)} DA"
    }
    val neutral = delta == null || abs(delta) < 0.005
    Text(
        label,
        fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold,
        color = when {
            neutral -> DsColors.TextSecondary
            good    -> DsColors.Success
            else    -> DsColors.Danger
        },
        modifier = Modifier.padding(top = 2.dp).clip(DsShapes.pill).background(
            when {
                neutral -> DsColors.SurfaceSunken
                good    -> DsColors.SuccessLight
                else    -> DsColors.DangerLight
            }
        ).padding(horizontal = 7.dp, vertical = 1.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filters   : PriceHistoryFilters,
    resultsOf : (PriceHistoryFilters) -> Int,
    onApply   : (PriceHistoryFilters) -> Unit,
    onDismiss : () -> Unit,
) {
    // Edited in the sheet and applied on confirmation, so a half-made choice never moves the list.
    var draft by remember { mutableStateOf(filters) }
    // Fully expanded, with no half-way stop, as the Mouvements sheet is: a filter sheet that
    // re-settles while being used moves the control out from under the finger.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = DsColors.Surface
    ) {
        // The wrapped « Trier par » chips make the sheet tall enough to reach the navigation bar,
        // so the content clears it and scrolls rather than pushing its own button off the screen.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = DsSpacing.lg)
                .padding(bottom = DsSpacing.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filtres", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = { draft = PriceHistoryFilters(kind = draft.kind, query = draft.query) }) {
                    Text("Réinitialiser", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }
            SheetGroup("Période") {
                PricePeriod.entries.forEach { SheetChip(it.label, draft.period == it) { draft = draft.copy(period = it) } }
            }
            SheetGroup("Variation") {
                PriceVariation.entries.forEach { SheetChip(it.label, draft.variation == it) { draft = draft.copy(variation = it) } }
            }
            SheetGroup("Trier par") {
                PriceSort.entries.forEach { SheetChip(it.label, draft.sort == it) { draft = draft.copy(sort = it) } }
            }
            val results = resultsOf(draft)
            Button(
                onClick  = { onApply(draft) },
                enabled  = results > 0,
                modifier = Modifier.fillMaxWidth().padding(top = DsSpacing.lg).height(48.dp),
                shape    = DsShapes.medium,
                colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary, contentColor = DsColors.Surface)
            ) {
                Text(
                    if (results == 0) "Aucun résultat" else "Afficher $results résultat" + if (results > 1) "s" else "",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetGroup(title: String, content: @Composable () -> Unit) {
    Text(title, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, modifier = Modifier.padding(top = DsSpacing.md, bottom = DsSpacing.xs))
    // Wrapping, not a single row: « Trier par » holds four labels as long as « Prix décroissant »,
    // which a row fits by squeezing the last chip out of shape instead of moving it down a line.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs),
        verticalArrangement   = Arrangement.spacedBy(DsSpacing.xs)
    ) { content() }
}

@Composable
private fun SheetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold,
        color = if (selected) DsColors.Primary else DsColors.TextSecondary,
        modifier = Modifier.clip(DsShapes.pill)
            .background(if (selected) DsColors.PrimaryLight else DsColors.Surface)
            .border(1.dp, if (selected) DsColors.Primary else DsColors.Border, DsShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun EmptyState(
    icon: ImageVector, title: String, message: String,
    action: String? = null, onAction: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(DsSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(56.dp).clip(DsShapes.pill).background(DsColors.PrimaryLight), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(28.dp))
        }
        Text(title, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, modifier = Modifier.padding(top = DsSpacing.md))
        Text(message, fontSize = DsTextSize.body, color = DsColors.TextSecondary)
        if (action != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = DsSpacing.md),
                shape = DsShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary, contentColor = DsColors.Surface)
            ) { Text(action, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** « Septembre 2026 », as the list groups its rows. */
private fun LocalDate.monthLabel(): String =
    month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRENCH).replaceFirstChar { it.uppercase() } + " " + year
