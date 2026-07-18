package com.distrigo.app.ui.charges

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.ChargeSubType
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargeSubTypesScreen(
    typeId         : Int,
    viewModel      : ChargeViewModel = viewModel(),
    onBack         : () -> Unit,
    onSubTypeClick : (Int) -> Unit
) {
    val subTypes   by viewModel.subTypes.collectAsState()
    val chargeTypes by viewModel.chargeTypes.collectAsState()
    val type = chargeTypes.find { it.id == typeId }

    var showAddSubTypeDialog by remember { mutableStateOf(false) }
    var newSubTypeName       by remember { mutableStateOf("") }
    var newSubTypeHasFourn   by remember { mutableStateOf(false) }

    // ── حالة الحذف ──
    var longPressSubType        by remember { mutableStateOf<ChargeSubType?>(null) }
    var showDeleteSubTypeDialog by remember { mutableStateOf(false) }
    var deleteSubTypeError      by remember { mutableStateOf("") }

    LaunchedEffect(typeId) { viewModel.loadSubTypes(typeId) }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        Row(
            modifier           = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            verticalAlignment  = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)
            }
            Spacer(Modifier.width(DsSpacing.xs))
            type?.let {
                Box(
                    modifier         = Modifier.size(44.dp).clip(DsShapes.medium).background(ChargeIconMapper.colorFor(it.color_hex).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(ChargeIconMapper.iconFor(it.icon), contentDescription = null, tint = ChargeIconMapper.colorFor(it.color_hex), modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(type?.name ?: "", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                Text("${subTypes.size} sous-types", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
            }
        }

        if (subTypes.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Aucun sous-type pour l'instant", color = DsColors.TextSecondary)
            }
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxWidth().weight(1f),
                contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                items(subTypes, key = { it.id }) { sub ->
                    ChargeSubTypeRow(
                        sub         = sub,
                        onClick     = { onSubTypeClick(sub.id) },
                        onLongClick = { longPressSubType = sub; showDeleteSubTypeDialog = true }
                    )
                }
            }
        }

        Button(
            onClick  = { showAddSubTypeDialog = true },
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(DsSpacing.xs))
            Text("Ajouter un sous-type", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }

    // ── Dialog: Ajouter un sous-type ──
    if (showAddSubTypeDialog) {
        AlertDialog(
            onDismissRequest = { showAddSubTypeDialog = false; newSubTypeName = ""; newSubTypeHasFourn = false },
            title = { Text("Nouveau sous-type") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newSubTypeName, onValueChange = { newSubTypeName = it },
                        placeholder = { Text("Ex: Amende") }, singleLine = true, shape = DsShapes.medium
                    )
                    Spacer(Modifier.height(DsSpacing.md))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Nécessite un fournisseur/station", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                        Switch(checked = newSubTypeHasFourn, onCheckedChange = { newSubTypeHasFourn = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newSubTypeName.isNotBlank(),
                    onClick = {
                        viewModel.addSubType(
                            typeId = typeId, name = newSubTypeName.trim(), icon = "category",
                            hasFournisseur = newSubTypeHasFourn,
                            onSuccess = { showAddSubTypeDialog = false; newSubTypeName = ""; newSubTypeHasFourn = false },
                            onError   = {}
                        )
                    }
                ) { Text("Ajouter") }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubTypeDialog = false; newSubTypeName = ""; newSubTypeHasFourn = false }) { Text("Annuler") }
            }
        )
    }

    // ── Dialog: Supprimer un sous-type ──
    if (showDeleteSubTypeDialog && longPressSubType != null) {
        AlertDialog(
            onDismissRequest = { showDeleteSubTypeDialog = false; deleteSubTypeError = "" },
            title            = { Text("Supprimer \"${longPressSubType!!.name}\" ?") },
            text = {
                Column {
                    Text("Cette action est irréversible.")
                    if (deleteSubTypeError.isNotEmpty()) {
                        Spacer(Modifier.height(DsSpacing.sm))
                        Text(deleteSubTypeError, color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSubType(
                        id = longPressSubType!!.id, typeId = typeId,
                        onSuccess = { showDeleteSubTypeDialog = false; longPressSubType = null; deleteSubTypeError = "" },
                        onError   = { msg -> deleteSubTypeError = msg }
                    )
                }) { Text("Supprimer", color = DsColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSubTypeDialog = false; deleteSubTypeError = "" }) { Text("Annuler") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChargeSubTypeRow(sub: ChargeSubType, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick     = onClick,
            onLongClick = if (!sub.is_default) onLongClick else null
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
                    modifier         = Modifier.size(40.dp).clip(DsShapes.medium).background(DsColors.PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(ChargeIconMapper.iconFor(sub.icon), contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(DsSpacing.md))
                Text(sub.name, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${"%,.0f".format(sub.total_this_month)} DA", fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold, color = DsColors.Primary)
                    Text("Total ce mois", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
                }
                Spacer(Modifier.width(DsSpacing.sm))
                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(14.dp))
            }
        }
    }
}