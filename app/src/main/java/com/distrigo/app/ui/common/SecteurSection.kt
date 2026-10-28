package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.Client
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

/** The section of clients without a secteur; always listed last. */
internal const val SANS_SECTEUR = "Sans secteur"

/** [clients] by secteur, sorted by name, « Sans secteur » last. */
internal fun groupBySecteur(clients: List<Client>): Map<String, List<Client>> =
    clients
        .groupBy { secteurOf(it) }
        .toSortedMap(compareBy { if (it == SANS_SECTEUR) "\uFFFF" else it })

internal fun secteurOf(client: Client): String =
    client.secteur_name?.takeIf { it.isNotBlank() } ?: SANS_SECTEUR

/**
 * A secteur's fold: its name and count on the left, the arrow at the right edge, turning when it
 * opens. The name takes the room left by the arrow, so every arrow lines up whatever the name.
 */
@Composable
internal fun SecteurHeader(
    name     : String,
    count    : Int,
    expanded : Boolean,
    onToggle : () -> Unit,
    modifier : Modifier = Modifier,
    trailing : (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsColors.SurfaceSunken)
            .clickable(onClick = onToggle)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (name == SANS_SECTEUR) stringResource(R.string.common_no_secteur) else name).uppercase(),
                fontSize   = DsTextSize.caption,
                fontWeight = FontWeight.Bold,
                color      = DsColors.PrimaryDark,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(DsSpacing.sm))
            Text("$count", fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary)
        }
        if (trailing != null) {
            Spacer(Modifier.width(DsSpacing.sm))
            trailing()
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(if (expanded) R.string.common_cd_collapse else R.string.common_cd_expand),
            tint     = DsColors.PrimaryDark,
            modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f)
        )
    }
}
