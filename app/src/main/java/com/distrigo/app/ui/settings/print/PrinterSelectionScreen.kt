package com.distrigo.app.ui.settings.print

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.distrigo.app.core.format.westernDigits
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
import com.distrigo.app.data.print.ConnectionMethod
import com.distrigo.app.data.print.PaperProfile
import com.distrigo.app.data.print.PaperSize
import com.distrigo.app.data.print.RasterBenchmark
import com.distrigo.app.data.print.discovery.WifiState
import com.distrigo.app.data.print.transport.NetworkAddress
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
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

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
    val state by viewModel.state.collectAsStateWithLifecycle()

    var renaming  by remember { mutableStateOf<SavedPrinter?>(null) }
    var menuFor   by remember { mutableStateOf<SavedPrinter?>(null) }
    var configuring by remember { mutableStateOf<SavedPrinter?>(null) }
    var addingByAddress by remember { mutableStateOf(false) }

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
            PrintFailure.NO_NETWORK ->
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            // Nothing for the app to do: no radio, or nothing chosen yet and the list is already open.
            PrintFailure.NO_BLUETOOTH, PrintFailure.NOT_CONFIGURED -> Unit
        }
        viewModel.dismissNotice()
    }

    BackHandler { onBack() }

    Column(
        modifier = Modifier.fillMaxSize().background(DsColors.SurfaceMuted)
    ) {
        DsTopAppBar(
            title   = stringResource(R.string.settings_printers),
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
                ListHeader(stringResource(R.string.settings_printers_saved))
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

            if (state.method == ConnectionMethod.WIFI) {
                // The network's name is not needed to print — a printer is reached by address — but an
                // unreachable printer's usual cause is the phone having drifted onto mobile data or the
                // neighbour's router, and that is invisible unless it is said out loud.
                ListHeader(stringResource(R.string.settings_printers_network))
                NetworkNote(state.wifi)

                Spacer(Modifier.height(DsSpacing.md))
                OutlinedButton(
                    onClick  = { addingByAddress = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = DsShapes.medium,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text(stringResource(R.string.settings_add_by_ip), fontSize = DsTextSize.bodySmall, color = DsColors.Primary)
                }
            } else {
                ListHeader(stringResource(R.string.settings_paired_devices))
                if (state.bonded.isEmpty()) {
                    EmptyNote(
                        stringResource(R.string.settings_no_paired)
                    )
                } else {
                    state.bonded.forEach { device ->
                        DiscoveredRow(device) { viewModel.save(device) }
                        Spacer(Modifier.height(DsSpacing.sm))
                    }
                }
            }

            Spacer(Modifier.height(DsSpacing.lg))

            ListHeader(stringResource(R.string.settings_search))
            ScanButton(
                wifi     = state.method == ConnectionMethod.WIFI,
                scanning = state.scanning,
                onScan   = {
                    when {
                        // A network sweep needs no permission at all — just a subnet to sweep.
                        state.method == ConnectionMethod.WIFI -> viewModel.startScan()
                        !BluetoothSppTransport.hasScanPermission(context) ->
                            permission.launch(BluetoothSppTransport.scanPermissions())
                        else -> viewModel.startScan()
                    }
                },
                onStop = viewModel::stopScan,
            )
            if (state.method == ConnectionMethod.WIFI && !state.scanning && !viewModel.canScanNetwork()) {
                Spacer(Modifier.height(DsSpacing.sm))
                EmptyNote(stringResource(R.string.settings_connect_wifi))
            }

            if (state.discovered.isNotEmpty()) {
                Spacer(Modifier.height(DsSpacing.sm))
                state.discovered.forEach { device ->
                    DiscoveredRow(device) { viewModel.save(device) }
                    Spacer(Modifier.height(DsSpacing.sm))
                }
            } else if (state.scanning) {
                Spacer(Modifier.height(DsSpacing.sm))
                EmptyNote(
                    stringResource(if (state.method == ConnectionMethod.WIFI) R.string.settings_searching_network else R.string.settings_searching)
                )
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
            onBenchmark = { menuFor = null; viewModel.benchmarkRaster(printer) },
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

    if (addingByAddress) {
        NetworkAddressDialog(
            onConfirm = { host, port ->
                addingByAddress = false
                viewModel.addByAddress(host, port)
            },
            onDismiss = { addingByAddress = false },
        )
    }

    state.notice?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::dismissNotice,
            title   = { Text(stringResource(R.string.settings_printer)) },
            text    = { Text(text) },
            confirmButton = { TextButton(onClick = viewModel::dismissNotice) { Text(stringResource(R.string.common_action_ok)) } },
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
        connecting        -> stringResource(R.string.settings_connecting) to DsColors.TextSecondary
        failure != null   -> stringResource(R.string.settings_not_connected_retry) to DsColors.Danger
        !isSelected       -> null
        link is PrinterLink.Ready   -> stringResource(R.string.settings_printer_ready) to DsColors.Success
        link is PrinterLink.Idle    -> stringResource(R.string.settings_printer_paired) to DsColors.TextSecondary
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
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.settings_cd_selected), tint = DsColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DsSpacing.sm))
        }
        IconButton(onClick = onMenu, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.settings_cd_options), tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DiscoveredRow(device: DiscoveredDevice, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsColors.Surface)
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
            Text(stringResource(R.string.settings_is_printer), fontSize = DsTextSize.caption, color = DsColors.Primary)
        }
    }
}

@Composable
private fun ScanButton(wifi: Boolean, scanning: Boolean, onScan: () -> Unit, onStop: () -> Unit) {
    OutlinedButton(
        onClick  = if (scanning) onStop else onScan,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape    = DsShapes.medium,
    ) {
        if (scanning) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DsColors.Primary)
            Spacer(Modifier.width(DsSpacing.sm))
            Text(stringResource(R.string.settings_stop_search), fontSize = DsTextSize.bodySmall, color = DsColors.Primary)
        } else {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text(
                stringResource(if (wifi) R.string.settings_search_network else R.string.settings_search_printers),
                fontSize = DsTextSize.bodySmall,
                color    = DsColors.Primary,
            )
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
    onBenchmark: () -> Unit,
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
            SheetAction(stringResource(R.string.settings_print_test), stringResource(R.string.settings_print_test_sub), enabled = !busy, onClick = onTest)
            SheetAction(
                stringResource(R.string.settings_raster_test),
                stringResource(R.string.settings_raster_test_sub, benchmarkPaperMm(printer).toString()),
                enabled = !busy,
                onClick = onBenchmark,
            )
            SheetAction(stringResource(R.string.settings_probe), stringResource(R.string.settings_probe_sub), enabled = !busy, onClick = onProbe)
            SheetAction(stringResource(R.string.settings_format_language_encoding), "${printer.paper.label} · ${printer.language.label} · ${printer.codePage.label}", onClick = onConfigure)
            SheetAction(stringResource(R.string.settings_rename), null, onClick = onRename)
            SheetAction(stringResource(R.string.common_action_delete), null, destructive = true, onClick = onRemove)
        }
    }
}

/** Roughly how much paper the benchmark will use, so the menu can say before it starts. */
private fun benchmarkPaperMm(printer: SavedPrinter): Int =
    PaperProfile.of(printer.paper)?.let { RasterBenchmark.paperMillimetres(it) / 10 } ?: 0

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
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                stringResource(R.string.settings_confirm_with_test),
                fontSize = DsTextSize.bodySmall,
                color    = DsColors.TextSecondary,
            )
            Spacer(Modifier.height(DsSpacing.lg))

            // A4 is absent on purpose. It is not a thermal width at all — it takes the PDF path and
            // Android's print dialog — so offering it for a Bluetooth receipt printer would be offering
            // a setting that cannot work.
            ConfigGroup(stringResource(R.string.settings_paper_format)) {
                PrinterChoiceRow(PaperSize.entries.filter { it.isThermal }, live.paper, { it.label }) {
                    viewModel.configure(live.id, paper = it)
                }
            }
            ConfigGroup(stringResource(R.string.settings_language)) {
                PrinterChoiceRow(PrintLanguage.entries, live.language, { it.label }) {
                    viewModel.configure(live.id, language = it)
                }
            }
            ConfigGroup(stringResource(R.string.settings_encoding)) {
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

/** The current network, or why it cannot be named. */
@Composable
private fun NetworkNote(wifi: WifiState?) {
    val (text, colour) = when (wifi) {
        is WifiState.Connected    -> stringResource(R.string.settings_network_name, wifi.ssid) to DsColors.TextPrimary
        WifiState.Off             -> stringResource(R.string.settings_wifi_off) to DsColors.Danger
        WifiState.NotConnected    -> stringResource(R.string.settings_wifi_not_connected) to DsColors.Danger
        // The SSID is gated behind the location permission because knowing the network is knowing
        // roughly where you are. Printing works without it, so this explains rather than demands.
        is WifiState.Unnamed -> when (wifi.reason) {
            WifiState.Unnamed.Reason.PERMISSION ->
                stringResource(R.string.settings_wifi_hidden_permission) to DsColors.TextSecondary
            WifiState.Unnamed.Reason.LOCATION_OFF ->
                stringResource(R.string.settings_wifi_hidden_location) to DsColors.TextSecondary
            WifiState.Unnamed.Reason.UNAVAILABLE ->
                stringResource(R.string.settings_wifi_connected) to DsColors.TextPrimary
        }
        null -> return
    }
    Text(text, fontSize = DsTextSize.bodySmall, color = colour)
}

/**
 * Address entry for a network printer.
 *
 * The port is pre-filled with 9100 and rarely touched — it is what virtually every network receipt
 * printer listens on — but it is editable, because the one that does not would otherwise be
 * unreachable with no way to say so.
 */
@Composable
private fun NetworkAddressDialog(onConfirm: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(NetworkAddress.DEFAULT_PORT.toString()) }
    val parsedPort = port.toIntOrNull()
    val valid = host.isNotBlank() && parsedPort != null && parsedPort in 1..65535

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_network_printer)) },
        text  = {
            Column {
                Text(
                    stringResource(R.string.settings_ip_hint),
                    fontSize = DsTextSize.caption,
                    color    = DsColors.TextSecondary,
                )
                Spacer(Modifier.height(DsSpacing.md))
                OutlinedTextField(
                    value         = host,
                    onValueChange = { host = westernDigits(it) },
                    label         = { Text(stringResource(R.string.settings_ip_address)) },
                    placeholder   = { Text("192.168.1.50") },
                    singleLine    = true,
                    shape         = DsShapes.medium,
                    modifier      = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(DsSpacing.sm))
                OutlinedTextField(
                    value         = port,
                    onValueChange = { port = westernDigits(it).filter(Char::isDigit).take(5) },
                    label         = { Text(stringResource(R.string.settings_port)) },
                    singleLine    = true,
                    shape         = DsShapes.medium,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(host, parsedPort ?: NetworkAddress.DEFAULT_PORT) }) {
                Text(stringResource(R.string.common_action_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_action_cancel)) } },
    )
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_rename_printer)) },
        text  = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                singleLine    = true,
                shape         = DsShapes.medium,
                modifier      = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(stringResource(R.string.common_action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_action_cancel)) } },
    )
}
