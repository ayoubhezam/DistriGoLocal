package com.distrigo.app.ui.goal

import com.distrigo.app.core.format.percentText
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.core.format.MoneyFormatter
import com.distrigo.app.data.local.entity.incentive.CalculationSource
import com.distrigo.app.data.local.entity.incentive.CommissionPeriodEntity
import com.distrigo.app.data.local.entity.incentive.PeriodType
import com.distrigo.app.data.model.Amount
import com.distrigo.app.data.model.incentive.CommissionPeriod
import com.distrigo.app.data.model.incentive.GoalProgress
import com.distrigo.app.data.model.incentive.GoalState
import com.distrigo.app.data.model.incentive.GoalStep
import com.distrigo.app.data.model.incentive.GoalSteps
import com.distrigo.app.data.model.incentive.PaceStatus
import com.distrigo.app.data.model.incentive.RewardResult
import com.distrigo.app.data.model.incentive.TierState
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsInfo
import com.distrigo.app.ui.designsystem.DsInfoSheet
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.format.AmountVisualTransformation
import com.distrigo.app.ui.format.LocalMoneyFormatter
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil

// The navy card's own shades (GoalScreen.tsx): soft text on navy, and the goal's colours as on the Dashboard (D18)
private val OnNavySoft = Color(0xFFCBD5E1)
private val OnNavyFaint = Color(0xFF94A3B8)
private val RingReached = Color(0xFF86EFAC)
private val RingExceeded = Color(0xFFFFD54F)
private val BarPast = Color(0xFFCBD5E1)
private val GoldOnWhite = Color(0xFFB7791F)
private val CardShape = RoundedCornerShape(24.dp)
private val NavyShape = RoundedCornerShape(28.dp)

/**
 * « Mon objectif » (D21, from the owner's Figma design, 2026-10-21): the navy card with its ring, « Reste à
 * réaliser » and « Rythme conseillé », the period in steps, the projection, the commission when the policy shows it,
 * and the past periods. The ring aims at the next tier ([GoalProgress.target]), and a row of markers shows the tiers.
 * With the bonus hidden the phone may be the rep's: no commission, and no edit button.
 */
@Composable
fun MonObjectifScreen(
    onBack: () -> Unit,
    /** Paramètres → Politique de commission: to set one, or to change « Paliers ». */
    onEditPolicy: () -> Unit,
    viewModel: MonObjectifViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val state by viewModel.state.collectAsState()
    val money = LocalMoneyFormatter.current
    var info by remember { mutableStateOf<DsInfo?>(null) }
    var editing by remember { mutableStateOf(false) }
    val goal = state.progress

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        Header(
            onBack = onBack,
            // D21: hidden with the bonus — the phone is then likely the rep's, who must not change their own target
            onEdit = if (goal != null && goal.showReward) {
                { if (goal.rules.sortedTiers.isNotEmpty()) onEditPolicy() else editing = true }
            } else null,
        )
        when {
            !state.loaded -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.PrimaryDark)
            }
            goal == null -> NoPolicy(onEditPolicy)
            else -> Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = DsSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.lg),
            ) {
                NavyCard(goal, onInfo = { info = ruleInfo(goal, state.freeGood, money) })
                TrajectoryCard(goal, state.freeGood)
                if (state.steps.isNotEmpty()) StepsCard(goal, state.steps)
                PastCard(goal, state.history)
            }
        }
    }
    info?.let { DsInfoSheet(it, onDismiss = { info = null }) }
    if (editing && goal != null) {
        ObjectiveSheet(
            goal = goal,
            lastAchieved = state.history.firstOrNull()?.achieved_value,
            onSave = { target, source, done, error -> viewModel.saveObjective(target, source, done, error) },
            onDismiss = { editing = false },
        )
    }
}

/** The rule in plain French, and how it is counted, behind the navy card's ⓘ. */
private fun ruleInfo(goal: GoalProgress, freeGood: String?, money: MoneyFormatter) = DsInfo(
    title = "La règle",
    icon = Icons.Default.EmojiEvents,
    tint = DsColors.Primary,
    summary = GoalWording.rule(goal.rules, goal.period.type, money, goal.showReward, freeGood),
    toKnow = listOf(
        "Ventes facturées = Ventes (dépôt + tournée) − Retours clients",
        "Recouvrement = Payé à la vente + Versements",
        "% = Réalisé ÷ Objectif × 100, arrondi vers le bas",
        "Une période terminée est figée.",
    ) + listOfNotNull(
        if (goal.policySince.isAfter(goal.period.start) && goal.policySince in goal.period)
            "Politique modifiée le ${goal.policySince.dayOfMonth}/${goal.policySince.monthValue}." else null,
    ),
)

@Composable
private fun Header(onBack: () -> Unit, onEdit: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = DsSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareButton(Icons.AutoMirrored.Filled.ArrowBack, "Retour", DsColors.Surface, DsColors.TextSecondary, onBack)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PERFORMANCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, color = DsColors.TextTertiary)
            Text("Mon objectif", fontSize = DsTextSize.title, fontWeight = FontWeight.ExtraBold, color = DsColors.PrimaryDark)
        }
        if (onEdit != null) SquareButton(Icons.Outlined.Edit, "Modifier l'objectif", DsColors.PrimaryLight, DsColors.Primary, onEdit)
        else Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun SquareButton(icon: ImageVector, label: String, background: Color, tint: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.size(48.dp).clip(shape).background(background)
            .border(1.dp, if (background == DsColors.Surface) DsColors.Border else Color.Transparent, shape)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun NoPolicy(onEditPolicy: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(DsSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
    ) {
        Box(Modifier.size(64.dp).clip(DsShapes.large).background(DsColors.PrimaryLight), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Flag, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(32.dp))
        }
        Text("Aucun objectif pour le moment", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark, textAlign = TextAlign.Center)
        Text("Fixez un objectif et la façon de calculer la prime : votre progression s'affichera ici.",
            fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onEditPolicy, shape = DsShapes.medium, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Fixer un objectif", fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        }
    }
}

// ── The navy card ──

private fun ringColor(state: GoalState): Color = when (state) {
    GoalState.BELOW -> DsColors.Primary
    GoalState.REACHED -> RingReached
    GoalState.EXCEEDED -> RingExceeded
}

@Composable
private fun NavyCard(goal: GoalProgress, onInfo: () -> Unit) {
    val money = LocalMoneyFormatter.current
    val target = goal.target
    Box(Modifier.fillMaxWidth().clip(NavyShape).background(DsColors.PrimaryDark)) {
        // The two soft circles of the design
        Canvas(Modifier.matchParentSize()) {
            drawCircle(DsColors.Primary.copy(alpha = 0.2f), radius = 88.dp.toPx(), center = Offset(size.width + 8.dp.toPx(), -20.dp.toPx()))
            drawCircle(DsColors.AccentViolet.copy(alpha = 0.2f), radius = 72.dp.toPx(), center = Offset(32.dp.toPx(), size.height + 12.dp.toPx()))
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    StatusChip(goal)
                    Spacer(Modifier.height(DsSpacing.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(GoalWording.achievedIn(goal.period), fontSize = DsTextSize.caption, fontWeight = FontWeight.Medium, color = OnNavySoft)
                        IconButton(onClick = onInfo, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Info, contentDescription = "En savoir plus sur la règle", tint = OnNavyFaint, modifier = Modifier.size(16.dp))
                        }
                    }
                    // One unbreakable figure beside the ring: smaller as it grows, so « DA » never breaks off (2026-10-21)
                    val amount = GoalWording.whole(goal.value, money)
                    val size = when {
                        amount.length <= 10 -> DsTextSize.display
                        amount.length <= 13 -> 21.sp
                        else -> 18.sp
                    }
                    Text(amount, fontSize = size, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, softWrap = false)
                    Text(
                        "sur ${GoalWording.whole(target.threshold, money)}" + (target.tier?.let { " · palier $it" } ?: ""),
                        fontSize = DsTextSize.caption, fontWeight = FontWeight.Medium, color = OnNavyFaint,
                        modifier = Modifier.padding(top = DsSpacing.xs),
                    )
                }
                Spacer(Modifier.width(DsSpacing.md))
                Ring(goal)
            }
            if (goal.tiers.isNotEmpty()) TierMarkers(goal)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f)).padding(DsSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val behind = goal.next != null && goal.pace?.status == PaceStatus.BEHIND
                val tint = if (behind) DsColors.Warning else DsColors.Success
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(if (behind) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(DsSpacing.md))
                Text(GoalWording.encouragement(goal), fontSize = DsTextSize.caption, fontWeight = FontWeight.Medium, color = OnNavySoft, lineHeight = 16.sp)
            }
        }
    }
}

/** « EN AVANCE », « EN BONNE VOIE », « EN RETARD », « ATTEINT »: an icon and words, not a colour alone. */
@Composable
private fun StatusChip(goal: GoalProgress) {
    val status = goal.pace?.status
    val (text, icon, tint) = when {
        goal.next == null || status == PaceStatus.REACHED -> Triple("ATTEINT", Icons.Default.CheckCircle, RingReached)
        status == PaceStatus.AHEAD -> Triple("EN AVANCE", Icons.Default.Bolt, RingReached)
        status == PaceStatus.BEHIND -> Triple("EN RETARD", Icons.AutoMirrored.Filled.TrendingDown, DsColors.Warning)
        else -> Triple("EN BONNE VOIE", Icons.Default.Bolt, RingReached)
    }
    Row(
        Modifier.clip(DsShapes.pill).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = DsSpacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = Color(0xFFDBEAFE))
    }
}

/** The ring to the current aim; it fills as the figures do, and turns green at 100 %, gold beyond. */
@Composable
private fun Ring(goal: GoalProgress) {
    val target = goal.target
    val fraction = if (target.threshold <= 0) 0f else (goal.value / target.threshold).coerceIn(0.0, 1.0).toFloat()
    val sweep by animateFloatAsState(fraction, tween(700), label = "ring")
    val color = ringColor(target.state)
    Box(
        Modifier.size(112.dp).clearAndSetSemantics { contentDescription = "${percentText("${target.percent}")} ${GoalWording.ofAim(goal)}" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (sweep > 0f) drawArc(color, -90f, 360f * sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(percentText("${target.percent}"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(target.tier?.let { "du palier $it" } ?: "atteint", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = OnNavyFaint)
        }
    }
}

/** P1 ✓ ◐ P2 ○ P3: reached tiers filled, the one under way half-filled, the others outlined. */
@Composable
private fun TierMarkers(goal: GoalProgress) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.lg)) {
        Text("Paliers", fontSize = DsTextSize.caption, fontWeight = FontWeight.Medium, color = OnNavyFaint)
        goal.tiers.forEachIndexed { index, tier ->
            val reached = tier.state == TierState.PASSED || tier.state == TierState.CURRENT
            val spoken = when (tier.state) {
                TierState.PASSED, TierState.CURRENT -> "atteint"
                TierState.NEXT -> "en cours"
                TierState.LOCKED -> "à venir"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "Palier ${index + 1} $spoken" },
            ) {
                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 1.5.dp.toPx()
                        when (tier.state) {
                            TierState.PASSED, TierState.CURRENT -> drawCircle(RingReached)
                            TierState.NEXT -> {
                                drawCircle(Color.White, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
                                drawArc(Color.White, 90f, 180f, true)
                            }
                            TierState.LOCKED -> drawCircle(OnNavyFaint, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
                        }
                    }
                    if (reached) Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.PrimaryDark, modifier = Modifier.size(11.dp))
                }
                Spacer(Modifier.width(DsSpacing.xs))
                Text("P${index + 1}", fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold,
                    color = if (tier.state == TierState.LOCKED) OnNavyFaint else Color.White)
            }
        }
    }
}

// ── « Votre trajectoire » (owner's design, 2026-10-22): four figures in one card, amounts in full ──

@Composable
private fun TrajectoryCard(goal: GoalProgress, freeGood: String?) {
    val money = LocalMoneyFormatter.current
    val pace = goal.pace
    val next = goal.next
    val target = goal.target
    val ofAim = GoalWording.ofAim(goal)
    val cells = buildList<@Composable RowScope.() -> Unit> {
        add {
            if (next != null) {
                TrajectoryCell(Icons.Outlined.Flag, DsColors.Primary, "Reste à réaliser", GoalWording.whole(next.gap, money),
                    "${percentText("${100 - target.percent}")} $ofAim")
            } else {
                TrajectoryCell(Icons.Default.EmojiEvents, GoldOnWhite, "Dépassé de :",
                    GoalWording.whole((goal.value - target.threshold).coerceAtLeast(0.0), money))
            }
        }
        if (pace != null) add {
            if (next != null) {
                // Rounded up to the thousand: a pace to keep, not a figure to the centime
                val perDay = ceil(pace.neededPerDay / 1_000) * 1_000
                TrajectoryCell(Icons.Default.Speed, DsColors.Warning, "Rythme conseillé", "${GoalWording.whole(perDay, money)} / Jour",
                    "Pendant ${pace.daysLeft} ${if (pace.daysLeft <= 1) "jour" else "jours"}")
            } else {
                TrajectoryCell(Icons.Default.Speed, DsColors.AccentTeal, "Rythme actuel", "${GoalWording.whole(pace.perDaySoFar, money)} / Jour",
                    "${pace.daysLeft} ${if (pace.daysLeft <= 1) "jour restant" else "jours restants"}")
            }
        }
        if (pace != null) add {
            TrajectoryCell(Icons.Default.AutoGraph, DsColors.Success, "Projection", GoalWording.whole(pace.projection, money),
                "${percentText("${goal.rules.percentOf(pace.projection, target.threshold)}")} $ofAim")
        }
        // Only when the policy shows the bonus (D13, D20)
        if (goal.showReward) add {
            TrajectoryCell(Icons.Default.WorkspacePremium, DsColors.AccentViolet, "Commission estimée",
                GoalWording.rewardValue(goal.reward, money, freeGood), GoalWording.commissionNote(goal))
        }
    }
    Column(Modifier.fillMaxWidth().clip(CardShape).background(DsColors.Surface).border(1.dp, DsColors.Border, CardShape)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
            CardTitle("Votre trajectoire", "Les repères clés pour atteindre votre cap", Modifier.weight(1f))
            Spacer(Modifier.width(DsSpacing.sm))
            TrajectoryChip(goal)
        }
        cells.chunked(2).forEach { pair ->
            HorizontalDivider(color = DsColors.Border)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                pair[0]()
                if (pair.size == 2) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(DsColors.Border))
                    pair[1]()
                }
            }
        }
    }
}

/** The pace as the design's green chip: an icon and words, never a colour alone. */
@Composable
private fun TrajectoryChip(goal: GoalProgress) {
    val status = goal.pace?.status
    val (text, icon, tint) = when {
        goal.next == null || status == PaceStatus.REACHED -> Triple("ATTEINT", Icons.Default.CheckCircle, DsColors.Success)
        status == PaceStatus.AHEAD -> Triple("EN AVANCE", Icons.AutoMirrored.Filled.TrendingUp, DsColors.Success)
        status == PaceStatus.BEHIND -> Triple("EN RETARD", Icons.AutoMirrored.Filled.TrendingDown, DsColors.Warning)
        else -> Triple("EN BONNE VOIE", Icons.AutoMirrored.Filled.TrendingUp, DsColors.Success)
    }
    Row(
        Modifier.clip(DsShapes.pill).background(tint.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
private fun RowScope.TrajectoryCell(icon: ImageVector, tint: Color, label: String, value: String, sub: String? = null) {
    Column(Modifier.weight(1f).padding(DsSpacing.lg)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(DsSpacing.md))
        Text(label, fontSize = DsTextSize.caption, fontWeight = FontWeight.Medium, color = DsColors.TextSecondary)
        Text(value, fontSize = DsTextSize.body, fontWeight = FontWeight.ExtraBold, color = DsColors.PrimaryDark, modifier = Modifier.padding(top = 2.dp))
        sub?.let { Text(it, fontSize = DsTextSize.caption, color = DsColors.TextTertiary, modifier = Modifier.padding(top = 2.dp)) }
    }
}

// ── The period in steps ──

@Composable
private fun WhiteCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(DsColors.Surface).border(1.dp, DsColors.Border, CardShape).padding(20.dp),
        content = content,
    )
}

@Composable
private fun CardTitle(title: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, fontSize = DsTextSize.body, fontWeight = FontWeight.ExtraBold, color = DsColors.PrimaryDark)
        Text(sub, fontSize = DsTextSize.caption, color = DsColors.TextTertiary, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun StepsCard(goal: GoalProgress, steps: List<GoalStep>) {
    val pace = goal.pace
    val title = when (goal.period.type) {
        PeriodType.MONTHLY -> "Rythme hebdomadaire"
        PeriodType.QUARTERLY -> "Rythme mensuel"
        PeriodType.YEARLY -> "Rythme trimestriel"
    }
    WhiteCard {
        Row(verticalAlignment = Alignment.Top) {
            CardTitle(title, "Progression par rapport au cap prévu", Modifier.weight(1f))
            val gap = pace?.let { GoalSteps.paceGap(goal.value, goal.target.threshold, it.daysElapsed, goal.period.days) }
            when {
                gap != null -> {
                    val up = gap >= 0
                    val text = (if (up) "+" else "−") + percentText("${abs(gap) / 10},${abs(gap) % 10}")
                    Badge(text, if (up) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        if (up) DsColors.Success else DsColors.Warning, if (up) DsColors.SuccessLight else DsColors.WarningLight,
                        spoken = if (up) "$text d'avance sur le cap prévu" else "$text de retard sur le cap prévu")
                }
                goal.next == null -> Badge("Atteint", Icons.Default.CheckCircle, DsColors.Success, DsColors.SuccessLight, "Atteint")
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.md), verticalAlignment = Alignment.Bottom) {
            steps.forEach { step -> StepBar(step, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Badge(text: String, icon: ImageVector, tint: Color, background: Color, spoken: String) {
    Row(
        Modifier.clip(DsShapes.pill).background(background).padding(horizontal = 10.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(2.dp))
        Text(text, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
private fun StepBar(step: GoalStep, modifier: Modifier) {
    val money = LocalMoneyFormatter.current
    // As the design: 82 % of the track is the step's cap, never under 18 % so a small week still shows
    val share = if (step.future || step.target <= 0) 0f else (step.value / step.target * 0.82).coerceIn(0.18, 1.0).toFloat()
    val height by animateFloatAsState(share, tween(600), label = "step")
    val tint = if (step.current) DsColors.Primary else DsColors.TextTertiary
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = if (step.future) "${step.label} : à venir"
            else "${step.label} : ${GoalWording.whole(step.value, money)} sur ${GoalWording.whole(step.target, money)} prévus"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // In full, as every amount (owner, 2026-10-22); « DA » left to the card's other figures, for the width
        Text(if (step.future) "—" else GoalWording.whole(step.value, money).removeSuffix("DA").trimEnd(), fontSize = 10.sp,
            fontWeight = FontWeight.Bold, color = tint, maxLines = 1, softWrap = false)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp)).background(DsColors.SurfaceSunken), contentAlignment = Alignment.BottomCenter) {
            if (height > 0f) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(height).clip(RoundedCornerShape(12.dp))
                    .background(if (step.current) DsColors.Primary else BarPast))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(step.label, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = tint)
    }
}

// ── The past periods ──

@Composable
private fun PastCard(goal: GoalProgress, history: List<CommissionPeriodEntity>) {
    val money = LocalMoneyFormatter.current
    WhiteCard {
        Text(if (goal.period.type == PeriodType.MONTHLY) "Mois précédents" else "Périodes précédentes",
            fontSize = DsTextSize.body, fontWeight = FontWeight.ExtraBold, color = DsColors.PrimaryDark)
        Spacer(Modifier.height(DsSpacing.md))
        if (history.isEmpty()) {
            Text("Les périodes terminées s'afficheront ici, avec leur résultat.", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        }
        history.take(3).forEachIndexed { index, period ->
            if (index > 0) HorizontalDivider(color = DsColors.Border, modifier = Modifier.padding(vertical = DsSpacing.sm))
            val name = runCatching {
                GoalWording.pastPeriodName(CommissionPeriod(period.period_type, LocalDate.parse(period.period_start), LocalDate.parse(period.period_end)))
            }.getOrDefault(period.period_start)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (period.reached) Icons.Default.CheckCircle else Icons.Default.Close, contentDescription = null,
                    tint = if (period.reached) DsColors.Success else DsColors.TextTertiary, modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(DsSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                    Text("${percentText("${period.achieved_percent}")} · ${GoalWording.whole(period.achieved_value, money)}",
                        fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                }
                Text(
                    when {
                        !period.reached -> "Non atteint"
                        goal.showReward && period.reward_amount != null -> GoalWording.whole(period.reward_amount, money)
                        goal.showReward && period.free_good_quantity != null -> "${period.free_good_quantity} offerts"
                        else -> "Atteint"
                    },
                    fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold,
                    color = if (period.reached) DsColors.Success else DsColors.TextSecondary,
                )
            }
        }
    }
}

// ── « Ajuster mon objectif » (single-target policies) ──

/** Rounded up to [step]: 1 103 000 → 1 110 000 for a step of 10 000. */
private fun roundUp(value: Double, step: Double) = ceil(value / step) * step

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObjectiveSheet(
    goal: GoalProgress,
    lastAchieved: Double?,
    onSave: (Double, CalculationSource, () -> Unit, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val money = LocalMoneyFormatter.current
    var draft by remember { mutableStateOf(Amount.inputText(goal.objective)) }
    var source by remember { mutableStateOf(goal.rules.source) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val parsed = Amount.parse(draft)
    // Quick amounts from the figures, not fixed ones: the objective, 10 % more, the last period's result
    val chips = listOfNotNull(
        goal.objective,
        roundUp(goal.objective * 1.1, 10_000.0),
        lastAchieved?.takeIf { it > 0 }?.let { roundUp(it, 10_000.0) },
    ).filter { it > 0 }.distinct().sorted()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DsColors.Surface,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Ajuster mon objectif", fontSize = DsTextSize.title, fontWeight = FontWeight.ExtraBold, color = DsColors.PrimaryDark)
                    Text("Définissez un cap motivant et réaliste.", fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary,
                        modifier = Modifier.padding(top = DsSpacing.xs))
                }
                SquareButton(Icons.Default.Close, "Fermer", DsColors.SurfaceSunken, DsColors.TextSecondary, onDismiss)
            }
            Spacer(Modifier.height(20.dp))
            SheetLabel("Objectif")
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(DsColors.PrimaryLight.copy(alpha = 0.5f))
                    .border(2.dp, DsColors.Primary.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(horizontal = DsSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = Amount.sanitizeInput(it); error = null },
                    singleLine = true,
                    visualTransformation = AmountVisualTransformation(money.format),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = DsColors.PrimaryDark, textDirection = TextDirection.ContentOrLtr),
                    cursorBrush = SolidColor(DsColors.Primary),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Objectif en dinars" },
                )
                Text("DA", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextTertiary)
            }
            Spacer(Modifier.height(DsSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                chips.forEach { amount ->
                    val selected = parsed == amount
                    Box(
                        Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) DsColors.Primary else DsColors.SurfaceSunken)
                            .clickable { draft = Amount.inputText(amount); error = null }
                            .semantics { contentDescription = GoalWording.whole(amount, money) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(GoalWording.compact(amount), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else DsColors.TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            SheetLabel("Mesuré sur")
            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                listOf(
                    Triple(CalculationSource.INVOICED_SALES, "Ventes facturées", Icons.AutoMirrored.Filled.ReceiptLong),
                    Triple(CalculationSource.COLLECTED_CASH, "Recouvrement", Icons.Default.Payments),
                ).forEach { (value, label, icon) ->
                    val selected = source == value
                    Row(
                        Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (selected) DsColors.PrimaryLight else DsColors.Surface)
                            .border(1.dp, if (selected) DsColors.Primary else DsColors.Border, RoundedCornerShape(16.dp))
                            .clickable { source = value },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, tint = if (selected) DsColors.Primary else DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = if (selected) DsColors.Primary else DsColors.TextSecondary)
                    }
                }
            }
            error?.let { Text(it, fontSize = DsTextSize.caption, color = DsColors.Danger, modifier = Modifier.padding(top = DsSpacing.md)) }
            Spacer(Modifier.height(DsSpacing.xxl))
            Button(
                onClick = {
                    val target = parsed
                    if (target == null || target <= 0) { error = "Saisissez un objectif supérieur à zéro."; return@Button }
                    saving = true
                    onSave(target, source, { saving = false; onDismiss() }, { saving = false; error = it })
                },
                enabled = !saving,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Enregistrer l'objectif", fontSize = DsTextSize.body, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(DsSpacing.sm))
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text.uppercase(), fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = DsColors.TextTertiary,
        modifier = Modifier.padding(bottom = DsSpacing.sm))
}
