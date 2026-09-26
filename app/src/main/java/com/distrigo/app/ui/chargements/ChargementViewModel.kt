package com.distrigo.app.ui.chargements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.ChargementDraft
import com.distrigo.app.data.repository.ChargementDraftRepository
import com.distrigo.app.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChargementViewModel @Inject constructor(
    private val repository     : ProductRepository,
    private val draftRepository: ChargementDraftRepository
) : ViewModel() {

    // ── Brouillons ───────────────────────────────────────────────────────────
    //
    // Listed drafts only. The repository's query excludes every row belonging to the
    // single-product "Modifier" card, so that card's private editing state can never reach a
    // screen — it is durable, not visible.

    val drafts: StateFlow<List<ChargementDraft>> =
        draftRepository.observeDrafts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val draftCount: StateFlow<Int> =
        draftRepository.observeCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun deleteDraft(id: Int) {
        viewModelScope.launch { draftRepository.delete(id) }
    }

    /** Bulk delete for the Brouillons screen's selection mode. One statement, one invalidation. */
    fun deleteDrafts(ids: Collection<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { draftRepository.deleteAll(ids.toList()) }
    }

    // ── The single-product card's private editing state ──────────────────────
    //
    // Durable but invisible: these rows carry `single_product_id`, which is exactly what the
    // Brouillons query excludes. They exist so a crash, a flat battery or a swipe-away cannot lose
    // what someone typed into the Modifier card, not so it can be resumed from a list.

    /** The pending edit for a product, restored silently when its card is reopened. */
    suspend fun productDraft(productId: Int): ChargementDraft? =
        draftRepository.draftForProduct(productId)

    /**
     * Keeps that pending edit in step with the card, and returns the row id it now owns.
     *
     * [isDirty] false means the card has been brought back to where it started — a zero delta and
     * both fields empty — so any row is deleted rather than kept. A pending edit that would restore
     * nothing is worse than none: it would survive a crash only to put the user back exactly where
     * they already were, while occupying the product's one unique slot.
     */
    suspend fun saveProductDraft(
        draftId : Int?,
        product : com.distrigo.app.data.model.Product,
        target  : Double,
        note    : String,
        userName: String,
        isDirty : Boolean
    ): Int? {
        if (!isDirty) {
            draftId?.let { draftRepository.delete(it) }
            return null
        }
        return draftRepository.upsert(
            draftId,
            com.distrigo.app.data.model.ChargementDraftSnapshot(
                singleProductId = product.id,
                lines = listOf(
                    com.distrigo.app.data.model.ChargementDraftLine(
                        product_id    = product.id,
                        product_name  = product.name,
                        unit_type     = product.unit_type,
                        target_camion = target
                    )
                ),
                note     = note,
                userName = userName
            )
        )
    }

    /**
      * Called when the user chooses to leave and discard rather than save.
      *
      * Launched on [viewModelScope], not the caller's: the screen calls this and then immediately
      * navigates away, and a scope tied to that screen would be cancelled before the delete ran —
      * leaving behind exactly the pending edit the user just asked to throw away.
      */
    fun discardProductDraft(productId: Int) {
        viewModelScope.launch { draftRepository.deleteForProduct(productId) }
    }

    // No chargement list, no sessions and no form state here any more. Nothing on screen showed the
    // list or the sessions, yet `init` read every chargement and then each one's lines, one query each,
    // whenever this ViewModel was created - opening Stock Camion or a chargement screen started 4,224
    // queries over 369,564 lines on the test data. The form's state lives on
    // ChargementFormSessionViewModel.

    fun createChargement(
        note      : String?,
        userName  : String? = null,
        items     : List<Map<String, Any?>>,
        /** The draft this movement was composed in, deleted inside the same transaction. */
        draftId   : Int? = null,
        onSuccess : () -> Unit,
        onError   : (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.createChargement(note, items, userName, draftId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Erreur inconnue")
            }
        }
    }
}
