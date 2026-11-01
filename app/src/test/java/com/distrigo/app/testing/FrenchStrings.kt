package com.distrigo.app.testing

import com.distrigo.app.R
import com.distrigo.app.core.text.AppStrings
import com.distrigo.app.core.text.UiText
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The app's French resources for JVM unit tests: [install] makes [AppStrings] write a [UiText] as the phone would in
 * French, from res/values, so a test can keep checking the words the user reads (« au moins 18 Mo ») after they moved
 * out of the code. Plurals take French's forms: 0 and 1 the singular, from 2 the plural.
 */
object FrenchStrings {

    private class Plural(val one: String, val other: String)

    private val strings = mutableMapOf<String, String>()
    private val plurals = mutableMapOf<String, Plural>()

    private val stringNames: Map<Int, String> by lazy { names(R.string::class.java) }
    private val pluralNames: Map<Int, String> by lazy { names(R.plurals::class.java) }

    fun install() {
        if (strings.isEmpty()) load()
        AppStrings.installForTests(::write)
    }

    fun uninstall() = AppStrings.installForTests(null)

    fun write(text: UiText): String = when (text) {
        is UiText.Raw -> text.text
        is UiText.Joined -> text.parts.joinToString(text.separator) { write(it) }
        is UiText.Res -> {
            val raw = strings.getValue(stringNames.getValue(text.id))
            if (text.args.isEmpty()) raw else String.format(Locale.FRENCH, raw, *args(text.args))
        }
        is UiText.Plural -> {
            val p = plurals.getValue(pluralNames.getValue(text.id))
            String.format(Locale.FRENCH, if (text.count in 0..1) p.one else p.other, *args(text.args))
        }
    }

    private fun args(args: List<Any>): Array<Any> = args.map { if (it is UiText) write(it) else it }.toTypedArray()

    private fun names(type: Class<*>): Map<Int, String> =
        type.fields.associate { it.getInt(null) to it.name }

    private fun load() {
        val dir = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/res/values").takeIf(File::isDirectory) ?: File(it, "src/main/res/values") }
            .first { it.isDirectory }
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        dir.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }!!.forEach { file ->
            val root = parser.parse(file).documentElement
            val list = root.getElementsByTagName("string")
            for (i in 0 until list.length) {
                val e = list.item(i) as Element
                strings[e.getAttribute("name")] = unescape(e.textContent)
            }
            val ps = root.getElementsByTagName("plurals")
            for (i in 0 until ps.length) {
                val e = ps.item(i) as Element
                val items = e.getElementsByTagName("item")
                var one = ""
                var other = ""
                for (j in 0 until items.length) {
                    val item = items.item(j) as Element
                    when (item.getAttribute("quantity")) {
                        "one" -> one = unescape(item.textContent)
                        "other" -> other = unescape(item.textContent)
                    }
                }
                plurals[e.getAttribute("name")] = Plural(one.ifEmpty { other }, other)
            }
        }
    }

    /** Android's escapes: \' \" \n and \uXXXX. */
    private fun unescape(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> { out.append('\n'); i += 2 }
                    't' -> { out.append('\t'); i += 2 }
                    'u' -> { out.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 6 }
                    else -> { out.append(n); i += 2 }
                }
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }
}
