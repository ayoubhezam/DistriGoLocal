package com.distrigo.app.data.model.incentive

import com.distrigo.app.data.local.entity.incentive.CalculationSource
import com.distrigo.app.data.local.entity.incentive.IncentiveType
import com.distrigo.app.data.model.Amount
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor

/** One tier of a policy in « Paliers »: from [threshold], included, [rate] % of the whole measure, or [fixedBonus]. */
data class TierRule(val order: Int, val threshold: Double, val rate: Double?, val fixedBonus: Double?)

/**
 * A commission policy's rules as the calculation reads them, free of Room (docs/design/dashboard_objectif_plan.md).
 *
 * The objective is the policy's target, or in « Paliers » the first tier's threshold (D13): a tier below the target
 * would pay before the objective is reached, and a target below the first tier would be reached for nothing. A
 * threshold is reached to the half centime, as a balance is read (see [Amount.owing]).
 */
data class CommissionRules(
    val type: IncentiveType,
    val source: CalculationSource,
    val target: Double,
    val rate: Double? = null,
    val fixedBonus: Double? = null,
    val freeGoodProductId: Int? = null,
    val freeGoodQuantity: Int? = null,
    val tiers: List<TierRule> = emptyList(),
) {
    /** The tiers that can pay, lowest threshold first; none outside « Paliers ». */
    val sortedTiers: List<TierRule> =
        if (type == IncentiveType.PROGRESSIVE_TIERS) tiers.filter { it.threshold > 0 }.sortedBy { it.threshold } else emptyList()

    val objective: Double = sortedTiers.firstOrNull()?.threshold ?: target

    fun reaches(value: Double, threshold: Double): Boolean = value >= threshold - Amount.HALF_CENTIME

    /** The tier [value] has reached: the highest one it passes, or null below the first. */
    fun tierAt(value: Double): TierRule? = sortedTiers.lastOrNull { reaches(value, it.threshold) }

    /**
     * [value] as a share of the objective, rounded down (D13): 99,6 % reads 99, and 100 only once the objective is
     * reached — rounding to the nearest used to show « 100 % » with the bonus still out of reach.
     */
    fun percentOf(value: Double): Int {
        if (objective <= 0) return 0
        val share = floor(value * 100 / objective).toInt().coerceAtLeast(0)
        return if (reaches(value, objective)) share.coerceAtLeast(100) else share.coerceAtMost(99)
    }

    /**
     * What [value] earns. Nothing below the objective. In « Taux fixe » the rate applies to the whole measure; in
     * « Paliers » the reached tier's rate does too, or its fixed bonus — which is why each tier is a jump.
     */
    fun rewardAt(value: Double): RewardResult {
        val percent = percentOf(value)
        if (objective <= 0 || !reaches(value, objective)) return RewardResult.NotAchieved(percent)
        return when (type) {
            IncentiveType.FIXED_RATE  -> RewardResult.CashReward(value * (rate ?: 0.0) / 100, percent)
            IncentiveType.FIXED_BONUS -> RewardResult.CashReward(fixedBonus ?: 0.0, percent)
            IncentiveType.FREE_GOODS  -> RewardResult.FreeGoodsReward(freeGoodProductId ?: 0, freeGoodQuantity ?: 0, percent)
            IncentiveType.PROGRESSIVE_TIERS -> {
                val tier = tierAt(value) ?: return RewardResult.NotAchieved(percent)
                RewardResult.CashReward(tier.fixedBonus ?: (value * (tier.rate ?: 0.0) / 100), percent, tier.order)
            }
        }
    }

    /** The nearest threshold above [value] — the objective, or the next tier — with what it would earn; null at the top. */
    fun nextAt(value: Double): Milestone? {
        val thresholds = if (sortedTiers.isNotEmpty()) sortedTiers.map { it.threshold } else listOf(objective)
        val next = thresholds.firstOrNull { it > 0 && !reaches(value, it) } ?: return null
        return Milestone(threshold = next, gap = next - value, reward = rewardAt(next))
    }
}

/** A threshold still ahead: how far it is, and what reaching it would earn. */
data class Milestone(val threshold: Double, val gap: Double, val reward: RewardResult)

enum class TierState { PASSED, CURRENT, NEXT, LOCKED }

data class TierProgress(val tier: TierRule, val state: TierState)

/** How the period is going against the next milestone, or [REACHED] when none is left. */
enum class PaceStatus { AHEAD, ON_TRACK, BEHIND, REACHED }

/**
 * The pace over calendar days, today counted as both elapsed and still available: [perDaySoFar] is the measure
 * over the days elapsed, [neededPerDay] what is left to the next milestone over the days left, and [projection]
 * the measure at the end of the period if the pace holds.
 */
data class Pace(
    val daysElapsed: Int,
    val daysLeft: Int,
    val perDaySoFar: Double,
    val neededPerDay: Double,
    val projection: Double,
    val status: PaceStatus,
)

/**
 * Where the active policy stands over its current period — the one snapshot every screen reads, so none of them
 * calculates (plan, Part 2 Step 2). [value] is the measure so far, never below zero; [reward] what it earns now;
 * [next] the nearest milestone; [pace] null once the period is over. [averageSale] turns a gap into a number of
 * sales. [showReward] is the policy's switch (D13); [policySince] the day the policy was saved.
 */
data class GoalProgress(
    val rules: CommissionRules,
    val period: CommissionPeriod,
    val today: LocalDate,
    val value: Double,
    val percent: Int,
    val reached: Boolean,
    val reward: RewardResult,
    val tiers: List<TierProgress>,
    val next: Milestone?,
    val pace: Pace?,
    val averageSale: Double?,
    val showReward: Boolean,
    val policySince: LocalDate,
) {
    val objective: Double get() = rules.objective

    companion object {
        /** Projected at least this far past the next milestone, the period is « En avance ». */
        const val AHEAD_MARGIN = 1.10

        fun of(
            rules: CommissionRules,
            period: CommissionPeriod,
            today: LocalDate,
            value: Double,
            salesCount: Int,
            salesTotal: Double,
            showReward: Boolean,
            policySince: LocalDate,
        ): GoalProgress {
            val measure = value.coerceAtLeast(0.0)
            val next = rules.nextAt(measure)
            return GoalProgress(
                rules        = rules,
                period       = period,
                today        = today,
                value        = measure,
                percent      = rules.percentOf(measure),
                reached      = rules.objective > 0 && rules.reaches(measure, rules.objective),
                reward       = rules.rewardAt(measure),
                tiers        = tierStates(rules, measure),
                next         = next,
                pace         = if (today in period) paceOf(period, today, measure, next) else null,
                averageSale  = if (salesCount > 0) salesTotal / salesCount else null,
                showReward   = showReward,
                policySince  = policySince,
            )
        }

        private fun tierStates(rules: CommissionRules, value: Double): List<TierProgress> {
            val current = rules.tierAt(value)
            val next = rules.sortedTiers.firstOrNull { !rules.reaches(value, it.threshold) }
            return rules.sortedTiers.map { tier ->
                TierProgress(tier, when {
                    tier == current                      -> TierState.CURRENT
                    rules.reaches(value, tier.threshold) -> TierState.PASSED
                    tier == next                         -> TierState.NEXT
                    else                                 -> TierState.LOCKED
                })
            }
        }

        private fun paceOf(period: CommissionPeriod, today: LocalDate, value: Double, next: Milestone?): Pace {
            val elapsed = (ChronoUnit.DAYS.between(period.start, today) + 1).toInt().coerceIn(1, period.days)
            val left = period.days - elapsed + 1
            val perDay = value / elapsed
            val projection = perDay * period.days
            val aim = next?.threshold
            return Pace(
                daysElapsed  = elapsed,
                daysLeft     = left,
                perDaySoFar  = perDay,
                neededPerDay = if (aim == null) 0.0 else (aim - value).coerceAtLeast(0.0) / left,
                projection   = projection,
                status       = when {
                    aim == null                     -> PaceStatus.REACHED
                    projection >= aim * AHEAD_MARGIN -> PaceStatus.AHEAD
                    projection >= aim                -> PaceStatus.ON_TRACK
                    else                             -> PaceStatus.BEHIND
                },
            )
        }
    }
}
