package com.distrigo.app.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

// The app's new design language, first drawn for Compte (docs/activation_codes.md, step C5): Plus Jakarta Sans,
// navy and blue on white cards with a hairline border, over the light Mist page. New screens are built from these
// pieces; the older screens move to them one at a time.

/**
 * The sizes, in one place, set on 2026-10-14 against a reference app the owner chose. Plus Jakarta Sans is wide,
 * so the text sits a step below the usual sizes to read the same.
 */
object BrandSize {
    val ScreenTitle    = 20.sp   // the top bar
    val Name           = 16.sp   // what a page is about (Compte's business)
    val CardTitle      = 14.sp   // a card's header
    val Action         = 14.sp   // buttons, choices
    val Body           = 13.sp   // a row's label and value, an action row, a dialog's text
    val BodyLine       = 18.sp
    val Small          = 12.sp   // captions, notices
    val SmallLine      = 16.sp
    val Tiny           = 11.sp   // pills
    val DialogTitle    = 18.sp
    val Code           = 15.sp   // an activation code in a card
    val AvatarInitials = 18.sp

    val HeaderIcon     = 18.dp
    val RowIcon        = 18.dp
    val ButtonIcon     = 18.dp
    val AvatarIcon     = 24.dp
    val Avatar         = 52.dp
    val RoundAction    = 36.dp
    val Button         = 46.dp
}

/** A card's rows start under its icon, so the dividers start there too. */
private val RowInset = DsSpacing.lg + BrandSize.RowIcon + DsSpacing.md
private val WaveHeight = 64.dp

/**
 * A page: its title pinned at the top (a hairline under it once the page scrolls), the cards on Mist between the
 * page's margins, and the wave at the end, in the page's flow so it never covers a row. [actions] go at the
 * bar's end.
 */
@Composable
fun BrandScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }
    Column(Modifier.fillMaxSize().background(DsBrand.Mist)) {
        BrandTopBar(title, onBack, divided = scrolled, actions = actions)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // At least the space left tall, so the wave sits at the bottom
            Column(Modifier.verticalScroll(scroll).heightIn(min = maxHeight)) {
                Column(
                    modifier            = Modifier.padding(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
                    content             = content
                )
                Spacer(Modifier.weight(1f).heightIn(min = DsSpacing.xxl))
                BrandWave(Modifier)
            }
        }
    }
}

/** Back, the page's title, and its [actions]; [divided] draws the hairline that tells the page has scrolled. */
@Composable
fun BrandTopBar(title: String, onBack: () -> Unit, divided: Boolean = false, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(DsBrand.Mist)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = DsSpacing.xs, end = DsSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsBrand.Ink)
            }
            Text(
                title,
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = BrandSize.ScreenTitle,
                color      = DsBrand.Ink,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f)
            )
            actions()
        }
        HorizontalDivider(color = if (divided) DsBrand.CardBorder else Color.Transparent, thickness = 1.dp)
    }
}

/** The onboarding's blue wave, rising from the bottom-left corner. */
@Composable
fun BrandWave(modifier: Modifier) {
    Canvas(modifier.fillMaxWidth(0.62f).height(WaveHeight)) {
        val w = size.width
        val h = size.height
        val wave = Path().apply {
            moveTo(0f, h * 0.15f)
            cubicTo(w * 0.30f, h * 0.05f, w * 0.55f, h * 0.95f, w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(wave, Brush.linearGradient(listOf(DsBrand.Blue, DsBrand.Ink), start = Offset(0f, 0f), end = Offset(w, h)))
    }
}

/** A white card, rounded, with the onboarding's hairline border. */
@Composable
fun BrandCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(Color.White)
            .border(1.dp, DsBrand.CardBorder, DsShapes.large),
        content  = content
    )
}

/** A card's title band: its icon, its title, and what goes at its end. */
@Composable
fun BrandCardHeader(icon: ImageVector, title: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier          = Modifier.fillMaxWidth().background(DsBrand.Mist).padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = DsBrand.Blue, modifier = Modifier.size(BrandSize.HeaderIcon))
        Spacer(Modifier.width(DsSpacing.md))
        Text(title, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.CardTitle, color = DsBrand.Ink, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** A card's body: its padding, and room between what it holds. */
@Composable
fun BrandCardBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        content             = content
    )
}

/**
 * A detail: its icon, its label, its value at the end, on two lines when long. [onClick] makes it a row to change,
 * with its chevron; [enabled] false holds it while something else runs. A [value] of null reads « Ajouter » on a
 * row to change (short enough beside « Nombre de commerciaux », where « À renseigner » broke in two), else « — »;
 * [badge] shows in its place (a status pill).
 */
@Composable
fun BrandInfoRow(
    icon: ImageVector,
    label: String,
    value: String?,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    badge: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClickLabel = "Modifier $label", onClick = onClick) else Modifier)
            .padding(start = DsSpacing.lg, end = if (onClick != null) DsSpacing.md else DsSpacing.lg, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = DsBrand.Ink, modifier = Modifier.size(BrandSize.RowIcon))
        Spacer(Modifier.width(DsSpacing.md))
        Text(label, fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = BrandSize.Body, color = DsBrand.Slate)
        Spacer(Modifier.width(DsSpacing.md))
        if (badge != null) {
            Spacer(Modifier.weight(1f))
            badge()
        } else {
            Text(
                value ?: if (onClick != null) "Ajouter" else "—",
                fontFamily = DsBrand.Font,
                fontWeight = if (value != null) FontWeight.Bold else FontWeight.SemiBold,
                fontSize   = BrandSize.Body,
                lineHeight = BrandSize.BodyLine,
                color      = if (value != null) DsBrand.Ink else DsBrand.Blue,
                textAlign  = TextAlign.End,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f)
            )
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = DsBrand.Slate, modifier = Modifier.padding(start = DsSpacing.xs))
        }
    }
}

/** An action in a card: its icon, its label, its chevron, or a spinner while it runs. */
@Composable
fun BrandActionRow(icon: ImageVector, label: String, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !busy, onClick = onClick)
            .padding(start = DsSpacing.lg, end = DsSpacing.md, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = DsBrand.Ink, modifier = Modifier.size(BrandSize.RowIcon))
        Spacer(Modifier.width(DsSpacing.md))
        Text(label, fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = BrandSize.Body, color = DsBrand.Ink, modifier = Modifier.weight(1f))
        if (busy) {
            CircularProgressIndicator(color = DsBrand.Blue, strokeWidth = 2.dp, modifier = Modifier.padding(end = DsSpacing.xs).size(18.dp))
        } else {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = DsBrand.Slate)
        }
    }
}

/** Between two rows of a card, from under the icons to the edge. */
@Composable
fun BrandRowDivider() {
    HorizontalDivider(color = DsBrand.CardBorder, thickness = 1.dp, modifier = Modifier.padding(start = RowInset))
}

/** A state as a small pill: « Actif » in green, the others in their colour. */
@Composable
fun BrandPill(label: String, color: Color, background: Color) {
    Text(
        label,
        fontFamily = DsBrand.Font,
        fontWeight = FontWeight.Bold,
        fontSize   = BrandSize.Tiny,
        color      = color,
        modifier   = Modifier.clip(DsShapes.pill).background(background).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

/** What just happened, above the cards: green when done, red when not. */
@Composable
fun BrandMessage(text: String, isError: Boolean) {
    val tint = if (isError) DsColors.Danger else DsColors.Success
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(if (isError) DsColors.DangerLight else DsColors.SuccessLight)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle, contentDescription = null, tint = tint, modifier = Modifier.size(BrandSize.ButtonIcon))
        Spacer(Modifier.width(DsSpacing.sm))
        Text(text, fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = BrandSize.Small, lineHeight = BrandSize.SmallLine, color = tint)
    }
}

/** A note inside a card, tinted like the state it explains. */
@Composable
fun BrandNotice(text: String, color: Color, background: Color) {
    Text(
        text,
        fontFamily = DsBrand.Font,
        fontWeight = FontWeight.SemiBold,
        fontSize   = BrandSize.Small,
        lineHeight = BrandSize.SmallLine,
        color      = color,
        modifier   = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(background)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm + 2.dp)
    )
}

/**
 * A full-width button with its icon: outlined in blue, or [filled] navy when it is the thing to do now; outlined in
 * red when it [danger]ously takes something away (a suspension, a deletion).
 */
@Composable
fun BrandWideButton(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    enabled: Boolean,
    busy: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = if (danger) DsColors.Danger else DsBrand.Blue
    val content: @Composable RowScope.() -> Unit = {
        if (busy) {
            CircularProgressIndicator(color = if (filled) Color.White else accent, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(BrandSize.ButtonIcon))
            Spacer(Modifier.width(DsSpacing.sm))
            Text(label, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Action)
        }
    }
    if (filled) {
        Button(
            onClick  = onClick,
            enabled  = enabled && !busy,
            modifier = Modifier.fillMaxWidth().height(BrandSize.Button),
            shape    = DsShapes.pill,
            colors   = ButtonDefaults.buttonColors(containerColor = DsBrand.Ink, contentColor = Color.White),
            content  = content
        )
    } else {
        OutlinedButton(
            onClick  = onClick,
            enabled  = enabled && !busy,
            modifier = Modifier.fillMaxWidth().height(BrandSize.Button),
            shape    = DsShapes.pill,
            border   = BorderStroke(1.5.dp, if (enabled) accent else DsBrand.CardBorder),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            content  = content
        )
    }
}

@Composable
/** A dialog's text, under its title. */
fun BrandDialogText(text: String) {
    Text(text, fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = BrandSize.Body, lineHeight = BrandSize.BodyLine, color = DsBrand.Slate)
}

/**
 * A dialog in the design language: white, rounded, Plus Jakarta Sans. [busy]: the server is working on the answer, so the
 * buttons wait and the confirm one spins. What [extra] holds scrolls when the keyboard leaves it little room.
 */
@Composable
fun BrandDialog(
    title: String,
    text: String?,
    confirm: Pair<String, () -> Unit>,
    dismiss: Pair<String, () -> Unit>? = null,
    danger: Boolean = false,
    busy: Boolean = false,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) (dismiss ?: confirm).second() },
        title            = { Text(title, fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = BrandSize.DialogTitle, color = DsBrand.Ink) },
        text             = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                text?.let { BrandDialogText(it) }
                extra?.invoke(this)
            }
        },
        confirmButton    = {
            TextButton(onClick = confirm.second, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(color = DsBrand.Blue, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text(confirm.first, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Action, color = if (danger) DsColors.Danger else DsBrand.Blue)
                }
            }
        },
        dismissButton    = dismiss?.let {
            {
                TextButton(onClick = it.second, enabled = !busy) {
                    Text(it.first, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Action, color = DsBrand.Slate)
                }
            }
        },
        shape            = DsShapes.large,
        containerColor   = Color.White
    )
}

/** What a choice is about, above it. */
@Composable
fun BrandFieldLabel(text: String) {
    Text(text, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Small, color = DsBrand.Ink)
}

/** A choice among a few, as pills: the chosen one navy, the others outlined; they wrap onto more lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> BrandChips(options: List<Pair<T, String>>, selected: T, enabled: Boolean = true, onSelect: (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
        verticalArrangement   = Arrangement.spacedBy(DsSpacing.sm)
    ) {
        options.forEach { (value, label) ->
            val chosen = value == selected
            Text(
                label,
                fontFamily = DsBrand.Font,
                fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold,
                fontSize   = BrandSize.Body,
                color      = if (chosen) Color.White else DsBrand.Ink,
                modifier   = Modifier
                    .clip(DsShapes.pill)
                    .background(if (chosen) DsBrand.Ink else Color.White)
                    .border(1.dp, if (chosen) DsBrand.Ink else DsBrand.CardBorder, DsShapes.pill)
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = DsSpacing.sm)
            )
        }
    }
}

/** An activation code in the brand's heaviest weight, spaced so each character reads apart. */
@Composable
fun BrandCodeText(text: String, fontSize: TextUnit = 18.sp) {
    Text(text, fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, letterSpacing = 1.5.sp, color = DsBrand.Ink)
}
