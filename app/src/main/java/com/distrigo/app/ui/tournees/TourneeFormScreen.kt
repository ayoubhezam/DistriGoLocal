package com.distrigo.app.ui.tournees

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.geo.GeoRepository
import com.distrigo.app.data.model.Secteur
import com.distrigo.app.data.model.Tournee
import com.distrigo.app.ui.common.SearchableSelectSheet
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager

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
    var secteurId          by remember { mutableStateOf(tournee?.secteur_id) }
    var secteurName        by remember { mutableStateOf(tournee?.secteur_name ?: "") }
    var note               by remember { mutableStateOf(tournee?.note ?: "") }

    var showWilayaSheet    by remember { mutableStateOf(false) }
    var showCommuneSheet   by remember { mutableStateOf(false) }
    var showSecteurSheet   by remember { mutableStateOf(false) }

    var communeError       by remember { mutableStateOf("") }
    var isSaving           by remember { mutableStateOf(false) }

    val secteurs by viewModel.secteurs.collectAsState()

    LaunchedEffect(communeName) {
        if (communeName.isNotBlank()) viewModel.loadSecteurs(communeName)
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
                secteurId   = secteurId,
                secteurName = secteurName.trim().ifEmpty { null },
                note        = note.trim().ifEmpty { null },
                onSuccess   = { onSaved() },
                onError     = { isSaving = false }
            )
        } else {
            viewModel.createTournee(
                nom         = communeName.trim(),
                wilayaName  = wilayaName.trim().ifEmpty { null },
                communeName = communeName.trim().ifEmpty { null },
                secteurId   = secteurId,
                secteurName = secteurName.trim().ifEmpty { null },
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

        // ── Secteur (optionnel) ──
        Column {
            Text(
                "Secteur (optionnel)",
                fontSize   = DsTextSize.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color      = DsColors.TextPrimary
            )
            Text(
                "Sélectionnez un secteur existant ou ajoutez-en un nouveau.",
                fontSize = DsTextSize.caption,
                color    = DsColors.TextSecondary,
                modifier = Modifier.padding(bottom = DsSpacing.xs, top = 2.dp)
            )
            DsSelectorField(
                label       = null,
                value       = secteurName,
                placeholder = "Sélectionner un secteur",
                onClick     = { if (communeName.isNotBlank()) showSecteurSheet = true }
            )
        }

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
            itemLabel  = { it.nameFr },
            onDismiss  = { showWilayaSheet = false },
            onSelect   = { wilaya ->
                wilayaName         = wilaya.nameFr
                selectedWilayaCode = wilaya.wilayaCode
                communeName        = ""
                secteurId          = null
                secteurName        = ""
            }
        )
    }

    if (showCommuneSheet && selectedWilayaCode != null) {
        SearchableSelectSheet(
            title      = "Sélectionner une commune",
            items      = GeoRepository.getCommunes(selectedWilayaCode!!),
            itemLabel  = { it.nameFr },
            onDismiss  = { showCommuneSheet = false },
            onSelect   = { commune ->
                communeName = commune.nameFr
                secteurId   = null
                secteurName = ""
            }
        )
    }

    if (showSecteurSheet) {
        SecteurPickerSheet(
            communeName = communeName,
            wilayaName  = wilayaName,
            secteurs    = secteurs,
            onDismiss   = { showSecteurSheet = false },
            onSelect    = { secteur ->
                secteurId   = secteur.id
                secteurName = secteur.nom
            },
            onAddNew    = { nom ->
                viewModel.createSecteur(
                    nom         = nom,
                    communeName = communeName,
                    wilayaName  = wilayaName.ifEmpty { null },
                    onSuccess   = { secteur ->
                        secteurId   = secteur.id
                        secteurName = secteur.nom
                    },
                    onError     = { }
                )
            }
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecteurPickerSheet(
    communeName : String,
    wilayaName  : String,
    secteurs    : List<Secteur>,
    onDismiss   : () -> Unit,
    onSelect    : (Secteur) -> Unit,
    onAddNew    : (String) -> Unit
) {
    var showAddField by remember { mutableStateOf(false) }
    var newName       by remember { mutableStateOf("") }
    var isFieldFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = isFieldFocused) {
        focusManager.clearFocus()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.md)
                .padding(bottom = DsSpacing.lg)
        ) {
            Text(
                "Secteurs disponibles à $communeName",
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary
            )
            Spacer(Modifier.height(DsSpacing.sm))

            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(secteurs, key = { it.id }) { secteur ->
                    Text(
                        text     = secteur.nom,
                        fontSize = DsTextSize.body,
                        color    = DsColors.TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(secteur); onDismiss() }
                            .padding(vertical = DsSpacing.sm)
                    )
                    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
                }
                if (secteurs.isEmpty()) {
                    item {
                        Text(
                            "Aucun secteur pour cette commune",
                            color    = DsColors.TextSecondary,
                            modifier = Modifier.padding(vertical = DsSpacing.md)
                        )
                    }
                }
            }

            Spacer(Modifier.height(DsSpacing.sm))

            if (showAddField) {
                OutlinedTextField(
                    value         = newName,
                    onValueChange = { newName = it },
                    placeholder   = { Text("Nom du secteur") },
                    singleLine    = true,
                    shape         = DsShapes.medium,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFieldFocused = it.isFocused }
                )
                Spacer(Modifier.height(DsSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    OutlinedButton(
                        onClick  = { showAddField = false; newName = "" },
                        modifier = Modifier.weight(1f)
                    ) { Text("Annuler") }
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onAddNew(newName.trim())
                                newName = ""
                                showAddField = false
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                    ) { Text("Ajouter") }
                }
            } else {
                OutlinedButton(
                    onClick  = { showAddField = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = DsShapes.medium
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ajouter un nouveau secteur", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DsSelectorField(
    label       : String?,
    value       : String,
    placeholder : String,
    error       : String = "",
    onClick     : () -> Unit
) {
    Column {
        if (label != null) {
            Text(
                label,
                fontSize = DsTextSize.bodySmall,
                color    = DsColors.TextSecondary,
                modifier = Modifier.padding(bottom = DsSpacing.xs)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (error.isNotEmpty()) DsColors.Danger else DsColors.Border, DsShapes.medium)
                .clip(DsShapes.medium)
                .clickable(onClick = onClick)
                .padding(horizontal = DsSpacing.md, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text     = value.ifBlank { placeholder },
                fontSize = DsTextSize.body,
                color    = if (value.isBlank()) DsColors.TextTertiary else DsColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = DsColors.TextSecondary)
        }
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