package com.distrigo.app.ui.secteurs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

/** The tile beside a card's name, and the icons on it. */
internal val CardTile = 44.dp
internal val CardTileIcon = 22.dp
internal val MetaIcon = 15.dp
private val ChevronSize = 20.dp
private val EmptyTile = 52.dp
internal val CardMinHeight = 68.dp

/** « 1 quartier », « 12 quartiers ». */
internal fun count(n: Int, singular: String, plural: String) = "$n ${if (n == 1) singular else plural}"

/** What a short tap on the blue name says: the edit is a long press. */
internal const val EDIT_HINT = "Appui long sur le nom en bleu pour le modifier."

/** A white card row: [leading], the [content] column, and a chevron. */
@Composable
internal fun CardRow(
    label: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    /** A row just added: 1 lights it in blue, fading to 0. */
    highlight: Float = 0f,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = CardMinHeight).clip(DsShapes.large)
            .background(lerp(DsColors.Surface, DsColors.PrimaryLight, highlight))
            .border(1.dp, lerp(DsColors.Border, DsColors.Primary, highlight), DsShapes.large)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md),
    ) {
        leading()
        Column(Modifier.weight(1f)) { content() }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(ChevronSize))
    }
}

@Composable
internal fun CardTitle(text: String) =
    Text(text, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark, maxLines = 2, overflow = TextOverflow.Ellipsis)

@Composable
internal fun CardLine(text: String) =
    Text(text, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
internal fun Meta(icon: ImageVector, tint: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(MetaIcon))
        Text(label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextSecondary)
    }
}

@Composable
internal fun Tile(icon: ImageVector, tint: Color, size: Dp = CardTile, iconSize: Dp = CardTileIcon) {
    Box(Modifier.size(size).clip(DsShapes.medium).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** « + Ajouter un quartier »: the light blue pill a section's action is. */
@Composable
internal fun TonalAction(label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(DsShapes.pill).background(DsColors.PrimaryLight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs),
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.PrimaryDark, modifier = Modifier.size(MetaIcon))
        Text(label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark)
    }
}

/** « Quartiers (4) » and the section's action. */
@Composable
internal fun SectionHeader(title: String, count: Int, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark)
        Box(
            Modifier.padding(start = DsSpacing.sm).clip(DsShapes.pill).background(DsColors.SurfaceSunken)
                .padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs),
        ) {
            Text("$count", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark)
        }
        Box(Modifier.weight(1f))
        action()
    }
}

/** No entry yet, no match, or nothing could be read: a tile, a title, a line, and maybe an action. */
@Composable
internal fun EmptyState(icon: ImageVector, tint: Color, title: String, text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
    ) {
        Tile(icon, tint, EmptyTile, CardTileIcon)
        Text(title, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark, textAlign = TextAlign.Center)
        Text(text, fontSize = DsTextSize.body, color = DsColors.TextSecondary, textAlign = TextAlign.Center)
        if (action != null && onAction != null) TonalAction(action, onAction)
    }
}

/**
 * The record's name under the bar's title, in blue, then « · [rest] » muted (owner's prototype v5): a long press
 * opens its form, a short tap says so ([onTap], the hint). The long press has its own accessibility action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EditableSubtitle(name: String, rest: String?, editLabel: String, onEdit: () -> Unit, onTap: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.clip(DsShapes.small)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Afficher l'astuce",
                onLongClickLabel = editLabel,
                onClick = onTap,
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onEdit() },
            )
            .padding(vertical = DsSpacing.xs / 2),
    ) {
        Text(
            name, fontSize = DsTextSize.bodyLarge, color = DsColors.Primary, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        if (!rest.isNullOrBlank()) {
            Text(" · $rest", fontSize = DsTextSize.bodyLarge, color = DsColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A label (« * » when required), the control, then its error or its help. */
@Composable
internal fun Field(
    label: String,
    required: Boolean = false,
    error: String? = null,
    help: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        Text(
            if (required) "$label *" else label,
            fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark,
        )
        content()
        when {
            error != null -> Text(error, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
            help != null -> Text(help, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        }
    }
}

@Composable
internal fun TextInput(value: String, onValueChange: (String) -> Unit, placeholder: String, isError: Boolean = false, lines: Int = 1) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        placeholder     = { Text(placeholder, fontSize = DsTextSize.body) },
        singleLine      = lines == 1,
        minLines        = lines,
        isError         = isError,
        shape           = DsShapes.large,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction      = if (lines == 1) ImeAction.Next else ImeAction.Default,
        ),
        colors          = dsTextFieldColors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary, errorBorderColor = DsColors.Danger),
        modifier        = Modifier.fillMaxWidth(),
    )
}
