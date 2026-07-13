package com.distrigo.app.ui.clients

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Client
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

@Composable
fun ClientsScreen(
    viewModel : ClientViewModel = viewModel(),
    modifier  : Modifier = Modifier,
    onFullScreenChange : (Boolean) -> Unit = {}
) {
    val clients   by viewModel.clients.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var search           by remember { mutableStateOf("") }
    var typeFilter       by remember { mutableStateOf("all") }
    var debtOnly         by remember { mutableStateOf(false) }
    var showAddScreen    by remember { mutableStateOf(false) }
    var editingClient    by remember { mutableStateOf<Client?>(null) }
    var selectedClient   by remember { mutableStateOf<Client?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Client?>(null) }
    var longPressClient  by remember { mutableStateOf<Client?>(null) }

    if (showAddScreen) {
        onFullScreenChange(true)
        BackHandler { showAddScreen = false; onFullScreenChange(false) }
        ClientFormScreen(
            onBack  = { showAddScreen = false; onFullScreenChange(false) },
            onSaved = { _ -> showAddScreen = false; onFullScreenChange(false); viewModel.loadClients() }
        )
        return
    }

    editingClient?.let { c ->
        onFullScreenChange(true)
        BackHandler { editingClient = null; onFullScreenChange(false) }
        ClientFormScreen(
            client  = c,
            onBack  = { editingClient = null; onFullScreenChange(false) },
            onSaved = { _ -> editingClient = null; onFullScreenChange(false); viewModel.loadClients() }
        )
        return
    }
    selectedClient?.let { c ->
        BackHandler { selectedClient = null }
        ClientDetailScreen(
            client   = c,
            onBack   = { selectedClient = null },
            onEdit   = { editingClient = c; selectedClient = null },
            onDelete = { showDeleteDialog = c; selectedClient = null }
        )
        return
    }

    // ── Delete confirmation ──
    showDeleteDialog?.let { client ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Supprimer le client") },
            text  = { Text("Voulez-vous supprimer \"${client.name}\" ?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteClient(
                        id        = client.id,
                        onSuccess = { showDeleteDialog = null },
                        onError   = { showDeleteDialog = null }
                    )
                }) { Text("Supprimer", color = DsColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Annuler") }
            }
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
                                editingClient   = client
                                longPressClient = null
                            }
                            .padding(14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                        Text("Modifier", fontSize = DsTextSize.body, color = DsColors.Primary, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.medium)
                            .background(DsColors.DangerLight)
                            .clickable {
                                showDeleteDialog = client
                                longPressClient  = null
                            }
                            .padding(14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(20.dp))
                        Text("Supprimer", fontSize = DsTextSize.body, color = DsColors.Danger, fontWeight = FontWeight.Medium)
                    }
                }
            }
        )
    }

    val filtered = clients.filter { c ->
        val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val matchesSearch = tokens.isEmpty() || tokens.all { token ->
            c.name.contains(token, ignoreCase = true) || (c.phone?.contains(token, ignoreCase = true) == true)
        }
        matchesSearch &&
                (typeFilter == "all" || c.customer_type == typeFilter) &&
                (!debtOnly || c.balance > 0)
    }

    val debtClients = clients.filter { it.balance > 0 }
    val totalDebt   = debtClients.sumOf { it.balance }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        // ── Header ──
        Row(
            modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Clients", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.TextPrimary)
            FloatingActionButton(
                onClick        = { showAddScreen = true },
                containerColor = DsColors.Primary,
                contentColor   = Color.White,
                modifier       = Modifier.size(40.dp),
                shape          = DsShapes.pill
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter")
            }
        }

        // ── Debt banner ──
        if (totalDebt > 0) {
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
                        "Dettes en cours · ${debtClients.size} client(s)",
                        fontSize   = DsTextSize.caption,
                        fontWeight = FontWeight.SemiBold,
                        color      = DsColors.Danger
                    )
                    Text(
                        "${"%.2f".format(totalDebt)} DA",
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

        // ── Search ──
        OutlinedTextField(
            value         = search,
            onValueChange = { search = it },
            placeholder   = { Text("Rechercher par nom ou téléphone…", fontSize = DsTextSize.body) },
            leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon  = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { search = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Effacer", tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            },
            modifier      = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
            shape         = DsShapes.large,
            singleLine    = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = DsColors.Border,
                focusedBorderColor   = DsColors.Primary
            )
        )

        Spacer(Modifier.height(DsSpacing.sm))

        // ── Filter chips ──
        LazyRow(
            contentPadding        = PaddingValues(horizontal = DsSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            item {
                DsFilterChip(
                    label        = "Avec dettes",
                    active       = debtOnly,
                    activeBg     = DsColors.DangerLight,
                    activeBorder = DsColors.Danger,
                    activeText   = DsColors.Danger,
                    onClick      = { debtOnly = !debtOnly }
                )
            }
            item {
                DsFilterChip(
                    label        = "Tous",
                    active       = typeFilter == "all",
                    activeBg     = DsColors.PrimaryLight,
                    activeBorder = DsColors.Primary,
                    activeText   = DsColors.Primary,
                    onClick      = { typeFilter = "all" }
                )
            }
            item {
                DsFilterChip(
                    label        = "Détail",
                    active       = typeFilter == "retail",
                    activeBg     = DsColors.TagRetail.second,
                    activeBorder = DsColors.TagRetail.first,
                    activeText   = DsColors.TagRetail.first,
                    onClick      = { typeFilter = "retail" }
                )
            }
            item {
                DsFilterChip(
                    label        = "Gros",
                    active       = typeFilter == "wholesale",
                    activeBg     = DsColors.TagWholesale.second,
                    activeBorder = DsColors.TagWholesale.first,
                    activeText   = DsColors.TagWholesale.first,
                    onClick      = { typeFilter = "wholesale" }
                )
            }
            item {
                DsFilterChip(
                    label        = "Société",
                    active       = typeFilter == "business",
                    activeBg     = DsColors.TagBusiness.second,
                    activeBorder = DsColors.TagBusiness.first,
                    activeText   = DsColors.TagBusiness.first,
                    onClick      = { typeFilter = "business" }
                )
            }
        }

        Spacer(Modifier.height(DsSpacing.sm))

        Text(
            "${filtered.size} client(s)",
            fontSize = DsTextSize.caption,
            color    = DsColors.TextSecondary,
            modifier = Modifier.padding(horizontal = DsSpacing.lg)
        )

        Spacer(Modifier.height(DsSpacing.sm))

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
            }
            filtered.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PersonOutline,
                            contentDescription = null,
                            tint     = DsColors.TextTertiary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text("Aucun client trouvé", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                ) {
                    items(filtered) { client ->
                        ClientCard(
                            client      = client,
                            onClick     = { selectedClient = client },
                            onLongClick = { longPressClient = client }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DsFilterChip(
    label        : String,
    active       : Boolean,
    activeBg     : Color,
    activeBorder : Color,
    activeText   : Color,
    onClick      : () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(DsShapes.pill)
            .background(if (active) activeBg else DsColors.Surface)
            .border(1.dp, if (active) activeBorder else DsColors.Border, DsShapes.pill)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            fontSize   = DsTextSize.bodySmall,
            color      = if (active) activeText else DsColors.TextSecondary,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClientCard(
    client      : Client,
    onClick     : () -> Unit,
    onLongClick : () -> Unit
) {
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
            if (client.image_uri != null) {
                val imageBytes = Base64.decode(client.image_uri.substringAfter("base64,"), Base64.NO_WRAP)
                val bitmap     = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                bitmap?.let {
                    Image(
                        bitmap             = it.asImageBitmap(),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop
                    )
                }
            } else {
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
            if (client.balance > 0) {
                Text(
                    "%.2f".format(client.balance),
                    fontSize   = DsTextSize.body,
                    fontWeight = FontWeight.Bold,
                    color      = DsColors.Danger
                )
                Text("DA dû", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            } else {
                Text("✓ Soldé", fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Success)
            }
        }

        Spacer(Modifier.width(8.dp))

        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(16.dp))
    }
}
