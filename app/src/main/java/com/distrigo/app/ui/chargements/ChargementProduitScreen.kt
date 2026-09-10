package com.distrigo.app.ui.chargements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.model.Product
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.dsTextFieldColors

/**
 * Moving one product between the dépôt and the camion, on one screen.
 *
 * ### What this replaces
 *
 * "Modifier" on a Stock camion product used to mount the whole `ChargementNavHost` for a single
 * row. That host starts at the product catalogue and then jumped straight to the cart with
 * `popUpTo(products, inclusive = false)` — deliberately leaving the catalogue on the back stack.
 * The result was a two-step wizard to change one number, and a Back that landed on a list nobody
 * had asked to see. There is no list here and no cart: the card *is* the screen.
 *
 * ### Nothing is written until the button
 *
 * The quantity, the note and "Effectué par" are local state. [ChargementViewModel.createChargement]
 * is called once, from "Enregistrer le mouvement", and only for a non-zero delta — the same rule
 * the cart screen applies, so a save that changes nothing writes nothing.
 *
 * ### Leaving with unsaved work
 *
 * Both ways out — the system back gesture and the bar's back arrow — go through [attemptBack].
 * Guarding one and not the other would be worse than guarding neither, because the unguarded one
 * is the one that eventually gets used. The warning appears only when there is something to lose:
 * a changed quantity, or text in either field. Backing out of an untouched card just leaves.
 */
@Composable
fun ChargementProduitScreen(
    product  : Product,
    viewModel: ChargementViewModel = hiltViewModel(),
    onBack   : () -> Unit,
    onSaved  : () -> Unit
) {
    // Seeded from the product's current camion stock, so an untouched card has a zero delta and
    // "Aucun changement" on it — the same starting point the cart row shows.
    var targetCamion by remember(product.id) { mutableStateOf(product.camion_stock) }
    var note         by remember(product.id) { mutableStateOf("") }
    var userName     by remember(product.id) { mutableStateOf("") }
    var isSaving     by remember { mutableStateOf(false) }
    var showDiscard  by remember { mutableStateOf(false) }
    var saveError    by remember { mutableStateOf("") }

    val delta = targetCamion - product.camion_stock

    // Anything that would be written counts, not just the quantity: a note typed and then lost to
    // a back gesture is the same broken promise as a quantity typed and lost.
    val isDirty = delta != 0.0 || note.isNotBlank() || userName.isNotBlank()

    fun attemptBack() {
        if (isDirty) showDiscard = true else onBack()
    }

    BackHandler { attemptBack() }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            containerColor   = DsColors.Surface,
            title = {
                Text("Quitter sans enregistrer ?", fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            },
            text = {
                Text(
                    "Les informations modifiées ne seront pas enregistrées.",
                    fontSize = DsTextSize.bodySmall,
                    color    = DsColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; onBack() }) {
                    Text("Quitter", color = DsColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                // Named for what it does to the *back*, not to the edit: this button is how you
                // stay and carry on.
                TextButton(onClick = { showDiscard = false }) {
                    Text("Annuler", color = DsColors.TextSecondary)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(
            title    = "Mouvement de stock",
            subtitle = product.name,
            leading  = DsTopBarLeading.Back { attemptBack() }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            // The same card the cart uses, opened: one product, one stepper, the dépôt and camion
            // previews it already computes. Removing it is meaningless here — there is no
            // selection to remove it from — so onRemove is the same as leaving.
            ChargementCartRow(
                item              = ChargementCartItem(product = product, targetCamion = targetCamion),
                onQuantityChange  = { targetCamion = it.coerceAtLeast(0.0) },
                onRemove          = { attemptBack() },
                initiallyExpanded = true
            )

            OutlinedTextField(
                value         = userName,
                onValueChange = { userName = it },
                placeholder   = { Text("Effectué par (optionnel)", fontSize = DsTextSize.body) },
                leadingIcon   = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier      = Modifier.fillMaxWidth(),
                shape         = DsShapes.medium,
                singleLine    = true,
                colors = dsTextFieldColors(
                    unfocusedBorderColor = DsColors.Border,
                    focusedBorderColor   = DsColors.Primary
                )
            )

            OutlinedTextField(
                value         = note,
                onValueChange = { note = it },
                placeholder   = { Text("Note (optionnel)", fontSize = DsTextSize.body) },
                modifier      = Modifier.fillMaxWidth(),
                shape         = DsShapes.medium,
                minLines      = 2,
                colors = dsTextFieldColors(
                    unfocusedBorderColor = DsColors.Border,
                    focusedBorderColor   = DsColors.Primary
                )
            )

            if (saveError.isNotEmpty()) {
                Text(saveError, fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
            }
        }

        Button(
            onClick = {
                if (delta == 0.0) return@Button
                isSaving  = true
                saveError = ""
                viewModel.createChargement(
                    note     = note.trim().ifEmpty { null },
                    userName = userName.trim().ifEmpty { null },
                    items    = listOf(
                        mapOf(
                            "product_id" to product.id,
                            "quantity"   to kotlin.math.abs(delta),
                            "direction"  to if (delta > 0) "vers_camion" else "vers_depot"
                        )
                    ),
                    onSuccess = { onSaved() },
                    onError   = { isSaving = false; saveError = it }
                )
            },
            // A zero delta has nothing to write, so the button says so by being dead rather than
            // by silently doing nothing — which is what the cart screen does today.
            enabled  = delta != 0.0 && !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md)
                .height(52.dp),
            shape    = DsShapes.medium,
            colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    "Enregistrer le mouvement",
                    fontSize   = DsTextSize.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White
                )
            }
        }
    }
}
