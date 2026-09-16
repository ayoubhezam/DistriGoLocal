package com.distrigo.app.data.repository

import com.distrigo.app.data.model.defaultChargeSubTypeUuid
import com.distrigo.app.data.model.defaultChargeTypeUuid
import com.distrigo.app.data.local.dao.ChargeDao
import com.distrigo.app.data.local.entity.ChargeEntity
import com.distrigo.app.data.local.entity.ChargeSubTypeEntity
import com.distrigo.app.data.local.entity.ChargeTypeEntity
import com.distrigo.app.data.model.Charge
import com.distrigo.app.data.model.ChargeSubType
import com.distrigo.app.data.model.ChargeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChargeRepository(
    private val chargeDao: ChargeDao
) {
    // ── Mapping ──
    private fun ChargeTypeEntity.toChargeType(subtypesCount: Int = 0, totalThisMonth: Double = 0.0) = ChargeType(
        id = this.id, name = this.name, icon = this.icon, color_hex = this.color_hex,
        is_default = this.is_default, subtypes_count = subtypesCount, total_this_month = totalThisMonth
    )

    private fun ChargeSubTypeEntity.toChargeSubType(expensesCount: Int = 0, totalThisMonth: Double = 0.0) = ChargeSubType(
        id = this.id, type_id = this.type_id, name = this.name, icon = this.icon,
        has_fournisseur = this.has_fournisseur, is_default = this.is_default,
        expenses_count = expensesCount, total_this_month = totalThisMonth
    )

    private fun ChargeEntity.toCharge() = Charge(
        id = this.id, type_id = this.type_id, type_name = this.type_name,
        subtype_id = this.subtype_id, subtype_name = this.subtype_name,
        montant = this.montant, date_time = this.date_time,
        fournisseur = this.fournisseur, note = this.note, created_at = this.created_at
    )

    private fun currentMonth(): String = java.time.LocalDate.now().toString().take(7) // "yyyy-MM"

    // ── Seed Data (فئات افتراضية) ──
    // Each built-in type and subtype has a stable key, from which every phone derives the same uuid
    // (see defaultTypeUuid). Never change a key: it is that type's identity.
    private data class SeedSubType(val key: String, val name: String, val icon: String, val hasFournisseur: Boolean)
    private data class SeedType(val key: String, val name: String, val icon: String, val colorHex: String, val subtypes: List<SeedSubType>)

    private val DEFAULT_CHARGE_TYPES = listOf(
        SeedType("vehicule", "Véhicule", "directions_car", "#3F51B5", listOf(
            SeedSubType("carburant", "Carburant", "local_gas_station", true),
            SeedSubType("pneus", "Pneus", "trip_origin", true),
            SeedSubType("reparation", "Réparation", "build", true),
            SeedSubType("vidange", "Vidange", "oil_barrel", true),
            SeedSubType("assurance", "Assurance", "shield", true)
        )),
        SeedType("personnel", "Personnel", "people", "#4CAF50", listOf(
            SeedSubType("salaire", "Salaire", "payments", false),
            SeedSubType("prime", "Prime", "card_giftcard", false),
            SeedSubType("formation", "Formation", "school", false)
        )),
        SeedType("bureau", "Bureau", "apartment", "#FF9800", listOf(
            SeedSubType("loyer", "Loyer", "home", false),
            SeedSubType("electricite", "Électricité", "bolt", false),
            SeedSubType("internet", "Internet", "wifi", false),
            SeedSubType("fournitures", "Fournitures", "inventory", false)
        )),
        SeedType("distribution", "Distribution", "local_shipping", "#009688", listOf(
            SeedSubType("peage", "Péage", "toll", false),
            SeedSubType("parking", "Parking", "local_parking", false),
            SeedSubType("livraison", "Livraison", "local_shipping", false),
            SeedSubType("emballage", "Emballage", "inventory_2", false)
        )),
        SeedType("achats", "Achats", "shopping_cart", "#F44336", listOf(
            SeedSubType("materiel", "Matériel", "build", false),
            SeedSubType("nettoyage", "Nettoyage", "cleaning_services", false),
            SeedSubType("divers", "Divers", "category", false)
        )),
        SeedType("divers", "Divers", "more_horiz", "#9E9E9E", listOf(
            SeedSubType("imprevu", "Imprévu", "warning", false),
            SeedSubType("autre", "Autre", "more_horiz", false)
        ))
    )

    suspend fun seedDefaultChargeTypesIfNeeded() {
        if (chargeDao.getAllChargeTypes().isNotEmpty()) return
        val now = java.time.Instant.now().toString()
        for (seedType in DEFAULT_CHARGE_TYPES) {
            val typeId = chargeDao.insertChargeType(
                ChargeTypeEntity(
                    name = seedType.name, icon = seedType.icon,
                    color_hex = seedType.colorHex, is_default = true, created_at = now,
                    uuid = defaultChargeTypeUuid(seedType.key)
                )
            ).toInt()
            seedType.subtypes.forEach { sub ->
                chargeDao.insertSubType(
                    ChargeSubTypeEntity(
                        type_id = typeId, name = sub.name, icon = sub.icon,
                        has_fournisseur = sub.hasFournisseur, is_default = true, created_at = now,
                        uuid = defaultChargeSubTypeUuid(seedType.key, sub.key)
                    )
                )
            }
        }
    }

    // ── Charge Types ──
    // Two GROUP BY queries: subtype counts per type, and the month's total per type (by the charge's
    // own type_id, as before). This used to load every charge ever recorded, plus one query per type
    // just to count its subtypes. On Dispatchers.Default, like every Kotlin step after a query here.
    suspend fun getChargeTypesWithStats(month: String? = null): List<ChargeType> = withContext(Dispatchers.Default) {
        val types = chargeDao.getAllChargeTypes()
        val targetMonth = month ?: currentMonth()
        val subtypeCounts = chargeDao.getSubTypeCountsByType().associate { it.type_id to it.count }
        val (start, end) = monthRange(targetMonth)
        val monthTotals = chargeDao.getMonthTotalsByType(start, end).associate { it.type_id to it.total }
        types.map { type ->
            type.toChargeType(subtypeCounts[type.id] ?: 0, monthTotals[type.id] ?: 0.0)
        }
    }

    suspend fun addChargeType(name: String, icon: String, colorHex: String): Long {
        return chargeDao.insertChargeType(
            ChargeTypeEntity(
                name = name, icon = icon, color_hex = colorHex,
                is_default = false, created_at = java.time.Instant.now().toString()
            )
        )
    }

    suspend fun deleteChargeType(id: Int) {
        val type = chargeDao.getChargeTypeById(id) ?: return
        if (type.is_default) throw IllegalStateException("Impossible de supprimer un type par défaut")

        val subtypes = chargeDao.getSubTypesForType(id)
        val hasCharges = subtypes.any { chargeDao.getChargesForSubType(it.id).isNotEmpty() }
        if (hasCharges) {
            throw IllegalStateException("Impossible de supprimer : des dépenses existent déjà sous ce type")
        }

        subtypes.forEach { chargeDao.softDeleteSubTypeById(it.id) }
        chargeDao.softDeleteChargeTypeById(id)
    }

    // ── Charge SubTypes ──
    // One GROUP BY query for the month's count and total per subtype of this type. This used to run
    // one query per subtype, each loading that subtype's whole history.
    suspend fun getSubTypesWithStats(typeId: Int, month: String? = null): List<ChargeSubType> = withContext(Dispatchers.Default) {
        val subtypes = chargeDao.getSubTypesForType(typeId)
        val targetMonth = month ?: currentMonth()
        val (start, end) = monthRange(targetMonth)
        val stats = chargeDao.getMonthStatsBySubType(typeId, start, end).associateBy { it.subtype_id }
        subtypes.map { sub ->
            val s = stats[sub.id]
            sub.toChargeSubType(
                expensesCount   = s?.count ?: 0,
                totalThisMonth  = s?.total ?: 0.0
            )
        }
    }

    suspend fun addSubType(typeId: Int, name: String, icon: String, hasFournisseur: Boolean): Long {
        return chargeDao.insertSubType(
            ChargeSubTypeEntity(
                type_id = typeId, name = name, icon = icon,
                has_fournisseur = hasFournisseur, is_default = false,
                created_at = java.time.Instant.now().toString()
            )
        )
    }

    suspend fun deleteSubType(id: Int) {
        val sub = chargeDao.getSubTypeById(id) ?: return
        if (sub.is_default) throw IllegalStateException("Impossible de supprimer un sous-type par défaut")

        val charges = chargeDao.getChargesForSubType(id)
        if (charges.isNotEmpty()) {
            throw IllegalStateException("Impossible de supprimer : ${charges.size} dépense(s) enregistrée(s) sous ce sous-type")
        }
        chargeDao.softDeleteSubTypeById(id)
    }

    // ── Charges ──
    suspend fun getCharges(subtypeId: Int, month: String? = null): List<Charge> {
        val charges = if (month == null) {
            chargeDao.getChargesForSubType(subtypeId)
        } else {
            val (start, end) = monthRange(month)
            chargeDao.getChargesForSubTypeInRange(subtypeId, start, end)
        }
        return charges.map { it.toCharge() }
    }

    suspend fun addCharge(
        subtypeId: Int, montant: Double, dateTime: String,
        fournisseur: String?, note: String?
    ): Map<String, Any> {
        val subType = chargeDao.getSubTypeById(subtypeId)
            ?: throw IllegalStateException("Sous-type introuvable: $subtypeId")
        val type = chargeDao.getChargeTypeById(subType.type_id)
            ?: throw IllegalStateException("Type introuvable: ${subType.type_id}")

        chargeDao.insertCharge(
            ChargeEntity(
                type_id = type.id, type_name = type.name,
                subtype_id = subType.id, subtype_name = subType.name,
                montant = montant, date_time = dateTime,
                fournisseur = if (subType.has_fournisseur) fournisseur else null,
                note = note, created_at = java.time.Instant.now().toString()
            )
        )
        return mapOf("message" to "Dépense ajoutée avec succès")
    }

    suspend fun updateCharge(
        id: Int, montant: Double, dateTime: String,
        fournisseur: String?, note: String?
    ): Map<String, Any> {
        val existing = chargeDao.getChargeById(id)
            ?: throw IllegalStateException("Dépense introuvable: $id")
        chargeDao.updateCharge(
            existing.copy(montant = montant, date_time = dateTime, fournisseur = fournisseur, note = note)
        )
        return mapOf("message" to "Dépense mise à jour avec succès")
    }

    suspend fun deleteCharge(id: Int): Map<String, Any> {
        chargeDao.deleteChargeById(id)
        return mapOf("message" to "Dépense supprimée avec succès")
    }

}
