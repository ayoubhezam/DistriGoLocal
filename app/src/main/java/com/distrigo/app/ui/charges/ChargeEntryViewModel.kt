package com.distrigo.app.ui.charges

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.Amount
import com.distrigo.app.data.model.Charge
import com.distrigo.app.data.model.ChargeSubType
import com.distrigo.app.data.model.ChargeType
import com.distrigo.app.data.repository.ChargeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * One pass through the expense form: a single screen, opened three ways —
 *
 *  - from a subtype's list ("Ajouter une dépense"): the subtype is given;
 *  - from the Charges home, the quick entry: the subtype is picked on the form, starting on the one
 *    used last;
 *  - on an existing charge: everything is filled in from it.
 *
 * Everything it shows is loaded by id, so it does not depend on which lists the Charges screens
 * happen to hold — the quick entry opens with none of them loaded.
 */
@HiltViewModel
class ChargeEntryViewModel @Inject constructor(
    private val repository: ChargeRepository,
    savedState: SavedStateHandle
) : ViewModel() {

    // Room ids start at 1: a missing argument is -1.
    private val subtypeArg: Int? = savedState.get<Int>(ARG_SUBTYPE)?.takeIf { it > 0 }
    val chargeId: Int? = savedState.get<Int>(ARG_CHARGE)?.takeIf { it > 0 }
    val isEdit: Boolean get() = chargeId != null

    /** The quick entry: no subtype was given, so the form offers them. */
    val choosesSubtype: Boolean = subtypeArg == null && chargeId == null

    var loaded by mutableStateOf(false); private set
    var subTypes by mutableStateOf<List<ChargeSubType>>(emptyList()); private set
    private var types by mutableStateOf<Map<Int, ChargeType>>(emptyMap())
    private var editing by mutableStateOf<Charge?>(null)

    var subtypeId by mutableStateOf<Int?>(null); private set
    /** The amount as typed, sanitized (Amount.sanitizeInput): "3500.5". */
    var amount by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    var time by mutableStateOf(nowToTheMinute())
    var fournisseur by mutableStateOf("")
    var note by mutableStateOf("")
    var noteOpen by mutableStateOf(false)

    var recentAmounts by mutableStateOf<List<Double>>(emptyList()); private set
    var recentSuppliers by mutableStateOf<List<String>>(emptyList()); private set

    var saving by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    private var original: Snapshot? = null

    val subType: ChargeSubType? get() = subTypes.find { it.id == subtypeId }

    /** "Carburant · Gasoil" — from the charge itself when its subtype has since been deleted. */
    val subtitle: String
        get() {
            val sub = subType
            val typeName = sub?.let { types[it.type_id]?.name } ?: editing?.type_name
            val subName = sub?.name ?: editing?.subtype_name
            return listOfNotNull(typeName, subName).joinToString(" · ")
        }

    val showsSupplier: Boolean get() = subType?.has_fournisseur == true || (subType == null && !editing?.fournisseur.isNullOrBlank())

    val amountValue: Double? get() = Amount.parse(amount)?.takeIf { it > 0.0 }

    val canSave: Boolean get() = loaded && !saving && amountValue != null && (isEdit || subtypeId != null)

    /** Something typed that leaving would lose. */
    val isDirty: Boolean
        get() = original?.let { it != snapshot() }
            ?: (amount.isNotEmpty() || fournisseur.isNotBlank() || note.isNotBlank())

    init {
        viewModelScope.launch {
            types = repository.getChargeTypes().associateBy { it.id }
            subTypes = repository.getAllSubTypes()
            val charge = chargeId?.let { repository.getCharge(it) }
            editing = charge
            if (charge != null) {
                amount = BigDecimal.valueOf(Amount.normalize(charge.montant)).stripTrailingZeros().toPlainString()
                val at = runCatching { Instant.parse(charge.date_time).atZone(ZoneId.systemDefault()) }.getOrNull()
                date = at?.toLocalDate() ?: LocalDate.now()
                time = at?.toLocalTime()?.withSecond(0)?.withNano(0) ?: nowToTheMinute()
                fournisseur = charge.fournisseur ?: ""
                note = charge.note ?: ""
                noteOpen = note.isNotBlank()
            }
            val start = charge?.subtype_id ?: subtypeArg ?: repository.getLastUsedSubtypeId() ?: subTypes.firstOrNull()?.id
            selectSubtype(start)
            if (charge != null) original = snapshot()
            loaded = true
        }
    }

    fun selectSubtype(id: Int?) {
        subtypeId = id
        if (id == null) { recentAmounts = emptyList(); recentSuppliers = emptyList(); return }
        viewModelScope.launch {
            recentAmounts = repository.getRecentAmounts(id)
            recentSuppliers = repository.getRecentSuppliers(id)
        }
    }

    fun onAmountChange(raw: String) { amount = Amount.sanitizeInput(raw); error = null }

    fun useAmount(value: Double) {
        amount = BigDecimal.valueOf(Amount.normalize(value)).stripTrailingZeros().toPlainString()
        error = null
    }

    fun save(onSaved: (SavedCharge) -> Unit) {
        val montant = amountValue ?: run { error = "Saisissez un montant supérieur à zéro."; return }
        val sub = subtypeId
        if (!isEdit && sub == null) { error = "Choisissez une catégorie."; return }
        saving = true
        error = null
        val dateTime = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toString()
        val supplier = fournisseur.trim().ifEmpty { null }.takeIf { showsSupplier }
        val noteValue = note.trim().ifEmpty { null }
        viewModelScope.launch {
            try {
                val saved = if (isEdit) {
                    repository.updateCharge(chargeId!!, montant, dateTime, supplier, noteValue)
                    val charge = editing!!
                    SavedCharge(chargeId, charge.subtype_id, charge.type_id, montant, isNew = false)
                } else {
                    val id = repository.addCharge(sub!!, montant, dateTime, supplier, noteValue)
                    SavedCharge(id, sub, subType?.type_id, montant, isNew = true)
                }
                onSaved(saved)
            } catch (e: Exception) {
                // Said, next to the button: a refused save used to leave only a spinner that stopped.
                error = e.message ?: "L'enregistrement a échoué."
                saving = false
            }
        }
    }

    fun delete(onDeleted: (SavedCharge) -> Unit) {
        val charge = editing ?: return
        saving = true
        viewModelScope.launch {
            try {
                repository.deleteCharge(charge.id)
                onDeleted(SavedCharge(charge.id, charge.subtype_id, charge.type_id, charge.montant, isNew = false))
            } catch (e: Exception) {
                error = e.message ?: "La suppression a échoué."
                saving = false
            }
        }
    }

    private data class Snapshot(val amount: Double?, val date: LocalDate, val time: LocalTime, val fournisseur: String, val note: String)

    private fun snapshot() = Snapshot(Amount.parse(amount), date, time, fournisseur.trim(), note.trim())

    private fun nowToTheMinute(): LocalTime = LocalTime.now().withSecond(0).withNano(0)

    companion object {
        const val ARG_SUBTYPE = "subtypeId"
        const val ARG_CHARGE = "chargeId"
    }
}

/** A charge just written, for the list it returns to: its "Annuler", and what to reload. */
data class SavedCharge(
    val id: Int,
    val subtypeId: Int,
    val typeId: Int?,
    val montant: Double,
    val isNew: Boolean
)
