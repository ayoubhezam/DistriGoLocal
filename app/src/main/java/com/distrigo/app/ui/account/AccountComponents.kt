package com.distrigo.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize

// What the Compte screen shares with its dialogs. Signing in and up moved to the onboarding (ui/onboarding).

@Composable
internal fun PrimaryButton(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick  = onClick,
        enabled  = enabled && !busy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = DsShapes.medium,
        colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(label, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun MessageLine(message: AccountMessage) {
    Text(
        message.text,
        fontSize = DsTextSize.bodySmall,
        color    = if (message.isError) DsColors.Danger else DsColors.Success,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (message.isError) DsColors.DangerLight else DsColors.SuccessLight, DsShapes.small)
            .padding(DsSpacing.md)
    )
}
