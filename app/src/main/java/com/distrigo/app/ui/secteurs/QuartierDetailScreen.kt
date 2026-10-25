package com.distrigo.app.ui.secteurs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.lists.QuartierClient
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.EntityAvatar
import com.distrigo.app.ui.common.matchesAllTokens
import com.distrigo.app.ui.common.searchTokens
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.LocalLicense
import com.distrigo.app.ui.navigation.DrillTarget
import com.distrigo.app.ui.navigation.LocalDrillDown
import com.distrigo.app.ui.navigation.ShowsRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Past this many clients, the quartier's list gets a search (owner's prototype v5). */
private const val SEARCH_FROM = 6

/** How long an added client's row stays fully lit, then how long it fades. */
private const val FLASH_HOLD_MS = 700L
private const val FLASH_FADE_MS = 1600

/**
 * « Détails du quartier » (owner's prototype v5, 2026-10-24): no summary card and no « + » in the bar — the
 * quartier's name is the blue line under the title (long press: its form), then its clients and « Ajouter un
 * client », which offers an existing client or a new one.
 *
 * [onAddExisting] and [onAddNew] open « Ajouter des clients » and the new client's form; a null one says « Bientôt
 * disponible. ».
 */
@Composable
fun QuartierDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAddExisting: ((Long) -> Unit)?,
    onAddNew: ((Long) -> Unit)?,
    /** Clients just added from « Ajouter des clients », lit up for a moment, and the line that says so. */
    added: Set<Int> = emptySet(),
    addedMessage: String? = null,
    viewModel: QuartierDetailViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    ShowsRecord(DrillTarget.Quartier(viewModel.quartierId.toInt()))
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()
    val license = LocalLicense.current
    val drill = LocalDrillDown.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val say: (String) -> Unit = { message ->
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message) }
    }
    var choosing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // 1 when the added rows come back, fading out over two seconds
    val flash = remember { Animatable(if (added.isEmpty()) 0f else 1f) }
    LaunchedEffect(addedMessage) { addedMessage?.let(say) }
    val loaded = state as? QuartierDetailUiState.Success
    // Coming back, the list first shown can be the one from before: wait for the refresh that has the added rows
    val arrived = loaded?.clients?.any { it.id in added } == true
    LaunchedEffect(arrived) {
        if (!arrived) return@LaunchedEffect
        val first = loaded!!.clients.indexOfFirst { it.id in added }
        // The header, then the search past six clients, come before the rows
        if (first >= 0) listState.animateScrollToItem(first + if (loaded.clients.size > SEARCH_FROM) 2 else 1)
        // Lit long enough to be seen once the screen has settled, then fading
        delay(FLASH_HOLD_MS)
        flash.animateTo(0f, tween(durationMillis = FLASH_FADE_MS))
    }

    Box(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        Column(Modifier.fillMaxSize()) {
            val quartier = (state as? QuartierDetailUiState.Success)?.quartier
            DsTopAppBar(
                title   = "Détails du quartier",
                leading = DsTopBarLeading.Back(onBack),
                subtitleContent = quartier?.let {
                    {
                        EditableSubtitle(
                            name = it.name, rest = it.secteurName, editLabel = "Modifier le quartier ${it.name}",
                            onEdit = { onEdit(it.id) }, onTap = { say(EDIT_HINT) },
                        )
                    }
                },
            )
            when (val s = state) {
                QuartierDetailUiState.Loading -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.PrimaryDark)
                }
                is QuartierDetailUiState.Error -> EmptyState(Icons.Outlined.Home, DsColors.Danger, "Quartier introuvable", s.message)
                is QuartierDetailUiState.Success -> {
                    val tokens = searchTokens(query)
                    val shown = s.clients.filter {
                        matchesAllTokens(tokens, it.name, it.phone, it.phone?.filterNot(Char::isWhitespace), it.address)
                    }
                    LazyColumn(
                        state               = listState,
                        contentPadding      = PaddingValues(DsSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
                    ) {
                        item(key = "header") {
                            SectionHeader("Clients", s.clients.size) {
                                TonalAction("Ajouter un client") { license.runOrExplain { choosing = true } }
                            }
                        }
                        if (s.clients.size > SEARCH_FROM) item(key = "search") {
                            DsCompactSearchField(value = query, onValueChange = viewModel::search, placeholder = "Rechercher un client")
                        }
                        items(shown, key = { it.id }) { client ->
                            ClientRow(client, highlight = if (client.id in added) flash.value else 0f) { drill(DrillTarget.Client(client.id)) }
                        }
                        when {
                            s.clients.isEmpty() -> item(key = "none") {
                                EmptyState(
                                    Icons.Outlined.Group, DsColors.AccentViolet, "Aucun client dans ce quartier",
                                    "Utilisez « Ajouter un client » pour choisir un client existant ou en créer un nouveau.",
                                )
                            }
                            shown.isEmpty() -> item(key = "no-match") {
                                EmptyState(Icons.Outlined.Search, DsColors.Primary, "Aucun résultat", "Aucun client ne correspond à « $query ».")
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(DsSpacing.lg))
    }

    val id = viewModel.quartierId
    if (choosing) {
        AddClientSheet(
            onExisting = { choosing = false; onAddExisting?.invoke(id) ?: say("Bientôt disponible.") },
            onNew      = { choosing = false; onAddNew?.invoke(id) ?: say("Bientôt disponible.") },
            onDismiss  = { choosing = false },
        )
    }
}

/** A client: its avatar, its name, its phone. Opens its record. */
@Composable
private fun ClientRow(client: QuartierClient, highlight: Float, onClick: () -> Unit) {
    CardRow(client.name, onClick, leading = { EntityAvatar(client.name, client.imageUri, size = CardTile) }, highlight = highlight) {
        CardTitle(client.name)
        CardLine(client.phone?.takeIf { it.isNotBlank() } ?: "Téléphone non renseigné")
    }
}

/** « Ajouter un client »: exactly two choices, an existing client or a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddClientSheet(onExisting: () -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = DsColors.Surface,
    ) {
        Column(
            Modifier.padding(start = DsSpacing.lg, end = DsSpacing.lg, bottom = DsSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
        ) {
            Text("Ajouter un client", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark)
            CardRow("Ajouter un client existant", onExisting, leading = { Tile(Icons.Outlined.Group, DsColors.AccentViolet) }) {
                CardTitle("Ajouter un client existant")
                CardLine("Choisir dans la liste de vos clients")
            }
            CardRow("Ajouter un nouveau client", onNew, leading = { Tile(Icons.Default.Add, DsColors.Success) }) {
                CardTitle("Ajouter un nouveau client")
                CardLine("Créer une fiche, quartier présélectionné")
            }
        }
    }
}
