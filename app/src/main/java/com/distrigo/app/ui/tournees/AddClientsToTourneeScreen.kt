package com.distrigo.app.ui.tournees

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.distrigo.app.ui.clients.ClientViewModel
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.compose.material.icons.filled.PersonAdd
import androidx.activity.compose.BackHandler

@Composable
fun AddClientsToTourneeScreen(
    tourneeId          : Int,
    tourneeNom         : String,
    existingClientIds  : Set<Int>,
    onBack              : () -> Unit,
    onSaved              : () -> Unit,
    tourneeViewModel      : TourneeViewModel = viewModel(),
    clientViewModel        : ClientViewModel = viewModel()
) {
    BackHandler { onBack() }

    val clients by clientViewModel.clients.collectAsState()
    var search    by remember { mutableStateOf("") }
    var selected  by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isSaving  by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf("") }
    var showAddClientScreen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { clientViewModel.loadClients() }

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

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.xs))
            Column(modifier = Modifier.weight(1f)) {
                Text("Ajouter des clients", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text(tourneeNom, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
            IconButton(
                onClick  = { showAddClientScreen = true },
                modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Nouveau client", tint = DsColors.Primary, modifier = Modifier.size(18.dp))
            }
        }

        OutlinedTextField(
            value         = search,
            onValueChange = { search = it },
            placeholder   = { Text("Rechercher un client ou une zone", fontSize = DsTextSize.body) },
            leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier      = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
            shape         = DsShapes.large,
            singleLine    = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = DsColors.Border,
                focusedBorderColor   = DsColors.Primary
            )
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
            items(filtered, key = { it.id }) { client ->
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
                        Text(initials, fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold, color = DsColors.TextSecondary)
                    }
                    Spacer(Modifier.width(DsSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(client.name, fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary, maxLines = 1)
                        val address = listOfNotNull(client.commune_name, client.wilaya_name).joinToString(", ")
                        Text(address.ifEmpty { "—" }, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
                    }
                    if (isSelected) {
                        Box(
                            modifier         = Modifier.size(22.dp).clip(DsShapes.small).background(DsColors.Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        Button(
            onClick  = { save() },
            enabled  = selected.isNotEmpty() && !isSaving,
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    if (selected.isEmpty()) "Sélectionnez des clients"
                    else "Ajouter ${selected.size} client(s) à la tournée",
                    fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
