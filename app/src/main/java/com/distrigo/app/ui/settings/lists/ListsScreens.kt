package com.distrigo.app.ui.settings.lists

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.distrigo.app.ui.common.shownName
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.geo.GeoRepository
import com.distrigo.app.data.lists.ListEntry
import com.distrigo.app.data.lists.ListKind
import com.distrigo.app.data.lists.ListsRepository
import com.distrigo.app.ui.common.DsCompactSearchField
import com.distrigo.app.ui.common.SearchableSelectSheet
import com.distrigo.app.ui.common.matchesAllTokens
import com.distrigo.app.ui.common.mirrorInRtl
import com.distrigo.app.ui.common.searchTokens
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarAddButton
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.LocalLicense
import com.distrigo.app.ui.settings.SettingsDivider
import com.distrigo.app.ui.settings.SettingsRow
import com.distrigo.app.ui.settings.SettingsSection
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.res.stringResource
import com.distrigo.app.data.lists.ListWords
import com.distrigo.app.ui.common.BuiltInNames
import com.distrigo.app.R

/** An entry's name as shown: a built-in charge or perte type still under its seeded name, in the app's language. */
private fun shownName(kind: ListKind, entry: ListEntry): String = when (kind) {
    ListKind.CHARGE_TYPES, ListKind.CHARGE_SUBTYPES -> BuiltInNames.chargeType(entry.name)
    ListKind.PERTE_TYPES -> BuiltInNames.perteType(entry.name)
    else -> entry.name
}

/** « 1 catégorie », « 12 catégories », in the app's language. */
private fun count(kind: ListKind, n: Int): String = ListWords.count(ListWords.countRes(kind), n)

// ── « Listes et catégories » ──

/**
 * « Listes et catégories » (owner, 2026-10-21): every list the forms add to, grouped by where it is used, each with
 * how many entries it has. A list opens its screen, where its entries are renamed and deleted.
 */
@Composable
fun ListsHomeScreen(
    onBack: () -> Unit,
    onOpen: (ListKind) -> Unit,
    viewModel: ListsHomeViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    fun n(kind: ListKind) = counts?.get(kind) ?: 0
    fun of(kind: ListKind) = counts?.let { count(kind, n(kind)) }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = stringResource(R.string.settings_lists), leading = DsTopBarLeading.Back(onBack))
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.xl),
        ) {
            Text(
                stringResource(R.string.lists_intro),
                fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary,
            )
            SettingsSection(stringResource(R.string.lists_label_products)) {
                SettingsRow(
                    Icons.Outlined.Category, DsColors.AccentViolet, stringResource(R.string.lists_label_categories),
                    listOfNotNull(of(ListKind.CATEGORIES), of(ListKind.SOUS_CATEGORIES)).joinToString(" · "),
                    onClick = { onOpen(ListKind.CATEGORIES) },
                )
                SettingsDivider()
                SettingsRow(Icons.Outlined.Sell, DsColors.Primary, stringResource(R.string.lists_label_brands), of(ListKind.MARQUES), onClick = { onOpen(ListKind.MARQUES) })
            }
            SettingsSection(stringResource(R.string.lists_section_charges)) {
                SettingsRow(
                    Icons.AutoMirrored.Outlined.ReceiptLong, DsColors.Warning, stringResource(R.string.lists_label_charge_types),
                    listOfNotNull(of(ListKind.CHARGE_TYPES), of(ListKind.CHARGE_SUBTYPES)).joinToString(" · "),
                    onClick = { onOpen(ListKind.CHARGE_TYPES) },
                )
            }
            SettingsSection(stringResource(R.string.lists_section_pertes)) {
                SettingsRow(
                    Icons.Outlined.RemoveShoppingCart, DsColors.Danger, stringResource(R.string.lists_label_perte_types), of(ListKind.PERTE_TYPES),
                    onClick = { onOpen(ListKind.PERTE_TYPES) },
                )
            }
            SettingsSection(stringResource(R.string.lists_label_clients)) {
                SettingsRow(Icons.Outlined.Place, DsColors.Success, stringResource(R.string.lists_label_secteurs), of(ListKind.SECTEURS), onClick = { onOpen(ListKind.SECTEURS) })
            }
        }
    }
}

// ── One list ──

/**
 * One list: its entries by name, each with what uses it; « + » adds one. An entry opens its actions — the entries
 * under it, « Renommer », « Supprimer » (or why it cannot be). Every write is behind the license.
 */
@Composable
fun ListEditorScreen(
    onBack: () -> Unit,
    /** A category's sous-catégories, a type's sous-types. */
    onOpenChildren: (ListKind, ListEntry) -> Unit,
    viewModel: ListEditorViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val license = LocalLicense.current
    val kind = viewModel.kind

    var acting by remember { mutableStateOf<ListEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ListEntry?>(null) }
    var deleting by remember { mutableStateOf<ListEntry?>(null) }

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title    = stringResource(ListWords.labelRes(kind)),
            subtitle = viewModel.parentName,
            leading  = DsTopBarLeading.Back(onBack),
        ) {
            DsTopBarAddButton(contentDescription = stringResource(ListWords.addRes(kind)), onClick = { license.runOrExplain { adding = true } })
        }

        val entries = state.entries
        when {
            entries == null -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.PrimaryDark)
            }
            entries.isEmpty() -> EmptyList(kind)
            else -> {
                val tokens = searchTokens(state.query)
                // As stored and as shown: a built-in type reads in the app's language
                val shown = entries.filter { matchesAllTokens(tokens, it.name, shownName(kind, it), it.detail) }
                // A search only once a list is long enough to need one
                if (entries.size > SEARCH_FROM) {
                    DsCompactSearchField(
                        value = state.query, onValueChange = viewModel::search, placeholder = stringResource(R.string.lists_search),
                        modifier = Modifier.padding(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.md),
                    )
                }
                LazyColumn(
                    contentPadding      = PaddingValues(DsSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                ) {
                    items(shown, key = { it.id }) { entry -> EntryRow(kind, entry) { acting = entry } }
                    if (shown.isEmpty()) item(key = "none") {
                        Text(stringResource(R.string.lists_no_result), fontSize = DsTextSize.body, color = DsColors.TextSecondary, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg))
                    }
                }
            }
        }
    }

    acting?.let { entry ->
        EntryActions(
            kind     = kind,
            entry    = entry,
            onChildren = kind.children?.let { child -> { acting = null; onOpenChildren(child, entry) } },
            onRename = { acting = null; license.runOrExplain { renaming = entry } },
            onDelete = { acting = null; license.runOrExplain { deleting = entry } },
            onDismiss = { acting = null },
        )
    }
    if (adding) {
        NameDialog(
            title   = stringResource(ListWords.newRes(kind)),
            initial = "",
            confirm = stringResource(R.string.common_action_add),
            kind    = kind,
            asks    = true,
            onSave  = { name, commune, wilaya, hasFournisseur, done -> viewModel.add(name, commune, wilaya, hasFournisseur, done) },
            onDismiss = { adding = false },
        )
    }
    renaming?.let { entry ->
        NameDialog(
            title   = stringResource(R.string.settings_rename),
            initial = entry.name,
            confirm = stringResource(R.string.common_action_save),
            kind    = kind,
            asks    = false,
            note    = renameNote(kind, entry),
            onSave  = { name, _, _, _, done -> viewModel.rename(entry.id, name, done) },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { entry ->
        DeleteDialog(kind, entry, onDelete = { done -> viewModel.delete(entry.id, done) }, onDismiss = { deleting = null })
    }
}

/** Past this many entries, the list gets a search field. */
private const val SEARCH_FROM = 8

@Composable
private fun EmptyList(kind: ListKind) {
    Column(Modifier.fillMaxWidth().padding(DsSpacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(ListWords.emptyRes(kind)), fontSize = DsTextSize.body,
            fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark, textAlign = TextAlign.Center)
        Spacer(Modifier.height(DsSpacing.xs))
        Text(stringResource(R.string.lists_add_with_plus), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, textAlign = TextAlign.Center)
    }
}

/** What uses an entry, and what is under it: « 12 produits · 3 sous-catégories », or « Aucune utilisation ». */
private fun uses(kind: ListKind, entry: ListEntry): String {
    val parts = buildList {
        entry.detail?.let { add(it) }
        if (entry.usages.isEmpty()) add(ListWords.text(R.string.lists_no_use)) else add(entry.usages.joinToString(" · "))
        kind.children?.let { child -> if (entry.children > 0) add(count(child, entry.children)) }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun EntryRow(kind: ListKind, entry: ListEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(DsShapes.large).background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .clickable(role = Role.Button, onClickLabel = shownName(kind, entry), onClick = onClick)
            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(shownName(kind, entry), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(uses(kind, entry), fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (entry.isDefault) {
            Spacer(Modifier.width(DsSpacing.sm))
            BuiltInPill()
        }
        Spacer(Modifier.width(DsSpacing.xs))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(20.dp))
    }
}

/** A type the app comes with: renamable, never deleted. */
@Composable
private fun BuiltInPill() {
    Row(
        Modifier.clip(DsShapes.pill).background(DsColors.SurfaceSunken).padding(horizontal = DsSpacing.sm, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.lists_default), fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.TextSecondary)
    }
}

/** An entry's actions, as a sheet: the entries under it, « Renommer », « Supprimer » or why it cannot be. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryActions(
    kind: ListKind,
    entry: ListEntry,
    onChildren: (() -> Unit)?,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = DsColors.Surface,
    ) {
        Column(Modifier.padding(bottom = DsSpacing.xxxl)) {
            Column(Modifier.padding(horizontal = DsSpacing.lg)) {
                Text(shownName(kind, entry), fontSize = DsTextSize.headline, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(uses(kind, entry), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
            }
            Spacer(Modifier.height(DsSpacing.md))
            val child = kind.children
            if (onChildren != null && child != null) {
                ActionRow(Icons.Outlined.SubdirectoryArrowRight, DsColors.Primary, stringResource(ListWords.labelRes(child)), count(child, entry.children), mirrored = true, onClick = onChildren)
            }
            ActionRow(Icons.Outlined.Edit, DsColors.Primary, stringResource(R.string.settings_rename), stringResource(if (kind.feminine) R.string.lists_rename_sub_f else R.string.lists_rename_sub_m), onClick = onRename)
            val blocked = when {
                entry.isDefault -> stringResource(R.string.lists_built_in_row)
                entry.usages.isNotEmpty() -> stringResource(if (kind.feminine) R.string.lists_used_by_f else R.string.lists_used_by_m, ListWords.and(entry.usages.map { it.toString() }))
                else -> null
            }
            ActionRow(
                Icons.Outlined.DeleteOutline, DsColors.Danger, stringResource(R.string.common_action_delete),
                blocked ?: if (kind.softDeleted) stringResource(if (kind.feminine) R.string.lists_restore_later_f else R.string.lists_restore_later_m)
                else stringResource(R.string.lists_for_good),
                enabled = blocked == null, danger = true, onClick = onDelete,
            )
            Spacer(Modifier.height(DsSpacing.md))
            OutlinedButton(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg).heightIn(min = 50.dp),
                shape    = DsShapes.large,
                border   = BorderStroke(1.5.dp, DsColors.Border),
            ) {
                Text(stringResource(R.string.common_action_cancel), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    /** The icon points along the reading direction and has no AutoMirrored version. */
    mirrored: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md),
    ) {
        val shade = if (enabled) tint else DsColors.TextTertiary
        Box(Modifier.size(40.dp).clip(DsShapes.medium).background(shade.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = shade, modifier = Modifier.size(20.dp).then(if (mirrored) Modifier.mirrorInRtl() else Modifier))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold,
                color = when { !enabled -> DsColors.TextTertiary; danger -> DsColors.Danger; else -> DsColors.TextPrimary })
            Text(subtitle, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
        if (enabled) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(20.dp))
    }
}

/** What a rename also changes, said before it is saved. */
private fun renameNote(kind: ListKind, entry: ListEntry): String? {
    val n = entry.usages.sumOf { it.count }
    if (n == 0L) return null
    return ListWords.text(R.string.lists_rename_note, ListWords.and(entry.usages.map { it.toString() }))
}

/**
 * The name of an entry, to add or rename it. A new sous-type also asks whether it needs a supplier; a new secteur,
 * its wilaya and commune. [onSave] answers with null when done, or why not, shown under the field.
 */
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    kind: ListKind,
    /** Adding, so the sous-type's and the secteur's own fields are asked too. */
    asks: Boolean,
    note: String? = null,
    onSave: (name: String, commune: String?, wilaya: String?, hasFournisseur: Boolean, done: (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var hasFournisseur by remember { mutableStateOf(false) }
    var wilaya by remember { mutableStateOf<com.distrigo.app.data.model.Wilaya?>(null) }
    var commune by remember { mutableStateOf<String?>(null) }
    var choosingWilaya by remember { mutableStateOf(false) }
    var choosingCommune by remember { mutableStateOf(false) }
    val needsPlace = asks && kind == ListKind.SECTEURS
    val ready = ListsRepository.clean(name).isNotEmpty() && (!needsPlace || commune != null) && !saving

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.lists_name)) }, singleLine = true, isError = error != null,
                    supportingText = error?.let { { Text(it, color = DsColors.Danger) } },
                    shape = DsShapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (asks && kind == ListKind.CHARGE_SUBTYPES) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.charges_needs_supplier), fontSize = DsTextSize.body, color = DsColors.TextPrimary, modifier = Modifier.weight(1f))
                        Switch(checked = hasFournisseur, onCheckedChange = { hasFournisseur = it })
                    }
                }
                if (needsPlace) {
                    PlaceField(stringResource(R.string.lists_wilaya), wilaya?.let { "${it.wilayaCode}-${it.shownName()}" }, stringResource(R.string.lists_cd_choose_wilaya)) { choosingWilaya = true }
                    PlaceField(stringResource(R.string.lists_commune), com.distrigo.app.ui.common.communeShownName(wilaya?.nameFr, commune),
                        stringResource(R.string.lists_cd_choose_commune), enabled = wilaya != null) { choosingCommune = true }
                }
                note?.let { Text(it, fontSize = DsTextSize.caption, color = DsColors.TextSecondary) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    saving = true
                    onSave(name, commune, wilaya?.nameFr, hasFournisseur) { reason ->
                        saving = false
                        if (reason == null) onDismiss() else error = reason
                    }
                },
            ) { Text(confirm, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.common_action_cancel)) } },
        containerColor    = DsColors.Surface,
        titleContentColor = DsColors.TextPrimary,
        textContentColor  = DsColors.TextSecondary,
    )

    if (choosingWilaya) {
        SearchableSelectSheet(
            title     = stringResource(R.string.common_select_wilaya),
            items     = GeoRepository.getWilayas(),
            itemLabel = { "${it.wilayaCode}-${it.shownName()}" },
            onDismiss = { choosingWilaya = false },
            onSelect  = { wilaya = it; commune = null },
        )
    }
    val chosen = wilaya
    if (choosingCommune && chosen != null) {
        val communes = remember(chosen) { GeoRepository.getCommunes(chosen.wilayaCode).sortedBy { it.id } }
        SearchableSelectSheet(
            title     = stringResource(R.string.common_select_commune),
            items     = communes,
            itemLabel = { it.shownName() },
            onDismiss = { choosingCommune = false },
            onSelect  = { commune = it.nameFr },
        )
    }
}

/** A field that opens a picker: its label, and what was picked or « Choisir ». */
@Composable
private fun PlaceField(label: String, value: String?, chooseLabel: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(DsShapes.medium).border(1.dp, DsColors.Border, DsShapes.medium)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = chooseLabel, onClick = onClick)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
    ) {
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        Text(value ?: stringResource(R.string.pertes_choose), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold,
            color = when { value != null -> DsColors.TextPrimary; enabled -> DsColors.Primary; else -> DsColors.TextTertiary })
    }
}

/** « Supprimer « X » ? », saying where it goes and what goes with it. */
@Composable
private fun DeleteDialog(kind: ListKind, entry: ListEntry, onDelete: (done: (String?) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val body = buildString {
        append(ListWords.text(if (!kind.softDeleted) R.string.lists_secteur_deleted_for_good else if (kind.feminine) R.string.lists_delete_to_trash_f else R.string.lists_delete_to_trash_m))
        kind.children?.let { child ->
            if (entry.children > 0) append(" " + ListWords.count(if (child == ListKind.SOUS_CATEGORIES) R.plurals.lists_children_also_subcategories else R.plurals.lists_children_also_subtypes, entry.children))
        }
    }
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(stringResource(R.string.lists_delete_title, shownName(kind, entry)), fontWeight = FontWeight.Bold) },
        text  = {
            Column {
                Text(body)
                error?.let {
                    Spacer(Modifier.height(DsSpacing.sm))
                    Text(it, color = DsColors.Danger, fontSize = DsTextSize.caption)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !deleting, onClick = {
                deleting = true
                onDelete { reason -> deleting = false; if (reason == null) onDismiss() else error = reason }
            }) { Text(stringResource(R.string.common_action_delete), color = DsColors.Danger, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(enabled = !deleting, onClick = onDismiss) { Text(stringResource(R.string.common_action_cancel)) } },
        containerColor    = DsColors.Surface,
        titleContentColor = DsColors.TextPrimary,
        textContentColor  = DsColors.TextSecondary,
    )
}
