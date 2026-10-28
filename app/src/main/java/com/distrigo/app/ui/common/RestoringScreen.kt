package com.distrigo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

/** Shown while a restore scheduled before the restart is being installed, instead of a frozen first frame. */
@Composable
fun RestoringScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(DsColors.Surface).padding(DsSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = DsColors.Primary)
        Text(
            stringResource(R.string.common_restoring_title),
            modifier = Modifier.padding(top = DsSpacing.lg),
            fontSize = DsTextSize.title, fontWeight = FontWeight.SemiBold, color = DsColors.PrimaryDark, textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.common_restoring_body),
            modifier = Modifier.padding(top = DsSpacing.sm),
            fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, textAlign = TextAlign.Center
        )
    }
}
