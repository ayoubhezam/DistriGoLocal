package com.distrigo.app.ui.tournees

import com.distrigo.app.ui.designsystem.LocalLicense

import com.distrigo.app.ui.common.groupBySecteur
import com.distrigo.app.ui.common.SecteurHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
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
import com.distrigo.app.ui.clients.ClientViewModel
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import androidx.compose.material.icons.filled.PersonAdd
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.GridView
import com.distrigo.app.ui.common.EntityImage
import com.distrigo.app.ui.common.DsCompactSearchField
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.distrigo.app.R

@Composable
fun AddClientsToTourneeScreen(
    tourneeId          : Int,
    tourneeNom         : String,
    existingClientIds  : Set<Int>,
    onBack              : () -> Unit,
    onSaved              : () -> Unit,
    tourneeViewModel      : TourneeViewModel = hiltViewModel(),
    clientViewModel        : ClientViewModel = hiltViewModel()
) {
    BackHandler { onBack() }

    val clients by clientViewModel.clients.collectAsState()
    var search    by remember { mutableStateOf("") }
    var selected  by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isSaving  by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf("") }
    var showAddClientScreen by remember { mutableStateOf(false) }

    // ── Add Client Sub-screen ──
    if (showAddClientScreen) {
        com.distrigo.app.ui.clients.ClientFormScreen(
            onBack  = { showAddClientScreen = false },
            onSaved = { newClientId ->
                showAddClientScreen = false
                clientViewModel.loadClientsAndUpdate(newClientId) { newClient ->
                    if (newClient != null) {
                        selected = selected + newClientId
                    }
                }
            }
        )
        return
    }

    val available = clients.filter { it.id !in existingClientIds }
    val filtered = available.filter { client ->
        val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        tokens.isEmpty() || tokens.all { token ->
            client.name.contains(token, ignoreCase = true) ||
                    (client.commune_name?.contains(token, ignoreCase = true) == true) ||
                    (client.wilaya_name?.contains(token, ignoreCase = true) == true)
        }
    }
    val groupedClients = groupBySecteur(filtered)
    // Folded by secteur, as « Choisir un client »: a search opens every section it matches, and
    // a single secteur has nothing to fold.
    var opened by remember { mutableStateOf(emptySet<String>()) }
    fun isOpen(secteur: String) = search.isNotBlank() || groupedClients.size == 1 || secteur in opened

    // A blocked write explains itself instead (docs/license_l5_enforcement.md §2)
    val license = LocalLicense.current

    fun save() {
        if (selected.isEmpty()) return
        isSaving = true
        tourneeViewModel.addClientsToTournee(
            tourneeId = tourneeId,
            clientIds = selected.toList(),
            onSuccess = { isSaving = false; onSaved() },
            onError   = { isSaving = false; errorMsg = it }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(
            title    = stringResource(R.string.tournees_add_clients),
            subtitle = tourneeNom,
            leading  = DsTopBarLeading.Back(onBack)
        ) {
            IconButton(
                onClick  = { showAddClientScreen = true },
                // A 40dp square, not a 48dp icon button, so it supplies the difference to the
                // bar's standard end margin itself.
                modifier = Modifier.padding(end = DsSpacing.md).size(40.dp).clip(DsShapes.medium).background(DsColors.PrimaryDark)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = stringResource(R.string.common_new_client), tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        DsCompactSearchField(
            value         = search,
            onValueChange = { search = it },
            placeholder   = stringResource(R.string.tournees_search_client_zone),
            modifier      = Modifier.padding(horizontal = DsSpacing.lg)
        )

        Spacer(Modifier.height(DsSpacing.sm))

        if (errorMsg.isNotEmpty()) {
            Text(errorMsg, color = DsColors.Danger, fontSize = DsTextSize.caption, modifier = Modifier.padding(horizontal = DsSpacing.lg))
            Spacer(Modifier.height(DsSpacing.xs))
        }

        LazyColumn(
            modifier            = Modifier.weight(1f),
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            groupedClients.forEach { (secteurLabel, clientsInGroup) ->
                item(key = "header_$secteurLabel") {
                    val groupIds = remember(clientsInGroup) { clientsInGroup.map { it.id }.toSet() }
                    val allSelected = groupIds.isNotEmpty() && selected.containsAll(groupIds)

                    SecteurHeader(
                        name     = secteurLabel,
                        count    = clientsInGroup.size,
                        expanded = isOpen(secteurLabel),
                        onToggle = { opened = if (secteurLabel in opened) opened - secteurLabel else opened + secteurLabel }
                    ) {
                        Text(
                            stringResource(if (allSelected) R.string.common_action_deselect_all else R.string.common_action_select_all),
                            fontSize   = DsTextSize.caption,
                            fontWeight = FontWeight.SemiBold,
                            color      = DsColors.PrimaryDark,
                            modifier   = Modifier.clickable {
                                selected = if (allSelected) selected - groupIds else selected + groupIds
                            }
                        )
                    }
                }
                if (isOpen(secteurLabel)) items(clientsInGroup, key = { it.id }) { client ->
                    val isSelected = client.id in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.large)
                            .background(DsColors.Surface)
                            .border(1.dp, DsColors.Border, DsShapes.large)
                            .clickable { selected = if (isSelected) selected - client.id else selected + client.id }
                            .padding(DsSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val initials = client.name.split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                        Box(
                            modifier         = Modifier.size(34.dp).clip(DsShapes.medium).background(DsColors.SurfaceMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!client.image_uri.isNullOrBlank()) {
                                EntityImage(
                                    ref                = client.image_uri,
                                    contentDescription = null,
                                    modifier           = Modifier.fillMaxSize()
                                ) {
                                    Text(initials, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = DsColors.TextSecondary)
                                }
                            } else {
                                Text(initials, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = DsColors.TextSecondary)
                            }
                        }
                        Spacer(Modifier.width(DsSpacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(client.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary, maxLines = 1)
                            val address = listOfNotNull(client.commune_name, client.wilaya_name).joinToString(", ")
                            Text(address.ifEmpty { "—" }, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
                        }
                        if (isSelected) {
                            Box(
                                modifier         = Modifier.size(22.dp).clip(DsShapes.small).background(DsColors.PrimaryDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick  = { license.runOrExplain { save() } },
            enabled  = selected.isNotEmpty() && !isSaving,
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    if (selected.isEmpty()) stringResource(R.string.tournees_select_clients)
                    else pluralStringResource(R.plurals.tournees_add_n_clients, selected.size, selected.size),
                    fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
