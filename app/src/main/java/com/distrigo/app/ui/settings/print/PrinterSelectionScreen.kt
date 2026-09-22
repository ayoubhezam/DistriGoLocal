package com.distrigo.app.ui.settings.print

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.print.PaperSize
import com.distrigo.app.data.print.PrintLanguage
import com.distrigo.app.data.print.PrinterLink
import com.distrigo.app.data.print.SavedPrinter
import com.distrigo.app.data.print.discovery.DiscoveredDevice
import com.distrigo.app.data.print.lang.PrinterCodePage
import com.distrigo.app.data.print.message
import com.distrigo.app.data.print.transport.BluetoothSppTransport
import com.distrigo.app.data.print.transport.PrintFailure
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading

/**
 * Choosing a printer.
 *
 * Ordered by how most users actually get here: **saved** first, then **paired** — devices already
 * bonded in Android's own Bluetooth settings, which need no scan and usually no permission prompt —
 * and only then a scan, for the minority who have not paired. Making the scan the headline action
 * would send every user through a permission dialog and a radio inquiry to find a device the phone
 * already knew about.
 *
 * Nothing here filters out non-printers. The Bluetooth class of a genuine printer is IMAGING, but the
 * inexpensive units our users buy often report UNCATEGORIZED, so likely printers are sorted to the top
 * and everything stays visible.
 */
@Composable
fun PrinterSelectionScreen(
    onBack   : () -> Unit,
    viewModel: PrinterSelectionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var renaming  by remember { mutableStateOf<SavedPrinter?>(null) }
    var menuFor   by remember { mutableStateOf<SavedPrinter?>(null) }
    var configuring by remember { mutableStateOf<SavedPrinter?>(null) }

    // Granting a Bluetooth permission does not restart the screen, so what the list could not read
    // before has to be re-read after.
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refresh() }

    // Turning the radio on is the system's dialog, not ours; the result lands the same way.
    val enableBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refresh() }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Each blocker's own remedy. The label in PrinterGate.message() and the action taken here have to
    // agree: a button that says "Réessayer" and opens the system settings instead is worse than no
    // button, because the user follows it and loses their place.
    fun resolve(failure: PrintFailure) {
        when (failure) {
            PrintFailure.PERMISSION_DENIED ->
                permission.launch(BluetoothSppTransport.scanPermissions())
            PrintFailure.ADAPTER_OFF ->
                enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            // Pairing is Android's job, not ours: a printer bonded anywhere else is bonded for us too,
            // and re-implementing the PIN flow would only be a worse copy of the system one.
            PrintFailure.NOT_PAIRED ->
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            // "Réessayer" / "Réimprimer" mean try the printer again, which is what probe does.
            PrintFailure.UNREACHABLE, PrintFailure.INTERRUPTED ->
                state.saved.firstOrNull { it.id == state.selectedId }?.let(viewModel::probe)
            // Nothing for the app to do: no radio, or nothing chosen yet and the list is already open.
            PrintFailure.NO_BLUETOOTH, PrintFailure.NOT_CONFIGURED -> Unit
        }
        viewModel.dismissNotice()
    }

    BackHandler { onBack() }

    Column(
        modifier = Modifier.fillMaxSize().background(DsColors.Surface)
    ) {
        DsTopAppBar(
            title   = "Imprimantes",
            leading = DsTopBarLeading.Back(onBack),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(DsSpacing.lg),
        ) {
            // ── Whatever is stopping printing, said once, at the top, with its own button ──
            (state.link as? PrinterLink.Blocked)?.let { blocked ->
                ProblemBanner(blocked.reason, onAction = { resolve(blocked.reason) })
                Spacer(Modifier.height(DsSpacing.lg))
            }

            if (state.saved.isNotEmpty()) {
                ListHeader("Enregistrées")
                state.saved.forEach { printer ->
                    SavedPrinterRow(
                        printer    = printer,
                        isSelected = printer.id == state.selectedId,
                        connecting = printer.id == state.connectingId,
                        failure    = state.failures[printer.id],
                        link       = state.link,
                        onSelect   = { viewModel.select(printer.id) },
                        onMenu     = { menuFor = printer },
                    )
                    Spacer(Modifier.height(DsSpacing.sm))
                }
                Spacer(Modifier.height(DsSpacing.lg))
            }

            ListHeader("Appareils jumelés")
            if (state.bonded.isEmpty()) {
                EmptyNote(
                    "Aucun appareil jumelé. Jumelez l'imprimante dans les réglages Bluetooth " +
                        "du téléphone, ou lancez une recherche."
                )
            } else {
                state.bonded.forEach { device ->
                    DiscoveredRow(device) { viewModel.save(device) }
                    Spacer(Modifier.height(DsSpacing.sm))
                }
            }

            Spacer(Modifier.height(DsSpacing.lg))

            ListHeader("Recherche")
            ScanButton(
                scanning = state.scanning,
                onScan   = {
                    if (!BluetoothSppTransport.hasScanPermission(context)) {
                        permission.launch(BluetoothSppTransport.scanPermissions())
                    } else {
                        viewModel.startScan()
                    }
                },
                onStop = viewModel::stopScan,
            )

            if (state.discovered.isNotEmpty()) {
                Spacer(Modifier.height(DsSpacing.sm))
                state.discovered.forEach { device ->
                    DiscoveredRow(device) { viewModel.save(device) }
                    Spacer(Modifier.height(DsSpacing.sm))
                }
            } else if (state.scanning) {
                Spacer(Modifier.height(DsSpacing.sm))
                EmptyNote("Recherche en cours… Assurez-vous que l'imprimante est allumée.")
            }

            Spacer(Modifier.height(DsSpacing.xxl))
        }
    }

    // ── Sheets and dialogs ──

    menuFor?.let { printer ->
        PrinterActionsSheet(
            printer   = printer,
            busy      = state.busy,
            onRename  = { menuFor = null; renaming = printer },
            onConfigure = { menuFor = null; configuring = printer },
            onTest    = { menuFor = null; viewModel.testPrint(printer) },
            onProbe   = { menuFor = null; viewModel.probe(printer) },
            onRemove  = { menuFor = null; viewModel.remove(printer.id) },
            onDismiss = { menuFor = null },
        )
    }

    renaming?.let { printer ->
        RenameDialog(
            initial   = printer.displayName,
            onConfirm = { name -> viewModel.rename(printer.id, name); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    configuring?.let { printer ->
        PrinterConfigSheet(
            printer   = printer,
            viewModel = viewModel,
            onDismiss = { configuring = null },
        )
    }

    state.notice?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::dismissNotice,
            title   = { Text("Imprimante") },
            text    = { Text(text) },
            confirmButton = { TextButton(onClick = viewModel::dismissNotice) { Text("OK") } },
        )
    }
}

// ─────────────────────────────── pieces ───────────────────────────────

@Composable
private fun ListHeader(text: String) {
    Text(
        text.uppercase(),
        fontSize   = DsTextSize.caption,
        fontWeight = FontWeight.Bold,
        color      = DsColors.TextTertiary,
        modifier   = Modifier.padding(bottom = DsSpacing.sm),
    )
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary)
}

/**
 * The blocker, with the one button that clears it.
 *
 * Every [PrintFailure] carries its own wording and action from [message], so this never has to say
 * "erreur" and leave the user to work out what to do.
 */
@Composable
private fun ProblemBanner(failure: PrintFailure, onAction: () -> Unit) {
    val msg = failure.message()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.large)
            .background(DsColors.WarningLight)
            .padding(DsSpacing.lg),
    ) {
        Text(msg.title, fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
        Spacer(Modifier.height(2.dp))
        Text(msg.detail, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        msg.action?.let { action ->
            Spacer(Modifier.height(DsSpacing.sm))
            Button(
                onClick = onAction,
                shape   = DsShapes.medium,
                colors  = ButtonDefaults.buttonColors(containerColor = DsColors.Warning),
            ) { Text(action, fontSize = DsTextSize.bodySmall, color = androidx.compose.ui.graphics.Color.White) }
        }
    }
}

@Composable
private fun SavedPrinterRow(
    printer   : SavedPrinter,
    isSelected: Boolean,
    connecting: Boolean,
    failure   : PrintFailure?,
    link      : PrinterLink,
    onSelect  : () -> Unit,
    onMenu    : () -> Unit,
) {
    // What this row can honestly claim. A printer nobody has connected to says nothing at all: the
    // gate has not looked at it, so a green dot would be a claim the app cannot make.
    val status: Pair<String, androidx.compose.ui.graphics.Color>? = when {
        connecting        -> "Connexion en cours…" to DsColors.TextSecondary
        failure != null   -> "Non connectée — appuyez pour réessayer" to DsColors.Danger
        !isSelected       -> null
        link is PrinterLink.Ready   -> "Prête" to DsColors.Success
        link is PrinterLink.Idle    -> "Jumelée" to DsColors.TextSecondary
        link is PrinterLink.Blocked -> link.reason.message().title to DsColors.Danger
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(if (isSelected) DsColors.PrimaryLight else DsColors.SurfaceMuted)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) DsColors.Primary else DsColors.Border,
                shape = DsShapes.medium,
            )
            // Disabled while connecting: the probe holds the radio, and a second tap would queue a
            // connection to a printer the user may already have changed their mind about.
            .clickable(enabled = !connecting) { onSelect() }
            .padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (connecting) {
            CircularProgressIndicator(
                modifier    = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color       = DsColors.Primary,
            )
        } else {
            Icon(
                Icons.Default.Print,
                contentDescription = null,
                tint     = if (isSelected) DsColors.Primary else DsColors.TextTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                printer.displayName,
                fontSize   = DsTextSize.body,
                fontWeight = FontWeight.SemiBold,
                color      = DsColors.TextPrimary,
            )
            Text(
                "${printer.paper.label} · ${printer.language.label} · ${printer.id}",
                fontSize = DsTextSize.caption,
                color    = DsColors.TextSecondary,
            )
            status?.let { (label, colour) ->
                Text(label, fontSize = DsTextSize.caption, color = colour, fontWeight = FontWeight.Medium)
            }
        }
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = "Sélectionnée", tint = DsColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DsSpacing.sm))
        }
        IconButton(onClick = onMenu, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DiscoveredRow(device: DiscoveredDevice, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsColors.SurfaceMuted)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .clickable { onAdd() }
            .padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint     = DsColors.Primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(device.name, fontSize = DsTextSize.body, color = DsColors.TextPrimary)
            Text(device.address, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
        // Stated rather than acted on: it decides the order, and the user decides the rest.
        if (device.looksLikePrinter) {
            Text("Imprimante ?", fontSize = DsTextSize.caption, color = DsColors.Primary)
        }
    }
}

@Composable
private fun ScanButton(scanning: Boolean, onScan: () -> Unit, onStop: () -> Unit) {
    OutlinedButton(
        onClick  = if (scanning) onStop else onScan,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape    = DsShapes.medium,
    ) {
        if (scanning) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DsColors.Primary)
            Spacer(Modifier.width(DsSpacing.sm))
            Text("Arrêter la recherche", fontSize = DsTextSize.bodySmall, color = DsColors.Primary)
        } else {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text("Rechercher des imprimantes", fontSize = DsTextSize.bodySmall, color = DsColors.Primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrinterActionsSheet(
    printer    : SavedPrinter,
    busy       : Boolean,
    onRename   : () -> Unit,
    onConfigure: () -> Unit,
    onTest     : () -> Unit,
    onProbe    : () -> Unit,
    onRemove   : () -> Unit,
    onDismiss  : () -> Unit,
) {
    // Two separate things kept "Supprimer" off the screen, and both are needed.
    //
    // skipPartiallyExpanded: left to itself the sheet opens at its half-height detent and the rows
    // simply run out past the bottom of its own background, into the navigation bar. It is a menu,
    // not a browsable surface — there is no reason for it to open half-open.
    //
    // navigationBarsPadding: even fully expanded, the sheet draws under the system navigation, so
    // the last row would sit beneath the gesture bar — visible but not tappable, the worst of both.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = DsColors.Surface,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DsSpacing.lg)
                .padding(bottom = DsSpacing.xxl)
        ) {
            Text(printer.displayName, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            Spacer(Modifier.height(DsSpacing.lg))
            SheetAction("Imprimer un test", "Confirme la largeur, les accents et le langage", enabled = !busy, onClick = onTest)
            SheetAction("Vérifier la connexion", "Se connecte sans rien imprimer", enabled = !busy, onClick = onProbe)
            SheetAction("Format, langage, encodage", "${printer.paper.label} · ${printer.language.label} · ${printer.codePage.label}", onClick = onConfigure)
            SheetAction("Renommer", null, onClick = onRename)
            SheetAction("Supprimer", null, destructive = true, onClick = onRemove)
        }
    }
}

@Composable
private fun SheetAction(
    title      : String,
    subtitle   : String?,
    enabled    : Boolean = true,
    destructive: Boolean = false,
    onClick    : () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = DsSpacing.md, horizontal = DsSpacing.sm),
    ) {
        Text(
            title,
            fontSize   = DsTextSize.body,
            fontWeight = FontWeight.Medium,
            color = when {
                !enabled    -> DsColors.TextTertiary
                destructive -> DsColors.Danger
                else        -> DsColors.TextPrimary
            },
        )
        subtitle?.let {
            Text(it, fontSize = DsTextSize.caption, color = DsColors.TextSecondary)
        }
    }
}

/**
 * The three hardware settings, per printer.
 *
 * They live here rather than on the settings screen because they belong to the machine: a rep with an
 * 80 mm unit at the counter and a 58 mm on the belt must not re-pick the width every time they switch.
 * The values on the settings screen are what a *newly added* printer starts from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrinterConfigSheet(
    printer  : SavedPrinter,
    viewModel: PrinterSelectionViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val live = state.saved.firstOrNull { it.id == printer.id } ?: printer

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = DsColors.Surface,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DsSpacing.lg)
                .padding(bottom = DsSpacing.xxl)
        ) {
            Text(live.displayName, fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.TextPrimary)
            Text(
                "À confirmer avec un test d'impression.",
                fontSize = DsTextSize.bodySmall,
                color    = DsColors.TextSecondary,
            )
            Spacer(Modifier.height(DsSpacing.lg))

            // A4 is absent on purpose. It is not a thermal width at all — it takes the PDF path and
            // Android's print dialog — so offering it for a Bluetooth receipt printer would be offering
            // a setting that cannot work.
            ConfigGroup("Format du papier") {
                PrinterChoiceRow(PaperSize.entries.filter { it.isThermal }, live.paper, { it.label }) {
                    viewModel.configure(live.id, paper = it)
                }
            }
            ConfigGroup("Langage") {
                PrinterChoiceRow(PrintLanguage.entries, live.language, { it.label }) {
                    viewModel.configure(live.id, language = it)
                }
            }
            ConfigGroup("Encodage") {
                PrinterChoiceRow(PrinterCodePage.entries, live.codePage, { it.storageCode.uppercase() }) {
                    viewModel.configure(live.id, codePage = it)
                }
            }
        }
    }
}

@Composable
private fun ConfigGroup(label: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = DsSpacing.lg)) {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
        content()
    }
}

@Composable
private fun <T> PrinterChoiceRow(
    options : List<T>,
    selected: T,
    label   : (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(DsShapes.medium)
                    .background(if (isSelected) DsColors.PrimaryLight else DsColors.SurfaceMuted)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) DsColors.Primary else DsColors.Border,
                        shape = DsShapes.medium,
                    )
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    fontSize   = DsTextSize.caption,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (isSelected) DsColors.Primary else DsColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renommer l'imprimante") },
        text  = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                singleLine    = true,
                shape         = DsShapes.medium,
                modifier      = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
