package com.distrigo.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.R
import com.distrigo.app.data.license.licText
import com.distrigo.app.ui.designsystem.BrandWave
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsSpacing
import kotlinx.coroutines.delay

/** Supabase sends one reset e-mail a minute per address: « Renvoyer le code » waits as long. */
private const val RESEND_AFTER_SECONDS = 60

/** The code Supabase e-mails: 6 digits by default, up to 10 if the project is set so. */
internal fun isResetCode(text: String) = text.trim().matches(Regex("\\d{6,10}"))

/**
 * « Mot de passe oublié » (e-mail accounts): the address, then the 6-digit code from the e-mail with a new password.
 * The code is typed here rather than a link opened, so it works whatever device reads the e-mail. Once the
 * password holds, the account is signed in and the phone activated as after Connexion ([OnboardingStatus]).
 */
@Composable
internal fun ResetPasswordPage(actions: OnboardingActions, status: OnboardingStatus, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf(actions.resetEmail()) }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val sentTo = status.resetSentTo

    // « Renvoyer le code » counts down from each sending
    var wait by rememberSaveable { mutableIntStateOf(0) }
    // A code asked for: the code step starts unmarked, and « Renvoyer » waits its minute
    LaunchedEffect(sentTo) { if (sentTo != null) { wait = RESEND_AFTER_SECONDS; tried = false } }
    LaunchedEffect(wait) { if (wait > 0) { delay(1_000); wait -= 1 } }

    Box(Modifier.fillMaxSize().background(DsBrand.Mist)) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight)) {
                FormTopBar(onBack)
                Column(Modifier.padding(horizontal = FormGutter)) {
                    Spacer(Modifier.height(DsSpacing.md))
                    if (sentTo == null) {
                        FormTitle(licText(R.string.onb_reset_title), licText(R.string.onb_reset_sub))
                        Spacer(Modifier.height(DsSpacing.lg))
                        val send = {
                            tried = true
                            if (isEmail(email) && !status.busy) actions.requestPasswordReset(email.trim())
                        }
                        BrandTextField(
                            value         = email,
                            onValueChange = { email = it },
                            label         = licText(R.string.acc_email),
                            placeholder   = licText(R.string.onb_email_example),
                            icon          = Icons.Outlined.Email,
                            error         = if (tried && !isEmail(email)) licText(R.string.onb_invalid_email) else null,
                            keyboardType  = KeyboardType.Email,
                            onDone        = send
                        )
                        Spacer(Modifier.height(DsSpacing.lg))
                        status.error?.let {
                            FormError(it)
                            Spacer(Modifier.height(DsSpacing.md))
                        }
                        BrandButton(licText(R.string.onb_reset_send), onClick = send, busy = status.busy)
                    } else {
                        FormTitle(licText(R.string.onb_reset_title), licText(R.string.onb_reset_sub_code))
                        Spacer(Modifier.height(DsSpacing.md))
                        Text(
                            licText(R.string.onb_reset_sent, sentTo),
                            fontFamily = DsBrand.Font,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 13.sp,
                            lineHeight = 18.sp,
                            color      = DsBrand.Ink
                        )
                        Spacer(Modifier.height(DsSpacing.lg))
                        val confirm = {
                            tried = true
                            if (isResetCode(code) && password.length >= MIN_PASSWORD && !status.busy) {
                                actions.resetPassword(sentTo, code.trim(), password)
                            }
                        }
                        BrandTextField(
                            value         = code,
                            onValueChange = { typed -> code = typed.filter(Char::isDigit).take(10) },
                            label         = licText(R.string.onb_reset_code_label),
                            placeholder   = licText(R.string.onb_reset_code_example),
                            icon          = Icons.Outlined.Key,
                            error         = if (tried && !isResetCode(code)) licText(R.string.onb_reset_enter_code) else null,
                            keyboardType  = KeyboardType.NumberPassword
                        )
                        Spacer(Modifier.height(DsSpacing.sm + 2.dp))
                        BrandPasswordField(
                            value         = password,
                            onValueChange = { password = it },
                            label         = licText(R.string.onb_reset_new_password),
                            placeholder   = licText(R.string.onb_password_min, MIN_PASSWORD),
                            error         = if (tried && password.length < MIN_PASSWORD) licText(R.string.onb_password_min, MIN_PASSWORD) else null,
                            onDone        = confirm
                        )
                        Spacer(Modifier.height(DsSpacing.lg))
                        status.error?.let {
                            FormError(it)
                            Spacer(Modifier.height(DsSpacing.md))
                        }
                        BrandButton(licText(R.string.onb_reset_confirm), onClick = confirm, busy = status.busy)
                        Spacer(Modifier.height(DsSpacing.sm))
                        TextButton(
                            onClick  = { wait = RESEND_AFTER_SECONDS; actions.requestPasswordReset(sentTo) },
                            enabled  = wait == 0 && !status.busy,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                if (wait > 0) licText(R.string.onb_reset_resend_in, wait) else licText(R.string.onb_reset_resend),
                                fontFamily = DsBrand.Font,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 13.sp,
                                color      = if (wait > 0) DsBrand.Slate else DsBrand.Blue
                            )
                        }
                        TextButton(
                            onClick  = { tried = false; code = ""; password = ""; actions.changeResetEmail() },
                            enabled  = !status.busy,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                licText(R.string.onb_reset_other_email),
                                fontFamily = DsBrand.Font,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 13.sp,
                                color      = DsBrand.Slate
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f).heightIn(min = DsSpacing.lg))
                BrandWave(Modifier)
            }
        }
    }
}
