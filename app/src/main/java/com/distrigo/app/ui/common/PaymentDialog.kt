package com.distrigo.app.ui.common

import com.distrigo.app.core.format.westernNumber
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.format.LocalMoneyFormatter
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

/**
 * Records a payment against what a client owes, or against what is owed to a supplier.
 *
 * One dialog for both, and for both places each is offered from: the party's own screen and the full
 * history behind « Voir tout », where the payments are listed and so the button belongs. Four copies
 * of the same form is how they drift apart.
 */
@Composable
fun PaymentDialog(
    balance   : Double,
    onSubmit  : (amount: Double, note: String?, onError: (String) -> Unit, onSuccess: () -> Unit) -> Unit,
    onDismiss : () -> Unit,
) {
    val money = LocalMoneyFormatter.current
    var amount by remember { mutableStateOf("") }
    var note   by remember { mutableStateOf("") }
    var error  by remember { mutableStateOf("") }

    fun close() {
        amount = ""; note = ""; error = ""
        onDismiss()
    }

    val invalidAmount = stringResource(R.string.common_payment_invalid_amount)
    AlertDialog(
        onDismissRequest = { close() },
        title = { Text(stringResource(R.string.common_payment_record), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DsShapes.medium)
                        .background(DsColors.DangerLight)
                        .padding(DsSpacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.common_label_remaining_balance), fontSize = DsTextSize.bodySmall, color = DsColors.Danger)
                        Text(
                            money.da(balance),
                            fontSize = DsTextSize.body, fontWeight = FontWeight.Bold, color = DsColors.Danger
                        )
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = westernNumber(it); error = "" },
                    label = { Text(stringResource(R.string.common_label_amount_da)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error.isNotEmpty(),
                    shape = DsShapes.medium,
                    colors = dsTextFieldColors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor = DsColors.Primary
                    )
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.common_label_note_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = DsShapes.medium,
                    colors = dsTextFieldColors(
                        unfocusedBorderColor = DsColors.Border,
                        focusedBorderColor = DsColors.Primary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1000.0, 2000.0, 5000.0).forEach { quick ->
                        OutlinedButton(
                            onClick = { amount = quick.toInt().toString() },
                            modifier = Modifier.weight(1f),
                            shape = DsShapes.small,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("${quick.toInt()}", fontSize = DsTextSize.caption)
                        }
                    }
                    OutlinedButton(
                        onClick = { amount = balance.toString() },
                        modifier = Modifier.weight(1.5f),
                        shape = DsShapes.small,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.common_payment_settle_all), fontSize = DsTextSize.caption)
                    }
                }

                if (error.isNotEmpty()) {
                    Text(error, color = DsColors.Danger, fontSize = DsTextSize.caption)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val value = amount.toDoubleOrNull()
                    if (value == null || value <= 0) {
                        error = invalidAmount
                        return@Button
                    }
                    onSubmit(value, note.ifEmpty { null }, { error = it }, { close() })
                },
                colors = ButtonDefaults.buttonColors(containerColor = DsColors.Danger)
            ) {
                Text(stringResource(R.string.common_action_confirm), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = { close() }) { Text(stringResource(R.string.common_action_cancel)) }
        },
        containerColor    = DsColors.Surface,
        titleContentColor = DsColors.TextPrimary,
        textContentColor  = DsColors.TextSecondary
    )
}
