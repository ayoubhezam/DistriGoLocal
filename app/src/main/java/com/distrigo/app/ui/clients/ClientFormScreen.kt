package com.distrigo.app.ui.clients

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Client
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Map

@Composable
fun ClientFormScreen(
    client       : Client? = null,
    onBack       : () -> Unit,
    onSaved      : (Int) -> Unit,
    viewModel    : ClientViewModel  = viewModel()
) {
    val isEdit = client != null
    val context = LocalContext.current

    var name         by remember { mutableStateOf(client?.name ?: "") }
    var phone        by remember { mutableStateOf(client?.phone ?: "") }
    var wilayaName   by remember { mutableStateOf(client?.wilaya_name ?: "") }
    var communeName  by remember { mutableStateOf(client?.commune_name ?: "") }
    var address      by remember { mutableStateOf(client?.address ?: "") }
    var note         by remember { mutableStateOf(client?.note ?: "") }
    var customerType by remember { mutableStateOf(client?.customer_type ?: "retail") }
    var imageBase64  by remember { mutableStateOf<String?>(client?.image_uri) }
    var latitudeStr  by remember { mutableStateOf(client?.latitude?.toString() ?: "") }
    var longitudeStr by remember { mutableStateOf(client?.longitude?.toString() ?: "") }
    var isLocating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf("") }
    var isSaving  by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val stream         = context.contentResolver.openInputStream(it)
            val originalBitmap = BitmapFactory.decodeStream(stream)
            stream?.close()
            val maxSize = 400
            val width   = originalBitmap.width
            val height  = originalBitmap.height
            val ratio   = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
            val resized = android.graphics.Bitmap.createScaledBitmap(
                originalBitmap, (width * ratio).toInt(), (height * ratio).toInt(), true
            )
            val outputStream = java.io.ByteArrayOutputStream()
            resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, outputStream)
            imageBase64 = "data:image/jpeg;base64," +
                    Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }
    }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }




    fun save() {
        if (name.isBlank()) {
            nameError = "Le nom est obligatoire."
            return
        }
        nameError = ""
        isSaving  = true

        val data = mapOf(
            "name"          to name.trim(),
            "phone"         to phone.trim().ifEmpty { null },
            "wilaya_name"   to wilayaName.trim().ifEmpty { null },
            "commune_name"  to communeName.trim().ifEmpty { null },
            "address"       to address.trim().ifEmpty { null },
            "note"          to note.trim().ifEmpty { null },
            "customer_type" to customerType,
            "image_uri"     to imageBase64,
            "latitude"      to latitudeStr.trim().toDoubleOrNull(),
            "longitude"     to longitudeStr.trim().toDoubleOrNull()
        )

        if (isEdit) {
            viewModel.updateClient(
                id        = client!!.id,
                client    = data,
                onSuccess = { onSaved(client.id) },
                onError   = { isSaving = false }
            )
        } else {
            viewModel.addClient(
                client    = data,
                onSuccess = { result ->
                    val newId = (result["id"] as? Double)?.toInt() ?: 0
                    onSaved(newId)
                },
                onError   = { isSaving = false }
            )
        }
    }

    BackHandler { onBack() }

    //اضافة جديدة
    if (showLocationPicker) {
        ClientLocationPickerScreen(
            initialLatitude  = latitudeStr.toDoubleOrNull(),
            initialLongitude = longitudeStr.toDoubleOrNull(),
            onBack = { showLocationPicker = false },
            onLocationSelected = { lat, lng ->
                latitudeStr  = lat.toString()
                longitudeStr = lng.toString()
                showLocationPicker = false
            }
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
            .verticalScroll(rememberScrollState())
            .padding(DsSpacing.lg)
    ) {
        // ── Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.xs))
            Text(
                if (isEdit) "Modifier le client" else "Nouveau client",
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary
            )
        }

        Spacer(Modifier.height(DsSpacing.md))

        // ── Photo ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(DsShapes.large)
                .background(DsColors.SurfaceMuted)
                .clickable { imagePicker.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (imageBase64 != null) {
                val imageBytes = Base64.decode(imageBase64!!.substringAfter("base64,"), Base64.NO_WRAP)
                val bitmap     = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                bitmap?.let {
                    Image(
                        bitmap             = it.asImageBitmap(),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint     = DsColors.Primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(DsSpacing.xs))
                    Text("Ajouter une photo", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(DsSpacing.md))

        // ── Type de client ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DsShapes.medium)
                .background(DsColors.SurfaceSunken)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf(
                "retail"     to "Détail",
                "wholesale"  to "Gros",
                "business"   to "Société"
            ).forEach { (value, label) ->
                val active = customerType == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(DsShapes.small)
                        .background(if (active) DsColors.Primary else Color.Transparent)
                        .clickable { customerType = value }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize   = DsTextSize.bodySmall,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color      = if (active) Color.White else DsColors.TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(DsSpacing.md))

        // ── Nom ──
        DsFormField(
            label         = "Nom du client *",
            value         = name,
            onValueChange = { name = it; nameError = "" },
            error         = nameError,
            placeholder   = "Ex: Ahmed Benali"
        )

        Spacer(Modifier.height(DsSpacing.md))

        // ── Téléphone ──
        DsFormField(
            label         = "Téléphone",
            value         = phone,
            onValueChange = { phone = it },
            placeholder   = "0555 12 34 56",
            keyboardType  = KeyboardType.Phone
        )

        Spacer(Modifier.height(DsSpacing.md))

        // ── Wilaya + Commune ──
        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            Box(modifier = Modifier.weight(1f)) {
                DsFormField(
                    label         = "Wilaya",
                    value         = wilayaName,
                    onValueChange = { wilayaName = it },
                    placeholder   = "Ex: Alger"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                DsFormField(
                    label         = "Commune",
                    value         = communeName,
                    onValueChange = { communeName = it },
                    placeholder   = "Ex: Bab Ezzouar"
                )
            }
        }

        Spacer(Modifier.height(DsSpacing.md))
        // ── Adresse ──
        DsFormField(
            label         = "Adresse",
            value         = address,
            onValueChange = { address = it },
            placeholder   = "Adresse complète",
            minLines      = 2,
            maxLines      = 2
        )

        Spacer(Modifier.height(DsSpacing.md))

        // ── Localisation (pour navigation GPS) ──
        Text(
            "Localisation (optionnel)",
            fontSize   = DsTextSize.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color      = DsColors.TextSecondary
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
            Box(modifier = Modifier.weight(1f)) {
                DsFormField(
                    label         = "Latitude",
                    value         = latitudeStr,
                    onValueChange = { latitudeStr = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                    placeholder   = "Ex: 36.7538",
                    keyboardType  = KeyboardType.Number
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                DsFormField(
                    label         = "Longitude",
                    value         = longitudeStr,
                    onValueChange = { longitudeStr = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                    placeholder   = "Ex: 3.0588",
                    keyboardType  = KeyboardType.Number
                )
            }
        }
        Spacer(Modifier.height(DsSpacing.sm))


        Spacer(Modifier.height(DsSpacing.xs))

        OutlinedButton(
            onClick  = { showLocationPicker = true },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape    = DsShapes.medium
        ) {
            Icon(Icons.Default.Map, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Choisir sur la carte", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        }

        if (locationError.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(locationError, fontSize = DsTextSize.caption, color = DsColors.Danger)
        }
        if (locationError.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(locationError, fontSize = DsTextSize.caption, color = DsColors.Danger)
        }


        // ── Note ──
        DsFormField(
            label         = "Note (optionnel)",
            value         = note,
            onValueChange = { note = it },
            placeholder   = "Remarques sur ce client",
            minLines      = 2,
            maxLines      = 3,
            imeAction     = ImeAction.Done
        )

        Spacer(Modifier.height(DsSpacing.xxl))

        // ── Enregistrer ──
        Button(
            onClick  = { save() },
            enabled  = !isSaving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    if (isEdit) "Enregistrer les modifications" else "Ajouter le client",
                    fontSize   = DsTextSize.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White
                )
            }
        }

        Spacer(Modifier.height(DsSpacing.lg))
    }
}

@Composable
private fun DsFormField(
    label         : String,
    value         : String,
    onValueChange : (String) -> Unit,
    error         : String = "",
    placeholder   : String = "",
    keyboardType  : KeyboardType = KeyboardType.Text,
    minLines      : Int = 1,
    maxLines      : Int = 1,
    imeAction     : ImeAction = ImeAction.Next
) {
    Column {
        Text(
            label,
            fontSize = DsTextSize.bodySmall,
            color    = DsColors.TextSecondary,
            modifier = Modifier.padding(bottom = DsSpacing.xs)
        )
        OutlinedTextField(
            value           = value,
            onValueChange   = onValueChange,
            placeholder     = { Text(placeholder, fontSize = DsTextSize.body) },
            singleLine      = minLines == 1 && maxLines == 1,
            minLines        = minLines,
            maxLines        = maxLines,
            isError         = error.isNotEmpty(),
            modifier        = Modifier.fillMaxWidth(),
            shape           = DsShapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = DsColors.Border,
                focusedBorderColor   = DsColors.Primary,
                errorBorderColor     = DsColors.Danger
            )
        )
        if (error.isNotEmpty()) {
            Text(
                error,
                fontSize = DsTextSize.caption,
                color    = DsColors.Danger,
                modifier = Modifier.padding(start = DsSpacing.xs, top = 2.dp)
            )
        }
    }
}