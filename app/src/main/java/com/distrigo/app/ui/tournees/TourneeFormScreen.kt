package com.distrigo.app.ui.tournees

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.geo.GeoRepository
import com.distrigo.app.data.model.Tournee
import com.distrigo.app.ui.common.SearchableSelectSheet
import com.distrigo.app.ui.common.DsSelectorField
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
@Composable
fun TourneeFormScreen(
    tournee      : Tournee? = null,
    onBack       : () -> Unit,
    onSaved      : () -> Unit,
    viewModel    : TourneeViewModel = viewModel()
) {
    val isEdit = tournee != null

    var wilayaName         by remember { mutableStateOf(tournee?.wilaya_name ?: "") }
    var communeName        by remember { mutableStateOf(tournee?.commune_name ?: "") }
    var selectedWilayaCode by remember(wilayaName) {
        mutableStateOf(GeoRepository.findWilayaByFrName(wilayaName)?.wilayaCode)
    }
    var note               by remember { mutableStateOf(tournee?.note ?: "") }

    var showWilayaSheet    by remember { mutableStateOf(false) }
    var showCommuneSheet   by remember { mutableStateOf(false) }

    var communeError       by remember { mutableStateOf("") }
    var isSaving           by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!isEdit && wilayaName.isBlank()) {
            viewModel.getDefaultWilaya()?.let { defaultWilaya ->
                wilayaName         = defaultWilaya
                selectedWilayaCode = GeoRepository.findWilayaByFrName(defaultWilaya)?.wilayaCode
            }
        }
    }

    fun save() {
        if (communeName.isBlank()) {
            communeError = "La commune est obligatoire."
            return
        }
        communeError = ""
        isSaving = true

        if (isEdit) {
            viewModel.updateTournee(
                id          = tournee!!.id,
                nom         = communeName.trim(),
                wilayaName  = wilayaName.trim().ifEmpty { null },
                communeName = communeName.trim().ifEmpty { null },
                note        = note.trim().ifEmpty { null },
                onSuccess   = { onSaved() },
                onError     = { isSaving = false }
            )
        } else {
            viewModel.createTournee(
                nom         = communeName.trim(),
                wilayaName  = wilayaName.trim().ifEmpty { null },
                communeName = communeName.trim().ifEmpty { null },
                note        = note.trim().ifEmpty { null },
                onSuccess   = { onSaved() },
                onError     = { isSaving = false }
            )
        }
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
                if (isEdit) "Modifier la tournée" else "Nouvelle tournée",
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary
            )
        }

        Spacer(Modifier.height(DsSpacing.md))

        // ── Bandeau d'info ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DsColors.PrimaryLight, DsShapes.medium)
                .padding(DsSpacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DsSpacing.xs))
            Text(
                "La tournée sera identifiée par la wilaya, la commune et le secteur (si spécifié).",
                fontSize = DsTextSize.bodySmall,
                color    = DsColors.Primary
            )
        }

        Spacer(Modifier.height(DsSpacing.md))

        // ── Wilaya ──
        DsSelectorField(
            label       = "Wilaya *",
            value       = wilayaName,
            placeholder = "Sélectionner une wilaya",
            onClick     = { showWilayaSheet = true }
        )

        Spacer(Modifier.height(DsSpacing.md))

        // ── Commune ──
        DsSelectorField(
            label       = "Commune *",
            value       = communeName,
            placeholder = "Sélectionner une commune",
            error       = communeError,
            onClick     = { if (selectedWilayaCode != null) showCommuneSheet = true }
        )


        Spacer(Modifier.height(DsSpacing.md))

        // ── Note ──
        DsFormField(
            label         = "Note (optionnel)",
            value         = note,
            onValueChange = { note = it },
            placeholder   = "Remarques sur cette tournée",
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
                    if (isEdit) "Enregistrer les modifications" else "Créer la tournée",
                    fontSize   = DsTextSize.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White
                )
            }
        }

        Spacer(Modifier.height(DsSpacing.lg))
    }

    // ── Sheets ──
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