package com.distrigo.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing

// Compte in the onboarding's identity (docs/activation_codes.md, step C5): Plus Jakarta Sans, navy and blue on
// white cards with a hairline border, over the light Mist page.

/**
 * Compte's sizes in one place, set on 2026-10-14 against a reference app the owner chose. Plus Jakarta Sans is
 * wide, so the text sits a step below the usual sizes to read the same.
 */
internal object CompteSize {
    val ScreenTitle    = 20.sp   // « Compte »
    val Name           = 16.sp   // the business, in the identity card
    val CardTitle      = 14.sp   // a card's header
    val Action         = 14.sp   // buttons, choices
    val Body           = 13.sp   // a row's label and value, an action row, a dialog's text
    val BodyLine       = 18.sp
    val Small          = 12.sp   // captions, notices, the check-in line
    val SmallLine      = 16.sp
    val Tiny           = 11.sp   // the status pill
    val DialogTitle    = 18.sp
    val Code           = 15.sp   // the code in its card
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
private val RowIconSize = CompteSize.RowIcon
private val RowInset = DsSpacing.lg + RowIconSize + DsSpacing.md
private val ButtonHeight = CompteSize.Button

/** A white card, rounded, with the onboarding's hairline border. */
@Composable
internal fun BrandCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(Color.White)
            .border(1.dp, DsBrand.CardBorder, DsShapes.large),
        content  = content
    )
}

/** A card's title band: its icon, its title, and what goes at its end (a status pill). */
@Composable
internal fun CardHeader(icon: ImageVector, title: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier          = Modifier.fillMaxWidth().background(DsBrand.Mist).padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = DsBrand.Blue, modifier = Modifier.size(CompteSize.HeaderIcon))
        Spacer(Modifier.width(DsSpacing.md))
        Text(title, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = CompteSize.CardTitle, color = DsBrand.Ink, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/**
 * A detail: its icon, its label, its value at the end, on two lines when long. [onClick] makes it a row to change,
 * with its chevron; [enabled] false holds it while something else runs. A [value] of null reads « À renseigner »
 * (to change) or « — »; [badge] shows in its place (the subscription's status).
 */
@Composable
internal fun InfoRow(
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
        Icon(icon, contentDescription = null, tint = DsBrand.Ink, modifier = Modifier.size(RowIconSize))
        Spacer(Modifier.width(DsSpacing.md))
        Text(label, fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = CompteSize.Body, color = DsBrand.Slate)
        Spacer(Modifier.width(DsSpacing.md))
        if (badge != null) {
            Spacer(Modifier.weight(1f))
            badge()
        } else {
            Text(
                value ?: if (onClick != null) "À renseigner" else "—",
                fontFamily = DsBrand.Font,
                fontWeight = if (value != null) FontWeight.Bold else FontWeight.SemiBold,
                fontSize   = CompteSize.Body,
                lineHeight = CompteSize.BodyLine,
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

/** « Gestion du compte »: an action, its icon, its label, its chevron, or a spinner while it runs. */
@Composable
internal fun ActionRow(icon: ImageVector, label: String, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !busy, onClick = onClick)
            .padding(start = DsSpacing.lg, end = DsSpacing.md, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = DsBrand.Ink, modifier = Modifier.size(RowIconSize))
        Spacer(Modifier.width(DsSpacing.md))
        Text(label, fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = CompteSize.Body, color = DsBrand.Ink, modifier = Modifier.weight(1f))
        if (busy) {
            CircularProgressIndicator(color = DsBrand.Blue, strokeWidth = 2.dp, modifier = Modifier.padding(end = DsSpacing.xs).size(18.dp))
        } else {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = DsBrand.Slate)
        }
    }
}

/** Between two rows of a card, from under the icons to the edge. */
@Composable
internal fun RowDivider() {
    HorizontalDivider(color = DsBrand.CardBorder, thickness = 1.dp, modifier = Modifier.padding(start = RowInset))
}

/** The subscription's state, as a small pill: « Actif » in green, the others in their colour. */
@Composable
internal fun StatusPill(label: String, color: Color, background: Color) {
    Text(
        label,
        fontFamily = DsBrand.Font,
        fontWeight = FontWeight.Bold,
        fontSize   = CompteSize.Tiny,
        color      = color,
        modifier   = Modifier.clip(DsShapes.pill).background(background).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

/** What just happened, above the cards: green when done, red when not. */
@Composable
internal fun MessageLine(message: AccountMessage) {
    val tint = if (message.isError) DsColors.Danger else DsColors.Success
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(if (message.isError) DsColors.DangerLight else DsColors.SuccessLight)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (message.isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle, contentDescription = null, tint = tint, modifier = Modifier.size(CompteSize.ButtonIcon))
        Spacer(Modifier.width(DsSpacing.sm))
        Text(message.text, fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = CompteSize.Small, lineHeight = CompteSize.SmallLine, color = tint)
    }
}

/** A note inside a card, tinted like the state it explains. */
@Composable
internal fun CardNotice(text: String, color: Color, background: Color) {
    Text(
        text,
        fontFamily = DsBrand.Font,
        fontWeight = FontWeight.SemiBold,
        fontSize   = CompteSize.Small,
        lineHeight = CompteSize.SmallLine,
        color      = color,
        modifier   = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(background)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm + 2.dp)
    )
}

/**
 * A button with its icon: outlined in blue, or [filled] navy when it is the thing to do now (« Saisir un code »
 * once the subscription is ending).
 */
@Composable
internal fun IconButtonWide(label: String, icon: ImageVector, filled: Boolean, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        if (busy) {
            CircularProgressIndicator(color = if (filled) Color.White else DsBrand.Blue, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(CompteSize.ButtonIcon))
            Spacer(Modifier.width(DsSpacing.sm))
            Text(label, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = CompteSize.Action)
        }
    }
    if (filled) {
        Button(
            onClick  = onClick,
            enabled  = enabled && !busy,
            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            shape    = DsShapes.pill,
            colors   = ButtonDefaults.buttonColors(containerColor = DsBrand.Ink, contentColor = Color.White),
            content  = content
        )
    } else {
        OutlinedButton(
            onClick  = onClick,
            enabled  = enabled && !busy,
            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            shape    = DsShapes.pill,
            border   = androidx.compose.foundation.BorderStroke(1.5.dp, if (enabled) DsBrand.Blue else DsBrand.CardBorder),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = DsBrand.Blue),
            content  = content
        )
    }
}

/** A card's body: its padding, and room between what it holds. */
@Composable
internal fun CardBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        content             = content
    )
}
