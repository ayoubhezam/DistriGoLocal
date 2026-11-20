package com.distrigo.app.ui.account

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.distrigo.app.data.license.licText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * « Abonnement » (docs/license_l5_enforcement.md §8.2): what the subscription's state is, a code to renew it, and
 * how to reach DistriGo. Reached from the « Consultation seule » sheet, the license's banner and Compte. The code
 * goes through Compte's own « Saisir un code » (AccountViewModel.renewWithCode), not a new path.
 */
@Composable
fun AbonnementScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    paywall: PaywallViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    BackHandler { onBack() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Google Play's plans (docs/play_billing.md), only while BillingSwitch is on: off, this screen is as it was
    val sale by paywall.state.collectAsStateWithLifecycle()
    val signedIn = state.account as? com.distrigo.app.data.auth.AccountState.SignedIn
    // Read again when the account changes (an e-mail added to a code account) or the phone gets its license
    androidx.compose.runtime.LaunchedEffect(signedIn?.session?.isAnonymous, signedIn?.session?.userId, state.license.heldLicense?.businessId) {
        paywall.open()
    }
    // While Google Play sells, the code is the other way, folded under the plans
    var codeOpen by rememberSaveable { mutableStateOf(false) }
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

    BrandScreen(title = licText(R.string.lic_action_subscription), onBack = onBack) {
        Text(
            if (valid) licText(R.string.acc_sub_sub_valid) else licText(R.string.acc_sub_sub_invalid),
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
        StateCard(license, billing = paywall.enabled)

        // Only DistriGo lifts a suspension (§8.2): no plan to buy then, nor a code
        val canPay = license !is LicenseState.Suspended && license !is LicenseState.OtherBusiness
        if (paywall.enabled && canPay) {
            sale.message?.let { BrandMessage(it.text, it.isError) }
            PaywallCard(
                step       = sale.step,
                onSelect   = paywall::select,
                onBuy      = { paywall.buy(context.findActivity()) },
                onAddEmail = { viewModel.ask(AccountDialog.AddEmail()) },
                onManage   = { productId ->
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(com.distrigo.app.data.billing.manageSubscriptionUrl(productId, context.packageName)),
                            ),
                        )
                    }
                },
            )
            if (!codeOpen) CodeFoldLink { codeOpen = true }
        }

        // A code renews a license this phone holds
        if (canPay && (!paywall.enabled || codeOpen)) {
            BrandCard {
                BrandCardHeader(Icons.Outlined.Key, licText(R.string.acc_activation_code))
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
                            licText(R.string.acc_activate_sub),
                            Icons.Outlined.VerifiedUser,
                            filled  = true,
                            enabled = code.length == CODE_LENGTH && state.work == null,
                            busy    = busy,
                            onClick = submit,
                        )
                    } else {
                        Paragraph(licText(R.string.acc_sign_in_first))
                    }
                }
            }
        }

        // About the code: only where the code can be typed
        if (!paywall.enabled || codeOpen) Text(
            licText(R.string.acc_access_restored),
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

/**
 * The state, in words true to the case (§8.2). While Google Play sells ([billing]), the words say « abonnez-vous »
 * rather than « saisissez un code »: codes stay possible, folded below, but are not promoted.
 */
@Composable
private fun StateCard(license: LicenseState, billing: Boolean) {
    val held = license.heldLicense
    val trial = held?.plan == "trial"
    val (title, text, tone) = when (license) {
        is LicenseState.Expired -> Triple(
            licText(R.string.acc_sub_expired_title),
            when {
                trial -> licText(if (billing) R.string.bill_state_trial_ended else R.string.acc_trial_ended)
                billing -> licText(R.string.bill_state_ended_on, date(license.license.validTo))
                else -> licText(R.string.acc_sub_ended_on, date(license.license.validTo))
            },
            Tone.ENDED,
        )
        is LicenseState.Grace -> Triple(
            licText(R.string.acc_sub_grace_title, dayMonth(license.license.validTo), dayMonth(license.license.graceUntil)),
            licText(if (billing) R.string.bill_state_grace else R.string.acc_grace_text),
            Tone.ENDED,
        )
        is LicenseState.RenewSoon -> Triple(
            licText(R.string.acc_sub_expires_title, dayMonth(license.license.validTo)),
            licText(if (billing) R.string.bill_state_extend_now else R.string.acc_extend_now),
            Tone.SOON,
        )
        is LicenseState.Suspended -> Triple(
            licText(R.string.acc_suspended_title),
            licText(R.string.acc_suspended_text),
            Tone.ENDED,
        )
        is LicenseState.Active, is LicenseState.CheckInSoon -> Triple(
            licText(R.string.acc_active_title),
            licText(if (billing) R.string.bill_state_valid_until else R.string.acc_sub_valid_until, date((license as LicenseState.Judged).license.validTo)),
            Tone.ACTIVE,
        )
        else -> Triple(
            licText(R.string.lic_action_subscription),
            LicensePolicy.of(license).block?.reason ?: licText(R.string.acc_reading_state),
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

@Composable
private fun Paragraph(text: String) {
    Text(text, fontFamily = DsBrand.Font, fontSize = BrandSize.Body, lineHeight = BrandSize.BodyLine, color = DsBrand.Slate)
}

private fun date(millis: Long): String = DAY_YEAR.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
private fun dayMonth(millis: Long): String = DAY_MONTH.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private val DAY_YEAR: DateTimeFormatter get() = com.distrigo.app.core.format.AppDates.numeric("dd/MM/yyyy")
private val DAY_MONTH: DateTimeFormatter get() = com.distrigo.app.core.format.AppDates.numeric("dd/MM")

