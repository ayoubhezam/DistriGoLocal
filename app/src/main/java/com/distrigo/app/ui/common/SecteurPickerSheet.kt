package com.distrigo.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.Secteur
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecteurPickerSheet(
    communeName : String,
    wilayaName  : String,
    secteurs    : List<Secteur>,
    onDismiss   : () -> Unit,
    onSelect    : (Secteur) -> Unit,
    onAddNew    : (String) -> Unit
) {
    var showAddField by remember { mutableStateOf(false) }
    var newName       by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.md)
                .padding(bottom = DsSpacing.lg)
        ) {
            Text(
                "Secteurs disponibles à $communeName",
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.TextPrimary
            )
            Spacer(Modifier.height(DsSpacing.sm))

            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(secteurs, key = { _, s -> s.id }) { index, secteur ->
                    Text(
                        text     = "${(index + 1).toString().padStart(2, '0')}-${secteur.nom}",
                        fontSize = DsTextSize.body,
                        color    = DsColors.TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(secteur); onDismiss() }
                            .padding(vertical = DsSpacing.sm)
                    )
                    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
                }
                if (secteurs.isEmpty()) {
                    item {
                        Text(
                            "Aucun secteur pour cette commune",
                            color    = DsColors.TextSecondary,
                            modifier = Modifier.padding(vertical = DsSpacing.md)
                        )
                    }
                }
            }

            Spacer(Modifier.height(DsSpacing.sm))

            if (showAddField) {
                OutlinedTextField(
                    value         = newName,
                    onValueChange = { newName = it },
                    placeholder   = { Text("Nom du secteur") },
                    singleLine    = true,
                    shape         = DsShapes.medium,
                    modifier      = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(DsSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    OutlinedButton(
                        onClick  = { showAddField = false; newName = "" },
                        modifier = Modifier.weight(1f)
                    ) { Text("Annuler") }
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onAddNew(newName.trim())
                                newName = ""
                                showAddField = false
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                    ) { Text("Ajouter") }
                }
            } else {
                OutlinedButton(
                    onClick  = { showAddField = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = DsShapes.medium
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ajouter un nouveau secteur", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}