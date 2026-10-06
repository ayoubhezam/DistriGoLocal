package com.distrigo.app.ui.rapports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.repository.DormantProduct
import com.distrigo.app.data.repository.ProductGrouping
import com.distrigo.app.data.repository.ProductRanking
import com.distrigo.app.data.repository.ProductReport
import com.distrigo.app.data.repository.ProductSales
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.EntityImage
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.common.formatQty
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.navigation.DrillTarget
import com.distrigo.app.ui.navigation.LocalDrillDown

/** How many products the report itself ranks; "Voir tout" ranks them all. */
private const val TOP_PRODUCTS = 10

/** How many groups the ring shows; the rest are "Autres". */
private const val TOP_GROUPS = 5

private val ProduitsColor = Color(0xFF9333EA)
private val GroupColors = listOf(Color(0xFF6366F1), Color(0xFF0E9384), Color(0xFFF79009), Color(0xFFE91E63), Color(0xFF0BA5EC), Color(0xFF98A2B3))
private val AbcColors = listOf(Color(0xFF12B76A), Color(0xFFF79009), Color(0xFF98A2B3))

private val INFO_TOTAL = ReportInfo(
    "Ventes des produits",
    "Ce que les produits ont rapporté sur la période — la somme des lignes de vente, au prix de vente — dépôt et camion, ou l'un des deux selon Tout / Dépôt / Camion.\n\n" +
        "En dessous, la marge brute : ventes − ce que les produits vendus avaient coûté, chaque ligne au prix d'achat qu'elle avait au moment de la vente."
)
private val INFO_SOLD = ReportInfo("Produits vendus", "Le nombre de produits différents vendus au moins une fois sur la période.")
private val INFO_RATE = ReportInfo(
    "Taux de marge",
    "Marge brute ÷ coût d'achat des produits vendus × 100, sur le prix d'achat comme le compte la Direction du Commerce. Ni les charges ni les pertes n'en sont déduites."
)
private val INFO_A = ReportInfo(
    "Classe A",
    "Les produits qui font, à eux seuls, les premiers 80 % des ventes de la période — ceux à ne jamais laisser manquer. Le pourcentage dit quelle part de vos produits vendus ils représentent."
)
private val INFO_DORMANT = ReportInfo(
    "Sans vente",
    "Les produits en stock — au dépôt, au camion, ou les deux selon le choix — qui ne se sont pas vendus une seule fois sur la période. Le montant est ce que ce stock a coûté, au prix d'achat actuel : de l'argent immobilisé. Touchez la carte pour la liste."
)
private val INFO_TOP = ReportInfo(
    "Meilleurs produits",
    "Les produits classés par chiffre d'affaires, par marge brute ou par quantité vendue. La quantité se lit dans l'unité de chaque produit (carton, pièce, kg) : elle ne s'additionne pas d'un produit à l'autre."
)
private val INFO_GROUPS = ReportInfo(
    "Répartition",
    "Les ventes de la période réparties par catégorie, par marque ou par fournisseur, tels qu'ils sont aujourd'hui sur la fiche produit. Les cinq premiers, le reste dans « Autres »; les produits sans valeur sous « Sans … »."
)
private val INFO_ABC = ReportInfo(
    "Analyse ABC",
    "Les produits classés par ventes, du plus vendu au moins vendu :\n• A : ceux qui font les premiers 80 % des ventes ;\n• B : les 15 % suivants ;\n• C : les 5 % restants.\n\n" +
        "D'ordinaire, peu de produits font l'essentiel : les A demandent le plus d'attention (stock, prix), les C peuvent être revus."
)

/**
 * Rapports › Produits: what sold, what earned, and what did not move — over the shared period and
 * source. A tap on a product opens its page.
 */
@Composable
fun ProduitsReportScreen(onBack: () -> Unit, onSeeAll: () -> Unit, onSansVente: () -> Unit, viewModel: ProduitsReportViewModel) {
    val state by viewModel.state.collectAsState()
    val ranking by viewModel.ranking.collectAsState()
    val grouping by viewModel.grouping.collectAsState()
    val drill = LocalDrillDown.current

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = "Produits", subtitle = "Ce qui se vend, ce qui rapporte", leading = DsTopBarLeading.Back(onBack))
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (state.loading && state.report != null) {
                LinearProgressIndicator(Modifier.fillMaxSize(), color = DsColors.Primary, trackColor = Color.Transparent)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        ) {
            item { ReportFilterBar(state.filter, viewModel::setFilter) }
            val report = state.report
            when {
                report == null && state.error != null -> item { ReportMessage(state.error!!) }
                report == null -> item {
                    Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                }
                else -> {
                    item { TotalCard(report) }
                    item { Tiles(report, onSansVente) }
                    if (report.lines.isEmpty()) {
                        item { ReportMessage("Aucune vente sur cette période.") }
                    } else {
                        item { TitleWithInfo("Meilleurs produits", INFO_TOP, action = "Voir tout", onAction = onSeeAll) }
                        item {
                            ReportSegmented(ProductRanking.entries, ranking, { it.label }, { viewModel.ranking.value = it },
                                Modifier.padding(horizontal = DsSpacing.lg))
                        }
                        val top = report.ranked(ranking).take(TOP_PRODUCTS)
                        itemsIndexed(top, key = { _, p -> "top_${p.productId}" }) { i, p ->
                            ProductLine(i + 1, p, ranking) { drill(DrillTarget.Produit(p.productId)) }
                        }
                        item { GroupsCard(report, grouping) { viewModel.grouping.value = it } }
                        item { AbcCard(report) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalCard(report: ProductReport) {
    val money = LocalMoneyFormatter.current
    ReportHeroCard(
        title = "Ventes des produits",
        amount = money.da(report.total),
        caption = "Marge brute ${money.da(report.margin)}",
        info = INFO_TOTAL,
    )
}

@Composable
private fun Tiles(report: ProductReport, onSansVente: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val loss = report.margin < 0
    val a = report.abc.first()
    Column(Modifier.padding(horizontal = DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile("Produits vendus", report.lines.size.toString(), DsColors.TextPrimary, Modifier.weight(1f), null,
                Icons.Default.ShoppingCart, INFO_SOLD, accent = ProduitsColor)
            KpiTile("Taux de marge", report.marginRate?.let { percent(it) } ?: "—",
                if (loss) DsColors.Danger else DsColors.TextPrimary, Modifier.weight(1f), null,
                if (loss) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp, INFO_RATE,
                accent = if (loss) DsColors.Danger else DsColors.Primary)
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile("Classe A", plural(a.products, "produit", "produits"), DsColors.TextPrimary, Modifier.weight(1f),
                percentOf(a.products.toDouble(), report.lines.size.toDouble()), Icons.Default.Star, INFO_A, accent = AbcColors[0])
            // The stock that did not move, in money; the card opens the list.
            KpiTile("Sans vente", money.da(report.dormantValue), DsColors.TextPrimary,
                Modifier.weight(1f).clip(DsShapes.large).clickable(role = Role.Button, onClick = onSansVente),
                plural(report.dormant.size, "produit", "produits"), Icons.Default.Warehouse, INFO_DORMANT, accent = DsColors.Warning)
        }
    }
}

/** A section heading with its ⓘ, and an action on its right if given. */
@Composable
internal fun TitleWithInfo(text: String, info: ReportInfo, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg).padding(top = DsSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
        InfoButton(info, tint = DsColors.TextTertiary)
        Spacer(Modifier.weight(1f))
        if (action != null) Text(
            action, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.Primary,
            modifier = Modifier.clip(DsShapes.pill).clickable(role = Role.Button, onClick = onAction)
                .padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs),
        )
    }
}

/** A product's picture as Produits shows it: 36.dp, rounded, a cart without one. */
@Composable
internal fun Thumb(imageUri: String?) {
    Box(Modifier.size(36.dp).clip(DsShapes.small).background(DsColors.PrimaryLight), contentAlignment = Alignment.Center) {
        EntityImage(ref = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize()) {
            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * A product in a ranking: its rank, picture and name; under it the quantity in its unit and its margin
 * rate; on the right the figure it is ranked by.
 */
@Composable
fun ProductLine(rank: Int, p: ProductSales, by: ProductRanking, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    Row(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().clip(DsShapes.medium).background(DsColors.Surface)
            .clickable(role = Role.Button, onClick = onClick).padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.TextTertiary,
            modifier = Modifier.width(26.dp))
        Thumb(p.imageUri)
        Spacer(Modifier.width(DsSpacing.md))
        // The name has the whole line; the figure goes under it, on the right, beside the details.
        Column(Modifier.weight(1f)) {
            Text(p.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val rate = p.marginRate?.let { " · ${percent(it)}" } ?: ""
                Text("${formatQty(p.quantity)} ${p.unit}$rate", fontSize = DsTextSize.caption,
                    color = if (p.margin < 0) DsColors.Danger else DsColors.TextSecondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(DsSpacing.sm))
                val figure = when (by) {
                    ProductRanking.CA -> money.da(p.total)
                    ProductRanking.MARGE -> money.da(p.margin)
                    ProductRanking.QUANTITE -> "${formatQty(p.quantity)} ${p.unit}"
                }
                Text(figure, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold,
                    color = if (by == ProductRanking.MARGE && p.margin < 0) DsColors.Danger else DsColors.TextPrimary,
                    maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
internal fun CardColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface).padding(DsSpacing.lg)
    ) { content() }
}

@Composable
internal fun CardTitle(text: String, info: ReportInfo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        InfoButton(info, tint = DsColors.TextTertiary)
    }
}

/** The sales by category, brand or supplier: a ring of the first five and the rest, with their shares. */
@Composable
private fun GroupsCard(report: ProductReport, by: ProductGrouping, onGrouping: (ProductGrouping) -> Unit) {
    val money = LocalMoneyFormatter.current
    val groups = remember(report, by) { report.groups(by) }
    val shown = groups.take(TOP_GROUPS)
    val rest = groups.drop(TOP_GROUPS)
    val slices = shown.map { it.name to it.total } + (if (rest.isNotEmpty()) listOf("Autres" to rest.sumOf { it.total }) else emptyList())
    CardColumn {
        CardTitle("Répartition", INFO_GROUPS)
        Spacer(Modifier.height(DsSpacing.md))
        ReportSegmented(ProductGrouping.entries, by, { it.label }, onGrouping)
        Spacer(Modifier.height(DsSpacing.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ShareRing(slices.mapIndexed { i, (_, v) -> v to GroupColors[i % GroupColors.size] }, report.total, "Total", Modifier.weight(1f))
            Spacer(Modifier.width(DsSpacing.lg))
            Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                slices.forEachIndexed { i, (name, amount) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 4.dp).size(10.dp).clip(DsShapes.pill).background(GroupColors[i % GroupColors.size]))
                        Spacer(Modifier.width(DsSpacing.sm))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(DsSpacing.xs))
                                Text(percentOf(amount, report.total) ?: percent(0.0), fontSize = DsTextSize.bodySmall,
                                    fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                            }
                            FitText(money.da(amount), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** A, B, C: a bar of their shares of the sales, and each with its number of products. */
@Composable
private fun AbcCard(report: ProductReport) {
    val money = LocalMoneyFormatter.current
    val abc = report.abc
    val total = report.total
    val count = report.lines.size
    CardColumn {
        CardTitle("Analyse ABC", INFO_ABC)
        Spacer(Modifier.height(2.dp))
        Text("Peu de produits font l'essentiel des ventes", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        Spacer(Modifier.height(DsSpacing.md))
        // One bar: each class as wide as its share of the products, so a narrow A says "few products".
        Row(Modifier.fillMaxWidth().height(10.dp).clip(DsShapes.pill)) {
            abc.forEachIndexed { i, c ->
                if (c.products > 0) Box(Modifier.weight(c.products.toFloat()).fillMaxSize().background(AbcColors[i]))
            }
        }
        Spacer(Modifier.height(DsSpacing.md))
        abc.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().padding(vertical = DsSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(DsShapes.small).background(AbcColors[i].copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Text(c.label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = AbcColors[i])
                }
                Spacer(Modifier.width(DsSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(plural(c.products, "produit", "produits"), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold,
                        color = DsColors.TextPrimary, maxLines = 1)
                    Text("${percentOf(c.products.toDouble(), count.toDouble()) ?: percent(0.0)} des produits",
                        fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
                }
                Spacer(Modifier.width(DsSpacing.sm))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${percentOf(c.total, total) ?: percent(0.0)} des ventes", fontSize = DsTextSize.bodySmall,
                        fontWeight = FontWeight.Bold, color = DsColors.TextPrimary, maxLines = 1)
                    Text(money.da(c.total), fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Rapports › Produits › Voir tout: every product sold, ranked, searchable; a tap opens it. */
@Composable
fun ProductRankingScreen(onBack: () -> Unit, viewModel: ProduitsReportViewModel) {
    val state by viewModel.state.collectAsState()
    val ranking by viewModel.ranking.collectAsState()
    val query by viewModel.query.collectAsState()
    val drill = LocalDrillDown.current
    val report = state.report
    val ranked = remember(report, ranking) { report?.ranked(ranking).orEmpty() }
    // Each keeps its rank in the whole list, searched or not.
    val shown = remember(ranked, query) {
        val q = query.trim()
        ranked.mapIndexed { i, p -> i + 1 to p }.filter { q.isEmpty() || it.second.name.contains(q, ignoreCase = true) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(query, ranking) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = "Produits vendus", leading = DsTopBarLeading.Back(onBack))
        Column(Modifier.fillMaxWidth().background(DsColors.Surface).padding(bottom = DsSpacing.sm)) {
            DsCompactSearchField(value = query, onValueChange = { viewModel.query.value = it }, placeholder = "Rechercher un produit",
                modifier = Modifier.padding(horizontal = DsSpacing.lg))
            Spacer(Modifier.height(8.dp))
            ReportSegmented(ProductRanking.entries, ranking, { it.label }, { viewModel.ranking.value = it }, Modifier.padding(horizontal = DsSpacing.lg))
            Spacer(Modifier.height(6.dp))
            Text(if (query.isBlank()) plural(ranked.size, "produit", "produits") else "${shown.size} sur ${ranked.size}",
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary, modifier = Modifier.padding(horizontal = DsSpacing.lg))
        }
        when {
            report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DsColors.Primary) }
            shown.isEmpty() -> ReportMessage(if (query.isBlank()) "Aucune vente sur cette période." else "Aucun résultat pour « ${query.trim()} »")
            else -> LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
            ) {
                items(shown, key = { it.second.productId }) { (rank, p) -> ProductLine(rank, p, ranking) { drill(DrillTarget.Produit(p.productId)) } }
            }
        }
    }
}

/** Rapports › Produits › Sans vente: the stocked products that did not sell, the costliest stock first. */
@Composable
fun DormantProductsScreen(onBack: () -> Unit, viewModel: ProduitsReportViewModel) {
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()
    val drill = LocalDrillDown.current
    val money = LocalMoneyFormatter.current
    val report = state.report
    val all = report?.dormant.orEmpty()
    val shown = remember(all, query) { val q = query.trim(); all.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) } }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = "Sans vente", subtitle = report?.let { "Stock immobilisé : ${money.da(it.dormantValue)}" }, leading = DsTopBarLeading.Back(onBack))
        Column(Modifier.fillMaxWidth().background(DsColors.Surface).padding(bottom = DsSpacing.sm)) {
            DsCompactSearchField(value = query, onValueChange = { viewModel.query.value = it }, placeholder = "Rechercher un produit",
                modifier = Modifier.padding(horizontal = DsSpacing.lg))
            Spacer(Modifier.height(8.dp))
            Text(if (query.isBlank()) plural(all.size, "produit", "produits") else "${shown.size} sur ${all.size}",
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary, modifier = Modifier.padding(horizontal = DsSpacing.lg))
        }
        when {
            report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DsColors.Primary) }
            shown.isEmpty() -> ReportMessage(if (query.isBlank()) "Tout le stock s'est vendu sur cette période." else "Aucun résultat pour « ${query.trim()} »")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
            ) {
                items(shown, key = { it.productId }) { d -> DormantLine(d) { drill(DrillTarget.Produit(d.productId)) } }
            }
        }
    }
}

@Composable
private fun DormantLine(d: DormantProduct, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    Row(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().clip(DsShapes.medium).background(DsColors.Surface)
            .clickable(role = Role.Button, onClick = onClick).padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(d.imageUri)
        Spacer(Modifier.width(DsSpacing.md))
        // As the ranked rows: the name has the whole line, the value goes under it on the right.
        Column(Modifier.weight(1f)) {
            Text(d.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Stock : ${formatQty(d.stock)} ${d.unit}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(money.da(d.value), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary, maxLines = 1, softWrap = false)
            }
        }
    }
}
