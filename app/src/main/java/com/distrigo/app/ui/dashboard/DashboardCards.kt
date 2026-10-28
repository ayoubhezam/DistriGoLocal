package com.distrigo.app.ui.dashboard

import com.distrigo.app.ui.designsystem.skeleton
import com.distrigo.app.ui.designsystem.SkeletonBar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import com.distrigo.app.data.repository.ActivityFilter
import com.distrigo.app.data.repository.PaymentState
import com.distrigo.app.data.repository.RecentSale
import com.distrigo.app.data.repository.TourneeDay
import com.distrigo.app.data.repository.VisitState
import com.distrigo.app.ui.common.EntityAvatar
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.rapports.CardTitle
import com.distrigo.app.ui.rapports.ReportInfo
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.distrigo.app.data.repository.TourneeDayClient
import androidx.compose.ui.res.stringResource
import com.distrigo.app.ui.designsystem.LocalArabicTrialFonts
import com.distrigo.app.R

@Composable
private fun infoTournee() = ReportInfo(stringResource(R.string.dashboard_tournee_title), stringResource(R.string.dashboard_tournee_info))
private val DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

// ── Tournée du jour (Part 1 §6–7) ──

private val VisitState.color: Color
    get() = when (this) {
        VisitState.WITH_SALE -> DsColors.Success
        VisitState.WITHOUT_SALE -> DsColors.Warning
        VisitState.PENDING -> DsColors.TextTertiary
    }

/** The open tournée: how many clients are visited, then with and without a sale, and those still to see. */
@Composable
internal fun TourneeCard(tournee: TourneeDay, onClick: () -> Unit) {
    val fraction = if (tournee.total > 0) tournee.visited.toFloat() / tournee.total else 0f
    Column(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large).clickable(role = Role.Button, onClick = onClick).padding(DsSpacing.lg),
    ) {
        CardTitle(stringResource(R.string.dashboard_tournee_title), infoTournee())
        Text(tournee.label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)

        val visitedDescription = stringResource(R.string.dashboard_tournee_visited_cd, tournee.visited, tournee.total)
        Spacer(Modifier.height(DsSpacing.md))
        Text(
            stringResource(R.string.dashboard_tournee_visited, tournee.visited, tournee.total),
            fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary,
        )
        Spacer(Modifier.height(DsSpacing.xs))
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(DsShapes.pill).background(DsColors.SurfaceSunken)
                .semantics { contentDescription = visitedDescription }
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(DsShapes.pill).background(DsColors.PrimaryDark))
        }
        Spacer(Modifier.height(DsSpacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            VisitCount(stringResource(R.string.dashboard_visit_with_sale), tournee.withSale, VisitState.WITH_SALE.color, Modifier.weight(1f))
            VisitCount(stringResource(R.string.dashboard_visit_without_sale), tournee.withoutSale, VisitState.WITHOUT_SALE.color, Modifier.weight(1f))
            VisitCount(stringResource(R.string.dashboard_visit_pending), tournee.pending, DsColors.TextSecondary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun VisitCount(label: String, count: Int, color: Color, modifier: Modifier) {
    Column(modifier.clip(DsShapes.medium).background(color.copy(alpha = 0.08f)).padding(DsSpacing.sm)) {
        Text("$count", fontSize = DsTextSize.title, fontWeight = FontWeight.ExtraBold, color = color,
            fontFamily = LocalArabicTrialFonts.current?.ui)
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * « Clients du jour » (D7): the tournée's clients in a row, each with its visit's colour; « Ajouter » first, « Voir tout »
 * last. A client opens [onClient], the tournée's own action sheet.
 */
@Composable
internal fun TourneeClientsRow(tournee: TourneeDay, onAdd: () -> Unit, onSeeAll: () -> Unit, onClient: (TourneeDayClient) -> Unit) {
    val collator = Collator.getInstance(FR)
    val clients = tournee.clients.sortedWith { a, b -> collator.compare(a.name.trim(), b.name.trim()) }
    Column {
        Text(
            stringResource(R.string.dashboard_clients_of_day), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark,
            fontFamily = LocalArabicTrialFonts.current?.ui,
            modifier = Modifier.padding(horizontal = DsSpacing.lg),
        )
        Spacer(Modifier.height(DsSpacing.sm))
        LazyRow(
            contentPadding = PaddingValues(horizontal = DsSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.md),
        ) {
            item(key = "add") {
                RoundAction(stringResource(R.string.common_action_add), Icons.Default.Add, DsColors.PrimaryDark, onAdd)
            }
            items(clients, key = { it.clientId }) { client ->
                Column(
                    // Not clipped: a rounded corner cut the first letter of a name as long as the cell
                    Modifier.width(64.dp).clickable(role = Role.Button, onClickLabel = client.name) { onClient(client) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        // Circles, as in the Figma design
                        EntityAvatar(name = client.name, imageUri = client.imageUri, size = 52.dp, shape = CircleShape)
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(DsColors.Surface).padding(2.dp)
                                .clip(CircleShape).background(client.state.color)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        client.name, fontSize = DsTextSize.caption, color = DsColors.TextPrimary, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    )
                }
            }
            item(key = "all") {
                RoundAction(stringResource(R.string.common_action_see_all), Icons.AutoMirrored.Filled.ArrowForward, DsColors.TextSecondary, onSeeAll)
            }
        }
    }
}

@Composable
private fun RoundAction(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp).clip(DsShapes.medium).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(DsColors.Surface).border(1.5.dp, color.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, textAlign = TextAlign.Center,
            fontFamily = LocalArabicTrialFonts.current?.ui)
    }
}

// ── Activité récente (Part 1 §8) ──

/** The section's title and its filter « Tous / Payées / À crédit » (D11), each at least 40 dp tall. */
@Composable
internal fun ActivityHeader(selected: ActivityFilter, onSelect: (ActivityFilter) -> Unit) {
    Column(Modifier.padding(horizontal = DsSpacing.lg)) {
        Text(stringResource(R.string.dashboard_recent_activity), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark,
            fontFamily = LocalArabicTrialFonts.current?.ui)
        Spacer(Modifier.height(DsSpacing.sm))
        Row(
            Modifier.fillMaxWidth().clip(DsShapes.pill).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.pill).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ActivityFilter.entries.forEach { filter ->
                val on = filter == selected
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp).clip(DsShapes.pill)
                        .background(if (on) DsColors.PrimaryDark else Color.Transparent)
                        .clickable(role = Role.Tab, onClick = { onSelect(filter) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(filter.label), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold,
                        fontFamily = LocalArabicTrialFonts.current?.ui,
                        color = if (on) Color.White else DsColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
internal fun ActivityEmpty(filter: ActivityFilter) {
    Text(
        when (filter) {
            ActivityFilter.ALL -> stringResource(R.string.dashboard_activity_empty_all)
            ActivityFilter.PAID -> stringResource(R.string.dashboard_activity_empty_paid)
            ActivityFilter.CREDIT -> stringResource(R.string.dashboard_activity_empty_credit)
        },
        fontSize = DsTextSize.body, color = DsColors.TextSecondary, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.lg),
    )
}

/** « 14:32 » today, « 17/10 · 14:32 » before. */
private fun saleTime(createdAt: String, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = runCatching { Instant.parse(createdAt).atZone(zone) }.getOrNull() ?: return ""
    return if (at.toLocalDate() == LocalDate.now(zone)) at.format(TIME) else "${at.format(DAY_MONTH)} · ${at.format(TIME)}"
}

/** A recent sale: the client, its number and time, the amount, and how it was paid (no delivery status, D4). */
/** How many sales « Activité récente » shows: its skeleton has as many rows. */
internal const val RECENT_SALES = 4

/** A row's height, the same loaded or not: its skeleton then takes exactly its place. */
private val RECENT_SALE_ROW_HEIGHT = 60.dp

/** [RecentSaleRow]'s shape while the sales are read. */
@Composable
internal fun RecentSaleRowSkeleton() {
    Row(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().height(RECENT_SALE_ROW_HEIGHT).clip(DsShapes.large)
            .background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.large)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).skeleton(DsShapes.medium))
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBar(120.dp, 12.dp)
            SkeletonBar(160.dp, 10.dp)
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBar(80.dp, 12.dp)
            SkeletonBar(48.dp, 14.dp)
        }
    }
}

@Composable
internal fun RecentSaleRow(sale: RecentSale, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val (pill, color) = when (sale.state) {
        PaymentState.PAID -> stringResource(R.string.common_status_paid) to DsColors.Success
        PaymentState.PARTIAL -> stringResource(R.string.common_status_partial) to DsColors.Warning
        PaymentState.UNPAID -> stringResource(R.string.common_status_unpaid) to DsColors.Danger
    }
    Row(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large).clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = RECENT_SALE_ROW_HEIGHT).padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntityAvatar(name = sale.clientName ?: "?", imageUri = sale.clientImage, size = 40.dp)
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(sale.clientName ?: stringResource(R.string.common_label_client), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val where = stringResource(if (sale.source == "camion") R.string.common_source_tournee else R.string.common_source_depot)
            // The time first: a long number is what the ellipsis may cut, not when the sale was made
            Text(
                listOfNotNull(saleTime(sale.createdAt).takeIf { it.isNotEmpty() }, where, sale.numero).joinToString(" · "),
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(money.da(sale.total), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                pill, fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = color,
                modifier = Modifier.clip(DsShapes.pill).background(color.copy(alpha = 0.12f)).padding(horizontal = DsSpacing.sm, vertical = 2.dp),
            )
        }
    }
}
