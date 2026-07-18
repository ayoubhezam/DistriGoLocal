package com.distrigo.app.ui.screens.rapport

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class ReportDateRange(
    val startIso: String,
    val endIso: String,
    val previousStartIso: String,
    val previousEndIso: String,
    val allDatesInRange: List<LocalDate>
)

/** يحوّل ReportPeriod إلى نطاق تواريخ فعلي + النطاق المكافئ للفترة السابقة (لحساب Trend). */
fun ReportPeriod.toDateRange(referenceDate: LocalDate = LocalDate.now()): ReportDateRange {
    return when (this) {
        ReportPeriod.AUJOURD_HUI -> {
            val start = referenceDate
            val end = start.plusDays(1)
            ReportDateRange(
                startIso = "${start}T00:00:00.000",
                endIso = "${end}T00:00:00.000",
                previousStartIso = "${start.minusDays(1)}T00:00:00.000",
                previousEndIso = "${start}T00:00:00.000",
                allDatesInRange = listOf(start)
            )
        }
        ReportPeriod.SEMAINE -> {
            val start = referenceDate.with(DayOfWeek.MONDAY)
            val end = start.plusDays(7)
            val previousStart = start.minusDays(7)
            ReportDateRange(
                startIso = "${start}T00:00:00.000",
                endIso = "${end}T00:00:00.000",
                previousStartIso = "${previousStart}T00:00:00.000",
                previousEndIso = "${start}T00:00:00.000",
                allDatesInRange = (0..6).map { start.plusDays(it.toLong()) }
            )
        }
        ReportPeriod.MOIS -> {
            val start = referenceDate.withDayOfMonth(1)
            val end = start.plusMonths(1)
            val previousStart = start.minusMonths(1)
            ReportDateRange(
                startIso = "${start}T00:00:00.000",
                endIso = "${end}T00:00:00.000",
                previousStartIso = "${previousStart}T00:00:00.000",
                previousEndIso = "${start}T00:00:00.000",
                allDatesInRange = (0 until start.lengthOfMonth()).map { start.plusDays(it.toLong()) }
            )
        }
        ReportPeriod.PERSONNALISEE -> {
            // TODO: يحتاج date picker لم يُبنَ بعد — نستخدم "Semaine" كقيمة افتراضية مؤقتة
            ReportPeriod.SEMAINE.toDateRange(referenceDate)
        }
    }
}

/** حرف واحد يمثل اليوم بالفرنسية (L, M, M, J, V, S, D) لتسمية أعمدة الرسم البياني */
fun LocalDate.toShortDayLabel(): String =
    dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).take(1).uppercase()