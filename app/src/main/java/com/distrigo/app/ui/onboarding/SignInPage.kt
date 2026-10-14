package com.distrigo.app.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.R
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsSpacing

private val WaveHeight = 64.dp

/** Connexion, as in the mockup: the logo, e-mail and password, Google, and the way to sign up. */
@Composable
internal fun SignInPage(actions: OnboardingActions, onBack: () -> Unit, onCreateAccount: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    // Errors wait for the first try, so an untouched form isn't already red
    var tried by rememberSaveable { mutableStateOf(false) }
    val emailError = if (tried && !isEmail(email)) "Adresse e-mail invalide" else null
    val passwordError = if (tried && password.isEmpty()) "Saisissez votre mot de passe" else null
    val submit = {
        tried = true
        if (isEmail(email) && password.isNotEmpty()) actions.signIn(email.trim(), password)
    }

    Box(Modifier.fillMaxSize().background(DsBrand.Mist)) {
        BottomWave(Modifier.align(Alignment.BottomStart).navigationBarsPadding())
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // At least a screen tall, so the last line sits at the bottom; scrolls once the keyboard is up
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight)) {
                FormTopBar(onBack)
                Column(Modifier.padding(horizontal = FormGutter)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painterResource(R.drawable.brand_logo_on_light), contentDescription = "DistriGo", modifier = Modifier.width(100.dp))
                        Spacer(Modifier.height(DsSpacing.xs))
                        Text(
                            BRAND_TAGLINE,
                            fontFamily    = DsBrand.Font,
                            fontWeight    = FontWeight.Bold,
                            fontSize      = 9.sp,
                            letterSpacing = 1.4.sp,
                            color         = DsBrand.Ink
                        )
                    }
                    Spacer(Modifier.height(DsSpacing.lg))
                    FormTitle("Connexion", "Accédez à votre espace en toute sécurité")
                    Spacer(Modifier.height(DsSpacing.lg))
                    BrandTextField(
                        value         = email,
                        onValueChange = { email = it },
                        label         = "Adresse e-mail",
                        placeholder   = "exemple@domaine.com",
                        icon          = Icons.Outlined.Email,
                        error         = emailError,
                        keyboardType  = KeyboardType.Email
                    )
                    Spacer(Modifier.height(DsSpacing.sm + 2.dp))
                    BrandPasswordField(
                        value         = password,
                        onValueChange = { password = it },
                        label         = "Mot de passe",
                        placeholder   = "Votre mot de passe",
                        error         = passwordError,
                        onDone        = submit
                    )
                    TextButton(onClick = actions.forgotPassword, modifier = Modifier.align(Alignment.End)) {
                        Text(
                            "Mot de passe oublié ?",
                            fontFamily     = DsBrand.Font,
                            fontWeight     = FontWeight.SemiBold,
                            fontSize       = 13.sp,
                            color          = DsBrand.Blue,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                    BrandButton("Se connecter", onClick = submit)
                    actions.signInWithGoogle?.let { google ->
                        OrDivider()
                        GoogleButton(google)
                    }
                }
                Spacer(Modifier.weight(1f).heightIn(min = DsSpacing.lg))
                Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Text("Pas encore de compte ?", fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = DsBrand.Slate)
                    TextButton(onClick = onCreateAccount) {
                        Text("Créer un compte", fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DsBrand.Blue)
                    }
                }
                // Keeps the last line clear of the wave
                Spacer(Modifier.height(WaveHeight))
            }
        }
    }
}

/** The mockup's blue wave rising from the bottom-left corner. */
@Composable
private fun BottomWave(modifier: Modifier) {
    Canvas(modifier.fillMaxWidth(0.62f).height(WaveHeight)) {
        val w = size.width
        val h = size.height
        val wave = Path().apply {
            moveTo(0f, h * 0.15f)
            cubicTo(w * 0.30f, h * 0.05f, w * 0.55f, h * 0.95f, w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(wave, Brush.linearGradient(listOf(DsBrand.Blue, DsBrand.Ink), start = Offset(0f, 0f), end = Offset(w, h)))
    }
}
