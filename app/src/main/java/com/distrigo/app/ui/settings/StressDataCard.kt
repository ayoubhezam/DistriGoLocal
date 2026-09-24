package com.distrigo.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.debug.StressDataGenerator
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where a stress-data run has got to. */
sealed interface StressDataState {
    data object Idle : StressDataState
    data class Running(val step: String, val fraction: Float) : StressDataState
    data class Done(val seconds: Long) : StressDataState
    data class Failed(val message: String) : StressDataState
}

/** Runs [StressDataGenerator]. Debug builds only — see [StressDataCard]. */
@HiltViewModel
class StressDataViewModel @Inject constructor(private val db: AppDatabase) : ViewModel() {

    private val _state = MutableStateFlow<StressDataState>(StressDataState.Idle)
    val state: StateFlow<StressDataState> = _state.asStateFlow()

    fun generate() {
        if (_state.value is StressDataState.Running) return
        _state.value = StressDataState.Running("Démarrage…", 0f)
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            _state.value = try {
                StressDataGenerator(db).generate { _state.value = StressDataState.Running(it.step, it.fraction) }
                StressDataState.Done((System.currentTimeMillis() - startedAt) / 1000)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                StressDataState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }
}

/**
 * The generator's button, shown in Paramètres **only in debug builds**.
 *
 * Asks first, because what it adds cannot be taken back one row at a time: 5,000 products, 500
 * clients, 3,000 purchases, 4,000 tournées with about 80,000 sales, returns, charges and pertes, numbered and
 * in the stock ledger like real ones.
 */
@Composable
fun StressDataCard(viewModel: StressDataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var confirming by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.DangerLight)
            .clickable(enabled = state !is StressDataState.Running) { confirming = true }
            .padding(DsSpacing.lg),
    ) {
        Text("DEBUG · Générer des données de test", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.Danger)
        Text(
            when (val s = state) {
                StressDataState.Idle       -> "Cinq ans : 5 000 produits, 500 clients, 3 000 achats, 4 000 tournées de 15 à 25 ventes (dont 10 reçus de 80 à 100 lignes), 6 750 retours, 7 500 charges, 2 000 pertes"
                is StressDataState.Running -> "${s.step} — ne quittez pas cet écran"
                is StressDataState.Done    -> "Terminé en ${s.seconds} s"
                is StressDataState.Failed  -> "Échec : ${s.message}"
            },
            fontSize = DsTextSize.caption,
            color = DsColors.TextSecondary,
        )
        (state as? StressDataState.Running)?.let { running ->
            Spacer(Modifier.height(DsSpacing.sm))
            LinearProgressIndicator(progress = { running.fraction }, modifier = Modifier.fillMaxWidth())
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Générer des données de test ?") },
            text = {
                Text(
                    "Ajoute cinq ans d'activité : 5 000 produits, 500 clients, 3 000 achats, 4 000 tournées fermées avec environ 80 000 ventes, 6 750 retours, 7 500 charges et 2 000 pertes. " +
                        "Ils ne peuvent pas être retirés en une fois. Comptez une dizaine de minutes, téléphone branché, sans quitter cet écran."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = false; viewModel.generate() }) { Text("Générer") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Annuler") }
            },
        )
    }
}
