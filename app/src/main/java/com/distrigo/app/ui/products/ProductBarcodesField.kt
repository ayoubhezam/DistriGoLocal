package com.distrigo.app.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.distrigo.app.data.local.entity.MAX_BARCODES_PER_PRODUCT
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors

/**
 * The product form's list of other barcodes, under the primary one.
 *
 * Each code can be removed, or made the primary (the star), which swaps it with the primary field. A new
 * code is typed or scanned, then added; the form checks it is not already on the form, and the save checks
 * no other product has it. The counter includes the primary code, as the limit does.
 */
@Composable
fun OtherBarcodesField(
    codes         : List<String>,
    newCode       : String,
    onNewCode     : (String) -> Unit,
    error         : String,
    onAdd         : () -> Unit,
    onScan        : () -> Unit,
    onRemove      : (String) -> Unit,
    onMakePrimary : (String) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Text("Autres codes-barres", fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
            Text("${codes.size + 1} / $MAX_BARCODES_PER_PRODUCT", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
        }

        codes.forEach { code ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = DsSpacing.xs)
                    .clip(DsShapes.medium)
                    .border(1.dp, DsColors.Border, DsShapes.medium)
                    .background(DsColors.Surface)
                    .padding(start = DsSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(code, fontSize = DsTextSize.body, color = DsColors.TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onMakePrimary(code) }) {
                    Icon(Icons.Default.StarBorder, contentDescription = "Définir comme code principal", tint = DsColors.Primary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onRemove(code) }) {
                    Icon(Icons.Default.Close, contentDescription = "Retirer ce code-barres", tint = DsColors.Danger, modifier = Modifier.size(20.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value           = newCode,
                onValueChange   = onNewCode,
                placeholder     = { Text("Ajouter un code-barres", fontSize = DsTextSize.body) },
                singleLine      = true,
                isError         = error.isNotEmpty(),
                modifier        = Modifier.weight(1f),
                shape           = DsShapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
                colors          = dsTextFieldColors(
                    unfocusedBorderColor = DsColors.Border,
                    focusedBorderColor   = DsColors.Primary
                )
            )
            FilledTonalIconButton(
                onClick  = onScan,
                modifier = Modifier.size(48.dp),
                shape    = DsShapes.medium,
                colors   = IconButtonDefaults.filledTonalIconButtonColors(containerColor = DsColors.PrimaryLight, contentColor = DsColors.Primary)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner un autre code-barres")
            }
            FilledTonalIconButton(
                onClick  = onAdd,
                modifier = Modifier.size(48.dp),
                shape    = DsShapes.medium,
                colors   = IconButtonDefaults.filledTonalIconButtonColors(containerColor = DsColors.Primary, contentColor = DsColors.Surface)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter ce code-barres")
            }
        }
        if (error.isNotEmpty()) {
            Text(error, fontSize = DsTextSize.caption, color = DsColors.Danger, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
