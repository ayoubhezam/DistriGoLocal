package com.distrigo.app.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    var sheetOpen by remember { mutableStateOf(false) }

    /** Reads the picked image into the store, then tells the caller. */
    fun store(uri: Uri, thenDelete: File? = null) {
        scope.launch {
            val ref = ImageCapture.captureToStore(context, uri)
            thenDelete?.delete()
            if (ref == null) onError("Image illisible") else onPicked(ref)
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { store(it) }
    }

    // The file the camera writes into, kept across the launch so the result can be read from it.
    var pending by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = pending
        pending = null
        when {
            file == null -> Unit
            taken        -> store(FileProvider.getUriForFile(context, authority(context), file), thenDelete = file)
            // Cancelled: the camera app wrote nothing worth keeping.
            else         -> file.delete()
        }
    }

    fun launchCamera() {
        val file = newCameraFile(context)
        pending = file
        runCatching { camera.launch(FileProvider.getUriForFile(context, authority(context), file)) }
            .onFailure {
                pending = null
                file.delete()
                onError("Aucune application appareil photo n'est disponible.")
            }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else onError("L'accès à l'appareil photo a été refusé. Vous pouvez l'autoriser dans les paramètres du téléphone.")
    }

    if (sheetOpen) {
        PhotoSourceSheet(
            onCamera  = {
                sheetOpen = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    launchCamera()
                } else {
                    permission.launch(Manifest.permission.CAMERA)
                }
            },
            onGallery = { sheetOpen = false; gallery.launch("image/*") },
            onDismiss = { sheetOpen = false }
        )
    }

    return remember { PhotoPicker { sheetOpen = true } }
}

/** The two sources, as a sheet — the shape every other choice in the app is offered in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSourceSheet(onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
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
                "Ajouter une photo",
                fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary,
                modifier = Modifier.padding(bottom = DsSpacing.sm)
            )
            SourceRow(Icons.Default.PhotoCamera, "Prendre une photo", "Avec l'appareil photo", onCamera)
            SourceRow(Icons.Default.PhotoLibrary, "Choisir depuis la galerie", "Parmi les images du téléphone", onGallery)
        }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column {
            Text(title, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
            Text(subtitle, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
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
