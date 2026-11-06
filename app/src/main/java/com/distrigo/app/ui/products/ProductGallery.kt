package com.distrigo.app.ui.products

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.distrigo.app.R
import com.distrigo.app.data.local.entity.MAX_IMAGES_PER_PRODUCT
import com.distrigo.app.data.model.ProductImage
import com.distrigo.app.ui.common.EntityImage
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

/**
 * The product's photos: a swipeable main image, a counter, and a row of dots, one per photo.
 *
 * ### Dots, not thumbnails
 *
 * Five photos at most, of the same product: a thumbnail strip repeated, small, what the main image already shows,
 * and took a row of the screen for it (the owner's choice, 2026-11-06). The dots only say how many there are and
 * which one is shown; the pager is what moves between them. The main photo, which the thumbnails marked with a ★,
 * now carries the ★ itself while it is on screen.
 *
 * ### Managing photos is a mode, not a permanent control
 *
 * This sits on a read-only detail screen whose other fields are all edited behind **Modifier**, so
 * photos are too: [manageMode] shows the buttons that act on the photo on screen — make it the main
 * photo, delete it — and the one that adds photos, several at once from the gallery. Nothing
 * destructive is reachable until it does.
 *
 * ### Why a pager rather than a swipe listener
 *
 * [HorizontalPager] settles three things the prototype's touch handler got wrong at once: it only
 * claims the gesture once it is horizontal, so a vertical scroll through the page no longer flicks
 * the photo; it does not synthesise a click after a drag, so swiping no longer opens the viewer as
 * well; and it animates between pages instead of hard-cutting.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductGallery(
    images          : List<ProductImage>,
    manageMode      : Boolean,
    adding          : Boolean,
    onOpenViewer    : (Int) -> Unit,
    onAddPhoto      : () -> Unit,
    onDeletePhoto   : (ProductImage) -> Unit,
    onSetCover      : (ProductImage) -> Unit,
    modifier        : Modifier = Modifier
) {
    if (images.isEmpty()) {
        GalleryEmptyState(manageMode = manageMode, adding = adding, onAddPhoto = onAddPhoto, modifier = modifier)
        return
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount   = { images.size }
    )
    // A deleted last photo leaves the pager past the end for a frame: what is shown is the last one there is
    val currentIndex = pagerState.currentPage.coerceIn(0, images.size - 1)
    val current = images[currentIndex]

    // Photos just added are shown: the pager goes to the first of them, so the user sees what came in
    var knownCount by remember { mutableIntStateOf(images.size) }
    LaunchedEffect(images.size) {
        if (images.size > knownCount) pagerState.animateScrollToPage(knownCount)
        knownCount = images.size
    }

    Column(modifier = modifier) {

        // White, not the muted surface. The photo is drawn Fit, so the frame shows around it —
        // and product shots are overwhelmingly cut out on white, which a grey frame turns into a
        // visible rectangle behind the product.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(DsShapes.large)
                .background(DsColors.Surface)
        ) {
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = DsSpacing.sm
            ) { page ->
                EntityImage(
                    ref                = images[page].ref,
                    contentDescription = stringResource(R.string.products_photo_n_of, page + 1, images.size),
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .fillMaxSize()
                        .clickable { onOpenViewer(page) }
                        .padding(DsSpacing.md)
                ) {
                    // A reference that will not resolve — a file gone after a restore — reads as
                    // "no picture" here exactly as it does everywhere else.
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint     = DsColors.TextTertiary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            if (images.size > 1) {
                // Which photo the product rows and the receipts use: said on the photo, once there is a choice
                if (current.isCover) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(DsSpacing.md)
                            .size(24.dp)
                            .clip(DsShapes.pill)
                            .background(DsColors.Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = stringResource(R.string.products_photo_cover),
                            tint     = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(DsSpacing.md)
                        .clip(DsShapes.pill)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = DsSpacing.sm, vertical = 2.dp)
                ) {
                    Text(
                        "${currentIndex + 1} / ${images.size}",
                        fontSize   = DsTextSize.caption,
                        fontWeight = FontWeight.Medium,
                        color      = Color.White
                    )
                }
            }
        }

        if (images.size > 1) {
            Spacer(Modifier.height(DsSpacing.md))
            GalleryDots(
                count    = images.size,
                current  = currentIndex,
                active   = DsColors.Primary,
                inactive = DsColors.Border,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (manageMode) {
            Spacer(Modifier.height(DsSpacing.md))
            // What acts on the photo on screen: swipe to another to act on it
            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                GalleryAction(
                    icon     = if (current.isCover) Icons.Default.Star else Icons.Outlined.StarOutline,
                    label    = stringResource(R.string.products_photo_cover),
                    tint     = if (current.isCover) Color.White else DsColors.Primary,
                    fill     = if (current.isCover) DsColors.Primary else DsColors.PrimaryLight,
                    // The main photo is chosen by making another one main, never by unsetting it
                    onClick  = if (current.isCover) null else ({ onSetCover(current) }),
                    clickLabel = stringResource(R.string.products_photo_cd_make_cover),
                )
                GalleryAction(
                    icon     = Icons.Outlined.Delete,
                    label    = stringResource(R.string.products_photo_delete_short),
                    tint     = DsColors.Danger,
                    fill     = DsColors.DangerLight,
                    onClick  = { onDeletePhoto(current) },
                    clickLabel = stringResource(R.string.products_photo_cd_delete),
                )
            }
            if (images.size < MAX_IMAGES_PER_PRODUCT) {
                Spacer(Modifier.height(DsSpacing.sm))
                AddPhotosButton(adding = adding, onClick = onAddPhoto)
            }
            Spacer(Modifier.height(DsSpacing.sm))
            Text(
                text = if (images.size >= MAX_IMAGES_PER_PRODUCT)
                    stringResource(R.string.products_photos_max, MAX_IMAGES_PER_PRODUCT)
                else
                    stringResource(R.string.products_photos_count, images.size, MAX_IMAGES_PER_PRODUCT),
                fontSize  = DsTextSize.caption,
                color     = DsColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * One dot per photo, the shown one larger and in [active]. Read-only: the pager moves between photos, and the
 * counter already says « 2 / 4 » to a screen reader, so the dots are hidden from it.
 */
@Composable
internal fun GalleryDots(count: Int, current: Int, active: Color, inactive: Color, modifier: Modifier = Modifier) {
    Row(
        modifier              = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val selected = index == current
            val size by animateDpAsState(if (selected) DotActive else DotInactive, label = DOT_SIZE_ANIMATION)
            val color by animateColorAsState(if (selected) active else inactive, label = DOT_COLOR_ANIMATION)
            Box(Modifier.size(size).clip(DsShapes.pill).background(color))
        }
    }
}

private val DotActive: Dp = 8.dp
private val DotInactive: Dp = 6.dp

// Animation names for the inspector, never shown to anyone
private const val DOT_SIZE_ANIMATION = "gallery dot size"
private const val DOT_COLOR_ANIMATION = "gallery dot color"

/** A manage-mode button for the photo on screen. [onClick] null: shown as the photo's state, not tappable. */
@Composable
private fun RowScope.GalleryAction(
    icon       : ImageVector,
    label      : String,
    tint       : Color,
    fill       : Color,
    onClick    : (() -> Unit)?,
    clickLabel : String,
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 44.dp)
            .clip(DsShapes.pill)
            .background(fill)
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(DsSpacing.xs))
        Text(label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1)
    }
}

/** « Ajouter des photos »: the camera, or several from the gallery at once. A spinner while they are stored. */
@Composable
private fun AddPhotosButton(adding: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(DsShapes.pill)
            .border(1.dp, DsColors.Primary.copy(alpha = 0.35f), DsShapes.pill)
            .then(if (adding) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        if (adding) {
            CircularProgressIndicator(color = DsColors.Primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(DsSpacing.sm))
        Text(
            stringResource(if (adding) R.string.products_photos_adding else R.string.common_photos_add),
            fontSize   = DsTextSize.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color      = DsColors.Primary
        )
    }
}

/**
 * What a product with no photos shows — which is most of them.
 *
 * Not an error and not an empty box: outside manage mode it is the same muted placeholder the rest
 * of the app draws for a missing picture, and inside it, it is the button that adds the first ones.
 */
@Composable
private fun GalleryEmptyState(
    manageMode : Boolean,
    adding     : Boolean,
    onAddPhoto : () -> Unit,
    modifier   : Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(DsShapes.large)
            .background(DsColors.SurfaceMuted)
            .then(if (manageMode && !adding) Modifier.clickable(role = Role.Button, onClick = onAddPhoto) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (adding) {
                CircularProgressIndicator(color = DsColors.Primary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            } else {
                Icon(
                    if (manageMode) Icons.Default.AddAPhoto else Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint     = if (manageMode) DsColors.Primary else DsColors.TextTertiary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(DsSpacing.sm))
            Text(
                when {
                    adding     -> stringResource(R.string.products_photos_adding)
                    manageMode -> stringResource(R.string.common_photos_add)
                    else       -> stringResource(R.string.products_photo_none)
                },
                fontSize   = DsTextSize.bodySmall,
                fontWeight = FontWeight.Medium,
                color      = if (manageMode) DsColors.Primary else DsColors.TextSecondary
            )
            if (!manageMode) {
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.products_photo_none_hint),
                    fontSize = DsTextSize.caption,
                    color    = DsColors.TextTertiary
                )
            }
        }
    }
}

/** A checkmark badge, used by the manage-mode toggle in the app bar. */
@Composable
fun GalleryModeDoneIcon() {
    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.products_photos_done), tint = DsColors.Primary)
}
