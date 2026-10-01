package com.distrigo.app.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the money format where it belongs, by reading the sources.
 *
 * The business's money format is display: screens, receipts, PDFs. It must never shape what is
 * stored, exported or imported — a CSV written with "1 236 790,50" is a CSV Excel reads as text — and
 * no amount may be written in the phone's own locale, which is how the same sale used to show
 * "1250,50" on one phone and "1250.50" on another. Each rule below fails with the files and lines
 * that break it.
 *
 * Comments are left out before matching: documentation may name what the code must not use.
 */
class MoneyFormattingGuardTest {

    private val sources: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/com/distrigo/app").takeIf(File::isDirectory) ?: File(it, "src/main/java/com/distrigo/app") }
        .first { it.isDirectory }

    private fun kotlinFiles(under: String = ""): List<File> =
        File(sources, under).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun File.rel(): String = relativeTo(sources).invariantSeparatorsPath

    /** The code of a file with its comments blanked out, line numbers kept. */
    private fun code(file: File): List<String> {
        val text = file.readText()
        val out = StringBuilder(text.length)
        var i = 0
        var inString = false
        while (i < text.length) {
            val c = text[i]
            when {
                inString -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < text.length) { out.append(text[i + 1]); i++ }
                    else if (c == '"') inString = false
                }
                c == '"' -> { inString = true; out.append(c) }
                text.startsWith("//", i) -> {
                    while (i < text.length && text[i] != '\n') i++
                    continue
                }
                text.startsWith("/*", i) -> {
                    val end = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                    text.substring(i, end).forEach { if (it == '\n') out.append('\n') }
                    i = end
                    continue
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString().split('\n')
    }

    private fun violations(files: List<File>, pattern: Regex): List<String> = files.flatMap { file ->
        code(file).mapIndexedNotNull { index, line -> if (pattern.containsMatchIn(line)) "${file.rel()}:${index + 1}  ${line.trim()}" else null }
    }

    private fun assertNone(rule: String, found: List<String>) =
        assertTrue("$rule\n" + found.joinToString("\n"), found.isEmpty())

    @Test
    fun theScanSeesTheApp() {
        assertTrue("no sources under $sources", kotlinFiles("ui").size > 100 && kotlinFiles("data").size > 50)
    }

    /** Repositories, DAOs, entities and models deal in numbers; only a receipt — a document for people — is written. */
    @Test
    fun theDataLayerNeverFormatsMoney() {
        val data = kotlinFiles("data").filterNot { it.rel().startsWith("data/print/") }
        assertNone(
            "The data layer formats money (keep amounts as numbers; a screen writes them with LocalMoneyFormatter):",
            violations(data, Regex("""\b(Local)?MoneyFormatter\b""")),
        )
    }

    /** Files leave the app in their own fixed shape: CSV with ';' and a decimal ',', XLSX with numeric cells. */
    @Test
    fun exportsAndImportsIgnoreTheDisplayFormat() {
        assertNone(
            "Export or import reads the money display format:",
            violations(kotlinFiles("data/export") + kotlinFiles("data/importer"), Regex("""\bMoneyFormat""")),
        )
    }

    /** A decimal written in the phone's locale reads differently from one phone to the next. */
    @Test
    fun noDecimalIsWrittenInThePhonesLocale() {
        val noLocale = Regex(""""%,?\.?\d*f"\.format\((?!\s*(java\.util\.)?Locale\.)|String\.format\(\s*"[^"]*%,?\.?\d*f""")
        assertNone(
            "A decimal formatted in the phone's locale (money: LocalMoneyFormatter / receipt.money; anything else: pass a Locale):",
            violations(kotlinFiles(), noLocale),
        )
    }

    @Test
    fun theOldMoneyHelpersStayGone() {
        assertNone(
            "An old money helper is back (use LocalMoneyFormatter):",
            violations(kotlinFiles(), Regex("""\bformatDZD\b|\bAmount\.format\(|\bgroupThousands\b""")),
        )
    }
}
