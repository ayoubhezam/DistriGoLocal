package com.distrigo.app.ui.secteurs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.ui.navigation.DrillTarget
import com.distrigo.app.ui.navigation.ShowsRecord
import com.distrigo.app.data.lists.QuartierSummary
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.LocalLicense
import kotlinx.coroutines.launch

/**
 * « Détails du secteur » (owner's prototype v5, 2026-10-24): no summary card — the secteur's name is the blue line
 * under the title (long press: its form) — then its quartiers and « Ajouter un quartier ».
 *
 * [onAddQuartier] and [onOpenQuartier] are null until the quartier screens exist; their taps say so meanwhile.
 */
@Composable
fun SecteurDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAddQuartier: ((Long) -> Unit)?,
    onOpenQuartier: ((Long) -> Unit)?,
    viewModel: SecteurDetailViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    ShowsRecord(DrillTarget.Secteur(viewModel.secteurId.toInt()))
    val state by viewModel.state.collectAsState()
    val license = LocalLicense.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val say: (String) -> Unit = { message ->
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message) }
    }
    val soon = { say("Bientôt disponible.") }

    Box(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        Column(Modifier.fillMaxSize()) {
            val secteur = (state as? SecteurDetailUiState.Success)?.secteur
            DsTopAppBar(
                title   = "Détails du secteur",
                leading = DsTopBarLeading.Back(onBack),
                subtitleContent = secteur?.let {
                    {
                        EditableSubtitle(
                            name = it.name, rest = it.commune, editLabel = "Modifier le secteur ${it.name}",
                            onEdit = { onEdit(it.id) }, onTap = { say(EDIT_HINT) },
                        )
                    }
                },
            )
            when (val s = state) {
                SecteurDetailUiState.Loading -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.PrimaryDark)
                }
                is SecteurDetailUiState.Error -> EmptyState(Icons.Outlined.Place, DsColors.Danger, "Secteur introuvable", s.message)
                is SecteurDetailUiState.Success -> {
                    val add = { license.runOrExplain { onAddQuartier?.invoke(s.secteur.id) ?: soon() } }
                    LazyColumn(
                        contentPadding      = PaddingValues(DsSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
                    ) {
                        item(key = "header") {
                            SectionHeader("Quartiers", s.quartiers.size) { TonalAction("Ajouter un quartier", add) }
                        }
                        items(s.quartiers, key = { it.id }) { quartier ->
                            QuartierRow(quartier) { onOpenQuartier?.invoke(quartier.id) ?: soon() }
                        }
                        if (s.quartiers.isEmpty()) item(key = "none") {
                            EmptyState(
                                Icons.Outlined.Home, DsColors.Primary, "Aucun quartier",
                                "Ajoutez les quartiers de ce secteur pour y rattacher vos clients.",
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(DsSpacing.lg))
    }
}

/** A quartier: the house tile, its name, how many clients. */
@Composable
private fun QuartierRow(quartier: QuartierSummary, onClick: () -> Unit) {
    CardRow(quartier.name, onClick, leading = { Tile(Icons.Outlined.Home, DsColors.Primary) }) {
        CardTitle(quartier.name)
        CardLine(count(quartier.clients, "client", "clients"))
    }
}
