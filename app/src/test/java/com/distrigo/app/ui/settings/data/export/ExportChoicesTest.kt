package com.distrigo.app.ui.settings.data.export

import com.distrigo.app.data.export.ExportDataset
import com.distrigo.app.data.export.ExportFormat
import com.distrigo.app.data.export.ExportPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ExportChoicesTest {

    private val today = LocalDate.of(2026, 9, 17)
    private fun day(month: Int, dayOfMonth: Int, year: Int = 2026) = LocalDate.of(year, month, dayOfMonth)

    @Test
    fun `presets cover the local days they say`() {
        assertEquals(ExportPeriod(day(9, 1), day(9, 17)), ExportChoices.period(PeriodPreset.THIS_MONTH, today))
        assertEquals(ExportPeriod(day(8, 1), day(8, 31)), ExportChoices.period(PeriodPreset.LAST_MONTH, today))
        assertEquals(ExportPeriod(day(1, 1), day(9, 17)), ExportChoices.period(PeriodPreset.THIS_YEAR, today))
        assertEquals(ExportPeriod.ALL, ExportChoices.period(PeriodPreset.ALL, today))
    }

    @Test
    fun `last month in January is December, and February has its own length`() {
        assertEquals(ExportPeriod(day(12, 1, 2025), day(12, 31, 2025)), ExportChoices.period(PeriodPreset.LAST_MONTH, day(1, 10)))
        assertEquals(ExportPeriod(day(2, 1, 2028), day(2, 29, 2028)), ExportChoices.period(PeriodPreset.LAST_MONTH, day(3, 5, 2028)))
    }

    @Test
    fun `chosen dates may be open on either side or picked the wrong way round`() {
        assertEquals(ExportPeriod(day(9, 3), day(9, 10)), ExportChoices.period(PeriodPreset.CUSTOM, today, day(9, 3), day(9, 10)))
        assertEquals(ExportPeriod(day(9, 3), day(9, 10)), ExportChoices.period(PeriodPreset.CUSTOM, today, day(9, 10), day(9, 3)))
        assertEquals(ExportPeriod(day(9, 3), null), ExportChoices.period(PeriodPreset.CUSTOM, today, day(9, 3), null))
        assertEquals(ExportPeriod.ALL, ExportChoices.period(PeriodPreset.CUSTOM, today))
    }

    @Test
    fun `a period reads as a phrase`() {
        assertEquals("du 01/09/2026 au 17/09/2026", ExportChoices.describe(ExportPeriod(day(9, 1), day(9, 17))))
        assertEquals("du 17/09/2026", ExportChoices.describe(ExportPeriod(day(9, 17), day(9, 17))))
        assertEquals("depuis le 01/09/2026", ExportChoices.describe(ExportPeriod(day(9, 1), null)))
        assertEquals("jusqu'au 17/09/2026", ExportChoices.describe(ExportPeriod(null, day(9, 17))))
        assertEquals("de toutes les dates", ExportChoices.describe(ExportPeriod.ALL))
    }

    @Test
    fun `file names say what and when`() {
        val september = ExportPeriod(day(9, 1), day(9, 17))
        val csv = ExportFormat.CSV
        assertEquals("DistriGo-ventes-2026-09-01-au-2026-09-17.csv", ExportChoices.fileName(setOf(ExportDataset.VENTES), september, today, csv))
        assertEquals("DistriGo-charges-depuis-2026-09-01.csv", ExportChoices.fileName(setOf(ExportDataset.CHARGES), ExportPeriod(day(9, 1), null), today, csv))
        assertEquals("DistriGo-pertes-2026-09-17.csv", ExportChoices.fileName(setOf(ExportDataset.PERTES), ExportPeriod(day(9, 17), day(9, 17)), today, csv))
        assertEquals(
            "clients and products are as they are today, whatever period is chosen",
            "DistriGo-export-2026-09-17.xlsx", ExportChoices.fileName(setOf(ExportDataset.CLIENTS, ExportDataset.PRODUITS), september, today, ExportFormat.XLSX)
        )
    }

    /** Several datasets are one workbook, but several CSV files, which travel as a zip. */
    @Test
    fun `the format decides the extension and the type`() {
        val period = ExportPeriod.ALL
        val all = ExportDataset.entries.toSet()
        val one = setOf(ExportDataset.VENTES)
        assertEquals("DistriGo-export-tout.xlsx", ExportChoices.fileName(all, period, today, ExportFormat.XLSX))
        assertEquals("DistriGo-export-tout.zip", ExportChoices.fileName(all, period, today, ExportFormat.CSV))
        assertEquals("DistriGo-ventes-tout.xlsx", ExportChoices.fileName(one, period, today, ExportFormat.XLSX))
        assertEquals("DistriGo-ventes-tout.csv", ExportChoices.fileName(one, period, today, ExportFormat.CSV))
        assertEquals(ExportFormat.XLSX.mimeType, ExportChoices.mimeType(all, ExportFormat.XLSX))
        assertEquals("text/csv", ExportChoices.mimeType(one, ExportFormat.CSV))
        assertEquals("application/zip", ExportChoices.mimeType(all, ExportFormat.CSV))
    }
}
