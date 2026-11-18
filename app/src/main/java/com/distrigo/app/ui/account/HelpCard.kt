package com.distrigo.app.ui.account

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.distrigo.app.R
import com.distrigo.app.core.web.LegalPage
import com.distrigo.app.core.web.LegalPages
import com.distrigo.app.data.license.licText
import com.distrigo.app.ui.common.layoutEnd
import com.distrigo.app.ui.designsystem.BrandCard
import com.distrigo.app.ui.designsystem.BrandCardHeader
import com.distrigo.app.ui.designsystem.BrandRowDivider
import com.distrigo.app.ui.designsystem.BrandSize
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsSpacing

/**
 * « Aide »: how to reach us, each contact opening its app, then the legal pages on the website. In Compte, not on the subscription screen: it is about help
 * only, never about buying (owner, 2026-11-17).
 */
@Composable
internal fun HelpCard() {
    val context = LocalContext.current
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent) }
    }
    BrandCard {
        BrandCardHeader(Icons.Outlined.HeadsetMic, licText(R.string.acc_help))
        SupportRow(Icons.Outlined.Phone, licText(R.string.acc_phone), SUPPORT_PHONE) { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SUPPORT_PHONE"))) }
        BrandRowDivider()
        SupportRow(Icons.AutoMirrored.Outlined.Chat, licText(R.string.acc_whatsapp), SUPPORT_PHONE) { open(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_WHATSAPP))) }
        BrandRowDivider()
        SupportRow(Icons.Outlined.Email, licText(R.string.acc_email_short), SUPPORT_EMAIL) { open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))) }
        BrandRowDivider()
        SupportRow(Icons.Outlined.Schedule, licText(R.string.acc_support_hours), licText(R.string.acc_support_hours_value), onClick = null)
        // The pages on distrigoo.com, in the app's language: also reachable once the app is gone (Play requires the
        // deletion page to be)
        BrandRowDivider()
        SupportRow(Icons.Outlined.Policy, licText(R.string.acc_privacy_policy), "", onClick = { LegalPages.open(context, LegalPage.PRIVACY) })
        BrandRowDivider()
        SupportRow(Icons.Outlined.Description, licText(R.string.acc_terms_of_use), "", onClick = { LegalPages.open(context, LegalPage.TERMS) })
        BrandRowDivider()
        SupportRow(Icons.Outlined.PersonRemove, licText(R.string.acc_delete_on_web), "", onClick = { LegalPages.open(context, LegalPage.ACCOUNT_DELETION) })
    }
}

@Composable
private fun SupportRow(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)?) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = label, onClick = onClick) else Modifier)
            .padding(start = DsSpacing.lg, end = if (onClick != null) DsSpacing.md else DsSpacing.lg, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = DsBrand.Ink, modifier = Modifier.size(BrandSize.RowIcon))
        Spacer(Modifier.width(DsSpacing.md))
        Text(label, fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = BrandSize.Body, color = DsBrand.Slate)
        Spacer(Modifier.width(DsSpacing.md))
        Text(
            value,
            fontFamily = DsBrand.Font,
            fontWeight = FontWeight.Bold,
            fontSize   = BrandSize.Body,
            color      = if (onClick != null) DsBrand.Blue else DsBrand.Ink,
            textAlign  = layoutEnd(),
            modifier   = Modifier.weight(1f)
        )
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = DsBrand.Slate, modifier = Modifier.padding(start = DsSpacing.xs))
        }
    }
}

private const val SUPPORT_PHONE = "0697875799"
private const val SUPPORT_WHATSAPP = "https://wa.me/213697875799"
private const val SUPPORT_EMAIL = "contact@distrigoo.com"
