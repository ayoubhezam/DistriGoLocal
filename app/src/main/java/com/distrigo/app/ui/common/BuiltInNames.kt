package com.distrigo.app.ui.common

import com.distrigo.app.R
import com.distrigo.app.core.locale.AppLanguage
import com.distrigo.app.core.text.AppStrings
import com.distrigo.app.core.text.UiText
import com.distrigo.app.data.model.DefaultPerteType
import com.distrigo.app.data.model.RetourClientMotifs
import com.distrigo.app.data.model.RetourFournisseurMotifs
import com.distrigo.app.data.model.defaultChargeSubTypeUuid
import com.distrigo.app.data.model.defaultChargeTypeUuid
import com.distrigo.app.data.repository.ChargeRepository
import java.util.Locale

/**
 * The built-in perte and charge types and the retour motifs in the app's language (D7). A type is recognised by its
 * uuid, which every phone derives from the same key (defaultTypeUuid), and shown translated only while it keeps the
 * name it was seeded with: a type the user renamed is their word, shown as they wrote it. A motif is stored as its
 * French sentence and recognised by it. What is stored never changes.
 */
object BuiltInNames {

    private class Seeded(val name: String, val key: String)

    private val types: Map<String, Seeded> by lazy {
        buildMap {
            DefaultPerteType.entries.forEach { put(it.uuid, Seeded(it.seedName, "perte_${it.key}")) }
            ChargeRepository.DEFAULT_CHARGE_TYPES.forEach { type ->
                put(defaultChargeTypeUuid(type.key), Seeded(type.name, "charge_${type.key}"))
                type.subtypes.forEach { sub ->
                    put(defaultChargeSubTypeUuid(type.key, sub.key), Seeded(sub.name, "charge_${type.key}_${sub.key}"))
                }
            }
        }
    }

    /** A perte type's, charge type's or charge subtype's [name], given its [uuid]. */
    fun type(uuid: String?, name: String): String {
        if (!arabic()) return name
        val seeded = types[uuid]?.takeIf { it.name == name } ?: return name
        return written(TYPE_IDS[seeded.key]) ?: name
    }

    /** A retour motif, stored as its French sentence. */
    fun motif(motif: String): String =
        if (!arabic()) motif else written(MOTIF_IDS[motif]) ?: motif

    private fun written(id: Int?): String? = id?.let { AppStrings.write(UiText.Res(it)) }

    private fun arabic(): Boolean = AppLanguage.of(Locale.getDefault()) == AppLanguage.ARABIC

    private val TYPE_IDS = mapOf(
        "perte_casse" to R.string.builtin_perte_casse,
        "perte_peremption" to R.string.builtin_perte_peremption,
        "perte_vol" to R.string.builtin_perte_vol,
        "perte_perte_transport" to R.string.builtin_perte_transport,
        "perte_don" to R.string.builtin_perte_don,
        "perte_autre" to R.string.builtin_autre,
        "charge_vehicule" to R.string.builtin_charge_vehicule,
        "charge_vehicule_carburant" to R.string.builtin_charge_carburant,
        "charge_vehicule_pneus" to R.string.builtin_charge_pneus,
        "charge_vehicule_reparation" to R.string.builtin_charge_reparation,
        "charge_vehicule_vidange" to R.string.builtin_charge_vidange,
        "charge_vehicule_assurance" to R.string.builtin_charge_assurance,
        "charge_personnel" to R.string.builtin_charge_personnel,
        "charge_personnel_salaire" to R.string.builtin_charge_salaire,
        "charge_personnel_prime" to R.string.builtin_charge_prime,
        "charge_personnel_formation" to R.string.builtin_charge_formation,
        "charge_bureau" to R.string.builtin_charge_bureau,
        "charge_bureau_loyer" to R.string.builtin_charge_loyer,
        "charge_bureau_electricite" to R.string.builtin_charge_electricite,
        "charge_bureau_internet" to R.string.builtin_charge_internet,
        "charge_bureau_fournitures" to R.string.builtin_charge_fournitures,
        "charge_distribution" to R.string.builtin_charge_distribution,
        "charge_distribution_peage" to R.string.builtin_charge_peage,
        "charge_distribution_parking" to R.string.builtin_charge_parking,
        "charge_distribution_livraison" to R.string.builtin_charge_livraison,
        "charge_distribution_emballage" to R.string.builtin_charge_emballage,
        "charge_achats" to R.string.builtin_charge_achats,
        "charge_achats_materiel" to R.string.builtin_charge_materiel,
        "charge_achats_nettoyage" to R.string.builtin_charge_nettoyage,
        "charge_achats_divers" to R.string.builtin_divers,
        "charge_divers" to R.string.builtin_divers,
        "charge_divers_imprevu" to R.string.builtin_charge_imprevu,
        "charge_divers_autre" to R.string.builtin_autre,
    )

    private val MOTIF_IDS: Map<String, Int> by lazy {
        val clientIds = listOf(
            R.string.builtin_motif_defectueux, R.string.builtin_motif_perime, R.string.builtin_motif_erreur_livraison,
            R.string.builtin_motif_client_insatisfait, R.string.builtin_autre,
        )
        val supplierIds = listOf(
            R.string.builtin_motif_defectueux_repris, R.string.builtin_motif_defectueux_refuse,
            R.string.builtin_motif_perime_repris, R.string.builtin_motif_perime_refuse,
            R.string.builtin_motif_erreur_commande, R.string.builtin_motif_excedent, R.string.builtin_autre,
        )
        RetourClientMotifs.ALL.map { it.id }.zip(clientIds).toMap() +
            RetourFournisseurMotifs.ALL.map { it.id }.zip(supplierIds).toMap()
    }

    /** For a test: the built-ins with no translation, and whether every motif has one. */
    internal fun untranslatedTypes(): List<String> = types.values.map { it.key }.filter { it !in TYPE_IDS }
    internal fun motifCount(): Int = MOTIF_IDS.size
}
