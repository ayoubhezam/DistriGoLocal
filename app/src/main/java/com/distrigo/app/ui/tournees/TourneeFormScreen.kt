package com.distrigo.app.ui.tournees

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.distrigo.app.data.model.Tournee
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

    var nom               by remember { mutableStateOf(tournee?.nom ?: "") }
    var chauffeur         by remember { mutableStateOf(tournee?.chauffeur ?: "") }
    var vehicule          by remember { mutableStateOf(tournee?.vehicule ?: "") }
    var note              by remember { mutableStateOf(tournee?.note ?: "") }
    var wilayaName        by remember { mutableStateOf(tournee?.wilaya_name ?: "") }
    var communeName       by remember { mutableStateOf(tournee?.commune_name ?: "") }
    var nomError          by remember { mutableStateOf("") }
    var isSaving          by remember { mutableStateOf(false) }

    fun save() {
        if (nom.isBlank()) {
            nomError = "Le nom de la tournée est obligatoire."
            return
        }
        nomError = ""
        isSaving = true

        if (isEdit) {
            viewModel.updateTournee(
                id          = tournee!!.id,
                nom         = nom.trim(),
                wilayaName  = wilayaName.trim().ifEmpty { null },
                communeName = communeName.trim().ifEmpty { null },
                chauffeur   = chauffeur.trim().ifEmpty { null },
                vehicule    = vehicule.trim().ifEmpty { null },
                note        = note.trim().ifEmpty { null },
                onSuccess   = { onSaved() },
                onError     = { isSaving = false }
            )
        } else {
            viewModel.createTournee(
                nom         = nom.trim(),
                wilayaName  = wilayaName.trim().ifEmpty { null },
                communeName = communeName.trim().ifEmpty { null },
                chauffeur   = chauffeur.trim().ifEmpty { null },
                vehicule    = vehicule.trim().ifEmpty { null },
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

        // ── Nom ──
        DsFormField(
            label         = "Nom de la tournée *",
            value         = nom,
            onValueChange = { nom = it; nomError = "" },
            error         = nomError,
            placeholder   = "Ex: Tournée Est"
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

        // ── Chauffeur / Vendeur ──
        DsFormField(
            label         = "Chauffeur / Vendeur",
            value         = chauffeur,
            onValueChange = { chauffeur = it },
            placeholder   = "Ex: Ahmed Ben"
        )

        Spacer(Modifier.height(DsSpacing.md))

        // ── Véhicule ──
        DsFormField(
            label         = "Véhicule",
            value         = vehicule,
            onValueChange = { vehicule = it },
            placeholder   = "Ex: Camion 01"
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
