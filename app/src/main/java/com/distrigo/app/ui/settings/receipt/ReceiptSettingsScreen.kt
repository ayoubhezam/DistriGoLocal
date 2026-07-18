package com.distrigo.app.ui.settings.receipt

import android.graphics.BitmapFactory
import android.widget.Toast
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
import com.distrigo.app.data.BusinessSettingsStore
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

@Composable
fun ReceiptSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var name        by remember { mutableStateOf(BusinessSettingsStore.getBusinessName(context)) }
    var logoFile    by remember { mutableStateOf(BusinessSettingsStore.getLogoFile(context)) }
    var logoVersion by remember { mutableStateOf(0) }
    var isSaving    by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            logoFile = BusinessSettingsStore.saveLogo(context, it)
            logoVersion++
        }
    }

    fun save() {
        isSaving = true
        BusinessSettingsStore.saveBusinessName(context, name.trim())
        Toast.makeText(context, "Paramètres du reçu enregistrés", Toast.LENGTH_SHORT).show()
        isSaving = false
        onBack()
    }

    BackHandler { onBack() }

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
                "Paramètres du reçu",
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary
            )
        }

        Spacer(Modifier.height(DsSpacing.md))

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
                key(logoVersion) {
                    val bitmap = BitmapFactory.decodeFile(logoFile!!.absolutePath)
                    bitmap?.let {
                        Image(
                            bitmap             = it.asImageBitmap(),
                            contentDescription = null,
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.Crop
                        )
                    }
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
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