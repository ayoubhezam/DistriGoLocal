package com.distrigo.app.ui.products

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.local.entity.MAX_IMAGES_PER_PRODUCT
import com.distrigo.app.data.model.ProductImage
import com.distrigo.app.ui.common.EntityImage
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

/**
 * The product's photos: a swipeable main image, a counter, and a thumbnail strip.
 *
 * ### Managing photos is a mode, not a permanent control
 *
 * This sits on a read-only detail screen whose other fields are all edited behind **Modifier**, so
 * photos are too: [manageMode] turns on the add tile, the delete badges and the cover picker, and
 * nothing destructive is reachable until it does. The prototype this came from showed an 18 dp
 * delete badge on every thumbnail at all times — a destructive control, under the minimum touch
 * target, on the corner of a control whose job is *select*.
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
    onOpenViewer    : (Int) -> Unit,
    onAddPhoto      : () -> Unit,
    onDeletePhoto   : (ProductImage) -> Unit,
    onSetCover      : (ProductImage) -> Unit,
    modifier        : Modifier = Modifier
) {
    if (images.isEmpty()) {
        GalleryEmptyState(manageMode = manageMode, onAddPhoto = onAddPhoto, modifier = modifier)
        return
    }

    // Keyed on the photo count so removing one cannot leave the pager on a page that no longer
    // exists — the state is rebuilt, and coerceAtMost keeps the user near where they were.
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount   = { images.size }
    )

    val stripState = rememberLazyListState()

    // Follow the pager with the strip, so the active thumbnail is always on screen. This is what
    // makes a strip that scrolls usable: past the fourth photo the active one would otherwise sit
    // off the right edge with nothing to say so.
    LaunchedEffect(pagerState, images.size) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page < images.size) stripState.animateScrollToItem(page)
        }
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
                    contentDescription = "Photo ${page + 1} sur ${images.size}",
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
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(DsSpacing.md)
                        .clip(DsShapes.pill)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = DsSpacing.sm, vertical = 2.dp)
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${images.size}",
                        fontSize   = DsTextSize.caption,
                        fontWeight = FontWeight.Medium,
                        color      = Color.White
                    )
                }
            }
        }

        // The strip earns its place only when there is something to choose between, or something
        // to manage. One photo and no mode means the main image says it all.
        val canAdd = manageMode && images.size < MAX_IMAGES_PER_PRODUCT
        if (images.size > 1 || manageMode) {
            Spacer(Modifier.height(DsSpacing.sm))
            LazyRow(
                state               = stripState,
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                contentPadding      = PaddingValues(horizontal = DsSpacing.xs),
                modifier            = Modifier.fillMaxWidth()
            ) {
                items(images, key = { it.id }) { image ->
                    GalleryThumbnail(
                        image      = image,
                        active     = images.indexOf(image) == pagerState.currentPage,
                        manageMode = manageMode,
                        onSelect   = { onOpenViewer(images.indexOf(image)) },
                        onDelete   = { onDeletePhoto(image) },
                        onSetCover = { onSetCover(image) }
                    )
                }
                if (canAdd) {
                    item(key = "add") {
                        AddPhotoTile(onClick = onAddPhoto)
                    }
                }
            }
        }

        if (manageMode) {
            Spacer(Modifier.height(DsSpacing.sm))
            Text(
                text = if (images.size >= MAX_IMAGES_PER_PRODUCT)
                    "Maximum $MAX_IMAGES_PER_PRODUCT photos atteint · appuyez sur ★ pour définir la photo principale"
                else
                    "${images.size}/$MAX_IMAGES_PER_PRODUCT photos · appuyez sur ★ pour définir la photo principale",
                fontSize = DsTextSize.caption,
                color    = DsColors.TextSecondary,
                modifier = Modifier.padding(horizontal = DsSpacing.xs)
            )
        }
    }
}

/**
 * One thumbnail. In manage mode it grows a delete badge and a cover badge.
 *
 * Both badges are 28 dp and sit outside the 56 dp image, so neither overlaps the tap area that
 * selects the photo and neither is the 18 dp target the prototype used.
 */
@Composable
private fun GalleryThumbnail(
    image      : ProductImage,
    active     : Boolean,
    manageMode : Boolean,
    onSelect   : () -> Unit,
    onDelete   : () -> Unit,
    onSetCover : () -> Unit
) {
    Box(modifier = Modifier.size(width = 64.dp, height = if (manageMode) 84.dp else 64.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(56.dp)
                .clip(DsShapes.medium)
                .background(DsColors.SurfaceMuted)
                .border(
                    width = if (active) 2.dp else 1.dp,
                    color = if (active) DsColors.Primary else DsColors.Border,
                    shape = DsShapes.medium
                )
                .clickable(onClick = onSelect),
            contentAlignment = Alignment.Center
        ) {
            EntityImage(
                ref                = image.ref,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize().clip(DsShapes.medium)
            ) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint     = DsColors.TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // The cover is marked in both modes — outside manage mode it is the only way to tell
            // which photo the product rows and the receipts will use.
            if (image.isCover) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(3.dp)
                        .size(16.dp)
                        .clip(DsShapes.pill)
                        .background(DsColors.Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Photo principale",
                        tint     = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        if (manageMode) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs)
            ) {
                if (!image.isCover) {
                    ThumbnailAction(
                        icon = Icons.Default.Star,
                        tint = DsColors.Primary,
                        background = DsColors.PrimaryLight,
                        description = "Définir comme photo principale",
                        onClick = onSetCover
                    )
                }
                ThumbnailAction(
                    icon = Icons.Default.Close,
                    tint = DsColors.Danger,
                    background = DsColors.DangerLight,
                    description = "Supprimer cette photo",
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun ThumbnailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(DsShapes.pill)
            .background(background)
            .border(1.dp, DsColors.Border, DsShapes.pill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
    Box(modifier = Modifier.size(width = 64.dp, height = 84.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(56.dp)
                .clip(DsShapes.medium)
                .background(DsColors.PrimaryLight)
                .border(1.dp, DsColors.Primary.copy(alpha = 0.35f), DsShapes.medium)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AddAPhoto,
                contentDescription = "Ajouter une photo",
                tint     = DsColors.Primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * What a product with no photos shows — which is most of them.
 *
 * Not an error and not an empty box: outside manage mode it is the same muted placeholder the rest
 * of the app draws for a missing picture, and inside it, it is the button that adds the first one.
 */
@Composable
private fun GalleryEmptyState(
    manageMode : Boolean,
    onAddPhoto : () -> Unit,
    modifier   : Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(DsShapes.large)
            .background(DsColors.SurfaceMuted)
            .then(if (manageMode) Modifier.clickable(onClick = onAddPhoto) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (manageMode) Icons.Default.AddAPhoto else Icons.Default.PhotoCamera,
                contentDescription = null,
                tint     = if (manageMode) DsColors.Primary else DsColors.TextTertiary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(DsSpacing.sm))
            Text(
                if (manageMode) "Ajouter une photo" else "Aucune photo",
                fontSize   = DsTextSize.bodySmall,
                fontWeight = FontWeight.Medium,
                color      = if (manageMode) DsColors.Primary else DsColors.TextSecondary
            )
            if (!manageMode) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Gérer les photos pour en ajouter",
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
    Icon(Icons.Default.Check, contentDescription = "Terminer", tint = DsColors.Primary)
}
