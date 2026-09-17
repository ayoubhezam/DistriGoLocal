package com.distrigo.app.ui.settings.data.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.export.DataExporter
import com.distrigo.app.data.export.ExportDataset
import com.distrigo.app.data.export.ExportFormat
import com.distrigo.app.data.export.ExportPeriod
import com.distrigo.app.data.local.database.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate
import javax.inject.Inject

/** What the user has chosen, and what the screen is doing with it. */
data class ExportUiState(
    val selected: Set<ExportDataset> = ExportDataset.entries.toSet(),
    val format: ExportFormat = ExportFormat.XLSX,
    val preset: PeriodPreset = PeriodPreset.THIS_MONTH,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val working: Boolean = false,
    val result: ExportResult? = null,
) {
    val today: LocalDate get() = LocalDate.now()
    val period: ExportPeriod get() = ExportChoices.period(preset, today, customFrom, customTo)
    /** Whether any chosen dataset is filtered by the period; clients and products alone are not. */
    val usesPeriod: Boolean get() = selected.any { it.byPeriod }
    val fileName: String get() = ExportChoices.fileName(selected, period, today, format)
    val mimeType: String get() = ExportChoices.mimeType(selected, format)
}

sealed class ExportResult {
    data class Saved(val fileName: String, val rows: Map<ExportDataset, Int>) : ExportResult()
    /** Written to the app's cache for the share sheet, which the screen now opens. */
    data class ReadyToShare(val uri: Uri, val mimeType: String, val rows: Map<ExportDataset, Int>) : ExportResult()
    data class Failed(val message: String) : ExportResult()
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    fun toggle(dataset: ExportDataset) = _state.update {
        it.copy(selected = if (dataset in it.selected) it.selected - dataset else it.selected + dataset)
    }

    fun selectAll(all: Boolean) = _state.update { it.copy(selected = if (all) ExportDataset.entries.toSet() else emptySet()) }

    fun chooseFormat(format: ExportFormat) = _state.update { it.copy(format = format) }

    fun choosePreset(preset: PeriodPreset) = _state.update { it.copy(preset = preset) }

    fun chooseFrom(day: LocalDate?) = _state.update { it.copy(customFrom = day, preset = PeriodPreset.CUSTOM) }

    fun chooseTo(day: LocalDate?) = _state.update { it.copy(customTo = day, preset = PeriodPreset.CUSTOM) }

    fun dismissResult() = _state.update { it.copy(result = null) }

    /** Writes the export to [destination], a document the file picker just created for it. */
    fun saveTo(destination: Uri) = run { current ->
        try {
            val rows = openForWriting(destination).use { write(current, it) }
            ExportResult.Saved(displayName(destination) ?: current.fileName, rows)
        } catch (e: Exception) {
            if (e !is IOException && e !is SecurityException && e !is IllegalArgumentException) throw e
            Log.w(TAG, "export not saved", e)
            deleteQuietly(destination)
            ExportResult.Failed(
                "Impossible d'enregistrer l'export à cet emplacement. Vérifiez l'espace disponible ou choisissez un autre emplacement."
            )
        }
    }

    /**
     * Writes the export to `cache/exports` for the share sheet. Earlier exports there are removed first: a shared
     * file only has to exist until the app it was shared with has read it.
     */
    fun share() = run { current ->
        val dir = File(context.cacheDir, SHARE_DIR)
        try {
            dir.deleteRecursively()
            dir.mkdirs()
            val file = File(dir, current.fileName)
            val rows = file.outputStream().use { write(current, it) }
            ExportResult.ReadyToShare(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), current.mimeType, rows)
        } catch (e: IOException) {
            Log.w(TAG, "export not written for sharing", e)
            dir.deleteRecursively()
            ExportResult.Failed("Impossible de préparer l'export. Vérifiez l'espace disponible sur le téléphone, puis réessayez.")
        }
    }

    private fun write(current: ExportUiState, out: OutputStream): Map<ExportDataset, Int> {
        // In the order the screen lists them, whatever order they were ticked in.
        val datasets = ExportDataset.entries.filter { it in current.selected }
        return DataExporter(db.openHelper.readableDatabase).export(datasets, current.period, current.format, out)
    }

    private fun run(block: (ExportUiState) -> ExportResult) {
        val current = _state.value
        if (current.working || current.selected.isEmpty()) return
        _state.update { it.copy(working = true, result = null) }
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { block(current) }
            } catch (e: Exception) {
                Log.e(TAG, "export failed", e)
                ExportResult.Failed("Une erreur inattendue a empêché l'export.")
            }
            _state.update { it.copy(working = false, result = result) }
        }
    }

    private fun openForWriting(uri: Uri): OutputStream {
        val truncating = try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: UnsupportedOperationException) {
            null
        }
        return truncating ?: context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("no output stream for $uri")
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    /** Removes a file left half written, so it is not mistaken for a complete export. */
    private fun deleteQuietly(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            Log.w(TAG, "could not remove the unfinished export", e)
        }
    }

    private companion object {
        const val TAG = "ExportViewModel"
        const val SHARE_DIR = "exports"
    }
}
