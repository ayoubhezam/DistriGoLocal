package com.distrigo.app.ui.settings.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.backup.BackupFailedException
import com.distrigo.app.data.backup.BackupMessages
import com.distrigo.app.data.importer.ImportPlan
import com.distrigo.app.data.importer.ImportPlanException
import com.distrigo.app.data.importer.ImportRepository
import com.distrigo.app.data.importer.ImportResult
import com.distrigo.app.data.importer.ImportSheet
import com.distrigo.app.data.importer.ImportTemplate
import com.distrigo.app.data.importer.ImportStaleException
import com.distrigo.app.data.importer.XlsxReadException
import com.distrigo.app.data.importer.XlsxWorkbook
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.distrigo.app.data.importer.importText
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import com.distrigo.app.R

sealed class ImportState {
    /** Waiting for a file to be picked. */
    object Idle : ImportState()
    data class Working(val label: String) : ImportState()
    /** The file is read and planned for [sheet]; the user decides. */
    data class Preview(val fileName: String, val workbook: XlsxWorkbook, val plan: ImportPlan, val sheet: ImportSheet) : ImportState()
    data class Done(val fileName: String, val result: ImportResult) : ImportState()
    /** Nothing was written; [message] says why, and [retryable] whether picking the same file again could help. */
    data class Failed(val message: String) : ImportState()
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: ImportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    /** Reads the picked file and shows what importing its [sheet] would do. */
    fun inspect(uri: Uri, sheet: ImportSheet) {
        if (_state.value is ImportState.Working) return
        _state.value = ImportState.Working(importText(R.string.import_reading))
        viewModelScope.launch {
            _state.value = try {
                val name = displayName(uri)
                val workbook = withContext(Dispatchers.IO) {
                    (context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())).use { importer.read(it) }
                }
                ImportState.Preview(name, workbook, importer.plan(workbook, sheet), sheet)
            } catch (e: XlsxReadException) {
                Log.w(TAG, "file not readable: ${e.reason}", e)
                ImportState.Failed(message(e.reason))
            } catch (e: ImportPlanException) {
                ImportState.Failed(e.message!!)
            } catch (e: IOException) {
                Log.w(TAG, "file not opened", e)
                ImportState.Failed(importText(R.string.import_cannot_open))
            } catch (e: SecurityException) {
                Log.w(TAG, "file not opened", e)
                ImportState.Failed(importText(R.string.import_cannot_open))
            }
        }
    }

    /** Applies the plan the screen shows, after a safety backup. */
    fun apply() {
        val preview = _state.value as? ImportState.Preview ?: return
        _state.value = ImportState.Working(importText(R.string.import_working))
        viewModelScope.launch {
            _state.value = try {
                ImportState.Done(preview.fileName, importer.apply(preview.workbook, preview.plan, preview.sheet))
            } catch (e: BackupFailedException) {
                Log.w(TAG, "no safety backup, so no import", e)
                ImportState.Failed(BackupMessages.safetyBackupFailed(e.reason))
            } catch (e: ImportStaleException) {
                ImportState.Failed(importText(R.string.import_stale))
            }
        }
    }

    /**
     * Writes [sheet]'s empty template where the user chose, then says how it went: « Modèle enregistré », or why
     * not.
     */
    fun saveTemplate(uri: Uri, sheet: ImportSheet, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) {
                    (context.contentResolver.openOutputStream(uri) ?: throw FileNotFoundException(uri.toString())).use { ImportTemplate.write(sheet, it) }
                }
                importText(R.string.import_template_saved)
            } catch (e: IOException) {
                Log.w(TAG, "template not written", e)
                importText(R.string.import_template_failed)
            } catch (e: SecurityException) {
                Log.w(TAG, "template not written", e)
                importText(R.string.import_template_failed)
            }
            onDone(message)
        }
    }

    /** Back to the start, to pick another file. */
    fun reset() {
        if (_state.value !is ImportState.Working) _state.value = ImportState.Idle
    }

    private fun displayName(uri: Uri): String = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
        }
    } catch (e: Exception) {
        null
    } ?: uri.lastPathSegment ?: importText(R.string.import_file)

    private fun message(reason: XlsxReadException.Reason): String = when (reason) {
        XlsxReadException.Reason.NOT_A_WORKBOOK ->
            importText(R.string.import_not_xlsx)
        XlsxReadException.Reason.LEGACY_OR_PROTECTED ->
            importText(R.string.import_old_or_locked)
        XlsxReadException.Reason.TOO_LARGE -> importText(R.string.import_too_large)
        XlsxReadException.Reason.DAMAGED -> importText(R.string.import_damaged)
    }

    private companion object {
        const val TAG = "ImportViewModel"
    }
}
