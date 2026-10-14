package com.distrigo.app.ui.account

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import com.distrigo.app.data.license.LicenseState
import com.distrigo.app.data.license.heldLicense
import com.distrigo.app.data.license.holdsLicense
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.onboarding.BottomWave
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
 * The account, this phone's subscription and the business, laid out as the owner's mockup in the onboarding's
 * identity (docs/activation_codes.md, step C5; docs/license_architecture.md §7).
 */
@Composable
fun CompteScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    onOpenOnboarding: (OnboardingStart) -> Unit,
) {
    BackHandler { onBack() }
    val state by viewModel.state.collectAsState()

    val scroll = rememberScrollState()
    // A hairline under the bar once the cards pass beneath it
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }

    Column(Modifier.fillMaxSize().background(DsBrand.Mist)) {
        // Pinned: only what is under it scrolls
        CompteTopBar(onBack, divided = scrolled)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // At least the space left tall, so the wave sits at the bottom; in the page's flow, so it never covers a row
            Column(Modifier.verticalScroll(scroll).heightIn(min = maxHeight)) {
                Column(
                    modifier            = Modifier.padding(start = DsSpacing.lg, end = DsSpacing.lg, top = DsSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
                ) {
                    state.message?.let { MessageLine(it) }
                    when (val account = state.account) {
                        AccountState.Loading -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DsBrand.Blue)
                        }
                        AccountState.SignedOut -> SignedOutCard(
                            onSignIn = { onOpenOnboarding(OnboardingStart.SIGN_IN) },
                            onSignUp = { onOpenOnboarding(OnboardingStart.SIGN_UP) },
                            onCode   = { onOpenOnboarding(OnboardingStart.CODE) },
                        )
                        is AccountState.SignedIn -> SignedInCards(account.session, state, viewModel)
                    }
                }
                Spacer(Modifier.weight(1f).heightIn(min = DsSpacing.xxl))
                BottomWave(Modifier)
            }
        }
    }

    state.dialog?.let { AccountDialogs(it, state, viewModel) }
}

@Composable
private fun CompteTopBar(onBack: () -> Unit, divided: Boolean) {
    Column(Modifier.fillMaxWidth().background(DsBrand.Mist)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = DsSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsBrand.Ink)
            }
            Text("Compte", fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = CompteSize.ScreenTitle, color = DsBrand.Ink)
        }
        HorizontalDivider(color = if (divided) DsBrand.CardBorder else Color.Transparent, thickness = 1.dp)
    }
}

@Composable
private fun ColumnScope.SignedInCards(session: AuthSession, state: AccountUiState, viewModel: AccountViewModel) {
    val idle = state.work == null
    IdentityCard(session, state.profile)
    state.activationCode?.let { CodeCard(it, withoutEmail = session.isAnonymous) }
    SubscriptionCard(
        license     = state.license,
        work        = state.work,
        onActivate  = viewModel::activate,
        onCheckIn   = viewModel::checkIn,
        onEnterCode = { viewModel.ask(AccountDialog.EnterCode()) },
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
        CardHeader(Icons.Outlined.Settings, "Gestion du compte")
        if (session.isAnonymous) {
            ActionRow(Icons.Outlined.Email, "Ajouter une adresse e-mail", enabled = idle) { viewModel.ask(AccountDialog.AddEmail()) }
            RowDivider()
        }
        ActionRow(Icons.AutoMirrored.Outlined.Logout, "Se déconnecter", enabled = idle, busy = state.work == AccountWork.SIGNING_OUT) {
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
            fontSize   = CompteSize.Body,
            color      = DsColors.Danger
        )
    }
}

// ── Cards ──

@Composable
private fun SignedOutCard(onSignIn: () -> Unit, onSignUp: () -> Unit, onCode: () -> Unit) {
    BrandCard {
        CardBody {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar { Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = DsBrand.Blue, modifier = Modifier.size(CompteSize.AvatarIcon)) }
                Spacer(Modifier.width(DsSpacing.lg))
                Column(Modifier.weight(1f)) {
                    Text("Activez DistriGo sur ce téléphone", fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = CompteSize.Name, lineHeight = 21.sp, color = DsBrand.Ink)
                    Spacer(Modifier.height(DsSpacing.xs))
                    Text(
                        "Un compte porte votre abonnement. Vos clients, ventes et stock restent sur ce téléphone.",
                        fontFamily = DsBrand.Font,
                        fontWeight = FontWeight.Medium,
                        fontSize   = CompteSize.Small,
                        lineHeight = CompteSize.SmallLine,
                        color      = DsBrand.Slate
                    )
                }
            }
            Spacer(Modifier.height(DsSpacing.xs))
            IconButtonWide("Se connecter", Icons.AutoMirrored.Outlined.Login, filled = true, enabled = true, onClick = onSignIn)
            IconButtonWide("Créer un compte", Icons.Outlined.PersonAddAlt, filled = false, enabled = true, onClick = onSignUp)
            // A code bought from DistriGo or a reseller: no e-mail needed (docs/activation_codes.md)
            IconButtonWide("Activer avec un code", Icons.Outlined.Key, filled = false, enabled = true, onClick = onCode)
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
                    session.isAnonymous -> Icon(Icons.Outlined.Key, contentDescription = null, tint = DsBrand.Blue, modifier = Modifier.size(CompteSize.AvatarIcon))
                    initials != null -> Text(initials, fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = CompteSize.AvatarInitials, color = DsBrand.Blue)
                    else -> Icon(Icons.Outlined.Person, contentDescription = null, tint = DsBrand.Blue, modifier = Modifier.size(CompteSize.AvatarIcon))
                }
            }
            Spacer(Modifier.width(DsSpacing.lg))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = CompteSize.Name, lineHeight = 21.sp, color = DsBrand.Ink, maxLines = 2)
                profile?.contactPhone?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = CompteSize.Body, color = DsBrand.Slate)
                }
                caption?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = CompteSize.Small, color = DsBrand.Slate, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Avatar(content: @Composable () -> Unit) {
    Box(
        modifier         = Modifier.size(CompteSize.Avatar).clip(CircleShape).background(DsBrand.Blue.copy(alpha = 0.1f)),
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
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = DsBrand.Ink, modifier = Modifier.size(CompteSize.HeaderIcon))
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Votre code d'activation", fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = CompteSize.CardTitle, color = DsBrand.Ink)
                Spacer(Modifier.height(2.dp))
                CodeText(if (shown) formatted else formatted.take(4) + "-••••-••••", fontSize = CompteSize.Code)
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
            fontSize   = CompteSize.Small,
            lineHeight = CompteSize.SmallLine,
            color      = DsBrand.Slate,
            modifier   = Modifier.padding(start = DsSpacing.lg + CompteSize.HeaderIcon + DsSpacing.md, end = DsSpacing.lg, top = DsSpacing.sm, bottom = DsSpacing.lg)
        )
    }
}

/** The code in the brand's heaviest weight, spaced so each character reads apart. */
@Composable
internal fun CodeText(text: String, fontSize: TextUnit = 18.sp) {
    Text(text, fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, letterSpacing = 1.5.sp, color = DsBrand.Ink)
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier         = Modifier
            .size(CompteSize.RoundAction)
            .clip(CircleShape)
            .background(DsBrand.Blue.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = DsBrand.Blue, modifier = Modifier.size(CompteSize.ButtonIcon))
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
    onActivate: () -> Unit,
    onCheckIn: () -> Unit,
    onEnterCode: () -> Unit,
) {
    val look = license.look()
    val held = license.heldLicense
    val ended = license is LicenseState.Grace || license is LicenseState.Expired
    // An e-mail sign-up on a phone that already had its trial: a license that ended the day before it began
    val trialUsed = held != null && held.plan == "trial" && held.validTo < held.validFrom
    BrandCard {
        // The status in its own row: next to the title, any pill pushes it onto two lines on a phone
        CardHeader(Icons.Outlined.CalendarMonth, "Informations sur l'abonnement")
        InfoRow(Icons.Outlined.VerifiedUser, "Statut", null, badge = { StatusPill(look.label, look.color, look.background) })
        if (held != null) {
            RowDivider()
            InfoRow(
                Icons.Outlined.WorkspacePremium,
                "Formule",
                when {
                    trialUsed -> "Essai déjà utilisé"
                    held.plan == "trial" -> "Essai gratuit"
                    else -> "DistriGo Solo"
                }
            )
            if (!trialUsed) {
                RowDivider()
                InfoRow(Icons.Outlined.Event, "Date de début", day(held.validFrom))
                RowDivider()
                InfoRow(Icons.Outlined.EventAvailable, if (ended) "Terminé le" else "Date d'expiration", day(held.validTo))
            }
            if (license is LicenseState.Grace) {
                RowDivider()
                InfoRow(Icons.Outlined.HourglassBottom, "Fin de la grâce", day(held.graceUntil))
            }
        }
        CardBody {
            val notice = if (trialUsed) "L'essai gratuit a déjà été utilisé sur ce téléphone : saisissez un code pour activer DistriGo." else license.notice(held)
            notice?.let { CardNotice(it, look.color, look.background) }
            if (license.holdsLicense) {
                // A code pays for more time: the thing to do once the subscription is ending
                val ending = license is LicenseState.RenewSoon || ended
                IconButtonWide("Saisir un code", Icons.Outlined.Key, filled = ending, enabled = work == null, onClick = onEnterCode)
                held?.let { CheckedLine(it.issuedAt, checking = work == AccountWork.CHECKING_IN, enabled = work == null, onCheckIn = onCheckIn) }
            } else {
                IconButtonWide("Activer cet appareil", Icons.Outlined.VerifiedUser, filled = true, enabled = work == null, busy = work == AccountWork.ACTIVATING, onClick = onActivate)
            }
        }
    }
}

/**
 * « Vérifié le … · Vérifier »: the last check-in, and one now. Until the app checks in by itself (L4), this is
 * how the phone renews the days it may stay offline.
 */
@Composable
private fun CheckedLine(issuedAt: Long, checking: Boolean, enabled: Boolean, onCheckIn: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("Vérifié le ${CHECKED.format(Instant.ofEpochMilli(issuedAt).atZone(ZoneId.systemDefault()))}", fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = CompteSize.Small, color = DsBrand.Slate)
        Text("  ·  ", fontFamily = DsBrand.Font, fontSize = CompteSize.Small, color = DsBrand.Slate)
        if (checking) {
            CircularProgressIndicator(color = DsBrand.Blue, strokeWidth = 2.dp, modifier = Modifier.padding(vertical = DsSpacing.sm).size(14.dp))
        } else {
            Text(
                "Vérifier",
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.Bold,
                fontSize   = CompteSize.Small,
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
        CardHeader(Icons.Outlined.Apartment, "Informations de l'entreprise")
        when {
            profile != null -> {
                val edit = { field: ProfileField -> if (profile.canEdit) ({ onEdit(field) }) else null }
                InfoRow(Icons.Outlined.Storefront, "Nom de l'entreprise", profile.name, edit(ProfileField.NAME), enabled = idle)
                RowDivider()
                InfoRow(Icons.Outlined.Phone, "Téléphone", profile.contactPhone, edit(ProfileField.PHONE), enabled = idle)
                RowDivider()
                InfoRow(Icons.Outlined.WorkOutline, "Type d'activité", activityLabel(profile.activity), edit(ProfileField.ACTIVITY), enabled = idle)
                RowDivider()
                InfoRow(Icons.Outlined.Groups, "Nombre de commerciaux", salesRepsLabel(profile.salesReps), edit(ProfileField.SALES_REPS), enabled = idle)
            }
            load == ProfileLoad.FAILED -> CardBody {
                Text(
                    problem ?: "Ces informations n'ont pas pu être lues.",
                    fontFamily = DsBrand.Font,
                    fontWeight = FontWeight.Medium,
                    fontSize   = CompteSize.Body,
                    lineHeight = CompteSize.BodyLine,
                    color      = DsBrand.Slate,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Réessayer", fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = CompteSize.Action, color = DsBrand.Blue)
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
