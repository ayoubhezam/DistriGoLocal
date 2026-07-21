package com.distrigo.app.ui.pertes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.PerteType
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

@Composable
fun PertesScreen(
    viewModel          : PerteViewModel = viewModel(),
    onFullScreenChange : (Boolean) -> Unit = {}
) {
    val perteTypes by viewModel.perteTypes.collectAsState()
    val isLoading  by viewModel.isLoading.collectAsState()

    var selectedTypeId       by remember { mutableStateOf<Int?>(null) }
    var showPerteForm        by remember { mutableStateOf(false) }
    var editingPerte         by remember { mutableStateOf<com.distrigo.app.data.model.Perte?>(null) }
    var showAddTypeDialog    by remember { mutableStateOf(false) }
    var newTypeName          by remember { mutableStateOf("") }
    var longPressType        by remember { mutableStateOf<PerteType?>(null) }
    var showDeleteTypeDialog by remember { mutableStateOf(false) }
    var deleteTypeError      by remember { mutableStateOf("") }

    // ── Formulaire de perte (plein écran) ──
    if (showPerteForm && selectedTypeId != null) {
        onFullScreenChange(true)
        BackHandler { showPerteForm = false; editingPerte = null; onFullScreenChange(false) }
        PerteFormScreen(
            typeId  = selectedTypeId!!,
            perte   = editingPerte,
            onBack  = { showPerteForm = false; editingPerte = null; onFullScreenChange(false) },
            onSaved = {
                showPerteForm = false
                editingPerte  = null
                onFullScreenChange(false)
                viewModel.loadPertes(selectedTypeId!!)
                viewModel.loadPerteTypes()
            }
        )
        return
    }

    // ── Liste des pertes d'un type ──
    selectedTypeId?.let { typeId ->
        BackHandler { selectedTypeId = null }
        PerteListScreen(
            typeId      = typeId,
            onBack      = { selectedTypeId = null },
            onAddPerte  = { editingPerte = null; showPerteForm = true },
            onEditPerte = { perte -> editingPerte = perte; showPerteForm = true }
        )
        return
    }
    LaunchedEffect(Unit) { viewModel.loadPerteTypes() }


    // ── Accueil : Types de pertes ──
    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier.size(40.dp).clip(DsShapes.pill).background(DsColors.Primary).clickable { showAddTypeDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un type", tint = Color.White)
            }
        }

        // ── Carte résumé globale (toutes les pertes confondues) ──
        if (perteTypes.isNotEmpty()) {
            val totalValue = perteTypes.sumOf { it.total_value }
            val totalQty   = perteTypes.sumOf { it.total_qty }
            val totalCount = perteTypes.sumOf { it.count }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                shape    = DsShapes.large,
                colors   = CardDefaults.cardColors(containerColor = DsColors.Primary)
            ) {
                Column(Modifier.padding(DsSpacing.lg)) {
                    Text("Résumé ce mois", fontSize = DsTextSize.bodySmall, color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(DsSpacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.xxl)) {
                        Column {
                            Text("${"%,.0f".format(totalValue)} DA", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text("Valeur totale", fontSize = DsTextSize.caption, color = Color.White.copy(alpha = 0.75f))
                        }
                        Column {
                            Text(formatQty(totalQty), fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text("Qté totale", fontSize = DsTextSize.caption, color = Color.White.copy(alpha = 0.75f))
                        }
                        Column {
                            Text("$totalCount", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text("Nombre de pertes", fontSize = DsTextSize.caption, color = Color.White.copy(alpha = 0.75f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(DsSpacing.md))
        }

        if (isLoading && perteTypes.isEmpty()) {
        }

        if (isLoading && perteTypes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.Primary)
            }
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                items(perteTypes, key = { it.id }) { type ->
                    PerteTypeRow(
                        type        = type,
                        onClick     = { selectedTypeId = type.id },
                        onLongClick = { longPressType = type; showDeleteTypeDialog = true }
                    )
                }
                item { Spacer(Modifier.height(DsSpacing.xl)) }
            }
        }
    }

    if (showAddTypeDialog) {
        AlertDialog(
            onDismissRequest = { showAddTypeDialog = false; newTypeName = "" },
            title            = { Text("Nouveau type de perte") },
            text = {
                OutlinedTextField(
                    value = newTypeName, onValueChange = { newTypeName = it },
                    placeholder = { Text("Ex: Erreur de livraison") }, singleLine = true, shape = DsShapes.medium
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newTypeName.isNotBlank(),
                    onClick = {
                        viewModel.addPerteType(
                            name = newTypeName.trim(), icon = "category", colorHex = "#98A2B3",
                            onSuccess = { showAddTypeDialog = false; newTypeName = "" },
                            onError   = {}
                        )
                    }
                ) { Text("Ajouter") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTypeDialog = false; newTypeName = "" }) { Text("Annuler") }
            }
        )
    }

    if (showDeleteTypeDialog && longPressType != null) {
        AlertDialog(
            onDismissRequest = { showDeleteTypeDialog = false; deleteTypeError = "" },
            title            = { Text("Supprimer \"${longPressType!!.name}\" ?") },
            text = {
                Column {
                    Text("Cette action est irréversible.")
                    if (deleteTypeError.isNotEmpty()) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text(deleteTypeError, color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePerteType(
                        id = longPressType!!.id,
                        onSuccess = { showDeleteTypeDialog = false; longPressType = null; deleteTypeError = "" },
                        onError   = { msg -> deleteTypeError = msg }
                    )
                }) { Text("Supprimer", color = DsColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTypeDialog = false; deleteTypeError = "" }) { Text("Annuler") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PerteTypeRow(type: PerteType, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick     = onClick,
            onLongClick = if (!type.is_default) onLongClick else null
        ),
        shape = DsShapes.medium,
        color = DsColors.SurfaceMuted
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(DsSpacing.md),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier         = Modifier.size(44.dp).clip(DsShapes.medium).background(PerteIconMapper.colorFor(type.color_hex).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(PerteIconMapper.iconFor(type.icon), contentDescription = null, tint = PerteIconMapper.colorFor(type.color_hex), modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(DsSpacing.md))
                Column {
                    Text(type.name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                    Text(type.description ?: "", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${"%,.0f".format(type.total_value)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = PerteIconMapper.colorFor(type.color_hex))
                Text(formatQty(type.total_qty), fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
            }
        }
    }
}