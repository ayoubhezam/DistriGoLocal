package com.distrigo.app.ui.account

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
) {
    BackHandler { onBack() }
    val state by viewModel.state.collectAsState()

    BrandScreen(title = "Compte", onBack = onBack) {
        state.message?.let { BrandMessage(it.text, it.isError) }
        when (val account = state.account) {
            AccountState.Loading -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DsBrand.Blue)
            }
            AccountState.SignedOut -> SignedOutCard(
                onSignIn = { onOpenOnboarding(OnboardingStart.SIGN_IN) },
                onSignUp = { onOpenOnboarding(OnboardingStart.SIGN_UP) },
                onCode   = { onOpenOnboarding(OnboardingStart.CODE) },
            )
            is AccountState.SignedIn -> SignedInCards(account.session, state, viewModel, onOpenAdmin)
        }
    }

    state.dialog?.let { AccountDialogs(it, state, viewModel) }
}

@Composable
private fun ColumnScope.SignedInCards(session: AuthSession, state: AccountUiState, viewModel: AccountViewModel, onOpenAdmin: () -> Unit) {
    val idle = state.work == null
    IdentityCard(session, state.profile)
    state.activationCode?.let { CodeCard(it, withoutEmail = session.isAnonymous) }
    val context = LocalContext.current
    SubscriptionCard(
        license     = state.license,
        work        = state.work,
        batteryHint = state.batteryHint,
        onActivate  = viewModel::activate,
        onCheckIn   = viewModel::checkIn,
        onEnterCode = { viewModel.ask(AccountDialog.EnterCode()) },
        onOpenAppSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        },
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

@Composable
private fun SignedOutCard(onSignIn: () -> Unit, onSignUp: () -> Unit, onCode: () -> Unit) {
    BrandCard {
        BrandCardBody {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar { Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = DsBrand.Blue, modifier = Modifier.size(BrandSize.AvatarIcon)) }
                Spacer(Modifier.width(DsSpacing.lg))
                Column(Modifier.weight(1f)) {
                    Text("Activez DistriGo sur ce téléphone", fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = BrandSize.Name, lineHeight = 21.sp, color = DsBrand.Ink)
                    Spacer(Modifier.height(DsSpacing.xs))
                    Text(
                        "Un compte porte votre abonnement. Vos clients, ventes et stock restent sur ce téléphone.",
                        fontFamily = DsBrand.Font,
                        fontWeight = FontWeight.Medium,
                        fontSize   = BrandSize.Small,
                        lineHeight = BrandSize.SmallLine,
                        color      = DsBrand.Slate
                    )
                }
            }
            Spacer(Modifier.height(DsSpacing.xs))
            BrandWideButton("Se connecter", Icons.AutoMirrored.Outlined.Login, filled = true, enabled = true, onClick = onSignIn)
            BrandWideButton("Créer un compte", Icons.Outlined.PersonAddAlt, filled = false, enabled = true, onClick = onSignUp)
            // A code bought from DistriGo or a reseller: no e-mail needed (docs/activation_codes.md)
            BrandWideButton("Activer avec un code", Icons.Outlined.Key, filled = false, enabled = true, onClick = onCode)
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
    onActivate: () -> Unit,
    onCheckIn: () -> Unit,
    onEnterCode: () -> Unit,
    onOpenAppSettings: () -> Unit,
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
            val notice = if (trialUsed) "L'essai gratuit a déjà été utilisé sur ce téléphone : saisissez un code pour activer DistriGo." else license.notice(held)
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
            if (license.holdsLicense) {
                // A code pays for more time: the thing to do once the subscription is ending. Not while suspended:
                // the days would be added, but only DistriGo lifts a suspension
                val ending = license is LicenseState.RenewSoon || ended
                if (license !is LicenseState.Suspended) {
                    BrandWideButton("Saisir un code", Icons.Outlined.Key, filled = ending, enabled = work == null, onClick = onEnterCode)
                }
                held?.let { CheckedLine(it.issuedAt, checking = work == AccountWork.CHECKING_IN, enabled = work == null, onCheckIn = onCheckIn) }
            } else {
                BrandWideButton("Activer cet appareil", Icons.Outlined.VerifiedUser, filled = true, enabled = work == null, busy = work == AccountWork.ACTIVATING, onClick = onActivate)
            }
        }
    }
}

/**
 * « Vérifié le … · Vérifier »: the last check-in, the button's or the background job's (the license's issue time),
 * and one now.
 */
@Composable
private fun CheckedLine(issuedAt: Long, checking: Boolean, enabled: Boolean, onCheckIn: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("Vérifié le ${CHECKED.format(Instant.ofEpochMilli(issuedAt).atZone(ZoneId.systemDefault()))}", fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = BrandSize.Small, color = DsBrand.Slate)
        Text("  ·  ", fontFamily = DsBrand.Font, fontSize = BrandSize.Small, color = DsBrand.Slate)
        if (checking) {
            CircularProgressIndicator(color = DsBrand.Blue, strokeWidth = 2.dp, modifier = Modifier.padding(vertical = DsSpacing.sm).size(14.dp))
        } else {
            Text(
                "Vérifier",
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.Bold,
                fontSize   = BrandSize.Small,
                color      = if (enabled) DsBrand.Blue else DsBrand.Slate,
                modifier   = Modifier
                    .clip(DsShapes.small)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onCheckIn)
                    .padding(horizontal = DsSpacing.xs, vertical = DsSpacing.sm)
            )
        }
    }
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
                BrandInfoRow(Icons.Outlined.Phone, "Téléphone", profile.contactPhone, edit(ProfileField.PHONE), enabled = idle)
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
    is LicenseState.CheckInRequired -> Look("Vérification nécessaire", "Connectez ce téléphone à Internet, puis touchez « Vérifier ».", DsColors.Warning, DsColors.WarningLight)
    LicenseState.Revoked -> Look("Appareil libéré", "Cet appareil n'est plus associé à l'abonnement. Activez-le pour le reprendre.", DsColors.TextSecondary, DsColors.SurfaceSunken)
    is LicenseState.Suspended -> Look("Suspendu", LicenseClient.SUSPENDED_MESSAGE, DsColors.Danger, DsColors.DangerLight)
    is LicenseState.NoLicense -> Look("Non activé", "Activez cet appareil pour commencer.", DsColors.TextSecondary, DsColors.SurfaceSunken)
    LicenseState.Unchecked -> Look("…", "Lecture de la licence…", DsColors.TextSecondary, DsColors.SurfaceSunken)
}

/** The note under the dates: nothing while all is well, otherwise what the state asks for, with its date. */
private fun LicenseState.notice(held: License?): String? = when (this) {
    is LicenseState.Active -> null
    is LicenseState.RenewSoon -> held?.let { "Votre abonnement se termine le ${day(it.validTo)} : saisissez un code pour le prolonger." } ?: look().explanation
    is LicenseState.CheckInSoon -> held?.let { "Connectez ce téléphone à Internet avant le ${day(it.offlineUntil)}, puis touchez « Vérifier »." } ?: look().explanation
    else -> look().explanation.ifEmpty { null }
}

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)
private val MOMENT = DateTimeFormatter.ofPattern("d MMM yyyy 'à' HH:mm", Locale.FRENCH)
private val CHECKED = DateTimeFormatter.ofPattern("d MMM 'à' HH:mm", Locale.FRENCH)

internal fun day(epochMillis: Long): String = DAY.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

internal fun moment(epochMillis: Long): String = MOMENT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
