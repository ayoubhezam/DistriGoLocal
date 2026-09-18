package com.distrigo.app.ui.settings.data

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
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.FileUpload
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
import com.distrigo.app.data.backup.CreatedBackup
import com.distrigo.app.data.backup.RestoreResult
import com.distrigo.app.data.backup.auto.AutoBackupOutcome
import com.distrigo.app.data.backup.auto.AutoBackupRunner
import com.distrigo.app.ui.common.formatRelativeFr
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Paramètres → Données et sauvegarde: save all the data to a file, and replace it with a file saved before.
 *
 * Every operation runs in [DataBackupViewModel]; while one runs, the screen cannot be left, so a backup is
 * never abandoned half-way from here and a scheduled restore always reaches its restart.
 */
@Composable
fun DataBackupScreen(
    onBack: () -> Unit,
    viewModel: DataBackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val lastBackup by viewModel.lastBackup.collectAsState()
    val counts by viewModel.counts.collectAsState()
    val safetyBackups by viewModel.safetyBackups.collectAsState()
    val lastRestore by viewModel.lastRestore.collectAsState()
    val autoStatus by viewModel.autoStatus.collectAsState()
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE)
    ) { uri -> uri?.let(viewModel::createBackup) }
    // A .distrigo file has no registered type, and each provider reports its own for it, so any file may be picked;
    // a file that is not a backup is refused with a message.
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.inspect(it) }
    }

    // Read again each time the screen shows, including on the way back from an export or an import.
    LaunchedEffect(showExport, showImport) { if (!showExport && !showImport) viewModel.refresh() }

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
            else -> onBack()
        }
    }

    if (showImport) {
        com.distrigo.app.ui.settings.data.importer.ImportScreen(onBack = { showImport = false })
        return
    }
    if (showExport) {
        com.distrigo.app.ui.settings.data.export.ExportScreen(onBack = { showExport = false })
        return
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
                .background(DsColors.Surface)
        ) {
            DsTopAppBar(
                title = "Données et sauvegarde",
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

                BackupSection(
                    lastBackup = lastBackup,
                    counts = counts,
                    onCreate = { createLauncher.launch(BackupFormat.fileName(Instant.now())) }
                )

                AutoBackupSection(
                    status = autoStatus,
                    onToggle = onToggleAutoBackup,
                    onChooseFolder = { folderLauncher.launch(null) },
                    onBackupNow = viewModel::backupNow,
                    onOpenAppSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    },
                )

                ExportSection(onOpen = { showExport = true }, onImport = { showImport = true })

                RestoreSection(onPick = { openLauncher.launch(arrayOf("*/*")) })

                if (safetyBackups.isNotEmpty()) {
                    SafetyBackupsSection(safetyBackups, onRestore = viewModel::inspectSafetyBackup)
                }

                Spacer(Modifier.height(DsSpacing.lg))
            }
        }

        when (val current = state) {
            is DataBackupState.Working -> WorkingOverlay(current.label)
            is DataBackupState.BackupSaved -> BackupSavedDialog(current.backup, onDismiss = viewModel::dismiss)
            is DataBackupState.AutoBackupSaved -> AutoBackupSavedDialog(current.outcome, onDismiss = viewModel::dismiss)
            is DataBackupState.Failed -> FailedDialog(current.message, onDismiss = viewModel::dismiss)
            else -> Unit
        }
    }
}

// ── Sections ──

@Composable
private fun BackupSection(lastBackup: LastBackup?, counts: Map<String, Long>, onCreate: () -> Unit) {
    SectionTitle("Sauvegarde")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.SurfaceMuted)
            .padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = if (lastBackup == null) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                background = if (lastBackup == null) DsColors.WarningLight else DsColors.SuccessLight,
                tint = if (lastBackup == null) DsColors.Warning else DsColors.Success,
            )
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Dernière sauvegarde", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                if (lastBackup == null) {
                    Text("Aucune sauvegarde pour le moment", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                } else {
                    val relative = formatRelativeFr(lastBackup.at.toString())
                    Text(
                        DataBackupFormatting.dateTime(lastBackup.at) + (relative?.let { " ($it)" } ?: ""),
                        fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary
                    )
                    Text(
                        listOfNotNull(lastBackup.fileName.takeIf { it.isNotEmpty() }, lastBackup.size?.let(DataBackupFormatting::size)).joinToString(" · "),
                        fontSize = DsTextSize.caption, color = DsColors.TextSecondary
                    )
                }
            }
        }
        if (counts.isNotEmpty()) {
            Text(DataBackupFormatting.countsSummary(counts), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        }
        Button(
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = DsShapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text("Créer une sauvegarde", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Text(
            "Le fichier contient toutes vos données et vos photos. Gardez-le hors du téléphone (Google Drive, ordinateur) pour le retrouver si le téléphone est perdu ou remplacé.",
            fontSize = DsTextSize.caption, color = DsColors.TextSecondary
        )
    }
}

@Composable
private fun AutoBackupSection(
    status: AutoBackupStatus,
    onToggle: (Boolean) -> Unit,
    onChooseFolder: () -> Unit,
    onBackupNow: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val folder = status.folder
    val state = status.state
    val lost = folder.chosen && !folder.available
    SectionTitle("Sauvegarde automatique")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.SurfaceMuted)
            .padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        // ── On/off ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sauvegarde chaque nuit", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                Text(
                    "Vers 3 h, seulement si les données ont changé",
                    fontSize = DsTextSize.caption, color = DsColors.TextSecondary
                )
            }
            Switch(
                checked = state.enabled,
                onCheckedChange = onToggle,
                // Turning on needs a folder that can be used; turning off is always possible.
                enabled = state.enabled || (folder.chosen && folder.available),
                colors = SwitchDefaults.colors(checkedTrackColor = DsColors.Primary)
            )
        }
        if (!folder.chosen) {
            Text("Choisissez d'abord le dossier où enregistrer les sauvegardes.", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }

        // ── Folder ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = if (lost) Icons.Default.WarningAmber else Icons.Default.Folder,
                background = if (lost) DsColors.WarningLight else DsColors.PrimaryLight,
                tint = if (lost) DsColors.Warning else DsColors.Primary,
            )
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Dossier", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                Text(
                    when {
                        !folder.chosen -> "Aucun dossier choisi"
                        lost -> "Dossier inaccessible"
                        else -> folder.name ?: "Dossier choisi"
                    },
                    fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary
                )
            }
        }

        // ── Status ──
        if (state.enabled) {
            StatusLine(
                "Dernière sauvegarde automatique",
                state.lastAt?.takeIf { state.lastLocation == com.distrigo.app.data.backup.auto.BackupLocation.PICKED_FOLDER }
                    ?.let { DataBackupFormatting.dateTimeWithAge(it) } ?: "Aucune pour le moment"
            )
            DataBackupFormatting.lastCheck(state)?.let { StatusLine("Dernière vérification", it) }
            status.nextRunAt?.let { StatusLine("Prochaine", "vers le " + DataBackupFormatting.dateTime(it)) }
        }
        state.lastProblem?.let { problem ->
            Notice(Icons.Default.WarningAmber, DsColors.WarningLight, DsColors.Warning, DataBackupFormatting.problem(problem, state.problemStreak))
        }
        if (status.overdue) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.WarningLight).padding(DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                Text(
                    "La sauvegarde de nuit n'a pas eu lieu depuis plus d'une journée. Android la retarde peut-être pour économiser la batterie : " +
                        "dans les paramètres de DistriGo, réglez la batterie sur « Non restreinte ».",
                    fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary
                )
                TextButton(onClick = onOpenAppSettings, contentPadding = PaddingValues(0.dp)) {
                    Text("Ouvrir les paramètres de DistriGo", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (state.enabled && !status.notificationsAllowed) {
            Text(
                "Les notifications de DistriGo sont désactivées : un échec ne sera signalé que sur cet écran.",
                fontSize = DsTextSize.caption, color = DsColors.TextSecondary
            )
        }

        // ── Actions ──
        OutlinedButton(
            onClick = onChooseFolder,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = DsShapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
        ) {
            Text(if (folder.chosen) "Changer de dossier" else "Choisir le dossier", fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        }
        if (folder.chosen && folder.available) {
            OutlinedButton(
                onClick = onBackupNow,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = DsShapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
            ) {
                Text("Sauvegarder maintenant", fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
            }
        }
        Text(
            "Les ${AutoBackupRunner.KEEP} dernières sauvegardes automatiques sont conservées dans ce dossier. Les autres fichiers du dossier ne sont jamais modifiés.",
            fontSize = DsTextSize.caption, color = DsColors.TextSecondary
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Column {
        Text(label, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        Text(value, fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary)
    }
}

@Composable
private fun ExportSection(onOpen: () -> Unit, onImport: () -> Unit) {
    SectionTitle("Export et import")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.SurfaceMuted)
            .padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        Text(
            "Ventes, achats, paiements, clients, produits, mouvements de stock, charges et pertes, en fichier Excel ou CSV, à ouvrir sur un ordinateur ou à envoyer au comptable.",
            fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary
        )
        OutlinedButton(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = DsShapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
        ) {
            Icon(Icons.Default.TableChart, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text("Exporter les données", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        }
        Text(
            "Ajoutez ou mettez à jour des produits et des clients depuis un fichier Excel : le fichier exporté sert de modèle.",
            fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary
        )
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = DsShapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text("Importer depuis Excel", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        }
    }
}

@Composable
private fun RestoreSection(onPick: () -> Unit) {
    SectionTitle("Restauration")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.SurfaceMuted)
            .padding(DsSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
    ) {
        Text(
            "Remplace toutes les données de ce téléphone par celles d'une sauvegarde. Vous verrez son contenu avant de confirmer, et une copie de sécurité de vos données actuelles sera faite d'abord.",
            fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary
        )
        OutlinedButton(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = DsShapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, DsColors.Border)
        ) {
            Icon(Icons.Default.RestorePage, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text("Restaurer une sauvegarde", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        }
    }
}

@Composable
private fun SafetyBackupsSection(backups: List<SafetyBackup>, onRestore: (SafetyBackup) -> Unit) {
    SectionTitle("Copies de sécurité")
    Text(
        "Faites automatiquement avant chaque restauration et chaque import. Restaurez-en une pour annuler une restauration ou un import. Elles sont supprimées si l'application est désinstallée.",
        fontSize = DsTextSize.caption, color = DsColors.TextSecondary
    )
    Column(
        modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.SurfaceMuted)
    ) {
        backups.forEachIndexed { index, backup ->
            if (index > 0) HorizontalDivider(color = DsColors.Border)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = DsColors.TextTertiary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(DsSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (com.distrigo.app.data.backup.RestoreCoordinator.isImportBackup(backup.file)) "Avant l'import du" else "Avant la restauration du",
                        fontSize = DsTextSize.caption, color = DsColors.TextSecondary
                    )
                    Text(DataBackupFormatting.dateTime(backup.at), fontSize = DsTextSize.body, color = DsColors.TextPrimary)
                    Text(DataBackupFormatting.size(backup.size), fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
                }
                TextButton(onClick = { onRestore(backup) }) {
                    Text("Restaurer", color = DsColors.Primary, fontWeight = FontWeight.SemiBold)
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
                Text("Sauvegarde du ${DataBackupFormatting.dateTime(it)}", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
            }
        }
        TextButton(onClick = onDismiss) { Text("OK", color = tint, fontWeight = FontWeight.SemiBold) }
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

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = "Restaurer cette sauvegarde ?", leading = DsTopBarLeading.Back(onCancel))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.lg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.SurfaceMuted).padding(DsSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)
            ) {
                Text(
                    (if (isSafetyBackup) "Copie de sécurité du " else "Sauvegarde du ") + DataBackupFormatting.dateTime(manifest.createdAt),
                    fontSize = DsTextSize.title, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary
                )
                DetailLine("Téléphone", manifest.deviceModel.ifEmpty { "Inconnu" } + if (preview.fromThisPhone) " (ce téléphone)" else "")
                if (!isSafetyBackup && preview.fileName.isNotEmpty()) {
                    DetailLine("Fichier", listOfNotNull(preview.fileName, preview.fileSize?.let(DataBackupFormatting::size)).joinToString(" · "))
                }
                DetailLine("Photos", preview.photoCount.toString())
                if (manifest.appVersion.isNotEmpty()) DetailLine("Version", manifest.appVersion)
            }

            if (!preview.sameDatabase) {
                Notice(
                    Icons.Default.WarningAmber, DsColors.WarningLight, DsColors.Warning,
                    "Cette sauvegarde ne provient pas des données actuelles de ce téléphone : elle contient les données d'une autre installation de DistriGo."
                )
            }
            if (preview.needsUpgrade) {
                Notice(
                    Icons.Default.Info, DsColors.PrimaryLight, DsColors.Primary,
                    "Créée par une version antérieure de DistriGo : elle sera mise à jour pendant la restauration."
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
                "Toutes les données actuelles de ce téléphone seront remplacées. " +
                    "Une copie de sécurité en sera enregistrée d'abord, puis l'application redémarrera."
            )
            Button(
                onClick = { confirming = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = DsShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.Danger)
            ) {
                Text("Restaurer", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Annuler", color = DsColors.TextSecondary)
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
    Column(Modifier.fillMaxWidth().clip(DsShapes.large).border(1.dp, DsColors.Border, DsShapes.large)) {
        Row(Modifier.fillMaxWidth().background(DsColors.SurfaceSunken).padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm)) {
            Text("", Modifier.weight(1.4f))
            Text("Sauvegarde", Modifier.weight(1f), fontSize = DsTextSize.caption, color = DsColors.TextSecondary, textAlign = TextAlign.End)
            Text("Actuellement", Modifier.weight(1f), fontSize = DsTextSize.caption, color = DsColors.TextSecondary, textAlign = TextAlign.End)
        }
        preview.headline.forEach { (table, counts) ->
            val (inBackup, now) = counts
            HorizontalDivider(color = DsColors.Border)
            Row(Modifier.fillMaxWidth().padding(horizontal = DsSpacing.lg, vertical = DsSpacing.sm)) {
                Text(DataBackupFormatting.tableLabel(table), Modifier.weight(1.4f), fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary)
                Text(inBackup.toString(), Modifier.weight(1f), fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold,
                    color = DsColors.TextPrimary, textAlign = TextAlign.End)
                Text(now.toString(), Modifier.weight(1f), fontSize = DsTextSize.bodySmall,
                    color = if (now > inBackup) DsColors.Danger else DsColors.TextSecondary, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun TypedConfirmationDialog(onDismiss: () -> Unit, onConfirmed: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim().equals(CONFIRMATION_WORD, ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmer la restauration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Text("Vos données actuelles seront remplacées. Tapez $CONFIRMATION_WORD pour confirmer.")
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
                Text("Restaurer", color = if (matches) DsColors.Danger else DsColors.TextTertiary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        containerColor = DsColors.Surface,
        titleContentColor = DsColors.TextPrimary,
        textContentColor = DsColors.TextSecondary
    )
}

private const val CONFIRMATION_WORD = "RESTAURER"

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
            Text("Ne quittez pas l'application.", fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
    }
}

@Composable
private fun BackupSavedDialog(backup: CreatedBackup, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DsColors.Success) },
        title = { Text("Sauvegarde enregistrée") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Text("${backup.fileName} (${DataBackupFormatting.size(backup.size)}) a été enregistrée et vérifiée.")
                Text("${backup.manifest.images.size} photo(s) incluse(s).", fontSize = DsTextSize.bodySmall)
                val left = backup.missingPhotos + backup.damagedPhotos
                if (left > 0) {
                    Text("$left photo(s) introuvable(s) ou endommagée(s) sur le téléphone n'ont pas pu être incluses.",
                        fontSize = DsTextSize.bodySmall, color = DsColors.Warning)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = DsColors.Primary, fontWeight = FontWeight.SemiBold) } },
        containerColor = DsColors.Surface,
        titleContentColor = DsColors.TextPrimary,
        textContentColor = DsColors.TextSecondary
    )
}

@Composable
private fun AutoBackupSavedDialog(outcome: AutoBackupOutcome.Saved, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (outcome.inPrivateStorage) Icons.Default.WarningAmber else Icons.Default.CheckCircle, contentDescription = null,
                tint = if (outcome.inPrivateStorage) DsColors.Warning else DsColors.Success
            )
        },
        title = { Text("Sauvegarde enregistrée") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Text("${outcome.fileName} (${DataBackupFormatting.size(outcome.size)}) a été enregistrée et vérifiée dans « ${outcome.folderName} ».")
                if (outcome.removed > 0) {
                    Text("${outcome.removed} ancienne(s) sauvegarde(s) automatique(s) supprimée(s).", fontSize = DsTextSize.bodySmall)
                }
                if (outcome.inPrivateStorage) {
                    Text(
                        "Le dossier choisi n'est pas accessible : cette copie est restée dans l'application et sera perdue si l'application est désinstallée.",
                        fontSize = DsTextSize.bodySmall, color = DsColors.Warning
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = DsColors.Primary, fontWeight = FontWeight.SemiBold) } },
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
        title = { Text("Opération impossible") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = DsColors.Primary, fontWeight = FontWeight.SemiBold) } },
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
        Text("Redémarrage de DistriGo…", fontSize = DsTextSize.title, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
        Spacer(Modifier.height(DsSpacing.sm))
        Text(
            "La sauvegarde sera installée au démarrage. Si l'application ne se rouvre pas d'elle-même, ouvrez-la.",
            fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, textAlign = TextAlign.Center
        )
    }
}

// ── Small pieces ──

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
}

@Composable
private fun IconBadge(icon: ImageVector, background: Color, tint: Color) {
    Box(Modifier.size(40.dp).clip(DsShapes.medium).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.padding(end = DsSpacing.md), fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        Text(value, Modifier.weight(1f), fontSize = DsTextSize.bodySmall, color = DsColors.TextPrimary, textAlign = TextAlign.End)
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
