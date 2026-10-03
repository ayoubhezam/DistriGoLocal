package com.distrigo.app.ui.rapports

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.repository.AgeBand
import com.distrigo.app.data.repository.DebtReport
import com.distrigo.app.data.repository.DebtSide
import com.distrigo.app.data.repository.DebtorLine
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.format.LocalMoneyFormatter
import java.time.format.DateTimeFormatter

/** Each age band's colour: green while recent, to red past 90 days. */
private val AgeColors = mapOf(
    AgeBand.RECENT to Color(0xFF12B76A),
    AgeBand.MONTH to Color(0xFFFDB022),
    AgeBand.TWO_MONTHS to Color(0xFFF79009),
    AgeBand.OLD to Color(0xFFF04438),
)

private val DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** The words that change with the side, so the layout is written once. */
private class SideWords(
    val owed: String, val debtors: (Int) -> String, val credit: String,
    val payments: String, val paymentUnit: Pair<String, String>, val returns: String, val listTitle: String,
    val noPayment: String, val lastPayment: String, val noDebt: String,
)

private fun wordsFor(side: DebtSide) = when (side) {
    DebtSide.CLIENTS -> SideWords(
        owed = "Reste à encaisser",
        debtors = { plural(it, "client débiteur", "clients débiteurs") },
        credit = "Crédit accordé",
        payments = "Versements reçus",
        paymentUnit = "versement" to "versements",
        returns = "Retours clients",
        listTitle = "Clients débiteurs",
        noPayment = "Aucun versement",
        lastPayment = "Dernier versement le",
        noDebt = "Aucun client ne vous doit d'argent.",
    )
    DebtSide.FOURNISSEURS -> SideWords(
        owed = "Reste à payer",
        debtors = { plural(it, "fournisseur à payer", "fournisseurs à payer") },
        credit = "Achats à crédit",
        payments = "Versements payés",
        paymentUnit = "versement" to "versements",
        returns = "Retours fournisseurs",
        listTitle = "Fournisseurs à payer",
        noPayment = "Aucun versement",
        lastPayment = "Dernier versement le",
        noDebt = "Vous ne devez rien à vos fournisseurs.",
    )
}

/**
 * Rapports › Créances et dettes: what clients owe and what is owed to suppliers. Today's balances —
 * their total, how old they are, who owes them — and what the period did to them: credit given,
 * payments, returns.
 */
@Composable
fun DebtReportScreen(onBack: () -> Unit, viewModel: DebtReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val words = wordsFor(state.side)

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = "Créances et dettes", subtitle = "Ce qui reste à encaisser et à payer", leading = DsTopBarLeading.Back(onBack))
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
            item { ReportFilterBar(state.filter, viewModel::setFilter, showSource = false) }
            item {
                ReportSegmented(
                    DebtSide.entries, state.side, { it.label }, viewModel::setSide,
                    Modifier.padding(horizontal = DsSpacing.lg),
                )
            }

            val report = state.report
            when {
                report == null && state.error != null -> item { ReportMessage(state.error!!) }
                report == null -> item {
                    Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DsColors.Primary)
                    }
                }
                else -> {
                    item { OwedCard(report, words) }
                    item { SectionTitle("Sur la période") }
                    item { FlowTiles(report, words) }
                    if (report.debtors.isEmpty()) {
                        item { ReportMessage(words.noDebt) }
                    } else {
                        item { AgeCard(report) }
                        item { SectionTitle("${words.listTitle} (${report.debtors.size})") }
                        items(report.debtors, key = { it.id }) { DebtorRow(it, words) }
                    }
                }
            }
        }
    }
}

/** Today's balance: the total owed, by how many, and the newest and oldest part of it. */
@Composable
private fun OwedCard(report: DebtReport, words: SideWords) {
    val money = LocalMoneyFormatter.current
    ReportHeroCard(
        title = "${words.owed} · au ${report.today.format(DAY)}",
        amount = money.da(report.outstanding),
        caption = words.debtors(report.debtors.size),
        halves = if (report.outstanding > 0) listOf(
            HeroHalf("Moins de 30 jours", money.da(report.ages[AgeBand.RECENT.ordinal])),
            HeroHalf("Plus de 90 jours", money.da(report.ages[AgeBand.OLD.ordinal]), bold = true),
        ) else emptyList(),
    )
}

/** The period's flows, and what they did to the balance. */
@Composable
private fun FlowTiles(report: DebtReport, words: SideWords) {
    val money = LocalMoneyFormatter.current
    val (one, many) = words.paymentUnit
    val change = report.change
    Column(Modifier.padding(horizontal = DsSpacing.lg), verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile(words.credit, money.da(report.credit), DsColors.Warning, Modifier.weight(1f), null)
            KpiTile(
                words.payments, money.da(report.payments.total), DsColors.Success, Modifier.weight(1f),
                plural(report.payments.count, one, many),
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            KpiTile(
                words.returns, money.da(report.returns.total), DsColors.TextPrimary, Modifier.weight(1f),
                plural(report.returns.count, "retour", "retours"),
            )
            // More owed is bad news on either side: red when the balance grew, green when it shrank.
            val grew = change > 0.005
            val shrank = change < -0.005
            KpiTile(
                "Évolution du solde",
                if (grew) "+ ${money.da(change)}" else if (shrank) "− ${money.da(-change)}" else money.da(0.0),
                when { grew -> DsColors.Danger; shrank -> DsColors.Success; else -> DsColors.TextPrimary },
                Modifier.weight(1f),
                when { grew -> "En hausse"; shrank -> "En baisse"; else -> "Stable" },
            )
        }
    }
}

/** How old today's balance is: a bar of the four bands, then each band's amount and share. */
@Composable
private fun AgeCard(report: DebtReport) {
    val money = LocalMoneyFormatter.current
    val total = report.outstanding
    Column(
        Modifier
            .padding(horizontal = DsSpacing.lg)
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .padding(DsSpacing.lg)
    ) {
        Text("Ancienneté", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        Spacer(Modifier.height(2.dp))
        Text("Depuis combien de temps l'argent est dû", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        Spacer(Modifier.height(DsSpacing.md))
        Row(Modifier.fillMaxWidth().height(10.dp).clip(DsShapes.pill).background(DsColors.SurfaceSunken)) {
            AgeBand.entries.forEach { band ->
                val share = if (total > 0) (report.ages[band.ordinal] / total).toFloat() else 0f
                if (share > 0f) Box(Modifier.weight(share).fillMaxHeight().background(AgeColors.getValue(band)))
            }
        }
        Spacer(Modifier.height(DsSpacing.md))
        AgeBand.entries.forEach { band ->
            val amount = report.ages[band.ordinal]
            Row(Modifier.fillMaxWidth().padding(vertical = DsSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(DsShapes.pill).background(AgeColors.getValue(band)))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(band.label, fontSize = DsTextSize.body, color = DsColors.TextPrimary, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(money.da(amount), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                    Text(percentOf(amount, total) ?: percent(0.0), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                }
            }
        }
    }
}

/** One party that owes or is owed: its name and last payment, its balance and how old its oldest debt is. */
@Composable
private fun DebtorRow(line: DebtorLine, words: SideWords) {
    val money = LocalMoneyFormatter.current
    val color = AgeColors.getValue(line.oldest)
    Row(
        Modifier
            .padding(horizontal = DsSpacing.lg)
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsColors.Surface)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                line.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                line.lastPayment?.let { "${words.lastPayment} ${it.format(DAY)}" } ?: words.noPayment,
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
            )
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            FitText(money.da(line.balance), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                line.oldest.label, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = color,
                modifier = Modifier.clip(DsShapes.pill).background(color.copy(alpha = 0.12f)).padding(horizontal = DsSpacing.sm, vertical = 2.dp),
            )
        }
    }
}
