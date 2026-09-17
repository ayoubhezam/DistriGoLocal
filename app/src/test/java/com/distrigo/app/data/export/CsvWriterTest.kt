package com.distrigo.app.data.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.ZoneId

/** CSV for French Excel: encoding, separators, numbers, dates, quoting, and formulas that must not run. */
class CsvWriterTest {

    private val algiers = ZoneId.of("Africa/Algiers")

    private fun csv(block: CsvWriter.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { out -> CsvWriter(out, algiers).use { it.block() } }.toByteArray()

    private fun cell(cell: CsvCell) = CsvWriter.format(cell, algiers)

    /** A minimal reader for what the writer produces, so tests can check a file reads back as it was meant. */
    private fun parse(bytes: ByteArray): List<List<String>> {
        val text = bytes.toString(Charsets.UTF_8).removePrefix(CsvWriter.BOM)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ';' -> { row.add(field.toString()); field.clear() }
                !quoted && c == '\r' && text.getOrNull(i + 1) == '\n' -> {
                    row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf(); i++
                }
                else -> field.append(c)
            }
            i++
        }
        return rows
    }

    @Test
    fun `the file starts with one UTF-8 byte-order mark and keeps every character`() {
        val bytes = csv {
            header("Client", "Ville")
            row(CsvCell.Text("Épicerie El Amel"), CsvCell.Text("سوق أهراس"))
        }
        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.copyOf(3))
        assertEquals(1, bytes.toString(Charsets.UTF_8).count { it == '﻿' })
        assertEquals(listOf(listOf("Client", "Ville"), listOf("Épicerie El Amel", "سوق أهراس")), parse(bytes))
    }

    @Test
    fun `cells are separated by semicolons and rows end with CRLF`() {
        val text = csv { row(CsvCell.Text("a"), CsvCell.Integer(2), CsvCell.Text(null)) }.toString(Charsets.UTF_8)
        assertEquals("﻿a;2;\r\n", text)
    }

    @Test
    fun `numbers use a decimal comma, round half up, and never go scientific`() {
        assertEquals("1234,50", cell(CsvCell.Number(1234.5)))
        assertEquals("2,35", cell(CsvCell.Number(2.345)))
        assertEquals("0,10", cell(CsvCell.Number(0.1)))
        assertEquals("10000000,00", cell(CsvCell.Number(1e7)))
        assertEquals("-150,00", cell(CsvCell.Number(-150.0)))
        assertEquals("0,00", cell(CsvCell.Number(-0.001)))
        assertEquals("12,5", cell(CsvCell.Number(12.5, decimals = 3, trimZeros = true)))
        assertEquals("12", cell(CsvCell.Number(12.0, decimals = 3, trimZeros = true)))
        assertEquals("0,333", cell(CsvCell.Number(1.0 / 3, decimals = 3, trimZeros = true)))
        assertEquals("", cell(CsvCell.Number(null)))
        assertEquals("", cell(CsvCell.Number(Double.NaN)))
    }

    @Test
    fun `instants are written in local time, calendar dates as they are`() {
        // 23:30 UTC on the 16th is 00:30 on the 17th in Algeria.
        assertEquals("17/09/2026 00:30", cell(CsvCell.DateTime("2026-09-16T23:30:00.643262Z")))
        assertEquals("16/09/2026", cell(CsvCell.Date("2026-09-16")))
        assertEquals("a calendar date where an instant belongs is still a date", "16/09/2026", cell(CsvCell.DateTime("2026-09-16")))
        assertEquals("unreadable values are kept as text", "hier", cell(CsvCell.DateTime("hier")))
        assertEquals("", cell(CsvCell.DateTime(null)))
    }

    @Test
    fun `yes and no are words`() {
        assertEquals("Oui", cell(CsvCell.YesNo(true)))
        assertEquals("Non", cell(CsvCell.YesNo(false)))
        assertEquals("", cell(CsvCell.YesNo(null)))
    }

    @Test
    fun `text that would start a formula is made plain text`() {
        for (formula in listOf("=HYPERLINK(\"http://x\")", "+213555123456", "-2+3", "@SUM(A1)", "\tcmd", "\r=1+1")) {
            val written = CsvWriter.text(formula)
            val value = if (written.startsWith("\"")) written.substring(1, written.length - 1).replace("\"\"", "\"") else written
            assertTrue("$formula -> $written", value.startsWith("'"))
            assertEquals(formula, value.removePrefix("'"))
        }
        assertEquals("Lait Candia 1L", CsvWriter.text("Lait Candia 1L"))
        assertEquals("a formula sign later in the text is harmless", "Prix = 120", CsvWriter.text("Prix = 120"))
    }

    /** Only user text is neutralized: numbers and dates the writer formats itself stay numbers and dates. */
    @Test
    fun `negative numbers are not neutralized`() {
        val rows = parse(csv { row(CsvCell.Number(-150.0), CsvCell.Text("-150")) })
        assertEquals(listOf("-150,00", "'-150"), rows.single())
    }

    @Test
    fun `cells with separators, quotes, line breaks or edge spaces are quoted and read back whole`() {
        val values = listOf("Boulangerie; Pâtisserie", "Le \"Grand\" Café", "Ligne 1\r\nLigne 2", " espace", "fin ", "normal")
        val bytes = csv { row(values.map { CsvCell.Text(it) }) }
        assertEquals("\"Boulangerie; Pâtisserie\"", CsvWriter.text(values[0]))
        assertEquals("\"Le \"\"Grand\"\" Café\"", CsvWriter.text(values[1]))
        assertEquals("normal", CsvWriter.text("normal"))
        assertEquals(listOf(values), parse(bytes))
    }

    @Test
    fun `a table writes its header then one row per item`() {
        data class Sale(val client: String, val total: Double, val at: String)
        val columns = listOf(
            CsvColumn<Sale>("Client") { CsvCell.Text(it.client) },
            CsvColumn("Total (DA)") { CsvCell.Number(it.total) },
            CsvColumn("Date") { CsvCell.DateTime(it.at) },
        )
        var count = 0
        val bytes = csv {
            count = table(columns, sequenceOf(Sale("Épicerie El Amel", 3450.0, "2026-09-17T08:15:00Z"), Sale("=cmd", -20.0, "2026-09-17T09:00:00Z")))
        }
        assertEquals(2, count)
        assertEquals(
            listOf(
                listOf("Client", "Total (DA)", "Date"),
                listOf("Épicerie El Amel", "3450,00", "17/09/2026 09:15"),
                listOf("'=cmd", "-20,00", "17/09/2026 10:00"),
            ),
            parse(bytes)
        )
    }
}
