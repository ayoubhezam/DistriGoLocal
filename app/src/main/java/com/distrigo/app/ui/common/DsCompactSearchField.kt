package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

// The app's one search bar.
//
// Two entry points share a single surface, so they cannot drift apart:
//  - DsCompactSearchField   — a search box you type into.
//  - DsCompactSearchTrigger — something that looks like that search box and opens a picker when
//    tapped (the Rapports client/product bars), or stands in for one that is not available yet
//    (the "choose a supplier first" preview on a form's first step).
//
// Placeholders follow one rule: "Rechercher un/une <what the list holds>", short enough to sit on
// one line beside the scanner on a narrow phone. A second term is kept only where it names a
// different key the user would not guess ("… ou un bon").

/** Height of every compact search bar in the app. */
val DsCompactSearchHeight: Dp = 44.dp

/**
 * A compact, filled search field: a sunken pill with the search glyph, the text, and room for a
 * trailing action or two.
 *
 * Built on [BasicTextField] rather than `OutlinedTextField`, whose 56dp minimum and label
 * machinery make a search bar as tall as a form field. A search bar only has to say what it
 * searches, so this one is 44dp, borderless until focused, and takes a one-line placeholder.
 *
 * The clear button appears once there is text. Anything else — a barcode scanner, say — goes in
 * [trailing], built with [DsCompactSearchAction] so the targets match.
 */
@Composable
fun DsCompactSearchField(
    value         : String,
    onValueChange : (String) -> Unit,
    placeholder   : String,
    modifier      : Modifier = Modifier,
    trailing      : @Composable RowScope.() -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val interaction  = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    BasicTextField(
        value             = value,
        onValueChange     = onValueChange,
        singleLine        = true,
        textStyle         = TextStyle(fontSize = DsTextSize.body, color = DsColors.TextPrimary),
        cursorBrush       = SolidColor(DsColors.Primary),
        keyboardOptions   = KeyboardOptions(imeAction = ImeAction.Search),
        // The list narrows as the user types, so "search" has nothing left to run — it only puts
        // the keyboard away so the results can be seen.
        keyboardActions   = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        interactionSource = interaction,
        modifier          = modifier.fillMaxWidth().height(DsCompactSearchHeight),
        decorationBox     = { innerTextField ->
            DsCompactSearchSurface(modifier = Modifier.fillMaxSize(), focused = isFocused) {
                DsCompactSearchGlyph(tint = DsColors.TextSecondary)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) DsCompactSearchText(placeholder, DsColors.TextTertiary)
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    DsCompactSearchAction(
                        icon               = Icons.Default.Close,
                        contentDescription = "Effacer",
                        tint               = DsColors.TextSecondary,
                        iconSize           = 18.dp,
                        onClick            = { onValueChange("") }
                    )
                }
                trailing()
            }
        }
    )
}

/**
 * The same bar as [DsCompactSearchField], but tapped rather than typed into.
 *
 * @param text what has been picked, shown in the primary colour; null shows [placeholder] instead.
 * @param onClear shown as a clear button while [text] is set; omit it for a bar that cannot be
 *   cleared from here.
 * @param enabled false for a bar that is not available yet: it keeps its shape, dims, and does
 *   not respond — the placeholder should then say what unlocks it.
 */
@Composable
fun DsCompactSearchTrigger(
    text        : String?,
    placeholder : String,
    onClick     : () -> Unit,
    modifier    : Modifier = Modifier,
    enabled     : Boolean = true,
    onClear     : (() -> Unit)? = null
) {
    DsCompactSearchSurface(
        modifier = modifier.fillMaxWidth().height(DsCompactSearchHeight),
        focused  = false,
        onClick  = if (enabled) onClick else null
    ) {
        DsCompactSearchGlyph(tint = if (enabled) DsColors.TextSecondary else DsColors.TextTertiary)
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            DsCompactSearchText(
                text  = text ?: placeholder,
                color = if (text != null && enabled) DsColors.TextPrimary else DsColors.TextTertiary
            )
        }
        if (text != null && onClear != null && enabled) {
            DsCompactSearchAction(
                icon               = Icons.Default.Close,
                contentDescription = "Effacer",
                tint               = DsColors.TextSecondary,
                iconSize           = 18.dp,
                onClick            = onClear
            )
        }
    }
}

/**
 * A trailing action for a compact search bar. 40dp, the most the 44dp bar can hold without the
 * target spilling over its edge.
 */
@Composable
fun DsCompactSearchAction(
    icon               : ImageVector,
    contentDescription : String,
    tint               : Color,
    iconSize           : Dp = 20.dp,
    onClick            : () -> Unit
) {
    Box(
        modifier         = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

// ── The shared surface ───────────────────────────────────────────────────────

@Composable
private fun DsCompactSearchSurface(
    modifier : Modifier,
    focused  : Boolean,
    onClick  : (() -> Unit)? = null,
    content  : @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clip(DsShapes.pill)
            .background(DsColors.SurfaceSunken)
            // A focus ring rather than a permanent border: at rest it is a quiet surface, and the
            // ring is what tells the user where their typing is going.
            .border(1.dp, if (focused) DsColors.Primary else Color.Transparent, DsShapes.pill)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(start = DsSpacing.md, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content           = content
    )
}

@Composable
private fun DsCompactSearchGlyph(tint: Color) {
    Icon(Icons.Default.Search, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(DsSpacing.sm))
}

@Composable
private fun DsCompactSearchText(text: String, color: Color) {
    Text(
        text,
        fontSize = DsTextSize.body,
        color    = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
