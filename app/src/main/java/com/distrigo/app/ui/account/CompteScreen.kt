package com.distrigo.app.ui.account

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

    BrandScreen(title = "Compte", onBack = onBack) {
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
        BrandCardHeader(Icons.Outlined.Settings, "Gestion du compte")
        if (session.isAnonymous) {
            BrandActionRow(Icons.Outlined.Email, "Ajouter une adresse e-mail", enabled = idle) { viewModel.ask(AccountDialog.AddEmail()) }
            BrandRowDivider()
        }
        // DistriGo's owner only (app_metadata); codes-admin checks it again on every call
        if (session.isAdmin) {
            BrandActionRow(Icons.Outlined.AdminPanelSettings, "Administration", enabled = idle, onClick = onOpenAdmin)
            BrandRowDivider()
        }
        BrandActionRow(Icons.AutoMirrored.Outlined.Logout, "Se déconnecter", enabled = idle, busy = state.work == AccountWork.SIGNING_OUT) {
            viewModel.ask(AccountDialog.ConfirmSignOut)
        }
    }
    TextButton(
        onClick  = { viewModel.ask(AccountDialog.ConfirmDelete) },
        enabled  = idle,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    ) {
        Text(
            if (state.work == AccountWork.DELETING) "Suppression…" else "Supprimer le compte",
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
        BrandCardHeader(Icons.AutoMirrored.Outlined.Login, "Connexion")
        BrandCardBody {
            Text(
                "Un compte porte votre abonnement. Vos clients, ventes et stock restent sur ce téléphone.",
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
    val title = profile?.name ?: session.displayName ?: session.email ?: if (session.isAnonymous) "Compte sans e-mail" else "Votre compte"
    val caption = if (session.isAnonymous) "Activé avec un code" else session.email?.takeIf { it != title }
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
                Text("Votre code d'activation", fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.CardTitle, color = DsBrand.Ink)
                Spacer(Modifier.height(2.dp))
                BrandCodeText(if (shown) formatted else formatted.take(4) + "-••••-••••", fontSize = BrandSize.Code)
            }
            RoundAction(if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (shown) "Masquer le code" else "Afficher le code") {
                shown = !shown
            }
            Spacer(Modifier.width(DsSpacing.sm))
            RoundAction(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, if (copied) "Code copié" else "Copier le code") {
                copyCode(context, formatted)
                copied = true
            }
        }
        Text(
            if (withoutEmail) "Gardez-le en lieu sûr : sans adresse e-mail, lui seul ouvre votre entreprise sur un nouveau téléphone."
            else "Gardez-le en lieu sûr : il ouvre votre entreprise sur un nouveau téléphone.",
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
    val clip = ClipData.newPlainText("Code d'activation", code)
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
        BrandCardHeader(Icons.Outlined.CalendarMonth, "Informations sur l'abonnement")
        BrandInfoRow(Icons.Outlined.VerifiedUser, "Statut", null, badge = { BrandPill(look.label, look.color, look.background) })
        if (held != null) {
            BrandRowDivider()
            BrandInfoRow(
                Icons.Outlined.WorkspacePremium,
                "Formule",
                when {
                    trialUsed -> "Essai déjà utilisé"
                    held.plan == "trial" -> "Essai gratuit"
                    else -> "DistriGo Solo"
                }
            )
            if (!trialUsed) {
                BrandRowDivider()
                BrandInfoRow(Icons.Outlined.Event, "Date de début", day(held.validFrom))
                BrandRowDivider()
                BrandInfoRow(Icons.Outlined.EventAvailable, if (ended) "Terminé le" else "Date d'expiration", day(held.validTo))
            }
            if (license is LicenseState.Grace) {
                BrandRowDivider()
                BrandInfoRow(Icons.Outlined.HourglassBottom, "Fin de la grâce", day(held.graceUntil))
            }
        }
        BrandCardBody {
            val notice = when {
                trialUsed -> "L'essai gratuit a déjà été utilisé sur ce téléphone : saisissez un code pour activer DistriGo."
                license is LicenseState.OtherBusiness -> otherBusinessNotice(dataName, accountName)
                else -> license.notice(held)
            }
            notice?.let { BrandNotice(it, look.color, look.background) }
            if (batteryHint && license.holdsLicense) {
                // A hint, never a block (decision 6): the button opens the screen where the battery is set
                BrandNotice(
                    "La vérification automatique semble bloquée par l'économie de batterie. Dans les paramètres de " +
                        "DistriGo, réglez la batterie sur « Non restreinte ».",
                    DsColors.Warning,
                    DsColors.WarningLight,
                )
                BrandWideButton("Ouvrir les paramètres de DistriGo", Icons.Outlined.Settings, filled = false, enabled = true, onClick = onOpenAppSettings)
            }
            if (license is LicenseState.OtherBusiness) {
                // The two answers (docs/license_l4_checkin.md §7); signing out asks first, as from « Gestion du compte »
                BrandWideButton(
                    accountName?.let { "Les garder pour $it" } ?: "Garder les données pour ce compte",
                    Icons.Outlined.Check,
                    filled  = true,
                    enabled = work == null,
                    busy    = work == AccountWork.KEEPING_DATA,
                    onClick = onKeepData,
                )
                BrandWideButton("Se déconnecter", Icons.AutoMirrored.Outlined.Logout, filled = false, enabled = work == null, busy = work == AccountWork.SIGNING_OUT, onClick = onSignOut)
            }
            if (license.holdsLicense) {
                // A code pays for more time: the thing to do once the subscription is ending. Not while suspended:
                // the days would be added, but only DistriGo lifts a suspension; nor before the data's business is settled
                val ending = license is LicenseState.RenewSoon || ended
                if (license !is LicenseState.Suspended && license !is LicenseState.OtherBusiness) {
                    BrandWideButton("Saisir un code", Icons.Outlined.Key, filled = ending, enabled = work == null, onClick = onEnterCode)
                }
                // « Abonnement » (docs/license_l5_enforcement.md §8.2): the code, the state and DistriGo's contacts, for any
                // license held, active included
                BrandWideButton("Abonnement et assistance", Icons.Outlined.WorkspacePremium, filled = false, enabled = work == null, onClick = onOpenSubscription)
                held?.let { CheckedLine(it.issuedAt) }
            } else {
                BrandWideButton("Activer cet appareil", Icons.Outlined.VerifiedUser, filled = true, enabled = work == null, busy = work == AccountWork.ACTIVATING, onClick = onActivate)
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
        "Mis à jour le ${CHECKED.format(Instant.ofEpochMilli(issuedAt).atZone(ZoneId.systemDefault()))}",
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
        BrandCardHeader(Icons.Outlined.Apartment, "Informations de l'entreprise")
        when {
            profile != null -> {
                val edit = { field: ProfileField -> if (profile.canEdit) ({ onEdit(field) }) else null }
                BrandInfoRow(Icons.Outlined.Storefront, "Nom de l'entreprise", profile.name, edit(ProfileField.NAME), enabled = idle)
                BrandRowDivider()
                BrandInfoRow(Icons.Outlined.Phone, "Téléphone", profile.contactPhone?.let(::westernDigits), edit(ProfileField.PHONE), enabled = idle)
                BrandRowDivider()
                BrandInfoRow(Icons.Outlined.WorkOutline, "Type d'activité", activityLabel(profile.activity), edit(ProfileField.ACTIVITY), enabled = idle)
                BrandRowDivider()
                BrandInfoRow(Icons.Outlined.Groups, "Nombre de commerciaux", salesRepsLabel(profile.salesReps), edit(ProfileField.SALES_REPS), enabled = idle)
            }
            load == ProfileLoad.FAILED -> BrandCardBody {
                Text(
                    problem ?: "Ces informations n'ont pas pu être lues.",
                    fontFamily = DsBrand.Font,
                    fontWeight = FontWeight.Medium,
                    fontSize   = BrandSize.Body,
                    lineHeight = BrandSize.BodyLine,
                    color      = DsBrand.Slate,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Réessayer", fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Action, color = DsBrand.Blue)
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
    is LicenseState.Active -> Look("Actif", "", DsColors.Success, DsColors.SuccessLight)
    is LicenseState.RenewSoon -> Look("Expire bientôt", "Pensez à renouveler votre abonnement.", DsColors.Warning, DsColors.WarningLight)
    is LicenseState.CheckInSoon -> Look("À vérifier", "Connectez ce téléphone à Internet dans les prochains jours.", DsColors.Warning, DsColors.WarningLight)
    is LicenseState.Grace -> Look("Période de grâce", "L'abonnement est terminé : saisissez un code pour continuer à enregistrer.", DsColors.Danger, DsColors.DangerLight)
    is LicenseState.Expired -> Look("Expiré", "Consultation seule : saisissez un code pour reprendre. Sauvegarde et export restent disponibles.", DsColors.Danger, DsColors.DangerLight)
    is LicenseState.ClockBehind -> Look("Date incorrecte", "La date du téléphone est en retard. Activez « Date et heure automatiques ».", DsColors.Danger, DsColors.DangerLight)
    is LicenseState.NotYetValid -> Look("Pas encore valable", "L'abonnement commence plus tard.", DsColors.Warning, DsColors.WarningLight)
    is LicenseState.CheckInRequired -> Look("Vérification nécessaire", "Connectez ce téléphone à Internet : la vérification se fait toute seule.", DsColors.Warning, DsColors.WarningLight)
    LicenseState.Revoked -> Look("Appareil libéré", "Cet appareil n'est plus associé à l'abonnement. Activez-le pour le reprendre.", DsColors.TextSecondary, DsColors.SurfaceSunken)
    is LicenseState.Suspended -> Look("Suspendu", LicenseClient.SUSPENDED_MESSAGE, DsColors.Danger, DsColors.DangerLight)
    is LicenseState.OtherBusiness -> Look("Autre entreprise", "Ces données appartiennent à une autre entreprise.", DsColors.Danger, DsColors.DangerLight)
    is LicenseState.NoLicense -> Look("Non activé", "Activez cet appareil pour commencer.", DsColors.TextSecondary, DsColors.SurfaceSunken)
    LicenseState.Unchecked -> Look("…", "Lecture de la licence…", DsColors.TextSecondary, DsColors.SurfaceSunken)
}

/** OtherBusiness, with the names when known: the data's (its settings) and the account's (its profile). */
private fun otherBusinessNotice(dataName: String?, accountName: String?): String {
    val data = dataName?.let { "Les données de ce téléphone (« $it »)" } ?: "Les données de ce téléphone"
    val account = accountName?.let { "« $it »" } ?: "celle de ce compte"
    return "$data appartiennent à une autre entreprise que $account. Gardez-les pour votre entreprise si elles sont " +
        "bien à vous ; sinon, déconnectez-vous."
}

/** The note under the dates: nothing while all is well, otherwise what the state asks for, with its date. */
private fun LicenseState.notice(held: License?): String? = when (this) {
    is LicenseState.Active -> null
    is LicenseState.RenewSoon -> held?.let { "Votre abonnement se termine le ${day(it.validTo)} : saisissez un code pour le prolonger." } ?: look().explanation
    is LicenseState.CheckInSoon -> held?.let { "Connectez ce téléphone à Internet avant le ${day(it.offlineUntil)} : la vérification se fait toute seule." } ?: look().explanation
    else -> look().explanation.ifEmpty { null }
}

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)
private val MOMENT = DateTimeFormatter.ofPattern("d MMM yyyy 'à' HH:mm", Locale.FRENCH)
private val CHECKED = DateTimeFormatter.ofPattern("d MMM 'à' HH:mm", Locale.FRENCH)

internal fun day(epochMillis: Long): String = DAY.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

internal fun moment(epochMillis: Long): String = MOMENT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
