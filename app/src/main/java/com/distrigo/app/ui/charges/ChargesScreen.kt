package com.distrigo.app.ui.charges

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.distrigo.app.data.model.ChargeType
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.compose.foundation.combinedClickable
@Composable
fun ChargesScreen(
    viewModel          : ChargeViewModel = viewModel(),
    onFullScreenChange : (Boolean) -> Unit = {}
) {
    val chargeTypes by viewModel.chargeTypes.collectAsState()
    val isLoading    by viewModel.isLoading.collectAsState()

    var selectedTypeId    by remember { mutableStateOf<Int?>(null) }
    var selectedSubTypeId by remember { mutableStateOf<Int?>(null) }
    var showChargeForm    by remember { mutableStateOf(false) }
    var editingCharge     by remember { mutableStateOf<com.distrigo.app.data.model.Charge?>(null) }
    var showAddTypeDialog by remember { mutableStateOf(false) }
    var newTypeName       by remember { mutableStateOf("") }
    var longPressType       by remember { mutableStateOf<ChargeType?>(null) }
    var showDeleteTypeDialog by remember { mutableStateOf(false) }
    var deleteTypeError      by remember { mutableStateOf("") }

    // ── Formulaire de dépense (niveau le plus profond، plein écran) ──
    if (showChargeForm && selectedSubTypeId != null) {
        onFullScreenChange(true)
        BackHandler {
            showChargeForm = false
            editingCharge  = null
            onFullScreenChange(false)
        }
        ChargeFormScreen(
            subtypeId = selectedSubTypeId!!,
            charge    = editingCharge,
            onBack    = {
                showChargeForm = false
                editingCharge  = null
                onFullScreenChange(false)
            },
            onSaved   = {
                showChargeForm = false
                editingCharge  = null
                onFullScreenChange(false)
                viewModel.loadCharges(selectedSubTypeId!!)
                viewModel.loadChargeTypes()
            }
        )
        return
    }

    // ── Liste des dépenses d'un sous-type ──
    selectedSubTypeId?.let { subtypeId ->
        BackHandler { selectedSubTypeId = null }
        ChargeListScreen(
            subtypeId    = subtypeId,
            onBack       = { selectedSubTypeId = null },
            onAddCharge  = { editingCharge = null; showChargeForm = true },
            onEditCharge = { charge -> editingCharge = charge; showChargeForm = true }
        )
        return
    }

    // ── Sous-types d'un type ──
    selectedTypeId?.let { typeId ->
        BackHandler { selectedTypeId = null }
        ChargeSubTypesScreen(
            typeId         = typeId,
            onBack         = { selectedTypeId = null },
            onSubTypeClick = { selectedSubTypeId = it }
        )
        return
    }
    LaunchedEffect(Unit) { viewModel.loadChargeTypes() }


    // ── Types de charges (accueil) ──
    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text("Types de charges", fontSize = DsTextSize.headline, fontWeight = FontWeight.ExtraBold, color = DsColors.TextPrimary)
                Text("Gérez vos catégories de charges", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
            }
            Box(
                modifier         = Modifier.size(40.dp).clip(DsShapes.pill).background(DsColors.Primary).clickable { showAddTypeDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un type", tint = Color.White)
            }
        }

        // ── Carte résumé globale (toutes les charges confondues) ──
        if (chargeTypes.isNotEmpty()) {
            val totalValue = chargeTypes.sumOf { it.total_this_month }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg),
                shape    = DsShapes.large,
                colors   = CardDefaults.cardColors(containerColor = DsColors.Primary)
            ) {
                Column(Modifier.padding(DsSpacing.lg)) {
                    Text("Total des charges ce mois", fontSize = DsTextSize.bodySmall, color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(DsSpacing.sm))
                    Text("${"%,.0f".format(totalValue)} DA", fontSize = DsTextSize.display, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(DsSpacing.xs))
                    Text("${chargeTypes.size} types de charges actifs", fontSize = DsTextSize.caption, color = Color.White.copy(alpha = 0.75f))
                }
            }
            Spacer(Modifier.height(DsSpacing.md))
        }

        if (isLoading && chargeTypes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsColors.Primary)
            }
        } else {
            LazyColumn(
                modifier               = Modifier.fillMaxSize(),
                contentPadding         = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                verticalArrangement    = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                items(chargeTypes, key = { it.id }) { type ->
                    ChargeTypeRow(
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
            title            = { Text("Nouveau type de charge") },
            text = {
                OutlinedTextField(
                    value = newTypeName, onValueChange = { newTypeName = it },
                    placeholder = { Text("Ex: Marketing") }, singleLine = true,
                    shape = DsShapes.medium
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newTypeName.isNotBlank(),
                    onClick = {
                        viewModel.addChargeType(
                            name = newTypeName.trim(), icon = "category", colorHex = "#5B6EF5",
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
                    viewModel.deleteChargeType(
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


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChargeTypeRow(type: ChargeType, onClick: () -> Unit, onLongClick: () -> Unit) {
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
                    modifier         = Modifier.size(44.dp).clip(DsShapes.medium).background(ChargeIconMapper.colorFor(type.color_hex).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(ChargeIconMapper.iconFor(type.icon), contentDescription = null, tint = ChargeIconMapper.colorFor(type.color_hex), modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(DsSpacing.md))
                Column {
                    Text(type.name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                    Text("${type.subtypes_count} sous-types", fontSize = DsTextSize.bodySmall, color = DsColors.Primary)
                }
            }
            Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(14.dp))
        }
    }
}