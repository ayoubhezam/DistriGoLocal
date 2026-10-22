package com.distrigo.app.ui.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.entity.incentive.CommissionPeriodEntity
import com.distrigo.app.data.local.entity.incentive.IncentiveType
import com.distrigo.app.data.local.entity.incentive.CalculationSource
import com.distrigo.app.data.model.incentive.GoalProgress
import com.distrigo.app.data.model.incentive.GoalStep
import com.distrigo.app.data.repository.IncentiveRepository
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** « Mon objectif » as the screen shows it; [loaded] false until read once. [progress] is null without a policy. */
data class MonObjectifState(
    val loaded: Boolean = false,
    val progress: GoalProgress? = null,
    /** The product a « Gratuité » gives, by name. */
    val freeGood: String? = null,
    /** The ended periods, latest first. */
    val history: List<CommissionPeriodEntity> = emptyList(),
    /** The period in steps, for « Rythme hebdomadaire ». */
    val steps: List<GoalStep> = emptyList(),
)

/**
 * « Mon objectif » (Part 2, Step 6): the active policy's progress and the frozen periods, read again each time a
 * sale, a payment, a return or the policy is written — as the Dashboard's card is.
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonObjectifViewModel @Inject constructor(
    private val incentives: IncentiveRepository,
    private val products: ProductRepository,
    reports: ReportRepository,
) : ViewModel() {

    val state: StateFlow<MonObjectifState> = reports.goalChanges().debounce(400).onStart { emit(Unit) }
        .mapLatest { read() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), MonObjectifState())

    /** « Ajuster mon objectif » (single-target policies only); [onDone] once saved, the screen rereads by itself. */
    fun saveObjective(target: Double, source: CalculationSource, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { incentives.saveObjective(target, source) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "Enregistrement impossible") }
        }
    }

    private suspend fun read(): MonObjectifState {
        val progress = runCatching { incentives.progress() }.getOrNull()
        val steps = progress?.let { runCatching { incentives.steps(it) }.getOrNull() }.orEmpty()
        val freeGood = progress?.rules?.takeIf { it.type == IncentiveType.FREE_GOODS }?.freeGoodProductId
            ?.let { id -> runCatching { products.getLiveProduct(id)?.name }.getOrNull() }
        val history = runCatching { incentives.history() }.getOrDefault(emptyList())
        return MonObjectifState(loaded = true, progress = progress, freeGood = freeGood, history = history, steps = steps)
    }
}
