package com.distrigo.app.ui.settings.receipt

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.BusinessSettingsStore
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.common.FileImage
import kotlinx.coroutines.launch

@Composable
fun ReceiptSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var name        by remember { mutableStateOf(BusinessSettingsStore.getBusinessName(context)) }
    var phone       by remember { mutableStateOf(BusinessSettingsStore.getBusinessPhone(context) ?: "") }
    var logoFile    by remember { mutableStateOf(BusinessSettingsStore.getLogoFile(context)) }
    var logoVersion by remember { mutableStateOf(0) }
    var isSaving    by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // Downscaled and written off the main thread (see saveLogo). The previous logo stays on
            // screen until the new one is on disk, and stays saved if the new one cannot be read.
            scope.launch {
                val saved = BusinessSettingsStore.saveLogo(context, it)
                if (saved != null) {
                    logoFile = saved
                    logoVersion++
                } else {
                    Toast.makeText(context, "Image illisible", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun save() {
        isSaving = true
        BusinessSettingsStore.saveBusinessName(context, name.trim())
        BusinessSettingsStore.saveBusinessPhone(context, phone.trim())
        Toast.makeText(context, "Paramètres du reçu enregistrés", Toast.LENGTH_SHORT).show()
        isSaving = false
        onBack()
    }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        DsTopAppBar(
            title   = "Paramètres du reçu",
            leading = DsTopBarLeading.Back(onBack)
        )

        // The screen-wide inset moved off the root so the bar can run edge to edge; the scrolling
        // body carries it instead.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(DsSpacing.lg)
        ) {

            // ── Logo ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(DsShapes.large)
                    .background(DsColors.SurfaceMuted)
                    .clickable { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (logoFile != null) {
                    // Drawn by Coil, sized to this box and off the main thread. It used to be decoded
                    // right here with BitmapFactory, whole, on every recomposition — once per
                    // keystroke in the fields below. key(logoVersion) starts a fresh request when a
                    // new logo replaces the old one under the same file name.
                    key(logoVersion) {
                        FileImage(
                            file               = logoFile,
                            contentDescription = null,
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.Crop
                        ) { }
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
                        Text("Ajouter un logo", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(DsSpacing.md))

            // ── Nom du commerce ──
            Column {
                Text(
                    "Nom du commerce",
                    fontSize = DsTextSize.bodySmall,
                    color    = DsColors.TextSecondary,
                    modifier = Modifier.padding(bottom = DsSpacing.xs)
                )
                OutlinedTextField(
                    value           = name,
                    onValueChange   = { name = it },
                    placeholder     = { Text("Ex: DISTRIGO", fontSize = DsTextSize.body) },
                    singleLine      = true,
                    modifier        = Modifier.fillMaxWidth(),
                    shape           = DsShapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    colors = dsTextFieldColors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )
            }

            Spacer(Modifier.height(DsSpacing.md))

            // ── Téléphone ──
            //
            // Printed in the receipt's left column, under the date. Optional: left empty the
            // receipt shows "-" there rather than an empty row, so a receipt never looks truncated.
            Column {
                Text(
                    "Téléphone",
                    fontSize = DsTextSize.bodySmall,
                    color    = DsColors.TextSecondary,
                    modifier = Modifier.padding(bottom = DsSpacing.xs)
                )
                OutlinedTextField(
                    value           = phone,
                    onValueChange   = { phone = it },
                    placeholder     = { Text("Ex: 0555 12 34 56", fontSize = DsTextSize.body) },
                    singleLine      = true,
                    modifier        = Modifier.fillMaxWidth(),
                    shape           = DsShapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    colors = dsTextFieldColors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor   = DsColors.Primary
                    )
                )
            }

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
                        "Enregistrer",
                        fontSize   = DsTextSize.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White
                    )
                }
            }

            Spacer(Modifier.height(DsSpacing.lg))
        }
    }
}