package com.distrigo.app.ui.rapports

import com.distrigo.app.core.format.percentText
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.data.repository.PlaceLevel
import com.distrigo.app.data.repository.PlaceNode
import com.distrigo.app.data.repository.PlaceSlice
import com.distrigo.app.data.repository.RING_SLICES
import com.distrigo.app.data.repository.ringSlices
import com.distrigo.app.ui.common.FitText
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.format.LocalMoneyFormatter
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The ring's colours, the largest part first (owner's prototype); « Autres » is grey. */
private val SliceColors = listOf(
    DsColors.Primary, DsColors.AccentTeal, DsColors.Warning, DsColors.AccentViolet, DsColors.AccentRose, DsColors.AccentSky,
)
private val OthersColor = Color(0xFF9AA6C0)

/** How many clients a picked quartier lists before « Voir tout ». */
private const val CLIENTS_SHOWN = 10

private const val TAU = (2 * PI).toFloat()

private val INFO_PLACES = ReportInfo(
    "Répartition des ventes",
    "Chiffre d'affaires de la période, selon le lieu du client : wilaya, commune, secteur, quartier.\n" +
        "Le lieu est celui de la fiche client aujourd'hui.\n" +
        "« Sans … » : les clients sans ce renseignement.\n\n" +
        "Touchez une part pour l'ouvrir, le centre pour remonter.",
)

private fun slicePercent(share: Double) = percentText(String.format(Locale.FRENCH, "%.0f", share * 100))

/**
 * « Répartition des ventes » (owner's prototype, 2026-10-25): the period's sales by wilaya › commune › secteur ›
 * quartier on a ring, the five largest parts and « Autres »; a part opens the level under it, a quartier lists its
 * clients; amounts always in full.
 */
fun LazyListScope.salesPlacesItems(
    state: VentesReportState,
    onOpen: (PlaceNode) -> Unit,
    onUp: () -> Unit,
    onTo: (Int) -> Unit,
    onSeeAll: (PlaceNode) -> Unit,
    onClient: (Int) -> Unit,
) {
    val place = state.place ?: return
    item(key = "places_title") { TitleWithInfo("Répartition des ventes", INFO_PLACES) }
    item(key = "places_card") {
        PlacesCard(place, state.placeTrail, state.pickedQuartier, onOpen, onUp, onTo, onSeeAll, onClient)
    }
}

@Composable
private fun PlacesCard(
    place: PlaceNode,
    trail: List<PlaceNode>,
    picked: PlaceNode?,
    onOpen: (PlaceNode) -> Unit,
    onUp: () -> Unit,
    onTo: (Int) -> Unit,
    onSeeAll: (PlaceNode) -> Unit,
    onClient: (Int) -> Unit,
) {
    val money = LocalMoneyFormatter.current
    val slices = remember(place) { ringSlices(place) }
    val canUp = picked != null || trail.size > 1
    val childLevel = place.level.next ?: PlaceLevel.CLIENT
    val open: (PlaceSlice) -> Unit = { slice -> if (slice.isOthers) onSeeAll(place) else slice.node?.let(onOpen) }

    Column(
        Modifier
            .padding(horizontal = DsSpacing.lg)
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .padding(DsSpacing.lg),
    ) {
        Breadcrumb(trail, picked, canUp, onUp, onTo)

        // What the ring shows
        Column(Modifier.fillMaxWidth().padding(top = DsSpacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    picked != null -> "Clients du quartier"
                    place.level == PlaceLevel.ALL -> "Chiffre d'affaires par wilaya"
                    else -> childLevel.many.replaceFirstChar { it.uppercase() } + " de"
                },
                fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary,
            )
            Text(
                (picked ?: place).name, fontSize = DsTextSize.title, fontWeight = FontWeight.ExtraBold, color = DsColors.PrimaryDark,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }

        PlaceRing(
            place = place, slices = slices, picked = picked, canUp = canUp,
            onSlice = open, onCenter = onUp,
        ) {
            // The centre: the place's (or the picked quartier's) total, in full
            val shown = picked ?: place
            FitText(
                money.da(shown.total), fontSize = DsTextSize.title, fontWeight = FontWeight.ExtraBold,
                color = DsColors.PrimaryDark, textAlign = TextAlign.Center, minScale = 0.45f,
            )
            Text(
                if (picked != null) picked.name else "Total des ventes", fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Text(
                if (picked != null) "${percentOf(picked.total, place.total) ?: "—"} de ${place.name}"
                else plural(place.children.size, childLevel.one, childLevel.many),
                fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = DsColors.Primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }

        HorizontalDivider(color = DsColors.Border, modifier = Modifier.padding(top = DsSpacing.sm))
        if (picked != null) {
            ListHead("Top clients · ${picked.name}", "Retour", onUp)
            picked.children.take(CLIENTS_SHOWN).forEachIndexed { i, client ->
                PlaceRow(
                    color = SliceColors[i % SliceColors.size], mark = "${i + 1}", name = client.name,
                    sub = "${percentOf(client.total, picked.total) ?: "—"} du quartier",
                    amount = money.da(client.total),
                    onClick = client.clientId?.let { id -> { onClient(id) } },
                )
            }
            if (picked.children.size > CLIENTS_SHOWN) {
                SeeAllLink("Voir tous les clients (${picked.children.size})") { onSeeAll(picked) }
            }
        } else {
            ListHead(
                "Répartition par ${childLevel.one}",
                if (place.children.size > RING_SLICES) "Voir tout (${place.children.size})" else null,
            ) { onSeeAll(place) }
            slices.forEachIndexed { i, slice ->
                PlaceRow(
                    color = if (slice.isOthers) OthersColor else SliceColors[i % SliceColors.size],
                    mark = if (slice.isOthers) "…" else slice.name.take(1).uppercase(),
                    name = slice.name,
                    sub = if (slice.isOthers) {
                        val shares = slice.others.map { it.total / place.total }
                        "de ${percent(shares.min())} à ${percent(shares.max())} · ${plural(slice.others.size, childLevel.one, childLevel.many)}"
                    } else percent(slice.share),
                    amount = money.da(slice.total),
                    onClick = { open(slice) },
                )
            }
        }
    }
}

/** ← and the places opened, the current one in bold; a tap on one goes back to it. */
@Composable
private fun Breadcrumb(trail: List<PlaceNode>, picked: PlaceNode?, canUp: Boolean, onUp: () -> Unit, onTo: (Int) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(trail.size, picked) { scroll.animateScrollTo(scroll.maxValue) }
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).clip(CircleShape)
                .then(if (canUp) Modifier.border(1.dp, DsColors.Border, CircleShape).clickable(role = Role.Button, onClickLabel = "Retour", onClick = onUp) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (canUp) Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.PrimaryDark, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(DsSpacing.xs))
        Row(Modifier.weight(1f).horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
            val names = trail.map { it.name } + listOfNotNull(picked?.name)
            names.forEachIndexed { i, name ->
                val current = i == names.lastIndex
                if (i > 0) Text("›", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
                Text(
                    name, fontSize = DsTextSize.caption,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                    color = if (current) DsColors.PrimaryDark else DsColors.TextSecondary,
                    modifier = Modifier.clip(DsShapes.small)
                        .then(if (current) Modifier else Modifier.clickable(role = Role.Button) { if (i < trail.size) onTo(i) })
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun ListHead(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = DsSpacing.md, bottom = DsSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (action != null) Text(
            action, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.Primary,
            modifier = Modifier.clip(DsShapes.pill).clickable(role = Role.Button, onClick = onAction)
                .padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs),
        )
    }
}

@Composable
private fun SeeAllLink(text: String, onClick: () -> Unit) {
    Text(
        text, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.Primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(DsShapes.medium).clickable(role = Role.Button, onClick = onClick).padding(DsSpacing.md),
    )
}

/** A part of the ring, or a client: its tinted mark, its name and share, its amount in full; a chevron when it opens. */
@Composable
private fun PlaceRow(color: Color, mark: String, name: String, sub: String, amount: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clip(DsShapes.medium)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = DsSpacing.xs, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(mark, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Text(amount, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark, maxLines = 1)
        if (onClick != null) Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DsColors.TextTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── The ring ──

/** One part as drawn: from [start] to [end] (radians, clockwise from the top), its colour and opacity. */
private data class Arc(val start: Float, val end: Float, val color: Color, val alpha: Float = 1f)

/** A part going from [from] to [to] while its opacity fades between [fadeFrom] and [fadeTo] over [fadeStart]..[fadeEnd] of the move. */
private data class Move(
    val from: Pair<Float, Float>, val to: Pair<Float, Float>, val color: Color,
    val fadeFrom: Float, val fadeTo: Float, val fadeStart: Float, val fadeEnd: Float,
) {
    fun at(t: Float, e: Float): Arc {
        val f = ((t - fadeStart) / (fadeEnd - fadeStart)).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        return Arc(
            from.first + (to.first - from.first) * e, from.second + (to.second - from.second) * e, color,
            fadeFrom + (fadeTo - fadeFrom) * f,
        )
    }
}

/** The ring's parts at rest: each slice from its start to its end. */
private fun restArcs(slices: List<PlaceSlice>): List<Pair<Float, Float>> {
    var acc = 0f
    return slices.map { s -> val a = acc; acc += s.share.toFloat() * TAU; a to acc }
}

private fun sliceColor(slices: List<PlaceSlice>, i: Int) = if (slices[i].isOthers) OthersColor else SliceColors[i % SliceColors.size]

/** Which of [slices] holds [node]: itself or a place above it, or « Autres » that gathers one. */
private fun holder(slices: List<PlaceSlice>, node: PlaceNode): Int {
    fun holds(key: String) = node.key == key || node.key.startsWith("$key|")
    return slices.indexOfFirst { s -> s.node?.let { holds(it.key) } ?: s.others.any { holds(it.key) } }
}

/** Is [place] somewhere under [above]? Keys go down by « | », and the wilayas hold everything. */
private fun isUnder(place: PlaceNode, above: PlaceNode) = above.level == PlaceLevel.ALL && place.level != PlaceLevel.ALL ||
    place.key.startsWith(above.key + "|")

/**
 * The ring: a part touched widens and splits into the level under it (owner's prototype); going up, the parts close
 * back into the one they came from. The picked quartier stands out, the others fade. [hole] sits in the middle.
 */
@Composable
private fun PlaceRing(
    place: PlaceNode,
    slices: List<PlaceSlice>,
    picked: PlaceNode?,
    canUp: Boolean,
    onSlice: (PlaceSlice) -> Unit,
    onCenter: () -> Unit,
    hole: @Composable () -> Unit,
) {
    val progress = remember { Animatable(1f) }
    var shownPlace by remember { mutableStateOf(place) }
    var shownSlices by remember { mutableStateOf(slices) }
    var moves by remember { mutableStateOf<List<Move>>(emptyList()) }
    val measurer = rememberTextMeasurer()
    // A share on the ring, « 45 % »: left to right on a right-to-left screen too (Type.kt)
    val labelStyle = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, textDirection = TextDirection.ContentOrLtr)
    val pickedIndex = picked?.let { holder(slices, it) } ?: -1

    // A new place, or new figures for it: the parts move from where they were
    LaunchedEffect(place.key, slices) {
        val old = shownSlices
        val oldArcs = restArcs(old)
        val newArcs = restArcs(slices)
        val from = shownPlace
        val next = mutableListOf<Move>()
        val kind = when {
            from.key == place.key -> "same"
            isUnder(place, from) -> "down"
            isUnder(from, place) -> "up"
            else -> "in"
        }
        when (kind) {
            "down" -> {
                val p = holder(old, place).takeIf { it >= 0 }
                val span = p?.let { oldArcs[it] } ?: (0f to TAU)
                old.indices.forEach { i ->
                    val same = i == p
                    val edge = if (oldArcs[i].second <= span.first + 1e-4f) 0f to 0f else TAU to TAU
                    next += Move(oldArcs[i], if (same) 0f to TAU else edge, sliceColor(old, i), 1f, 0f, if (same) 0.3f else 0f, if (same) 0.85f else 0.6f)
                }
                val width = span.second - span.first
                slices.indices.forEach { i ->
                    val (a, b) = newArcs[i]
                    next += Move(span.first + a / TAU * width to span.first + b / TAU * width, a to b, sliceColor(slices, i), 0f, 1f, 0.05f, 0.6f)
                }
            }
            "up" -> {
                val p = holder(slices, from).takeIf { it >= 0 }
                val span = p?.let { newArcs[it] } ?: (0f to TAU)
                slices.indices.forEach { i ->
                    val edge = if (newArcs[i].second <= span.first + 1e-4f) 0f to 0f else TAU to TAU
                    next += Move(if (i == p) 0f to TAU else edge, newArcs[i], sliceColor(slices, i), 0f, 1f, 0.15f, 0.8f)
                }
                val width = span.second - span.first
                old.indices.forEach { i ->
                    val (a, b) = oldArcs[i]
                    next += Move(a to b, span.first + a / TAU * width to span.first + b / TAU * width, sliceColor(old, i), 1f, 0f, 0.3f, 0.85f)
                }
            }
            "same" -> {
                slices.indices.forEach { i ->
                    val start = oldArcs.getOrNull(i) ?: (TAU to TAU)
                    next += Move(start, newArcs[i], sliceColor(slices, i), if (i < old.size) 1f else 0f, 1f, 0f, 0.5f)
                }
            }
            else -> slices.indices.forEach { i ->
                next += Move(0f to 0f, newArcs[i], sliceColor(slices, i), 0f, 1f, 0f, 0.4f)
            }
        }
        moves = next
        shownPlace = place
        shownSlices = slices
        progress.snapTo(0f)
        progress.animateTo(
            1f,
            tween(
                durationMillis = when (kind) { "down" -> 950; "up" -> 780; "same" -> 450; else -> 600 },
                easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f),
            ),
        )
        moves = emptyList()
    }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = DsSpacing.sm), contentAlignment = Alignment.Center) {
        val side = min(maxWidth.value * 0.82f, 260f).dp
        val density = LocalDensity.current
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.fillMaxSize()
                    .semantics { contentDescription = "Répartition des ventes : ${place.name}" }
                    .pointerInput(slices, canUp) {
                        detectTapGestures { tap ->
                            if (progress.isRunning) return@detectTapGestures
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val outer = min(size.width, size.height) / 2f - with(density) { 10.dp.toPx() }
                            val inner = outer * 0.66f
                            val r = hypot(tap.x - c.x, tap.y - c.y)
                            if (r < inner) { if (canUp) onCenter(); return@detectTapGestures }
                            if (r > outer + with(density) { 10.dp.toPx() }) return@detectTapGestures
                            var a = atan2(tap.x - c.x, -(tap.y - c.y))
                            if (a < 0) a += TAU
                            val i = restArcs(slices).indexOfFirst { (s, e) -> a >= s && a < e }
                            if (i >= 0) onSlice(slices[i])
                        }
                    },
            ) {
                val outer = size.minDimension / 2f - 10.dp.toPx()
                val thickness = outer * 0.34f
                val radius = outer - thickness / 2f
                val gap = 0.022f
                val box = Size(radius * 2, radius * 2)
                val topLeft = Offset(center.x - radius, center.y - radius)
                fun arc(a: Arc, shift: Offset = Offset.Zero) {
                    val sweep = a.end - a.start - gap
                    if (sweep <= 0f || a.alpha <= 0.01f) return
                    translate(shift.x, shift.y) {
                        drawArc(
                            a.color.copy(alpha = a.alpha), startAngle = Math.toDegrees((a.start + gap / 2 - PI / 2)).toFloat(),
                            sweepAngle = Math.toDegrees(sweep.toDouble()).toFloat(), useCenter = false,
                            topLeft = topLeft, size = box, style = Stroke(width = thickness),
                        )
                    }
                }
                val running = moves.isNotEmpty()
                if (running) {
                    val t = progress.value
                    val e = FastOutSlowInEasing.transform(t)
                    moves.forEach { arc(it.at(t, e)) }
                } else {
                    val rest = restArcs(slices)
                    slices.indices.forEach { i ->
                        val (s, e) = rest[i]
                        val isPicked = i == pickedIndex
                        val mid = (s + e) / 2
                        val shift = if (isPicked) Offset(8.dp.toPx() * sin(mid), -8.dp.toPx() * cos(mid)) else Offset.Zero
                        arc(Arc(s, e, sliceColor(slices, i), if (pickedIndex >= 0 && !isPicked) 0.3f else 1f), shift)
                        // Its share on the part, when it has room
                        if (e - s > 0.5f) {
                            val text = measurer.measure(slicePercent(slices[i].share), labelStyle)
                            val at = Offset(center.x + radius * sin(mid) + shift.x, center.y - radius * cos(mid) + shift.y)
                            drawText(
                                text, topLeft = Offset(at.x - text.size.width / 2f, at.y - text.size.height / 2f),
                                alpha = if (pickedIndex >= 0 && !isPicked) 0.4f else 1f,
                            )
                        }
                    }
                }
            }
            // The hole: the figures, and a tap there goes up
            Column(
                Modifier.size(side * 0.5f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { hole() }
        }
    }
}

// ── « Voir tout » ──

/** Every child of [place], ranked, with its bar and share; a tap opens it (a client, its page). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesSheet(place: PlaceNode?, onDismiss: () -> Unit, onOpen: (PlaceNode) -> Unit, onClient: (Int) -> Unit) {
    if (place == null) return
    val money = LocalMoneyFormatter.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val level = place.level.next ?: PlaceLevel.CLIENT
    val top = max(place.children.maxOfOrNull { it.total } ?: 0.0, 1.0)
    val close: (() -> Unit) -> Unit = { then -> scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); then() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = DsColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg)) {
            Text(
                if (level == PlaceLevel.CLIENT) "Tous les clients" else "Toutes les ${level.many}",
                fontSize = DsTextSize.title, fontWeight = FontWeight.ExtraBold, color = DsColors.PrimaryDark,
            )
            Text(
                "${place.name} · ${plural(place.children.size, level.one, level.many)} · ${money.da(place.total)}",
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
            )
            Spacer(Modifier.height(DsSpacing.sm))
        }
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = DsSpacing.md)) {
            itemsIndexed(place.children, key = { _, n -> n.key }) { i, node ->
                val color = if (i < RING_SLICES) SliceColors[i % SliceColors.size] else OthersColor
                Column(
                    Modifier.fillMaxWidth().clip(DsShapes.medium)
                        .clickable(role = Role.Button) {
                            close { if (node.clientId != null) onClient(node.clientId) else onOpen(node) }
                        }
                        .padding(horizontal = DsSpacing.sm, vertical = DsSpacing.md),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(String.format(Locale.FRENCH, "%02d", i + 1), fontSize = DsTextSize.caption, color = DsColors.TextTertiary, modifier = Modifier.width(26.dp))
                        Text(
                            node.name, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(DsSpacing.sm))
                        Text(money.da(node.total), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark, maxLines = 1)
                    }
                    Row(Modifier.padding(start = 26.dp, top = DsSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(4.dp).clip(DsShapes.pill).background(DsColors.SurfaceSunken)) {
                            Box(Modifier.fillMaxWidth((node.total / top).toFloat().coerceIn(0f, 1f)).height(4.dp).clip(DsShapes.pill).background(color))
                        }
                        Spacer(Modifier.width(DsSpacing.sm))
                        Text(percentOf(node.total, place.total) ?: "—", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                }
                if (i < place.children.lastIndex) HorizontalDivider(color = DsColors.Border)
            }
        }
    }
}
