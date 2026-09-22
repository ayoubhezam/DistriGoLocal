package com.distrigo.app.data.print

import com.distrigo.app.data.print.lang.PrinterCodePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The device-local settings file: what it keeps, and what it does when it is damaged.
 *
 * The file is the only record of which printer this phone prints to, and it is deliberately excluded
 * from every backup, so there is nothing to restore it from — which makes "survives a round trip" and
 * "a corrupt file does not crash the launch" the two things worth pinning.
 */
class PrintSettingsStoreTest {

    @get:Rule val temp = TemporaryFolder()

    private fun store() = PrintSettingsStore(temp.root.resolve("print"))

    private fun printer(id: String, name: String = id) = SavedPrinter(
        id          = id,
        displayName = name,
        method      = ConnectionMethod.BLUETOOTH,
        paper       = PaperSize.MM58,
        language    = PrintLanguage.ESC_POS,
        codePage    = PrinterCodePage.CP1252,
    )

    @Test
    fun `defaults apply when nothing has been written`() {
        val settings = store().current()
        assertEquals(ConnectionMethod.BLUETOOTH, settings.method)
        assertEquals(PaperSize.MM80, settings.defaultPaper)
        assertEquals(PrintLanguage.ESC_POS, settings.defaultLanguage)
        assertTrue(settings.printers.isEmpty())
        assertNull(settings.selectedPrinterId)
    }

    @Test
    fun `settings and printers survive a round trip through the file`() {
        store().apply {
            update { it.copy(defaultPaper = PaperSize.MM58, method = ConnectionMethod.WIFI) }
            addPrinter(printer("AA:BB:CC:DD:EE:FF", "Caisse"))
        }

        // A fresh instance reads the file rather than the cache, which is what the next launch does.
        val reloaded = store().current()
        assertEquals(PaperSize.MM58, reloaded.defaultPaper)
        assertEquals(ConnectionMethod.WIFI, reloaded.method)
        assertEquals(1, reloaded.printers.size)
        assertEquals("Caisse", reloaded.printers.first().displayName)
        assertEquals("AA:BB:CC:DD:EE:FF", reloaded.selectedPrinterId)
    }

    @Test
    fun `adding a printer selects it, and adding the same address twice replaces it`() {
        val store = store()
        store.addPrinter(printer("AA:11", "Premier"))
        store.addPrinter(printer("BB:22", "Second"))
        assertEquals("BB:22", store.current().selectedPrinterId)

        store.addPrinter(printer("aa:11", "Renommé à l'ajout"))
        val settings = store.current()
        assertEquals("two entries for one address", 2, settings.printers.size)
        assertEquals("Renommé à l'ajout", settings.selectedPrinter?.displayName)
    }

    @Test
    fun `removing the selected printer falls back to one that is left`() {
        val store = store()
        store.addPrinter(printer("AA:11"))
        store.addPrinter(printer("BB:22"))

        store.removePrinter("BB:22")
        // Not null: removing a spare must not silently disable printing.
        assertEquals("AA:11", store.current().selectedPrinterId)

        store.removePrinter("AA:11")
        assertNull(store.current().selectedPrinterId)
    }

    @Test
    fun `configure changes only the fields it is given`() {
        val store = store()
        store.addPrinter(printer("AA:11", "Caisse"))

        store.configurePrinter("AA:11", paper = PaperSize.MM80)
        store.current().printers.first().let {
            assertEquals(PaperSize.MM80, it.paper)
            assertEquals(PrintLanguage.ESC_POS, it.language)
            assertEquals("Caisse", it.displayName)
        }

        store.configurePrinter("AA:11", codePage = PrinterCodePage.CP858)
        store.current().printers.first().let {
            assertEquals(PrinterCodePage.CP858, it.codePage)
            assertEquals("paper should not have been reset", PaperSize.MM80, it.paper)
        }
    }

    @Test
    fun `a blank rename is ignored rather than wiping the name`() {
        val store = store()
        store.addPrinter(printer("AA:11", "Caisse"))
        store.renamePrinter("AA:11", "   ")
        assertEquals("Caisse", store.current().printers.first().displayName)
    }

    @Test
    fun `a printer's own paper wins over the device default`() {
        val store = store()
        store.update { it.copy(defaultPaper = PaperSize.MM80) }
        store.addPrinter(printer("AA:11").copy(paper = PaperSize.MM58))
        assertEquals(PaperSize.MM58, store.current().effectivePaper)

        store.removePrinter("AA:11")
        assertEquals(PaperSize.MM80, store.current().effectivePaper)
    }

    @Test
    fun `a corrupt file falls back to defaults instead of throwing`() {
        store().update { it.copy(defaultPaper = PaperSize.MM58) }
        temp.root.resolve("print/settings.json").writeText("{ this is not json")

        // Constructed on the launch path, so a throw here would be a crash on start with no way back —
        // the file is in no_backup and no backup can restore it.
        val settings = store().current()
        assertEquals(PaperSize.MM80, settings.defaultPaper)
        assertTrue(settings.printers.isEmpty())
    }

    @Test
    fun `an unknown stored value falls back rather than failing the whole file`() {
        temp.root.resolve("print").mkdirs()
        temp.root.resolve("print/settings.json").writeText(
            """{"method":"zigbee","default_paper":"120mm","default_language":"postscript","printers":[]}"""
        )
        val settings = store().current()
        assertEquals(ConnectionMethod.BLUETOOTH, settings.method)
        assertEquals(PaperSize.MM80, settings.defaultPaper)
        assertEquals(PrintLanguage.ESC_POS, settings.defaultLanguage)
    }
}
