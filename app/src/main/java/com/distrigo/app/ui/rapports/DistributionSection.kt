package com.distrigo.app.ui.rapports

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.repository.CommuneStat
import com.distrigo.app.data.repository.DistributionReport
import com.distrigo.app.data.repository.NO_COMMUNE
import com.distrigo.app.data.repository.SectorStat
import com.distrigo.app.ui.common.EntityAvatar
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.navigation.DrillTarget
import com.distrigo.app.ui.navigation.LocalDrillDown
import java.time.format.DateTimeFormatter

// The bars' two parts, in the theme's colours: the clients who bought, and those who did not.
private val ServedColor = DsColors.Primary
private val UnservedColor = DsColors.Warning
private val DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private const val ALL_COMMUNES = "Toutes les communes"
private const val SANS_COMMUNE = "Sans commune"

/** A commune chosen, as the selector names it: null for all of them, [NO_COMMUNE] for the clients without one. */
private fun communeLabel(commune: String?) = when (commune) {
    null -> ALL_COMMUNES
    NO_COMMUNE -> SANS_COMMUNE
    else -> commune
}

private val INFO_DISTRIBUTION = ReportInfo(
    "Meilleure distribution",
    "Où sont vos clients, et lesquels ont acheté sur la période.\n\n" +
        "• Clients : ceux enregistrés à la fin de la période — sans les clients supprimés —, dans la commune choisie s'il y en a une.\n" +
        "• Clients servis : ceux qui ont au moins une vente sur la période — dépôt et camion, ou l'un des deux selon Tout / Dépôt / Camion.\n" +
        "• Conversion : clients servis ÷ clients.\n\n" +
        "Le choix d'une commune ne change que ces trois chiffres et le Top 7 secteurs ; le chiffre d'affaires et le reste du rapport restent ceux de toute l'activité."
)
private val INFO_TOP_SECTORS = ReportInfo(
    "Top 7 secteurs",
    "Les sept secteurs qui comptent le plus de clients, le plus grand en haut ; la longueur d'une barre suit son nombre de clients. " +
        "En bleu les clients servis, en orange ceux sans vente ; le pourcentage est la conversion du secteur.\n\n" +
        "Touchez un secteur pour voir ses clients sans vente. Les clients sans secteur n'y figurent pas."
)
private val INFO_COMMUNES = ReportInfo(
    "Répartition par commune",
    "Chaque commune : ses clients servis sur ses clients, sa conversion, et le nombre de secteurs qu'elle compte. " +
        "« $SANS_COMMUNE » : les clients dont la fiche n'a pas de commune. Toutes les communes, quelle que soit celle choisie plus haut."
)

/**
 * The Ventes report's distribution, as items of its list: the commune selector, the three figures,
 * the seven largest sectors, and the communes. The commune chosen — null for all of them — narrows the
 * figures and the sectors only.
 */
fun LazyListScope.distributionItems(
    distribution: DistributionReport,
    commune: String?,
    onCommune: (String?) -> Unit,
    onSeeAll: () -> Unit,
    onSector: (SectorStat) -> Unit,
) {
    item(key = "distribution_title") { TitleWithInfo("Meilleure distribution", INFO_DISTRIBUTION) }
    item(key = "distribution_commune") { CommuneSelector(commune, distribution.communeChoices, onCommune) }
    item(key = "distribution_kpis") { DistributionKpis(distribution) }
    item(key = "distribution_sectors") { TopSectorsCard(distribution, onSeeAll, onSector) }
    item(key = "distribution_communes") { TitleWithInfo("Répartition par commune", INFO_COMMUNES) }
    items(distribution.communes, key = { "commune_${it.name.orEmpty()}" }) { CommuneCard(it) }
}

/**
 * The commune the figures and sectors are narrowed to, as the period is chosen at the top of the
 * report: the same outlined field, opening a list — "Toutes les communes", each by name, then "Sans
 * commune" when some clients have none.
 */
@Composable
private fun CommuneSelector(selected: String?, names: List<String>, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    // The commune chosen stays offered when the period leaves it without a client.
    val options: List<String?> = listOf(null) + names + listOfNotNull(selected?.takeIf { it !in names })
    Box(Modifier.padding(horizontal = DsSpacing.lg)) {
        ReportSelectorField(
            Icons.Default.LocationOn, communeLabel(selected), null, "Changer de commune",
            Modifier.onSizeChanged { width = it.width },
        ) { open = true }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(with(LocalDensity.current) { width.toDp() }),
            shape = DsShapes.medium,
            containerColor = DsColors.Surface,
        ) {
            options.forEach { option ->
                val on = option == selected
                DropdownMenuItem(
                    text = {
                        Text(communeLabel(option), fontSize = DsTextSize.body,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) DsColors.Primary else DsColors.TextPrimary)
                    },
                    trailingIcon = if (on) ({ Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.Primary) }) else null,
                    onClick = { open = false; if (!on) onSelect(option) },
                )
            }
        }
    }
}

// ── The three figures ──

@Composable
private fun DistributionKpis(d: DistributionReport) {
    Row(
        Modifier.padding(horizontal = DsSpacing.lg).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
    ) {
        MiniKpi(Icons.Default.Groups, "Clients", countFigure(d.clients), "au total", DsColors.Primary, Modifier.weight(1f))
        MiniKpi(Icons.Default.HowToReg, "Clients servis", countFigure(d.served), "ont acheté", DsColors.Success, Modifier.weight(1f))
        MiniKpi(Icons.Default.Percent, "Conversion", rateFigure(d.rate), "servis / clients", DsColors.Warning, Modifier.weight(1f))
    }
}

/** A third of a row: the figure dark, as everywhere in the reports; the theme colour on its icon, label and border. */
@Composable
private fun MiniKpi(icon: ImageVector, label: String, value: Figure, caption: String, accent: Color, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().clip(DsShapes.large).background(DsColors.Surface)
            .border(1.dp, accent.copy(alpha = 0.35f), DsShapes.large).padding(DsSpacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(DsSpacing.sm))
        AnimatedFigure(value, fontSize = DsTextSize.title, fontWeight = FontWeight.ExtraBold, color = DsColors.TextPrimary)
        FitText(label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = accent)
        FitText(caption, fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
    }
}

// ── Sectors ──

@Composable
private fun TopSectorsCard(d: DistributionReport, onSeeAll: () -> Unit, onSector: (SectorStat) -> Unit) {
    val top = d.topSectors
    val largest = top.firstOrNull()?.clients ?: 0
    CardColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Top 7 secteurs", INFO_TOP_SECTORS)
            Spacer(Modifier.weight(1f))
            // Only when there is more than the bars show — the smaller sectors, the empty ones.
            if (d.sectors.size > top.size) SeeAll(onSeeAll)
        }
        Spacer(Modifier.height(DsSpacing.xs))
        SectorLegend()
        Spacer(Modifier.height(DsSpacing.sm))
        if (top.isEmpty()) {
            Text("Aucun client n'est rangé dans un secteur.", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary,
                modifier = Modifier.padding(vertical = DsSpacing.sm))
        }
        top.forEach { s -> SectorBar(s, largest, onClick = { onSector(s) }) }
        if (d.unsectored > 0) {
            Spacer(Modifier.height(DsSpacing.xs))
            Text("${plural(d.unsectored, "client", "clients")} sans secteur", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
    }
}

@Composable
private fun SeeAll(onClick: () -> Unit) {
    Text(
        "Voir tout", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.Primary,
        modifier = Modifier.clip(DsShapes.pill).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs),
    )
}

@Composable
private fun SectorLegend(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LegendDot(ServedColor)
        Text("Clients servis", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        Spacer(Modifier.width(DsSpacing.md))
        LegendDot(UnservedColor)
        Text("Sans vente", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.padding(end = DsSpacing.xs).size(8.dp).clip(DsShapes.pill).background(color))
}

/**
 * A sector as a bar: its name and conversion, its clients served over its clients; under them the bar,
 * as long as its share of [largest]'s clients, the served part then the rest.
 */
@Composable
private fun SectorBar(
    s: SectorStat,
    largest: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Room left and right inside the tap area, for a bar drawn as a card of its own. */
    inset: Dp = 0.dp,
) {
    Column(
        modifier.fillMaxWidth().clip(DsShapes.small).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = inset, vertical = DsSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The name and its rate share what the count leaves; a long name gives way first.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(DsSpacing.xs))
                Text(percent(s.rate), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary,
                    maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.width(DsSpacing.sm))
            Text("${s.served} / ${s.clients}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.height(6.dp))
        StackedBar(s.served, s.withoutSale, if (largest > 0) s.clients / largest.toFloat() else 0f)
    }
}

/**
 * A track, and over it [served] then [unserved], together [length] of its width. When they change, the
 * bar slides to its new length and its new split; not on its first showing.
 */
@Composable
private fun StackedBar(served: Int, unserved: Int, length: Float) {
    val clients = served + unserved
    val bar by animateFloatAsState(if (clients > 0 && length > 0f) length.coerceIn(0.02f, 1f) else 0f, glide(), label = "bar")
    val share by animateFloatAsState(if (clients > 0) served / clients.toFloat() else 0f, glide(), label = "share")
    Box(Modifier.fillMaxWidth().height(10.dp).clip(DsShapes.pill).background(DsColors.SurfaceSunken)) {
        if (bar > 0f) {
            // The rest of the bar is the clients without a sale; the served part is drawn over it.
            Box(Modifier.fillMaxWidth(bar).fillMaxHeight().clip(DsShapes.pill).background(UnservedColor)) {
                if (share > 0f) Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(ServedColor))
            }
        }
    }
}

// ── Communes ──

/** A commune: its name and conversion, the conversion as a bar, and its clients and sectors. */
@Composable
private fun CommuneCard(c: CommuneStat) {
    Column(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().clip(DsShapes.medium).background(DsColors.Surface)
            .padding(DsSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.name ?: SANS_COMMUNE, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold,
                color = if (c.name == null) DsColors.TextSecondary else DsColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(DsSpacing.sm))
            AnimatedFigure(rateFigure(c.rate), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }
        Spacer(Modifier.height(DsSpacing.sm))
        val rate by animateFloatAsState(c.rate.toFloat(), glide(), label = "rate")
        LinearProgressIndicator(
            progress = { rate },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(DsShapes.pill),
            color = DsColors.Success,
            trackColor = DsColors.SurfaceSunken,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(DsSpacing.sm))
        val sectors = if (c.name != null) " • ${plural(c.sectors, "secteur", "secteurs")}" else ""
        Text("${c.served} / ${c.clients} clients$sectors", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
    }
}

// ── A sector, opened ──

/** The sheet of the sector opened, if any: its figures, then its clients without a sale — a tap opens one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectorSheetHost(sheet: SectorSheet?, onDismiss: () -> Unit) {
    if (sheet == null) return
    val drill = LocalDrillDown.current
    val s = sheet.sector
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = DsColors.Surface,
    ) {
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = DsSpacing.lg)) {
            item(key = "head") {
                Column(Modifier.padding(horizontal = DsSpacing.lg)) {
                    Text(s.name, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                    Text("Commune : ${s.commune}", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                    Spacer(Modifier.height(DsSpacing.md))
                    Row(Modifier.fillMaxWidth()) {
                        SheetFigure("Clients", s.clients.toString(), Modifier.weight(1f))
                        SheetFigure("Servis", s.served.toString(), Modifier.weight(1f))
                        SheetFigure("Sans vente", s.withoutSale.toString(), Modifier.weight(1f))
                        SheetFigure("Conversion", percent(s.rate), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(DsSpacing.md))
                    StackedBar(s.served, s.withoutSale, 1f)
                    Spacer(Modifier.height(DsSpacing.xs))
                    SectorLegend()
                    Spacer(Modifier.height(DsSpacing.lg))
                    Text("Clients sans vente (${s.withoutSale})", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold,
                        color = DsColors.TextPrimary)
                    Spacer(Modifier.height(DsSpacing.xs))
                }
            }
            val unserved = sheet.unserved
            when {
                unserved == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(DsSpacing.xl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                }
                unserved.isEmpty() -> item(key = "none") {
                    Text("Tous les clients de ce secteur ont acheté sur la période.", fontSize = DsTextSize.bodySmall,
                        color = DsColors.TextSecondary, modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm))
                }
                else -> items(unserved, key = { it.id }) { client ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { drill(DrillTarget.Client(client.id)) }
                            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EntityAvatar(client.name, client.imageUri, size = 36.dp)
                        Spacer(Modifier.width(DsSpacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(client.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(client.lastSale?.let { "Dernier achat : ${it.format(DAY)}" } ?: "Aucun achat",
                                fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DsColors.TextTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetFigure(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FitText(value, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
    }
}

// ── Voir tout ──

/** Rapports › Ventes › Secteurs: every sector of the report's commune, the most clients first. */
@Composable
fun SectorsScreen(onBack: () -> Unit, viewModel: VentesReportViewModel) {
    val state by viewModel.state.collectAsState()
    val d = state.distribution

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title = "Secteurs",
            subtitle = state.commune?.let { "Commune : ${communeLabel(it)}" } ?: "Tous les secteurs",
            leading = DsTopBarLeading.Back(onBack),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
        ) {
            item(key = "legend") { SectorLegend(Modifier.padding(horizontal = DsSpacing.lg)) }
            if (d == null) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                }
            } else {
                val largest = d.sectors.firstOrNull()?.clients ?: 0
                items(d.sectors, key = { it.id }) { s ->
                    SectorBar(
                        s, largest, onClick = { viewModel.openSector(s) },
                        modifier = Modifier.padding(horizontal = DsSpacing.lg).clip(DsShapes.medium).background(DsColors.Surface),
                        inset = DsSpacing.md,
                    )
                }
                if (d.sectors.isEmpty()) item(key = "none") { ReportMessage("Aucun secteur.") }
                if (d.unsectored > 0) item(key = "unsectored") {
                    Text("${plural(d.unsectored, "client", "clients")} sans secteur", fontSize = DsTextSize.caption,
                        color = DsColors.TextSecondary, modifier = Modifier.padding(horizontal = DsSpacing.lg))
                }
            }
        }
    }
    SectorSheetHost(state.sheet, viewModel::closeSector)
}
