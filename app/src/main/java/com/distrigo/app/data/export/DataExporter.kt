package com.distrigo.app.data.export

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.FilterOutputStream
import java.io.OutputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The file an export is written as. */
enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    /** One workbook, one sheet per dataset: types, dates and codes survive, whatever computer opens it. */
    XLSX("Excel (.xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),

    /** One `.csv` per dataset, for software that reads CSV: several datasets come as a `.zip` of them. */
    CSV("CSV (autres logiciels)", "csv", "text/csv");

    /** What several datasets are packed into, which for CSV is a zip of files. */
    fun extensionFor(datasets: Int): String = if (this == CSV && datasets > 1) "zip" else extension

    fun mimeTypeFor(datasets: Int): String = if (this == CSV && datasets > 1) "application/zip" else mimeType
}

/**
 * Writes [ExportDataset]s from the database in the format asked for.
 *
 * Rows are streamed from the query into the file one at a time, so a long sales history is never held in
 * memory, and every dataset is read in one read transaction, so an export describes one moment: a sale
 * recorded while it is written does not appear in the sales and not in its lines.
 */
class DataExporter(private val db: SupportSQLiteDatabase, private val zone: ZoneId = ZoneId.systemDefault()) {

    /**
     * Writes [datasets] to [out], which it does not close, and returns the rows written for each. Blocks: call it
     * off the main thread.
     */
    fun export(
        datasets: List<ExportDataset>,
        period: ExportPeriod,
        format: ExportFormat,
        out: OutputStream,
    ): Map<ExportDataset, Int> {
        val wanted = datasets.distinct()
        require(wanted.isNotEmpty()) { "nothing to export" }
        db.beginTransactionReadOnly()
        try {
            val counts = when {
                format == ExportFormat.XLSX -> workbook(wanted, period, out)
                wanted.size == 1 -> mapOf(wanted.single() to csv(wanted.single(), period, out))
                else -> csvZip(wanted, period, out)
            }
            db.setTransactionSuccessful()
            return counts
        } finally {
            db.endTransaction()
        }
    }

    private fun workbook(datasets: List<ExportDataset>, period: ExportPeriod, out: OutputStream): Map<ExportDataset, Int> {
        val writer = XlsxWriter(KeepOpen(out), zone)
        val counts = datasets.associateWith { dataset ->
            rows(dataset, period) { rows -> writer.sheet(dataset.label, dataset.columns, rows) }
        }
        writer.close() // writes the workbook's own parts; KeepOpen leaves [out] open
        return counts
    }

    private fun csv(dataset: ExportDataset, period: ExportPeriod, out: OutputStream): Int {
        val writer = CsvWriter(KeepOpen(out), zone)
        val count = rows(dataset, period) { rows -> writer.table(dataset.columns, rows) }
        writer.close()
        return count
    }

    private fun csvZip(datasets: List<ExportDataset>, period: ExportPeriod, out: OutputStream): Map<ExportDataset, Int> {
        val zip = ZipOutputStream(out)
        val counts = datasets.associateWith { dataset ->
            zip.putNextEntry(ZipEntry("${dataset.fileName}.csv"))
            csv(dataset, period, KeepOpen(zip)).also { zip.closeEntry() }
        }
        zip.finish()
        return counts
    }

    /** Runs [dataset]'s query and hands its rows to [write], one at a time. */
    private fun rows(dataset: ExportDataset, period: ExportPeriod, write: (Sequence<ExportRow>) -> Int): Int {
        val (sql, args) = dataset.query(period, zone)
        return db.query(SimpleSQLiteQuery(sql, args)).use { cursor ->
            val row = ExportRow(cursor)
            write(generateSequence { if (cursor.moveToNext()) row else null })
        }
    }

    /** Lets a writer be closed, to flush it, without closing the stream underneath. */
    private class KeepOpen(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = flush()
    }
}
