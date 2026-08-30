package com.distrigo.app.ui.suppliers

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.geo.GeoRepository
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.ui.common.DsSelectorField
import com.distrigo.app.ui.common.SearchableSelectSheet
import com.distrigo.app.ui.products.FormField
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

fun formatPhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return digits.chunked(2).joinToString(" ")
}

fun formatDZD(amount: Double): String {
    return String.format("%,.2f", amount)
        .replace(",", " ")
        .replace(".", ",")
}

@Composable
fun SupplierFormScreen(
    supplier     : Supplier? = null,
    onBack       : () -> Unit,
    onSaved      : () -> Unit,
    viewModel    : SupplierViewModel = hiltViewModel()
) {
    val isEdit = supplier != null

    var name    by remember { mutableStateOf(supplier?.name    ?: "") }
    var phone   by remember { mutableStateOf(supplier?.phone   ?: "") }
    var address by remember { mutableStateOf(supplier?.address ?: "") }
    var note    by remember { mutableStateOf(supplier?.note    ?: "") }
    var balance by remember { mutableStateOf(if (isEdit) supplier!!.initial_balance.toString() else "") }
    var latitude         by remember { mutableStateOf<Double?>(supplier?.latitude) }
    var longitude        by remember { mutableStateOf<Double?>(supplier?.longitude) }
    var showMapPicker    by remember { mutableStateOf(false) }

    var wilayaName  by remember { mutableStateOf(supplier?.wilaya_name ?: "") }
    var communeName by remember { mutableStateOf(supplier?.commune_name ?: "") }
    var selectedWilayaCode by remember(wilayaName) {
        mutableStateOf(GeoRepository.findWilayaByFrName(wilayaName)?.wilayaCode)
    }
    var showWilayaSheet  by remember { mutableStateOf(false) }
    var showCommuneSheet by remember { mutableStateOf(false) }
    var imageBase64 by remember { mutableStateOf<String?>(supplier?.image_uri) }

    var nameError    by remember { mutableStateOf("") }
    var phoneError   by remember { mutableStateOf("") }
    var isSaving     by remember { mutableStateOf(false) }

    val colors   = listOf(0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A, 0xFFC62828, 0xFFE65100, 0xFF00695C)
    val color    = if (name.isNotEmpty()) Color(colors[name[0].code % colors.size]) else DsColors.Primary
    val initials = name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val stream         = context.contentResolver.openInputStream(it)
            val originalBitmap = BitmapFactory.decodeStream(stream)
            stream?.close()
            val maxSize = 400
            val ratio   = minOf(maxSize.toFloat() / originalBitmap.width, maxSize.toFloat() / originalBitmap.height)
            val resized = android.graphics.Bitmap.createScaledBitmap(
                originalBitmap, (originalBitmap.width * ratio).toInt(), (originalBitmap.height * ratio).toInt(), true
            )
            val outputStream = java.io.ByteArrayOutputStream()
            resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, outputStream)
            imageBase64 = "data:image/jpeg;base64," +
                    Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }
    }

    fun validate(): Boolean {
        var valid = true
        nameError = ""; phoneError = ""
        if (name.isBlank()) { nameError = "Le nom est obligatoire."; valid = false }
        return valid
    }

    fun save() {
        if (!validate()) return
        isSaving = true
        val data = if (isEdit) {
            mapOf(
                "name"      to name.trim(),
                "phone" to phone.trim().ifEmpty { null },
                "address"   to address.trim().ifEmpty { null },
                "note"      to note.trim().ifEmpty { null },
                "initial_balance" to (balance.toDoubleOrNull() ?: 0.0),
                "balance"   to (balance.toDoubleOrNull() ?: 0.0),
                "latitude"  to latitude,
                "longitude" to longitude,
                "wilaya_name"  to wilayaName.trim().ifEmpty { null },
                "commune_name" to communeName.trim().ifEmpty { null },
                "image_uri"    to imageBase64
            )
        } else {
            mapOf(
                "name"            to name.trim(),
                "phone"           to phone.trim(),
                "address"         to address.trim().ifEmpty { null },
                "note"            to note.trim().ifEmpty { null },
                "balance"         to (balance.toDoubleOrNull() ?: 0.0),
                "initial_balance" to (balance.toDoubleOrNull() ?: 0.0),
                "latitude"        to latitude,
                "longitude"       to longitude,
                "wilaya_name"     to wilayaName.trim().ifEmpty { null },
                "commune_name"    to communeName.trim().ifEmpty { null },
                "image_uri"       to imageBase64
            )
        }
        if (isEdit) {
            viewModel.updateSupplier(
                id        = supplier!!.id,
                supplier  = data,
                onSuccess = { onSaved() },
                onError   = { isSaving = false }
            )
        } else {
            viewModel.addSupplier(
                supplier  = data,
                onSuccess = { onSaved() },
                onError   = { isSaving = false }
            )
        }
    }

    BackHandler { onBack() }

    if (showMapPicker) {
        BackHandler { showMapPicker = false }
        MapPickerScreen(
            initialLat       = latitude  ?: 36.1901,
            initialLng       = longitude ?: 5.4133,
            onLocationPicked = { lat, lng ->
                latitude      = lat
                longitude     = lng
                showMapPicker = false
            },
            onBack = { showMapPicker = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        DsTopAppBar(
            title   = if (isEdit) "Modifier le fournisseur" else "Nouveau fournisseur",
            leading = DsTopBarLeading.Back(onBack)
        )

        // The screen-wide inset moved off the root so the bar can run edge to edge; the
        // scrolling body carries it instead.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(DsSpacing.lg)
        ) {

            Spacer(Modifier.height(16.dp))

            // ── Photo ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(DsShapes.large)
                    .background(DsColors.SurfaceSunken)
                    .clickable { photoPicker.launch("image/*") },
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
                } else if (name.isNotEmpty()) {
                    Box(
                        modifier         = Modifier.size(64.dp).clip(DsShapes.pill),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.13f)))
                        Text(initials, fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold, color = color)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint     = DsColors.Primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Ajouter une photo", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── Nom ──
            FormField(
                label         = "Nom du fournisseur *",
                value         = name,
                onValueChange = { name = it; nameError = "" },
                error         = nameError,
                placeholder   = "Ex: Société Al Baraka"
            )
            Spacer(Modifier.height(12.dp))

            // ── Téléphone ──
            FormField(
                label         = "Numéro de téléphone",
                value         = phone,
                onValueChange = { phone = it; phoneError = "" },
                error         = phoneError,
                placeholder   = "05XX XXX XXX",
                isNumber      = true
            )
            Spacer(Modifier.height(12.dp))

            // ── Localisation ──
            Text("Localisation", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
            OutlinedButton(
                onClick  = { showMapPicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape    = DsShapes.medium,
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.Primary),
                border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (latitude != null && longitude != null)
                        "${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"
                    else
                        "Choisir sur la carte",
                    fontSize = DsTextSize.body
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Wilaya + Commune ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    DsSelectorField(
                        label       = "Wilaya",
                        value       = wilayaName,
                        placeholder = "Sélectionner une wilaya",
                        onClick     = { showWilayaSheet = true }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    DsSelectorField(
                        label       = "Commune",
                        value       = communeName,
                        placeholder = "Sélectionner une commune",
                        onClick     = { if (selectedWilayaCode != null) showCommuneSheet = true }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // ── Adresse ──
            FormField(
                label         = "Adresse",
                value         = address,
                onValueChange = { address = it },
                placeholder   = "Rue, ville…"
            )
            Spacer(Modifier.height(12.dp))

            // ── Solde initial ──
            FormField(
                label         = "Solde initial (DA)",
                value         = balance,
                onValueChange = { balance = it },
                placeholder   = "0",
                isNumber      = true
            )
            Text(
                "Montant que vous devez à ce fournisseur au départ",
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
            Spacer(Modifier.height(12.dp))

            // ── Note ──
            Text("Note (optionnel)", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
            OutlinedTextField(
                value         = note,
                onValueChange = { note = it },
                placeholder   = { Text("Informations supplémentaires…", fontSize = DsTextSize.body) },
                modifier      = Modifier.fillMaxWidth(),
                shape         = DsShapes.medium,
                minLines      = 2,
                maxLines      = 4,
                colors        = dsTextFieldColors(
                    unfocusedBorderColor = DsColors.Border,
                    focusedBorderColor   = DsColors.Primary
                )
            )

            Spacer(Modifier.height(24.dp))

            // ── Save ──
            Button(
                onClick  = { save() },
                enabled  = !isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = DsShapes.large,
                colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
            ) {
                if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text(
                    if (isEdit) "Enregistrer les modifications" else "Ajouter le fournisseur",
                    fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))
        }

        if (showWilayaSheet) {
            SearchableSelectSheet(
                title      = "Sélectionner une wilaya",
                items      = GeoRepository.getWilayas(),
                itemLabel  = { "${it.wilayaCode}-${it.nameFr}" },
                onDismiss  = { showWilayaSheet = false },
                onSelect   = { wilaya ->
                    wilayaName         = wilaya.nameFr
                    selectedWilayaCode = wilaya.wilayaCode
                    communeName        = ""
                }
            )
        }

        if (showCommuneSheet && selectedWilayaCode != null) {
            val sortedCommunes = remember(selectedWilayaCode) {
                GeoRepository.getCommunes(selectedWilayaCode!!).sortedBy { it.id }
            }
            SearchableSelectSheet(
                title      = "Sélectionner une commune",
                items      = sortedCommunes.mapIndexed { index, commune -> (index + 1) to commune },
                itemLabel  = { (num, commune) -> "${num.toString().padStart(2, '0')}-${commune.nameFr}" },
                onDismiss  = { showCommuneSheet = false },
                onSelect   = { (_, commune) ->
                    communeName = commune.nameFr
                }
            )
        }
    }
}
