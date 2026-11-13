package com.distrigo.app.ui.clients

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.distrigo.app.ui.designsystem.LocalLicense

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.produceState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.Client
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsTopBarSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import androidx.compose.ui.text.style.TextOverflow
import com.distrigo.app.ui.common.EntityImage
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.clientsInDebt
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.common.refusalText
import com.distrigo.app.ui.common.PartyFilterSheet
import com.distrigo.app.ui.common.CountAndFiltersRow
import com.distrigo.app.ui.common.RemovableFilterChips
import com.distrigo.app.ui.common.ClientListFilters
import com.distrigo.app.ui.common.placesOf
import com.distrigo.app.ui.designsystem.DsTopBarOverflowMenu
import com.distrigo.app.data.model.Amount
import com.distrigo.app.ui.designsystem.BrandWideButton
import com.distrigo.app.ui.designsystem.BrandDialog
import com.distrigo.app.ui.designsystem.BrandChips
import com.distrigo.app.ui.common.EntityAvatar
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R
import com.distrigo.app.ui.common.ScrollToTopBox
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.res.pluralStringResource
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClientsScreen(
    viewModel     : ClientViewModel = hiltViewModel(),
    modifier      : Modifier = Modifier,
    onBack        : (() -> Unit)? = null,
    onAddClient   : () -> Unit = {},
    onEditClient  : (Int) -> Unit = {},
    onClientClick : (Int) -> Unit = {},
    /** "⋮ → Importer depuis Excel": clients from a workbook's « Clients » sheet. */
    onImport      : () -> Unit = {},
    /**
     * Select mode, « Ajouter des clients » to a quartier (owner, 2026-10-24): rows tick instead of opening, and a
     * bottom button saves. Null for the list itself. [viewModel] then lists only clients outside the quartier.
     */
    selection     : ClientSelectionViewModel? = null,
    onSelectionSaved : (ClientSelectionEvent.Saved) -> Unit = {},
){
    val selecting = selection != null
    val noSelection = remember { kotlinx.coroutines.flow.MutableStateFlow(ClientSelectionState()) }
    val picked by (selection?.state ?: noSelection).collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    if (selection != null) {
        LaunchedEffect(selection) {
            selection.events.collect { event -> if (event is ClientSelectionEvent.Saved) onSelectionSaved(event) }
        }
        // A selection is work: leaving with one asks first, as the forms do
        BackHandler { if (picked.selected.isNotEmpty()) confirmDiscard = true else onBack?.invoke() }
    }
    val money = LocalMoneyFormatter.current
    // A blocked write explains itself instead (docs/license_l5_enforcement.md §2)
    val license = LocalLicense.current

    // The list as the search and the filters narrow it, searched and paged in SQL once typing pauses (PERF-01, see
    // ClientViewModel.pagedClients). Only the search box reads what is being typed.
    val shown = viewModel.pagedClients.collectAsLazyPagingItems()
    val shownCount by viewModel.shownCount.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    // Leaving the list clears the search, as it did when the box's text lived in this screen.
    DisposableEffect(Unit) { onDispose { viewModel.listSearch = "" } }
    var showFilterSheet  by remember { mutableStateOf(false) }
    val filters          = viewModel.listFilters
    var showDeleteDialog by remember { mutableStateOf<Client?>(null) }
    var longPressClient  by remember { mutableStateOf<Client?>(null) }

    // ── Delete confirmation ──
    showDeleteDialog?.let { client ->
        var deleteError by remember { mutableStateOf("") }
        val refusalContext = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.clients_delete_title)) },
            text  = {
                Column {
                    Text(stringResource(R.string.products_delete_body, client.name))
                    if (deleteError.isNotEmpty()) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text(deleteError, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteClient(
                        id        = client.id,
                        onSuccess = { showDeleteDialog = null },
                        onError   = { deleteError = refusalText(it, money).resolve(refusalContext) }
                    )
                }) { Text(stringResource(R.string.common_action_delete), color = DsColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.common_action_cancel)) }
            },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    // ── Long-press menu ──
    longPressClient?.let { client ->
        AlertDialog(
            onDismissRequest = { longPressClient = null },
            title          = { Text(client.name, fontWeight = FontWeight.Bold) },
            confirmButton  = {},
            dismissButton  = {},
            shape          = DsShapes.medium,
            containerColor = DsColors.Surface,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.medium)
                            .background(DsColors.PrimaryLight)
                            .clickable {
                                longPressClient = null
                                license.runOrExplain { onEditClient(client.id) }
                            }
                            .padding(14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.common_action_edit), fontSize = DsTextSize.body, color = DsColors.Primary, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.medium)
                            .background(DsColors.DangerLight)
                            .clickable {
                                longPressClient  = null
                                license.runOrExplain { showDeleteDialog = client }
                            }
                            .padding(14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.common_action_delete), fontSize = DsTextSize.body, color = DsColors.Danger, fontWeight = FontWeight.Medium)
                    }
                }
            },
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }

    // The sheet offers only places the clients actually have, so no choice can empty the list. Read when it opens.
    val wilayas  by produceState(emptyList<String>(), showFilterSheet) { if (showFilterSheet) value = viewModel.places("wilaya_name") }
    val secteurs by produceState(emptyList<String>(), showFilterSheet) { if (showFilterSheet) value = viewModel.places("secteur_name") }
    val communes by produceState(emptyList<String>(), showFilterSheet, filters.wilaya) {
        value = if (showFilterSheet && filters.wilaya != null) viewModel.places("commune_name", filters.wilaya) else emptyList()
    }
    val typeNames = listOf("retail" to stringResource(R.string.common_client_type_retail), "wholesale" to stringResource(R.string.common_client_type_wholesale), "business" to stringResource(R.string.common_client_type_business))
    val filterChips: List<Pair<String, () -> Unit>> = buildList {
        filters.type?.let { t -> add((typeNames.toMap()[t] ?: t) to { viewModel.listFilters = filters.copy(type = null) }) }
        filters.balance?.let { b -> add(stringResource(b.label) to { viewModel.listFilters = filters.copy(balance = null) }) }
        filters.wilaya?.let { w -> add(stringResource(R.string.common_chip_value, stringResource(R.string.common_label_wilaya), com.distrigo.app.ui.common.wilayaShownName(w).orEmpty()) to { viewModel.listFilters = filters.copy(wilaya = null, commune = null) }) }
        filters.commune?.let { c -> add(stringResource(R.string.common_chip_value, stringResource(R.string.common_label_commune), com.distrigo.app.ui.common.communeShownName(filters.wilaya, c).orEmpty()) to { viewModel.listFilters = filters.copy(commune = null) }) }
        filters.secteur?.let { s -> add(stringResource(R.string.common_chip_value, stringResource(R.string.common_label_secteur), s) to { viewModel.listFilters = filters.copy(secteur = null) }) }
    }

    if (showFilterSheet) {
        PartyFilterSheet(
            resultCount = shownCount ?: 0,
            wilayas     = wilayas,
            wilaya      = filters.wilaya,
            // A commune belongs to one wilaya: another wilaya drops it.
            onWilaya    = { viewModel.listFilters = filters.copy(wilaya = it, commune = null) },
            communes    = communes,
            commune     = filters.commune,
            onCommune   = { viewModel.listFilters = filters.copy(commune = it) },
            balance     = filters.balance,
            onBalance   = { viewModel.listFilters = filters.copy(balance = it) },
            onReset     = { viewModel.listFilters = ClientListFilters() },
            onDismiss   = { showFilterSheet = false },
            types       = typeNames,
            type        = filters.type,
            onType      = { viewModel.listFilters = filters.copy(type = it) },
            secteurs    = secteurs,
            secteur     = filters.secteur,
            onSecteur   = { viewModel.listFilters = filters.copy(secteur = it) }
        )
    }

    if (confirmDiscard) {
        BrandDialog(
            title   = stringResource(R.string.clients_discard_title),
            text    = stringResource(R.string.clients_discard_body),
            confirm = stringResource(R.string.clients_discard) to { confirmDiscard = false; onBack?.invoke() },
            dismiss = stringResource(R.string.clients_discard_continue) to { confirmDiscard = false },
            danger  = true,
        )
    }

    val owingCount = summary?.owingCount ?: 0
    val totalDebt  = summary?.owingTotal ?: 0.0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.SurfaceMuted)
    ) {
        // Pushed from the Plus drawer over whichever tab was showing, so it takes a back
        // affordance; null-safe because the screen is still usable as a root.
        val target = picked.target
        DsTopAppBar(
            title    = stringResource(if (selecting) R.string.tournees_add_clients else R.string.common_label_clients),
            subtitle = target?.takeIf { selecting }?.let { stringResource(R.string.clients_to_quartier, it.name, it.secteurName) },
            licenseBanner = true,
            leading  = onBack?.let { back ->
                DsTopBarLeading.Back { if (selecting && picked.selected.isNotEmpty()) confirmDiscard = true else back() }
            } ?: DsTopBarLeading.None,
            size     = if (selecting) DsTopBarSize.Regular else DsTopBarSize.Large
        ) {
            if (!selecting) FloatingActionButton(
                onClick        = { onAddClient() },
                containerColor = DsColors.PrimaryDark,
                contentColor   = Color.White,
                modifier       = Modifier.size(40.dp),
                shape          = DsShapes.pill
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.common_action_add))
            }
            if (!selecting) DsTopBarOverflowMenu(stringResource(R.string.products_import_excel) to onImport)
        }

        // The header used to be the list's first item and scrolled away with it. It is a pinned
        // bar now, so the list starts below it and carries only the list.
        val clientsListState = rememberLazyListState()
        // A full screen without the bottom bar: just off the screen's edge, or off the « Ajouter » row.
        ScrollToTopBox(state = clientsListState, modifier = Modifier.weight(1f)) {
            LazyColumn(state = clientsListState, modifier = Modifier.fillMaxSize()) {
                if (totalDebt > 0 && !selecting) {
                    item {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = DsSpacing.lg)
                                    .clip(DsShapes.large)
                                    .background(DsColors.DangerLight)
                                    .padding(DsSpacing.lg),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        pluralStringResource(R.plurals.clients_debts_n, owingCount, owingCount),
                                        fontSize   = DsTextSize.caption,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = DsColors.Danger
                                    )
                                    Text(
                                        money.da(totalDebt),
                                        fontSize   = DsTextSize.headline,
                                        fontWeight = FontWeight.ExtraBold,
                                        color      = DsColors.Danger
                                    )
                                }
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint     = DsColors.Danger.copy(alpha = 0.5f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.height(DsSpacing.md))
                        }
                    }
                }

                stickyHeader {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DsColors.SurfaceMuted)
                    ) {
                        // ── Search ──
                        DsCompactSearchField(
                            value         = viewModel.listSearch,
                            onValueChange = { viewModel.listSearch = it },
                            placeholder   = stringResource(R.string.common_search_client),
                            modifier      = Modifier.padding(horizontal = DsSpacing.lg)
                        )

                        Spacer(Modifier.height(DsSpacing.sm))

                        // ── Count · Filtres ── the type and debt chips that used to sit here are in the
                        // sheet now, with the client's place beside them; what is applied shows below.
                        CountAndFiltersRow(
                            label         = (shownCount ?: 0).let { pluralStringResource(R.plurals.common_clients_count, it, it) },
                            filtersActive = filters.isActive,
                            onOpenFilters = { showFilterSheet = true }
                        )
                        RemovableFilterChips(filterChips, onClearAll = { viewModel.listFilters = ClientListFilters(withoutQuartier = filters.withoutQuartier) })
                        if (selecting) {
                            // « Sans quartier »: the clients not placed yet, the usual ones to add
                            val free = summary?.withoutQuartier ?: 0
                            Box(Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs)) {
                                BrandChips(
                                    options  = listOf(false to stringResource(R.string.common_filter_all), true to stringResource(R.string.clients_without_quartier_n, free)),
                                    selected = filters.withoutQuartier,
                                    onSelect = { viewModel.listFilters = filters.copy(withoutQuartier = it) },
                                )
                            }
                        }

                        Spacer(Modifier.height(DsSpacing.xs))
                    }
                }
                items(count = shown.itemCount, key = shown.itemKey { it.id }, contentType = { "client" }) { index ->
                    val client = shown[index]
                    Box(modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs)) {
                        if (client == null) com.distrigo.app.ui.common.PartyRowPlaceholder()
                        else if (selection != null) {
                            SelectableClientCard(client, selected = client.id in picked.selected) { selection.toggle(client.id) }
                        } else ClientCard(
                            client      = client,
                            onClick     = { onClientClick(client.id) },
                            onLongClick = { longPressClient = client }
                        )
                    }
                }
                if (shownCount == 0) {
                    // Under the search and filters, so they stay at hand; the hint only when there are no clients at all.
                    // Not while counting: the list starts empty and would flash « Aucun client ».
                    item(key = "empty", contentType = "empty") {
                        val noClients = summary?.total == 0
                        Box(Modifier.fillMaxWidth().fillParentMaxHeight(0.7f), contentAlignment = Alignment.Center) {
                            com.distrigo.app.ui.common.EmptyState(
                                illustration = R.drawable.empty_clients,
                                title        = stringResource(if (noClients) R.string.empty_clients_title else R.string.common_no_client_matches),
                                hint         = stringResource(R.string.empty_hint_clients).takeIf { noClients },
                            )
                        }
                    }
                }
            }
        }
        if (selection != null) {
            val n = picked.selected.size
            Column(
                Modifier.fillMaxWidth().background(DsColors.Surface).padding(DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                picked.error?.let { Text(it, fontSize = DsTextSize.bodySmall, color = DsColors.Danger) }
                BrandWideButton(
                    label   = if (n == 0) stringResource(R.string.tournees_select_clients) else pluralStringResource(R.plurals.clients_add_n, n, n),
                    icon    = Icons.Default.Check,
                    filled  = true,
                    enabled = n > 0 && !picked.saving && picked.target != null,
                    busy    = picked.saving,
                    onClick = { license.runOrExplain { selection.save() } },
                )
            }
        }
    }
}

/**
 * A client in « Ajouter des clients »: a box ticked by a tap, its name and phone, then where it is — or, ticked while in
 * another quartier, « Sera déplacé depuis « … » ».
 */
@Composable
private fun SelectableClientCard(client: Client, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(if (selected) DsColors.PrimaryLight else DsColors.Surface)
            .border(1.dp, if (selected) DsColors.PrimaryDark else DsColors.Border, DsShapes.large)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(checked = selected, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = DsColors.PrimaryDark))
        EntityAvatar(client.name, client.image_uri)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                client.name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                client.phone?.takeIf { it.isNotBlank() } ?: stringResource(R.string.clients_phone_not_set),
                fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, maxLines = 1
            )
            val from = client.quartier_name?.takeIf { it.isNotBlank() }
            if (selected && from != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.clients_moved_from, from), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold,
                        color = DsColors.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                val place = if (from != null) listOfNotNull(from, client.secteur_name).joinToString(" · ") else stringResource(R.string.clients_no_quartier)
                Text(place, fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClientCard(
    client      : Client,
    onClick     : () -> Unit,
    onLongClick : () -> Unit
) {
    val money = LocalMoneyFormatter.current
    val typeColors = when (client.customer_type) {
        "wholesale" -> DsColors.TagWholesale
        "business"  -> DsColors.TagBusiness
        else        -> DsColors.TagRetail
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .combinedClickable(
                onClick     = { onClick() },
                onLongClick = { onLongClick() }
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(DsShapes.medium)
                .background(typeColors.second),
            contentAlignment = Alignment.Center
        ) {
            EntityImage(
                ref                = client.image_uri,
                contentDescription = null,
                modifier           = Modifier.fillMaxSize()
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = typeColors.first, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                client.name,
                fontSize   = DsTextSize.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color      = DsColors.TextPrimary,
                maxLines   = 1
            )
            val subtitle = client.phone?.takeIf { it.isNotBlank() }
                ?: com.distrigo.app.ui.common.placeLine(client.commune_name, client.wilaya_name)

            subtitle?.let {
                Text(it, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, maxLines = 1)
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            if (Amount.owing(client.balance)) {
                Text(
                    money.amount(client.balance),
                    fontSize   = DsTextSize.body,
                    fontWeight = FontWeight.Bold,
                    color      = DsColors.Danger
                )
                Text(stringResource(R.string.clients_da_due), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            } else {
                Text(stringResource(R.string.common_status_settled_check), fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Success)
            }
        }

        Spacer(Modifier.width(8.dp))

        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(16.dp))
    }
}
