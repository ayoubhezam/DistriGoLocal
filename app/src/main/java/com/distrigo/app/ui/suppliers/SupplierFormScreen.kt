package com.distrigo.app.ui.suppliers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.ui.products.*
import androidx.activity.compose.BackHandler

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
    viewModel    : SupplierViewModel = viewModel()
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

    var nameError    by remember { mutableStateOf("") }
    var phoneError   by remember { mutableStateOf("") }
    var isSaving     by remember { mutableStateOf(false) }

    val colors   = listOf(0xFF1565C0, 0xFF2E7D32, 0xFF6A1B9A, 0xFFC62828, 0xFFE65100, 0xFF00695C)
    val color    = if (name.isNotEmpty()) Color(colors[name[0].code % colors.size]) else PrimaryBlue
    val initials = name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

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
                "commune_name" to communeName.trim().ifEmpty { null }
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
                "commune_name"    to communeName.trim().ifEmpty { null }
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
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Spacer(Modifier.width(4.dp))
            Text(
                if (isEdit) "Modifier le fournisseur" else "Nouveau fournisseur",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Avatar Preview ──
        if (name.isNotEmpty()) {
            Box(
                modifier         = Modifier.size(64.dp).clip(RoundedCornerShape(50)).align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.13f)))
                Text(initials, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Spacer(Modifier.height(16.dp))
        }

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
        Text("Localisation", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedButton(
            onClick  = { showMapPicker = true },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
            border   = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (latitude != null && longitude != null)
                    "${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"
                else
                    "Choisir sur la carte",
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── Wilaya + Commune ──
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                FormField(
                    label         = "Wilaya",
                    value         = wilayaName,
                    onValueChange = { wilayaName = it },
                    placeholder   = "Ex: Alger"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                FormField(
                    label         = "Commune",
                    value         = communeName,
                    onValueChange = { communeName = it },
                    placeholder   = "Ex: Bab Ezzouar"
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
            fontSize = 11.sp, color = TextMuted,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
        Spacer(Modifier.height(12.dp))

        // ── Note ──
        Text("Note (optionnel)", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value         = note,
            onValueChange = { note = it },
            placeholder   = { Text("Informations supplémentaires…", fontSize = 14.sp) },
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
            minLines      = 2,
            maxLines      = 4,
            colors        = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderGray,
                focusedBorderColor   = PrimaryBlue
            )
        )

        Spacer(Modifier.height(24.dp))

        // ── Save ──
        Button(
            onClick  = { save() },
            enabled  = !isSaving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            else Text(
                if (isEdit) "Enregistrer les modifications" else "Ajouter le fournisseur",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}
