package com.distrigo.app.ui.account

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.auth.AuthSession
import com.distrigo.app.data.license.heldLicense
import com.distrigo.app.data.license.LicenseState
import com.distrigo.app.data.license.SeatHolder
import com.distrigo.app.data.license.holdsLicense
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import com.distrigo.app.ui.onboarding.CODE_LENGTH
import com.distrigo.app.ui.onboarding.CodeDashes
import com.distrigo.app.ui.onboarding.MIN_PASSWORD
import com.distrigo.app.ui.onboarding.OnboardingStart
import com.distrigo.app.ui.onboarding.formatCode
import com.distrigo.app.ui.onboarding.normalizeCode
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The account and this phone's subscription: docs/license_architecture.md §7. */
@Composable
fun CompteScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    onOpenOnboarding: (OnboardingStart) -> Unit,
) {
    BackHandler { onBack() }
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = "Compte", leading = DsTopBarLeading.Back(onBack))
        Column(
            modifier            = Modifier.verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md)
        ) {
            state.message?.let { MessageLine(it) }
            when (val account = state.account) {
                AccountState.Loading -> Box(Modifier.fillMaxWidth().padding(DsSpacing.xxxl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DsColors.Primary)
                }
                AccountState.SignedOut -> SignedOutCard(
                    onSignIn = { onOpenOnboarding(OnboardingStart.SIGN_IN) },
                    onSignUp = { onOpenOnboarding(OnboardingStart.SIGN_UP) },
                    onCode   = { onOpenOnboarding(OnboardingStart.CODE) },
                )
                is AccountState.SignedIn -> {
                    AccountCard(account.session)
                    SubscriptionCard(
                        license  = state.license,
                        work     = state.work,
                        onActivate = viewModel::activate,
                        onCheckIn  = viewModel::checkIn,
                        onEnterCode = { viewModel.ask(AccountDialog.EnterCode()) },
                    )
                    state.activationCode?.let { CodeCard(it, withoutEmail = account.session.isAnonymous) }
                    if (account.session.isAnonymous) {
                        AddEmailCard(enabled = state.work == null, onAdd = { viewModel.ask(AccountDialog.AddEmail()) })
                    }
                    OutlinedButton(
                        onClick  = { viewModel.ask(AccountDialog.ConfirmSignOut) },
                        enabled  = state.work == null,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = DsShapes.medium
                    ) {
                        if (state.work == AccountWork.SIGNING_OUT) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Se déconnecter", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                        }
                    }
                    TextButton(
                        onClick  = { viewModel.ask(AccountDialog.ConfirmDelete) },
                        enabled  = state.work == null,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            if (state.work == AccountWork.DELETING) "Suppression…" else "Supprimer le compte",
                            fontSize = DsTextSize.bodySmall,
                            color    = DsColors.Danger
                        )
                    }
                }
            }
        }
    }

    state.dialog?.let { AccountDialogs(it, state, viewModel) }
}

@Composable
private fun SignedOutCard(onSignIn: () -> Unit, onSignUp: () -> Unit, onCode: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(Icons.Default.VerifiedUser, DsColors.PrimaryLight, DsColors.Primary)
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Activez DistriGo sur ce téléphone", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                Text(
                    "Un compte porte votre abonnement. Vos clients, ventes et stock restent sur ce téléphone.",
                    fontSize = DsTextSize.bodySmall,
                    color    = DsColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(DsSpacing.lg))
        PrimaryButton("Se connecter", busy = false, enabled = true, onClick = onSignIn)
        Spacer(Modifier.height(DsSpacing.sm))
        OutlinedButton(onClick = onSignUp, modifier = Modifier.fillMaxWidth().height(48.dp), shape = DsShapes.medium) {
            Text("Créer un compte", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        }
        // A code bought from DistriGo or a reseller: no e-mail needed (docs/activation_codes.md)
        TextButton(onClick = onCode, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Activer avec un code", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        }
    }
}

@Composable
private fun AccountCard(session: AuthSession) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(DsShapes.pill).background(DsColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                // An account from an activation code has no name: a key says how it was opened
                val initials = session.initials
                if (initials == null && session.isAnonymous) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(24.dp))
                } else {
                    Text(initials ?: "?", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.Primary)
                }
            }
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(session.displayName ?: session.email ?: if (session.isAnonymous) "Compte sans e-mail" else "Compte", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                session.email?.let { Text(it, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary) }
                Text(
                    when {
                        session.isAnonymous -> "Activé avec un code"
                        session.provider == "google" -> "Connecté avec Google"
                        else -> "Connecté par e-mail"
                    },
                    fontSize = DsTextSize.caption,
                    color    = DsColors.TextTertiary
                )
            }
        }
    }
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
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Abonnement", fontSize = DsTextSize.title, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(
                look.label,
                fontSize   = DsTextSize.caption,
                fontWeight = FontWeight.SemiBold,
                color      = look.color,
                modifier   = Modifier.clip(DsShapes.pill).background(look.background).padding(horizontal = DsSpacing.sm, vertical = DsSpacing.xs)
            )
        }
        Spacer(Modifier.height(DsSpacing.sm))
        Text(look.explanation, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)

        license.heldLicense?.let { held ->
            Spacer(Modifier.height(DsSpacing.md))
            Detail("Formule", if (held.plan == "trial") "Essai gratuit" else "DistriGo Solo")
            Detail(if (license is LicenseState.Grace || license is LicenseState.Expired) "Terminé le" else "Valable jusqu'au", day(held.validTo))
            if (license is LicenseState.Grace) Detail("Période de grâce jusqu'au", day(held.graceUntil))
            Detail("Dernière vérification", moment(held.issuedAt))
            Detail("Sans connexion jusqu'au", day(held.offlineUntil))
        }

        Spacer(Modifier.height(DsSpacing.lg))
        if (license.holdsLicense) {
            // A code pays for more time: the first thing to do once the subscription is ending
            val ending = license is LicenseState.RenewSoon || license is LicenseState.Grace || license is LicenseState.Expired
            if (ending) {
                PrimaryButton("Saisir un code", busy = false, enabled = work == null, onClick = onEnterCode)
                Spacer(Modifier.height(DsSpacing.sm))
            }
            OutlinedButton(
                onClick  = onCheckIn,
                enabled  = work == null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = DsShapes.medium
            ) {
                if (work == AccountWork.CHECKING_IN) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("Vérifier maintenant", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
                }
            }
            if (!ending) {
                TextButton(onClick = onEnterCode, enabled = work == null, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(DsSpacing.sm))
                    Text("Saisir un code", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
                }
            }
        } else {
            PrimaryButton("Activer cet appareil", busy = work == AccountWork.ACTIVATING, enabled = work == null, onClick = onActivate)
        }
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
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(Icons.Default.Key, DsColors.PrimaryLight, DsColors.Primary)
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Votre code d'activation", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                Text("La clé de votre entreprise", fontSize = DsTextSize.caption, color = DsColors.TextTertiary)
            }
        }
        Spacer(Modifier.height(DsSpacing.md))
        Row(
            modifier          = Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.Surface).padding(start = DsSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CodeText(if (shown) formatted else formatted.take(4) + "-••••-••••", Modifier.weight(1f))
            IconButton(onClick = { shown = !shown }) {
                Icon(
                    if (shown) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (shown) "Masquer le code" else "Afficher le code",
                    tint               = DsColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(DsSpacing.sm))
        Text(
            if (withoutEmail) "Gardez-le en lieu sûr : sans adresse e-mail, lui seul ouvre votre entreprise sur un nouveau téléphone."
            else "Gardez-le en lieu sûr : il ouvre votre entreprise sur un nouveau téléphone.",
            fontSize = DsTextSize.bodySmall,
            color    = DsColors.TextSecondary
        )
        TextButton(
            onClick  = {
                copyCode(context, formatted)
                copied = true
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = null, tint = DsColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(DsSpacing.sm))
            Text(if (copied) "Copié" else "Copier", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        }
    }
}

@Composable
private fun CodeText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontFamily    = FontFamily.Monospace,
        fontSize      = DsTextSize.title,
        fontWeight    = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color         = DsColors.TextPrimary,
        modifier      = modifier
    )
}

/** Android 13 and later keep a sensitive clip out of the clipboard preview, like a password. */
private fun copyCode(context: Context, code: String) {
    val clip = ClipData.newPlainText("Code d'activation", code)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

/** For an account made by a code: an e-mail and a password, to sign in without the code too. */
@Composable
private fun AddEmailCard(enabled: Boolean, onAdd: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(Icons.Default.Email, DsColors.PrimaryLight, DsColors.Primary)
            Spacer(Modifier.width(DsSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Adresse e-mail", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
                Text(
                    "Facultatif : avec une adresse e-mail et un mot de passe, vous pourrez aussi vous connecter sans votre code.",
                    fontSize = DsTextSize.bodySmall,
                    color    = DsColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(DsSpacing.md))
        OutlinedButton(onClick = onAdd, enabled = enabled, modifier = Modifier.fillMaxWidth().height(48.dp), shape = DsShapes.medium) {
            Text("Ajouter une adresse e-mail", fontSize = DsTextSize.body, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        }
    }
}

@Composable
private fun AccountDialogs(dialog: AccountDialog, state: AccountUiState, viewModel: AccountViewModel) {
    when (dialog) {
        is AccountDialog.BusinessName -> {
            var name by rememberSaveable { mutableStateOf(dialog.suggestion) }
            Dialog(
                title   = "Votre entreprise",
                text    = "Son nom apparaît dans votre compte. L'essai gratuit de 30 jours commence maintenant.",
                confirm = "Activer" to { if (name.isNotBlank()) viewModel.answerBusinessName(dialog.request, name) },
                dismiss = "Annuler" to viewModel::dismissDialog,
            ) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Nom de l'entreprise") },
                    singleLine    = true,
                    shape         = DsShapes.medium,
                    colors        = dsTextFieldColors(),
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        }
        is AccountDialog.Transfer -> Dialog(
            title   = "Abonnement utilisé ailleurs",
            text    = "Votre abonnement est actif sur ${holders(dialog.holders)}. Le transférer sur ce téléphone ? " +
                "L'autre téléphone passera en consultation seule à sa prochaine connexion. 3 transferts possibles par mois.",
            confirm = "Transférer ici" to { viewModel.confirmTransfer(dialog.request) },
            dismiss = "Annuler" to viewModel::dismissDialog,
        )
        is AccountDialog.TransferLimit -> Dialog(
            title   = "Limite de transferts atteinte",
            text    = "L'abonnement a déjà changé 3 fois de téléphone ce mois-ci." +
                (dialog.nextAt?.let { " Prochain transfert possible le ${day(it.toEpochMilli())}." } ?: ""),
            confirm = "Compris" to viewModel::dismissDialog,
        )
        is AccountDialog.Activated -> Dialog(
            title   = "DistriGo est activé",
            text    = buildString {
                append(if (dialog.trial == true) "Votre essai gratuit de 30 jours commence aujourd'hui." else "Cet appareil porte maintenant votre abonnement.")
                if (dialog.reduced) {
                    append(" Ce téléphone n'a pas pu prouver son intégrité : il devra se connecter à Internet chaque jour.")
                }
            },
            confirm = "Continuer" to viewModel::dismissDialog,
        )
        is AccountDialog.EnterCode -> {
            var code by rememberSaveable { mutableStateOf("") }
            val busy = state.work == AccountWork.RENEWING
            Dialog(
                title   = "Saisir un code",
                text    = "Le code reçu de DistriGo ou de votre revendeur prolonge votre abonnement.",
                confirm = "Valider" to { viewModel.renewWithCode(code) },
                dismiss = "Annuler" to viewModel::dismissDialog,
                busy    = busy,
            ) {
                OutlinedTextField(
                    value                = code,
                    onValueChange        = {
                        code = normalizeCode(it)
                        if (dialog.error != null) viewModel.ask(AccountDialog.EnterCode())
                    },
                    enabled              = !busy,
                    label                = { Text("Code d'activation") },
                    placeholder          = { Text("XXXX-XXXX-XXXX") },
                    singleLine           = true,
                    isError              = dialog.error != null,
                    supportingText       = { Text(dialog.error ?: "${code.length} / $CODE_LENGTH") },
                    textStyle            = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                    visualTransformation = CodeDashes,
                    keyboardOptions      = KeyboardOptions(
                        capitalization     = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType       = KeyboardType.Ascii,
                        imeAction          = ImeAction.Done
                    ),
                    keyboardActions      = KeyboardActions(onDone = { viewModel.renewWithCode(code) }),
                    shape                = DsShapes.medium,
                    colors               = dsTextFieldColors(),
                    modifier             = Modifier.fillMaxWidth()
                )
            }
        }
        is AccountDialog.Renewed -> Dialog(
            title   = "Abonnement prolongé",
            text    = dialog.validTo?.let { "Merci ! Votre abonnement est valable jusqu'au ${day(it)}." }
                ?: "Merci ! Le code a été ajouté à votre abonnement.",
            confirm = "Continuer" to viewModel::dismissDialog,
        )
        is AccountDialog.AddEmail -> {
            var email by rememberSaveable { mutableStateOf("") }
            var password by rememberSaveable { mutableStateOf("") }
            var visible by rememberSaveable { mutableStateOf(false) }
            val busy = state.work == AccountWork.ADDING_EMAIL
            Dialog(
                title   = "Ajouter une adresse e-mail",
                text    = "Vous pourrez vous connecter avec elle sur n'importe quel téléphone. " +
                    "Votre entreprise, votre abonnement et votre code ne changent pas.",
                confirm = "Ajouter" to { viewModel.addEmail(email, password) },
                dismiss = "Annuler" to viewModel::dismissDialog,
                busy    = busy,
            ) {
                OutlinedTextField(
                    value           = email,
                    onValueChange   = { email = it },
                    enabled         = !busy,
                    label           = { Text("Adresse e-mail") },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    shape           = DsShapes.medium,
                    colors          = dsTextFieldColors(),
                    modifier        = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value                = password,
                    onValueChange        = { password = it },
                    enabled              = !busy,
                    label                = { Text("Mot de passe") },
                    supportingText       = { Text("$MIN_PASSWORD caractères au moins") },
                    singleLine           = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon         = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (visible) "Masquer le mot de passe" else "Afficher le mot de passe"
                            )
                        }
                    },
                    keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions      = KeyboardActions(onDone = { viewModel.addEmail(email, password) }),
                    shape                = DsShapes.medium,
                    colors               = dsTextFieldColors(),
                    modifier             = Modifier.fillMaxWidth()
                )
                dialog.error?.let { Text(it, fontSize = DsTextSize.bodySmall, color = DsColors.Danger) }
            }
        }
        AccountDialog.ConfirmSignOut -> {
            val leaving = "Ce téléphone sera libéré de l'abonnement et passera en consultation seule. " +
                "Vos données restent sur le téléphone. Une connexion Internet est nécessaire."
            val withoutEmail = (state.account as? AccountState.SignedIn)?.session?.isAnonymous == true
            if (!withoutEmail) {
                Dialog(
                    title   = "Se déconnecter ?",
                    text    = leaving,
                    confirm = "Se déconnecter" to viewModel::signOut,
                    dismiss = "Annuler" to viewModel::dismissDialog,
                )
            } else {
                // No e-mail to sign in with: the code is the only way back
                val code = state.activationCode
                Dialog(
                    title   = "Se déconnecter ?",
                    text    = if (code != null) {
                        "Ce compte n'a pas d'adresse e-mail : pour y revenir, il faudra ce code d'activation. Notez-le avant de continuer."
                    } else {
                        "Ce compte n'a pas d'adresse e-mail, et son code d'activation n'est pas enregistré sur ce téléphone : " +
                            "sans ce code, vous ne pourrez plus y revenir. Ajoutez d'abord une adresse e-mail."
                    },
                    confirm = "Se déconnecter" to viewModel::signOut,
                    dismiss = "Annuler" to viewModel::dismissDialog,
                    danger  = true,
                ) {
                    if (code != null) {
                        Box(
                            modifier         = Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsColors.SurfaceSunken).padding(DsSpacing.md),
                            contentAlignment = Alignment.Center
                        ) {
                            CodeText(formatCode(code))
                        }
                    } else {
                        TextButton(onClick = { viewModel.ask(AccountDialog.AddEmail()) }) {
                            Text("Ajouter une adresse e-mail", fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
                        }
                    }
                    Text(leaving)
                }
            }
        }
        AccountDialog.ConfirmDelete -> Dialog(
            title   = "Supprimer le compte ?",
            text    = "Le compte, l'entreprise associée et l'abonnement seront supprimés définitivement. " +
                "Les données de ce téléphone (clients, ventes, stock) restent sur le téléphone." +
                if (state.activationCode != null) " Votre code d'activation ne fonctionnera plus." else "",
            confirm = "Supprimer" to viewModel::deleteAccount,
            dismiss = "Annuler" to viewModel::dismissDialog,
            danger  = true,
        )
    }
}

// ── Pieces ──

private data class Look(val label: String, val explanation: String, val color: Color, val background: Color)

private fun LicenseState.look(): Look = when (this) {
    is LicenseState.Active -> Look("Actif", "Tout fonctionne. L'application se vérifie seule dès qu'elle a Internet.", DsColors.Success, DsColors.SuccessLight)
    is LicenseState.RenewSoon -> Look("Expire bientôt", "Pensez à renouveler votre abonnement.", DsColors.Warning, DsColors.WarningLight)
    is LicenseState.CheckInSoon -> Look("À vérifier", "Connectez ce téléphone à Internet dans les prochains jours.", DsColors.Warning, DsColors.WarningLight)
    is LicenseState.Grace -> Look("Période de grâce", "L'abonnement est terminé : renouvelez-le pour continuer à enregistrer.", DsColors.Danger, DsColors.DangerLight)
    is LicenseState.Expired -> Look("Expiré", "Consultation seule. Sauvegarde et export restent disponibles.", DsColors.Danger, DsColors.DangerLight)
    is LicenseState.ClockBehind -> Look("Date incorrecte", "La date du téléphone est en retard. Activez « Date et heure automatiques ».", DsColors.Danger, DsColors.DangerLight)
    is LicenseState.NotYetValid -> Look("Pas encore valable", "L'abonnement commence plus tard.", DsColors.Warning, DsColors.WarningLight)
    is LicenseState.CheckInRequired -> Look("Vérification nécessaire", "Connectez ce téléphone à Internet, puis touchez « Vérifier maintenant ».", DsColors.Warning, DsColors.WarningLight)
    LicenseState.Revoked -> Look("Appareil libéré", "Cet appareil n'est plus associé à l'abonnement. Activez-le pour le reprendre.", DsColors.TextSecondary, DsColors.SurfaceSunken)
    is LicenseState.NoLicense -> Look("Non activé", "Activez cet appareil pour commencer.", DsColors.TextSecondary, DsColors.SurfaceSunken)
    LicenseState.Unchecked -> Look("…", "Lecture de la licence…", DsColors.TextSecondary, DsColors.SurfaceSunken)
}

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)
private val MOMENT = DateTimeFormatter.ofPattern("d MMM yyyy 'à' HH:mm", Locale.FRENCH)

private fun day(epochMillis: Long): String = DAY.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private fun moment(epochMillis: Long): String = MOMENT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private fun holders(holders: List<SeatHolder>): String =
    holders.joinToString(" et ") { holder ->
        val seen = holder.lastSeenAt?.let { " (vu le ${moment(it.toEpochMilli())})" } ?: ""
        "« ${holder.model ?: "un autre téléphone"} »$seen"
    }.ifBlank { "un autre téléphone" }

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(DsShapes.large).background(DsColors.SurfaceMuted).padding(DsSpacing.lg),
        content  = content
    )
}

@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector, background: Color, tint: Color) {
    Box(modifier = Modifier.size(40.dp).clip(DsShapes.medium).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.Medium, color = DsColors.TextPrimary)
    }
}

@Composable
private fun Dialog(
    title: String,
    text: String,
    confirm: Pair<String, () -> Unit>,
    dismiss: Pair<String, () -> Unit>? = null,
    danger: Boolean = false,
    /** The server is working on the answer: the buttons wait, the confirm one spins. */
    busy: Boolean = false,
    extra: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) (dismiss ?: confirm).second() },
        title = { Text(title) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                Text(text)
                extra?.invoke()
            }
        },
        confirmButton = {
            TextButton(onClick = confirm.second, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text(confirm.first, color = if (danger) DsColors.Danger else DsColors.Primary)
                }
            }
        },
        dismissButton = dismiss?.let { { TextButton(onClick = it.second, enabled = !busy) { Text(it.first) } } },
        containerColor    = DsColors.Surface,
        titleContentColor = DsColors.TextPrimary,
        textContentColor  = DsColors.TextSecondary
    )
}
