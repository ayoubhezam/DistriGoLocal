
@file:OptIn(ExperimentalMaterial3Api::class)
package com.distrigo.app.ui.charges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.lifecycle.viewmodel.compose.viewModel
import com.distrigo.app.data.model.Charge
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
@Composable
fun ChargeFormScreen(
    subtypeId : Int,
    charge    : Charge? = null,
    viewModel : ChargeViewModel = viewModel(),
    onBack    : () -> Unit,
    onSaved   : () -> Unit
) {
    val isEdit = charge != null
    val subTypes   by viewModel.subTypes.collectAsState()
    val chargeTypes by viewModel.chargeTypes.collectAsState()
    val subType = subTypes.find { it.id == subtypeId }
    val type    = chargeTypes.find { it.id == subType?.type_id }

    val initialDateTime = charge?.date_time?.let { Instant.parse(it).atZone(ZoneId.systemDefault()) }
    var currentStep by remember { mutableStateOf(1) }
    var montant     by remember { mutableStateOf(charge?.montant?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }
    var selectedDate by remember { mutableStateOf(initialDateTime?.toLocalDate() ?: LocalDate.now()) }
    var selectedTime by remember { mutableStateOf(initialDateTime?.toLocalTime() ?: LocalTime.now()) }
    var fournisseur  by remember { mutableStateOf(charge?.fournisseur ?: "") }
    var note         by remember { mutableStateOf(charge?.note ?: "") }
    var montantError by remember { mutableStateOf("") }
    var isSaving      by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val isoDateTime = selectedDate.atTime(selectedTime).atZone(ZoneId.systemDefault()).toInstant().toString()

    fun save() {
        val montantValue = montant.replace(",", ".").toDoubleOrNull()
        if (montantValue == null || montantValue <= 0.0) {
            montantError = "Montant invalide"
            return
        }
        montantError = ""
        isSaving = true
        val fournisseurValue = if (subType?.has_fournisseur == true) fournisseur.trim().ifEmpty { null } else null
        val noteValue = note.trim().ifEmpty { null }

        if (charge != null) {
            viewModel.updateCharge(
                id = charge.id, subtypeId = subtypeId, montant = montantValue,
                dateTime = isoDateTime, fournisseur = fournisseurValue, note = noteValue,
                onSuccess = onSaved, onError = { isSaving = false }
            )
        } else {
            viewModel.addCharge(
                subtypeId = subtypeId, montant = montantValue, dateTime = isoDateTime,
                fournisseur = fournisseurValue, note = noteValue,
                onSuccess = onSaved, onError = { isSaving = false }
            )
        }
    }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        // ── En-tête + indicateur d'étapes ──
        Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (currentStep == 2) currentStep = 1 else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsColors.TextPrimary)            }
            Spacer(Modifier.width(DsSpacing.sm))
            Text(if (isEdit) "Modifier la dépense" else "Nouvelle dépense", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
        }
        StepIndicator(currentStep = currentStep)
        Spacer(Modifier.height(DsSpacing.md))

        if (currentStep == 1) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                FixedInfoField(label = "Type de charge", icon = type?.icon ?: "category", value = type?.name ?: "")
                FixedInfoField(label = "Sous-type de charge", icon = subType?.icon ?: "category", value = subType?.name ?: "")

                DsFormField(
                    label = "Montant", value = montant,
                    onValueChange = { montant = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    error = montantError, placeholder = "0,00", keyboardType = KeyboardType.Decimal
                )

                Column {
                    Text("Date", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth(), shape = DsShapes.medium) {
                        Text(formatOrderDate(isoDateTime), color = DsColors.TextPrimary)
                    }
                }
                Column {
                    Text("Heure", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth(), shape = DsShapes.medium) {
                        Text(formatOrderTime(isoDateTime), color = DsColors.TextPrimary)
                    }
                }

                if (subType?.has_fournisseur == true) {
                    DsFormField(
                        label = "Fournisseur / Station", value = fournisseur,
                        onValueChange = { fournisseur = it }, placeholder = "Ex: Station Naftal - Hydra"
                    )
                }

                DsFormField(
                    label = "Notes (optionnel)", value = note, onValueChange = { note = it },
                    minLines = 2, maxLines = 3
                )
                Spacer(Modifier.height(DsSpacing.sm))
            }

            Button(
                onClick  = {
                    val v = montant.replace(",", ".").toDoubleOrNull()
                    if (v == null || v <= 0.0) montantError = "Montant invalide" else currentStep = 2
                },
                modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
                shape    = DsShapes.medium,
                colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
            ) {
                Text("Suivant", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                Text("Vérifiez les informations", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                SummaryRow("Type de charge", type?.name ?: "")
                SummaryRow("Sous-type de charge", subType?.name ?: "")
                SummaryRow("Montant", "${montant.ifBlank { "0" }} DA", highlight = true)
                SummaryRow("Date", "${formatOrderDate(isoDateTime)} à ${formatOrderTime(isoDateTime)}")
                if (subType?.has_fournisseur == true) {
                    SummaryRow("Fournisseur / Station", fournisseur.ifBlank { "—" })
                }
                SummaryRow("Notes", note.ifBlank { "Aucun" })
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
            ) {
                OutlinedButton(
                    onClick = { currentStep = 1 },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = DsShapes.medium
                ) { Text("Modifier") }

                Button(
                    onClick  = { save() },
                    enabled  = !isSaving,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    else Text("Enregistrer", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annuler") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        val timePickerState = androidx.compose.material3.rememberTimePickerState(
            initialHour = selectedTime.hour, initialMinute = selectedTime.minute, is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Sélectionner l'heure") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun StepIndicator(currentStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.xxxl),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepDot(number = 1, label = "Détails", active = currentStep >= 1)
        Box(Modifier.weight(1f).height(2.dp).background(if (currentStep >= 2) DsColors.Primary else DsColors.Border))
        StepDot(number = 2, label = "Résumé", active = currentStep >= 2)
    }
}

@Composable
private fun StepDot(number: Int, label: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(28.dp).clip(DsShapes.pill).background(if (active) DsColors.Primary else DsColors.Border),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", color = Color.White, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(DsSpacing.xs))
        Text(label, fontSize = DsTextSize.caption, color = if (active) DsColors.Primary else DsColors.TextTertiary)
    }
}

@Composable
private fun FixedInfoField(label: String, icon: String, value: String) {
    Column {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
        Surface(shape = DsShapes.medium, color = DsColors.SurfaceSunken, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(ChargeIconMapper.iconFor(icon), contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(value, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = DsSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        Text(
            value, fontSize = DsTextSize.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) DsColors.Primary else DsColors.TextPrimary
        )
    }
    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
}

@Composable
private fun DsFormField(
    label: String, value: String, onValueChange: (String) -> Unit,
    error: String = "", placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text, minLines: Int = 1, maxLines: Int = 1
) {
    Column {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = DsTextSize.body) },
            singleLine = minLines == 1 && maxLines == 1, minLines = minLines, maxLines = maxLines,
            isError = error.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = DsShapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary, errorBorderColor = DsColors.Danger
            )
        )
        if (error.isNotEmpty()) {
            Text(error, fontSize = DsTextSize.caption, color = DsColors.Danger, modifier = Modifier.padding(start = DsSpacing.xs, top = 2.dp))
        }
    }
}