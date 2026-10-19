package com.distrigo.app.data.repository

import androidx.room.withTransaction
import com.distrigo.app.data.local.dao.incentive.PolicyWithTiers
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.LicenseWriteGate
import com.distrigo.app.data.local.entity.incentive.CalculationSource
import com.distrigo.app.data.local.entity.incentive.CommissionPeriodEntity
import com.distrigo.app.data.local.entity.incentive.PolicyTierEntity
import com.distrigo.app.data.local.entity.incentive.TargetPolicyEntity
import com.distrigo.app.data.model.incentive.CommissionPeriod
import com.distrigo.app.data.model.incentive.CommissionRules
import com.distrigo.app.data.model.incentive.GoalProgress
import com.distrigo.app.data.model.incentive.RewardResult
import com.distrigo.app.data.model.incentive.TierRule
import com.distrigo.app.data.time.BusinessDates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The commission policy and where it stands (docs/design/dashboard_objectif_plan.md, Part 2).
 *
 * The measure is read with the reports' own rules, so the « Objectif » and the reports never disagree: every sale,
 * dépôt and tournée together (D9), on the local days of the period. « Ventes facturées » is their total less the
 * client returns dated in the period (D13); « Recouvrement » what was paid at the sale plus the separate payments.
 */
class IncentiveRepository(private val db: AppDatabase) {

    private val policyDao = db.targetPolicyDao()
    private val reports = db.reportDao()

    /**
     * The active policy's progress over the period [today] falls in, or null with no policy. The periods that ended
     * since it was saved are frozen first.
     */
    suspend fun progress(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): GoalProgress? {
        val policy = policyDao.getActivePolicy(null) ?: return null
        val rules = rulesOf(policy, policyDao.getTiersForPolicy(policy.id))
        freezeEndedPeriods(policy, rules, today, zone)

        val period = CommissionPeriod.containing(policy.period_type, today)
        val sales = salesIn(period, zone)
        return GoalProgress.of(
            rules       = rules,
            period      = period,
            today       = today,
            value       = measure(rules.source, period, zone, sales),
            salesCount  = sales.count,
            salesTotal  = sales.total,
            showReward  = policy.show_reward,
            policySince = sinceOf(policy, zone) ?: today,
        )
    }

    /** The ended periods, their results frozen, the latest first. */
    suspend fun history(): List<CommissionPeriodEntity> = policyDao.getPeriods()

    suspend fun getActivePolicyWithTiers(distributorId: Long? = null): PolicyWithTiers? {
        val policy = policyDao.getActivePolicy(distributorId) ?: return null
        return policyDao.getPolicyWithTiers(policy.id)
    }

    /**
     * Saves [policy] as the active one. The outgoing policy's ended periods are frozen first, so each period keeps
     * the rules it ran under.
     */
    suspend fun savePolicy(
        policy: TargetPolicyEntity,
        tiers: List<PolicyTierEntity>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        db.withTransaction {
            policyDao.getActivePolicy(policy.distributor_id)?.let { outgoing ->
                freezeEndedPeriods(outgoing, rulesOf(outgoing, policyDao.getTiersForPolicy(outgoing.id)), today, zone)
            }
            policyDao.deactivateExistingPolicies(policy.distributor_id)
            val policyId = policyDao.insertPolicy(policy)
            if (tiers.isNotEmpty()) {
                policyDao.insertTiers(tiers.map { it.copy(policy_id = policyId) })
            }
        }
    }

    /**
     * Freezes each period of [policy] that ended before [today] and is not frozen yet, back to the one it was saved
     * in, [MAX_FROZEN_AT_ONCE] at most. The walk stops at the first period already frozen: those before it were
     * frozen with it.
     */
    private suspend fun freezeEndedPeriods(policy: TargetPolicyEntity, rules: CommissionRules, today: LocalDate, zone: ZoneId) {
        val since = sinceOf(policy, zone) ?: return
        val closedAt = Instant.now().toString()
        var period = CommissionPeriod.containing(policy.period_type, today).previous()
        var frozen = 0
        while (frozen < MAX_FROZEN_AT_ONCE && !period.end.isBefore(since) &&
            policyDao.countPeriod(period.start.toString(), period.end.toString()) == 0
        ) {
            val value = measure(rules.source, period, zone, salesIn(period, zone))
            val reward = rules.rewardAt(value)
            // Figures already recorded, frozen: done even when the license is read-only (docs/license_l5_enforcement.md §3)
            LicenseWriteGate.allowing(db, LicenseWriteGate.Allowance.COMMISSION_FREEZE) {
                policyDao.insertPeriod(
                    CommissionPeriodEntity(
                        policy_uuid          = policy.uuid,
                        incentive_type       = policy.incentive_type,
                        period_type          = policy.period_type,
                        calculation_source   = policy.calculation_source,
                        period_start         = period.start.toString(),
                        period_end           = period.end.toString(),
                        achieved_value       = value,
                        objective            = rules.objective,
                        achieved_percent     = rules.percentOf(value),
                        reached              = reward !is RewardResult.NotAchieved,
                        reward_amount        = (reward as? RewardResult.CashReward)?.amount,
                        free_good_product_id = (reward as? RewardResult.FreeGoodsReward)?.productId,
                        free_good_quantity   = (reward as? RewardResult.FreeGoodsReward)?.quantity,
                        tier_order           = (reward as? RewardResult.CashReward)?.sourceTierOrder,
                        closed_at            = closedAt,
                        created_at           = closedAt,
                    )
                )
            }
            frozen++
            period = period.previous()
        }
    }

    private class Sales(val count: Int, val total: Double, val paid: Double)

    /** Every sale of the period, dépôt and tournée together (D9), as the reports count them. */
    private suspend fun salesIn(period: CommissionPeriod, zone: ZoneId): Sales {
        val (from, to) = bounds(period, zone)
        val bySource = reports.salesBySource(from, to, null)
        return Sales(bySource.sumOf { it.count }, bySource.sumOf { it.total }, bySource.sumOf { it.paid })
    }

    private suspend fun measure(source: CalculationSource, period: CommissionPeriod, zone: ZoneId, sales: Sales): Double =
        when (source) {
            CalculationSource.INVOICED_SALES ->
                sales.total - reports.clientReturns(period.start.toString(), period.end.toString()).total
            CalculationSource.COLLECTED_CASH -> {
                val (from, to) = bounds(period, zone)
                sales.paid + reports.clientPayments(from, to).total
            }
        }.coerceAtLeast(0.0)

    /** The period's local days as stored-instant bounds, `[start, end)`. */
    private fun bounds(period: CommissionPeriod, zone: ZoneId): Pair<String, String> =
        BusinessDates.dayStart(period.start, zone) to BusinessDates.dayStart(period.end.plusDays(1), zone)

    /** The local day [policy] was saved, or null if its date cannot be read. */
    private fun sinceOf(policy: TargetPolicyEntity, zone: ZoneId): LocalDate? =
        runCatching { LocalDate.parse(BusinessDates.localDay(policy.created_at, zone)) }.getOrNull()

    companion object {
        /** Periods frozen in one go, back from today: three years of months. */
        const val MAX_FROZEN_AT_ONCE = 36

        fun rulesOf(policy: TargetPolicyEntity, tiers: List<PolicyTierEntity>): CommissionRules = CommissionRules(
            type              = policy.incentive_type,
            source            = policy.calculation_source,
            target            = policy.target_value,
            rate              = policy.reward_rate,
            fixedBonus        = policy.fixed_bonus_amount,
            freeGoodProductId = policy.free_good_product_id,
            freeGoodQuantity  = policy.free_good_quantity,
            tiers             = tiers.map { TierRule(it.tier_order, it.min_threshold, it.reward_rate, it.fixed_bonus) },
        )
    }
}
