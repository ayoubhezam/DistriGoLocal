package com.distrigo.app.data.model.incentive

import com.distrigo.app.data.local.entity.incentive.PeriodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** « Rythme hebdomadaire » (D21): the period in steps, each with its share of the aim, and the badge's gap. */
class GoalStepsTest {

    private val october = CommissionPeriod.containing(PeriodType.MONTHLY, LocalDate.parse("2026-10-10"))

    @Test
    fun aMonthIsFourWeeksFromTheFirstTheLastToTheMonthsEnd() {
        val steps = GoalSteps.split(october)
        assertEquals(listOf("S1", "S2", "S3", "S4"), steps.map { it.first })
        assertEquals(LocalDate.parse("2026-10-07"), steps[0].third)
        assertEquals(LocalDate.parse("2026-10-22"), steps[3].second)
        assertEquals(LocalDate.parse("2026-10-31"), steps[3].third)
    }

    @Test
    fun eachStepAsksItsShareOfTheAim() {
        // 1 240 000 over 31 days is 40 000 a day: 7 days ask 280 000, the last 10 days 400 000
        val steps = GoalSteps.of(october, LocalDate.parse("2026-10-10"), 1_240_000.0, listOf(300_000.0, 50_000.0))
        assertEquals(280_000.0, steps[0].target, 0.01)
        assertEquals(400_000.0, steps[3].target, 0.01)
        assertEquals(listOf(false, true, false, false), steps.map { it.current })
        assertEquals(listOf(false, false, true, true), steps.map { it.future })
        assertEquals(0.0, steps[2].value, 0.0)
    }

    @Test
    fun aQuarterIsItsMonthsAYearItsQuarters() {
        val q4 = CommissionPeriod.containing(PeriodType.QUARTERLY, LocalDate.parse("2026-11-02"))
        assertEquals(listOf("Oct", "Nov", "Déc"), GoalSteps.split(q4).map { it.first })
        val year = CommissionPeriod.containing(PeriodType.YEARLY, LocalDate.parse("2026-11-02"))
        assertEquals(listOf("T1", "T2", "T3", "T4"), GoalSteps.split(year).map { it.first })
        assertEquals(LocalDate.parse("2026-12-31"), GoalSteps.split(year).last().third)
    }

    @Test
    fun theBadgeIsTheGapToThePlannedPaceInTenthsOfAPercent() {
        // 23 Oct: 1 200 000 × 23 ÷ 31 = 890 323 planned; 888 000 is 0,26 % short
        assertEquals(-3, GoalSteps.paceGap(888_000.0, 1_200_000.0, 23, 31))
        // 900 000 × 23 ÷ 31 = 667 742 planned; 888 000 is 33,0 % ahead
        assertEquals(330, GoalSteps.paceGap(888_000.0, 900_000.0, 23, 31))
        assertNull(GoalSteps.paceGap(1_300_000.0, 1_200_000.0, 23, 31))
        assertNull(GoalSteps.paceGap(100.0, 0.0, 23, 31))
    }
}
