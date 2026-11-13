package com.distrigo.app.ui.common

import com.distrigo.app.core.format.westernDigits
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.distrigo.app.data.local.paging.SecteurCount
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.distrigo.app.R
import com.distrigo.app.data.model.Client
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.format.LocalMoneyFormatter
import com.distrigo.app.ui.designsystem.DsTopBarAddButton
import com.distrigo.app.data.model.Amount

/**
 * Shared "pick a client via search" screen body: header + search field + the clients by secteur, used by the
 * client-picker route of VenteFormNavGraph and TourneeVenteFormNavGraph.
 *
 * Searched, counted and paged in SQL by [picker] (PERF-01): the folds come from a count per secteur, and an open
 * fold's rows from the pages, so the picker no longer holds every client.
 */
@Composable
fun ClientSearchPicker(
    picker           : ClientPickerList,
    selectedClient   : Client? = null,
    onClientSelected : (Client) -> Unit,
    onBack           : () -> Unit,
    onAddNewClient   : (() -> Unit)? = null,
    showBalance      : Boolean = true
) {
    val groups = picker.groups.collectAsStateWithLifecycle().value.orEmpty()
    val items  = picker.items.collectAsLazyPagingItems()
    // The picker outlives this screen (its pages are cached in the form's ViewModel): each visit starts unsearched
    androidx.compose.runtime.DisposableEffect(picker) { onDispose { picker.search = "" } }

    // One section per secteur, « Sans secteur » last, as in « Ajouter des clients ». Closed by
    // default so the list is a short index of secteurs; the selected client's secteur opens.
    var opened by remember {
        mutableStateOf(setOfNotNull(selectedClient?.let(::secteurOf)))
    }
    // A search shows its matches at once, and a single secteur has nothing to fold.
    fun isOpen(group: SecteurCount) = picker.search.isNotBlank() || groups.size == 1 || group.key() in opened

    Column(modifier = Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title   = stringResource(R.string.client_picker_title),
            leading = DsTopBarLeading.Back(onBack)
        ) {
            if (onAddNewClient != null) {
                DsTopBarAddButton(
                    contentDescription = stringResource(R.string.client_picker_add_new),
                    onClick            = onAddNewClient,
                    modifier           = Modifier.padding(end = DsSpacing.md)
                )
            }
        }

        DsCompactSearchField(
            value         = picker.search,
            onValueChange = { picker.search = it },
            placeholder   = stringResource(R.string.client_picker_search_placeholder),
            modifier      = Modifier.padding(horizontal = DsSpacing.lg)
        )

        Spacer(Modifier.height(DsSpacing.sm))

        val listState = rememberLazyListState()
        ScrollToTopBox(state = listState, modifier = Modifier.weight(1f)) {
            LazyColumn(
                state               = listState,
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                secteurFolds(
                    groups      = groups,
                    items       = items,
                    isOpen      = ::isOpen,
                    showHeaders = groups.size > 1,
                    header      = { group ->
                        val key = group.key()
                        SecteurHeader(
                            name     = key,
                            count    = group.count,
                            expanded = isOpen(group),
                            onToggle = { opened = if (key in opened) opened - key else opened + key }
                        )
                    },
                    row         = { client ->
                        ClientPickerRow(client, selectedClient?.id == client.id, showBalance) { onClientSelected(client) }
                    },
                )
            }
        }

    }
}

@Composable
private fun ClientPickerRow(client: Client, isSelected: Boolean, showBalance: Boolean, onClick: () -> Unit) {
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
            .border(1.dp, if (isSelected) DsColors.PrimaryDark else DsColors.Border, DsShapes.large)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val initials = client.name.split(" ").take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
        Box(
            modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(typeColors.second),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = typeColors.first)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(client.name, fontWeight = FontWeight.SemiBold, fontSize = DsTextSize.bodyLarge, color = DsColors.TextPrimary, maxLines = 1)
            Text(client.phone?.let(::westernDigits) ?: "—", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
        if (showBalance) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (Amount.owing(client.balance)) {
                    Text(money.da(client.balance), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.Danger)
                } else {
                    Text(stringResource(R.string.common_status_settled_check), fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Success)
                }
            }
        }
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Check, contentDescription = null, tint = DsColors.PrimaryDark, modifier = Modifier.size(16.dp))
        }
    }
}
