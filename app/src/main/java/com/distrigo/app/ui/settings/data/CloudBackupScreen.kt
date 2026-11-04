package com.distrigo.app.ui.settings.data

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.distrigo.app.R
import com.distrigo.app.data.backup.cloud.BackupPassword
import com.distrigo.app.data.backup.cloud.CloudBackupProblem
import com.distrigo.app.data.backup.cloud.CloudBackupState
import com.distrigo.app.ui.common.ltrIsolate
import com.distrigo.app.ui.designsystem.BrandCard
import com.distrigo.app.ui.designsystem.BrandCardBody
import com.distrigo.app.ui.designsystem.BrandCardHeader
import com.distrigo.app.ui.designsystem.BrandCodeText
import com.distrigo.app.ui.designsystem.BrandDialog
import com.distrigo.app.ui.designsystem.BrandDialogText
import com.distrigo.app.ui.designsystem.BrandInfoRow
import com.distrigo.app.ui.designsystem.BrandActionRow
import com.distrigo.app.ui.designsystem.BrandMessage
import com.distrigo.app.ui.designsystem.BrandNotice
import com.distrigo.app.ui.designsystem.BrandRowDivider
import com.distrigo.app.ui.designsystem.BrandScreen
import com.distrigo.app.ui.designsystem.BrandSize
import com.distrigo.app.ui.designsystem.BrandWideButton
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.onboarding.BrandPasswordField

/**
 * Paramètres › Sauvegarde › Sauvegarde Google Drive, in the Compte design language. [onRestoreFile] receives the
 * downloaded backup for the Sauvegarde screen's own restore (preview, warnings, restart).
 */
@Composable
fun CloudBackupScreen(
    onBack: () -> Unit,
    onRestoreFile: (Uri) -> Unit,
    viewModel: CloudBackupViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsState()
    val step by viewModel.step.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val fieldError by viewModel.fieldError.collectAsState()
    val message by viewModel.message.collectAsState()
    val job by viewModel.job.collectAsState()
    val context = LocalContext.current

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onConsentResult(result.resultCode, result.data)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        viewModel.consent.collect { consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
    }
    LaunchedEffect(Unit) { viewModel.restoreFile.collect(onRestoreFile) }
    LaunchedEffect(Unit) {
        viewModel.askNotifications.collect {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) { viewModel.refresh() }

    BackHandler(enabled = step is CloudStep.Restoring) { }

    CloudBackupContent(
        status = status, step = step, busy = busy, fieldError = fieldError, message = message, job = job,
        actions = CloudActions(
            onBack = onBack,
            startLink = viewModel::startLink,
            startRestore = viewModel::startRestore,
            reconnect = viewModel::reconnect,
            askRelock = viewModel::askRelock,
            backupNow = viewModel::backupNow,
            setAuto = viewModel::setAuto,
            setMobileData = viewModel::setMobileData,
            setPhotos = viewModel::setPhotos,
            askChangePassword = viewModel::askChangePassword,
            askChangeAccount = viewModel::askChangeAccount,
            askUnlink = viewModel::askUnlink,
            submitNewPassword = viewModel::submitNewPassword,
            recoveryCodeNoted = viewModel::recoveryCodeNoted,
            shareCode = { code ->
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, code), null))
            },
            submitSecret = viewModel::submitSecret,
            changePassword = viewModel::changePassword,
            unlink = viewModel::unlink,
            changeAccount = viewModel::changeAccount,
            restoreBackup = viewModel::restoreBackup,
            dismissStep = viewModel::dismissStep,
        ),
    )
}

/** What the screen can ask for; the screen itself only draws. */
class CloudActions(
    val onBack: () -> Unit = {},
    val startLink: () -> Unit = {},
    val startRestore: () -> Unit = {},
    val reconnect: () -> Unit = {},
    val askRelock: () -> Unit = {},
    val backupNow: () -> Unit = {},
    val setAuto: (Boolean) -> Unit = {},
    val setMobileData: (Boolean) -> Unit = {},
    val setPhotos: (Boolean) -> Unit = {},
    val askChangePassword: () -> Unit = {},
    val askChangeAccount: () -> Unit = {},
    val askUnlink: () -> Unit = {},
    val submitNewPassword: (String, String) -> Unit = { _, _ -> },
    val recoveryCodeNoted: () -> Unit = {},
    val shareCode: (String) -> Unit = {},
    val submitSecret: (String) -> Unit = {},
    val changePassword: (String, String, String) -> Unit = { _, _, _ -> },
    val unlink: () -> Unit = {},
    val changeAccount: () -> Unit = {},
    val restoreBackup: (CloudBackupChoice) -> Unit = {},
    val dismissStep: () -> Unit = {},
)

/** The screen for a given state: what [CloudBackupScreen] shows, and what the screenshot test draws in both languages. */
@Composable
fun CloudBackupContent(
    status: CloudStatus,
    step: CloudStep?,
    busy: Boolean,
    fieldError: String?,
    message: CloudMessage?,
    job: CloudJob?,
    actions: CloudActions,
) {
    BrandScreen(title = stringResource(R.string.cloud_title), onBack = { if (step !is CloudStep.Restoring) actions.onBack() }) {
        message?.let { BrandMessage(it.text, it.isError) }
        when {
            !status.loaded -> Unit
            !status.available -> UnavailableCard()
            status.state.linked -> LinkedCards(status.state, job, busy, actions)
            else -> NotLinkedCard(busy, actions)
        }
    }

    when (val current = step) {
        is CloudStep.CreatePassword -> CreatePasswordDialog(current.account, busy, fieldError, actions.submitNewPassword, actions.dismissStep)
        is CloudStep.RecoveryCode -> RecoveryCodeDialog(current.code, onDone = actions.recoveryCodeNoted, onShare = actions.shareCode)
        is CloudStep.EnterSecret -> SecretDialog(current.purpose, busy, fieldError, actions.submitSecret, actions.dismissStep)
        CloudStep.ChangePassword -> ChangePasswordDialog(busy, fieldError, actions.changePassword, actions.dismissStep)
        CloudStep.ConfirmUnlink -> BrandDialog(
            title   = stringResource(R.string.cloud_unlink_title),
            text    = stringResource(R.string.cloud_unlink_text, status.state.account.orEmpty()),
            confirm = stringResource(R.string.cloud_unlink) to actions.unlink,
            dismiss = stringResource(R.string.common_action_cancel) to actions.dismissStep,
            danger  = true,
            busy    = busy,
        )
        CloudStep.ConfirmChangeAccount -> BrandDialog(
            title   = stringResource(R.string.cloud_change_account_title),
            text    = stringResource(R.string.cloud_change_account_text, status.state.account.orEmpty()),
            confirm = stringResource(R.string.cloud_change_account) to actions.changeAccount,
            dismiss = stringResource(R.string.common_action_cancel) to actions.dismissStep,
            busy    = busy,
        )
        is CloudStep.ChooseBackup -> ChooseBackupDialog(current.choices, actions.restoreBackup, actions.dismissStep)
        is CloudStep.Restoring -> BrandDialog(
            title   = stringResource(R.string.cloud_restoring_title),
            text    = current.label,
            confirm = stringResource(R.string.common_action_ok) to {},
            busy    = true,
        )
        null -> Unit
    }
}

// ── Cards ──

@Composable
private fun UnavailableCard() {
    BrandCard {
        BrandCardHeader(Icons.Outlined.CloudOff, stringResource(R.string.cloud_title))
        BrandCardBody { BrandNotice(stringResource(R.string.cloud_unavailable), DsColors.Warning, DsColors.WarningLight) }
    }
}

@Composable
private fun NotLinkedCard(busy: Boolean, actions: CloudActions) {
    BrandCard {
        BrandCardHeader(Icons.Outlined.CloudUpload, stringResource(R.string.cloud_title))
        BrandCardBody {
            BrandDialogText(stringResource(R.string.cloud_intro))
            BrandWideButton(stringResource(R.string.cloud_link), Icons.Outlined.CloudSync, filled = true, enabled = !busy, busy = busy, onClick = actions.startLink)
            Text(stringResource(R.string.cloud_optional), fontFamily = DsBrand.Font, fontSize = BrandSize.Small, lineHeight = BrandSize.SmallLine, color = DsBrand.Slate)
            BrandWideButton(stringResource(R.string.cloud_restore_from), Icons.Outlined.Restore, filled = false, enabled = !busy, onClick = actions.startRestore)
        }
    }
}

@Composable
private fun LinkedCards(state: CloudBackupState, job: CloudJob?, busy: Boolean, actions: CloudActions) {
    BrandCard {
        BrandCardHeader(Icons.Outlined.CloudDone, stringResource(R.string.cloud_title))
        BrandInfoRow(Icons.Outlined.AccountCircle, stringResource(R.string.cloud_account), state.account?.let(::ltrIsolate))
        BrandRowDivider()
        val last = state.lastAt?.let { at ->
            val size = state.lastSize?.let(DataBackupFormatting::size)
            if (size != null) stringResource(R.string.cloud_last_value, DataBackupFormatting.dateTimeWithAge(at), size) else DataBackupFormatting.dateTimeWithAge(at)
        } ?: stringResource(R.string.cloud_none_yet)
        BrandInfoRow(Icons.Outlined.History, stringResource(R.string.cloud_last), last)
        BrandCardBody {
            state.lastProblem?.let { problem -> ProblemNotice(problem, busy, actions) }
            if (job != null) {
                JobRow(job)
            } else {
                BrandWideButton(stringResource(R.string.cloud_backup_now), Icons.Outlined.CloudUpload, filled = true, enabled = !busy, onClick = actions.backupNow)
            }
        }
    }

    BrandCard {
        BrandCardHeader(Icons.Outlined.Settings, stringResource(R.string.cloud_settings))
        SwitchRow(Icons.Outlined.Schedule, stringResource(R.string.cloud_auto), stringResource(R.string.cloud_auto_sub), state.enabled, actions.setAuto)
        BrandRowDivider()
        SwitchRow(Icons.Outlined.SignalCellularAlt, stringResource(R.string.cloud_mobile), stringResource(R.string.cloud_mobile_sub), state.allowMobileData, actions.setMobileData)
        BrandRowDivider()
        SwitchRow(Icons.Outlined.Photo, stringResource(R.string.cloud_photos), stringResource(R.string.cloud_photos_sub), state.includePhotos, actions.setPhotos)
    }

    BrandCard {
        BrandCardHeader(Icons.Outlined.ManageAccounts, stringResource(R.string.cloud_manage))
        BrandActionRow(Icons.Outlined.Restore, stringResource(R.string.cloud_restore), enabled = !busy && job == null, onClick = actions.startRestore)
        BrandRowDivider()
        BrandActionRow(Icons.Outlined.Password, stringResource(R.string.cloud_change_password), enabled = !busy, onClick = actions.askChangePassword)
        BrandRowDivider()
        BrandActionRow(Icons.Outlined.AccountCircle, stringResource(R.string.cloud_change_account), enabled = !busy && job == null, onClick = actions.askChangeAccount)
        BrandRowDivider()
        BrandActionRow(Icons.Outlined.LinkOff, stringResource(R.string.cloud_unlink), enabled = !busy && job == null, onClick = actions.askUnlink)
    }
}

/** The last run's problem, with the one thing that fixes it when there is one. */
@Composable
private fun ProblemNotice(problem: CloudBackupProblem, busy: Boolean, actions: CloudActions) {
    BrandNotice(problem.message, DsColors.Warning, DsColors.WarningLight)
    when (problem) {
        CloudBackupProblem.NEEDS_CONSENT ->
            BrandWideButton(stringResource(R.string.cloud_reconnect), Icons.Outlined.CloudSync, filled = false, enabled = !busy, onClick = actions.reconnect)
        CloudBackupProblem.NEEDS_PASSWORD ->
            BrandWideButton(stringResource(R.string.cloud_enter_password), Icons.Outlined.Key, filled = false, enabled = !busy, onClick = actions.askRelock)
        CloudBackupProblem.OTHER_BUSINESS ->
            BrandWideButton(stringResource(R.string.cloud_unlink), Icons.Outlined.LinkOff, filled = false, enabled = !busy, danger = true, onClick = actions.askUnlink)
        CloudBackupProblem.QUOTA_EXCEEDED ->
            Text(stringResource(R.string.cloud_quota_hint), fontFamily = DsBrand.Font, fontSize = BrandSize.Small, lineHeight = BrandSize.SmallLine, color = DsBrand.Slate)
        else -> Unit
    }
}

@Composable
private fun JobRow(job: CloudJob) {
    Row(
        modifier          = Modifier.fillMaxWidth().background(DsBrand.Mist, DsShapes.medium).padding(DsSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (job.waiting) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = DsBrand.Slate, modifier = Modifier.size(BrandSize.RowIcon))
        } else {
            CircularProgressIndicator(color = DsBrand.Blue, strokeWidth = 2.dp, modifier = Modifier.size(BrandSize.RowIcon))
        }
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(job.label, fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = BrandSize.Body, color = DsBrand.Ink)
            Text(stringResource(R.string.cloud_can_leave), fontFamily = DsBrand.Font, fontSize = BrandSize.Small, color = DsBrand.Slate)
        }
    }
}

/** A setting: its icon, label and what it means, the switch at the end; the whole row toggles. */
@Composable
private fun SwitchRow(icon: ImageVector, label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(start = DsSpacing.lg, end = DsSpacing.md, top = DsSpacing.sm, bottom = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = DsBrand.Ink, modifier = Modifier.size(BrandSize.RowIcon))
        Spacer(Modifier.width(DsSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = BrandSize.Body, color = DsBrand.Ink)
            Text(detail, fontFamily = DsBrand.Font, fontSize = BrandSize.Small, lineHeight = BrandSize.SmallLine, color = DsBrand.Slate)
        }
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = DsBrand.Blue))
    }
}

// ── Dialogs ──

@Composable
private fun CreatePasswordDialog(account: String, busy: Boolean, error: String?, onCreate: (String, String) -> Unit, onCancel: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var again by rememberSaveable { mutableStateOf("") }
    BrandDialog(
        title   = stringResource(R.string.cloud_create_title),
        text    = stringResource(R.string.cloud_create_text, account),
        confirm = stringResource(R.string.cloud_create) to { onCreate(password, again) },
        dismiss = stringResource(R.string.common_action_cancel) to onCancel,
        busy    = busy,
    ) {
        BrandPasswordField(password, { password = it }, stringResource(R.string.cloud_password), stringResource(R.string.cloud_password_hint, BackupPassword.MIN_LENGTH))
        BrandPasswordField(again, { again = it }, stringResource(R.string.cloud_password_again), stringResource(R.string.cloud_password_hint, BackupPassword.MIN_LENGTH), error = error, onDone = { onCreate(password, again) })
    }
}

/** Shown once: the code, « Partager », and « J'ai noté le code » before going on. Not dismissed by a tap outside. */
@Composable
private fun RecoveryCodeDialog(code: String, onDone: () -> Unit, onShare: (String) -> Unit) {
    var noted by rememberSaveable { mutableStateOf(false) }
    BrandDialog(
        title   = stringResource(R.string.cloud_code_title),
        text    = stringResource(R.string.cloud_code_text),
        confirm = stringResource(R.string.cloud_continue) to { if (noted) onDone() },
    ) {
        Column(
            Modifier.fillMaxWidth().background(DsBrand.Mist, DsShapes.medium).padding(DsSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DsSpacing.xs)
        ) {
            // Three groups per line, always left to right
            code.split("-").chunked(3).forEach { BrandCodeText(ltrIsolate(it.joinToString("-")), fontSize = BrandSize.Code) }
        }
        BrandWideButton(stringResource(R.string.cloud_code_share), Icons.Outlined.Share, filled = false, enabled = true) { onShare(code) }
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { noted = !noted },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = noted, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = DsBrand.Blue))
            Spacer(Modifier.width(DsSpacing.sm))
            Text(stringResource(R.string.cloud_code_noted), fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = BrandSize.Body, color = DsBrand.Ink)
        }
    }
}

@Composable
private fun SecretDialog(purpose: SecretPurpose, busy: Boolean, error: String?, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var secret by rememberSaveable { mutableStateOf("") }
    val (title, text) = when (purpose) {
        SecretPurpose.RELOCK -> R.string.cloud_relock_title to R.string.cloud_relock_text
        else -> R.string.cloud_join_title to R.string.cloud_join_text
    }
    BrandDialog(
        title   = stringResource(title),
        text    = stringResource(text),
        confirm = stringResource(R.string.cloud_open) to { onSubmit(secret) },
        dismiss = stringResource(R.string.common_action_cancel) to onCancel,
        busy    = busy,
    ) {
        BrandPasswordField(secret, { secret = it }, stringResource(R.string.cloud_secret), stringResource(R.string.cloud_secret), error = error, onDone = { onSubmit(secret) })
    }
}

@Composable
private fun ChangePasswordDialog(busy: Boolean, error: String?, onSave: (String, String, String) -> Unit, onCancel: () -> Unit) {
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var again by rememberSaveable { mutableStateOf("") }
    BrandDialog(
        title   = stringResource(R.string.cloud_change_password),
        text    = stringResource(R.string.cloud_change_text),
        confirm = stringResource(R.string.cloud_save) to { onSave(current, new, again) },
        dismiss = stringResource(R.string.common_action_cancel) to onCancel,
        busy    = busy,
    ) {
        BrandPasswordField(current, { current = it }, stringResource(R.string.cloud_current_password), stringResource(R.string.cloud_current_password))
        BrandPasswordField(new, { new = it }, stringResource(R.string.cloud_new_password), stringResource(R.string.cloud_password_hint, BackupPassword.MIN_LENGTH))
        BrandPasswordField(again, { again = it }, stringResource(R.string.cloud_password_again), stringResource(R.string.cloud_password_hint, BackupPassword.MIN_LENGTH), error = error, onDone = { onSave(current, new, again) })
    }
}

@Composable
private fun ChooseBackupDialog(choices: List<CloudBackupChoice>, onChoose: (CloudBackupChoice) -> Unit, onCancel: () -> Unit) {
    BrandDialog(
        title   = stringResource(R.string.cloud_choose_title),
        text    = stringResource(R.string.cloud_choose_text),
        confirm = stringResource(R.string.common_action_cancel) to onCancel,
    ) {
        choices.forEach { choice ->
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .background(DsBrand.Mist, DsShapes.medium)
                    .clickable(enabled = choice.openable) { onChoose(choice) }
                    .padding(DsSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = if (choice.openable) DsBrand.Ink else DsBrand.Slate, modifier = Modifier.size(BrandSize.RowIcon))
                Spacer(Modifier.width(DsSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        DataBackupFormatting.dateTimeWithAge(choice.at),
                        fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = BrandSize.Body,
                        color = if (choice.openable) DsBrand.Ink else DsBrand.Slate
                    )
                    Text(
                        stringResource(
                            R.string.cloud_last_value,
                            stringResource(if (choice.fromThisPhone) R.string.cloud_this_phone else R.string.cloud_other_phone),
                            DataBackupFormatting.size(choice.entry.meta.plainSize),
                        ),
                        fontFamily = DsBrand.Font, fontSize = BrandSize.Small, color = DsBrand.Slate
                    )
                    if (!choice.openable) {
                        Text(stringResource(R.string.cloud_old_key), fontFamily = DsBrand.Font, fontSize = BrandSize.Small, color = DsColors.Warning)
                    }
                }
            }
        }
    }
}
