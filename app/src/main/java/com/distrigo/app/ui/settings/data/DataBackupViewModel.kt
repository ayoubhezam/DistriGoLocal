package com.distrigo.app.ui.settings.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.backup.BackupCreator
import com.distrigo.app.data.backup.BackupFailedException
import com.distrigo.app.data.backup.BackupInspection
import com.distrigo.app.data.backup.BackupInspector
import com.distrigo.app.data.backup.BackupMessages
import com.distrigo.app.data.backup.BackupPreview
import com.distrigo.app.data.backup.CreatedBackup
import com.distrigo.app.data.backup.RestoreCoordinator
import com.distrigo.app.data.backup.RestoreInstaller
import com.distrigo.app.data.backup.RestoreOutcome
import com.distrigo.app.data.backup.RestoreResult
import com.distrigo.app.data.local.database.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject

/** The last backup made of this data, from `app_meta`. */
data class LastBackup(val at: Instant, val size: Long?, val fileName: String)

/** A safety backup kept from an earlier restore. */
data class SafetyBackup(val file: File, val at: Instant, val size: Long)

/** What the screen is doing. Only [Idle] lets the user start something or leave. */
sealed class DataBackupState {
    data object Idle : DataBackupState()
    data class Working(val label: String) : DataBackupState()
    data class BackupSaved(val backup: CreatedBackup) : DataBackupState()
    data class Previewing(val preview: BackupPreview, val isSafetyBackup: Boolean) : DataBackupState()
    /** A restore is scheduled: the app must restart now. */
    data object RestartNeeded : DataBackupState()
    data class Failed(val message: String) : DataBackupState()
}

@HiltViewModel
class DataBackupViewModel @Inject constructor(
    private val db: AppDatabase,
    private val creator: BackupCreator,
    private val inspector: BackupInspector,
    private val coordinator: RestoreCoordinator,
    private val installer: RestoreInstaller,
) : ViewModel() {

    val lastBackup: StateFlow<LastBackup?> =
        db.appMetaDao().observe(listOf(BackupCreator.KEY_LAST_AT, BackupCreator.KEY_LAST_SIZE, BackupCreator.KEY_LAST_NAME))
            .map { rows ->
                val values = rows.associate { it.key to it.value }
                val at = values[BackupCreator.KEY_LAST_AT]?.let { runCatching { Instant.parse(it) }.getOrNull() }
                at?.let { LastBackup(it, values[BackupCreator.KEY_LAST_SIZE]?.toLongOrNull(), values[BackupCreator.KEY_LAST_NAME].orEmpty()) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow<DataBackupState>(DataBackupState.Idle)
    val state: StateFlow<DataBackupState> = _state.asStateFlow()

    private val _counts = MutableStateFlow<Map<String, Long>>(emptyMap())
    val counts: StateFlow<Map<String, Long>> = _counts.asStateFlow()

    private val _safetyBackups = MutableStateFlow<List<SafetyBackup>>(emptyList())
    val safetyBackups: StateFlow<List<SafetyBackup>> = _safetyBackups.asStateFlow()

    private val _lastRestore = MutableStateFlow<RestoreResult?>(null)
    /** How the last restore ended, until the user dismisses it. */
    val lastRestore: StateFlow<RestoreResult?> = _lastRestore.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _counts.value = inspector.currentCounts()
                _safetyBackups.value = coordinator.safetyBackups().map {
                    SafetyBackup(it, DataBackupFormatting.safetyBackupTime(it), it.length())
                }
                _lastRestore.value = installer.lastResult()
            }
        }
    }

    fun createBackup(destination: Uri) = run("Création de la sauvegarde…") {
        try {
            val created = creator.create(destination) { step ->
                _state.value = DataBackupState.Working(
                    when (step) {
                        BackupCreator.Step.COPYING_DATABASE -> "Copie des données…"
                        BackupCreator.Step.BUILDING_FILE -> "Préparation du fichier…"
                        BackupCreator.Step.SAVING -> "Enregistrement…"
                        BackupCreator.Step.VERIFYING -> "Vérification de la sauvegarde…"
                    }
                )
            }
            DataBackupState.BackupSaved(created)
        } catch (e: BackupFailedException) {
            DataBackupState.Failed(BackupMessages.of(e.reason))
        }
    }

    fun inspect(uri: Uri, isSafetyBackup: Boolean = false) = run("Vérification du fichier…") {
        when (val inspection = inspector.inspect(uri)) {
            is BackupInspection.Restorable -> DataBackupState.Previewing(inspection.preview, isSafetyBackup)
            is BackupInspection.NotRestorable -> DataBackupState.Failed(BackupMessages.of(inspection.problem))
        }
    }

    fun inspectSafetyBackup(backup: SafetyBackup) = inspect(Uri.fromFile(backup.file), isSafetyBackup = true)

    fun restore(preview: BackupPreview) = run("Vérification de la sauvegarde…") {
        val outcome = coordinator.restore(preview.uri, preview.manifest, preview.fileName, preview.fileSize) { step ->
            _state.value = DataBackupState.Working(
                when (step) {
                    RestoreCoordinator.Step.CHECKING_BACKUP -> "Vérification de la sauvegarde…"
                    RestoreCoordinator.Step.SAVING_CURRENT_DATA -> "Copie de sécurité de vos données actuelles…"
                    RestoreCoordinator.Step.SCHEDULING -> "Préparation du redémarrage…"
                }
            )
        }
        when (outcome) {
            is RestoreOutcome.Scheduled -> DataBackupState.RestartNeeded
            is RestoreOutcome.NotRestorable -> DataBackupState.Failed(BackupMessages.of(outcome.problem))
            is RestoreOutcome.SafetyBackupFailed -> DataBackupState.Failed(BackupMessages.safetyBackupFailed(outcome.reason))
        }
    }

    /** Back to the screen after a result, an error or a preview the user did not confirm. */
    fun dismiss() {
        if (_state.value !is DataBackupState.Working && _state.value != DataBackupState.RestartNeeded) {
            _state.value = DataBackupState.Idle
            refresh()
        }
    }

    fun dismissLastRestore() {
        _lastRestore.value = null
        viewModelScope.launch(Dispatchers.IO) { installer.clearResult() }
    }

    /** Runs [block] off the main thread, one operation at a time, showing [label] until it reports a step. */
    private fun run(label: String, block: suspend () -> DataBackupState) {
        if (_state.value is DataBackupState.Working || _state.value == DataBackupState.RestartNeeded) return
        _state.value = DataBackupState.Working(label)
        viewModelScope.launch {
            _state.value = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                android.util.Log.e("DataBackupViewModel", "operation failed", e)
                DataBackupState.Failed("Une erreur inattendue est survenue. Vos données n'ont pas changé.")
            }
        }
    }
}
