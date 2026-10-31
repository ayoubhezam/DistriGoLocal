package com.distrigo.app.ui.clients

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
    val picked by (selection?.state ?: noSelection).collectAsState()
    var confirmDiscard by remember { mutableStateOf(false) }
    if (selection != null) {
        LaunchedEffect(selection) {
            selection.events.collect { event -> if (event is ClientSelectionEvent.Saved) onSelectionSaved(event) }
        }
        // A selection is work: leaving with one asks first, as the forms do
        BackHandler { if (picked.selected.isNotEmpty()) confirmDiscard = true else onBack?.invoke() }
    }
    val money = LocalMoneyFormatter.current
    val clients   by viewModel.clients.collectAsState()
    // A blocked write explains itself instead (docs/license_l5_enforcement.md §2)
    val license = LocalLicense.current
    val isLoading by viewModel.isLoading.collectAsState()

    // The list as the search and the filters narrow it, worked out off the main thread once typing
    // pauses (see ClientViewModel.shownClients). Only the search box reads what is being typed.
    val shown by viewModel.shownClients.collectAsState()
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

    // The sheet offers only places the clients actually have, so no choice can empty the list.
    val wilayas  = remember(clients) { placesOf(clients.map { it.wilaya_name }) }
    val secteurs = remember(clients) { placesOf(clients.map { it.secteur_name }) }
    val communes = remember(clients, filters.wilaya) {
        placesOf(clients.filter { it.wilaya_name?.trim().equals(filters.wilaya, ignoreCase = true) }.map { it.commune_name })
    }
    val typeNames = listOf("retail" to stringResource(R.string.common_client_type_retail), "wholesale" to stringResource(R.string.common_client_type_wholesale), "business" to stringResource(R.string.common_client_type_business))
    val filterChips: List<Pair<String, () -> Unit>> = buildList {
        filters.type?.let { t -> add((typeNames.toMap()[t] ?: t) to { viewModel.listFilters = filters.copy(type = null) }) }
        filters.balance?.let { b -> add(stringResource(b.label) to { viewModel.listFilters = filters.copy(balance = null) }) }
        filters.wilaya?.let { w -> add(stringResource(R.string.common_chip_value, stringResource(R.string.common_label_wilaya), w) to { viewModel.listFilters = filters.copy(wilaya = null, commune = null) }) }
        filters.commune?.let { c -> add(stringResource(R.string.common_chip_value, stringResource(R.string.common_label_commune), c) to { viewModel.listFilters = filters.copy(commune = null) }) }
        filters.secteur?.let { s -> add(stringResource(R.string.common_chip_value, stringResource(R.string.common_label_secteur), s) to { viewModel.listFilters = filters.copy(secteur = null) }) }
    }

    if (showFilterSheet) {
        PartyFilterSheet(
            resultCount = shown.size,
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

    val debtClients = remember(clients) { clientsInDebt(clients) }
    val totalDebt   = remember(debtClients) { debtClients.sumOf { it.balance } }

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
        LazyColumn(modifier = Modifier.weight(1f)) {
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
                                    pluralStringResource(R.plurals.clients_debts_n, debtClients.size, debtClients.size),
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
                        label         = pluralStringResource(R.plurals.common_clients_count, shown.size, shown.size),
                        filtersActive = filters.isActive,
                        onOpenFilters = { showFilterSheet = true }
                    )
                    RemovableFilterChips(filterChips, onClearAll = { viewModel.listFilters = ClientListFilters(withoutQuartier = filters.withoutQuartier) })
                    if (selecting) {
                        // « Sans quartier »: the clients not placed yet, the usual ones to add
                        val free = remember(clients) { clients.count { it.quartier_id == null } }
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
            items(shown, key = { it.id }) { client ->
                Box(modifier = Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs)) {
                    if (selection != null) {
                        SelectableClientCard(client, selected = client.id in picked.selected) { selection.toggle(client.id) }
                    } else ClientCard(
                        client      = client,
                        onClick     = { onClientClick(client.id) },
                        onLongClick = { longPressClient = client }
                    )
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
                ?: listOfNotNull(
                    client.commune_name?.takeIf { it.isNotBlank() },
                    client.wilaya_name?.takeIf { it.isNotBlank() }
                ).joinToString(", ").ifEmpty { null }

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
