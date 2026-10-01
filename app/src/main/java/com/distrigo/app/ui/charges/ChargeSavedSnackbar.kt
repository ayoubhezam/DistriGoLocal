package com.distrigo.app.ui.charges

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.distrigo.app.ui.format.LocalMoneyFormatter

/**
 * "Dépense de 3 500 DA enregistrée — Annuler", on the screen the expense form returns to.
 *
 * This is the form's safety net: it used to be a summary step to confirm before saving, which every
 * entry paid for. Now the entry is saved at once and a mistake is one tap to take back.
 */
@Composable
fun ChargeSavedSnackbar(viewModel: ChargeViewModel, modifier: Modifier = Modifier) {
    val money = LocalMoneyFormatter.current
    val saved by viewModel.lastSaved.collectAsState()
    val host = remember { SnackbarHostState() }

    LaunchedEffect(saved) {
        val charge = saved ?: return@LaunchedEffect
        val result = host.showSnackbar(
            message     = "Dépense de ${money.da(charge.montant)} enregistrée",
            actionLabel = "Annuler",
            duration    = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoLastSaved() else viewModel.dismissLastSaved()
    }

    SnackbarHost(hostState = host, modifier = modifier)
}
