package com.distrigo.app.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import com.distrigo.app.ui.designsystem.DsTextSize
import com.distrigo.app.ui.designsystem.DsTopAppBar
import com.distrigo.app.ui.designsystem.DsTopBarLeading
import com.distrigo.app.ui.designsystem.dsTextFieldColors
import kotlinx.coroutines.launch

private const val MIN_PASSWORD = 8

@Composable
fun SignInScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    onSignUp: () -> Unit,
    onSignedIn: () -> Unit,
) {
    BackHandler { onBack() }
    val state by viewModel.state.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var forgotten by remember { mutableStateOf(false) }
    val busy = state.work != null
    val submit = {
        if (email.isNotBlank() && password.isNotEmpty()) viewModel.signIn(email, password, onSignedIn)
    }

    AccountForm(title = "Connexion", onBack = onBack) {
        Text(
            "Connectez-vous à votre compte DistriGo pour activer l'abonnement sur ce téléphone.",
            fontSize = DsTextSize.body,
            color    = DsColors.TextSecondary
        )
        AccountField(email, { email = it }, "E-mail", KeyboardType.Email, ImeAction.Next)
        PasswordField(password, { password = it }, "Mot de passe", ImeAction.Done, onDone = submit)
        state.message?.let { MessageLine(it) }
        PrimaryButton("Se connecter", busy = busy, enabled = email.isNotBlank() && password.isNotEmpty(), onClick = submit)
        TextButton(onClick = { forgotten = true }, modifier = Modifier.align(Alignment.End)) {
            Text("Mot de passe oublié ?", fontSize = DsTextSize.bodySmall, color = DsColors.Primary)
        }
        if (GoogleSignIn.available) {
            OrDivider()
            GoogleButton(viewModel, enabled = !busy, onSignedIn = onSignedIn)
        }
        SwitchLine("Pas encore de compte ?", "Créer un compte", onSignUp)
    }

    if (forgotten) {
        AlertDialog(
            onDismissRequest = { forgotten = false },
            title = { Text("Mot de passe oublié") },
            text  = {
                Text(
                    "Pendant la version d'essai, demandez à l'équipe DistriGo de réinitialiser votre mot de passe. " +
                        "Vos données restent sur ce téléphone : rien n'est perdu."
                )
            },
            confirmButton     = { TextButton(onClick = { forgotten = false }) { Text("Compris") } },
            containerColor    = DsColors.Surface,
            titleContentColor = DsColors.TextPrimary,
            textContentColor  = DsColors.TextSecondary
        )
    }
}

@Composable
fun SignUpScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSignedIn: () -> Unit,
) {
    BackHandler { onBack() }
    val state by viewModel.state.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val busy = state.work != null

    val nameError = if (tried && name.isBlank()) "Indiquez votre nom" else null
    val emailError = if (tried && !email.trim().matches(Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) "Adresse e-mail invalide" else null
    val passwordError = if (tried && password.length < MIN_PASSWORD) "$MIN_PASSWORD caractères au moins" else null
    val confirmationError = if (tried && confirmation != password) "Les deux mots de passe diffèrent" else null
    val submit = {
        tried = true
        val valid = name.isNotBlank() && email.trim().matches(Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) &&
            password.length >= MIN_PASSWORD && confirmation == password
        if (valid) viewModel.signUp(name, email, password, onSignedIn)
    }

    AccountForm(title = "Créer un compte", onBack = onBack) {
        Text(
            "Le compte sert à l'abonnement. Vos clients, ventes et stock restent sur ce téléphone.",
            fontSize = DsTextSize.body,
            color    = DsColors.TextSecondary
        )
        AccountField(name, { name = it }, "Nom et prénom", KeyboardType.Text, ImeAction.Next, nameError)
        AccountField(email, { email = it }, "E-mail", KeyboardType.Email, ImeAction.Next, emailError)
        PasswordField(password, { password = it }, "Mot de passe ($MIN_PASSWORD caractères au moins)", ImeAction.Next, passwordError)
        PasswordField(confirmation, { confirmation = it }, "Confirmer le mot de passe", ImeAction.Done, confirmationError, onDone = submit)
        state.message?.let { MessageLine(it) }
        PrimaryButton("Créer mon compte", busy = busy, enabled = true, onClick = submit)
        if (GoogleSignIn.available) {
            OrDivider()
            GoogleButton(viewModel, enabled = !busy, onSignedIn = onSignedIn)
        }
        SwitchLine("Déjà un compte ?", "Se connecter", onSignIn)
    }
}

// ── Shared pieces ──

@Composable
internal fun AccountForm(title: String, onBack: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(DsColors.Surface)) {
        DsTopAppBar(title = title, leading = DsTopBarLeading.Back(onBack))
        Column(
            modifier            = Modifier.verticalScroll(rememberScrollState()).padding(DsSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DsSpacing.md),
            content             = content
        )
    }
}

@Composable
private fun AccountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    error: String? = null,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        label           = { Text(label) },
        singleLine      = true,
        isError         = error != null,
        supportingText  = error?.let { { Text(it) } },
        shape           = DsShapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
        colors          = dsTextFieldColors(),
        modifier        = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    error: String? = null,
    onDone: () -> Unit = {},
) {
    val focus = LocalFocusManager.current
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value                = value,
        onValueChange        = onValueChange,
        label                = { Text(label) },
        singleLine           = true,
        isError              = error != null,
        supportingText       = error?.let { { Text(it) } },
        shape                = DsShapes.medium,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions      = KeyboardActions(
            onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
            onDone = { focus.clearFocus(); onDone() },
        ),
        trailingIcon         = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Masquer le mot de passe" else "Afficher le mot de passe"
                )
            }
        },
        colors               = dsTextFieldColors(),
        modifier             = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun PrimaryButton(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick  = onClick,
        enabled  = enabled && !busy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = DsShapes.medium,
        colors   = ButtonDefaults.buttonColors(containerColor = DsColors.Primary)
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(label, fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun MessageLine(message: AccountMessage) {
    Text(
        message.text,
        fontSize = DsTextSize.bodySmall,
        color    = if (message.isError) DsColors.Danger else DsColors.Success,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (message.isError) DsColors.DangerLight else DsColors.SuccessLight, DsShapes.small)
            .padding(DsSpacing.md)
    )
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = DsColors.Border)
        Text("ou", fontSize = DsTextSize.bodySmall, color = DsColors.TextTertiary, modifier = Modifier.padding(horizontal = DsSpacing.md))
        HorizontalDivider(modifier = Modifier.weight(1f), color = DsColors.Border)
    }
}

@Composable
private fun GoogleButton(viewModel: AccountViewModel, enabled: Boolean, onSignedIn: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val scope = rememberCoroutineScope()
    OutlinedButton(
        onClick  = {
            if (activity != null) {
                scope.launch {
                    when (val result = GoogleSignIn.request(activity)) {
                        is GoogleSignIn.Result.Token -> viewModel.signInWithGoogle(result.idToken, result.rawNonce, onSignedIn)
                        is GoogleSignIn.Result.Failed -> viewModel.showError(result.message)
                        GoogleSignIn.Result.Cancelled -> Unit
                    }
                }
            }
        },
        enabled  = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = DsShapes.medium
    ) {
        Text("G", fontSize = DsTextSize.title, fontWeight = FontWeight.Bold, color = DsColors.Primary)
        Spacer(Modifier.size(DsSpacing.sm))
        Text("Continuer avec Google", fontSize = DsTextSize.bodyLarge, fontWeight = FontWeight.SemiBold, color = DsColors.TextPrimary)
    }
}

@Composable
private fun SwitchLine(question: String, action: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(question, fontSize = DsTextSize.bodySmall, color = DsColors.TextSecondary)
        TextButton(onClick = onClick) {
            Text(action, fontSize = DsTextSize.bodySmall, fontWeight = FontWeight.SemiBold, color = DsColors.Primary)
        }
    }
}
