package com.distrigo.app.ui.dashboard

import kotlin.math.roundToInt
import com.distrigo.app.core.format.percentText
import com.distrigo.app.ui.designsystem.skeleton
import com.distrigo.app.ui.designsystem.SkeletonOnColor
import com.distrigo.app.ui.designsystem.SkeletonBar
import com.distrigo.app.ui.designsystem.DsShimmer
import com.distrigo.app.data.repository.ActivityFilter
import java.time.LocalDate
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.local.entity.incentive.PeriodType
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.incentive.GoalProgress
import com.distrigo.app.data.model.incentive.GoalState
import com.distrigo.app.data.repository.DashboardAlerts
import com.distrigo.app.data.repository.DashboardSales
import com.distrigo.app.data.repository.TourneeDay
import com.distrigo.app.ui.charges.NewChargeDialog
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.EntityAvatar
import com.distrigo.app.ui.common.PaymentDialog
import com.distrigo.app.ui.common.matchesAllTokens
import com.distrigo.app.ui.common.searchTokens
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsTopBarRootActions
import com.distrigo.app.ui.designsystem.DsTopBarSize
import com.distrigo.app.ui.designsystem.LocalLicense
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.navigation.DrillTarget
import com.distrigo.app.ui.navigation.LocalDrillDown
import com.distrigo.app.ui.navigation.ReportEntry
import com.distrigo.app.ui.rapports.AnimatedFigure
import com.distrigo.app.ui.rapports.CardColumn
import com.distrigo.app.ui.rapports.CardTitle
import com.distrigo.app.ui.rapports.Figure
import com.distrigo.app.ui.rapports.InfoButton
import com.distrigo.app.ui.rapports.ReportInfo
import com.distrigo.app.ui.rapports.countFigure
import com.distrigo.app.ui.rapports.figure
import com.distrigo.app.ui.rapports.percent
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.distrigo.app.data.model.TourneeClientInfo
import com.distrigo.app.data.repository.VisitState
import com.distrigo.app.ui.common.bidiIsolate
import com.distrigo.app.ui.tournees.TourneeClientActionsSheet
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

internal val FR: Locale = Locale.FRENCH

@Composable
private fun infoToday() = ReportInfo(stringResource(R.string.dashboard_today_sales), stringResource(R.string.dashboard_today_info))

@Composable
private fun infoAlerts() = ReportInfo(stringResource(R.string.dashboard_alerts), stringResource(R.string.dashboard_alerts_info))

/**
 * The Dashboard (docs/design/dashboard_objectif_plan.md, Part 1): today's sales against « hier », the objective, the
 * quick actions, the alerts when there are any, the tournée of the day with its clients, and the latest sales.
 */
@Composable
fun DashboardScreen(
    onOpenReport         : (ReportEntry) -> Unit = {},
    onOpenMenu           : (() -> Unit)? = null,
    onNotificationsClick : () -> Unit = {},
    onProfileClick       : () -> Unit = {},
    /** D14: the tournée sale when [tourneeId] is open, otherwise Dépôt Vente. */
    onNewSale            : (tourneeId: Int?) -> Unit = {},
    onNewPurchase        : () -> Unit = {},
    onAddTourneeClients  : (TourneeDay) -> Unit = {},
    /** « Fixez un objectif »: the commission policy in Paramètres. */
    onSetObjective       : () -> Unit = {},
    /** « Créer une vente » on a client of the day: the tournée sale, the client already chosen. */
    onTourneeClientSale  : (tourneeId: Int, clientId: Int, clientName: String) -> Unit = { _, _, _ -> },
    /** « Objectif du mois » on the card: « Mon objectif » (D20). */
    onOpenGoal           : () -> Unit = {},
    viewModel            : DashboardViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsState()
    val content = (ui as? DashboardUiState.Ready)?.content
    val license = LocalLicense.current
    val drill = LocalDrillDown.current
    val open = { link: DashboardLink -> viewModel.prepare(link); onOpenReport(link.report) }
    var addingCharge by remember { mutableStateOf(false) }
    var collecting by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<Client?>(null) }
    // « Clients du jour »: the client whose sheet is open, then the confirmations the tournée asks for
    var clientSheet by remember { mutableStateOf<ClientSheet?>(null) }
    var confirmSale by remember { mutableStateOf<ClientSheet?>(null) }
    var confirmRemove by remember { mutableStateOf<ClientSheet?>(null) }
    var removeError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tournee = content?.tournee
    // « Nouvelle vente » with a tournée open: « Vente dépôt » or « Vente tournée », from where the tile sits
    val saleDial = remember { NewSaleSpeedDialState() }
    var saleTile by remember { mutableStateOf<Rect?>(null) }
    var saleTileFont by remember { mutableStateOf(DsTextSize.caption) }
    // The tournée the dial was opened for: kept while it folds away after that tournée closed
    var dialTournee by remember { mutableStateOf<TourneeDay?>(null) }
    LaunchedEffect(tournee) {
        if (tournee != null) dialTournee = tournee else saleDial.close()
    }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        // D16: the greeting and the date; the menu stays, the drawer being the only way to the other sections
        DsTopAppBar(
            title         = content?.firstName?.let { stringResource(R.string.dashboard_hello_name, it) } ?: stringResource(R.string.dashboard_hello),
            // From the clock, not from the sales: waiting for them, the line arrived late and the bar grew
            subtitle      = LocalDate.now().format(com.distrigo.app.core.format.AppDates.formatter("EEEE d MMMM")).replaceFirstChar { it.uppercase() },
            licenseBanner = true,
            leading       = onOpenMenu?.let { DsTopBarLeading.Menu(it) } ?: DsTopBarLeading.None,
            size          = DsTopBarSize.Large
        ) {
            DsTopBarRootActions(onNotificationsClick = onNotificationsClick, onProfileClick = onProfileClick)
        }

        // Owner 2026-10-22: the skeleton until every block is read, then the Dashboard in one crossfade — loaded
        // apart, each block pushed the ones below it down
        Crossfade(targetState = content == null, animationSpec = tween(200), label = "dashboard") { loading ->
        val c = content
        if (loading || c == null) {
            DashboardSkeleton(withTournee = viewModel.tourneeWasOpen)
            return@Crossfade
        }
        val sales = c.sales
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(top = DsSpacing.sm, bottom = DsSpacing.bottomNavClearance),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        ) {
            // The chip shares the first item with Ventes du jour: as an item of its own, arriving after the card, it
            // would be inserted above the list's anchor and stay scrolled out of sight
            item(key = "today") {
                Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                    tournee?.let { t -> TourneeChip(t) { drill(DrillTarget.Tournee(t.id)) } }
                    if (sales == null) {
                        // Could not be read: the card's place is kept, as while loading
                        DsShimmer { TodayCardSkeleton() }
                    } else {
                        TodayCard(
                            sales          = sales,
                            goal           = c.goal,
                            onClick        = { open(DashboardLink.TODAY) },
                            onGoal         = onOpenGoal,
                            onMonth        = { open(DashboardLink.MONTH) },
                            onSetObjective = onSetObjective,
                        )
                    }
                }
            }

            item(key = "actions") {
                QuickActions(
                    // No tournée: the dépôt sale straight away. One open: the choice between the two.
                    onNewSale     = { if (tournee == null) license.runOrExplain { onNewSale(null) } else saleDial.open() },
                    saleClickLabel = stringResource(if (tournee == null) R.string.dashboard_new_sale else R.string.dashboard_cd_open_choice),
                    onSaleTile    = { bounds, font -> saleTile = bounds; saleTileFont = font },
                    onCollect     = { license.runOrExplain { collecting = true } },
                    onNewPurchase = { license.runOrExplain(onNewPurchase) },
                    onCharge      = { license.runOrExplain { addingCharge = true } },
                )
            }

            // The tournée and its clients before the alerts (owner 2026-10-20)
            tournee?.let { t ->
                item(key = "tournee") { TourneeCard(t) { drill(DrillTarget.Tournee(t.id)) } }
                item(key = "tournee_clients") {
                    TourneeClientsRow(
                        tournee  = t,
                        onAdd    = { license.runOrExplain { onAddTourneeClients(t) } },
                        onSeeAll = { drill(DrillTarget.Tournee(t.id)) },
                        onClient = { c ->
                            scope.launch {
                                viewModel.tourneeClient(t.id, c.clientId)?.let {
                                    clientSheet = ClientSheet(t.id, it, hasSale = c.state == VisitState.WITH_SALE)
                                }
                            }
                        },
                    )
                }
            }

            // D12: only when there is something to see to
            c.alerts?.takeIf { it.any }?.let { alerts ->
                item(key = "alerts") { AlertsCard(alerts) { open(it) } }
            }

            item(key = "activity_title") {
                ActivityHeader(c.activityFilter, viewModel::showActivity)
            }
            if (c.activity.isEmpty()) {
                item(key = "activity_empty") { ActivityEmpty(c.activityFilter) }
            } else {
                items(c.activity, key = { "sale_${it.id}" }) { sale ->
                    RecentSaleRow(sale) { drill(DrillTarget.Vente(sale.id)) }
                }
            }
        }
        }
    }

    clientSheet?.let { sheet ->
        val info = sheet.info
        TourneeClientActionsSheet(
            info          = info,
            hasVente      = sheet.hasSale,
            // The Dashboard shows only the open tournée
            isOpen        = true,
            // As in the tournée: a client already served asks before a second sale
            onCreateSale  = {
                clientSheet = null
                if (sheet.hasSale) confirmSale = sheet
                else license.runOrExplain { onTourneeClientSale(sheet.tourneeId, info.client.id, info.client.name) }
            },
            onMarkVisited = {
                clientSheet = null
                license.runOrExplain {
                    viewModel.markVisited(sheet.tourneeId, info.client.id) { error ->
                        android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onRemove      = { clientSheet = null; license.runOrExplain { confirmRemove = sheet } },
            onDismiss     = { clientSheet = null },
        )
    }
    confirmSale?.let { sheet ->
        AlertDialog(
            onDismissRequest = { confirmSale = null },
            title = { Text(stringResource(R.string.dashboard_create_sale_title)) },
            text  = { Text(stringResource(R.string.dashboard_create_sale_body, sheet.info.client.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSale = null
                    license.runOrExplain { onTourneeClientSale(sheet.tourneeId, sheet.info.client.id, sheet.info.client.name) }
                }) {
Text(stringResource(R.string.common_yes), color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmSale = null }) { Text(stringResource(R.string.common_action_cancel)) } },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }
    confirmRemove?.let { sheet ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null; removeError = "" },
            title = { Text(stringResource(R.string.dashboard_remove_client_title), fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    Text(
                        stringResource(R.string.dashboard_remove_client_body, bidiIsolate(sheet.info.client.name))
                    )
                    if (removeError.isNotEmpty()) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text(removeError, color = DsColors.Danger, fontSize = DsTextSize.caption)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFromTournee(
                        tourneeId = sheet.tourneeId,
                        clientId  = sheet.info.client.id,
                        onSuccess = { confirmRemove = null; removeError = "" },
                        onError   = { removeError = it },
                    )
                }) {
                    Text(stringResource(R.string.common_action_remove), color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null; removeError = "" }) { Text(stringResource(R.string.common_action_cancel)) } },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    saleTile?.let { bounds ->
        dialTournee?.let { t ->
            NewSaleSpeedDial(
                state         = saleDial,
                tile          = bounds,
                tileLabelSize = saleTileFont,
                tourneeLabel  = t.label,
                // Each behind the license, as the tile was
                onDepot       = { license.runOrExplain { onNewSale(null) } },
                onTournee     = { license.runOrExplain { onNewSale(t.id) } },
            )
        }
    }
    if (addingCharge) NewChargeDialog(onDismiss = { addingCharge = false })
    if (collecting) {
        CollectSheet(
            load      = viewModel::clientsOwing,
            onPick    = { client -> collecting = false; paying = client },
            onDismiss = { collecting = false },
        )
    }
    paying?.let { client ->
        PaymentDialog(
            balance   = client.balance,
            onSubmit  = { amount, note, onError, onSuccess ->
                viewModel.addPayment(client.id, amount, note, onSuccess = { onSuccess(); paying = null }, onError = onError)
            },
            onDismiss = { paying = null },
        )
    }
}

/** A client of the day whose sheet is open: its tournée, the client as the sheet reads it, and whether it has bought. */
private data class ClientSheet(val tourneeId: Int, val info: TourneeClientInfo, val hasSale: Boolean)

/** The open tournée, as a chip under the greeting: where the rep is today. */
@Composable
private fun TourneeChip(tournee: TourneeDay, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = DsSpacing.lg).height(TOURNEE_CHIP_HEIGHT).clip(DsShapes.pill).background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.pill).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(DsColors.Success))
        Spacer(Modifier.width(DsSpacing.sm))
        Icon(Icons.Default.LocalShipping, contentDescription = null, tint = DsColors.PrimaryDark, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(DsSpacing.xs))
        Text(
            stringResource(R.string.dashboard_tournee_open, tournee.label),
            fontFamily = com.distrigo.app.ui.designsystem.LocalArabicFonts.current?.ui,
            fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The Dashboard's one card (the Figma design, owner 2026-10-20): Ventes du jour, its title and amount alone — no
 * comparison, no percentage —, the objective's bar and amount, then « Payé à la vente » and « Crédit ». Without a
 * commission policy the objective's place shows the month's sales and « Fixez un objectif ».
 */
// The tournée chip's height, one line whatever its label: its skeleton holds the same place
private val TOURNEE_CHIP_HEIGHT = 40.dp

// « Tournée du jour »'s height, measured the same way
private val TOURNEE_CARD_HEIGHT = 182.dp

// The Ventes du jour card's height with an objective, its usual look, measured on the M34 at the default font
// size: the skeleton holds exactly that place
private val TODAY_CARD_HEIGHT = 219.dp

/**
 * The Dashboard before its figures are read: the real top bar and quick actions, and in place of the rest shapes of
 * their size — the card, the activity's title and its four rows — so nothing moves when they arrive.
 */
@Composable
private fun DashboardSkeleton(withTournee: Boolean) {
    val loadingDescription = stringResource(R.string.dashboard_cd_loading)
    DsShimmer {
        Column(
            Modifier.fillMaxSize().padding(top = DsSpacing.sm).semantics { contentDescription = loadingDescription },
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        ) {
            // The chip shares the card's item, as on the loaded Dashboard
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                if (withTournee) {
                    Box(Modifier.padding(horizontal = DsSpacing.lg).width(240.dp).height(TOURNEE_CHIP_HEIGHT).skeleton(DsShapes.pill))
                }
                TodayCardSkeleton()
            }
            // Real, with nothing to wait for; a tap waits for the Dashboard, which knows the open tournée
            QuickActions(onNewSale = {}, onCollect = {}, onNewPurchase = {}, onCharge = {})
            if (withTournee) TourneeCardSkeleton()
            ActivityHeader(ActivityFilter.ALL) {}
            repeat(RECENT_SALES) { RecentSaleRowSkeleton() }
        }
    }
}

/** « Tournée du jour »'s shape: its title and place, the visits line and bar, the three counts. */
@Composable
private fun TourneeCardSkeleton() {
    Column(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().height(TOURNEE_CARD_HEIGHT).clip(DsShapes.large)
            .background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.large).padding(DsSpacing.lg),
    ) {
        SkeletonBar(140.dp, 16.dp)
        SkeletonBar(90.dp, 10.dp, Modifier.padding(top = 8.dp))
        SkeletonBar(160.dp, 12.dp, Modifier.padding(top = 16.dp))
        SkeletonBar(0.dp, 8.dp, Modifier.fillMaxWidth().padding(top = 10.dp))
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
            repeat(3) { Box(Modifier.weight(1f).height(48.dp).skeleton(DsShapes.medium)) }
        }
    }
}

/** [TodayCard]'s shape: the title, the amount, the objective's lines and bar, then « Payé à la vente » and « Crédit ». */
@Composable
private fun TodayCardSkeleton() {
    Column(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().height(TODAY_CARD_HEIGHT).clip(DsShapes.large)
            .background(DsColors.Primary).padding(DsSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SkeletonBar(110.dp, 14.dp, Modifier.padding(top = 4.dp), base = SkeletonOnColor)
        SkeletonBar(190.dp, 30.dp, Modifier.padding(top = 12.dp), base = SkeletonOnColor)
        Spacer(Modifier.height(DsSpacing.lg))
        Row(Modifier.fillMaxWidth()) {
            SkeletonBar(120.dp, 12.dp, base = SkeletonOnColor)
            Spacer(Modifier.weight(1f))
            SkeletonBar(40.dp, 12.dp, base = SkeletonOnColor)
        }
        SkeletonBar(0.dp, 8.dp, Modifier.fillMaxWidth().padding(top = 10.dp), base = SkeletonOnColor)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            SkeletonBar(150.dp, 12.dp, base = SkeletonOnColor)
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
        Spacer(Modifier.height(DsSpacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
            repeat(2) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    SkeletonBar(80.dp, 10.dp, base = SkeletonOnColor)
                    SkeletonBar(100.dp, 16.dp, Modifier.padding(top = 6.dp), base = SkeletonOnColor)
                }
            }
        }
    }
}

@Composable
private fun TodayCard(
    sales: DashboardSales,
    goal: GoalProgress?,
    onClick: () -> Unit,
    onGoal: () -> Unit,
    /** Without a policy: the month's sales, to their report. */
    onMonth: () -> Unit,
    onSetObjective: () -> Unit,
) {
    val money = LocalMoneyFormatter.current
    val white = Color.White
    Column(
        Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth().clip(DsShapes.large).background(DsColors.Primary)
            .clickable(role = Role.Button, onClick = onClick).padding(DsSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.dashboard_today_sales), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = white.copy(alpha = 0.9f),
                fontFamily = com.distrigo.app.ui.designsystem.LocalArabicFonts.current?.ui)
            InfoButton(infoToday(), tint = white.copy(alpha = 0.75f))
        }
        com.distrigo.app.ui.designsystem.ArabicDisplay {
            AnimatedFigure(money.figure(sales.today.total), fontSize = DsTextSize.display, fontWeight = FontWeight.ExtraBold,
                color = white, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(DsSpacing.md))
        if (goal != null) HeroGoal(goal, onGoal) else HeroMonth(sales, onMonth, onSetObjective)

        Spacer(Modifier.height(DsSpacing.md))
        HorizontalDivider(color = white.copy(alpha = 0.25f))
        Spacer(Modifier.height(DsSpacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
listOf(stringResource(R.string.dashboard_paid_at_sale) to sales.today.paid, stringResource(R.string.dashboard_credit) to sales.today.credit).forEach { (label, value) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = DsTextSize.caption, color = white.copy(alpha = 0.75f), textAlign = TextAlign.Center)
                    AnimatedFigure(money.figure(value), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold,
                        color = white, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun GoalProgress.title(): String = stringResource(
    when (period.type) {
        PeriodType.MONTHLY -> R.string.dashboard_goal_month
        PeriodType.QUARTERLY -> R.string.dashboard_goal_quarter
        PeriodType.YEARLY -> R.string.dashboard_goal_year
    }
)

// The goal's colour on the card's blue (owner, 2026-10-20): white on the way, green once reached, gold beyond. Both
// are light tints, readable on the blue where the app's own green would not be.
private val GoalReached = Color(0xFF86EFAC)
private val GoalExceeded = Color(0xFFFFD54F)

/**
 * The objective in the card: its name, « réalisé / cible DA », the percent, and the bar. In « Paliers » the target
 * is the next tier, named after the title, and the last one once all are passed ([GoalProgress.target]).
 */
@Composable
private fun HeroGoal(goal: GoalProgress, onClick: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val white = Color.White
    val target = goal.target
    val color = when (target.state) {
        GoalState.BELOW -> white
        GoalState.REACHED -> GoalReached
        GoalState.EXCEEDED -> GoalExceeded
    }
    Column(Modifier.fillMaxWidth().clip(DsShapes.medium).clickable(role = Role.Button, onClick = onClick).padding(vertical = DsSpacing.xs)) {
        Text(
            listOfNotNull(goal.title(), target.tier?.let { stringResource(R.string.dashboard_goal_tier, it) }).joinToString(" · "),
            fontSize = DsTextSize.caption, color = white.copy(alpha = 0.75f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.dashboard_goal_progress, money.amount(goal.value), money.da(target.threshold)), fontSize = DsTextSize.bodySmall,
                fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(DsSpacing.sm))
            Text(percentText("${target.percent}"), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(DsSpacing.xs))
        val fraction = if (target.threshold > 0) (goal.value / target.threshold).coerceIn(0.0, 1.0).toFloat() else 0f
        val goalDescription = target.tier?.let { stringResource(R.string.dashboard_cd_goal_tier, percentText("${target.percent}"), it) }
            ?: stringResource(R.string.dashboard_cd_goal, percentText("${target.percent}"))
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(DsShapes.pill).background(white.copy(alpha = 0.25f))
                .semantics {
                    contentDescription = goalDescription
                }
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(DsShapes.pill).background(color))
        }
    }
}

/** No commission policy: the month's sales, and « Fixez un objectif » (D3). */
@Composable
private fun HeroMonth(sales: DashboardSales, onClick: () -> Unit, onSetObjective: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val white = Color.White
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clip(DsShapes.medium).clickable(role = Role.Button, onClick = onClick).padding(vertical = DsSpacing.xs)) {
            Text(stringResource(R.string.dashboard_month_sales), fontSize = DsTextSize.caption, color = white.copy(alpha = 0.75f))
            Text(money.da(sales.month.total), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = white,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Row(
            Modifier.clip(DsShapes.pill).background(white.copy(alpha = 0.18f)).clickable(role = Role.Button, onClick = onSetObjective)
                .heightIn(min = 36.dp).padding(horizontal = DsSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = white, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.dashboard_set_goal), fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = white,
                fontFamily = com.distrigo.app.ui.designsystem.LocalArabicFonts.current?.ui)
        }
    }
}

/** The four quick actions, each behind the license (L5): Nouvelle vente, Encaissement, Nouvel achat, Charge. */
@Composable
private fun QuickActions(
    onNewSale: () -> Unit, onCollect: () -> Unit, onNewPurchase: () -> Unit, onCharge: () -> Unit,
    /** What a tap on « Nouvelle vente » does, said to accessibility. */
    saleClickLabel: String = stringResource(R.string.dashboard_new_sale),
    /** Where « Nouvelle vente » sits in the window and its label's size: the speed-dial draws it again there. */
    onSaleTile: (Rect, TextUnit) -> Unit = { _, _ -> },
) {
    BoxWithConstraints(Modifier.padding(horizontal = DsSpacing.lg).fillMaxWidth()) {
        // One size for the four labels, small enough for the longest word to fit a tile whole (« Encaissement » on a
        // narrow phone or a large font), so no word breaks in two
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val tile = (maxWidth - DsSpacing.sm * 3) / 4 - DsSpacing.xs * 2
        val labelStyle = LocalTextStyle.current.merge(TextStyle(fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold))
        val labels = listOf(R.string.dashboard_new_sale, R.string.dashboard_collect, R.string.dashboard_new_purchase, R.string.dashboard_charge)
            .map { stringResource(it) }
        // Every word of the four labels, in the app's language
        val longest = remember(measurer, labels) {
            labels.flatMap { it.split(' ') }.maxOf { measurer.measure(it, labelStyle, softWrap = false).size.width }
        }
        val fontSize = with(density) {
            if (longest <= tile.toPx()) DsTextSize.caption else DsTextSize.caption * (tile.toPx() / longest)
        }
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
        ) {
            QuickAction(
                Icons.Default.PointOfSale, labels[0], DsColors.PrimaryDark, fontSize,
                Modifier.weight(1f).onGloballyPositioned { onSaleTile(it.boundsInWindow(), fontSize) },
                onNewSale, clickLabel = saleClickLabel,
            )
            QuickAction(Icons.Default.Payments, labels[1], DsColors.Success, fontSize, Modifier.weight(1f), onCollect)
            QuickAction(Icons.Default.AddShoppingCart, labels[2], DsColors.Primary, fontSize, Modifier.weight(1f), onNewPurchase)
            QuickAction(Icons.Default.Receipt, labels[3], DsColors.Warning, fontSize, Modifier.weight(1f), onCharge)
        }
    }
}

/** A quick action's tile; [iconContent], when given, is drawn in its round in place of [icon] (the speed-dial's ✕). */
@Composable
internal fun QuickAction(
    icon: ImageVector, label: String, accent: Color, fontSize: TextUnit, modifier: Modifier, onClick: () -> Unit,
    clickLabel: String = label,
    iconContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxHeight().heightIn(min = 88.dp).clip(DsShapes.large).background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large).clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
            .padding(vertical = DsSpacing.md, horizontal = DsSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            if (iconContent != null) iconContent() else Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(DsSpacing.sm))
        Text(
            label, fontSize = fontSize, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary,
            fontFamily = com.distrigo.app.ui.designsystem.LocalArabicFonts.current?.ui,
            textAlign = TextAlign.Center, maxLines = 2, lineHeight = DsTextSize.bodySmall,
        )
    }
}

/** What needs seeing to, each line opening its list. Shown only when there is one (D12). */
@Composable
private fun AlertsCard(alerts: DashboardAlerts, onOpen: (DashboardLink) -> Unit) {
    val money = LocalMoneyFormatter.current
    CardColumn {
        val res = androidx.compose.ui.platform.LocalContext.current.resources
        CardTitle(stringResource(R.string.dashboard_alerts), infoAlerts())
        Spacer(Modifier.height(DsSpacing.sm))
        if (alerts.outOfStock > 0) AlertLine(Icons.Default.RemoveShoppingCart, DsColors.Danger, stringResource(R.string.dashboard_alert_out), stringResource(R.string.dashboard_alert_out_sub),
            productCount(res, alerts.outOfStock)) { onOpen(DashboardLink.RESTOCK) }
        if (alerts.lowStock > 0) AlertLine(Icons.Default.Inventory2, DsColors.Warning, stringResource(R.string.dashboard_alert_low), stringResource(R.string.dashboard_alert_low_sub),
            productCount(res, alerts.lowStock)) { onOpen(DashboardLink.RESTOCK) }
        if (alerts.oldClientDebt > 0.005) AlertLine(Icons.Default.HourglassBottom, DsColors.Danger, stringResource(R.string.dashboard_alert_debts), stringResource(R.string.dashboard_alert_debts_sub),
            money.figure(alerts.oldClientDebt)) { onOpen(DashboardLink.CLIENT_DEBTS) }
    }
}

/** « 12 produits », whole at every step of the count. */
private fun productCount(res: android.content.res.Resources, count: Int) =
    com.distrigo.app.ui.rapports.Figure(count.toDouble()) { res.getQuantityString(R.plurals.common_products_count, it.roundToInt(), it.roundToInt()) }

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

/**
 * « Encaissement » (D15): the clients who owe something, the largest balance first, with the app's search; a tap
 * opens the « Versement » dialog for that client.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectSheet(load: suspend () -> List<Client>, onPick: (Client) -> Unit, onDismiss: () -> Unit) {
    val money = LocalMoneyFormatter.current
    var clients by remember { mutableStateOf<List<Client>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { clients = runCatching { load() }.getOrDefault(emptyList()) }
    val tokens = searchTokens(query)
    val shown = clients.orEmpty().filter { matchesAllTokens(tokens, it.name, it.phone, it.secteur_name) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = DsColors.Surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg)) {
            Text(stringResource(R.string.dashboard_collect), fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark)
            Text(stringResource(R.string.dashboard_collect_choose), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
            Spacer(Modifier.height(DsSpacing.md))
            DsCompactSearchField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.common_search_client))
            Spacer(Modifier.height(DsSpacing.sm))
        }
        when {
            clients == null -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xxl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.PrimaryDark)
            }
            shown.isEmpty() -> Text(
                stringResource(if (clients.orEmpty().isEmpty()) R.string.dashboard_collect_nobody_owes else R.string.common_no_client_matches),
                fontSize = DsTextSize.body, color = DsColors.TextSecondary,
                modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xl),
            )
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), contentPadding = PaddingValues(bottom = DsSpacing.xxl)) {
                items(shown, key = { it.id }) { client ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(client) }
                            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EntityAvatar(name = client.name, imageUri = client.image_uri, size = 40.dp)
                        Spacer(Modifier.width(DsSpacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(client.name, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            client.secteur_name?.let {
                                Text(it, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.width(DsSpacing.sm))
                        Text(money.da(client.balance), fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Danger, maxLines = 1)
                    }
                }
            }
        }
    }
}
