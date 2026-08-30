package com.distrigo.app.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What occupies the bar's leading slot.
 *
 * Deliberately a parameter rather than a hardcoded icon: the same screen shape appears both as a
 * tab root (menu) and pushed onto a back stack (back), and pickers/modal-style routes want a close
 * affordance instead. Hardcoding the icon is what forces every pushed screen to hand-roll its own
 * header, which is the state this component replaces.
 */
sealed interface DsTopBarLeading {
    data object None : DsTopBarLeading
    data class Back (val onClick: () -> Unit) : DsTopBarLeading
    data class Menu (val onClick: () -> Unit) : DsTopBarLeading
    data class Close(val onClick: () -> Unit) : DsTopBarLeading
    data class Custom(val content: @Composable () -> Unit) : DsTopBarLeading
}

/**
 * How prominent the bar is.
 *
 * The axis is how prominent the section is, not whether a back arrow is present. [Regular] is for
 * leaf screens — a ticket, a form, a wizard step — where the title only has to name what you opened.
 * [Large] is for the roots of a tab, of the Plus drawer, or of a hub destination: a bigger title in
 * a taller bar, matching the weight those roots already drew by hand. A Large root still takes a
 * [DsTopBarLeading.Back] when it was pushed to get there.
 */
enum class DsTopBarSize { Regular, Large }

/** Content height of the bar, excluding any system inset applied by the caller. */
val DsTopBarHeight     : Dp = 56.dp
val DsTopBarLargeHeight: Dp = 72.dp

/** Trailing controls in a root bar, matching the 40dp FABs and pills already used there. */
val DsTopBarActionSize : Dp = 40.dp

/**
 * The app's single top app bar.
 *
 * Replaces the hand-written header `Row` each screen used to carry. Those disagreed on padding
 * (`lg` vs `sm` vs raw `10.dp`), on title size (`headline` vs `title` vs `bodySmall`) and on weight
 * (`ExtraBold` vs `Bold`), so bar height and title treatment changed from screen to screen. This
 * pins all of it in one place.
 *
 * Back deliberately uses `Icons.AutoMirrored`; the deprecated `Icons.Default.ArrowBack` the screens
 * used to import does not mirror in RTL layouts, and no longer appears anywhere in the app.
 *
 * The bar itself is static. Screens whose header shrinks on scroll compose it inside
 * [DsCollapsingHeaderState]'s block rather than asking the bar to animate itself.
 *
 * @param subtitle secondary line under the title — wizard steps use it for context ("Nouveau bon ·
 *   Fournisseur X"); most screens leave it null.
 * @param subtitleColor defaults to [DsColors.TextSecondary]. The wizard steps pass
 *   [DsColors.Primary] once a client or supplier has been picked, so the subtitle itself says
 *   whether that step is satisfied — grey "Choisir un client", blue once one is chosen.
 * @param containerColor defaults to [DsColors.Surface]; screens painted on `SurfaceMuted` pass their
 *   own so the bar does not float on a different white.
 */
@Composable
fun DsTopAppBar(
    title          : String,
    modifier       : Modifier = Modifier,
    subtitle       : String? = null,
    subtitleColor  : Color = DsColors.TextSecondary,
    leading        : DsTopBarLeading = DsTopBarLeading.None,
    size           : DsTopBarSize = DsTopBarSize.Regular,
    containerColor : Color = DsColors.Surface,
    actions        : @Composable RowScope.() -> Unit = {}
) {
    val hasLeading = leading != DsTopBarLeading.None
    val isLarge    = size == DsTopBarSize.Large

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .height(if (isLarge) DsTopBarLargeHeight else DsTopBarHeight)
            // With a leading icon the IconButton's own 12dp inset carries the title to the standard
            // offset; without one the title has to supply the full margin itself. The same holds at
            // the end: Regular actions are 48dp IconButtons that bring their own inset, while the
            // roots' 40dp pills and FABs bring none, so a Large bar supplies the whole margin.
            .padding(
                start = if (hasLeading) DsSpacing.xs else DsSpacing.lg,
                end   = if (isLarge) DsSpacing.lg else DsSpacing.xs
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (leading) {
            is DsTopBarLeading.Back   -> DsTopBarIcon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", leading.onClick)
            is DsTopBarLeading.Menu   -> DsTopBarIcon(Icons.Default.Menu,  "Menu",   leading.onClick)
            is DsTopBarLeading.Close  -> DsTopBarIcon(Icons.Default.Close, "Fermer", leading.onClick)
            is DsTopBarLeading.Custom -> leading.content()
            DsTopBarLeading.None      -> Unit
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (hasLeading) DsSpacing.xs else 0.dp, end = DsSpacing.sm)
        ) {
            Text(
                title,
                fontSize   = if (isLarge) DsTextSize.headline else DsTextSize.title,
                fontWeight = if (isLarge) FontWeight.ExtraBold else FontWeight.Bold,
                color      = DsColors.TextPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = if (isLarge) DsTextSize.bodySmall else DsTextSize.caption,
                    color    = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        actions()
    }
}

@Composable
private fun DsTopBarIcon(
    icon    : ImageVector,
    label   : String,
    onClick : () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = DsColors.TextPrimary)
    }
}

/**
 * The "Produits · 2/3" pill a wizard step carries at the end of its bar.
 *
 * Five copies of this existed across the form graphs, four padded to a raw 10dp and one to
 * [DsSpacing.sm]; this settles on the token.
 */
@Composable
fun DsStepBadge(
    label       : String,
    currentStep : Int,
    totalSteps  : Int
) {
    Box(
        modifier = Modifier
            .clip(DsShapes.pill)
            .background(DsColors.PrimaryLight)
            .padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs)
    ) {
        Text(
            "$label · $currentStep/$totalSteps",
            fontSize   = DsTextSize.caption,
            fontWeight = FontWeight.Bold,
            color      = DsColors.Primary
        )
    }
}


/**
 * The trailing pair every bottom-tab root carries: notifications, then the account.
 *
 * Account sits outermost because that is where people reach for it. Both are 40dp to match
 * the FABs and pills the roots already put in this slot — a default 48dp [IconButton] brings
 * its own inset and would sit tighter than everything else in the bar. A [DsTopBarSize.Large]
 * bar supplies the full end margin itself, so this adds none.
 */
@Composable
fun RowScope.DsTopBarRootActions(
    onNotificationsClick : () -> Unit,
    onProfileClick       : () -> Unit,
    notificationCount    : Int = 0,
    initials             : String? = null
) {
    DsTopBarNotificationButton(count = notificationCount, onClick = onNotificationsClick)
    Spacer(Modifier.width(DsSpacing.sm))
    DsTopBarAvatar(initials = initials, onClick = onProfileClick)
}

/**
 * Bell with an optional unread count.
 *
 * A count rather than a dot: "10" is actionable in a way a dot is not. It caps at "9+" so the
 * badge cannot widen past the button and unbalance the bar. At zero the badge is absent
 * entirely rather than showing "0".
 */
@Composable
fun DsTopBarNotificationButton(
    count   : Int = 0,
    onClick : () -> Unit
) {
    Box(
        modifier = Modifier
            .size(DsTopBarActionSize)
            .clip(DsShapes.pill)
            .background(DsColors.SurfaceMuted)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Notifications,
            contentDescription = "Notifications",
            tint     = DsColors.TextPrimary,
            modifier = Modifier.size(20.dp)
        )

        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .clip(DsShapes.pill)
                    .background(DsColors.Danger)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (count > 9) "9+" else "$count",
                    fontSize   = DsTextSize.caption,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                    maxLines   = 1
                )
            }
        }
    }
}

/**
 * The account control.
 *
 * Falls back to a person glyph until there is a name to derive initials from, then shows them
 * the way the client and supplier avatars already do — initials in a tinted circle.
 */
@Composable
fun DsTopBarAvatar(
    initials : String? = null,
    onClick  : () -> Unit
) {
    Box(
        modifier = Modifier
            .size(DsTopBarActionSize)
            .clip(DsShapes.pill)
            .background(DsColors.PrimaryLight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (initials.isNullOrBlank()) {
            Icon(
                Icons.Default.Person,
                contentDescription = "Profil",
                tint     = DsColors.Primary,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                initials,
                fontSize   = DsTextSize.bodySmall,
                fontWeight = FontWeight.Bold,
                color      = DsColors.Primary
            )
        }
    }
}
