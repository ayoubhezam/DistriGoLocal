package com.distrigo.app.ui.secteurs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.geo.GeoRepository
import com.distrigo.app.data.model.Commune
import com.distrigo.app.ui.common.SearchableSelectSheet
import com.distrigo.app.ui.designsystem.BrandDialog
import com.distrigo.app.ui.designsystem.BrandWideButton
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.LocalLicense

private val FieldMinHeight = 56.dp
private val FieldIcon = 22.dp
private val SmallIcon = 16.dp
private const val DESCRIPTION_LINES = 3

/** « Sedrata · Wilaya de Souk Ahras »: a commune, and the wilaya that tells two of the same name apart. */
private fun place(commune: String, wilaya: String?) = if (wilaya.isNullOrBlank()) commune else "$commune · Wilaya de $wilaya"

/**
 * « Nouveau secteur » / « Modifier le secteur » (owner's prototype v5, 2026-10-24). [onSaved] after a save,
 * [onDeleted] after a delete (the secteur's details are gone too); Back with unsaved changes asks first.
 */
@Composable
fun SecteurFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: SecteurFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val license = LocalLicense.current
    val editing = viewModel.secteurId != null

    var choosingWilaya by remember { mutableStateOf(false) }
    var choosingCommune by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var refusal by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SecteurFormEvent.Saved -> onSaved()
                SecteurFormEvent.Deleted -> onDeleted()
                is SecteurFormEvent.Refused -> { confirmDelete = false; refusal = event.reason }
            }
        }
    }
    val leave = { if (state.dirty) confirmDiscard = true else onBack() }
    BackHandler { leave() }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted).imePadding()) {
        DsTopAppBar(
            title   = if (editing) "Modifier le secteur" else "Nouveau secteur",
            leading = DsTopBarLeading.Back(leave),
        )
        when (val status = state.status) {
            SecteurFormStatus.Loading -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.PrimaryDark)
            }
            is SecteurFormStatus.Error -> Text(
                status.message, fontSize = DsTextSize.body, color = DsColors.TextSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(DsSpacing.xxl),
            )
            SecteurFormStatus.Ready -> {
                val f = state.fields
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.xl),
                ) {
                    Field("Nom du secteur", required = true, error = state.nameError, help = "Nom libre, indépendant des noms de quartiers.") {
                        TextInput(f.name, viewModel::setName, "Ex : Centre-ville", isError = state.nameError != null)
                        val commune = f.commune
                        if (commune != null && f.name.isBlank()) Suggestion(commune, viewModel::useSuggestion)
                    }
                    val locked = state.lockedBy != null
                    Field("Wilaya", required = true, error = state.wilayaError) {
                        PickField(
                            value = f.wilaya, placeholder = "Sélectionner une wilaya", label = "Choisir la wilaya",
                            locked = locked, isError = state.wilayaError != null, onClick = { choosingWilaya = true },
                        )
                    }
                    Field(
                        "Commune", required = true, error = state.communeError,
                        help = state.lockedBy?.let { "La commune ne peut pas être modifiée : le secteur est utilisé par $it." }
                            ?: "Choisissez d'abord la wilaya.".takeIf { f.wilaya == null },
                    ) {
                        PickField(
                            value = f.commune?.let { place(it, f.wilaya) }, placeholder = "Sélectionner une commune", label = "Choisir la commune",
                            locked = locked, enabled = f.wilaya != null, isError = state.communeError != null,
                            onClick = { choosingCommune = true },
                        )
                    }
                    Field("Description (optionnel)") {
                        TextInput(f.description, viewModel::setDescription, "Ex : Zone résidentielle au nord de la ville", lines = DESCRIPTION_LINES)
                    }
                    if (editing) {
                        BrandWideButton(
                            "Supprimer le secteur", Icons.Outlined.DeleteOutline, filled = false,
                            enabled = state.lockedBy == null && !state.saving, danger = true,
                            onClick = { license.runOrExplain { refusal = null; confirmDelete = true } },
                        )
                        val blocked = state.lockedBy?.let { "Impossible de le supprimer : il est utilisé par $it." } ?: refusal
                        blocked?.let { Text(it, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary) }
                    }
                }
                Box(Modifier.fillMaxWidth().background(DsColors.Surface).padding(DsSpacing.lg)) {
                    BrandWideButton(
                        "Enregistrer", Icons.Outlined.Check, filled = true, enabled = !state.saving, busy = state.saving,
                        onClick = { license.runOrExplain { viewModel.save() } },
                    )
                }
            }
        }
    }

    if (choosingWilaya) {
        SearchableSelectSheet(
            title      = "Wilaya",
            items      = remember { GeoRepository.getWilayas().sortedBy { it.wilayaCode } },
            itemLabel  = { "${it.wilayaCode}-${it.nameFr}" },
            itemKey    = { it.wilayaCode },
            isSelected = { it.nameFr == state.fields.wilaya },
            onDismiss  = { choosingWilaya = false },
            onSelect   = { viewModel.setWilaya(it.nameFr) },
        )
    }
    val wilaya = remember(state.fields.wilaya) { GeoRepository.findWilayaByFrName(state.fields.wilaya) }
    if (choosingCommune && wilaya != null) {
        // The wilaya's communes, its chef-lieu first, then in their own order
        val communes = remember(wilaya) {
            GeoRepository.getCommunes(wilaya.wilayaCode)
                .sortedWith(compareBy<Commune>({ !it.nameFr.equals(wilaya.nameFr, ignoreCase = true) }, { it.id }))
        }
        SearchableSelectSheet(
            title      = "Commune · Wilaya de ${wilaya.nameFr}",
            items      = communes,
            itemLabel  = { it.nameFr },
            itemKey    = { it.id },
            isSelected = { it.nameFr == state.fields.commune },
            onDismiss  = { choosingCommune = false },
            onSelect   = { viewModel.setCommune(it.nameFr) },
        )
    }
    if (confirmDiscard) {
        BrandDialog(
            title   = "Abandonner la saisie ?",
            text    = "Les informations saisies ne seront pas enregistrées.",
            confirm = "Abandonner" to { confirmDiscard = false; onBack() },
            dismiss = "Continuer la saisie" to { confirmDiscard = false },
            danger  = true,
        )
    }
    if (confirmDelete) {
        BrandDialog(
            title   = "Supprimer « ${state.loaded.name} » ?",
            text    = "Le secteur sera supprimé définitivement.",
            confirm = "Supprimer" to { viewModel.delete() },
            dismiss = "Annuler" to { confirmDelete = false },
            danger  = true,
            busy    = state.saving,
        )
    }
}

/**
 * A field that opens a list: what was picked, or [placeholder]. Read-only, with a lock, while the secteur is used;
 * greyed out until it can be used ([enabled]: the commune waits for the wilaya).
 */
@Composable
private fun PickField(
    value: String?,
    placeholder: String,
    label: String,
    locked: Boolean,
    isError: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val active = enabled && !locked
    Row(
        Modifier.fillMaxWidth().heightIn(min = FieldMinHeight).clip(DsShapes.large)
            .background(if (active) DsColors.Surface else DsColors.SurfaceSunken)
            .border(1.dp, if (isError) DsColors.Danger else DsColors.Border, DsShapes.large)
            .clickable(enabled = active, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value ?: placeholder, fontSize = DsTextSize.bodyLarge,
            color = if (value != null) DsColors.PrimaryDark else DsColors.TextTertiary, modifier = Modifier.weight(1f),
        )
        Icon(
            if (locked) Icons.Outlined.Lock else Icons.Default.ArrowDropDown, contentDescription = null,
            tint = if (active || locked) DsColors.TextSecondary else DsColors.TextTertiary, modifier = Modifier.size(FieldIcon),
        )
    }
}

/** « + Suggestion : Sedrata »: fills the name with the commune's. */
@Composable
private fun Suggestion(commune: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(DsShapes.pill).background(DsColors.PrimaryLight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.xs),
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(SmallIcon))
        Text("Suggestion : $commune", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
    }
}
