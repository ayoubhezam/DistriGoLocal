package com.distrigo.app.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A ratchet on the French written in the UI code (docs/i18n_rtl_plan.md, Phase 1): the count of texts still written
 * there — `Text("…")`, `contentDescription = "…"`, `title = "…"`, `label = "…"`, `subtitle = "…"`, `placeholder = "…"`,
 * `text = "…"` — may only go down. A new screen's words go in res/values/strings_<package>.xml, with their Arabic in
 * values-ar. When a section moves its text to resources, lower [BASELINE] to the count this test prints.
 */
class HardcodedTextGuardTest {

    private val sources: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/java/com/distrigo/app").takeIf(File::isDirectory) ?: File(it, "src/main/java/com/distrigo/app") }
        .first { it.isDirectory }

    private val hardcoded = Regex("""(\bText\(\s*|\b(contentDescription|title|label|subtitle|placeholder|text)\s*=\s*)"[^"]*[A-Za-zÀ-ÿ]""")

    /** The code of a file with its comments blanked out: documentation may quote French. */
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

    @Test
    fun noNewTextIsWrittenInTheCode() {
        val files = File(sources, "ui").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() +
            File(sources, "MainActivity.kt")
        val count = files.sumOf { file -> code(file).sumOf { line -> hardcoded.findAll(line).count() } }
        assertTrue(
            "$count texts are written in the UI code, more than the $BASELINE allowed: put the new ones in " +
                "res/values/strings_<package>.xml (and values-ar), or lower BASELINE to $count if text moved out.",
            count <= BASELINE,
        )
        println("Hardcoded UI texts: $count (baseline $BASELINE)")
    }

    private companion object {
        /** 1371 at Phase 1 (2026-10-27); lowered as Phases 3–4 move the text out. */
        const val BASELINE = 942
    }
}
