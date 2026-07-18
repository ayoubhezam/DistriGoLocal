package com.distrigo.app.data.repository

import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.incentive.CalculationSource
import com.distrigo.app.data.local.entity.incentive.IncentiveType
import com.distrigo.app.data.model.incentive.DistributorPerformance
import com.distrigo.app.data.model.incentive.RewardResult
import kotlin.math.roundToInt
import androidx.room.withTransaction
class IncentiveRepository(private val db: AppDatabase) {

    private val policyDao = db.targetPolicyDao()

    suspend fun calculateDistributorReward(
        distributorId: Long?,
        periodStartIso: String,
        periodEndIso: String
    ): RewardResult {
        val policy = policyDao.getActivePolicy(distributorId)
            ?: return RewardResult.NoPolicyConfigured
        val policyWithTiers = policyDao.getPolicyWithTiers(policy.id)
            ?: return RewardResult.NoPolicyConfigured

        val performance = getDistributorPerformance(periodStartIso, periodEndIso)
        val actualValue = when (policy.calculation_source) {
            CalculationSource.INVOICED_SALES -> performance.netSales
            CalculationSource.COLLECTED_CASH -> performance.collectedCash
        }

        val achievedPercent = if (policy.target_value == 0.0) 0
        else ((actualValue / policy.target_value) * 100).roundToInt()

        return when (policy.incentive_type) {
            IncentiveType.FIXED_RATE -> {
                if (actualValue < policy.target_value) RewardResult.NotAchieved(achievedPercent)
                else RewardResult.CashReward(
                    amount = actualValue * (policy.reward_rate ?: 0.0) / 100,
                    achievedPercent = achievedPercent
                )
            }
            IncentiveType.FIXED_BONUS -> {
                if (actualValue < policy.target_value) RewardResult.NotAchieved(achievedPercent)
                else RewardResult.CashReward(
                    amount = policy.fixed_bonus_amount ?: 0.0,
                    achievedPercent = achievedPercent
                )
            }
            IncentiveType.FREE_GOODS -> {
                if (actualValue < policy.target_value) RewardResult.NotAchieved(achievedPercent)
                else RewardResult.FreeGoodsReward(
                    productId = policy.free_good_product_id ?: 0,
                    quantity = policy.free_good_quantity ?: 0,
                    achievedPercent = achievedPercent
                )
            }
            IncentiveType.PROGRESSIVE_TIERS -> {
                val tier = policyWithTiers.tiers
                    .filter { actualValue >= it.min_threshold }
                    .maxByOrNull { it.min_threshold }
                    ?: return RewardResult.NotAchieved(achievedPercent)

                val amount = tier.fixed_bonus
                    ?: (actualValue * (tier.reward_rate ?: 0.0) / 100)

                RewardResult.CashReward(
                    amount = amount,
                    achievedPercent = achievedPercent,
                    sourceTierOrder = tier.tier_order
                )
            }
        }
    }

    /** يُستخدم أيضًا مباشرة من Tableau de bord لعرض Objectif/Réalisé بلا حاجة لحساب المكافأة كاملة */
    suspend fun getActiveTargetValue(distributorId: Long? = null): Double? {
        return policyDao.getActivePolicy(distributorId)?.target_value
    }

    private suspend fun getDistributorPerformance(
        periodStartIso: String,
        periodEndIso: String
    ): DistributorPerformance {
        val ventes = db.venteDao().getVentesBySourceBetween("camion", periodStartIso, periodEndIso)
        val netSales = ventes.sumOf { it.total }
        // Recouvrement = المبالغ المدفوعة عند البيع + أي دفعات منفصلة لاحقة خلال نفس الفترة
        val paidAtSale = ventes.sumOf { it.montant_paye }
        val separatePayments = db.clientPaymentDao().getPaymentsBetween(periodStartIso, periodEndIso).sumOf { it.amount }

        return DistributorPerformance(
            netSales = netSales,
            collectedCash = paidAtSale + separatePayments
        )
    }

    suspend fun getActivePolicyWithTiers(distributorId: Long? = null): com.distrigo.app.data.local.dao.incentive.PolicyWithTiers? {
        val policy = policyDao.getActivePolicy(distributorId) ?: return null
        return policyDao.getPolicyWithTiers(policy.id)
    }

    suspend fun savePolicy(
        policy: com.distrigo.app.data.local.entity.incentive.TargetPolicyEntity,
        tiers: List<com.distrigo.app.data.local.entity.incentive.PolicyTierEntity>
    ) {
        db.withTransaction {
            policyDao.deactivateExistingPolicies(policy.distributor_id)
            val policyId = policyDao.insertPolicy(policy)
            if (tiers.isNotEmpty()) {
                policyDao.insertTiers(tiers.map { it.copy(policy_id = policyId) })
            }
        }
    }


}