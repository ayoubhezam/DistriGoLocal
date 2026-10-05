package com.distrigo.app.ui.charges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.distrigo.app.data.model.ChargeSubType
import com.distrigo.app.data.model.ChargeType
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.inventory.InventoryDateField
import java.time.LocalDate

/** What the dialog saves: the sub-type, the amount, the supplier (only where the sub-type asks for one), the day and the note. */
data class ChargeInput(val subtypeId: Int, val montant: Double, val fournisseur: String?, val date: LocalDate, val note: String?)

/** Parses "1 500,50" or "1500.5" into an amount; null when it is not one. */
internal fun parseMontant(text: String): Double? =
    text.replace(" ", "").replace(" ", "").replace(",", ".").toDoubleOrNull()

/**
 * A charge, in a dialog centred on the screen: one list for its type and sub-type ("Véhicule ›
 * Carburant"), which ends with "+ Nouveau sous-type"; the amount; the supplier or station when the
 * sub-type asks for one; the day — "Aujourd'hui" unless another is picked, the time being the moment it
 * is saved; an optional note.
 */
@Composable
fun ChargeDialog(
    title        : String,
    types        : List<ChargeType>,
    subTypes     : List<ChargeSubType>,
    initial      : ChargeInput?,
    isSaving     : Boolean,
    error        : String,
    onSave       : (ChargeInput) -> Unit,
    onAddSubType : (typeId: Int, name: String, hasFournisseur: Boolean, onDone: (ChargeSubType?, String?) -> Unit) -> Unit,
    onDismiss    : () -> Unit,
) {
    var subtypeId by remember { mutableStateOf(initial?.subtypeId) }
    var montant by remember { mutableStateOf(initial?.montant?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var fournisseur by remember { mutableStateOf(initial?.fournisseur ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var menu by remember { mutableStateOf(false) }
    // "+ Nouveau sous-type": the list turns into its name, its type and its supplier switch.
    var newName by remember { mutableStateOf<String?>(null) }
    var newTypeId by remember { mutableStateOf<Int?>(null) }
    var newNeedsSupplier by remember { mutableStateOf(false) }
    var newError by remember { mutableStateOf("") }
    var newTypeMenu by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    val subType = subTypes.find { it.id == subtypeId }
    val typeOf = { s: ChargeSubType -> types.find { it.id == s.type_id } }
    val amount = parseMontant(montant)
    val amountError = if (montant.isNotBlank() && (amount == null || amount <= 0)) "Montant invalide" else null
    val needsSupplier = subType?.has_fournisseur == true

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = DsSpacing.lg)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(DsShapes.large)
                .background(DsColors.Surface)
                .verticalScroll(rememberScrollState())
                .padding(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            Text(title, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)

            val typing = newName
            if (typing != null) {
                OutlinedTextField(
                    value = typing, onValueChange = { newName = it; newError = "" },
                    label = { Text("Nouveau sous-type") }, singleLine = true,
                    isError = newError.isNotEmpty(),
                    supportingText = if (newError.isNotEmpty()) ({ Text(newError) }) else null,
                    shape = DsShapes.medium,
                    colors = dsTextFieldColors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary),
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    Field("Type", types.find { it.id == newTypeId }?.name, "Choisir un type") { newTypeMenu = true }
                    DropdownMenu(expanded = newTypeMenu, onDismissRequest = { newTypeMenu = false }, modifier = Modifier.background(DsColors.Surface)) {
                        types.forEach { t ->
                            DropdownMenuItem(text = { Text(t.name) }, onClick = { newTypeId = t.id; newTypeMenu = false; newError = "" })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nécessite un fournisseur / une station", fontSize = DsTextSize.body, color = DsColors.TextPrimary, modifier = Modifier.weight(1f))
                    Switch(
                        checked = newNeedsSupplier, onCheckedChange = { newNeedsSupplier = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = DsColors.Primary)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    OutlinedButton(onClick = { newName = null; newError = "" }, enabled = !adding, modifier = Modifier.weight(1f), shape = DsShapes.medium) {
                        Text("Annuler")
                    }
                    Button(
                        onClick = {
                            val t = newTypeId
                            if (t == null) { newError = "Choisissez un type"; return@Button }
                            adding = true
                            onAddSubType(t, typing, newNeedsSupplier) { created, failure ->
                                adding = false
                                if (created != null) { subtypeId = created.id; newName = null } else newError = failure ?: ""
                            }
                        },
                        enabled = !adding && typing.isNotBlank(),
                        modifier = Modifier.weight(1f), shape = DsShapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                    ) { Text("Ajouter", fontWeight = FontWeight.SemiBold) }
                }
            } else Box {
                Field(
                    "Type de charge",
                    subType?.let { s -> listOfNotNull(typeOf(s)?.name, s.name).joinToString(" › ") },
                    "Choisir un type de charge"
                ) { menu = true }
                // One list, grouped by type: a type's name as a header, its sub-types under it.
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(DsColors.Surface).heightIn(max = 420.dp)) {
                    types.forEach { t ->
                        val ofType = subTypes.filter { it.type_id == t.id }
                        if (ofType.isEmpty()) return@forEach
                        Text(
                            t.name.uppercase(), fontSize = DsTextSize.caption, fontWeight = FontWeight.Bold,
                            color = ChargeIconMapper.colorFor(t.color_hex),
                            modifier = Modifier.padding(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.sm, bottom = 2.dp)
                        )
                        ofType.forEach { s ->
                            DropdownMenuItem(
                                text = {
                                    Text(s.name, fontWeight = if (s.id == subtypeId) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (s.id == subtypeId) DsColors.Primary else DsColors.TextPrimary)
                                },
                                leadingIcon = { Icon(ChargeIconMapper.iconFor(s.icon), contentDescription = null, tint = ChargeIconMapper.colorFor(t.color_hex), modifier = Modifier.size(18.dp)) },
                                onClick = { subtypeId = s.id; menu = false }
                            )
                        }
                    }
                    HorizontalDivider(color = DsColors.Border)
                    DropdownMenuItem(
                        text = { Text("Nouveau sous-type", color = DsColors.Primary, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.Primary) },
                        onClick = { menu = false; newName = ""; newError = ""; newTypeId = subType?.type_id; newNeedsSupplier = false }
                    )
                }
            }

            OutlinedTextField(
                value = montant,
                onValueChange = { v -> montant = v.filter { it.isDigit() || it == ',' || it == '.' } },
                label = { Text("Montant") },
                suffix = { Text("DA") },
                singleLine = true,
                isError = amountError != null,
                supportingText = amountError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = DsShapes.medium,
                colors = dsTextFieldColors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary),
                modifier = Modifier.fillMaxWidth()
            )
            if (needsSupplier) {
                OutlinedTextField(
                    value = fournisseur, onValueChange = { fournisseur = it },
                    label = { Text("Fournisseur / Station") }, placeholder = { Text("Ex: Station Naftal - Hydra") },
                    singleLine = true, shape = DsShapes.medium,
                    colors = dsTextFieldColors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            InventoryDateField(date = date, enabled = !isSaving, onDateChange = { date = it })
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Note (optionnel)") }, singleLine = true, shape = DsShapes.medium,
                colors = dsTextFieldColors(unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary),
                modifier = Modifier.fillMaxWidth()
            )
            if (error.isNotEmpty()) Text(error, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)

            Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                OutlinedButton(onClick = onDismiss, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = DsShapes.medium) {
                    Text("Annuler")
                }
                Button(
                    onClick = {
                        val s = subtypeId; val a = amount
                        if (s != null && a != null) onSave(ChargeInput(
                            subtypeId = s, montant = a,
                            fournisseur = fournisseur.trim().takeIf { needsSupplier && it.isNotEmpty() },
                            date = date, note = note.trim().takeIf { it.isNotEmpty() }
                        ))
                    },
                    enabled = !isSaving && newName == null && subType != null && amount != null && amount > 0,
                    modifier = Modifier.weight(1f).height(48.dp), shape = DsShapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                    else Text("Enregistrer", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** A field that opens a list: its label, the value or a placeholder, a chevron. */
@Composable
private fun Field(label: String, value: String?, placeholder: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .clickable(role = Role.DropdownList, onClick = onClick)
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm)
    ) {
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value ?: placeholder, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium,
                color = if (value != null) DsColors.TextPrimary else DsColors.TextTertiary, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(DsSpacing.xs))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DsColors.TextSecondary)
        }
    }
}
