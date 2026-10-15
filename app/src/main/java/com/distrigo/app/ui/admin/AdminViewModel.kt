package com.distrigo.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.license.AdminAnswer
import com.distrigo.app.data.license.ClientBusiness
import com.distrigo.app.data.license.ClientOutcome
import com.distrigo.app.data.license.BatchDeletion
import com.distrigo.app.data.license.BatchDetails
import com.distrigo.app.data.license.CodeBatch
import com.distrigo.app.data.license.CodeOrder
import com.distrigo.app.data.license.CodeState
import com.distrigo.app.data.license.CodesAdminClient
import com.distrigo.app.data.license.LicenseManager
import com.distrigo.app.data.license.NewCode
import com.distrigo.app.data.license.heldLicense
import com.distrigo.app.ui.onboarding.CODE_LENGTH
import com.distrigo.app.ui.onboarding.formatCode
import com.distrigo.app.ui.onboarding.isWellFormedCode
import com.distrigo.app.ui.onboarding.normalizeCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The codes just made: the only time they show, since the server keeps their fingerprints alone. */
data class CreatedBatch(val order: CodeOrder, val codes: List<NewCode>, val shared: Boolean = false)

/** « Vérifier un code »: the typing, then what the server said, then perhaps the revocation. */
data class CodeCheck(
    val error: String? = null,
    /** XXXX-XXXX-XXXX, once found. */
    val code: String? = null,
    val state: CodeState? = null,
    val confirmRevoke: Boolean = false,
)

enum class AdminWork { LOADING, CREATING, CHECKING, REVOKING, READING_BATCH, SEARCHING, CHANGING }

/** What the owner is about to do to a batch, once confirmed. */
enum class BatchAction { REVOKE_FREE, DELETE }

/** What the owner is about to do to a client's account, once confirmed. */
enum class ClientAction { SUSPEND, REACTIVATE, DELETE }

data class AdminUiState(
    /** Null until read. */
    val batches: List<CodeBatch>? = null,
    val problem: String? = null,
    val notAllowed: Boolean = false,
    val work: AdminWork? = null,
    val createError: String? = null,
    val created: CreatedBatch? = null,
    val check: CodeCheck? = null,
    val message: String? = null,
    /** The batch opened from « Lots », its details once read, its action awaiting confirmation, what went wrong. */
    val openedBatch: CodeBatch? = null,
    val batchDetails: BatchDetails? = null,
    val batchAction: BatchAction? = null,
    val batchError: String? = null,
    // « Comptes clients »
    /** Null until searched. */
    val clients: List<ClientBusiness>? = null,
    /** What [clients] answers; null for the latest. */
    val clientsQuery: String? = null,
    val clientsProblem: String? = null,
    /** The client opened, its action awaiting confirmation, and what went wrong with it. */
    val selectedClient: ClientBusiness? = null,
    val clientAction: ClientAction? = null,
    val clientError: String? = null,
    /** The owner's own business: never suspended nor deleted from here. */
    val ownBusinessId: String? = null,
)

/**
 * The owner's « Administration » (docs/activation_codes.md, step C4). Scoped to its own screen's entry, so a
 * merchant opening Compte never asks codes-admin anything.
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val client: CodesAdminClient,
    license: LicenseManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState(ownBusinessId = license.state.value.heldLicense?.businessId))
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = work(AdminWork.LOADING) { readBatches() }

    // ── A new batch ──

    fun create(order: CodeOrder) {
        val name = order.batch.trim()
        val problem = when {
            name.isEmpty() -> "Donnez un nom au lot : le revendeur ou le client, et le mois."
            name.length > 80 -> "80 caractères au plus pour le nom du lot."
            (order.businessName?.trim()?.length ?: 0) > 120 -> "120 caractères au plus pour l'entreprise."
            else -> null
        }
        if (problem != null) return update { copy(createError = problem) }
        work(AdminWork.CREATING) {
            when (val answer = client.create(order.copy(batch = name))) {
                is AdminAnswer.Done -> {
                    update { copy(created = CreatedBatch(order.copy(batch = name), answer.value), createError = null) }
                    readBatches()
                }
                AdminAnswer.NotAllowed -> update { copy(createError = NOT_ALLOWED) }
                is AdminAnswer.Failed -> update { copy(createError = answer.message) }
            }
        }
    }

    fun dismissCreateError() = update { copy(createError = null) }

    /** Sent on WhatsApp or as PDF cards: leaving no longer needs a warning. */
    fun markShared() = update { copy(created = created?.copy(shared = true)) }

    /** Left the codes' screen: they are gone from the phone too. */
    fun forgetCreated() = update { copy(created = null) }

    // ── One code ──

    fun openCheck() = update { copy(check = CodeCheck()) }

    fun closeCheck() = update { copy(check = null) }

    fun checkCode(typed: String) {
        val code = normalizeCode(typed)
        if (!isWellFormedCode(code)) {
            val error = if (code.length < CODE_LENGTH) "Le code contient $CODE_LENGTH caractères." else TYPO
            return update { copy(check = CodeCheck(error = error)) }
        }
        work(AdminWork.CHECKING) {
            update {
                copy(
                    check = when (val answer = client.status(formatCode(code))) {
                        is AdminAnswer.Done -> CodeCheck(code = formatCode(code), state = answer.value)
                        AdminAnswer.NotAllowed -> CodeCheck(error = NOT_ALLOWED)
                        is AdminAnswer.Failed -> CodeCheck(error = answer.message)
                    },
                )
            }
        }
    }

    fun askRevoke() = update { copy(check = check?.copy(confirmRevoke = true)) }

    fun cancelRevoke() = update { copy(check = check?.copy(confirmRevoke = false)) }

    fun revoke() {
        val code = _state.value.check?.code ?: return
        work(AdminWork.REVOKING) {
            when (val answer = client.revoke(code, reason = "Révoqué depuis l'application")) {
                is AdminAnswer.Done -> {
                    val now = (client.status(code) as? AdminAnswer.Done)?.value
                    update {
                        copy(
                            check = CodeCheck(code = code, state = now ?: check?.state),
                            message = if (answer.value) "Le code $code est révoqué." else "Ce code n'existe pas.",
                        )
                    }
                    readBatches()
                }
                AdminAnswer.NotAllowed -> update { copy(check = check?.copy(confirmRevoke = false, error = NOT_ALLOWED)) }
                is AdminAnswer.Failed -> update { copy(check = check?.copy(confirmRevoke = false, error = answer.message)) }
            }
        }
    }

    fun dismissMessage() = update { copy(message = null) }

    // ── A batch ──

    fun openBatch(batch: CodeBatch) {
        update { copy(openedBatch = batch, batchDetails = null, batchAction = null, batchError = null) }
        readBatch(batch)
    }

    /** Its details again, after a read that failed. */
    fun retryBatch() = _state.value.openedBatch?.let(::readBatch)

    fun closeBatch() = update { copy(openedBatch = null, batchDetails = null, batchAction = null, batchError = null) }

    fun askBatch(action: BatchAction) = update { copy(batchAction = action, batchError = null) }

    fun cancelBatchAction() = update { copy(batchAction = null) }

    /** The confirmed action: its free codes revoked, or the batch deleted (refused while a code opens a business). */
    fun applyBatchAction() {
        val batch = _state.value.openedBatch ?: return
        val action = _state.value.batchAction ?: return
        work(AdminWork.CHANGING) {
            when (action) {
                BatchAction.REVOKE_FREE -> when (val answer = client.revokeBatch(batch, reason = "Lot révoqué depuis l'application")) {
                    is AdminAnswer.Done -> {
                        val revoked = answer.value ?: return@work batchGone(batch)
                        update { copy(batchAction = null, message = "${codes(revoked)} ${if (revoked > 1) "révoqués" else "révoqué"} dans « ${batch.name} ».") }
                        readBatches()
                        // The batch as it now is
                        (client.batch(batch) as? AdminAnswer.Done)?.value?.let { now -> update { copy(openedBatch = now.batch, batchDetails = now) } }
                    }
                    AdminAnswer.NotAllowed -> update { copy(batchAction = null, batchError = NOT_ALLOWED) }
                    is AdminAnswer.Failed -> update { copy(batchAction = null, batchError = answer.message) }
                }
                BatchAction.DELETE -> when (val answer = client.deleteBatch(batch)) {
                    is AdminAnswer.Done -> when (val deletion = answer.value) {
                        is BatchDeletion.Deleted -> {
                            update {
                                copy(
                                    openedBatch = null, batchDetails = null, batchAction = null,
                                    message = "Le lot « ${batch.name} » est supprimé (${codes(deletion.codes)}).",
                                )
                            }
                            readBatches()
                        }
                        is BatchDeletion.InUse -> update { copy(batchAction = null, batchError = inUse(deletion.keys)) }
                        BatchDeletion.Unknown -> batchGone(batch)
                    }
                    AdminAnswer.NotAllowed -> update { copy(batchAction = null, batchError = NOT_ALLOWED) }
                    is AdminAnswer.Failed -> update { copy(batchAction = null, batchError = answer.message) }
                }
            }
        }
    }

    private fun readBatch(batch: CodeBatch) = work(AdminWork.READING_BATCH) {
        when (val answer = client.batch(batch)) {
            is AdminAnswer.Done -> answer.value?.let { details -> update { copy(openedBatch = details.batch, batchDetails = details) } }
                ?: batchGone(batch)
            AdminAnswer.NotAllowed -> update { copy(batchError = NOT_ALLOWED) }
            is AdminAnswer.Failed -> update { copy(batchError = answer.message) }
        }
    }

    /** Deleted meanwhile (another phone, Studio): the dialog closes, and the list shows what there is. */
    private suspend fun batchGone(batch: CodeBatch) {
        update { copy(openedBatch = null, batchDetails = null, batchAction = null, message = "Le lot « ${batch.name} » n'existe plus.") }
        readBatches()
    }

    // ── « Comptes clients » ──

    private var search: Job? = null

    /**
     * A name, a phone, an e-mail or a code; empty, the latest clients. Searched as the owner types: a newer search
     * replaces one still running, so the last letters typed are never lost. Not while something else is under way.
     */
    fun searchClients(query: String) {
        val busy = _state.value.work
        if (busy != null && busy != AdminWork.SEARCHING) return
        search?.cancel()
        update { copy(work = AdminWork.SEARCHING, message = null) }
        val job = viewModelScope.launch { readClients(query) }
        search = job
        // Only the latest search ends the work: a replaced one finishing late must not
        job.invokeOnCompletion { if (search === job) update { copy(work = null) } }
    }

    fun openClient(business: ClientBusiness) = update { copy(selectedClient = business, clientAction = null, clientError = null) }

    fun closeClient() = update { copy(selectedClient = null, clientAction = null, clientError = null) }

    fun askClient(action: ClientAction) = update { copy(clientAction = action, clientError = null) }

    fun cancelClientAction() = update { copy(clientAction = null) }

    /** The confirmed action; [note] goes with a suspension (« Impayé »). */
    fun applyClientAction(note: String?) {
        val business = _state.value.selectedClient ?: return
        val action = _state.value.clientAction ?: return
        work(AdminWork.CHANGING) {
            val answer = when (action) {
                ClientAction.SUSPEND -> client.setSuspended(business.id, suspended = true, note = note)
                ClientAction.REACTIVATE -> client.setSuspended(business.id, suspended = false, note = note)
                ClientAction.DELETE -> client.deleteBusiness(business.id)
            }
            when (answer) {
                is AdminAnswer.Done -> when (answer.value) {
                    ClientOutcome.DONE -> {
                        readClients(_state.value.clientsQuery)
                        update {
                            copy(
                                selectedClient = if (action == ClientAction.DELETE) null else clients?.firstOrNull { it.id == business.id } ?: business,
                                clientAction = null,
                                message = when (action) {
                                    ClientAction.SUSPEND -> "« ${business.name} » est suspendu."
                                    ClientAction.REACTIVATE -> "« ${business.name} » est réactivé."
                                    ClientAction.DELETE -> "« ${business.name} » est supprimé."
                                },
                            )
                        }
                    }
                    ClientOutcome.OWN_BUSINESS -> update { copy(clientAction = null, clientError = OWN_BUSINESS) }
                    ClientOutcome.UNKNOWN -> {
                        readClients(_state.value.clientsQuery)
                        update { copy(selectedClient = null, clientAction = null, message = "Ce client n'existe plus.") }
                    }
                }
                AdminAnswer.NotAllowed -> update { copy(clientAction = null, clientError = NOT_ALLOWED) }
                is AdminAnswer.Failed -> update { copy(clientAction = null, clientError = answer.message) }
            }
        }
    }

    private suspend fun readClients(query: String?) {
        when (val answer = client.businesses(query)) {
            is AdminAnswer.Done -> update {
                copy(clients = answer.value, clientsQuery = query?.trim()?.ifEmpty { null }, clientsProblem = null)
            }
            AdminAnswer.NotAllowed -> update { copy(notAllowed = true) }
            is AdminAnswer.Failed -> update { copy(clientsProblem = answer.message) }
        }
    }

    private suspend fun readBatches() {
        when (val answer = client.batches()) {
            is AdminAnswer.Done -> update { copy(batches = answer.value, problem = null, notAllowed = false) }
            AdminAnswer.NotAllowed -> update { copy(notAllowed = true) }
            is AdminAnswer.Failed -> update { copy(problem = answer.message) }
        }
    }

    /** One request at a time; whatever ends it says so in [state]. */
    private fun work(kind: AdminWork, block: suspend () -> Unit) {
        if (_state.value.work != null) return
        // A new step: the last one's message has been read
        update { copy(work = kind, message = if (kind == AdminWork.LOADING) message else null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                update { copy(work = null) }
            }
        }
    }

    private inline fun update(change: AdminUiState.() -> AdminUiState) {
        _state.value = _state.value.change()
    }

    private companion object {
        const val NOT_ALLOWED = "Ce compte n'a pas accès à l'administration."
        const val OWN_BUSINESS = "C'est votre propre entreprise : elle ne peut être ni suspendue ni supprimée ici."
        const val TYPO = "Ce code contient une faute de frappe. Vérifiez chaque caractère."
    }
}
