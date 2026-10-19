package com.distrigo.app.data.model.incentive

import com.distrigo.app.data.local.entity.incentive.CalculationSource
import com.distrigo.app.data.local.entity.incentive.IncentiveType
import com.distrigo.app.data.local.entity.incentive.PeriodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The commission's rules and the « Objectif » snapshot (docs/design/dashboard_objectif_plan.md, D13). */
class GoalProgressTest {

    private val sales = CalculationSource.INVOICED_SALES

    private fun rules(type: IncentiveType, target: Double = 1_200_000.0, rate: Double? = null, bonus: Double? = null, tiers: List<TierRule> = emptyList()) =
        CommissionRules(type, sales, target, rate = rate, fixedBonus = bonus, freeGoodProductId = 7, freeGoodQuantity = 6, tiers = tiers)

    // The plan's example: P1 600 000 → 2 %, P2 900 000 → 2,5 %, P3 1 200 000 → 3,5 %
    private val paliers = rules(
        IncentiveType.PROGRESSIVE_TIERS, target = 1_000_000.0,
        tiers = listOf(TierRule(2, 1_200_000.0, 3.5, null), TierRule(0, 600_000.0, 2.0, null), TierRule(1, 900_000.0, 2.5, null)),
    )

    private val october = CommissionPeriod.containing(PeriodType.MONTHLY, LocalDate.parse("2026-10-10"))

    private fun progress(r: CommissionRules, value: Double, today: String = "2026-10-10", count: Int = 0, total: Double = 0.0) =
        GoalProgress.of(r, october, LocalDate.parse(today), value, count, total, showReward = true, policySince = october.start)

    private fun cash(result: RewardResult) = (result as RewardResult.CashReward).amount

    @Test
    fun `the percentage rounds down and reads 100 only once reached`() {
        val r = rules(IncentiveType.FIXED_BONUS, bonus = 15_000.0)
        assertEquals(74, r.percentOf(888_000.0))
        assertEquals(99, r.percentOf(1_195_200.0))   // 99,6 % used to read 100
        assertEquals(100, r.percentOf(1_200_000.0))
        assertEquals(100, r.percentOf(1_199_999.996)) // within half a centime: reached
        assertEquals(108, r.percentOf(1_300_000.0))
        assertEquals(0, rules(IncentiveType.FIXED_BONUS, target = 0.0).percentOf(500.0))
    }

    @Test
    fun `nothing is earned below the objective`() {
        val r = rules(IncentiveType.FIXED_RATE, rate = 3.0)
        assertEquals(RewardResult.NotAchieved(74), r.rewardAt(888_000.0))
        assertEquals(39_000.0, cash(r.rewardAt(1_300_000.0)), 0.001)
        assertEquals(15_000.0, cash(rules(IncentiveType.FIXED_BONUS, bonus = 15_000.0).rewardAt(1_200_000.0)), 0.0)
        assertEquals(RewardResult.FreeGoodsReward(7, 6, 100), rules(IncentiveType.FREE_GOODS).rewardAt(1_200_000.0))
    }

    @Test
    fun `in Paliers the objective is the first tier, and each tier pays on the whole measure`() {
        assertEquals(600_000.0, paliers.objective, 0.0)
        val reward = paliers.rewardAt(888_000.0) as RewardResult.CashReward
        assertEquals(17_760.0, reward.amount, 0.001)
        assertEquals(0, reward.sourceTierOrder)
        assertEquals(43_750.0, cash(paliers.rewardAt(1_250_000.0)), 0.001)
        assertEquals(RewardResult.NotAchieved(91), paliers.rewardAt(550_000.0))
    }

    @Test
    fun `a tier may pay a fixed bonus`() {
        val r = rules(IncentiveType.PROGRESSIVE_TIERS, tiers = listOf(TierRule(0, 500_000.0, null, 10_000.0), TierRule(1, 800_000.0, null, 25_000.0)))
        assertEquals(10_000.0, cash(r.rewardAt(700_000.0)), 0.0)
        assertEquals(25_000.0, cash(r.rewardAt(800_000.0)), 0.0)
    }

    @Test
    fun `the next milestone says how far and what it pays`() {
        val next = paliers.nextAt(888_000.0)!!
        assertEquals(900_000.0, next.threshold, 0.0)
        assertEquals(12_000.0, next.gap, 0.001)
        assertEquals(22_500.0, cash(next.reward), 0.001)
        assertNull(paliers.nextAt(1_250_000.0))

        val rate = rules(IncentiveType.FIXED_RATE, rate = 3.0)
        assertEquals(36_000.0, cash(rate.nextAt(888_000.0)!!.reward), 0.001)
        assertNull(rate.nextAt(1_200_000.0))
    }

    @Test
    fun `the tiers read passed, current, next and locked`() {
        assertEquals(listOf(TierState.CURRENT, TierState.NEXT, TierState.LOCKED), progress(paliers, 888_000.0).tiers.map { it.state })
        assertEquals(listOf(TierState.PASSED, TierState.CURRENT, TierState.NEXT), progress(paliers, 950_000.0).tiers.map { it.state })
        assertEquals(listOf(TierState.NEXT, TierState.LOCKED, TierState.LOCKED), progress(paliers, 100_000.0).tiers.map { it.state })
        assertTrue(progress(rules(IncentiveType.FIXED_RATE, rate = 3.0), 100.0).tiers.isEmpty())
    }

    @Test
    fun `the pace compares the projection with the next milestone`() {
        // October has 31 days; on the 10th, 10 are elapsed and 22 left, today counted in both
        val r = rules(IncentiveType.FIXED_BONUS, bonus = 15_000.0)
        val behind = progress(r, 300_000.0).pace!!
        assertEquals(10, behind.daysElapsed)
        assertEquals(22, behind.daysLeft)
        assertEquals(30_000.0, behind.perDaySoFar, 0.001)
        assertEquals(930_000.0, behind.projection, 0.001)
        assertEquals(900_000.0 / 22, behind.neededPerDay, 0.001)
        assertEquals(PaceStatus.BEHIND, behind.status)
        assertEquals(PaceStatus.ON_TRACK, progress(r, 400_000.0).pace!!.status)  // projects 1 240 000
        assertEquals(PaceStatus.AHEAD, progress(r, 450_000.0).pace!!.status)     // projects 1 395 000
        assertEquals(PaceStatus.REACHED, progress(r, 1_250_000.0).pace!!.status)
        assertEquals(0.0, progress(r, 1_250_000.0).pace!!.neededPerDay, 0.0)
    }

    @Test
    fun `an ended period has no pace`() {
        assertNull(progress(paliers, 888_000.0, today = "2026-11-02").pace)
    }

    @Test
    fun `the snapshot keeps the measure at zero or more, and gives the average sale`() {
        val p = progress(paliers, -5_000.0, count = 4, total = 20_000.0)
        assertEquals(0.0, p.value, 0.0)
        assertFalse(p.reached)
        assertEquals(5_000.0, p.averageSale!!, 0.0)
        assertNull(progress(paliers, 0.0).averageSale)
        assertTrue(progress(paliers, 600_000.0).reached)
    }
}
