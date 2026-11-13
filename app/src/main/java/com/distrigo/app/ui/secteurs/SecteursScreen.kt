package com.distrigo.app.ui.secteurs

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.distrigo.app.R
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
import com.distrigo.app.ui.common.communeShownName

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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val license = LocalLicense.current
    val add: () -> Unit = { license.runOrExplain(onNew) }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = sectText(R.string.sect_title), leading = DsTopBarLeading.Back(onBack)) {
            DsTopBarAddButton(contentDescription = sectText(R.string.sect_new), onClick = add)
        }

        when (val s = state) {
            SecteursUiState.Loading -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.PrimaryDark)
            }
            is SecteursUiState.Error -> EmptyState(Icons.Outlined.Place, DsColors.Danger, sectText(R.string.sect_unreadable), s.message)
            SecteursUiState.Empty -> EmptyState(
                Icons.Outlined.Place, DsColors.Success, sectText(R.string.sect_none),
                sectText(R.string.sect_none_text),
                action = sectText(R.string.sect_new), onAction = add,
            )
            is SecteursUiState.Success -> {
                val tokens = searchTokens(query)
                // The commune as stored and as shown (in Arabic, its Arabic name)
                val shown = s.secteurs.filter { matchesAllTokens(tokens, it.name, it.commune, communeShownName(it.wilaya, it.commune)) }
                DsCompactSearchField(
                    value = query, onValueChange = viewModel::search, placeholder = sectText(R.string.sect_search),
                    modifier = Modifier.padding(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.sm),
                )
                LazyColumn(
                    contentPadding      = PaddingValues(DsSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
                ) {
                    items(shown, key = { it.id }) { secteur -> SecteurCard(secteur) { onOpen(secteur.id) } }
                    if (shown.isEmpty()) item(key = "none") {
                        EmptyState(Icons.Outlined.Search, DsColors.Primary, sectText(R.string.sect_no_result), sectText(R.string.sect_no_match))
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
        CardLine(communeShownName(secteur.wilaya, secteur.commune) ?: secteur.commune)
        Spacer(Modifier.height(DsSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
            Meta(Icons.Outlined.Home, DsColors.Primary,
                if (secteur.quartiers == 0) sectText(R.string.sect_no_quartier) else count(secteur.quartiers, "quartier", "quartiers"))
            Meta(Icons.Outlined.Group, DsColors.AccentViolet, count(secteur.clients, "client", "clients"))
        }
    }
}
