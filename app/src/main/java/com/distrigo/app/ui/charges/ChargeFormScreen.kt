package com.distrigo.app.ui.charges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

@Composable
internal fun StepIndicator(currentStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DsSpacing.xxxl),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepDot(number = 1, label = "Détails", active = currentStep >= 1)
        Box(Modifier.weight(1f).height(2.dp).background(if (currentStep >= 2) DsColors.Primary else DsColors.Border))
        StepDot(number = 2, label = "Résumé", active = currentStep >= 2)
    }
}

@Composable
private fun StepDot(number: Int, label: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(28.dp).clip(DsShapes.pill).background(if (active) DsColors.Primary else DsColors.Border),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", color = Color.White, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(DsSpacing.xs))
        Text(label, fontSize = DsTextSize.caption, color = if (active) DsColors.Primary else DsColors.TextTertiary)
    }
}

@Composable
internal fun FixedInfoField(label: String, icon: String, value: String) {
    Column {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
        Surface(shape = DsShapes.medium, color = DsColors.SurfaceSunken, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(ChargeIconMapper.iconFor(icon), contentDescription = null, tint = DsColors.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(DsSpacing.sm))
                Text(value, fontSize = DsTextSize.body, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
            }
        }
    }
}

@Composable
internal fun SummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = DsSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        Text(
            value, fontSize = DsTextSize.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) DsColors.Primary else DsColors.TextPrimary
        )
    }
    HorizontalDivider(color = DsColors.Border, thickness = 0.5.dp)
}

@Composable
internal fun DsFormField(
    label: String, value: String, onValueChange: (String) -> Unit,
    error: String = "", placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text, minLines: Int = 1, maxLines: Int = 1
) {
    Column {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.padding(bottom = DsSpacing.xs))
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = DsTextSize.body) },
            singleLine = minLines == 1 && maxLines == 1, minLines = minLines, maxLines = maxLines,
            isError = error.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = DsShapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = DsColors.Border, focusedBorderColor = DsColors.Primary, errorBorderColor = DsColors.Danger
            )
        )
        if (error.isNotEmpty()) {
            Text(error, fontSize = DsTextSize.caption, color = DsColors.Danger, modifier = Modifier.padding(start = DsSpacing.xs, top = 2.dp))
        }
    }
}