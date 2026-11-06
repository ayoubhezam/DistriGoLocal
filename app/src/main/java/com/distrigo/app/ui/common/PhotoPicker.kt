package com.distrigo.app.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

/**
 * Where a product photo comes from: the camera, or the gallery.
 *
 * ### Why the camera at all
 *
 * A distributor meets a product before it exists in the catalogue — on a shelf, in a van, at a
 * supplier's counter. Sending them to the gallery means leaving the app, opening the camera, coming
 * back and finding the shot. One tap on « Prendre une photo » is the same job without the detour.
 *
 * ### Full size, not a thumbnail
 *
 * `ActivityResultContracts.TakePicture` writes into a [Uri] this app provides, so what comes back is
 * the camera's own file — not the postage stamp `ACTION_IMAGE_CAPTURE` hands over in its extras. The
 * file lands in `cacheDir/camera`, is read once by [ImageCapture] into the content-addressed store,
 * and is deleted immediately: nothing accumulates, and the camera roll is left alone.
 *
 * ### The permission
 *
 * An app that declares `CAMERA` in its manifest — this one does, for the barcode scanner — must hold
 * the permission before it may start a camera intent, even though the picture is taken by another
 * app. It is asked for at the moment the user chooses the camera, never on the way in, and a refusal
 * is reported rather than silently doing nothing.
 */
class PhotoPicker internal constructor(
    private val open: () -> Unit,
) {
    /** Offers the two sources. */
    fun choose() = open()
}

/**
 * A [PhotoPicker] for this screen: it shows the choice, handles the permission, and hands back the
 * [com.distrigo.app.data.image.ImageStore] reference of whatever was picked or taken.
 *
 * [onError] carries a message meant for the user — a refused permission, an unreadable image.
 */
@Composable
fun rememberPhotoPicker(
    onPicked : (String) -> Unit,
    onError  : (String) -> Unit,
): PhotoPicker {
    val context    = LocalContext.current
    val unreadable = stringResource(R.string.common_photo_unreadable)
    return rememberPhotoSourcePicker(title = stringResource(R.string.common_photo_add), onError = onError) { uri ->
        val ref = ImageCapture.captureToStore(context, uri)
        if (ref == null) onError(unreadable) else onPicked(ref)
    }
}

/**
 * [rememberPhotoPicker] for a gallery of several photos (a product's): the gallery lets up to [limit] be picked at
 * once, the camera still takes one. [onPicked] gets the stored references, in the order picked, and how many images
 * could not be read; [onBusy] is true while they are being stored, which takes a moment for five camera photos.
 */
@Composable
fun rememberPhotosPicker(
    limit    : Int,
    onPicked : (refs: List<String>, unreadable: Int) -> Unit,
    onError  : (String) -> Unit,
    onBusy   : (Boolean) -> Unit = {},
): PhotoPicker {
    val context = LocalContext.current
    return rememberSourcePicker(
        title           = stringResource(R.string.common_photos_add),
        gallerySubtitle = if (limit > 1) pluralStringResource(R.plurals.common_photo_gallery_up_to, limit, limit)
                          else stringResource(R.string.common_photo_gallery_sub),
        galleryLimit    = limit,
        onError         = onError,
        onRemove        = null,
    ) { uris ->
        onBusy(true)
        try {
            val refs = uris.mapNotNull { ImageCapture.captureToStore(context, it) }
            onPicked(refs, uris.size - refs.size)
        } finally {
            onBusy(false)
        }
    }
}

/**
 * The camera-or-gallery choice on its own, for a picture that is not an entity's photo (the account's profile
 * picture): [onImage] receives what was picked or taken, and may read it until it returns: a camera file is deleted
 * then. [onRemove], when given, adds « Supprimer la photo » under the two sources.
 */
@Composable
fun rememberPhotoSourcePicker(
    title    : String,
    onError  : (String) -> Unit,
    onRemove : (() -> Unit)? = null,
    onImage  : suspend (Uri) -> Unit,
): PhotoPicker = rememberSourcePicker(
    title           = title,
    gallerySubtitle = stringResource(R.string.common_photo_gallery_sub),
    galleryLimit    = 1,
    onError         = onError,
    onRemove        = onRemove,
) { uris -> uris.firstOrNull()?.let { onImage(it) } }

/**
 * The sheet and its launchers. [galleryLimit] above one opens Android's photo picker for that many at once; one keeps
 * the plain « choose a file » the app has always opened. [onImages] may read what it gets until it returns.
 */
@Composable
private fun rememberSourcePicker(
    title           : String,
    gallerySubtitle : String,
    galleryLimit    : Int,
    onError         : (String) -> Unit,
    onRemove        : (() -> Unit)?,
    onImages        : suspend (List<Uri>) -> Unit,
): PhotoPicker {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val noCamera      = stringResource(R.string.common_photo_no_camera)
    val cameraRefused = stringResource(R.string.common_photo_camera_refused)
    var sheetOpen by remember { mutableStateOf(false) }

    /** Hands the picked images to the caller, then drops the camera's file. */
    fun deliver(uris: List<Uri>, thenDelete: File? = null) {
        scope.launch {
            try {
                onImages(uris)
            } finally {
                thenDelete?.delete()
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { deliver(listOf(it)) }
    }
    // Remembered per limit: a new contract instance on every composition would register the launcher again each time.
    // At least two, which the contract requires; where Android has no photo picker the limit is not enforced, and the
    // caller refuses what does not fit.
    val manyContract = remember(galleryLimit) { ActivityResultContracts.PickMultipleVisualMedia(galleryLimit.coerceAtLeast(2)) }
    val galleryMany = rememberLauncherForActivityResult(manyContract) { uris ->
        if (uris.isNotEmpty()) deliver(uris)
    }

    // The file the camera writes into, kept across the launch so the result can be read from it. Saveable: a phone
    // short of memory may stop the app while the camera is in front, and the photo must still be found on return.
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = pendingPath?.let(::File)
        pendingPath = null
        when {
            file == null -> Unit
            taken        -> deliver(listOf(FileProvider.getUriForFile(context, authority(context), file)), thenDelete = file)
            // Cancelled: the camera app wrote nothing worth keeping.
            else         -> file.delete()
        }
    }

    fun launchCamera() {
        val file = newCameraFile(context)
        pendingPath = file.path
        runCatching { camera.launch(FileProvider.getUriForFile(context, authority(context), file)) }
            .onFailure {
                pendingPath = null
                file.delete()
                onError(noCamera)
            }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else onError(cameraRefused)
    }

    if (sheetOpen) {
        PhotoSourceSheet(
            title     = title,
            gallerySubtitle = gallerySubtitle,
            onCamera  = {
                sheetOpen = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    launchCamera()
                } else {
                    permission.launch(Manifest.permission.CAMERA)
                }
            },
            onGallery = {
                sheetOpen = false
                if (galleryLimit > 1) {
                    galleryMany.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    gallery.launch("image/*")
                }
            },
            onRemove  = onRemove?.let { remove -> { sheetOpen = false; remove() } },
            onDismiss = { sheetOpen = false }
        )
    }

    return remember { PhotoPicker { sheetOpen = true } }
}

/** The two sources, as a sheet — the shape every other choice in the app is offered in — and [onRemove] if any. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSourceSheet(
    title: String,
    gallerySubtitle: String,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = DsColors.Surface
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = DsSpacing.lg)
                .padding(bottom = DsSpacing.lg)
        ) {
            Text(
                title,
                fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark,
                modifier = Modifier.padding(bottom = DsSpacing.sm)
            )
            SourceRow(Icons.Default.PhotoCamera, stringResource(R.string.common_photo_take), stringResource(R.string.common_photo_take_sub), onCamera)
            SourceRow(Icons.Default.PhotoLibrary, stringResource(R.string.common_photo_gallery), gallerySubtitle, onGallery)
            if (onRemove != null) {
                SourceRow(Icons.Default.Delete, stringResource(R.string.common_photo_remove), subtitle = null, onClick = onRemove, danger = true)
            }
        }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(DsShapes.medium).background(if (danger) DsColors.Danger.copy(alpha = 0.1f) else DsColors.PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (danger) DsColors.Danger else DsColors.Primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column {
            Text(title, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = if (danger) DsColors.Danger else DsColors.TextPrimary)
            subtitle?.let { Text(it, fontSize = DsTextSize.caption, color = DsColors.TextSecondary) }
        }
    }
}

/** The authority declared for the app's FileProvider — see AndroidManifest.xml. */
private fun authority(context: Context) = "${context.packageName}.fileprovider"

/** A fresh file under `cacheDir/camera` for the camera to write into. */
private fun newCameraFile(context: Context): File {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    return File(dir, "photo-${System.currentTimeMillis()}.jpg")
}
