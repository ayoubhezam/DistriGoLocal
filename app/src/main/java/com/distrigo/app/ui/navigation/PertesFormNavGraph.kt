package com.distrigo.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.pertes.FixedInfoField
import com.distrigo.app.ui.pertes.PerteViewModel
import com.distrigo.app.ui.pertes.ProductPickerDialog
import com.distrigo.app.ui.pertes.StepIndicator
import com.distrigo.app.ui.pertes.SummaryRow
import com.distrigo.app.ui.pertes.formatQty
import com.distrigo.app.ui.purchases.formatOrderDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// Mirrors venteFormGraph/purchaseFormGraph/retourClientFormGraph: form state (product/quantity/
// source/date/motif/userName/photo) lives on the PertesGraph-scoped PerteViewModel instance —
// the same one PertesHome/PertesList already share. See
// PerteViewModel.formProduct/formQuantity/formSource/.../formSaveError.
//
// The product picker is a Compose Dialog() overlay (not a full-screen swap) in the original, so
// it stays local UI state inside the Details step rather than becoming its own destination —
// unlike the client/supplier picker *screens* in the other migrated wizards.
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.pertesFormGraph(
    navController : NavHostController,
    graphRoute    : String,
    viewModel     : @Composable () -> PerteViewModel,
    onBack  : () -> Unit,
    onSaved : () -> Unit
) {
    navigation(
        startDestination = Screen.PertesFormDetails.route,
        route = graphRoute,
        arguments = listOf(
            navArgument("typeId")  { type = NavType.IntType },
            navArgument("perteId") { type = NavType.IntType; defaultValue = -1 }
        )
    ) {
        composable(Screen.PertesFormDetails.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val typeIdArg = parentEntry.arguments!!.getInt("typeId")
            val perteIdArg = parentEntry.arguments?.getInt("perteId")?.takeIf { it != -1 }
            val isEdit = perteIdArg != null
            val perteTypes by viewModel.perteTypes.collectAsState()
            val products by viewModel.products.collectAsState()
            val pertes by viewModel.pertes.collectAsState()
            val editingPerte = perteIdArg?.let { id -> pertes.find { it.id == id } }
            val type = perteTypes.find { it.id == typeIdArg }

            val formProduct by viewModel.formProduct.collectAsState()
            val formQuantity by viewModel.formQuantity.collectAsState()
            val formSource by viewModel.formSource.collectAsState()
            val formDate by viewModel.formDate.collectAsState()
            val formMotif by viewModel.formMotif.collectAsState()
            val formUserName by viewModel.formUserName.collectAsState()
            val formSaveError by viewModel.formSaveError.collectAsState()
            var quantityError by remember { mutableStateOf("") }
            var showProductPicker by remember { mutableStateOf(false) }
            var showDatePicker by remember { mutableStateOf(false) }

            var initialized by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!initialized) {
                    viewModel.resetPerteForm()
                    initialized = true
                }
            }
            // Prefill once from the editing Perte — mirrors the original's one-shot
            // `remember { mutableStateOf(perte?.x ?: ...) }` capture (not re-applied if the
            // pertes list re-emits later). userName is intentionally NOT seeded here, matching
            // the original, which never carried it over in edit mode either.
            var prefillDone by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(editingPerte) {
                if (isEdit && editingPerte != null && !prefillDone) {
                    viewModel.setFormProduct(products.find { it.id == editingPerte.product_id })
                    viewModel.setFormQuantity(editingPerte.quantity)
                    viewModel.setFormSource(editingPerte.source)
                    viewModel.setFormDate(
                        editingPerte.date_time?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now()
                    )
                    viewModel.setFormMotif(editingPerte.motif ?: "")
                    viewModel.setFormPhotoBase64(editingPerte.photo_path)
                    prefillDone = true
                }
            }

            val isoDateTime = formDate.atStartOfDay(ZoneOffset.UTC).toInstant().toString()

            BackHandler { onBack() }

            Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                DsTopAppBar(
                    title   = if (isEdit) "Modifier la perte" else "Nouvelle perte",
                    leading = DsTopBarLeading.Back(onBack)
                )
                StepIndicator(1)
                Spacer(Modifier.height(DsSpacing.md))

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = DsSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
                ) {
                    FixedInfoField(label = "Type de perte", icon = type?.icon ?: "category", value = type?.name ?: "")

                    // ── Source du stock ──
                    Column {
                        Text("Source du stock", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.SurfaceSunken).padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf("depot" to "Dépôt", "camion" to "Camion").forEach { (value, label) ->
                                val active = formSource == value
                                Box(
                                    modifier = Modifier.weight(1f).clip(DsShapes.small)
                                        .background(if (active) DsColors.Primary else Color.Transparent)
                                        .clickable { viewModel.setFormSource(value) }
                                        .padding(vertical = DsSpacing.sm),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = if (active) Color.White else DsColors.TextSecondary)
                                }
                            }
                        }
                    }

                    // ── Produit ──
                    Column {
                        Text("Produit *", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                        Surface(
                            shape = DsShapes.medium, color = DsColors.SurfaceSunken,
                            modifier = Modifier.fillMaxWidth().clickable { showProductPicker = true }
                        ) {
                            Row(
                                Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    formProduct?.name ?: "Sélectionner un produit",
                                    fontSize = DsTextSize.body,
                                    color = if (formProduct != null) DsColors.TextPrimary else DsColors.TextTertiary
                                )
                                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    // ── Quantité + Unité (ثابتة من المنتج) ──
                    Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                        Column(Modifier.weight(1f)) {
                            Text("Quantité *", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.SurfaceSunken),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(onClick = { if (formQuantity > 0) { viewModel.setFormQuantity(formQuantity - 1); quantityError = "" } }) {
                                    Icon(Icons.Default.Remove, contentDescription = null, tint = DsColors.TextPrimary)
                                }
                                Text(formatQty(formQuantity), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                                IconButton(onClick = { viewModel.setFormQuantity(formQuantity + 1); quantityError = "" }) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.TextPrimary)
                                }
                            }
                            if (quantityError.isNotEmpty()) {
                                Text(quantityError, fontSize = DsTextSize.caption, color = DsColors.Danger, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Unité", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                            Surface(shape = DsShapes.medium, color = DsColors.SurfaceSunken, modifier = Modifier.fillMaxWidth()) {
                                Box(Modifier.padding(DsSpacing.md), contentAlignment = Alignment.CenterStart) {
                                    Text(formProduct?.unit_type ?: "—", fontSize = DsTextSize.body, color = DsColors.TextPrimary)
                                }
                            }
                        }
                    }

                    // ── Date ──
                    Column {
                        Text("Date", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth(), shape = DsShapes.medium) {
                            Text(formatOrderDate(isoDateTime), color = DsColors.TextPrimary)
                        }
                    }

                    // ── Motif ──
                    Column {
                        Text("Motif (optionnel)", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                        OutlinedTextField(
                            value = formMotif, onValueChange = { viewModel.setFormMotif(it) },
                            placeholder = { Text("Ex: Cartons abîmés, chute, casse...") },
                            modifier = Modifier.fillMaxWidth(), shape = DsShapes.medium, minLines = 2, maxLines = 3,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )
                    }

                    Spacer(Modifier.height(DsSpacing.md))
                    Column {
                        Text("Effectué par (optionnel)", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
                        OutlinedTextField(
                            value = formUserName, onValueChange = { viewModel.setFormUserName(it) },
                            placeholder = { Text("Nom du responsable") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(), shape = DsShapes.medium, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )
                    }

                    if (formSaveError.isNotEmpty()) {
                        Text(formSaveError, color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                    }
                    Spacer(Modifier.height(DsSpacing.sm))
                }

                Button(
                    onClick = {
                        var valid = true
                        if (formProduct == null) { viewModel.setFormSaveError("Sélectionnez un produit"); valid = false } else viewModel.setFormSaveError("")
                        if (formQuantity <= 0) { quantityError = "Quantité invalide"; valid = false }
                        if (valid) navController.navigate(Screen.PertesFormSummary.route)
                    },
                    modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg).height(52.dp),
                    shape = DsShapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                ) { Text("Suivant", color = Color.White, fontWeight = FontWeight.SemiBold) }
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

            if (showProductPicker) {
                ProductPickerDialog(
                    products  = products,
                    onSelect  = { viewModel.setFormProduct(it); showProductPicker = false },
                    onDismiss = { showProductPicker = false }
                )
            }
        }

        composable(Screen.PertesFormSummary.route) { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(graphRoute) }
            val viewModel = viewModel()
            val typeIdArg = parentEntry.arguments!!.getInt("typeId")
            val perteIdArg = parentEntry.arguments?.getInt("perteId")?.takeIf { it != -1 }
            val isEdit = perteIdArg != null
            val perteTypes by viewModel.perteTypes.collectAsState()
            val type = perteTypes.find { it.id == typeIdArg }

            val formProduct by viewModel.formProduct.collectAsState()
            val formQuantity by viewModel.formQuantity.collectAsState()
            val formSource by viewModel.formSource.collectAsState()
            val formDate by viewModel.formDate.collectAsState()
            val formMotif by viewModel.formMotif.collectAsState()
            val formUserName by viewModel.formUserName.collectAsState()
            val formPhotoBase64 by viewModel.formPhotoBase64.collectAsState()
            val formSaveError by viewModel.formSaveError.collectAsState()
            var isSaving by remember { mutableStateOf(false) }

            val isoDateTime = formDate.atStartOfDay(ZoneOffset.UTC).toInstant().toString()
            val valeurEstimee = (formProduct?.purchase_price ?: 0.0) * formQuantity

            fun doSave() {
                val product = formProduct
                if (product == null || formQuantity <= 0) {
                    viewModel.setFormSaveError(if (product == null) "Sélectionnez un produit" else "Quantité invalide")
                    navController.popBackStack()
                    return
                }
                isSaving = true
                if (isEdit) {
                    viewModel.updatePerte(
                        id = perteIdArg!!, typeId = typeIdArg, productId = product.id, quantity = formQuantity, source = formSource,
                        dateTime = isoDateTime, motif = formMotif.trim().ifEmpty { null }, photoPath = formPhotoBase64,
                        onSuccess = onSaved,
                        onError   = { msg -> isSaving = false; viewModel.setFormSaveError(msg); navController.popBackStack() }
                    )
                } else {
                    viewModel.addPerte(
                        typeId = typeIdArg, productId = product.id, quantity = formQuantity, source = formSource,
                        dateTime = isoDateTime, motif = formMotif.trim().ifEmpty { null }, photoPath = formPhotoBase64,
                        onSuccess = onSaved,
                        onError   = { msg -> isSaving = false; viewModel.setFormSaveError(msg); navController.popBackStack() }
                    )
                }
            }

            BackHandler { navController.popBackStack() }

            Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
                DsTopAppBar(
                    title   = if (isEdit) "Modifier la perte" else "Nouvelle perte",
                    leading = DsTopBarLeading.Back({ navController.popBackStack() })
                )
                StepIndicator(2)
                Spacer(Modifier.height(DsSpacing.md))

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
                ) {
                    Text("Vérifiez les informations", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
                    SummaryRow("Type de perte", type?.name ?: "")
                    SummaryRow("Produit", formProduct?.name ?: "")
                    SummaryRow("Quantité", "${formatQty(formQuantity)} ${formProduct?.unit_type ?: ""}")
                    SummaryRow("Source", if (formSource == "camion") "Camion" else "Dépôt")
                    SummaryRow("Valeur totale", "${"%,.0f".format(valeurEstimee)} DA", highlight = true)
                    SummaryRow("Date", formatOrderDate(isoDateTime))
                    SummaryRow("Motif", formMotif.ifBlank { "Aucun" })
                    if (formSaveError.isNotEmpty()) {
                        Text(formSaveError, color = DsColors.Danger, fontSize = DsTextSize.bodySmall)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), horizontalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                    OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.weight(1f).height(52.dp), shape = DsShapes.medium) {
                        Text("Modifier")
                    }
                    Button(
                        onClick = { doSave() }, enabled = !isSaving,
                        modifier = Modifier.weight(1f).height(52.dp), shape = DsShapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
                    ) {
                        if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("Enregistrer", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
