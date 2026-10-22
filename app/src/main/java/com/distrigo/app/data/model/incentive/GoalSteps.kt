package com.distrigo.app.data.model.incentive

import com.distrigo.app.data.local.entity.incentive.PeriodType
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor

/**
 * One step of a period for « Rythme hebdomadaire » (D21): [value] the measure over its days, [target] its share of
 * the aim — the aim × its days ÷ the period's days, so a short step asks less. [current] holds today; a [future]
 * step has not started.
 */
data class GoalStep(
    val label: String,
    val start: LocalDate,
    val end: LocalDate,
    val value: Double,
    val target: Double,
    val current: Boolean,
    val future: Boolean,
)

object GoalSteps {

    /**
     * [period] cut in steps: a month in four weeks from the 1st (S4 runs to the month's end, 7 to 10 days), a
     * quarter in its months, a year in its quarters.
     */
    fun split(period: CommissionPeriod): List<Triple<String, LocalDate, LocalDate>> = when (period.type) {
        PeriodType.MONTHLY -> (0 until 4).map { i ->
            val start = period.start.plusDays(7L * i)
            Triple("S${i + 1}", start, if (i == 3) period.end else start.plusDays(6))
        }
        PeriodType.QUARTERLY -> (0 until 3).map { i ->
            val start = period.start.plusMonths(i.toLong())
            val name = start.month.getDisplayName(TextStyle.SHORT, Locale.FRENCH).trimEnd('.').replaceFirstChar { it.uppercase() }
            Triple(name, start, start.plusMonths(1).minusDays(1))
        }
        PeriodType.YEARLY -> (0 until 4).map { i ->
            val start = period.start.plusMonths(3L * i)
            Triple("T${i + 1}", start, start.plusMonths(3).minusDays(1))
        }
    }

    /** Each step with its [values] (the measure per step, in [split]'s order) and its share of [aim]. */
    fun of(period: CommissionPeriod, today: LocalDate, aim: Double, values: List<Double>): List<GoalStep> =
        split(period).mapIndexed { i, (label, start, end) ->
            val days = (end.toEpochDay() - start.toEpochDay() + 1).toDouble()
            GoalStep(
                label   = label,
                start   = start,
                end     = end,
                value   = values.getOrElse(i) { 0.0 },
                target  = aim * days / period.days,
                current = today in CommissionPeriod(period.type, start, end),
                future  = today.isBefore(start),
            )
        }

    /**
     * How far ahead of the planned pace, in tenths of a percent: the measure against the aim's share of the days
     * elapsed (today included). +42 is « +4,2 % », −120 « −12,0 % »; null without an aim or with it reached.
     */
    fun paceGap(value: Double, aim: Double, daysElapsed: Int, periodDays: Int): Int? {
        if (aim <= 0 || periodDays <= 0 || value >= aim) return null
        val planned = aim * daysElapsed.coerceIn(1, periodDays) / periodDays
        return floor((value / planned - 1) * 1000 + 0.5).toInt()
    }
}
