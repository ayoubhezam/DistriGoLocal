package com.distrigo.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.repository.DashboardAlerts
import com.distrigo.app.data.repository.DashboardBalances
import com.distrigo.app.data.repository.DashboardSales
import com.distrigo.app.data.repository.change
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsTopBarRootActions
import com.distrigo.app.ui.designsystem.DsTopBarSize
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.navigation.ReportEntry
import com.distrigo.app.ui.rapports.AnimatedFigure
import com.distrigo.app.ui.rapports.CardColumn
import com.distrigo.app.ui.rapports.CardTitle
import com.distrigo.app.ui.rapports.Figure
import com.distrigo.app.ui.rapports.HeroHalf
import com.distrigo.app.ui.rapports.ReportHeroCard
import com.distrigo.app.ui.rapports.ReportInfo
import com.distrigo.app.ui.rapports.countFigure
import com.distrigo.app.ui.rapports.figure
import com.distrigo.app.ui.rapports.glidingValues
import com.distrigo.app.ui.rapports.percent
import com.distrigo.app.ui.rapports.plural
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val FR = Locale.FRENCH
private val TODAY = DateTimeFormatter.ofPattern("EEEE d MMMM", FR)

private val INFO_TODAY = ReportInfo(
    "Chiffre d'affaires du jour",
    "Les ventes d'aujourd'hui jusqu'à maintenant, dépôt et camion ensemble.\n\n" +
        "Comparées au même jour de la semaine dernière, jusqu'à la même heure : un mardi matin avec un mardi matin.\n\n" +
        "Touchez la carte pour le rapport Ventes du jour."
)
private val INFO_MONTH = ReportInfo(
    "Ce mois",
    "Le chiffre d'affaires et la marge brute du mois jusqu'à aujourd'hui, comparés au mois dernier jusqu'à la même date et la même heure.\n\n" +
        "Taux de marge : sur le prix d'achat, comme dans le rapport Ventes."
)
private val INFO_WEEK = ReportInfo(
    "7 derniers jours",
    "Le chiffre d'affaires de chacun des sept derniers jours, aujourd'hui compris, dépôt et camion ensemble. Aujourd'hui est en couleur."
)
private val INFO_ALERTS = ReportInfo(
    "Alertes",
    "• Ruptures : les produits suivis dont le stock est à zéro ou moins.\n" +
        "• Stock bas : ceux sous leur stock minimum.\n" +
        "• Créances de plus de 90 jours : ce que les clients doivent depuis plus de trois mois."
)
private val INFO_BALANCES = ReportInfo(
    "Créances et dettes",
    "Aujourd'hui : ce que vos clients vous doivent, et ce que vous devez à vos fournisseurs. Touchez-en un pour le détail."
)

/** The Dashboard: today, this month, the last seven days, what needs seeing to, and who owes what. */
@Composable
fun DashboardScreen(
    onOpenReport         : (ReportEntry) -> Unit = {},
    onOpenMenu           : (() -> Unit)? = null,
    onNotificationsClick : () -> Unit = {},
    onProfileClick       : () -> Unit = {},
    viewModel            : DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val open = { link: DashboardLink -> viewModel.prepare(link); onOpenReport(link.report) }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        // The tab roots are the only screens with no back arrow, so the menu takes the
        // leading slot and the global controls take the trailing one.
        DsTopAppBar(
            title   = "Dashboard",
            leading = onOpenMenu?.let { DsTopBarLeading.Menu(it) } ?: DsTopBarLeading.None,
            size    = DsTopBarSize.Large
        ) {
            DsTopBarRootActions(
                onNotificationsClick = onNotificationsClick,
                onProfileClick       = onProfileClick
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        ) {
            val sales = state.sales
            if (sales == null) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                }
            } else {
                item(key = "today_title") { Heading("Aujourd'hui", sales.now.format(TODAY).replaceFirstChar { it.uppercase() }) }
                item(key = "today") { TodayCard(sales) { open(DashboardLink.TODAY) } }
                item(key = "today_tiles") { TodayTiles(sales) { open(DashboardLink.TODAY) } }
                item(key = "month") { MonthCard(sales) { open(DashboardLink.MONTH) } }
                item(key = "week") { WeekCard(sales) { open(DashboardLink.WEEK) } }
            }
            state.alerts?.let { alerts -> item(key = "alerts") { AlertsCard(alerts) { open(it) } } }
            state.balances?.let { balances -> item(key = "balances") { BalancesCard(balances) { open(it) } } }
        }
    }
}

@Composable
private fun Heading(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg), verticalAlignment = Alignment.Bottom) {
        Text(title, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        Spacer(Modifier.width(DsSpacing.sm))
        Text(detail, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "▲ 12,5 %" or "▼ 3,0 %" — green up, red down — or nothing to compare with. */
private fun trend(rate: Double?): Pair<String, Color> = when {
    rate == null -> "—" to DsColors.TextTertiary
    rate >= 0.0005 -> "▲ ${percent(rate)}" to DsColors.Success
    rate <= -0.0005 -> "▼ ${percent(abs(rate))}" to DsColors.Danger
    else -> "= ${percent(0.0)}" to DsColors.TextSecondary
}

/** Last week's same day, by name: "mardi". */
private fun lastWeekDay(sales: DashboardSales) = sales.now.dayOfWeek.getDisplayName(TextStyle.FULL, FR)

@Composable
private fun TodayCard(sales: DashboardSales, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val (arrow, _) = trend(change(sales.today.total, sales.lastWeek.total))
    val compared = if (sales.lastWeek.total > 0) "$arrow vs ${lastWeekDay(sales)} dernier à la même heure"
                   else "Rien vendu ${lastWeekDay(sales)} dernier à la même heure"
    ReportHeroCard(
        title = "Chiffre d'affaires du jour",
        amount = money.figure(sales.today.total),
        caption = compared,
        info = INFO_TODAY,
        halves = listOf(
            HeroHalf("Payé à la vente", money.figure(sales.today.paid)),
            HeroHalf("À crédit", money.figure(sales.today.credit)),
        ),
        onClick = onClick,
    )
}

/** Today's sales, average basket and clients served, each against the same day last week. */
@Composable
private fun TodayTiles(sales: DashboardSales, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val t = sales.today
    val w = sales.lastWeek
    Row(
        Modifier.padding(horizontal = DsSpacing.lg).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
    ) {
        TrendTile(Icons.Default.Receipt, "Ventes", countFigure(t.count), change(t.count.toDouble(), w.count.toDouble()),
            DsColors.Primary, Modifier.weight(1f), onClick)
        TrendTile(Icons.Default.ShoppingBasket, "Panier moyen", t.basket?.let { money.figure(it) } ?: Figure.text("—"),
            t.basket?.let { now -> w.basket?.let { change(now, it) } }, DsColors.Success, Modifier.weight(1f), onClick)
        TrendTile(Icons.Default.Groups, "Clients servis", countFigure(t.clients), change(t.clients.toDouble(), w.clients.toDouble()),
            DsColors.Warning, Modifier.weight(1f), onClick)
    }
}

/** A third of a row: an icon in its colour, the figure, its label, and how it compares with last week. */
@Composable
private fun TrendTile(icon: ImageVector, label: String, value: Figure, rate: Double?, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val (arrow, color) = trend(rate)
    Column(
        modifier.fillMaxHeight().clip(DsShapes.large).background(DsColors.Surface)
            .clickable(role = Role.Button, onClick = onClick).padding(DsSpacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(DsSpacing.sm))
        AnimatedFigure(value, fontSize = DsTextSize.title, fontWeight = FontWeight.ExtraBold, color = DsColors.TextPrimary)
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(arrow, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

/** This month so far: its sales and gross margin, the sales against last month to the same date. */
@Composable
private fun MonthCard(sales: DashboardSales, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val m = sales.month
    val (arrow, color) = trend(change(m.total, sales.lastMonth.total))
    val lastMonth = sales.now.minusMonths(1).month.getDisplayName(TextStyle.FULL, FR)
    Column(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface)
            .clickable(role = Role.Button, onClick = onClick).padding(DsSpacing.lg),
    ) {
        CardTitle("Ce mois", INFO_MONTH)
        Spacer(Modifier.height(DsSpacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
            Column(Modifier.weight(1f)) {
                Text("Chiffre d'affaires", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                AnimatedFigure(money.figure(m.total), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text("$arrow vs $lastMonth", fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(1f)) {
                Text("Marge brute", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                AnimatedFigure(money.figure(m.margin), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold,
                    color = if (m.margin < 0) DsColors.Danger else DsColors.TextPrimary)
                Text(m.marginRate?.let { "Taux ${percent(it)}" } ?: "Taux —", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
        }
    }
}

/** The last seven days as bars, today in colour; the bars move when the figures change. */
@Composable
private fun WeekCard(sales: DashboardSales, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val days = sales.week
    val top = (days.maxOfOrNull { it.total } ?: 0.0).coerceAtLeast(1.0)
    val drawn = glidingValues(days.map { (it.total / top).toFloat() }, layout = days.firstOrNull()?.day)
    val today = DsColors.Primary
    val other = DsColors.Primary.copy(alpha = 0.35f)
    val stub = DsColors.SurfaceSunken
    Column(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface)
            .clickable(role = Role.Button, onClick = onClick).padding(DsSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("7 derniers jours", INFO_WEEK)
            Spacer(Modifier.weight(1f))
            AnimatedFigure(money.figure(days.sumOf { it.total }), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold,
                color = DsColors.TextSecondary)
        }
        Spacer(Modifier.height(DsSpacing.md))
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val slot = size.width / days.size.coerceAtLeast(1)
            val bar = slot * 0.55f
            val radius = CornerRadius(minOf(bar / 2, 6.dp.toPx()))
            val shares = drawn()
            days.forEachIndexed { i, d ->
                val x = i * slot + (slot - bar) / 2
                val h = shares.getOrElse(i) { 0f } * size.height
                if (d.total <= 0 || h <= 0f) {
                    drawRoundRect(stub, Offset(x, size.height - 2.dp.toPx()), Size(bar, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
                } else {
                    drawRoundRect(if (i == days.lastIndex) today else other, Offset(x, size.height - h), Size(bar, h), radius)
                }
            }
        }
        Spacer(Modifier.height(DsSpacing.xs))
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                Text(
                    d.day.dayOfWeek.getDisplayName(TextStyle.NARROW, FR),
                    fontSize = DsTextSize.caption, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    fontWeight = if (i == days.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == days.lastIndex) DsColors.Primary else DsColors.TextTertiary,
                )
            }
        }
    }
}

/** What needs seeing to, each line opening its list; or, when all is well, says so. */
@Composable
private fun AlertsCard(alerts: DashboardAlerts, onOpen: (DashboardLink) -> Unit) {
    val money = LocalMoneyFormatter.current
    CardColumn {
        CardTitle("Alertes", INFO_ALERTS)
        Spacer(Modifier.height(DsSpacing.sm))
        if (!alerts.any) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = DsSpacing.sm)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text("Aucune alerte", fontSize = DsTextSize.body, color = DsColors.TextSecondary)
            }
        }
        if (alerts.outOfStock > 0) AlertLine(Icons.Default.RemoveShoppingCart, DsColors.Danger, "Ruptures", "stock à zéro",
            countFigure(alerts.outOfStock, "produit", "produits")) { onOpen(DashboardLink.RESTOCK) }
        if (alerts.lowStock > 0) AlertLine(Icons.Default.Inventory2, DsColors.Warning, "Stock bas", "sous le minimum",
            countFigure(alerts.lowStock, "produit", "produits")) { onOpen(DashboardLink.RESTOCK) }
        if (alerts.oldClientDebt > 0.005) AlertLine(Icons.Default.HourglassBottom, DsColors.Danger, "Créances", "de plus de 90 jours",
            money.figure(alerts.oldClientDebt)) { onOpen(DashboardLink.CLIENT_DEBTS) }
    }
}

/** An alert: its icon, a short [label] and what it means under it — short, so a large amount beside it never cuts it. */
@Composable
private fun AlertLine(icon: ImageVector, color: Color, label: String, detail: String, value: Figure, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(DsShapes.small).clickable(role = Role.Button, onClick = onClick).padding(vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(DsShapes.small).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(DsSpacing.md))
        // The words take the whole line, the figure the line under them: an amount in the billions
        // beside them would cut them short.
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    append(label)
                    withStyle(SpanStyle(color = DsColors.TextSecondary, fontSize = DsTextSize.caption)) { append(" · $detail") }
                },
                fontSize = DsTextSize.body, color = DsColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            AnimatedFigure(value, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = color)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DsColors.TextTertiary)
    }
}

/** What the clients owe and what is owed to the suppliers, side by side, each opening its report. */
@Composable
private fun BalancesCard(balances: DashboardBalances, onOpen: (DashboardLink) -> Unit) {
    val money = LocalMoneyFormatter.current
    CardColumn {
        CardTitle("Créances et dettes", INFO_BALANCES)
        Spacer(Modifier.height(DsSpacing.md))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            BalanceHalf("Vos clients vous doivent", money.figure(balances.clientsOwe), DsColors.Success, Modifier.weight(1f)) {
                onOpen(DashboardLink.CLIENT_DEBTS)
            }
            BalanceHalf("Vous devez aux fournisseurs", money.figure(balances.owedToSuppliers), DsColors.Warning, Modifier.weight(1f)) {
                onOpen(DashboardLink.SUPPLIER_DEBTS)
            }
        }
    }
}

@Composable
private fun BalanceHalf(label: String, value: Figure, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clip(DsShapes.medium).background(accent.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onClick).padding(DsSpacing.md),
    ) {
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 2)
        Spacer(Modifier.height(DsSpacing.xs))
        AnimatedFigure(value, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
    }
}
