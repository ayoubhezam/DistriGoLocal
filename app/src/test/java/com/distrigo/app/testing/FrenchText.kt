package com.distrigo.app.testing

import com.distrigo.app.R
import com.distrigo.app.core.text.UiText
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * [UiText] written in French as the phone would write it, for a JVM test: the default resources
 * (res/values/strings*.xml) read from the source tree, a resource id found by its name in R, arguments put in with
 * Java's formatter (as Android's getString does), and French's plural rule (one for 0 and 1, other above). So a test
 * keeps checking the exact French sentence while the code under test returns UiText.
 */
object FrenchText {

    private val values: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/res/values").takeIf(File::isDirectory) ?: File(it, "src/main/res/values") }
        .first { it.isDirectory }

    private val strings = mutableMapOf<String, String>()
    private val plurals = mutableMapOf<String, Map<String, String>>()

    init {
        values.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }!!.forEach { file ->
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                when (e.tagName) {
                    "string" -> strings[e.getAttribute("name")] = unescape(e.textContent)
                    "plurals" -> {
                        val items = e.getElementsByTagName("item")
                        plurals[e.getAttribute("name")] =
                            (0 until items.length).associate { k ->
                                val item = items.item(k) as Element
                                item.getAttribute("quantity") to unescape(item.textContent)
                            }
                    }
                }
            }
        }
    }

    private val stringNames: Map<Int, String> = names(R.string::class.java)
    private val pluralNames: Map<Int, String> = names(R.plurals::class.java)

    private fun names(type: Class<*>) = type.fields.associate { it.getInt(null) to it.name }

    private fun unescape(s: String) = s.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("\\@", "@")

    fun write(text: UiText): String = when (text) {
        is UiText.Raw -> text.text
        is UiText.Joined -> text.parts.joinToString(text.separator) { write(it) }
        is UiText.Res -> format(strings.getValue(stringNames.getValue(text.id)), text.args)
        is UiText.Plural -> {
            val forms = plurals.getValue(pluralNames.getValue(text.id))
            format(forms[if (text.count in 0..1) "one" else "other"] ?: forms.getValue("other"), text.args)
        }
    }

    fun write(texts: List<UiText>): List<String> = texts.map(::write)

    private fun format(pattern: String, args: List<Any>): String =
        String.format(Locale.FRENCH, pattern, *args.map { (it as? UiText)?.let(::write) ?: it }.toTypedArray())
}
