package com.distrigo.app.data.billing

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * While Google Play sells (BillingSwitch on), the app's own words never send people to a code: codes stay possible,
 * folded under « J'ai un code d'activation », but are not promoted (owner, 2026-11-20). The one string allowed to
 * name a code is that link.
 */
class BillingWordingTest {

    private fun strings(dir: String): Map<String, String> =
        Regex("""<string name="(bill_[a-z_]+)">(.*?)</string>""")
            .findAll(File("src/main/res/$dir/strings_billing.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun `no billing text but the folded link mentions a code, in French or Arabic`() {
        val french = strings("values")
        val arabic = strings("values-ar")
        assertTrue("the same strings in both languages", french.keys == arabic.keys && french.size > 20)
        for ((name, text) in french) {
            if (name == "bill_have_code") continue
            assertTrue("$name: $text", !text.contains("code", ignoreCase = true))
        }
        for ((name, text) in arabic) {
            if (name == "bill_have_code") continue
            assertTrue("$name: $text", !text.contains("رمز"))
        }
    }
}
