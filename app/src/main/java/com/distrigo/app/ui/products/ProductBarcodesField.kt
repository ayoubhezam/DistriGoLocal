package com.distrigo.app.ui.products

import com.distrigo.app.core.format.westernDigits
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.local.entity.MAX_BARCODES_PER_PRODUCT
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors

/**
 * The product form's other barcodes, under the primary one, in a single row however many there are (the owner's
 * request, 2026-10-15): a new code is typed or scanned, then added; the list button beside it, with their number,
 * opens them all in a sheet, where each can be removed or made the primary (the star swaps it with the primary
 * field). The form checks a code is not already on it, and the save that no other product has it. The counter
 * includes the primary code, as the limit does.
 */
@Composable
fun OtherBarcodesField(
    primary       : String,
    codes         : List<String>,
    newCode       : String,
    onNewCode     : (String) -> Unit,
    error         : String,
    onAdd         : () -> Unit,
    onScan        : () -> Unit,
    onRemove      : (String) -> Unit,
    onMakePrimary : (String) -> Unit,
) {
    var listShown by rememberSaveable { mutableStateOf(false) }
    // The code just added, said under the field until the next one is typed
    var added by rememberSaveable { mutableStateOf<String?>(null) }
    var countBefore by rememberSaveable { mutableIntStateOf(codes.size) }
    LaunchedEffect(codes.size) {
        added = if (codes.size > countBefore) codes.lastOrNull() else null
        countBefore = codes.size
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Text("Autres codes-barres", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
            Text("${codes.size + 1} / $MAX_BARCODES_PER_PRODUCT", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value           = newCode,
                onValueChange   = {
                    added = null
                    onNewCode(westernDigits(it))
                },
                // One line: beside the scan, « + » and the list, the field is narrow
                placeholder     = { Text("Ajouter un code", fontSize = DsTextSize.body, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine      = true,
                isError         = error.isNotEmpty(),
                modifier        = Modifier.weight(1f),
                shape           = DsShapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
                trailingIcon    = {
                    IconButton(onClick = onScan) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner un autre code-barres", tint = DsColors.PrimaryDark)
                    }
                },
                colors          = dsTextFieldColors(
                    unfocusedBorderColor = DsColors.Border,
                    focusedBorderColor   = DsColors.Primary
                )
            )
            FilledTonalIconButton(
                onClick  = onAdd,
                modifier = Modifier.size(48.dp),
                shape    = DsShapes.medium,
                colors   = IconButtonDefaults.filledTonalIconButtonColors(containerColor = DsColors.PrimaryDark, contentColor = DsColors.Surface)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter ce code-barres")
            }
            // The list beside the field, with how many codes it holds
            if (codes.isNotEmpty()) {
                FilledTonalIconButton(
                    onClick  = { listShown = true },
                    modifier = Modifier.size(48.dp),
                    shape    = DsShapes.medium,
                    colors   = IconButtonDefaults.filledTonalIconButtonColors(containerColor = DsColors.PrimaryLight, contentColor = DsColors.Primary)
                ) {
                    BadgedBox(badge = { Badge(containerColor = DsColors.Primary, contentColor = DsColors.Surface) { Text("${codes.size}") } }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = if (codes.size == 1) "Voir l'autre code-barres" else "Voir les ${codes.size} autres codes-barres")
                    }
                }
            }
        }
        when {
            error.isNotEmpty() -> Text(error, fontSize = DsTextSize.caption, color = DsColors.Danger, modifier = Modifier.padding(top = 4.dp))
            // Only while it is still in the list: removed or made the primary, it no longer applies
            added != null && added in codes -> Text("« $added » ajouté.", fontSize = DsTextSize.caption, color = DsColors.Success, modifier = Modifier.padding(top = 4.dp))
        }
    }

    if (listShown) {
        BarcodesSheet(
            primary       = primary,
            codes         = codes,
            onRemove      = { code ->
                onRemove(code)
                // The last one gone: nothing left to show
                if (codes.size <= 1) listShown = false
            },
            onMakePrimary = onMakePrimary,
            onDismiss     = { listShown = false },
        )
    }
}

/** Every code of the product: the primary first, then the others, each to remove or make the primary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarcodesSheet(
    primary       : String,
    codes         : List<String>,
    onRemove      : (String) -> Unit,
    onMakePrimary : (String) -> Unit,
    onDismiss     : () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = DsColors.Surface,
    ) {
        Column(Modifier.padding(horizontal = DsSpacing.lg).padding(bottom = DsSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = DsSpacing.md)) {
                Text("Codes-barres du produit", fontSize = DsTextSize.title, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark, modifier = Modifier.weight(1f))
                Text("${codes.size + 1} / $MAX_BARCODES_PER_PRODUCT", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(DsSpacing.xs)) {
                if (primary.isNotBlank()) {
                    item(key = "primary") { BarcodeRow(primary, isPrimary = true) }
                }
                items(codes, key = { it }) { code ->
                    BarcodeRow(code, isPrimary = false, onMakePrimary = { onMakePrimary(code) }, onRemove = { onRemove(code) })
                }
            }
        }
    }
}

/** One code: the primary with its filled star and no action; another with its star (make primary) and its cross. */
@Composable
private fun BarcodeRow(code: String, isPrimary: Boolean, onMakePrimary: () -> Unit = {}, onRemove: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .border(1.dp, if (isPrimary) DsColors.Primary else DsColors.Border, DsShapes.medium)
            .background(if (isPrimary) DsColors.PrimaryLight else DsColors.Surface)
            .padding(start = DsSpacing.md)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.QrCode, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(DsSpacing.sm))
        Text(code, fontSize = DsTextSize.body, color = DsColors.TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (isPrimary) {
            Icon(Icons.Default.Star, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(DsSpacing.xs))
            Text("Principal", fontSize = DsTextSize.caption, fontWeight = FontWeight.SemiBold, color = DsColors.Primary, modifier = Modifier.padding(end = DsSpacing.md))
        } else {
            IconButton(onClick = onMakePrimary) {
                Icon(Icons.Default.StarBorder, contentDescription = "Définir comme code principal", tint = DsColors.Primary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Retirer ce code-barres", tint = DsColors.Danger, modifier = Modifier.size(20.dp))
            }
        }
    }
}
