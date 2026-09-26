package com.distrigo.app.ui.settings.diagnostics

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.distrigo.app.BuildConfig
import com.distrigo.app.diagnostics.DiagnosticReport
import com.distrigo.app.diagnostics.DiagnosticReports
import com.distrigo.app.diagnostics.ReportKind
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Paramètres → Diagnostic: the reports the app wrote about its own trouble — crashes, freezes (ANR), native
 * crashes, kills at the front, and in debug builds StrictMode's notes. They stay on the phone; Partager hands
 * one to another app when someone needs to read it.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Resolved on IO with the reads: filesDir touches the disk, which StrictMode rightly flagged here.
    val dir: suspend () -> java.io.File = { withContext(Dispatchers.IO) { DiagnosticReports.dir(context) } }
    var reports by remember { mutableStateOf<List<DiagnosticReport>?>(null) }
    var open by remember { mutableStateOf<DiagnosticReport?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    fun reload() = scope.launch { val d = dir(); reports = withContext(Dispatchers.IO) { DiagnosticReports.list(d) } }
    LaunchedEffect(Unit) { reload() }

    open?.let { report ->
        ReportDetail(
            report   = report,
            onBack   = { open = null },
            onShare  = { share(context, report) },
            onDelete = {
                scope.launch {
                    withContext(Dispatchers.IO) { DiagnosticReports.delete(report) }
                    open = null
                    reload()
                }
            },
        )
        return
    }

    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = "Diagnostic", leading = DsTopBarLeading.Back(onBack)) {
            if (!reports.isNullOrEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Tout effacer", tint = DsColors.TextSecondary)
                }
            }
        }
        Text(
            "Les plantages, blocages (ANR) et arrêts anormaux de l'application, enregistrés sur ce téléphone.",
            fontSize = DsTextSize.caption, color = DsColors.TextSecondary,
            modifier = Modifier.padding(horizontal = DsSpacing.lg),
        )
        if (BuildConfig.DEBUG) DebugTriggers()
        Spacer(Modifier.height(DsSpacing.sm))

        val list = reports
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DsColors.Primary) }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucun rapport", color = DsColors.TextSecondary)
            }
            else -> LazyColumn(
                contentPadding      = PaddingValues(horizontal = DsSpacing.lg, vertical = DsSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
            ) {
                items(list, key = { it.file.name }) { report -> ReportRow(report) { open = report } }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Effacer tous les rapports ?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { val d = dir(); withContext(Dispatchers.IO) { DiagnosticReports.clear(d) }; reload() }
                }) { Text("Effacer", color = DsColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}

private val WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault())

private fun colorOf(kind: ReportKind) = when (kind) {
    ReportKind.STRICT_MODE -> DsColors.Warning
    else                   -> DsColors.Danger
}

@Composable
private fun ReportRow(report: DiagnosticReport, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = DsShapes.medium, color = DsColors.SurfaceMuted) {
        Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(colorOf(report.kind).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.BugReport, contentDescription = null, tint = colorOf(report.kind), modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("${report.kind.label} · ${WHEN.format(report.at)}", fontSize = DsTextSize.caption, color = colorOf(report.kind), fontWeight = FontWeight.SemiBold)
                Text(report.title, fontSize = DsTextSize.body, color = DsColors.TextPrimary, maxLines = 2)
            }
        }
    }
}

/** A report in full, a line at a time: an ANR's thread dump runs to thousands of lines. */
@Composable
private fun ReportDetail(report: DiagnosticReport, onBack: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    BackHandler { onBack() }
    var lines by remember(report) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(report) { lines = withContext(Dispatchers.IO) { DiagnosticReports.read(report).lines() } }

    Column(Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = report.kind.label, leading = DsTopBarLeading.Back(onBack)) {
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Partager", tint = DsColors.Primary) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer", tint = DsColors.Danger) }
        }
        val text = lines
        if (text == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DsColors.Primary) }
        } else {
            SelectionContainer {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
                    contentPadding = PaddingValues(DsSpacing.md),
                ) {
                    items(text.size) { i ->
                        Text(text[i], fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, color = DsColors.TextPrimary, softWrap = false)
                    }
                }
            }
        }
    }
}

/** Debug builds: set off each kind of trouble on purpose, to see it reported. */
@Composable
private fun DebugTriggers() {
    var confirm by remember { mutableStateOf<String?>(null) }
    Row(Modifier.padding(horizontal = DsSpacing.lg, vertical = DsSpacing.xs), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        OutlinedButton(onClick = { confirm = "crash" }) { Text("Plantage de test", fontSize = 12.sp) }
        OutlinedButton(onClick = { confirm = "anr" }) { Text("Bloquer 30 s (ANR)", fontSize = 12.sp) }
    }
    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (what == "crash") "Faire planter l'application ?" else "Bloquer l'application 30 s ?") },
            text = {
                Text(
                    if (what == "crash") "DEBUG — l'application se ferme ; rouvrez-la et revenez ici pour lire le rapport."
                    else "DEBUG — touchez l'écran pendant le blocage : Android propose de fermer l'application. Fermez-la, rouvrez-la et revenez ici."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (what == "crash") throw IllegalStateException("Plantage de test demandé depuis l'écran Diagnostic")
                    else Thread.sleep(30_000)
                }) { Text("Continuer", color = DsColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
    }
}

private fun share(context: Context, report: DiagnosticReport) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", report.file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "DistriGo — ${report.kind.label} — ${report.title}")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Partager le rapport"))
}
