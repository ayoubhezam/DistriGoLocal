package com.distrigo.app.ui.chargements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Chargement
import com.distrigo.app.data.model.ChargementSession
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.products.ProductViewModel
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ChargementSessionsScreen(
    viewModel: ChargementViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onFullScreenChange : (Boolean) -> Unit = {}
) {
    val sessions  by viewModel.sessions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error     by viewModel.error.collectAsState()
    val productViewModel: ProductViewModel = viewModel()

    LaunchedEffect(Unit) { viewModel.loadSessions() }

    var selectedSession      by remember { mutableStateOf<ChargementSession?>(null) }
    var showNewChargement    by remember { mutableStateOf(false) }
    var selectedChargement   by remember { mutableStateOf<Chargement?>(null) }
    var longPressChargement  by remember { mutableStateOf<Chargement?>(null) }
    var correctingChargement by remember { mutableStateOf<Chargement?>(null) }
    var showNoteDialog       by remember { mutableStateOf(false) }
    var editingNote          by remember { mutableStateOf("") }

    // ── New Chargement Screen ──
    if (showNewChargement) {
        onFullScreenChange(true)
        ChargementFormScreen(
            onBack  = { showNewChargement = false; onFullScreenChange(false) },
            onSaved = {
                showNewChargement = false
                onFullScreenChange(false)
                viewModel.loadSessions()
            }
        )
        return
    }

    // ── Correction Screen ──
    correctingChargement?.let { original ->
        onFullScreenChange(true)
        ChargementFormScreen(
            correctionSource = original,
            onBack  = { correctingChargement = null; onFullScreenChange(false) },
            onSaved = {
                correctingChargement = null
                onFullScreenChange(false)
                viewModel.loadSessions()
            }
        )
        return
    }

    // ── Long Press Dialog ──
    longPressChargement?.let { chargement ->
        AlertDialog(
            onDismissRequest = { longPressChargement = null },
            title = { Text("Mouvement #${chargement.id}") },
            confirmButton = {},
            dismissButton = {},
            shape = DsShapes.medium,
            containerColor = DsColors.Surface,
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.medium)
                        .background(DsColors.PrimaryLight)
                        .clickable {
                            correctingChargement = chargement
                            longPressChargement  = null
                        }
                        .padding(DsSpacing.md),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                    Text("Corriger", fontSize = DsTextSize.body, color = DsColors.Primary, fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    // ── Chargement Detail Screen ──
    selectedChargement?.let { chargement ->
        BackHandler { selectedChargement = null }

        val versCamionCount = chargement.items?.count { it.direction == "vers_camion" } ?: 0
        val versDepotCount  = chargement.items?.count { it.direction == "vers_depot" } ?: 0

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DsColors.Surface)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedChargement = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
                    }
                    Spacer(Modifier.width(DsSpacing.xs))
                    Text(
                        "Chargement #${chargement.id}",
                        fontSize   = DsTextSize.title,
                        fontWeight = FontWeight.Bold,
                        color      = DsColors.TextPrimary
                    )
                }
                IconButton(
                    onClick  = { correctingChargement = chargement },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(DsShapes.small)
                        .background(DsColors.PrimaryLight)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Corriger",
                        tint     = DsColors.Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            LazyColumn(
                contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
                modifier            = Modifier.weight(1f)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DsShapes.large)
                            .background(DsColors.SurfaceMuted)
                            .padding(DsSpacing.lg),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                    ) {
                        Box(
                            modifier         = Modifier.size(42.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                formatOrderDate(chargement.created_at?.take(10) ?: ""),
                                fontSize   = DsTextSize.body,
                                fontWeight = FontWeight.SemiBold,
                                color      = DsColors.TextPrimary
                            )
                            Text(
                                formatOrderTime(chargement.created_at),
                                fontSize = DsTextSize.caption,
                                color    = DsColors.TextSecondary
                            )
                        }
                    }
                }

                item {
                    Text(
                        "Articles",
                        fontSize   = DsTextSize.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = DsColors.TextSecondary
                    )
                }

                chargement.items?.let { itemsList ->
                    items(itemsList, key = { it.id }) { item ->
                        val toCamion = item.direction == "vers_camion"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.large)
                                .background(DsColors.Surface)
                                .border(1.dp, DsColors.Border, DsShapes.large)
                                .padding(DsSpacing.md),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.product_name,
                                    fontSize   = DsTextSize.body,
                                    fontWeight = FontWeight.Medium,
                                    color      = DsColors.TextPrimary,
                                    maxLines   = 1
                                )
                                Text(
                                    "${formatQty(item.quantity)} ${item.unit_type}",
                                    fontSize = DsTextSize.caption,
                                    color    = DsColors.TextSecondary
                                )
                            }
                            Spacer(Modifier.width(DsSpacing.sm))
                            Box(
                                modifier = Modifier
                                    .clip(DsShapes.pill)
                                    .background(if (toCamion) DsColors.PrimaryLight else DsColors.SuccessLight)
                                    .padding(horizontal = DsSpacing.sm, vertical = 4.dp)
                            ) {
                                Text(
                                    if (toCamion) "Vers camion" else "Vers dépôt",
                                    fontSize   = DsTextSize.caption,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = if (toCamion) DsColors.Primary else DsColors.Success
                                )
                            }
                        }
                    }
                }

                chargement.note?.let { note ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(DsShapes.large)
                                .background(DsColors.SurfaceMuted)
                                .padding(DsSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                        ) {
                            Icon(Icons.Default.Notes, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
                            Text(note, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary)
                        }
                    }
                }
            }
        }
        return
    }

    // ── LEVEL 2 — Session Detail ──
    selectedSession?.let { session ->
        BackHandler { selectedSession = null }

        // ── Note Edit Dialog ──
        if (showNoteDialog) {
            AlertDialog(
                onDismissRequest = { showNoteDialog = false },
                title = { Text("Note de session") },
                text  = {
                    OutlinedTextField(
                        value         = editingNote,
                        onValueChange = { editingNote = it },
                        placeholder   = { Text("Ajouter une note…", fontSize = DsTextSize.body) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = DsShapes.medium,
                        minLines      = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = DsColors.Border,
                            focusedBorderColor   = DsColors.Primary
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateSessionNote(
                            id        = session.id,
                            note      = editingNote.trim().ifEmpty { null },
                            onSuccess = { showNoteDialog = false; viewModel.loadSessions() },
                            onError   = {}
                        )
                    }) {
                        Text("Enregistrer", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNoteDialog = false }) {
                        Text("Annuler")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DsColors.Surface)
        ) {
            // ── Header ──
            Row(
                modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedSession = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
                    }
                    Spacer(Modifier.width(DsSpacing.xs))
                    Column {
                        Text(
                            formatSessionDate(session.session_date),
                            fontSize = DsTextSize.caption,
                            color    = DsColors.TextSecondary
                        )
                        Text(
                            "Session #${session.id}",
                            fontSize   = DsTextSize.title,
                            fontWeight = FontWeight.Bold,
                            color      = DsColors.TextPrimary
                        )
                    }
                }
                IconButton(
                    onClick  = {
                        editingNote    = session.note ?: ""
                        showNoteDialog = true
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(DsShapes.small)
                        .background(DsColors.PrimaryLight)
                ) {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = "Note",
                        tint     = DsColors.Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            LazyColumn(
                contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                modifier            = Modifier.weight(1f),
                reverseLayout       = true
            )  {
                if (!showNoteDialog) {
                    session.note?.takeIf { it.isNotBlank() }?.let { note ->
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(DsShapes.large)
                                    .background(DsColors.SurfaceMuted)
                                    .padding(DsSpacing.md),
                                horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)
                            ) {
                                Icon(Icons.Default.Notes, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(16.dp))
                                Text(note, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary)
                            }
                        }
                    }
                }

                val sessionChargements = session.chargements
                if (sessionChargements.isNullOrEmpty()) {
                    item {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(DsSpacing.xxl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aucun mouvement dans cette session", color = DsColors.TextSecondary)
                        }
                    }
                } else {
                    items(sessionChargements, key = { it.id }) { chargement ->
                        ChargementCard(
                            chargement  = chargement,
                            onClick     = {
                                selectedChargement = chargement
                                viewModel.loadChargementDetail(chargement.id)
                            },
                            onLongClick = { longPressChargement = chargement }
                        )
                    }
                }
            }
        }
        return
    }

    // ── LEVEL 1 — Sessions List ──
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
            Text(
                "Sessions",
                fontSize   = DsTextSize.headline,
                fontWeight = FontWeight.ExtraBold,
                color      = DsColors.TextPrimary
            )
            IconButton(
                onClick  = { showNewChargement = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(DsShapes.pill)
                    .background(DsColors.Primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nouveau mouvement", tint = androidx.compose.ui.graphics.Color.White)
            }
        }

        // ── Loading ──
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.Primary)
            }
            return
        }

        // ── Error ──
        error?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(it, color = DsColors.Danger)
            }
            return
        }

        // ── Empty State ──
        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint     = DsColors.TextTertiary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(DsSpacing.md))
                    Text("Aucune session enregistrée", color = DsColors.TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
            return
        }

        // ── List ──
        LazyColumn(
            contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
            modifier            = Modifier.weight(1f)
        ) {
            items(sessions, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    onClick = {
                        selectedSession = session
                        viewModel.loadSessionDetail(session.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun SessionCard(session: ChargementSession, onClick: () -> Unit) {
    val totalMouvements = session.chargements?.size ?: 0
    val allItems        = session.chargements?.flatMap { it.items ?: emptyList() } ?: emptyList()
    val versCamionTotal = allItems.count { it.direction == "vers_camion" }
    val versDepotTotal  = allItems.count { it.direction == "vers_depot" }

    val summaryParts = mutableListOf("$totalMouvements mouvement(s)")
    if (versCamionTotal > 0) summaryParts.add("$versCamionTotal vers camion")
    if (versDepotTotal > 0) summaryParts.add("$versDepotTotal vers dépôt")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .clickable { onClick() }
            .padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier.size(46.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(22.dp))
        }

        Spacer(Modifier.width(DsSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Session #${session.id}",
                fontSize   = DsTextSize.caption,
                fontWeight = FontWeight.Medium,
                color      = DsColors.TextSecondary
            )
            Text(
                formatSessionDate(session.session_date),
                fontSize   = DsTextSize.bodyLarge,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary
            )
            Text(
                summaryParts.joinToString(" · "),
                fontSize = DsTextSize.caption,
                color    = DsColors.TextSecondary
            )
            session.note?.takeIf { it.isNotBlank() }?.let { note ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notes, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(note, fontSize = DsTextSize.caption, color = DsColors.TextSecondary, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.width(DsSpacing.sm))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(16.dp))
    }
}

private fun formatSessionDate(dateStr: String): String {
    return try {
        val date = LocalDate.parse(dateStr.take(10))
        date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH))
    } catch (e: Exception) { dateStr }
}
