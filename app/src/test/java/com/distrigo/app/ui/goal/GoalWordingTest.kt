package com.distrigo.app.ui.goal

import com.distrigo.app.core.format.MoneyFormat
import com.distrigo.app.core.format.MoneyFormatter
import com.distrigo.app.data.local.entity.incentive.CalculationSource
import com.distrigo.app.data.local.entity.incentive.IncentiveType
import com.distrigo.app.data.local.entity.incentive.PeriodType
import com.distrigo.app.data.model.incentive.CommissionPeriod
import com.distrigo.app.data.model.incentive.CommissionRules
import com.distrigo.app.data.model.incentive.GoalProgress
import com.distrigo.app.data.model.incentive.TierRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The words of « Mon objectif » (Part 2, Steps 3–6; D20): with the bonus shown, and without. */
class GoalWordingTest {

    private val money = MoneyFormatter.plain(MoneyFormat.SPACES)
    private val sales = CalculationSource.INVOICED_SALES
    private val october = CommissionPeriod.containing(PeriodType.MONTHLY, LocalDate.parse("2026-10-10"))

    // The plan's example: P1 600 000 → 2 %, P2 900 000 → 2,5 %, P3 1 200 000 → 3,5 %
    private val paliers = CommissionRules(
        IncentiveType.PROGRESSIVE_TIERS, sales, 1_000_000.0,
        tiers = listOf(TierRule(0, 600_000.0, 2.0, null), TierRule(1, 900_000.0, 2.5, null), TierRule(2, 1_200_000.0, 3.5, null)),
    )
    private val taux = CommissionRules(IncentiveType.FIXED_RATE, sales, 1_200_000.0, rate = 3.0)
    private val forfait = CommissionRules(IncentiveType.FIXED_BONUS, CalculationSource.COLLECTED_CASH, 500_000.0, fixedBonus = 15_000.0)

    private fun progress(rules: CommissionRules, value: Double, show: Boolean = true, count: Int = 0, total: Double = 0.0) =
        GoalProgress.of(rules, october, LocalDate.parse("2026-10-23"), value, count, total, showReward = show, policySince = october.start)

    @Test
    fun theRuleInPlainFrench() {
        assertEquals(
            "Vous touchez 2 % de vos ventes facturées dès 600 000,00 DA ce mois-ci, 2,5 % dès 900 000,00 DA, 3,5 % dès 1 200 000,00 DA.",
            GoalWording.rule(paliers, PeriodType.MONTHLY, money, showReward = true),
        )
        assertEquals("Vous touchez 3 % de vos ventes facturées dès 1 200 000,00 DA ce mois-ci.", GoalWording.rule(taux, PeriodType.MONTHLY, money, true))
        assertEquals(
            "Vous touchez une prime de 15 000,00 DA dès 500 000,00 DA de recouvrement ce trimestre.",
            GoalWording.rule(forfait, PeriodType.QUARTERLY, money, true),
        )
    }

    /** D20: with « Afficher la prime » off, thresholds only — no rate, no amount of a bonus. */
    @Test
    fun withTheBonusHiddenTheRuleSaysOnlyWhatToReach() {
        assertEquals(
            "Trois paliers de ventes facturées ce mois-ci : 600 000,00 DA, 900 000,00 DA et 1 200 000,00 DA.",
            GoalWording.rule(paliers, PeriodType.MONTHLY, money, showReward = false),
        )
        assertEquals("Votre objectif : 1 200 000,00 DA de ventes facturées cette année.", GoalWording.rule(taux, PeriodType.YEARLY, money, false))
        assertNull(GoalWording.reward(progress(taux, 1_300_000.0, show = false), money))
        assertNull(GoalWording.tierPays(paliers.sortedTiers.first(), showReward = false, money = money))
    }

    @Test
    fun theNextStepIsTheNearestTierAndItsBonusOnlyWhenShown() {
        // At 888 000: P1 pays 2 % × 888 000 = 17 760; P2 would pay 2,5 % × 900 000 = 22 500
        assertEquals(
            "Plus que 12 000,00 DA pour le palier 2 : votre prime passe de 17 760,00 DA à 22 500,00 DA",
            GoalWording.nextSentence(progress(paliers, 888_000.0), money),
        )
        assertEquals("Plus que 12 000,00 DA pour le palier 2", GoalWording.nextSentence(progress(paliers, 888_000.0, show = false), money))
        assertEquals("Palier max atteint", GoalWording.nextSentence(progress(paliers, 1_300_000.0), money))
        assertEquals("Objectif atteint", GoalWording.nextSentence(progress(taux, 1_300_000.0), money))
    }

    @Test
    fun theGapPerDayAsSalesWithTheAverageSale() {
        // 23 Oct: 9 days left; 312 000 to go is 34 667 a day; at 5 000 a sale, 7 sales a day
        val goal = progress(taux, 888_000.0, count = 100, total = 500_000.0)
        assertEquals("Plus que 312 000,00 DA en 9 jours · environ 34 666,67 DA par jour", GoalWording.nextSentence(goal, money))
        assertEquals(7, GoalWording.salesPerDay(goal))
        // Not for « Recouvrement »: a payment is not a sale
        assertNull(GoalWording.salesPerDay(progress(forfait, 100_000.0, count = 100, total = 500_000.0)))
    }

    @Test
    fun theBonusInParametresWords() {
        assertEquals("Prime à débloquer : 3 % de vos ventes facturées" to false, GoalWording.reward(progress(taux, 888_000.0), money))
        assertEquals("Prime : 39 000,00 DA" to true, GoalWording.reward(progress(taux, 1_300_000.0), money))
        assertEquals("Palier 1 · prime : 17 760,00 DA" to true, GoalWording.reward(progress(paliers, 888_000.0), money))
        assertEquals("2,5 %", GoalWording.tierPays(paliers.sortedTiers[1], showReward = true, money = money))
    }

    @Test
    fun theNavyCardsWords() {
        assertEquals("888 000 DA", GoalWording.whole(888_000.4, money))
        assertEquals("238K", GoalWording.compact(238_000.0))
        assertEquals("1,2M", GoalWording.compact(1_200_000.0))
        assertEquals("1M", GoalWording.compact(1_000_000.0))
        assertEquals("Réalisé en octobre", GoalWording.achievedIn(october))
        assertEquals("le palier 2", GoalWording.aim(progress(paliers, 888_000.0)))
        assertEquals("de l'objectif", GoalWording.ofAim(progress(taux, 888_000.0)))
    }

    /** D21: the line follows the pace, never the percent alone. */
    @Test
    fun theEncouragementFollowsThePace() {
        // 888 000 in 23 days projects 1 196 870: past P2 (900 000) by more than 10 %, short of 1 200 000
        assertEquals("Vous êtes en avance : à ce rythme, vous dépassez le palier 2.", GoalWording.encouragement(progress(paliers, 888_000.0)))
        assertEquals(
            "Il faut accélérer : un effort de plus chaque jour vous rapproche de l'objectif.",
            GoalWording.encouragement(progress(taux, 888_000.0)),
        )
        assertEquals("Palier max atteint. Continuez sur cette lancée.", GoalWording.encouragement(progress(paliers, 1_300_000.0)))
        assertEquals("Objectif atteint. Continuez sur cette lancée.", GoalWording.encouragement(progress(taux, 1_300_000.0)))
    }

    @Test
    fun theCommissionCardsWords() {
        assertEquals("taux fixe 3 %", GoalWording.policyLine(taux))
        assertEquals("paliers", GoalWording.policyLine(paliers))
        assertEquals("17 760 DA", GoalWording.rewardValue(progress(paliers, 888_000.0).reward, money))
        assertEquals("22 500 DA", GoalWording.rewardValue(progress(paliers, 888_000.0).next!!.reward, money))
        assertEquals("0 DA", GoalWording.rewardValue(progress(taux, 888_000.0).reward, money))
    }

    @Test
    fun periodNames() {
        assertEquals("octobre", GoalWording.periodName(october))
        assertEquals("Octobre 2026", GoalWording.pastPeriodName(october))
        assertEquals("4e trimestre 2026", GoalWording.pastPeriodName(CommissionPeriod.containing(PeriodType.QUARTERLY, LocalDate.parse("2026-11-02"))))
        assertEquals("2026", GoalWording.pastPeriodName(CommissionPeriod.containing(PeriodType.YEARLY, LocalDate.parse("2026-11-02"))))
    }
}
