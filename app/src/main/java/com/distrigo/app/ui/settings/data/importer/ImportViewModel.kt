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
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

sealed class ImportState {
    /** Waiting for a file to be picked. */
    object Idle : ImportState()
    data class Working(val label: String) : ImportState()
    /** The file is read and planned; the user decides. */
    data class Preview(val fileName: String, val workbook: XlsxWorkbook, val plan: ImportPlan) : ImportState()
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

    /** Reads the picked file and shows what importing it would do. */
    fun inspect(uri: Uri) {
        if (_state.value is ImportState.Working) return
        _state.value = ImportState.Working("Lecture du fichier…")
        viewModelScope.launch {
            _state.value = try {
                val name = displayName(uri)
                val workbook = withContext(Dispatchers.IO) {
                    (context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())).use { importer.read(it) }
                }
                ImportState.Preview(name, workbook, importer.plan(workbook))
            } catch (e: XlsxReadException) {
                Log.w(TAG, "file not readable: ${e.reason}", e)
                ImportState.Failed(message(e.reason))
            } catch (e: ImportPlanException) {
                ImportState.Failed(e.message!!)
            } catch (e: IOException) {
                Log.w(TAG, "file not opened", e)
                ImportState.Failed("Impossible d'ouvrir ce fichier. Choisissez-le à nouveau.")
            } catch (e: SecurityException) {
                Log.w(TAG, "file not opened", e)
                ImportState.Failed("Impossible d'ouvrir ce fichier. Choisissez-le à nouveau.")
            }
        }
    }

    /** Applies the plan the screen shows, after a safety backup. */
    fun apply() {
        val preview = _state.value as? ImportState.Preview ?: return
        _state.value = ImportState.Working("Copie de sécurité, puis import…")
        viewModelScope.launch {
            _state.value = try {
                ImportState.Done(preview.fileName, importer.apply(preview.workbook, preview.plan))
            } catch (e: BackupFailedException) {
                Log.w(TAG, "no safety backup, so no import", e)
                ImportState.Failed(BackupMessages.safetyBackupFailed(e.reason))
            } catch (e: ImportStaleException) {
                ImportState.Failed("Les données ont changé depuis l'aperçu ; rien n'a été importé. Choisissez le fichier à nouveau pour un aperçu à jour.")
            }
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
    } ?: uri.lastPathSegment ?: "fichier"

    private fun message(reason: XlsxReadException.Reason): String = when (reason) {
        XlsxReadException.Reason.NOT_A_WORKBOOK ->
            "Ce fichier n'est pas un classeur Excel (.xlsx). Exportez les produits ou les clients depuis DistriGo pour obtenir le modèle."
        XlsxReadException.Reason.LEGACY_OR_PROTECTED ->
            "Ce fichier est un ancien format Excel (.xls) ou est protégé par un mot de passe. Enregistrez-le en .xlsx, sans mot de passe."
        XlsxReadException.Reason.TOO_LARGE -> "Ce fichier est trop volumineux pour être importé sur le téléphone."
        XlsxReadException.Reason.DAMAGED -> "Ce fichier est endommagé ou incomplet. Enregistrez-le à nouveau depuis Excel, puis réessayez."
    }

    private companion object {
        const val TAG = "ImportViewModel"
    }
}
