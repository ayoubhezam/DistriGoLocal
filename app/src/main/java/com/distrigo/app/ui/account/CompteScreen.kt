package com.distrigo.app.ui.account

import com.distrigo.app.data.license.licText
import com.distrigo.app.core.format.westernDigits
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.auth.AuthSession
import com.distrigo.app.data.license.BusinessProfile
import com.distrigo.app.data.license.License
import com.distrigo.app.data.license.LicenseClient
import com.distrigo.app.data.license.LicenseState
import com.distrigo.app.data.license.heldLicense
import com.distrigo.app.data.license.holdsLicense
import com.distrigo.app.ui.designsystem.BrandActionRow
import com.distrigo.app.ui.designsystem.BrandCard
import com.distrigo.app.ui.designsystem.BrandCardBody
import com.distrigo.app.ui.designsystem.BrandCardHeader
import com.distrigo.app.ui.designsystem.BrandCodeText
import com.distrigo.app.ui.designsystem.BrandInfoRow
import com.distrigo.app.ui.designsystem.BrandMessage
import com.distrigo.app.ui.designsystem.BrandNotice
import com.distrigo.app.ui.designsystem.BrandPill
import com.distrigo.app.ui.designsystem.BrandRowDivider
import com.distrigo.app.ui.designsystem.BrandScreen
import com.distrigo.app.ui.designsystem.BrandSize
import com.distrigo.app.ui.designsystem.BrandWideButton
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.onboarding.CreateAccountLine
import com.distrigo.app.ui.onboarding.OnboardingActions
import com.distrigo.app.ui.onboarding.OnboardingStatus
import com.distrigo.app.ui.onboarding.OnboardingViewModel
import com.distrigo.app.ui.onboarding.SignInFields
import com.distrigo.app.ui.onboarding.rememberOnboardingActions
import com.distrigo.app.ui.onboarding.OnboardingStart
import com.distrigo.app.ui.onboarding.activityLabel
import com.distrigo.app.ui.onboarding.formatCode
import com.distrigo.app.ui.onboarding.salesRepsLabel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.distrigo.app.R

/**
 * The account, this phone's subscription and the business, laid out as the owner's mockup in the app's new
 * design language (ui/designsystem/BrandComponents.kt; docs/activation_codes.md, step C5).
 */
@Composable
fun CompteScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    onOpenOnboarding: (OnboardingStart) -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenSubscription: () -> Unit = {},
) {
    BackHandler { onBack() }
    val state by viewModel.state.collectAsState()

    // Signed out, Compte is Connexion, with the onboarding's own view model (the activity's): a sign-in that needs
    // more — a company, the seat moved here, a code — or succeeds continues in the onboarding, which MainActivity opens
    val onboarding: OnboardingViewModel = hiltViewModel(LocalContext.current.findActivity() as ComponentActivity)
    val signIn by onboarding.status.collectAsState()
    val actions = rememberOnboardingActions(onboarding)
    // A sign-in begun in this form: the form stays, its button turning, while the phone is activated — the account
    // is signed in seconds before — so the onboarding's next screen comes straight after it, with no Compte between
    var fromForm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(signIn.busy, signIn.error) {
        if (!fromForm || signIn.busy) return@LaunchedEffect
        fromForm = false
        val error = signIn.error
        if (error != null && state.account is AccountState.SignedIn) {
            // Signed in but not activated: said here, where « Activer cet appareil » tries again
            viewModel.showError(error)
            onboarding.reset()
        }
    }

    BrandScreen(title = licText(R.string.lic_action_account), onBack = onBack) {
        state.message?.let { BrandMessage(it.text, it.isError) }
        val account = state.account
        when {
            account == AccountState.Loading -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsBrand.Blue)
            }
            account == AccountState.SignedOut || fromForm -> SignInCard(
                actions         = actions,
                status          = signIn,
                onSignIn        = { email, password ->
                    fromForm = true
                    actions.signIn(email, password)
                },
                // Not followed: a Google sheet closed unanswered would leave the mark set (and Google is off for now)
                onGoogle        = actions.signInWithGoogle,
                onCode          = { onOpenOnboarding(OnboardingStart.CODE) },
                onCreateAccount = { onOpenOnboarding(OnboardingStart.SIGN_UP) },
                onShown         = onboarding::reset,
            )
            account is AccountState.SignedIn -> SignedInCards(account.session, state, viewModel, onOpenAdmin, onOpenSubscription)
        }
    }

    state.dialog?.let { AccountDialogs(it, state, viewModel) }
}

@Composable
private fun ColumnScope.SignedInCards(
    session: AuthSession,
    state: AccountUiState,
    viewModel: AccountViewModel,
    onOpenAdmin: () -> Unit,
    onOpenSubscription: () -> Unit,
) {
    val idle = state.work == null
    IdentityCard(session, state.profile)
    state.activationCode?.let { CodeCard(it, withoutEmail = session.isAnonymous) }
    val context = LocalContext.current
    SubscriptionCard(
        license     = state.license,
        work        = state.work,
        batteryHint = state.batteryHint,
        dataName    = state.dataName,
        // The account's business by its name, once the profile read is this license's
        accountName = state.profile?.takeIf { it.businessId == state.license.heldLicense?.businessId }?.name,
        onActivate  = viewModel::activate,
        onEnterCode = { viewModel.ask(AccountDialog.EnterCode()) },
        onOpenAppSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        },
        onKeepData  = viewModel::keepData,
        onSignOut   = { viewModel.ask(AccountDialog.ConfirmSignOut) },
        onOpenSubscription = onOpenSubscription,
    )
    // An account without a business has nothing to show here
    if (state.profileLoad != ProfileLoad.NO_BUSINESS) {
        BusinessCard(
            profile = state.profile,
            load    = state.profileLoad,
            problem = state.profileProblem,
            idle    = idle,
            onEdit  = { viewModel.ask(AccountDialog.EditProfile(it)) },
            onRetry = viewModel::refreshProfile,
        )
    }
    BrandCard {
        BrandCardHeader(Icons.Outlined.Settings, licText(R.string.acc_management))
        if (session.isAnonymous) {
            BrandActionRow(Icons.Outlined.Email, licText(R.string.acc_add_email), enabled = idle) { viewModel.ask(AccountDialog.AddEmail()) }
            BrandRowDivider()
        }
        // DistriGo's owner only (app_metadata); codes-admin checks it again on every call
        if (session.isAdmin) {
            BrandActionRow(Icons.Outlined.AdminPanelSettings, licText(R.string.acc_admin), enabled = idle, onClick = onOpenAdmin)
            BrandRowDivider()
        }
        BrandActionRow(Icons.AutoMirrored.Outlined.Logout, licText(R.string.acc_sign_out), enabled = idle, busy = state.work == AccountWork.SIGNING_OUT) {
            viewModel.ask(AccountDialog.ConfirmSignOut)
        }
    }
    TextButton(
        onClick  = { viewModel.ask(AccountDialog.ConfirmDelete) },
        enabled  = idle,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    ) {
        Text(
            if (state.work == AccountWork.DELETING) licText(R.string.acc_deleting) else licText(R.string.acc_delete_account),
            fontFamily = DsBrand.Font,
            fontWeight = FontWeight.SemiBold,
            fontSize   = BrandSize.Body,
            color      = DsColors.Danger
        )
    }
}

// ── Cards ──

/**
 * Signed out, Compte is Connexion itself (the owner's choice, 2026-10-15): the onboarding's form. A code and a new
 * account open the onboarding at their own page. [onShown]: an earlier try's error is not this one's.
 */
@Composable
private fun SignInCard(
    actions: OnboardingActions,
    status: OnboardingStatus,
    onSignIn: (email: String, password: String) -> Unit,
    onGoogle: (() -> Unit)?,
    onCode: () -> Unit,
    onCreateAccount: () -> Unit,
    onShown: () -> Unit,
) {
    LaunchedEffect(Unit) { onShown() }
    BrandCard {
        BrandCardHeader(Icons.AutoMirrored.Outlined.Login, licText(R.string.acc_sign_in_title))
        BrandCardBody {
            Text(
                licText(R.string.acc_sign_in_text),
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.Medium,
                fontSize   = BrandSize.Small,
                lineHeight = BrandSize.SmallLine,
                color      = DsBrand.Slate
            )
            Column {
                SignInFields(actions, status, onCode, signIn = onSignIn, google = onGoogle)
            }
            CreateAccountLine(onCreateAccount, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** Who this is: the business, its phone, and how the account signs in. */
@Composable
private fun IdentityCard(session: AuthSession, profile: BusinessProfile?) {
    val title = profile?.name ?: session.displayName ?: session.email ?: if (session.isAnonymous) licText(R.string.acc_no_email_account) else licText(R.string.acc_your_account)
    val caption = if (session.isAnonymous) licText(R.string.acc_activated_with_code) else session.email?.takeIf { it != title }
    BrandCard {
        Row(Modifier.padding(DsSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Avatar {
                val initials = session.initials
                when {
                    // An account from an activation code: a key says how it was opened
                    session.isAnonymous -> Icon(Icons.Outlined.Key, contentDescription = null, tint = DsBrand.Blue, modifier = Modifier.size(BrandSize.AvatarIcon))
                    initials != null -> Text(initials, fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = BrandSize.AvatarInitials, color = DsBrand.Blue)
                    else -> Icon(Icons.Outlined.Person, contentDescription = null, tint = DsBrand.Blue, modifier = Modifier.size(BrandSize.AvatarIcon))
                }
            }
            Spacer(Modifier.width(DsSpacing.lg))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = BrandSize.Name, lineHeight = 21.sp, color = DsBrand.Ink, maxLines = 2)
                profile?.contactPhone?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = BrandSize.Body, color = DsBrand.Slate)
                }
                caption?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = BrandSize.Small, color = DsBrand.Slate, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Avatar(content: @Composable () -> Unit) {
    Box(
        modifier         = Modifier.size(BrandSize.Avatar).clip(CircleShape).background(DsBrand.Blue.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * « Votre code d'activation »: the business key, kept encrypted on this phone (ActivationCodeStore) because the
 * server holds only its fingerprint. Hidden but for its first group until asked; copied as sensitive, so the
 * keyboard's clipboard preview does not show it.
 */
@Composable
private fun CodeCard(code: String, withoutEmail: Boolean) {
    val context = LocalContext.current
    var shown by rememberSaveable { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }
    val formatted = formatCode(code)
    BrandCard {
        Row(
            modifier          = Modifier.padding(start = DsSpacing.lg, end = DsSpacing.md, top = DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = DsBrand.Ink, modifier = Modifier.size(BrandSize.HeaderIcon))
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(licText(R.string.acc_your_code), fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.CardTitle, color = DsBrand.Ink)
                Spacer(Modifier.height(2.dp))
                BrandCodeText(if (shown) formatted else formatted.take(4) + "-••••-••••", fontSize = BrandSize.Code)
            }
            RoundAction(if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (shown) licText(R.string.acc_hide_code) else licText(R.string.acc_show_code)) {
                shown = !shown
            }
            Spacer(Modifier.width(DsSpacing.sm))
            RoundAction(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, if (copied) licText(R.string.acc_code_copied) else licText(R.string.acc_copy_code)) {
                copyCode(context, formatted)
                copied = true
            }
        }
        Text(
            if (withoutEmail) licText(R.string.acc_keep_code_no_email)
            else licText(R.string.acc_keep_code),
            fontFamily = DsBrand.Font,
            fontWeight = FontWeight.Medium,
            fontSize   = BrandSize.Small,
            lineHeight = BrandSize.SmallLine,
            color      = DsBrand.Slate,
            modifier   = Modifier.padding(start = DsSpacing.lg + BrandSize.HeaderIcon + DsSpacing.md, end = DsSpacing.lg, top = DsSpacing.sm, bottom = DsSpacing.lg)
        )
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier         = Modifier
            .size(BrandSize.RoundAction)
            .clip(CircleShape)
            .background(DsBrand.Blue.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = DsBrand.Blue, modifier = Modifier.size(BrandSize.ButtonIcon))
    }
}

/** Android 13 and later keep a sensitive clip out of the clipboard preview, like a password. */
private fun copyCode(context: Context, code: String) {
    val clip = ClipData.newPlainText(licText(R.string.acc_activation_code), code)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

@Composable
private fun SubscriptionCard(
    license: LicenseState,
    work: AccountWork?,
    batteryHint: Boolean,
    dataName: String?,
    accountName: String?,
    onActivate: () -> Unit,
    onEnterCode: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onKeepData: () -> Unit,
    onSignOut: () -> Unit,
    onOpenSubscription: () -> Unit,
) {
    val look = license.look()
    val held = license.heldLicense
    val ended = license is LicenseState.Grace || license is LicenseState.Expired
    // An e-mail sign-up on a phone that already had its trial: a license that ended the day before it began
    val trialUsed = held != null && held.plan == "trial" && held.validTo < held.validFrom
    BrandCard {
        // The status in its own row: next to the title, any pill pushes it onto two lines on a phone
        BrandCardHeader(Icons.Outlined.CalendarMonth, licText(R.string.acc_sub_info))
        BrandInfoRow(Icons.Outlined.VerifiedUser, licText(R.string.acc_status), null, badge = { BrandPill(look.label, look.color, look.background) })
        if (held != null) {
            BrandRowDivider()
            BrandInfoRow(
                Icons.Outlined.WorkspacePremium,
                licText(R.string.acc_plan),
                when {
                    trialUsed -> licText(R.string.acc_trial_used)
                    held.plan == "trial" -> licText(R.string.acc_trial)
                    else -> "DistriGo Solo"
                }
            )
            if (!trialUsed) {
                BrandRowDivider()
                BrandInfoRow(Icons.Outlined.Event, licText(R.string.acc_start_date), day(held.validFrom))
                BrandRowDivider()
                BrandInfoRow(Icons.Outlined.EventAvailable, if (ended) licText(R.string.acc_ended_on) else licText(R.string.acc_expiry_date), day(held.validTo))
            }
            if (license is LicenseState.Grace) {
                BrandRowDivider()
                BrandInfoRow(Icons.Outlined.HourglassBottom, licText(R.string.acc_grace_end), day(held.graceUntil))
            }
        }
        BrandCardBody {
            val notice = when {
                trialUsed -> licText(R.string.acc_trial_used_text)
                license is LicenseState.OtherBusiness -> otherBusinessNotice(dataName, accountName)
                else -> license.notice(held)
            }
            notice?.let { BrandNotice(it, look.color, look.background) }
            if (batteryHint && license.holdsLicense) {
                // A hint, never a block (decision 6): the button opens the screen where the battery is set
                BrandNotice(
                    licText(R.string.acc_battery_blocked),
                    DsColors.Warning,
                    DsColors.WarningLight,
                )
                BrandWideButton(licText(R.string.backup_open_app_settings), Icons.Outlined.Settings, filled = false, enabled = true, onClick = onOpenAppSettings)
            }
            if (license is LicenseState.OtherBusiness) {
                // The two answers (docs/license_l4_checkin.md §7); signing out asks first, as from « Gestion du compte »
                BrandWideButton(
                    accountName?.let { licText(R.string.acc_keep_for, it) } ?: licText(R.string.acc_keep_for_account),
                    Icons.Outlined.Check,
                    filled  = true,
                    enabled = work == null,
                    busy    = work == AccountWork.KEEPING_DATA,
                    onClick = onKeepData,
                )
                BrandWideButton(licText(R.string.acc_sign_out), Icons.AutoMirrored.Outlined.Logout, filled = false, enabled = work == null, busy = work == AccountWork.SIGNING_OUT, onClick = onSignOut)
            }
            if (license.holdsLicense) {
                // A code pays for more time: the thing to do once the subscription is ending. Not while suspended:
                // the days would be added, but only DistriGo lifts a suspension; nor before the data's business is settled
                val ending = license is LicenseState.RenewSoon || ended
                if (license !is LicenseState.Suspended && license !is LicenseState.OtherBusiness) {
                    BrandWideButton(licText(R.string.acc_enter_code), Icons.Outlined.Key, filled = ending, enabled = work == null, onClick = onEnterCode)
                }
                // « Abonnement » (docs/license_l5_enforcement.md §8.2): the code, the state and DistriGo's contacts, for any
                // license held, active included
                BrandWideButton(licText(R.string.acc_sub_and_help), Icons.Outlined.WorkspacePremium, filled = false, enabled = work == null, onClick = onOpenSubscription)
                held?.let { CheckedLine(it.issuedAt) }
            } else {
                BrandWideButton(licText(R.string.acc_activate_device), Icons.Outlined.VerifiedUser, filled = true, enabled = work == null, busy = work == AccountWork.ACTIVATING, onClick = onActivate)
            }
        }
    }
}

/**
 * « Mis à jour le … »: the last check-in, from the background or the app coming to the front (the license's issue
 * time). The phone checks in by itself (docs/license_l4_checkin.md): there is no button for it.
 */
@Composable
private fun CheckedLine(issuedAt: Long) {
    Text(
        Instant.ofEpochMilli(issuedAt).atZone(ZoneId.systemDefault()).let { licText(R.string.acc_updated_on, licText(R.string.backup_date_time, DAY_SHORT.format(it), TIME.format(it))) },
        fontFamily = DsBrand.Font,
        fontWeight = FontWeight.Medium,
        fontSize   = BrandSize.Small,
        color      = DsBrand.Slate,
        textAlign  = TextAlign.Center,
        modifier   = Modifier.fillMaxWidth().padding(vertical = DsSpacing.sm)
    )
}

/** « Informations de l'entreprise »: the business-profile, each row changed in its own dialog. */
@Composable
private fun BusinessCard(
    profile: BusinessProfile?,
    load: ProfileLoad,
    problem: String?,
    idle: Boolean,
    onEdit: (ProfileField) -> Unit,
    onRetry: () -> Unit,
) {
    BrandCard {
        BrandCardHeader(Icons.Outlined.Apartment, licText(R.string.acc_business_info))
        when {
            profile != null -> {
                val edit = { field: ProfileField -> if (profile.canEdit) ({ onEdit(field) }) else null }
                BrandInfoRow(Icons.Outlined.Storefront, licText(R.string.acc_business_name), profile.name, edit(ProfileField.NAME), enabled = idle)
                BrandRowDivider()
                BrandInfoRow(Icons.Outlined.Phone, licText(R.string.acc_phone), profile.contactPhone?.let(::westernDigits), edit(ProfileField.PHONE), enabled = idle)
                BrandRowDivider()
                BrandInfoRow(Icons.Outlined.WorkOutline, licText(R.string.acc_activity), activityLabel(profile.activity), edit(ProfileField.ACTIVITY), enabled = idle)
                BrandRowDivider()
                BrandInfoRow(Icons.Outlined.Groups, licText(R.string.acc_sales_reps), salesRepsLabel(profile.salesReps), edit(ProfileField.SALES_REPS), enabled = idle)
            }
            load == ProfileLoad.FAILED -> BrandCardBody {
                Text(
                    problem ?: licText(R.string.acc_info_unreadable),
                    fontFamily = DsBrand.Font,
                    fontWeight = FontWeight.Medium,
                    fontSize   = BrandSize.Body,
                    lineHeight = BrandSize.BodyLine,
                    color      = DsBrand.Slate,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.common_action_retry), fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Action, color = DsBrand.Blue)
                }
            }
            else -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsBrand.Blue, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
            }
        }
    }
}

// ── The subscription's state ──

private data class Look(val label: String, val explanation: String, val color: Color, val background: Color)

private fun LicenseState.look(): Look = when (this) {
    is LicenseState.Active -> Look(licText(R.string.acc_look_active), "", DsColors.Success, DsColors.SuccessLight)
    is LicenseState.RenewSoon -> Look(licText(R.string.acc_look_renew), licText(R.string.acc_look_renew_text), DsColors.Warning, DsColors.WarningLight)
    is LicenseState.CheckInSoon -> Look(licText(R.string.acc_look_check), licText(R.string.acc_look_check_text), DsColors.Warning, DsColors.WarningLight)
    is LicenseState.Grace -> Look(licText(R.string.acc_look_grace), licText(R.string.acc_look_grace_text), DsColors.Danger, DsColors.DangerLight)
    is LicenseState.Expired -> Look(licText(R.string.acc_look_expired), licText(R.string.acc_look_expired_text), DsColors.Danger, DsColors.DangerLight)
    is LicenseState.ClockBehind -> Look(licText(R.string.acc_look_clock), licText(R.string.acc_look_clock_text), DsColors.Danger, DsColors.DangerLight)
    is LicenseState.NotYetValid -> Look(licText(R.string.acc_look_not_yet), licText(R.string.acc_look_not_yet_text), DsColors.Warning, DsColors.WarningLight)
    is LicenseState.CheckInRequired -> Look(licText(R.string.acc_look_check_needed), licText(R.string.acc_look_check_needed_text), DsColors.Warning, DsColors.WarningLight)
    LicenseState.Revoked -> Look(licText(R.string.acc_look_revoked), licText(R.string.acc_look_revoked_text), DsColors.TextSecondary, DsColors.SurfaceSunken)
    is LicenseState.Suspended -> Look(licText(R.string.acc_look_suspended), LicenseClient.SUSPENDED_MESSAGE, DsColors.Danger, DsColors.DangerLight)
    is LicenseState.OtherBusiness -> Look(licText(R.string.acc_look_other), licText(R.string.lic_other_business_banner), DsColors.Danger, DsColors.DangerLight)
    is LicenseState.NoLicense -> Look(licText(R.string.acc_look_none), licText(R.string.acc_look_none_text), DsColors.TextSecondary, DsColors.SurfaceSunken)
    LicenseState.Unchecked -> Look("…", licText(R.string.acc_look_reading), DsColors.TextSecondary, DsColors.SurfaceSunken)
}

/** OtherBusiness, with the names when known: the data's (its settings) and the account's (its profile). */
private fun otherBusinessNotice(dataName: String?, accountName: String?): String {
    val data = dataName?.let { licText(R.string.acc_this_data_named, it) } ?: licText(R.string.acc_this_data)
    val account = accountName?.let { licText(R.string.acc_holder, it) } ?: licText(R.string.acc_this_account)
    return licText(R.string.acc_other_business_notice, data, account)
}

/** The note under the dates: nothing while all is well, otherwise what the state asks for, with its date. */
private fun LicenseState.notice(held: License?): String? = when (this) {
    is LicenseState.Active -> null
    is LicenseState.RenewSoon -> held?.let { licText(R.string.acc_notice_renew, day(it.validTo)) } ?: look().explanation
    is LicenseState.CheckInSoon -> held?.let { licText(R.string.acc_notice_check, day(it.offlineUntil)) } ?: look().explanation
    else -> look().explanation.ifEmpty { null }
}

private val DAY: DateTimeFormatter get() = com.distrigo.app.core.format.AppDates.formatter("d MMM yyyy")
private val DAY_SHORT: DateTimeFormatter get() = com.distrigo.app.core.format.AppDates.formatter("d MMM")
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun day(epochMillis: Long): String = DAY.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

internal fun moment(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).let { licText(R.string.backup_date_time, DAY.format(it), TIME.format(it)) }
