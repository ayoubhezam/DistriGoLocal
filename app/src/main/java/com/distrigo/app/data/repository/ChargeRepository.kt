package com.distrigo.app.data.repository

import com.distrigo.app.data.local.dao.ChargeDao
import com.distrigo.app.data.local.entity.ChargeEntity
import com.distrigo.app.data.local.entity.ChargeSubTypeEntity
import com.distrigo.app.data.local.entity.ChargeTypeEntity
import com.distrigo.app.data.model.Charge
import com.distrigo.app.data.model.ChargeSubType
import com.distrigo.app.data.model.ChargeType

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
    private data class SeedSubType(val name: String, val icon: String, val hasFournisseur: Boolean)
    private data class SeedType(val name: String, val icon: String, val colorHex: String, val subtypes: List<SeedSubType>)

    private val DEFAULT_CHARGE_TYPES = listOf(
        SeedType("Véhicule", "directions_car", "#3F51B5", listOf(
            SeedSubType("Carburant", "local_gas_station", true),
            SeedSubType("Pneus", "trip_origin", true),
            SeedSubType("Réparation", "build", true),
            SeedSubType("Vidange", "oil_barrel", true),
            SeedSubType("Assurance", "shield", true)
        )),
        SeedType("Personnel", "people", "#4CAF50", listOf(
            SeedSubType("Salaire", "payments", false),
            SeedSubType("Prime", "card_giftcard", false),
            SeedSubType("Formation", "school", false)
        )),
        SeedType("Bureau", "apartment", "#FF9800", listOf(
            SeedSubType("Loyer", "home", false),
            SeedSubType("Électricité", "bolt", false),
            SeedSubType("Internet", "wifi", false),
            SeedSubType("Fournitures", "inventory", false)
        )),
        SeedType("Distribution", "local_shipping", "#009688", listOf(
            SeedSubType("Péage", "toll", false),
            SeedSubType("Parking", "local_parking", false),
            SeedSubType("Livraison", "local_shipping", false),
            SeedSubType("Emballage", "inventory_2", false)
        )),
        SeedType("Achats", "shopping_cart", "#F44336", listOf(
            SeedSubType("Matériel", "build", false),
            SeedSubType("Nettoyage", "cleaning_services", false),
            SeedSubType("Divers", "category", false)
        )),
        SeedType("Divers", "more_horiz", "#9E9E9E", listOf(
            SeedSubType("Imprévu", "warning", false),
            SeedSubType("Autre", "more_horiz", false)
        ))
    )

    suspend fun seedDefaultChargeTypesIfNeeded() {
        if (chargeDao.getAllChargeTypes().isNotEmpty()) return
        val now = java.time.Instant.now().toString()
        for (seedType in DEFAULT_CHARGE_TYPES) {
            val typeId = chargeDao.insertChargeType(
                ChargeTypeEntity(
                    name = seedType.name, icon = seedType.icon,
                    color_hex = seedType.colorHex, is_default = true, created_at = now
                )
            ).toInt()
            seedType.subtypes.forEach { sub ->
                chargeDao.insertSubType(
                    ChargeSubTypeEntity(
                        type_id = typeId, name = sub.name, icon = sub.icon,
                        has_fournisseur = sub.hasFournisseur, is_default = true, created_at = now
                    )
                )
            }
        }
    }

    // ── Charge Types ──
    suspend fun getChargeTypesWithStats(month: String? = null): List<ChargeType> {
        val types = chargeDao.getAllChargeTypes()
        val allCharges = chargeDao.getAllCharges()
        val targetMonth = month ?: currentMonth()
        return types.map { type ->
            val subtypesCount = chargeDao.getSubTypesForType(type.id).size
            val monthTotal = allCharges
                .filter { it.type_id == type.id && it.date_time.take(7) == targetMonth }
                .sumOf { it.montant }
            type.toChargeType(subtypesCount, monthTotal)
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

        subtypes.forEach { chargeDao.deleteSubTypeById(it.id) }
        chargeDao.deleteChargeTypeById(id)
    }

    // ── Charge SubTypes ──
    suspend fun getSubTypesWithStats(typeId: Int, month: String? = null): List<ChargeSubType> {
        val subtypes = chargeDao.getSubTypesForType(typeId)
        val targetMonth = month ?: currentMonth()
        return subtypes.map { sub ->
            val monthCharges = chargeDao.getChargesForSubType(sub.id)
                .filter { it.date_time.take(7) == targetMonth }
            sub.toChargeSubType(
                expensesCount   = monthCharges.size,
                totalThisMonth  = monthCharges.sumOf { it.montant }
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
        chargeDao.deleteSubTypeById(id)
    }

    // ── Charges ──
    suspend fun getCharges(subtypeId: Int, month: String? = null): List<Charge> {
        val charges = chargeDao.getChargesForSubType(subtypeId)
        return (if (month != null) charges.filter { it.date_time.take(7) == month } else charges)
            .map { it.toCharge() }
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

    // ── لاستخدام Rapports لاحقاً ──
    suspend fun getMonthlyTotal(month: String): Double {
        return chargeDao.getAllCharges().filter { it.date_time.take(7) == month }.sumOf { it.montant }
    }
}