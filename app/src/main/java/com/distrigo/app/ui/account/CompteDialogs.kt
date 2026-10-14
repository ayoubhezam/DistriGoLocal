package com.distrigo.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.license.SeatHolder
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.onboarding.ACTIVITIES
import com.distrigo.app.ui.onboarding.BrandPasswordField
import com.distrigo.app.ui.onboarding.BrandTextField
import com.distrigo.app.ui.onboarding.CodeField
import com.distrigo.app.ui.onboarding.FormError
import com.distrigo.app.ui.onboarding.MIN_PASSWORD
import com.distrigo.app.ui.onboarding.activityLabel
import com.distrigo.app.ui.onboarding.formatCode
import com.distrigo.app.ui.onboarding.normalizeCode
import com.distrigo.app.ui.onboarding.salesRepsLabel
import com.distrigo.app.ui.onboarding.SALES_REPS as SALES_REPS_LABELS

/** The questions and answers shown over Compte, in the onboarding's identity. */
@Composable
internal fun AccountDialogs(dialog: AccountDialog, state: AccountUiState, viewModel: AccountViewModel) {
    when (dialog) {
        is AccountDialog.BusinessName -> {
            var name by rememberSaveable { mutableStateOf(dialog.suggestion) }
            val activate = { if (name.isNotBlank()) viewModel.answerBusinessName(dialog.request, name) }
            BrandDialog(
                title   = "Votre entreprise",
                text    = "Son nom apparaît dans votre compte. L'essai gratuit de 30 jours commence maintenant.",
                confirm = "Activer" to activate,
                dismiss = "Annuler" to viewModel::dismissDialog,
            ) {
                BrandTextField(
                    value          = name,
                    onValueChange  = { name = it },
                    label          = "Nom de l'entreprise",
                    placeholder    = "Ex. : SARL ABC Distribution",
                    icon           = Icons.Outlined.Storefront,
                    capitalization = KeyboardCapitalization.Words,
                    onDone         = activate
                )
            }
        }
        is AccountDialog.Transfer -> BrandDialog(
            title   = "Abonnement utilisé ailleurs",
            text    = "Votre abonnement est actif sur ${holders(dialog.holders)}. Le transférer sur ce téléphone ? " +
                "L'autre téléphone passera en consultation seule à sa prochaine connexion. 3 transferts possibles par mois.",
            confirm = "Transférer ici" to { viewModel.confirmTransfer(dialog.request) },
            dismiss = "Annuler" to viewModel::dismissDialog,
        )
        is AccountDialog.TransferLimit -> BrandDialog(
            title   = "Limite de transferts atteinte",
            text    = "L'abonnement a déjà changé 3 fois de téléphone ce mois-ci." +
                (dialog.nextAt?.let { " Prochain transfert possible le ${day(it.toEpochMilli())}." } ?: ""),
            confirm = "Compris" to viewModel::dismissDialog,
        )
        is AccountDialog.Activated -> BrandDialog(
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
            BrandDialog(
                title   = "Saisir un code",
                text    = "Le code reçu de DistriGo ou de votre revendeur prolonge votre abonnement.",
                confirm = "Valider" to { viewModel.renewWithCode(code) },
                dismiss = "Annuler" to viewModel::dismissDialog,
                busy    = busy,
            ) {
                CodeField(
                    code     = code,
                    onChange = {
                        code = normalizeCode(it)
                        if (dialog.error != null) viewModel.ask(AccountDialog.EnterCode())
                    },
                    enabled  = !busy,
                    error    = dialog.error,
                    onDone   = { viewModel.renewWithCode(code) }
                )
            }
        }
        is AccountDialog.Renewed -> BrandDialog(
            title   = "Abonnement prolongé",
            text    = dialog.validTo?.let { "Merci ! Votre abonnement est valable jusqu'au ${day(it)}." }
                ?: "Merci ! Le code a été ajouté à votre abonnement.",
            confirm = "Continuer" to viewModel::dismissDialog,
        )
        is AccountDialog.AddEmail -> {
            var email by rememberSaveable { mutableStateOf("") }
            var password by rememberSaveable { mutableStateOf("") }
            val add = { viewModel.addEmail(email, password) }
            BrandDialog(
                title   = "Ajouter une adresse e-mail",
                text    = "Vous pourrez vous connecter avec elle sur n'importe quel téléphone. " +
                    "Votre entreprise, votre abonnement et votre code ne changent pas.",
                confirm = "Ajouter" to add,
                dismiss = "Annuler" to viewModel::dismissDialog,
                busy    = state.work == AccountWork.ADDING_EMAIL,
            ) {
                BrandTextField(
                    value         = email,
                    onValueChange = { email = it },
                    label         = "Adresse e-mail",
                    placeholder   = "exemple@email.com",
                    icon          = Icons.Outlined.Email,
                    keyboardType  = KeyboardType.Email
                )
                BrandPasswordField(
                    value         = password,
                    onValueChange = { password = it },
                    label         = "Mot de passe",
                    placeholder   = "$MIN_PASSWORD caractères au moins",
                    onDone        = add
                )
                dialog.error?.let { FormError(it) }
            }
        }
        is AccountDialog.EditProfile -> EditProfileDialog(dialog, state, viewModel)
        AccountDialog.ConfirmSignOut -> {
            val leaving = "Ce téléphone sera libéré de l'abonnement et passera en consultation seule. " +
                "Vos données restent sur le téléphone. Une connexion Internet est nécessaire."
            val withoutEmail = (state.account as? AccountState.SignedIn)?.session?.isAnonymous == true
            if (!withoutEmail) {
                BrandDialog(
                    title   = "Se déconnecter ?",
                    text    = leaving,
                    confirm = "Se déconnecter" to viewModel::signOut,
                    dismiss = "Annuler" to viewModel::dismissDialog,
                )
            } else {
                // No e-mail to sign in with: the code is the only way back
                val code = state.activationCode
                BrandDialog(
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
                            modifier         = Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsBrand.Mist).padding(DsSpacing.md),
                            contentAlignment = Alignment.Center
                        ) {
                            CodeText(formatCode(code))
                        }
                    } else {
                        TextButton(onClick = { viewModel.ask(AccountDialog.AddEmail()) }) {
                            Text("Ajouter une adresse e-mail", fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = CompteSize.Action, color = DsBrand.Blue)
                        }
                    }
                    DialogText(leaving)
                }
            }
        }
        AccountDialog.ConfirmDelete -> BrandDialog(
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

/** One row of « Informations de l'entreprise »: typed (name, phone) or picked from the sign-up's lists. */
@Composable
private fun EditProfileDialog(dialog: AccountDialog.EditProfile, state: AccountUiState, viewModel: AccountViewModel) {
    val field = dialog.field
    val profile = state.profile
    val busy = state.work == AccountWork.SAVING_PROFILE
    when (field) {
        ProfileField.NAME, ProfileField.PHONE -> {
            var value by rememberSaveable(field) {
                mutableStateOf(if (field == ProfileField.NAME) profile?.name.orEmpty() else profile?.contactPhone.orEmpty())
            }
            val save = { viewModel.saveProfile(field, value) }
            BrandDialog(
                title   = field.title,
                text    = if (field == ProfileField.PHONE) "Le numéro où l'équipe DistriGo peut vous joindre. Laissez vide pour le retirer." else null,
                confirm = "Enregistrer" to save,
                dismiss = "Annuler" to viewModel::dismissDialog,
                busy    = busy,
            ) {
                BrandTextField(
                    value          = value,
                    onValueChange  = { value = it },
                    label          = field.title,
                    placeholder    = if (field == ProfileField.NAME) "Ex. : SARL ABC Distribution" else "Ex. : 0550 12 34 56",
                    icon           = if (field == ProfileField.NAME) Icons.Outlined.Storefront else Icons.Outlined.Phone,
                    error          = dialog.error,
                    keyboardType   = if (field == ProfileField.PHONE) KeyboardType.Phone else KeyboardType.Text,
                    capitalization = if (field == ProfileField.NAME) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                    onDone         = save
                )
            }
        }
        ProfileField.ACTIVITY, ProfileField.SALES_REPS -> {
            val activity = field == ProfileField.ACTIVITY
            var choice by rememberSaveable(field) {
                mutableStateOf((if (activity) activityLabel(profile?.activity) else salesRepsLabel(profile?.salesReps)).orEmpty())
            }
            BrandDialog(
                title   = field.title,
                text    = if (activity) null else "Combien de commerciaux vendent pour vous ?",
                confirm = "Enregistrer" to { viewModel.saveProfile(field, choice) },
                dismiss = "Annuler" to viewModel::dismissDialog,
                busy    = busy,
            ) {
                ChoiceList(if (activity) ACTIVITIES else SALES_REPS_LABELS, choice, enabled = !busy) { choice = it }
                dialog.error?.let { FormError(it) }
            }
        }
    }
}

/** The options as rows, the chosen one in blue with its tick. */
@Composable
private fun ChoiceList(options: List<String>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
        options.forEach { option ->
            val chosen = option == selected
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .clip(DsShapes.medium)
                    .background(if (chosen) DsBrand.Blue.copy(alpha = 0.06f) else Color.White)
                    .border(if (chosen) 1.5.dp else 1.dp, if (chosen) DsBrand.Blue else DsBrand.CardBorder, DsShapes.medium)
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = DsSpacing.md, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    option,
                    fontFamily = DsBrand.Font,
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize   = CompteSize.Action,
                    color      = if (chosen) DsBrand.Blue else DsBrand.Ink,
                    modifier   = Modifier.weight(1f)
                )
                if (chosen) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = DsBrand.Blue, modifier = Modifier.size(CompteSize.ButtonIcon))
            }
        }
    }
}

@Composable
private fun DialogText(text: String) {
    Text(text, fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = CompteSize.Body, lineHeight = CompteSize.BodyLine, color = DsBrand.Slate)
}

/**
 * The onboarding's dialog: white, rounded, Plus Jakarta Sans. [busy]: the server is working on the answer, so the
 * buttons wait and the confirm one spins. What [extra] holds scrolls when the keyboard leaves it little room.
 */
@Composable
private fun BrandDialog(
    title: String,
    text: String?,
    confirm: Pair<String, () -> Unit>,
    dismiss: Pair<String, () -> Unit>? = null,
    danger: Boolean = false,
    busy: Boolean = false,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) (dismiss ?: confirm).second() },
        title            = { Text(title, fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = CompteSize.DialogTitle, color = DsBrand.Ink) },
        text             = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(DsSpacing.md)) {
                text?.let { DialogText(it) }
                extra?.invoke(this)
            }
        },
        confirmButton    = {
            TextButton(onClick = confirm.second, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(color = DsBrand.Blue, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text(confirm.first, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = CompteSize.Action, color = if (danger) DsColors.Danger else DsBrand.Blue)
                }
            }
        },
        dismissButton    = dismiss?.let {
            {
                TextButton(onClick = it.second, enabled = !busy) {
                    Text(it.first, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = CompteSize.Action, color = DsBrand.Slate)
                }
            }
        },
        shape            = DsShapes.large,
        containerColor   = Color.White
    )
}

private fun holders(holders: List<SeatHolder>): String =
    holders.joinToString(" et ") { holder ->
        val seen = holder.lastSeenAt?.let { " (vu le ${moment(it.toEpochMilli())})" } ?: ""
        "« ${holder.model ?: "un autre téléphone"} »$seen"
    }.ifBlank { "un autre téléphone" }
