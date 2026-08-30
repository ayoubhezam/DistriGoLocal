package com.distrigo.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.distrigo.app.ui.charges.ChargeViewModel
import com.distrigo.app.ui.charges.DsFormField
import com.distrigo.app.ui.charges.FixedInfoField
import com.distrigo.app.ui.charges.StepIndicator
import com.distrigo.app.ui.charges.SummaryRow
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.purchases.formatOrderDate
import com.distrigo.app.ui.purchases.formatOrderTime
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

// Mirrors pertesFormGraph: form state (montant/date/time/fournisseur/note) lives on the
// ChargesGraph-scoped ChargeViewModel instance — the same one ChargesHome/ChargesSubTypes/
// ChargesList already share. See ChargeViewModel.formMontant/formDate/formTime/
// formFournisseur/formNote.
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.chargesFormGraph(
    navController : NavHostController,
    graphRoute    : String,
    viewModel     : @Composable () -> ChargeViewModel,
    onBack  : () -> Unit,
    onSaved : () -> Unit
) {
    navigation(
        startDestination = Screen.ChargesFormDetails.route,
        route = graphRoute,
        arguments = listOf(
            navArgument("subtypeId") { type = NavType.IntType },
            navArgument("chargeId")  { type = NavType.IntType; defaultValue = -1 }
        )
    ) {
        composable(Screen.ChargesFormDetails.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val subtypeIdArg = parentEntry.arguments!!.getInt("subtypeId")
            val chargeIdArg = parentEntry.arguments?.getInt("chargeId")?.takeIf { it != -1 }
            val isEdit = chargeIdArg != null
            val subTypes by viewModel.subTypes.collectAsState()
            val chargeTypes by viewModel.chargeTypes.collectAsState()
            val charges by viewModel.charges.collectAsState()
            val editingCharge = chargeIdArg?.let { id -> charges.find { it.id == id } }
            val subType = subTypes.find { it.id == subtypeIdArg }
            val type = chargeTypes.find { it.id == subType?.type_id }

            val formMontant by viewModel.formMontant.collectAsState()
            val formDate by viewModel.formDate.collectAsState()
            val formTime by viewModel.formTime.collectAsState()
            val formFournisseur by viewModel.formFournisseur.collectAsState()
            val formNote by viewModel.formNote.collectAsState()
            var montantError by remember { mutableStateOf("") }
            var showDatePicker by remember { mutableStateOf(false) }
            var showTimePicker by remember { mutableStateOf(false) }

            var initialized by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!initialized) {
                    viewModel.resetChargeForm()
                    initialized = true
                }
            }
            // Prefill once from the editing Charge — mirrors the original's one-shot
            // `remember { mutableStateOf(charge?.x ?: ...) }` capture.
            var prefillDone by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(editingCharge) {
                if (isEdit && editingCharge != null && !prefillDone) {
                    val montantStr = editingCharge.montant.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
                    viewModel.setFormMontant(montantStr)
                    val initialDateTime = editingCharge.date_time?.let { Instant.parse(it).atZone(ZoneId.systemDefault()) }
                    viewModel.setFormDate(initialDateTime?.toLocalDate() ?: java.time.LocalDate.now())
                    viewModel.setFormTime(initialDateTime?.toLocalTime() ?: LocalTime.now())
                    viewModel.setFormFournisseur(editingCharge.fournisseur ?: "")
                    viewModel.setFormNote(editingCharge.note ?: "")
                    prefillDone = true
                }
            }

            val isoDateTime = formDate.atTime(formTime).atZone(ZoneId.systemDefault()).toInstant().toString()

            BackHandler { onBack() }

            Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                DsTopAppBar(
                    title   = if (isEdit) "Modifier la dépense" else "Nouvelle dépense",
                    leading = DsTopBarLeading.Back(onBack)
                )
                StepIndicator(currentStep = 1)
                Spacer(Modifier.height(DsSpacing.md))

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
                ) {
                    FixedInfoField(label = "Type de charge", icon = type?.icon ?: "category", value = type?.name ?: "")
                    FixedInfoField(label = "Sous-type de charge", icon = subType?.icon ?: "category", value = subType?.name ?: "")

                    DsFormField(
                        label = "Montant", value = formMontant,
                        onValueChange = { viewModel.setFormMontant(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
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
                            label = "Fournisseur / Station", value = formFournisseur,
                            onValueChange = { viewModel.setFormFournisseur(it) }, placeholder = "Ex: Station Naftal - Hydra"
                        )
                    }

                    DsFormField(
                        label = "Notes (optionnel)", value = formNote, onValueChange = { viewModel.setFormNote(it) },
                        minLines = 2, maxLines = 3
                    )
                    Spacer(Modifier.height(DsSpacing.sm))
                }

                Button(
                    onClick  = {
                        val v = formMontant.replace(",", ".").toDoubleOrNull()
                        if (v == null || v <= 0.0) montantError = "Montant invalide" else navController.navigate(Screen.ChargesFormSummary.route)
                    },
                    modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
                    shape    = DsShapes.medium,
                    colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) {
                    Text("Suivant", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = formDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let {
                                viewModel.setFormDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                            }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annuler") } }
                ) { DatePicker(state = datePickerState) }
            }

            if (showTimePicker) {
                val timePickerState = rememberTimePickerState(
                    initialHour = formTime.hour, initialMinute = formTime.minute, is24Hour = true
                )
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    title = { Text("Sélectionner l'heure") },
                    text = { TimePicker(state = timePickerState) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.setFormTime(LocalTime.of(timePickerState.hour, timePickerState.minute))
                            showTimePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Annuler") } },
                    containerColor    = DsColors.Surface,
                    titleContentColor = DsColors.TextPrimary,
                    textContentColor  = DsColors.TextSecondary
                )
            }
        }

        composable(Screen.ChargesFormSummary.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val subtypeIdArg = parentEntry.arguments!!.getInt("subtypeId")
            val chargeIdArg = parentEntry.arguments?.getInt("chargeId")?.takeIf { it != -1 }
            val isEdit = chargeIdArg != null
            val subTypes by viewModel.subTypes.collectAsState()
            val chargeTypes by viewModel.chargeTypes.collectAsState()
            val subType = subTypes.find { it.id == subtypeIdArg }
            val type = chargeTypes.find { it.id == subType?.type_id }

            val formMontant by viewModel.formMontant.collectAsState()
            val formDate by viewModel.formDate.collectAsState()
            val formTime by viewModel.formTime.collectAsState()
            val formFournisseur by viewModel.formFournisseur.collectAsState()
            val formNote by viewModel.formNote.collectAsState()
            var isSaving by remember { mutableStateOf(false) }

            val isoDateTime = formDate.atTime(formTime).atZone(ZoneId.systemDefault()).toInstant().toString()

            fun doSave() {
                val montantValue = formMontant.replace(",", ".").toDoubleOrNull()
                if (montantValue == null || montantValue <= 0.0) {
                    // Defensive re-check — unreachable in practice since Details already
                    // validates before allowing navigation here, matching the original.
                    return
                }
                isSaving = true
                val fournisseurValue = if (subType?.has_fournisseur == true) formFournisseur.trim().ifEmpty { null } else null
                val noteValue = formNote.trim().ifEmpty { null }

                if (isEdit) {
                    viewModel.updateCharge(
                        id = chargeIdArg!!, subtypeId = subtypeIdArg, montant = montantValue,
                        dateTime = isoDateTime, fournisseur = fournisseurValue, note = noteValue,
                        onSuccess = onSaved, onError = { isSaving = false }
                    )
                } else {
                    viewModel.addCharge(
                        subtypeId = subtypeIdArg, montant = montantValue, dateTime = isoDateTime,
                        fournisseur = fournisseurValue, note = noteValue,
                        onSuccess = onSaved, onError = { isSaving = false }
                    )
                }
            }

            BackHandler { navController.popBackStack() }

            Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                DsTopAppBar(
                    title   = if (isEdit) "Modifier la dépense" else "Nouvelle dépense",
                    leading = DsTopBarLeading.Back({ navController.popBackStack() })
                )
                StepIndicator(currentStep = 2)
                Spacer(Modifier.height(DsSpacing.md))

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
                ) {
                    Text("Vérifiez les informations", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                    SummaryRow("Type de charge", type?.name ?: "")
                    SummaryRow("Sous-type de charge", subType?.name ?: "")
                    SummaryRow("Montant", "${formMontant.ifBlank { "0" }} DA", highlight = true)
                    SummaryRow("Date", "${formatOrderDate(isoDateTime)} à ${formatOrderTime(isoDateTime)}")
                    if (subType?.has_fournisseur == true) {
                        SummaryRow("Fournisseur / Station", formFournisseur.ifBlank { "—" })
                    }
                    SummaryRow("Notes", formNote.ifBlank { "Aucun" })
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)
                ) {
                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = DsShapes.medium
                    ) { Text("Modifier") }

                    Button(
                        onClick  = { doSave() },
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
    }
}
