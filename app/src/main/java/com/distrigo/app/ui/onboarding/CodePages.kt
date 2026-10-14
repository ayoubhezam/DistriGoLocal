package com.distrigo.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing

/** An activation code's length, without its dashes. */
internal const val CODE_LENGTH = 12

// Crockford's base32: no I, L, O or U, so no character can be read as another
private const val CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

/**
 * What was typed, reduced to a code's characters: capitals, O read as 0, I and L as 1, spaces, dashes and
 * anything else dropped, at most [CODE_LENGTH]. A code read aloud or copied from paper still matches.
 */
internal fun normalizeCode(typed: String): String =
    typed.uppercase()
        .mapNotNull { c ->
            when (c) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> c.takeIf { it in CODE_ALPHABET }
            }
        }
        .joinToString("")
        .take(CODE_LENGTH)

/** XXXX-XXXX-XXXX, as printed on the code. */
internal fun formatCode(code: String): String = code.chunked(4).joinToString("-")

/** Luhn mod 32, as the server's private.code_check_char: the check character that follows [payload]. */
internal fun codeCheckChar(payload: String): Char {
    var factor = 2
    var sum = 0
    for (i in payload.indices.reversed()) {
        val addend = factor * CODE_ALPHABET.indexOf(payload[i])
        sum += addend / 32 + addend % 32
        factor = 3 - factor
    }
    return CODE_ALPHABET[(32 - sum % 32) % 32]
}

/**
 * Twelve characters of the alphabet, the last checking the first eleven: a typo shows here, before the
 * server is asked, so it never counts as a refused attempt (docs/activation_codes.md, decision 5).
 */
internal fun isWellFormedCode(code: String): Boolean =
    code.length == CODE_LENGTH && code.all { it in CODE_ALPHABET } && codeCheckChar(code.take(CODE_LENGTH - 1)) == code.last()

/** Shows the dashes without storing them, so typing and deleting never trip over one. */
private object CodeDashes : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val mapping = object : OffsetMapping {
            // A dash after every fourth character, never after the last one typed
            override fun originalToTransformed(offset: Int) = offset + (offset - 1).coerceAtLeast(0) / 4
            override fun transformedToOriginal(offset: Int) = offset - offset / 5
        }
        return TransformedText(AnnotatedString(formatCode(text.text)), mapping)
    }
}

/**
 * « Activer avec un code »: the code, then, when the code carries no company, its name and a phone number.
 * What the server answers arrives in [status]: busy, an error for the code, the company asked, or done.
 */
@Composable
internal fun CodePages(
    actions: OnboardingActions,
    status: OnboardingStatus,
    onBack: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var step by rememberSaveable { mutableIntStateOf(1) }
    // The server asked for the company: the code was right
    LaunchedEffect(status.needsBusiness) { if (status.needsBusiness) step = 2 }
    val backToCode = {
        actions.resetCode()
        step = 1
    }
    BackHandler(enabled = step == 2, onBack = backToCode)

    AnimatedContent(
        targetState    = step,
        transitionSpec = {
            val forward = targetState > initialState
            slideInHorizontally { if (forward) it else -it } togetherWith slideOutHorizontally { if (forward) -it else it }
        },
        label          = "code step"
    ) { shown ->
        Box(Modifier.fillMaxSize().background(DsBrand.Mist)) {
            BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight)) {
                    FormTopBar(onBack = if (shown == 2) backToCode else onBack)
                    Column(Modifier.padding(horizontal = FormGutter)) {
                        if (shown == 1) {
                            CodeStep(
                                code         = code,
                                onCodeChange = {
                                    code = normalizeCode(it)
                                    if (status.error != null) actions.resetCode()
                                },
                                status       = status,
                                onActivate   = { actions.redeemCode(code) }
                            )
                        } else {
                            CodeBusinessStep(code = code, status = status, actions = actions)
                        }
                    }
                    if (shown == 1) {
                        Spacer(Modifier.weight(1f).heightIn(min = DsSpacing.lg))
                        Row(Modifier.align(Alignment.CenterHorizontally).padding(bottom = DsSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Text("Pas de code ?", fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = DsBrand.Slate)
                            TextButton(onClick = onCreateAccount) {
                                Text("Créer un compte", fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DsBrand.Blue)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeStep(code: String, onCodeChange: (String) -> Unit, status: OnboardingStatus, onActivate: () -> Unit) {
    // A short or mistyped code is caught here; whether it exists is the server's to say
    var tried by rememberSaveable { mutableStateOf(false) }
    val tooShort = tried && code.length < CODE_LENGTH
    val typo = tried && code.length == CODE_LENGTH && !isWellFormedCode(code)
    val submit = {
        tried = true
        if (isWellFormedCode(code) && !status.busy) onActivate()
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(DsSpacing.md))
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(DsBrand.Blue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Key, contentDescription = null, tint = DsBrand.Blue, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(DsSpacing.lg))
    }
    FormTitle("Activer avec un code", "Saisissez le code reçu de DistriGo ou de votre revendeur.")
    if (status.codeRequired) {
        Spacer(Modifier.height(DsSpacing.md))
        Notice("L'essai gratuit a déjà été utilisé sur ce téléphone : activez DistriGo avec un code.")
    }
    Spacer(Modifier.height(DsSpacing.xl))
    CodeField(
        code     = code,
        onChange = onCodeChange,
        enabled  = !status.busy,
        error    = when {
            status.error != null -> status.error
            tooShort -> "Le code contient $CODE_LENGTH caractères"
            typo -> "Ce code contient une faute de frappe. Vérifiez chaque caractère."
            else -> null
        },
        onDone   = submit
    )
    Spacer(Modifier.height(DsSpacing.xl))
    BrandButton("Activer", onClick = submit, busy = status.busy)
    Spacer(Modifier.height(DsSpacing.md))
    Text(
        "Le code active DistriGo sur ce téléphone. Gardez-le : il sert aussi à réactiver l'application sur un nouveau téléphone.",
        fontFamily = DsBrand.Font,
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 17.sp,
        color      = DsBrand.Slate,
        textAlign  = TextAlign.Center,
        modifier   = Modifier.fillMaxWidth()
    )
}

/** The code, large and spaced like on paper, with how many characters are still missing. */
@Composable
private fun CodeField(code: String, onChange: (String) -> Unit, enabled: Boolean, error: String?, onDone: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val codeStyle = TextStyle(
        fontFamily    = DsBrand.Font,
        fontWeight    = FontWeight.ExtraBold,
        fontSize      = 22.sp,
        letterSpacing = 2.5.sp,
        textAlign     = TextAlign.Center,
        color         = DsBrand.Ink
    )
    Column(Modifier.fillMaxWidth()) {
        BasicTextField(
            value                = code,
            onValueChange        = onChange,
            enabled              = enabled,
            singleLine           = true,
            textStyle            = codeStyle,
            cursorBrush          = SolidColor(DsBrand.Blue),
            keyboardOptions      = KeyboardOptions(
                capitalization     = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType       = KeyboardType.Ascii,
                imeAction          = ImeAction.Done
            ),
            keyboardActions      = KeyboardActions(onDone = { onDone() }),
            visualTransformation = CodeDashes,
            interactionSource    = interaction,
            modifier             = Modifier.fillMaxWidth(),
            decorationBox        = { field ->
                Column(Modifier.fieldFrame(active = focused, error = error != null).padding(vertical = DsSpacing.xs)) {
                    FieldLabel("Code d'activation")
                    Spacer(Modifier.height(DsSpacing.sm))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (code.isEmpty()) Text("XXXX-XXXX-XXXX", style = codeStyle.copy(color = Placeholder.copy(alpha = 0.45f)))
                        field()
                    }
                }
            }
        )
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { FieldError(error) }
            Text(
                "${code.length} / $CODE_LENGTH",
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 12.sp,
                // Green only when complete and not refused: next to an error it would contradict it
                color      = if (code.length == CODE_LENGTH && error == null) DsColors.Success else DsBrand.Slate,
                modifier   = Modifier.padding(top = DsSpacing.xs, end = DsSpacing.xs)
            )
        }
    }
}

/**
 * « Votre entreprise »: a valid [code] that carries no company, or (code null) an account without a business,
 * signed in on this phone. Who it belongs to, in two fields, then the activation.
 */
@Composable
private fun CodeBusinessStep(code: String?, status: OnboardingStatus, actions: OnboardingActions) {
    var company by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val submit = {
        tried = true
        if (company.isNotBlank() && isPhone(phone) && !status.busy) actions.completeBusiness(company.trim(), phone.trim())
    }

    // The code was accepted: say so before asking anything
    if (code != null) Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(DsColors.Success.copy(alpha = 0.1f))
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DsColors.Success, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(DsSpacing.sm))
        Text(
            "Code ${formatCode(code)} accepté",
            fontFamily = DsBrand.Font,
            fontWeight = FontWeight.Bold,
            fontSize   = 13.sp,
            color      = DsBrand.Ink
        )
    }
    if (code != null) Spacer(Modifier.height(DsSpacing.lg))
    FormTitle(
        "Votre entreprise",
        if (code != null) "Une dernière étape : à qui appartient ce code ?" else "Une dernière étape avant d'activer DistriGo sur ce téléphone."
    )
    Spacer(Modifier.height(DsSpacing.xl))
    BrandTextField(
        value          = company,
        onValueChange  = { company = it },
        label          = "Nom de l'entreprise",
        placeholder    = "Ex. : SARL ABC Distribution",
        icon           = Icons.Outlined.Apartment,
        error          = if (tried && company.isBlank()) "Indiquez le nom de l'entreprise" else null,
        capitalization = KeyboardCapitalization.Words
    )
    Spacer(Modifier.height(DsSpacing.md))
    BrandTextField(
        value         = phone,
        onValueChange = { phone = it },
        label         = "Téléphone",
        placeholder   = "Ex. : 07 12 34 56 78",
        icon          = Icons.Outlined.Phone,
        error         = if (tried && !isPhone(phone)) "Numéro de téléphone invalide" else null,
        keyboardType  = KeyboardType.Phone,
        onDone        = submit
    )
    status.error?.let {
        Spacer(Modifier.height(DsSpacing.sm))
        FieldError(it)
    }
    Spacer(Modifier.height(DsSpacing.xxl))
    BrandButton("Activer DistriGo", onClick = submit, busy = status.busy)
    Spacer(Modifier.height(DsSpacing.lg))
}

/** « Votre entreprise » on its own: an account without a business, after Connexion. */
@Composable
internal fun BusinessPage(actions: OnboardingActions, status: OnboardingStatus, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(DsBrand.Mist)) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight)) {
                FormTopBar(onBack = onBack)
                Column(Modifier.padding(horizontal = FormGutter)) {
                    Spacer(Modifier.height(DsSpacing.sm))
                    CodeBusinessStep(code = null, status = status, actions = actions)
                }
            }
        }
    }
}

/** A note in the brand's orange, above a form: why the user is here. */
@Composable
private fun Notice(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(DsShapes.medium)
            .background(DsBrand.Orange.copy(alpha = 0.14f))
            .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = DsBrand.OrangeDeep, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(DsSpacing.sm))
        Text(text, fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, color = DsBrand.Ink)
    }
}
