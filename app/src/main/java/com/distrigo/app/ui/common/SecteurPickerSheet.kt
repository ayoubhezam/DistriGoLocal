package com.distrigo.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.model.Secteur
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

/**
 * The commune's secteurs, with an inline "add a new one" field.
 *
 * Two modes, chosen by [selectedIds]:
 *
 *  - **single** (`selectedIds == null`) — a client has one secteur, so picking a row reports it and
 *    closes the sheet. This is what ClientFormScreen uses, unchanged.
 *  - **multiple** (`selectedIds != null`) — a tournée covers several, so rows carry a checkbox,
 *    [onSelect] reads as a toggle, and the sheet stays open until "Terminé". Adding a new secteur
 *    likewise selects it and leaves the sheet open, so several can be created in one pass.
 *
 * One component rather than two because everything else about the sheet — the per-commune list, the
 * numbering, the add field and its validation — is identical between the two callers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecteurPickerSheet(
    communeName : String,
    wilayaName  : String,
    secteurs    : List<Secteur>,
    onDismiss   : () -> Unit,
    onSelect    : (Secteur) -> Unit,
    onAddNew    : (String) -> Unit,
    selectedIds : Set<Int>? = null
) {
    var showAddField by remember { mutableStateOf(false) }
    var newName       by remember { mutableStateOf("") }

    val isMultiSelect = selectedIds != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = DsSpacing.md)
                .padding(bottom = DsSpacing.lg)
        ) {
            Text(
                stringResource(R.string.common_secteurs_in_commune, communeName),
                fontSize   = DsTextSize.title,
                fontWeight = FontWeight.Bold,
                color      = DsColors.PrimaryDark
            )
            if (isMultiSelect) {
                Spacer(Modifier.height(2.dp))
                Text(
                    if (selectedIds!!.isEmpty()) stringResource(R.string.common_secteurs_tap_to_select)
                    else stringResource(R.string.common_secteurs_selected, selectedIds.size),
                    fontSize = DsTextSize.bodySmall,
                    color    = if (selectedIds.isEmpty()) DsColors.TextSecondary else DsColors.Primary
                )
            }
            Spacer(Modifier.height(DsSpacing.sm))

            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(secteurs, key = { _, s -> s.id }) { index, secteur ->
                    val isSelected = selectedIds?.contains(secteur.id) == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(secteur)
                                // Single-select reports one answer and is done; multi-select stays
                                // open so the next one can be picked without reopening the sheet.
                                if (!isMultiSelect) onDismiss()
                            }
                            .padding(vertical = DsSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text     = "${(index + 1).toString().padStart(2, '0')}-${secteur.nom}",
                            fontSize = DsTextSize.body,
                            color    = DsColors.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (isMultiSelect) {
                            Checkbox(
                                checked         = isSelected,
                                onCheckedChange = { onSelect(secteur) },
                                colors          = CheckboxDefaults.colors(checkedColor = DsColors.Primary)
                            )
                        }
                    }
                    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
                }
                if (secteurs.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.common_secteurs_none_in_commune),
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
                    placeholder   = { Text(stringResource(R.string.common_secteur_name)) },
                    singleLine    = true,
                    shape         = DsShapes.medium,
                    modifier      = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(DsSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    OutlinedButton(
                        onClick  = { showAddField = false; newName = "" },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.common_action_cancel)) }
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onAddNew(newName.trim())
                                newName = ""
                                showAddField = false
                                if (!isMultiSelect) onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
                    ) { Text(stringResource(R.string.common_action_add)) }
                }
            } else {
                OutlinedButton(
                    onClick  = { showAddField = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = DsShapes.medium
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_secteur_add), color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }

            if (isMultiSelect) {
                Spacer(Modifier.height(DsSpacing.sm))
                Button(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
                ) { Text(stringResource(R.string.common_action_done), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
