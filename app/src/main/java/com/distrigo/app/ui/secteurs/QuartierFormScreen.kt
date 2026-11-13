package com.distrigo.app.ui.secteurs

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.distrigo.app.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.trash.Usage
import com.distrigo.app.ui.designsystem.BrandDialog
import com.distrigo.app.ui.designsystem.BrandWideButton
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.LocalLicense

private val LockIcon = 20.dp

/**
 * « Nouveau quartier » / « Modifier le quartier » (owner's prototype v5, 2026-10-24): the name, and the secteur
 * read-only. [onSaved] after a save, [onDeleted] after a delete; Back with unsaved changes asks first.
 */
@Composable
fun QuartierFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: QuartierFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val license = LocalLicense.current
    val editing = viewModel.quartierId != null

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
            title   = sectText(if (editing) R.string.sect_edit_quartier else R.string.sect_new_quartier),
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
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.xl),
                ) {
                    Field(sectText(R.string.sect_quartier_name), required = true, error = state.nameError) {
                        TextInput(state.name, viewModel::setName, sectText(R.string.sect_quartier_example), isError = state.nameError != null)
                    }
                    Field(sectText(R.string.sect_secteur), help = sectText(R.string.sect_secteur_fixed)) {
                        FixedSecteur(state.secteurName, state.commune)
                    }
                    if (editing) {
                        val used = state.clients > 0
                        BrandWideButton(
                            sectText(R.string.sect_delete_quartier), Icons.Outlined.DeleteOutline, filled = false,
                            enabled = !used && !state.saving, danger = true,
                            onClick = { license.runOrExplain { refusal = null; confirmDelete = true } },
                        )
                        val blocked = if (used) sectText(R.string.sect_delete_blocked, Usage("client", "clients", state.clients.toLong()).toString()) else refusal
                        blocked?.let { Text(it, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary) }
                    }
                }
                Box(Modifier.fillMaxWidth().background(DsColors.Surface).padding(DsSpacing.lg)) {
                    BrandWideButton(
                        sectText(R.string.common_action_save), Icons.Outlined.Check, filled = true, enabled = !state.saving, busy = state.saving,
                        onClick = { license.runOrExplain { viewModel.save() } },
                    )
                }
            }
        }
    }

    if (confirmDiscard) {
        BrandDialog(
            title   = sectText(R.string.clients_discard_title),
            text    = sectText(R.string.sect_discard_body),
            confirm = sectText(R.string.clients_discard) to { confirmDiscard = false; onBack() },
            dismiss = sectText(R.string.clients_discard_continue) to { confirmDiscard = false },
            danger  = true,
        )
    }
    if (confirmDelete) {
        BrandDialog(
            title   = sectText(R.string.sect_delete_named, state.loadedName),
            text    = sectText(R.string.sect_quartier_deleted),
            confirm = sectText(R.string.common_action_delete) to { viewModel.delete() },
            dismiss = sectText(R.string.common_action_cancel) to { confirmDelete = false },
            danger  = true,
            busy    = state.saving,
        )
    }
}

/** The quartier's secteur, read-only: its name, its commune, a lock. */
@Composable
private fun FixedSecteur(name: String, commune: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = CardMinHeight).clip(DsShapes.large).background(DsColors.SurfaceSunken)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark)
            Text(sectText(R.string.sect_commune_of, commune), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        }
        Icon(Icons.Outlined.Lock, contentDescription = sectText(R.string.sect_locked), tint = DsColors.TextSecondary, modifier = Modifier.size(LockIcon))
    }
}
