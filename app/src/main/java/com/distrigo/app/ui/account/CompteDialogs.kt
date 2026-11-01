package com.distrigo.app.ui.account

import com.distrigo.app.data.license.licText
import com.distrigo.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Storefront
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
import com.distrigo.app.data.auth.AccountState
import com.distrigo.app.data.license.SeatHolder
import com.distrigo.app.ui.designsystem.BrandCodeText
import com.distrigo.app.ui.designsystem.BrandDialog
import com.distrigo.app.ui.designsystem.BrandDialogText
import com.distrigo.app.ui.designsystem.BrandSize
import com.distrigo.app.ui.designsystem.DsBrand
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
                title   = licText(R.string.acc_your_business),
                text    = licText(R.string.acc_business_text),
                confirm = licText(R.string.acc_activate) to activate,
                dismiss = licText(R.string.acc_cancel) to viewModel::dismissDialog,
            ) {
                BrandTextField(
                    value          = name,
                    onValueChange  = { name = it },
                    label          = licText(R.string.acc_business_name),
                    placeholder    = licText(R.string.acc_business_example),
                    icon           = Icons.Outlined.Storefront,
                    capitalization = KeyboardCapitalization.Words,
                    onDone         = activate
                )
            }
        }
        is AccountDialog.Transfer -> BrandDialog(
            title   = licText(R.string.acc_used_elsewhere),
            text    = licText(R.string.acc_transfer_text, holders(dialog.holders)),
            confirm = licText(R.string.acc_transfer_here) to { viewModel.confirmTransfer(dialog.request) },
            dismiss = licText(R.string.acc_cancel) to viewModel::dismissDialog,
        )
        is AccountDialog.TransferLimit -> BrandDialog(
            title   = licText(R.string.acc_transfer_limit),
            text    = dialog.nextAt?.let { licText(R.string.acc_transfer_limit_next, day(it.toEpochMilli())) } ?: licText(R.string.acc_transfer_limit_text),
            confirm = licText(R.string.acc_understood) to viewModel::dismissDialog,
        )
        is AccountDialog.Activated -> BrandDialog(
            title   = licText(R.string.acc_activated),
            text    = buildString {
                append(licText(if (dialog.trial == true) R.string.acc_trial_starts else R.string.acc_device_holds))
                if (dialog.reduced) {
                    append(" " + licText(R.string.acc_reduced))
                }
            },
            confirm = licText(R.string.acc_continue) to viewModel::dismissDialog,
        )
        is AccountDialog.EnterCode -> {
            var code by rememberSaveable { mutableStateOf("") }
            val busy = state.work == AccountWork.RENEWING
            BrandDialog(
                title   = licText(R.string.acc_enter_code),
                text    = licText(R.string.acc_enter_code_text),
                confirm = licText(R.string.acc_validate) to { viewModel.renewWithCode(code) },
                dismiss = licText(R.string.acc_cancel) to viewModel::dismissDialog,
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
            title   = licText(R.string.acc_extended_title),
            text    = dialog.validTo?.let { licText(R.string.acc_extended_until, day(it)) }
                ?: licText(R.string.acc_extended),
            confirm = licText(R.string.acc_continue) to viewModel::dismissDialog,
        )
        is AccountDialog.AddEmail -> {
            var email by rememberSaveable { mutableStateOf("") }
            var password by rememberSaveable { mutableStateOf("") }
            val add = { viewModel.addEmail(email, password) }
            BrandDialog(
                title   = licText(R.string.acc_add_email),
                text    = licText(R.string.acc_add_email_text),
                confirm = licText(R.string.acc_add) to add,
                dismiss = licText(R.string.acc_cancel) to viewModel::dismissDialog,
                busy    = state.work == AccountWork.ADDING_EMAIL,
            ) {
                BrandTextField(
                    value         = email,
                    onValueChange = { email = it },
                    label         = licText(R.string.acc_email),
                    placeholder   = "exemple@email.com",
                    icon          = Icons.Outlined.Email,
                    keyboardType  = KeyboardType.Email
                )
                BrandPasswordField(
                    value         = password,
                    onValueChange = { password = it },
                    label         = licText(R.string.acc_password),
                    placeholder   = licText(R.string.acc_password_hint, MIN_PASSWORD),
                    onDone        = add
                )
                dialog.error?.let { FormError(it) }
            }
        }
        is AccountDialog.EditProfile -> EditProfileDialog(dialog, state, viewModel)
        AccountDialog.ConfirmSignOut -> {
            val leaving = licText(R.string.acc_sign_out_text)
            val withoutEmail = (state.account as? AccountState.SignedIn)?.session?.isAnonymous == true
            if (!withoutEmail) {
                BrandDialog(
                    title   = licText(R.string.acc_sign_out_q),
                    text    = leaving,
                    confirm = licText(R.string.acc_sign_out) to viewModel::signOut,
                    dismiss = licText(R.string.acc_cancel) to viewModel::dismissDialog,
                )
            } else {
                // No e-mail to sign in with: the code is the only way back
                val code = state.activationCode
                BrandDialog(
                    title   = licText(R.string.acc_sign_out_q),
                    text    = if (code != null) {
                        licText(R.string.acc_sign_out_code)
                    } else {
                        licText(R.string.acc_sign_out_no_code)
                    },
                    confirm = licText(R.string.acc_sign_out) to viewModel::signOut,
                    dismiss = licText(R.string.acc_cancel) to viewModel::dismissDialog,
                    danger  = true,
                ) {
                    if (code != null) {
                        Box(
                            modifier         = Modifier.fillMaxWidth().clip(DsShapes.medium).background(DsBrand.Mist).padding(DsSpacing.md),
                            contentAlignment = Alignment.Center
                        ) {
                            BrandCodeText(formatCode(code))
                        }
                    } else {
                        TextButton(onClick = { viewModel.ask(AccountDialog.AddEmail()) }) {
                            Text(licText(R.string.acc_add_email), fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Action, color = DsBrand.Blue)
                        }
                    }
                    BrandDialogText(leaving)
                }
            }
        }
        AccountDialog.ConfirmDelete -> BrandDialog(
            title   = licText(R.string.acc_delete_q),
            text    = licText(if (state.activationCode != null) R.string.acc_delete_text_code else R.string.acc_delete_text),
            confirm = licText(R.string.acc_delete) to viewModel::deleteAccount,
            dismiss = licText(R.string.acc_cancel) to viewModel::dismissDialog,
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
                text    = if (field == ProfileField.PHONE) licText(R.string.acc_phone_text) else null,
                confirm = licText(R.string.acc_save) to save,
                dismiss = licText(R.string.acc_cancel) to viewModel::dismissDialog,
                busy    = busy,
            ) {
                BrandTextField(
                    value          = value,
                    onValueChange  = { value = it },
                    label          = field.title,
                    placeholder    = if (field == ProfileField.NAME) licText(R.string.acc_business_example) else licText(R.string.acc_phone_example),
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
                text    = if (activity) null else licText(R.string.acc_how_many_reps),
                confirm = licText(R.string.acc_save) to { viewModel.saveProfile(field, choice) },
                dismiss = licText(R.string.acc_cancel) to viewModel::dismissDialog,
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
                    fontSize   = BrandSize.Action,
                    color      = if (chosen) DsBrand.Blue else DsBrand.Ink,
                    modifier   = Modifier.weight(1f)
                )
                if (chosen) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = DsBrand.Blue, modifier = Modifier.size(BrandSize.ButtonIcon))
            }
        }
    }
}

private fun holders(holders: List<SeatHolder>): String =
    com.distrigo.app.data.lists.ListWords.and(holders.map { holder ->
        val model = holder.model ?: licText(R.string.acc_another_phone)
        holder.lastSeenAt?.let { licText(R.string.acc_holder_seen, model, moment(it.toEpochMilli())) } ?: licText(R.string.acc_holder, model)
    }).ifBlank { licText(R.string.acc_another_phone) }
