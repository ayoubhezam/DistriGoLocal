package com.distrigo.app.data.print

import com.distrigo.app.data.print.lang.PrinterCodePage
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * This phone's printing configuration, kept in `no_backup/print/settings.json`.
 *
 * Not in the database, and the reason is the same one [com.distrigo.app.data.backup.auto.AutoBackupStore]
 * gives for the backup folder: these settings describe *this handset*, not the business's data. The
 * business identity a receipt prints — name, phone, logo — is a Room row precisely so it travels with
 * a backup and with the sync postponed to phase 4. A printer's MAC address must not: restore a backup
 * onto a second phone and it would inherit a pairing it has never had, and once several reps each
 * carry a phone they would all fight over one row.
 *
 * `no_backup` is also left out of Android's own cloud backup, which is the same thing said again at
 * the OS level.
 *
 * Written whole, through a temp file and a rename, so a kill mid-write keeps the previous settings
 * rather than half of the new ones.
 */
class PrintSettingsStore(private val dir: File) {

    private val file get() = File(dir, "settings.json")

    /**
     * The settings and every change to them.
     *
     * Read from disk once, on construction — it is a few hundred bytes in the app's own no-backup
     * directory, so this stays off the "no I/O on the main thread" list by being small and by
     * happening once at injection rather than per screen. Every later read is the cached value.
     */
    private val state = MutableStateFlow(readFromDisk())

    fun flow(): StateFlow<PrintSettings> = state.asStateFlow()

    fun current(): PrintSettings = state.value

    /** Applies [change] to the settings, writes them whole and publishes them. */
    fun update(change: (PrintSettings) -> PrintSettings): PrintSettings = synchronized(LOCK) {
        val next = change(state.value)
        writeToDisk(next)
        state.value = next
        next
    }

    // ── Saved printers ──
    //
    // Add/rename/remove all go through update(), so each one is a whole-file write and a publish, the
    // same as any other setting. There are at most a handful of printers; nothing here needs to be
    // cleverer than a list.

    /**
     * Adds a printer, or updates the one with the same address. It does **not** become the selected
     * one here.
     *
     * Selection is earned by answering a connection, in PrinterSelectionViewModel.select — so a
     * printer that was off when it was added does not end up labelled as the one receipts go to.
     */
    fun addPrinter(printer: SavedPrinter): PrintSettings = update { current ->
        current.copy(
            printers = current.printers.filterNot { it.id.equals(printer.id, ignoreCase = true) } + printer,
        )
    }

    fun renamePrinter(id: String, name: String): PrintSettings = update { current ->
        current.copy(printers = current.printers.map {
            if (it.id.equals(id, ignoreCase = true)) it.copy(displayName = name.trim().ifBlank { it.displayName }) else it
        })
    }

    /** Changes one printer's hardware settings — what the test print confirms. */
    fun configurePrinter(
        id      : String,
        paper   : PaperSize?       = null,
        language: PrintLanguage?   = null,
        codePage: PrinterCodePage? = null,
    ): PrintSettings = update { current ->
        current.copy(printers = current.printers.map {
            if (!it.id.equals(id, ignoreCase = true)) it
            else it.copy(
                paper    = paper    ?: it.paper,
                language = language ?: it.language,
                codePage = codePage ?: it.codePage,
            )
        })
    }

    /** Removes a printer, clearing the selection if it was the selected one. */
    fun removePrinter(id: String): PrintSettings = update { current ->
        val remaining = current.printers.filterNot { it.id.equals(id, ignoreCase = true) }
        current.copy(
            printers = remaining,
            // Removing the selected printer clears the selection rather than quietly promoting
            // another: promoting one would name a printer nobody has connected to, which is the whole
            // thing selection-by-connection exists to prevent. The user picks the replacement, and
            // that pick opens a connection.
            selectedPrinterId = current.selectedPrinterId?.takeIf { !it.equals(id, ignoreCase = true) },
        )
    }

    fun selectPrinter(id: String): PrintSettings = update { it.copy(selectedPrinterId = id) }

    private fun readFromDisk(): PrintSettings {
        if (!file.isFile) return PrintSettings()
        return try {
            val json = JsonParser().parse(file.readText()).asJsonObject
            PrintSettings(
                method            = ConnectionMethod.fromStorage(json.text("method")),
                selectedPrinterId = json.text("selected_printer_id"),
                defaultPaper      = PaperSize.fromStorage(json.text("default_paper")),
                defaultLanguage   = PrintLanguage.fromStorage(json.text("default_language")),
                defaultCodePage   = PrinterCodePage.fromStorage(json.text("default_code_page")),
                printers          = json.getAsJsonArray("printers")?.mapNotNull { it.toSavedPrinter() } ?: emptyList(),
            )
        } catch (e: RuntimeException) {
            // A truncated or hand-edited file costs the user their printer choice, not a crash on
            // launch. Same trade AutoBackupStore makes.
            PrintSettings()
        }
    }

    private fun writeToDisk(settings: PrintSettings) {
        val json = JsonObject().apply {
            addProperty("method", settings.method.storageCode)
            addProperty("selected_printer_id", settings.selectedPrinterId)
            addProperty("default_paper", settings.defaultPaper.storageCode)
            addProperty("default_language", settings.defaultLanguage.storageCode)
            addProperty("default_code_page", settings.defaultCodePage.storageCode)
            add("printers", JsonArray().apply {
                settings.printers.forEach { printer ->
                    add(JsonObject().apply {
                        addProperty("id", printer.id)
                        addProperty("display_name", printer.displayName)
                        addProperty("method", printer.method.storageCode)
                        addProperty("paper", printer.paper.storageCode)
                        addProperty("language", printer.language.storageCode)
                        addProperty("code_page", printer.codePage.storageCode)
                    })
                }
            })
        }
        dir.mkdirs()
        val temp = File(dir, "settings.json.tmp")
        temp.writeText(GSON.toJson(json))
        // Files.move with REPLACE_EXISTING rather than File.renameTo, which is specified to fail when
        // the destination already exists and does so on some filesystems — so the first write would
        // succeed and every write after it would throw. It happens to work on the phone's ext4/f2fs,
        // which is exactly why it would have gone unnoticed until it did not.
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun com.google.gson.JsonElement.toSavedPrinter(): SavedPrinter? {
        val obj = takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val id = obj.text("id") ?: return null
        return SavedPrinter(
            id          = id,
            displayName = obj.text("display_name") ?: id,
            method      = ConnectionMethod.fromStorage(obj.text("method")),
            paper       = PaperSize.fromStorage(obj.text("paper")),
            language    = PrintLanguage.fromStorage(obj.text("language")),
            codePage    = PrinterCodePage.fromStorage(obj.text("code_page")),
        )
    }

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    companion object {
        private val LOCK = Any()
        private val GSON = GsonBuilder().serializeNulls().setPrettyPrinting().create()

        fun forApp(context: android.content.Context) =
            PrintSettingsStore(File(context.applicationContext.noBackupFilesDir, "print"))
    }
}
