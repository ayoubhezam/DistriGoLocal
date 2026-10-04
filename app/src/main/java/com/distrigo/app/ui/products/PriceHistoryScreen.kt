package com.distrigo.app.ui.products

import com.distrigo.app.ui.common.formatQty
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.rapports.ReportFilterBar
import com.distrigo.app.ui.common.DsCompactSearchField
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.rememberLazyListState

/**
 * Every price a product changed hands at: what was paid for it and what it sold for, side by side.
 *
 * One period governs the whole screen — the summaries and the list agree by construction. It is picked
 * with the Rapports period bar, at the top, so a period is chosen the same way everywhere.
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
    onSeeAll    : () -> Unit,
) {
    // Counted with every filter but the segment itself, so the counts beside each segment say what
    // that segment would show.
    val overall = remember(movements, filters) { movements.narrow(filters, includeKind = false) }
    val shown   = remember(movements, filters) { movements.narrow(filters) }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title    = "Historique des prix",
            subtitle = productName,
            leading  = DsTopBarLeading.Back(onBack)
        )

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

        ReportFilterBar(
            filter     = filters.period,
            onChange   = { onFilters(filters.copy(period = it)) },
            showSource = false,
            modifier   = Modifier.padding(top = DsSpacing.sm, bottom = DsSpacing.xs),
        )

        KindSegments(
            filters = filters,
            counts  = mapOf(
                null to overall.size,
                PriceMovementKind.ACHAT to overall.count { it.kind == PriceMovementKind.ACHAT },
                PriceMovementKind.VENTE to overall.count { it.kind == PriceMovementKind.VENTE },
            ),
            onKind  = { onFilters(filters.copy(kind = it)) }
        )

        if (shown.isEmpty()) {
            EmptyState(Icons.Default.FilterAltOff, "Aucun mouvement", "Aucun mouvement de prix sur cette période.")
            return@Column
        }

        val kinds = PriceMovementKind.entries.filter { filters.kind == null || filters.kind == it }

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
            // The latest few; every one, searchable, is a screen of its own behind « Voir tout ».
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = DsSpacing.sm)) {
                    Text("Mouvements", fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.body, color = DsColors.TextPrimary)
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text(
                        shown.size.toString(),
                        fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary,
                        modifier = Modifier.clip(DsShapes.pill).background(DsColors.SurfaceSunken).padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Voir tout", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.Primary,
                        modifier = Modifier
                            .clip(DsShapes.pill)
                            .clickable(role = Role.Button, onClick = onSeeAll)
                            .padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs)
                    )
                }
            }
            items(shown.take(LATEST_MOVEMENTS), key = { "${it.kind}-${it.documentId}-${it.unitPrice}" }) { MovementRow(it) }
        }
    }
}

/** How many movements Historique des prix shows before « Voir tout ». */
private const val LATEST_MOVEMENTS = 3

/**
 * Historique des prix › Voir tout: every price movement of the period and the side chosen there —
 * newest first, grouped by month — with a search on the client or supplier, the kind and the document,
 * in the search bar Produits uses.
 */
@Composable
fun PriceMovementsScreen(
    productName : String,
    movements   : List<PriceMovement>,
    filters     : PriceHistoryFilters,
    onBack      : () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val all   = remember(movements, filters) { movements.narrow(filters.copy(query = "")) }
    val shown = remember(movements, filters, query) { movements.narrow(filters.copy(query = query)) }
    // A new search starts at the top of what it found.
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }
    // What the list covers, as chosen on Historique des prix: "Cette année · Vente".
    val scope = listOfNotNull(filters.period.period.label, filters.kind?.label).joinToString(" · ")

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = "Mouvements des prix", subtitle = productName, leading = DsTopBarLeading.Back(onBack))
        // On white, as in Produits: the sunken search pill reads against white, the list's grey under it.
        Column(Modifier.fillMaxWidth().background(DsColors.Surface).padding(bottom = DsSpacing.sm)) {
            DsCompactSearchField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = "Rechercher un client ou un fournisseur",
                modifier      = Modifier.padding(horizontal = DsSpacing.lg)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                (if (query.isBlank()) "${all.size} mouvement(s)" else "${shown.size} sur ${all.size}") + " · " + scope,
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
                modifier = Modifier.padding(horizontal = DsSpacing.lg)
            )
        }
        if (shown.isEmpty()) {
            EmptyState(
                Icons.Default.FilterAltOff, "Aucun résultat",
                if (query.isBlank()) "Aucun mouvement de prix sur cette période." else "Aucun mouvement ne correspond à « ${query.trim()} »."
            )
            return@Column
        }
        LazyColumn(
            state               = listState,
            modifier            = Modifier.weight(1f).fillMaxWidth(),
            contentPadding      = PaddingValues(DsSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            var currentMonth: String? = null
            shown.forEach { movement ->
                val month = movement.day().monthLabel()
                if (month != currentMonth) {
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
}

/** The colour each side of the trade is shown in: purchases green, sales blue. */
private fun PriceMovementKind.kindColor(): Color =
    if (this == PriceMovementKind.ACHAT) DsColors.Success else DsColors.Primary

// ── Pieces ───────────────────────────────────────────────────────────────────

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
private fun SummaryBlock(kind: PriceMovementKind, stats: PriceStats?, modifier: Modifier = Modifier) {
    val money = LocalMoneyFormatter.current
    val colour = kind.kindColor()
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
            stats?.let { money.da(it.last) } ?: "—",
            fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = colour
        )
        if (stats == null) {
            Text("Aucune donnée", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        } else {
            SummaryLine("Moyen", money.da(stats.average))
            SummaryLine("Min", money.da(stats.min))
            SummaryLine("Max", money.da(stats.max))
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
    val money = LocalMoneyFormatter.current
    Row(
        Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.medium).padding(horizontal = DsSpacing.md, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text("Écart vente − achat", fontSize = DsTextSize.body, color = DsColors.TextSecondary)
        Text(
            (if (gap > 0) "+" else if (gap < 0) "−" else "") + money.da(abs(gap)),
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
    val money = LocalMoneyFormatter.current
    val achat  = movement.kind == PriceMovementKind.ACHAT
    val colour = movement.kind.kindColor()
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
            Text(money.da(movement.unitPrice), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
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
    val money = LocalMoneyFormatter.current
    val delta = movement.delta
    val good  = delta != null && (if (movement.kind == PriceMovementKind.ACHAT) delta < 0 else delta > 0)
    val label = when {
        delta == null        -> if (movement.kind == PriceMovementKind.ACHAT) "Premier achat" else "Première vente"
        abs(delta) < 0.005   -> "Stable"
        delta > 0            -> "+${money.da(delta)}"
        else                 -> "−${money.da(-delta)}"
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
