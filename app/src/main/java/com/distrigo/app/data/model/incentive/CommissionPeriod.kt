package com.distrigo.app.data.model.incentive

import com.distrigo.app.data.local.entity.incentive.PeriodType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A commission period: the calendar month, quarter or year a policy measures, in local days, [start] and [end] both
 * included. A monthly policy runs from the 1st to the last day of each month, whatever day it was saved, so the
 * sales made before that day in the same month count too.
 */
data class CommissionPeriod(val type: PeriodType, val start: LocalDate, val end: LocalDate) {

    /** How many days the period has. */
    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    /** The period just before this one. */
    fun previous(): CommissionPeriod = containing(type, start.minusDays(1))

    operator fun contains(day: LocalDate): Boolean = !day.isBefore(start) && !day.isAfter(end)

    companion object {
        /** The period of [type] that [day] falls in. */
        fun containing(type: PeriodType, day: LocalDate): CommissionPeriod {
            val start = when (type) {
                PeriodType.MONTHLY   -> day.withDayOfMonth(1)
                PeriodType.QUARTERLY -> day.withDayOfMonth(1).withMonth((day.monthValue - 1) / 3 * 3 + 1)
                PeriodType.YEARLY    -> day.withDayOfYear(1)
            }
            val next = when (type) {
                PeriodType.MONTHLY   -> start.plusMonths(1)
                PeriodType.QUARTERLY -> start.plusMonths(3)
                PeriodType.YEARLY    -> start.plusYears(1)
            }
            return CommissionPeriod(type, start, next.minusDays(1))
        }
    }
}
