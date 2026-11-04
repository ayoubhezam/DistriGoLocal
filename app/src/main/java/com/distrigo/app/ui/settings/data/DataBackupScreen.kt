package com.distrigo.app.ui.settings.data

import com.distrigo.app.ui.common.layoutEnd
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import com.distrigo.app.ui.designsystem.DsInfo
import com.distrigo.app.ui.designsystem.DsInfoButton
import com.distrigo.app.ui.designsystem.DsInfoSheet
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.backup.AppRestart
import com.distrigo.app.data.backup.BackupFormat
import com.distrigo.app.data.backup.BackupMessages
import com.distrigo.app.data.backup.BackupPreview
import com.distrigo.app.data.backup.BackupJobResult
import com.distrigo.app.data.backup.BackupJobState
import com.distrigo.app.data.backup.RestoreResult
import com.distrigo.app.data.backup.auto.AutoBackupRunner
import com.distrigo.app.ui.common.relativeTime
import com.distrigo.app.ui.settings.settingsText
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import kotlinx.coroutines.delay
import java.time.Instant
import com.distrigo.app.data.backup.RestoreBusiness
import com.distrigo.app.data.backup.RestoreStockRule
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R
import androidx.compose.ui.res.pluralStringResource

/**
 * Paramètres → Sauvegarde et restauration: save all the data to a file, and replace it with a file saved before.
 *
 * Every operation runs in [DataBackupViewModel]; while one runs, the screen cannot be left, so a backup is
 * never abandoned half-way from here and a scheduled restore always reaches its restart.
 */
@Composable
fun DataBackupScreen(
    onBack: () -> Unit,
    /** Opens « Sauvegarde Google Drive ». */
    onCloud: () -> Unit = {},
    /** A backup the cloud screen downloaded, to restore here like a picked file; [onCloudRestoreTaken] once taken. */
    cloudRestore: String? = null,
    onCloudRestoreTaken: () -> Unit = {},
    viewModel: DataBackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val cloudAccount by viewModel.cloudAccount.collectAsState()
    LaunchedEffect(cloudRestore) {
        cloudRestore?.let {
            onCloudRestoreTaken()
            viewModel.inspect(Uri.parse(it))
        }
    }
    val lastBackup by viewModel.lastBackup.collectAsState()
    val counts by viewModel.counts.collectAsState()
    val safetyBackups by viewModel.safetyBackups.collectAsState()
    val lastRestore by viewModel.lastRestore.collectAsState()
    val autoStatus by viewModel.autoStatus.collectAsState()
    val job by viewModel.job.collectAsState()
    val running = job as? BackupJobState.Running
    // A failure stays on its card until the next attempt: the user may have been away when it ended
    val failed = (job as? BackupJobState.Done)?.let { done -> (done.result as? BackupJobResult.Failed)?.let { done.toFolder to it.message } }
    val context = LocalContext.current
    // The ⓘ open, if any
    var info by remember { mutableStateOf<DsInfo?>(null) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE)
    ) { uri -> uri?.let(viewModel::createBackup) }
    // A .distrigo file has no registered type, and each provider reports its own for it, so any file may be picked;
    // a file that is not a backup is refused with a message.
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.inspect(it) }
    }

    // Read again each time the screen shows: it is composed anew on the way back to it.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Turning daily backups on asks, once, to be allowed to notify failures; they are turned on either way.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.setAutoBackupEnabled(true)
    }
    val onToggleAutoBackup: (Boolean) -> Unit = { on ->
        if (on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setAutoBackupEnabled(on)
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::chooseAutoBackupFolder)
    }

    BackHandler {
        when (state) {
            is DataBackupState.Working, DataBackupState.RestartNeeded -> Unit
            is DataBackupState.Previewing -> viewModel.dismiss()
            is DataBackupState.ConfirmingRestore -> viewModel.cancelRestore()
            else -> onBack()
        }
    }


    when (val current = state) {
        DataBackupState.RestartNeeded -> {
            RestartingScreen()
            return
        }
        is DataBackupState.Previewing -> {
            RestorePreviewScreen(
                preview = current.preview,
                isSafetyBackup = current.isSafetyBackup,
                onCancel = viewModel::dismiss,
                onConfirmed = { viewModel.restore(current.preview) },
            )
            return
        }
        else -> Unit
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DsColors.SurfaceMuted)
        ) {
            DsTopAppBar(
                title = stringResource(R.string.settings_backup),
                leading = DsTopBarLeading.Back { if (state !is DataBackupState.Working) onBack() }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
            ) {
                lastRestore?.let { RestoreResultBanner(it, onDismiss = viewModel::dismissLastRestore) }

                ManualBackupCard(
                    lastBackup = lastBackup,
                    counts = counts,
                    running = running?.takeIf { !it.toFolder },
                    busy = running != null,
                    failure = failed?.takeIf { !it.first }?.second,
                    onInfo = { info = it },
                    onCreate = { createLauncher.launch(BackupFormat.fileName(Instant.now())) }
                )

                AutoBackupCard(
                    status = autoStatus,
                    running = running?.takeIf { it.toFolder },
                    busy = running != null,
                    failure = failed?.takeIf { it.first }?.second,
                    onInfo = { info = it },
                    onToggle = onToggleAutoBackup,
                    onChooseFolder = { folderLauncher.launch(null) },
                    onBackupNow = viewModel::backupNow,
                    onOpenAppSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    },
                )

                CloudCard(account = cloudAccount, enabled = running == null, onOpen = onCloud)

                // No restore while a backup is being written
                RestoreCard(enabled = running == null, onInfo = { info = it }, onPick = { openLauncher.launch(arrayOf("*/*")) })

                if (safetyBackups.isNotEmpty()) {
                    SafetyBackupsCard(safetyBackups, enabled = running == null, onInfo = { info = it }, onRestore = viewModel::inspectSafetyBackup)
                }

                Spacer(Modifier.height(DsSpacing.lg))
            }
        }

        when (val current = state) {
            is DataBackupState.Working -> WorkingOverlay(current.label)
            is DataBackupState.BackupSaved -> BackupSavedDialog(current.result, current.toFolder, onDismiss = viewModel::dismiss)
            is DataBackupState.Failed -> FailedDialog(current.message, onDismiss = viewModel::dismiss)
            is DataBackupState.ConfirmingRestore -> RestoreWarningDialog(
                rule = current.rule,
                otherBusiness = current.otherBusiness,
                onCancel = viewModel::cancelRestore,
                onConfirm = { viewModel.confirmRestore(current) },
            )
            else -> Unit
        }
        info?.let { DsInfoSheet(it, onDismiss = { info = null }) }
    }
}

// ── Cards ──
// The owner's design (2026-10-21): one card per job, its title and a line at most; what used to be explained in
// full on the screen is behind each title's ⓘ. Only what needs acting on now — a failed night, notifications off —
// stays on the card.

private val INFO_MANUAL: DsInfo get() = DsInfo(
    title = settingsText(R.string.backup_manual),
    icon = Icons.Default.CloudUpload,
    tint = DsColors.Primary,
    summary = settingsText(R.string.backup_manual_summary),
    toKnow = listOf(
        settingsText(R.string.backup_manual_know_1),
        settingsText(R.string.backup_manual_know_2),
    ),
)

private val INFO_AUTO: DsInfo get() = DsInfo(
    title = settingsText(R.string.backup_auto),
    icon = Icons.Default.History,
    tint = DsColors.AccentViolet,
    summary = settingsText(R.string.backup_auto_summary),
    toKnow = listOf(
        settingsText(R.string.backup_auto_know_1, AutoBackupRunner.KEEP),
        settingsText(R.string.backup_auto_know_2),
    ),
)

private val INFO_RESTORE: DsInfo get() = DsInfo(
    title = settingsText(R.string.backup_restore),
    icon = Icons.Default.SettingsBackupRestore,
    tint = DsColors.AccentTeal,
    summary = settingsText(R.string.backup_restore_summary),
    toKnow = listOf(
        settingsText(R.string.backup_restore_know_1),
        settingsText(R.string.backup_restore_know_2),
    ),
)

private val INFO_SAFETY: DsInfo get() = DsInfo(
    title = settingsText(R.string.backup_safety),
    icon = Icons.Default.Storage,
    tint = DsColors.AccentViolet,
    summary = settingsText(R.string.backup_safety_summary),
    toKnow = listOf(
        settingsText(R.string.backup_safety_know_1),
        settingsText(R.string.backup_safety_know_2),
    ),
)

/** A card of the screen: its icon tile, its title with its ⓘ, a [subtitle], then [content]. */
@Composable
private fun BackupCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    info: DsInfo,
    onInfo: (DsInfo) -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(DsShapes.large).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark, modifier = Modifier.weight(1f, fill = false))
                    DsInfoButton(title) { onInfo(info) }
                }
                subtitle?.let { Text(it, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary) }
            }
        }
        content()
    }
}

@Composable
private fun ManualBackupCard(
    lastBackup: LastBackup?,
    counts: Map<String, Long>,
    /** This card's backup, running in the background. */
    running: BackupJobState.Running?,
    /** A backup of either kind is running: one at a time. */
    busy: Boolean,
    failure: String?,
    onInfo: (DsInfo) -> Unit,
    onCreate: () -> Unit,
) {
    BackupCard(Icons.Default.CloudUpload, DsColors.Primary, stringResource(R.string.backup_manual), INFO_MANUAL, onInfo) {
        Column(Modifier.padding(start = 52.dp + DsSpacing.md)) {
            Text(stringResource(R.string.backup_last), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            if (lastBackup == null) {
                Text(stringResource(R.string.backup_none_yet), fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.Warning)
            } else {
                val relative = relativeTime(lastBackup.at.toString())?.let { com.distrigo.app.core.text.AppStrings.write(it) }
                Text(
                    DataBackupFormatting.dateTime(lastBackup.at) + (relative?.let { " ($it)" } ?: ""),
                    fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary
                )
            }
            if (counts.isNotEmpty()) {
                Text(DataBackupFormatting.countsSummary(counts), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
        }
        failure?.let { Notice(Icons.Default.WarningAmber, DsColors.DangerLight, DsColors.Danger, stringResource(R.string.backup_last_failed, it)) }
        if (running != null) {
            JobProgress(running.step)
        } else {
            Button(
                onClick = onCreate,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = DsShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.PrimaryDark)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(stringResource(R.string.backup_create), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A backup running in the background: its step, and that the screen may be left. */
@Composable
private fun JobProgress(step: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.PrimaryLight)
            .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(color = DsColors.Primary, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(step, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark)
            Text(stringResource(R.string.backup_can_leave), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
    }
}

@Composable
private fun AutoBackupCard(
    status: AutoBackupStatus,
    running: BackupJobState.Running?,
    busy: Boolean,
    failure: String?,
    onInfo: (DsInfo) -> Unit,
    onToggle: (Boolean) -> Unit,
    onChooseFolder: () -> Unit,
    onBackupNow: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val folder = status.folder
    val state = status.state
    val lost = folder.chosen && !folder.available
    BackupCard(Icons.Default.History, DsColors.AccentViolet, stringResource(R.string.backup_auto), INFO_AUTO, onInfo) {
        // The switch on its own line, under the title: beside it with the ⓘ, the title broke in two
        Row(Modifier.padding(start = 52.dp + DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.backup_every_night), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
            Switch(
                checked = state.enabled,
                onCheckedChange = onToggle,
                // Turning on needs a folder that can be used; turning off is always possible.
                enabled = state.enabled || (folder.chosen && folder.available),
                colors = SwitchDefaults.colors(checkedTrackColor = DsColors.Primary)
            )
        }
        // The folder, as a row to change it
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DsShapes.large)
                .background(if (lost) DsColors.WarningLight else DsColors.SurfaceMuted)
                .clickable(onClickLabel = stringResource(if (folder.chosen) R.string.backup_change_folder else R.string.backup_choose_the_folder), onClick = onChooseFolder)
                .padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (lost) Icons.Default.WarningAmber else Icons.Default.Folder, contentDescription = null,
                tint = if (lost) DsColors.Warning else DsColors.Primary, modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.backup_folder), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                Text(
                    when {
                        !folder.chosen -> stringResource(R.string.backup_choose_a_folder)
                        lost -> stringResource(R.string.backup_folder_lost)
                        else -> folder.name ?: stringResource(R.string.backup_folder_chosen)
                    },
                    fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold,
                    color = if (!folder.chosen) DsColors.Primary else DsColors.TextPrimary
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DsColors.TextTertiary)
        }

        // Where the nights stand, once they run
        if (state.enabled) {
            val last = state.lastAt?.takeIf { state.lastLocation == com.distrigo.app.data.backup.auto.BackupLocation.PICKED_FOLDER }
                ?.let { DataBackupFormatting.dateTimeWithAge(it) } ?: stringResource(R.string.backup_none_yet_lower)
            Text(stringResource(R.string.backup_last_value, last), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
            // Only after a night with nothing new: why « Dernière » may be old
            DataBackupFormatting.lastCheck(state)?.let { Text(stringResource(R.string.backup_checked_value, it), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary) }
            status.nextRunAt?.let { Text(stringResource(R.string.backup_next_value, DataBackupFormatting.dateTime(it)), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary) }
        }
        // What needs acting on, and only then
        state.lastProblem?.let { problem ->
            Notice(Icons.Default.WarningAmber, DsColors.WarningLight, DsColors.Warning, DataBackupFormatting.problem(problem, state.problemStreak))
        }
        if (status.overdue) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.WarningLight).padding(DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                Text(
                    stringResource(R.string.backup_overdue),
                    fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary
                )
                TextButton(onClick = onOpenAppSettings, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.backup_open_app_settings), color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (state.enabled && !status.notificationsAllowed) {
            Text(stringResource(R.string.backup_notifications_off), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
        failure?.let { Notice(Icons.Default.WarningAmber, DsColors.DangerLight, DsColors.Danger, stringResource(R.string.backup_last_failed, it)) }
        if (running != null) {
            JobProgress(running.step)
        } else if (folder.chosen && folder.available) {
            OutlinedButton(
                onClick = onBackupNow,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = DsShapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (busy) DsColors.Border else DsColors.Primary.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DsColors.Primary, disabledContentColor = DsColors.TextTertiary),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(stringResource(R.string.backup_now), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** « Sauvegarde Google Drive »: whether it is linked, and the way in. Its own screen holds the rest. */
@Composable
private fun CloudCard(account: String?, enabled: Boolean, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.Surface)
            .border(1.dp, DsColors.Border, DsShapes.large)
            .clickable(enabled = enabled, onClickLabel = stringResource(R.string.cloud_card_open), onClick = onOpen)
            .padding(DsSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).clip(DsShapes.large).background(DsColors.Primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.cloud_title), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.Bold, color = DsColors.PrimaryDark)
            Text(
                account?.let { stringResource(R.string.cloud_card_on, it) } ?: stringResource(R.string.cloud_card_off),
                fontSize = DsTextSize.bodySmall,
                color = if (account != null) DsColors.Success else DsColors.TextSecondary
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DsColors.TextTertiary)
    }
}

@Composable
private fun RestoreCard(enabled: Boolean, onInfo: (DsInfo) -> Unit, onPick: () -> Unit) {
    BackupCard(
        Icons.Default.SettingsBackupRestore, DsColors.AccentTeal, stringResource(R.string.backup_restore), INFO_RESTORE, onInfo,
        subtitle = stringResource(R.string.backup_restore_sub),
    ) {
        if (!enabled) Text(stringResource(R.string.backup_available_after), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        Button(
            onClick = onPick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = DsShapes.medium,
            // Grey while a backup runs, so it does not look tappable
            colors = ButtonDefaults.buttonColors(
                containerColor = DsColors.PrimaryLight, contentColor = DsColors.Primary,
                disabledContainerColor = DsColors.SurfaceSunken, disabledContentColor = DsColors.TextTertiary,
            )
        ) {
            Icon(Icons.Default.RestorePage, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text(stringResource(R.string.backup_restore_one), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SafetyBackupsCard(backups: List<SafetyBackup>, enabled: Boolean, onInfo: (DsInfo) -> Unit, onRestore: (SafetyBackup) -> Unit) {
    BackupCard(
        Icons.Default.Storage, DsColors.AccentViolet, stringResource(R.string.backup_safety), INFO_SAFETY, onInfo,
        subtitle = stringResource(R.string.backup_safety_sub),
    ) {
        Column(Modifier.fillMaxWidth().clip(DsShapes.large).border(1.dp, DsColors.Border, DsShapes.large)) {
            backups.forEachIndexed { index, backup ->
                if (index > 0) HorizontalDivider(color = DsColors.Border)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = DsSpacing.md, end = DsSpacing.sm, top = DsSpacing.sm, bottom = DsSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(DsSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(if (com.distrigo.app.data.backup.RestoreCoordinator.isImportBackup(backup.file)) R.string.backup_before_import else R.string.backup_before_restore),
                            fontSize = DsTextSize.caption, color = DsColors.TextSecondary
                        )
                        Text(DataBackupFormatting.dateTime(backup.at), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                        Text(DataBackupFormatting.size(backup.size), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                    }
                    TextButton(
                        onClick = { onRestore(backup) },
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = DsColors.PrimaryLight, contentColor = DsColors.Primary,
                            disabledContainerColor = DsColors.SurfaceSunken, disabledContentColor = DsColors.TextTertiary,
                        ),
                        shape = DsShapes.medium,
                    ) {
                        Text(stringResource(R.string.common_action_restore), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoreResultBanner(result: RestoreResult, onDismiss: () -> Unit) {
    val (background, tint, icon) = if (result.installed) {
        Triple(DsColors.SuccessLight, DsColors.Success, Icons.Default.CheckCircle)
    } else {
        Triple(DsColors.DangerLight, DsColors.Danger, Icons.Default.ErrorOutline)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(background).padding(DsSpacing.lg),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(BackupMessages.of(result), fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary)
            result.backupCreatedAt?.takeIf { result.installed }?.let {
                Text(stringResource(R.string.backup_of_date, DataBackupFormatting.dateTime(it)), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_action_ok), color = tint, fontWeight = FontWeight.SemiBold) }
    }
}

// ── Restore preview ──

@Composable
private fun RestorePreviewScreen(
    preview: BackupPreview,
    isSafetyBackup: Boolean,
    onCancel: () -> Unit,
    onConfirmed: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    val manifest = preview.manifest

    Column(Modifier.fillMaxSize().background(DsColors.SurfaceMuted)) {
        DsTopAppBar(title = stringResource(R.string.backup_restore_this), leading = DsTopBarLeading.Back(onCancel))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.large).padding(DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                Text(
                    stringResource(if (isSafetyBackup) R.string.backup_safety_of_date else R.string.backup_of_date, DataBackupFormatting.dateTime(manifest.createdAt)),
                    fontSize = DsTextSize.title, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary
                )
                DetailLine(stringResource(R.string.backup_phone), manifest.deviceModel.ifEmpty { stringResource(R.string.backup_unknown) }.let { model -> if (preview.fromThisPhone) stringResource(R.string.backup_this_phone, model) else model })
                if (!isSafetyBackup && preview.fileName.isNotEmpty()) {
                    DetailLine(stringResource(R.string.backup_file), listOfNotNull(preview.fileName, preview.fileSize?.let(DataBackupFormatting::size)).joinToString(" · "))
                }
                DetailLine(stringResource(R.string.backup_photos), if (preview.photosIncluded) preview.photoCount.toString() else stringResource(R.string.backup_photos_not_included))
                if (manifest.appVersion.isNotEmpty()) DetailLine(stringResource(R.string.backup_version), manifest.appVersion)
            }

            if (!preview.sameDatabase) {
                Notice(
                    Icons.Default.WarningAmber, DsColors.WarningLight, DsColors.Warning,
                    stringResource(R.string.backup_other_install)
                )
            }
            if (preview.needsUpgrade) {
                Notice(
                    Icons.Default.Info, DsColors.PrimaryLight, DsColors.Primary,
                    stringResource(R.string.backup_needs_upgrade)
                )
            }

            CountsTable(preview)
        }

        // The warning sits with the button rather than at the end of the scroll, so it is on screen whenever
        // the button is.
        Column(
            modifier = Modifier.fillMaxWidth().padding(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
        ) {
            Notice(
                Icons.Default.WarningAmber, DsColors.DangerLight, DsColors.Danger,
                stringResource(R.string.backup_replace_warning)
            )
            Button(
                onClick = { confirming = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = DsShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.Danger)
            ) {
                Text(stringResource(R.string.common_action_restore), fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_action_cancel), color = DsColors.TextSecondary)
            }
        }
    }

    if (confirming) {
        TypedConfirmationDialog(
            onDismiss = { confirming = false },
            onConfirmed = { confirming = false; onConfirmed() }
        )
    }
}

@Composable
private fun CountsTable(preview: BackupPreview) {
    Column(Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.Surface).border(1.dp, DsColors.Border, DsShapes.large)) {
        Row(Modifier.fillMaxWidth().background(DsColors.SurfaceSunken).padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm)) {
            Text("", Modifier.weight(1.4f))
            Text(stringResource(R.string.backup_column_backup), Modifier.weight(1f), fontSize = DsTextSize.caption, color = DsColors.TextSecondary, textAlign = layoutEnd())
            Text(stringResource(R.string.backup_column_now), Modifier.weight(1f), fontSize = DsTextSize.caption, color = DsColors.TextSecondary, textAlign = layoutEnd())
        }
        preview.headline.forEach { (table, counts) ->
            val (inBackup, now) = counts
            HorizontalDivider(color = DsColors.Border)
            Row(Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm)) {
                Text(DataBackupFormatting.tableLabel(table), Modifier.weight(1.4f), fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary)
                Text(inBackup.toString(), Modifier.weight(1f), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold,
                    color = DsColors.TextPrimary, textAlign = layoutEnd())
                Text(now.toString(), Modifier.weight(1f), fontSize = DsTextSize.bodySmall,
                    color = if (now > inBackup) DsColors.Danger else DsColors.TextSecondary, textAlign = layoutEnd())
            }
        }
    }
}

@Composable
private fun TypedConfirmationDialog(onDismiss: () -> Unit, onConfirmed: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val word = stringResource(R.string.backup_confirm_word)
    val matches = typed.trim().equals(word, ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Text(stringResource(R.string.backup_confirm_body, word))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    shape = DsShapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = dsTextFieldColors(focusedBorderColor = DsColors.Danger, unfocusedBorderColor = DsColors.Border)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmed, enabled = matches) {
                Text(stringResource(R.string.common_action_restore), color = if (matches) DsColors.Danger else DsColors.TextTertiary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_action_cancel)) } },
        containerColor = DsColors.Surface,
        titleContentColor = DsColors.TextPrimary,
        textContentColor = DsColors.TextSecondary
    )
}


// ── Progress, results, restart ──

@Composable
private fun WorkingOverlay(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            // Swallows taps, so nothing behind can be started while this runs.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(DsSpacing.xxl).clip(DsShapes.large).background(DsColors.Surface).padding(DsSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
        ) {
            CircularProgressIndicator(color = DsColors.Primary)
            Text(label, fontSize = DsTextSize.body, color = DsColors.TextPrimary, textAlign = TextAlign.Center)
            Text(stringResource(R.string.backup_do_not_leave), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
    }
}

/** A backup seen running here ended: where it went, and what to know about it. */
@Composable
private fun BackupSavedDialog(result: BackupJobResult.Saved, toFolder: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (result.inPrivateStorage) Icons.Default.WarningAmber else Icons.Default.CheckCircle, contentDescription = null,
                tint = if (result.inPrivateStorage) DsColors.Warning else DsColors.Success
            )
        },
        title = { Text(stringResource(R.string.backup_saved)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Text(result.folderName?.let { stringResource(R.string.backup_saved_in, result.fileName, DataBackupFormatting.size(result.size), it) }
                    ?: stringResource(R.string.backup_saved_here, result.fileName, DataBackupFormatting.size(result.size)))
                if (!toFolder) Text(pluralStringResource(R.plurals.backup_photos_included, result.photos, result.photos), fontSize = DsTextSize.bodySmall)
                if (result.photosLeftOut > 0) {
                    Text(pluralStringResource(R.plurals.backup_photos_left_out, result.photosLeftOut, result.photosLeftOut),
                        fontSize = DsTextSize.bodySmall, color = DsColors.Warning)
                }
                if (result.removed > 0) {
                    Text(pluralStringResource(R.plurals.backup_old_removed, result.removed, result.removed), fontSize = DsTextSize.bodySmall)
                }
                if (result.inPrivateStorage) {
                    Text(
                        stringResource(R.string.backup_kept_private),
                        fontSize = DsTextSize.bodySmall, color = DsColors.Warning
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_action_ok), color = DsColors.Primary, fontWeight = FontWeight.SemiBold) } },
        containerColor = DsColors.Surface,
        titleContentColor = DsColors.TextPrimary,
        textContentColor = DsColors.TextSecondary
    )
}

/**
 * Before a restore brings another business's data (docs/license_l4_checkin.md §7) or changes the stock rule: whose
 * data it is; the backup's rule against the phone's, products already below zero under a strict one (in the switch's
 * words), and a backup too old to have the rule. Asked after the backup is unpacked and checked, before anything
 * changes; "Annuler" leaves the phone as it was.
 */
@Composable
private fun RestoreWarningDialog(rule: RestoreStockRule, otherBusiness: RestoreBusiness?, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(if (otherBusiness != null) R.string.backup_other_business_title else R.string.backup_rule_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                otherBusiness?.let { Text(it.line(), fontSize = DsTextSize.bodySmall) }
                if (rule.worthTelling) rule.lines().forEach { Text(it, fontSize = DsTextSize.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.common_action_restore), color = DsColors.Primary, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.common_action_cancel)) } },
        containerColor = DsColors.Surface,
        titleContentColor = DsColors.TextPrimary,
        textContentColor = DsColors.TextSecondary
    )
}

@Composable
private fun FailedDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DsColors.Danger) },
        title = { Text(stringResource(R.string.backup_impossible)) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_action_ok), color = DsColors.Primary, fontWeight = FontWeight.SemiBold) } },
        containerColor = DsColors.Surface,
        titleContentColor = DsColors.TextPrimary,
        textContentColor = DsColors.TextSecondary
    )
}

/** Shown once a restore is scheduled; the app restarts after a moment, and the restore installs as it starts. */
@Composable
private fun RestartingScreen() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        delay(1_500)
        context.findActivity()?.let(AppRestart::restart)
    }
    Column(
        modifier = Modifier.fillMaxSize().background(DsColors.Surface).padding(DsSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = DsColors.Primary)
        Spacer(Modifier.height(DsSpacing.lg))
        Text(stringResource(R.string.backup_restarting), fontSize = DsTextSize.title, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark)
        Spacer(Modifier.height(DsSpacing.sm))
        Text(
            stringResource(R.string.backup_restarting_body),
            fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, textAlign = TextAlign.Center
        )
    }
}

// ── Small pieces ──

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.padding(end = DsSpacing.md), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        Text(value, Modifier.weight(1f), fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, textAlign = layoutEnd())
    }
}

@Composable
private fun Notice(icon: ImageVector, background: Color, tint: Color, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(background).padding(DsSpacing.lg),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(DsSpacing.md))
        Text(text, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
