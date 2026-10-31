package com.distrigo.app.ui.account

import com.distrigo.app.ui.common.layoutEnd
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.distrigo.app.R
import com.distrigo.app.data.debug.LicensePreview
import com.distrigo.app.data.license.LicensePolicy
import com.distrigo.app.data.license.LicenseState
import com.distrigo.app.data.license.heldLicense
import com.distrigo.app.data.license.holdsLicense
import com.distrigo.app.ui.designsystem.BrandCard
import com.distrigo.app.ui.designsystem.BrandCardBody
import com.distrigo.app.ui.designsystem.BrandCardHeader
import com.distrigo.app.ui.designsystem.BrandMessage
import com.distrigo.app.ui.designsystem.BrandRowDivider
import com.distrigo.app.ui.designsystem.BrandScreen
import com.distrigo.app.ui.designsystem.BrandSize
import com.distrigo.app.ui.designsystem.BrandWideButton
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.onboarding.CODE_LENGTH
import com.distrigo.app.ui.onboarding.CodeField
import com.distrigo.app.ui.onboarding.normalizeCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.automirrored.outlined.Chat

/**
 * « Abonnement » (docs/license_l5_enforcement.md §8.2): what the subscription's state is, a code to renew it, and
 * how to reach DistriGo. Reached from the « Consultation seule » sheet, the license's banner and Compte. The code
 * goes through Compte's own « Saisir un code » (AccountViewModel.renewWithCode), not a new path.
 */
@Composable
fun AbonnementScreen(viewModel: AccountViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val state by viewModel.state.collectAsState()
    // Debug builds can draw another state's screen (LicensePreview); a release build always shows the real one
    val context = LocalContext.current
    val preview by produceState<LicenseState?>(null, state.license) {
        value = withContext(Dispatchers.IO) { LicensePreview.read(context.filesDir, state.license) }
    }
    val license = preview ?: state.license
    val busy = state.work == AccountWork.RENEWING
    // The refusal of the last code, said under the field rather than in a dialog
    val codeError = (state.dialog as? AccountDialog.EnterCode)?.error
    var code by rememberSaveable { mutableStateOf("") }
    val submit = { viewModel.renewWithCode(code) }

    // A license still valid gets the owner's « actif » illustration; one that stopped or is about to stop, the expired one
    val valid = license is LicenseState.Active || license is LicenseState.CheckInSoon || license is LicenseState.RenewSoon

    BrandScreen(title = "Abonnement", onBack = onBack) {
        Text(
            if (valid) "Votre abonnement DistriGo, et de l'aide si besoin" else "Accédez à nouveau à toutes les fonctionnalités",
            fontFamily = DsBrand.Font,
            fontWeight = FontWeight.Medium,
            fontSize   = BrandSize.Body,
            color      = DsBrand.Slate,
        )
        Image(
            painter            = painterResource(if (valid) R.drawable.subscription_active else R.drawable.subscription_expired),
            contentDescription = null,
            contentScale       = ContentScale.Fit,
            modifier           = Modifier.fillMaxWidth().aspectRatio(689f / 279f)
        )
        state.message?.let { BrandMessage(it.text, it.isError) }
        StateCard(license)

        // Only DistriGo lifts a suspension (§8.2), and a code renews a license this phone holds
        if (license !is LicenseState.Suspended && license !is LicenseState.OtherBusiness) {
            BrandCard {
                BrandCardHeader(Icons.Outlined.Key, "Code d'activation")
                BrandCardBody {
                    if (license.holdsLicense) {
                        CodeField(
                            code     = code,
                            onChange = {
                                code = normalizeCode(it)
                                if (codeError != null) viewModel.dismissDialog()
                            },
                            enabled  = !busy,
                            error    = codeError,
                            onDone   = submit,
                        )
                        BrandWideButton(
                            "Activer mon abonnement",
                            Icons.Outlined.VerifiedUser,
                            filled  = true,
                            enabled = code.length == CODE_LENGTH && state.work == null,
                            busy    = busy,
                            onClick = submit,
                        )
                    } else {
                        Paragraph("Connectez-vous à votre compte dans Compte pour activer ce téléphone avec un code.")
                    }
                }
            }
        }

        SupportCard()

        Text(
            "Votre accès sera rétabli dès la validation du code (connexion Internet nécessaire).",
            fontFamily = DsBrand.Font,
            fontSize   = BrandSize.Small,
            lineHeight = BrandSize.SmallLine,
            color      = DsBrand.Slate,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth().padding(vertical = DsSpacing.sm)
        )
    }

    // The code's result: « Abonnement prolongé » and the rest of Compte's dialogs; the refusal stays under the field
    state.dialog?.takeIf { it !is AccountDialog.EnterCode }?.let { AccountDialogs(it, state, viewModel) }
}

/** The state, in words true to the case (§8.2). */
@Composable
private fun StateCard(license: LicenseState) {
    val held = license.heldLicense
    val trial = held?.plan == "trial"
    val (title, text, tone) = when (license) {
        is LicenseState.Expired -> Triple(
            "Votre abonnement a expiré",
            if (trial) "La période d'essai est terminée. Saisissez un code d'activation pour continuer à enregistrer vos ventes, achats et stocks."
            else "Votre abonnement DistriGo Solo a pris fin le ${date(license.license.validTo)}. Saisissez un code pour le renouveler.",
            Tone.ENDED,
        )
        is LicenseState.Grace -> Triple(
            "Expiré le ${dayMonth(license.license.validTo)} — période de grâce jusqu'au ${dayMonth(license.license.graceUntil)}",
            "Vous pouvez encore enregistrer jusqu'à la fin de la grâce. Saisissez un code pour prolonger votre abonnement.",
            Tone.ENDED,
        )
        is LicenseState.RenewSoon -> Triple(
            "Votre abonnement expire le ${dayMonth(license.license.validTo)}",
            "Saisissez un code pour le prolonger sans interruption.",
            Tone.SOON,
        )
        is LicenseState.Suspended -> Triple(
            "L'abonnement est suspendu",
            "Contactez DistriGo pour le réactiver : vos données restent consultables en attendant.",
            Tone.ENDED,
        )
        is LicenseState.Active, is LicenseState.CheckInSoon -> Triple(
            "Votre abonnement est actif",
            "Valable jusqu'au ${date((license as LicenseState.Judged).license.validTo)}. Un code ajoute du temps à votre abonnement.",
            Tone.ACTIVE,
        )
        else -> Triple(
            "Abonnement",
            LicensePolicy.of(license).block?.reason ?: "L'état de l'abonnement est en cours de lecture.",
            Tone.ENDED,
        )
    }
    BrandCard {
        Row(Modifier.fillMaxWidth().padding(DsSpacing.lg), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tone.background), contentAlignment = Alignment.Center) {
                Icon(tone.icon, contentDescription = null, tint = tone.color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = BrandSize.Name, color = DsBrand.Ink)
                Spacer(Modifier.padding(top = DsSpacing.xs))
                Paragraph(text)
            }
        }
    }
}

private enum class Tone(val icon: ImageVector, val color: Color, val background: Color) {
    ENDED(Icons.Outlined.ErrorOutline, DsColors.Danger, DsColors.DangerLight),
    SOON(Icons.Outlined.WarningAmber, DsColors.Warning, DsColors.WarningLight),
    ACTIVE(Icons.Outlined.CheckCircle, DsColors.Success, DsColors.SuccessLight),
}

/** « Besoin d'aide ? »: each contact opens its app (§8.2). */
@Composable
private fun SupportCard() {
    val context = LocalContext.current
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent) }
    }
    BrandCard {
        BrandCardHeader(Icons.Outlined.HeadsetMic, "Besoin d'aide ?")
        SupportRow(Icons.Outlined.Phone, "Téléphone", SUPPORT_PHONE) { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SUPPORT_PHONE"))) }
        BrandRowDivider()
        SupportRow(Icons.AutoMirrored.Outlined.Chat, "WhatsApp", SUPPORT_PHONE) { open(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_WHATSAPP))) }
        BrandRowDivider()
        SupportRow(Icons.Outlined.Email, "E-mail", SUPPORT_EMAIL) { open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))) }
        BrandRowDivider()
        SupportRow(Icons.Outlined.Schedule, "Horaires de support", "Tous les jours, 8h - 22h", onClick = null)
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

@Composable
private fun Paragraph(text: String) {
    Text(text, fontFamily = DsBrand.Font, fontSize = BrandSize.Body, lineHeight = BrandSize.BodyLine, color = DsBrand.Slate)
}

private fun date(millis: Long): String = DAY_YEAR.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
private fun dayMonth(millis: Long): String = DAY_MONTH.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private val DAY_YEAR: DateTimeFormatter get() = com.distrigo.app.core.format.AppDates.numeric("dd/MM/yyyy")
private val DAY_MONTH: DateTimeFormatter get() = com.distrigo.app.core.format.AppDates.numeric("dd/MM")

private const val SUPPORT_PHONE = "0697875799"
private const val SUPPORT_WHATSAPP = "https://wa.me/213697875799"
private const val SUPPORT_EMAIL = "dzaitech.store@gmail.com"
