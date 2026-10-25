package com.distrigo.app.ui.secteurs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.lists.SecteurSummary
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.matchesAllTokens
import com.distrigo.app.ui.common.searchTokens
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarAddButton
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.LocalLicense

/**
 * « Secteurs » (owner's prototype v5, 2026-10-24): a card per secteur — its name, its commune, how many quartiers
 * and clients — a search on the name and the commune, and « + » for a new secteur.
 */
@Composable
fun SecteursScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    /** The secteur tapped: its details. */
    onOpen: (Long) -> Unit,
    viewModel: SecteursViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()
    val license = LocalLicense.current
    val add: () -> Unit = { license.runOrExplain(onNew) }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = "Secteurs", leading = DsTopBarLeading.Back(onBack)) {
            DsTopBarAddButton(contentDescription = "Nouveau secteur", onClick = add)
        }

        when (val s = state) {
            SecteursUiState.Loading -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.PrimaryDark)
            }
            is SecteursUiState.Error -> EmptyState(Icons.Outlined.Place, DsColors.Danger, "Impossible de lire les secteurs", s.message)
            SecteursUiState.Empty -> EmptyState(
                Icons.Outlined.Place, DsColors.Success, "Aucun secteur",
                "Créez votre premier secteur pour organiser vos clients par zone.",
                action = "Nouveau secteur", onAction = add,
            )
            is SecteursUiState.Success -> {
                val tokens = searchTokens(query)
                val shown = s.secteurs.filter { matchesAllTokens(tokens, it.name, it.commune) }
                DsCompactSearchField(
                    value = query, onValueChange = viewModel::search, placeholder = "Rechercher un secteur ou une commune",
                    modifier = Modifier.padding(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.sm),
                )
                LazyColumn(
                    contentPadding      = PaddingValues(DsSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
                ) {
                    items(shown, key = { it.id }) { secteur -> SecteurCard(secteur) { onOpen(secteur.id) } }
                    if (shown.isEmpty()) item(key = "none") {
                        EmptyState(Icons.Outlined.Search, DsColors.Primary, "Aucun résultat", "Aucun secteur ne correspond à votre recherche.")
                    }
                }
            }
        }
    }
}

/** A secteur: the pin tile, its name and commune, then « N quartiers » and « N clients ». */
@Composable
private fun SecteurCard(secteur: SecteurSummary, onClick: () -> Unit) {
    CardRow(secteur.name, onClick, leading = { Tile(Icons.Outlined.Place, DsColors.Success) }) {
        CardTitle(secteur.name)
        CardLine(secteur.commune)
        Spacer(Modifier.height(DsSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
            Meta(Icons.Outlined.Home, DsColors.Primary,
                if (secteur.quartiers == 0) "Aucun quartier" else count(secteur.quartiers, "quartier", "quartiers"))
            Meta(Icons.Outlined.Group, DsColors.AccentViolet, count(secteur.clients, "client", "clients"))
        }
    }
}
