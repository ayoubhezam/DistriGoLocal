package com.distrigo.app.ui.settings.print

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.data.print.ConnectionMethod
import com.distrigo.app.data.print.PaperSize
import com.distrigo.app.data.print.PrintLanguage
import com.distrigo.app.data.print.PrintSettings
import com.distrigo.app.ui.common.FileImage
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.settings.receipt.BusinessSettingsViewModel
import kotlinx.coroutines.launch

/**
 * "Reçus et impression" — what the receipt says, and how it comes out.
 *
 * This replaces the old "Paramètres du reçu" screen, whose logo/name/phone form is the first section
 * here unchanged. The two were merged because two sibling settings cards whose names differ by one
 * word, both about the same piece of paper, leave a user who wants to change the logo with two
 * plausible places to look and no way to choose. See docs/print_architecture.md §3.
 *
 * The section order is the dependency order — the content feeds the preview, the preview shows the
 * paper — and the headers do the explaining, so nobody has to guess which section holds the logo.
 *
 * **Two stores behind one screen.** The content section writes the `business_settings` Room row, which
 * travels with backups and with the sync postponed to phase 4, because every device of a business
 * prints the same header. The printer section writes `no_backup/print/settings.json`, which must
 * *not* travel: a MAC address belongs to this handset, and a restored backup that carried one would
 * hand a second phone a pairing it never had. The two halves look alike here and are deliberately
 * stored nothing alike.
 */
@Composable
fun ReceiptAndPrintSettingsScreen(
    onBack    : () -> Unit,
    onPrinters: () -> Unit,
    businessViewModel: BusinessSettingsViewModel = hiltViewModel(),
    printViewModel   : PrintSettingsViewModel    = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val settings by businessViewModel.settings.collectAsState()
    var name  by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    // The fields start from the stored settings once they have loaded, and are the user's after that.
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(settings) {
        val current = settings ?: return@LaunchedEffect
        if (!loaded) {
            name = current.name
            phone = current.phone ?: ""
            loaded = true
        }
    }
    // Each logo is stored under its own content hash, so a new one is a new file and draws afresh.
    val logoFile = settings?.logoPath?.let { java.io.File(it) }
    var isSaving by remember { mutableStateOf(false) }

    val printSettings by printViewModel.settings.collectAsState()
    val preview       by printViewModel.preview.collectAsState()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // Downscaled and stored off the main thread (see BusinessSettingsRepository.saveLogo). The
            // previous logo stays until the new one is stored, and stays if the new one cannot be read.
            scope.launch {
                if (!businessViewModel.saveLogo(it)) {
                    Toast.makeText(context, "Image illisible", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun save() {
        isSaving = true
        scope.launch {
            businessViewModel.saveIdentity(name, phone)
            Toast.makeText(context, "Paramètres enregistrés", Toast.LENGTH_SHORT).show()
            isSaving = false
            onBack()
        }
    }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsColors.Surface)
    ) {
        DsTopAppBar(
            title   = "Reçus et impression",
            leading = DsTopBarLeading.Back(onBack)
        )

        // The screen-wide inset sits on the scrolling body, not the root, so the bar runs edge to edge.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(DsSpacing.lg)
        ) {

            // ══════════ CONTENU DU REÇU ══════════
            SectionHeader("Contenu du reçu", "Ce qui est imprimé en haut de chaque reçu")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(DsShapes.large)
                    .background(DsColors.SurfaceMuted)
                    .clickable { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (logoFile != null) {
                    // Drawn by Coil, sized to this box and off the main thread.
                    key(logoFile.path) {
                        FileImage(
                            file               = logoFile,
                            contentDescription = null,
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.Crop
                        ) { }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint     = DsColors.Primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(DsSpacing.xs))
                        Text("Ajouter un logo", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
                    }
                }
            }

            // Explicit controls rather than only the tap-to-change box, because the box can offer one
            // action and there are two — and "remove the logo entirely" is not something a user can
            // guess is available by looking at a picture of their logo.
            if (logoFile != null) {
                Spacer(Modifier.height(DsSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
                ) {
                    OutlinedButton(
                        onClick  = { imagePicker.launch("image/*") },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape    = DsShapes.medium,
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(DsSpacing.xs))
                        Text("Changer", fontSize = DsTextSize.bodySmall, color = DsColors.Primary)
                    }
                    OutlinedButton(
                        onClick  = {
                            scope.launch {
                                businessViewModel.removeLogo()
                                Toast.makeText(context, "Logo supprimé", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape    = DsShapes.medium,
                        border   = androidx.compose.foundation.BorderStroke(1.dp, DsColors.DangerLight),
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = DsColors.Danger, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(DsSpacing.xs))
                        Text("Supprimer", fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                    }
                }
            }

            Spacer(Modifier.height(DsSpacing.md))

            FieldLabel("Nom du commerce")
            OutlinedTextField(
                value           = name,
                onValueChange   = { name = it },
                placeholder     = { Text("Ex: DISTRIGO", fontSize = DsTextSize.body) },
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth(),
                shape           = DsShapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                colors = dsTextFieldColors(
                    unfocusedBorderColor = DsColors.Border,
                    focusedBorderColor   = DsColors.Primary
                )
            )

            Spacer(Modifier.height(DsSpacing.md))

            // Printed under the business name. Optional: left empty the receipt omits the row rather
            // than printing an empty one.
            FieldLabel("Téléphone")
            OutlinedTextField(
                value           = phone,
                onValueChange   = { phone = it },
                placeholder     = { Text("Ex: 0555 12 34 56", fontSize = DsTextSize.body) },
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth(),
                shape           = DsShapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                colors = dsTextFieldColors(
                    unfocusedBorderColor = DsColors.Border,
                    focusedBorderColor   = DsColors.Primary
                )
            )

            Spacer(Modifier.height(DsSpacing.xxl))

            // ══════════ IMPRIMANTE ══════════
            //
            // Saved as they are tapped, not on the button below — these are this phone's settings, not
            // a form, and a method the user switched to should already be in force if they leave.
            SectionHeader("Imprimante", "Propre à ce téléphone — non inclus dans les sauvegardes")

            FieldLabel("Méthode de connexion")
            ChoiceRow(
                options  = ConnectionMethod.entries,
                selected = printSettings.method,
                label    = { it.label },
                onSelect = printViewModel::setMethod,
            )

            Spacer(Modifier.height(DsSpacing.md))

            FieldLabel("Imprimante")
            PrinterRow(
                selectedName = printSettings.selectedPrinter?.displayName,
                onClick      = onPrinters,
            )

            Spacer(Modifier.height(DsSpacing.md))

            FieldLabel("Format du papier")
            ChoiceRow(
                options  = PaperSize.entries,
                selected = printSettings.effectivePaper,
                label    = { it.label },
                onSelect = printViewModel::setPaper,
            )
            Spacer(Modifier.height(DsSpacing.xs))
            Text(
                settingScope(printSettings, printSettings.effectivePaper.kindLabel),
                fontSize = DsTextSize.caption,
                color    = DsColors.TextTertiary,
            )

            Spacer(Modifier.height(DsSpacing.md))

            FieldLabel("Langage d'impression")
            ChoiceRow(
                options  = PrintLanguage.entries,
                selected = printSettings.effectiveLanguage,
                label    = { it.label },
                onSelect = printViewModel::setLanguage,
            )
            Spacer(Modifier.height(DsSpacing.xs))
            Text(
                settingScope(printSettings, printSettings.effectiveLanguage.note),
                fontSize = DsTextSize.caption,
                color    = DsColors.TextTertiary,
            )

            Spacer(Modifier.height(DsSpacing.xxl))

            // ══════════ APERÇU ══════════
            SectionHeader("Aperçu", "Exemple de reçu — la largeur et la mise en page sont réelles")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DsShapes.large)
                    .background(DsColors.SurfaceSunken)
                    .padding(vertical = DsSpacing.lg, horizontal = DsSpacing.md),
            ) {
                val paper = preview.paper
                if (paper == null) {
                    A4PreviewPlaceholder()
                } else {
                    ThermalReceiptPreview(rasters = preview.rasters, paper = paper)
                }
            }

            paperCaption(preview)?.let { caption ->
                Spacer(Modifier.height(DsSpacing.sm))
                Text(
                    caption,
                    fontSize = DsTextSize.caption,
                    color    = DsColors.TextTertiary,
                )
            }

            Spacer(Modifier.height(DsSpacing.xxl))

            Button(
                onClick  = { save() },
                enabled  = !isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = DsShapes.medium,
                colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        "Enregistrer",
                        fontSize   = DsTextSize.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White
                    )
                }
            }

            Spacer(Modifier.height(DsSpacing.lg))
        }
    }
}

/**
 * Says who a hardware setting applies to, appended to its own description.
 *
 * Without this the per-printer model is invisible and surprising: two printers with different paper,
 * one screen, and no clue that the chips followed the selection rather than the app.
 */
private fun settingScope(settings: PrintSettings, description: String): String =
    settings.selectedPrinter
        ?.let { "$description · réglage de ${it.displayName}" }
        ?: "$description · défaut pour les nouvelles imprimantes"

/** The numbers the layout was built against, stated so a surprising preview can be checked against them. */
private fun paperCaption(preview: PreviewState): String? {
    val paper = preview.paper ?: return null
    return "${paper.charsPerLine} caractères par ligne · ${paper.dotsPerLine} points · ${paper.dpi} dpi"
}

/**
 * The chosen printer, and the way to the list.
 *
 * Reads "Aucune" rather than hiding itself when nothing is chosen: an absent row says nothing at all
 * and leaves the user wondering which setting picks the device, while a row that names its emptiness
 * says "this exists and is not set yet" and offers the tap that sets it.
 */
@Composable
private fun PrinterRow(selectedName: String?, onClick: () -> Unit) {
    val hasPrinter = selectedName != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(DsShapes.medium)
            .background(DsColors.SurfaceMuted)
            .border(1.dp, DsColors.Border, DsShapes.medium)
            .clickable { onClick() }
            .padding(horizontal = DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Print,
            contentDescription = null,
            tint     = if (hasPrinter) DsColors.Primary else DsColors.TextTertiary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(DsSpacing.sm))
        Text(
            selectedName ?: "Aucune",
            fontSize   = DsTextSize.bodySmall,
            fontWeight = if (hasPrinter) FontWeight.Medium else FontWeight.Normal,
            color      = if (hasPrinter) DsColors.TextPrimary else DsColors.TextTertiary,
            modifier   = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint     = DsColors.TextTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(bottom = DsSpacing.md)) {
        Text(
            title.uppercase(),
            fontSize   = DsTextSize.caption,
            fontWeight = FontWeight.Bold,
            color      = DsColors.TextTertiary,
        )
        Spacer(Modifier.height(2.dp))
        Text(subtitle, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = DsTextSize.bodySmall,
        color    = DsColors.TextSecondary,
        modifier = Modifier.padding(bottom = DsSpacing.xs)
    )
}

/**
 * A segmented row of mutually exclusive choices.
 *
 * A row rather than a dropdown because every one of these has two or three options whose labels are
 * short: laying them out flat shows what the alternatives are without a tap, which is what makes the
 * preview below feel like it answers a question.
 */
@Composable
private fun <T> ChoiceRow(
    options : List<T>,
    selected: T,
    label   : (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
    ) {
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
                    fontSize   = DsTextSize.bodySmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (isSelected) DsColors.Primary else DsColors.TextSecondary,
                )
            }
        }
    }
}
