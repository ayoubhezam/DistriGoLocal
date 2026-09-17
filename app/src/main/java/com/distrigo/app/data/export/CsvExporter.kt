package com.distrigo.app.data.export

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.FilterOutputStream
import java.io.OutputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes [ExportDataset]s from the database as CSV: one dataset as a `.csv`, several as a `.zip` of them.
 *
 * Rows are streamed from the query into the file one at a time, so a long sales history is never held in
 * memory. Several datasets are read in one read transaction, so a zip describes one moment: a sale recorded
 * while it is written does not appear in the sales and not in its lines.
 */
class CsvExporter(private val db: SupportSQLiteDatabase, private val zone: ZoneId = ZoneId.systemDefault()) {

    /** Writes one [dataset] to [out], which it does not close. Returns the rows written. Blocks: off the main thread. */
    fun export(dataset: ExportDataset, period: ExportPeriod, out: OutputStream): Int = reading {
        write(dataset, period, out)
    }

    /**
     * Writes [datasets] to [out] as a ZIP holding `<dataset>.csv` for each, which it does not close. Returns the rows
     * written per dataset. Blocks: off the main thread.
     */
    fun exportZip(datasets: List<ExportDataset>, period: ExportPeriod, out: OutputStream): Map<ExportDataset, Int> = reading {
        val zip = ZipOutputStream(out)
        val counts = datasets.distinct().associateWith { dataset ->
            zip.putNextEntry(ZipEntry("${dataset.fileName}.csv"))
            write(dataset, period, KeepOpen(zip)).also { zip.closeEntry() }
        }
        zip.finish()
        counts
    }

    private fun write(dataset: ExportDataset, period: ExportPeriod, out: OutputStream): Int {
        val (sql, args) = dataset.query(period, zone)
        val writer = CsvWriter(KeepOpen(out), zone)
        val count = db.query(SimpleSQLiteQuery(sql, args)).use { cursor ->
            val row = ExportRow(cursor)
            writer.table(dataset.columns, generateSequence { if (cursor.moveToNext()) row else null })
        }
        writer.close() // flushes; KeepOpen leaves [out] open
        return count
    }

    private fun <T> reading(block: () -> T): T {
        db.beginTransactionReadOnly()
        try {
            return block().also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }

    /** Lets a writer be closed, to flush it, without closing the stream underneath. */
    private class KeepOpen(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = flush()
    }
}
