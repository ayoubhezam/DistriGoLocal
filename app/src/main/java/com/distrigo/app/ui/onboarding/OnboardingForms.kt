package com.distrigo.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.distrigo.app.R
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing

/** The same rule as the account's own forms (ui/account/SignInScreens.kt). */
internal const val MIN_PASSWORD = 8

private val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")

internal fun isEmail(text: String) = text.trim().matches(EMAIL)

/** An Algerian number: 0 then 8 or 9 digits, or +213 / 00213 then 9; spaces and dashes don't count. */
internal fun isPhone(text: String): Boolean {
    val trimmed = text.trim()
    val digits  = trimmed.filter { it.isDigit() }
    return when {
        trimmed.startsWith("+213") -> digits.length == 12
        trimmed.startsWith("00213") -> digits.length == 14
        else -> digits.startsWith("0") && digits.length in 9..10
    }
}

// Placeholders stay readable (4:1 on white) yet clearly lighter than what was typed
internal val Placeholder = DsBrand.Slate.copy(alpha = 0.85f)
private val ButtonHeight = 50.dp

/** The form screens' side margin, as on the feature pages. */
internal val FormGutter = DsSpacing.xl

/** Back on the left; for the sign-up steps, the step dots in the middle and « 1/2 » on the right. */
@Composable
internal fun FormTopBar(onBack: () -> Unit, step: Int? = null, stepCount: Int = 2) {
    Box(Modifier.fillMaxWidth().height(48.dp).padding(start = DsSpacing.xs, end = FormGutter)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DsBrand.Ink)
        }
        if (step != null) {
            PageDots(
                current  = step - 1,
                count    = stepCount,
                active   = DsBrand.Blue,
                inactive = DsBrand.Ink.copy(alpha = 0.18f),
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                "$step/$stepCount",
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                color      = DsBrand.Slate,
                modifier   = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
internal fun FormTitle(title: String, subtitle: String) {
    Text(title, fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 30.sp, color = DsBrand.Ink)
    Spacer(Modifier.height(DsSpacing.xs))
    Text(subtitle, fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, color = DsBrand.Slate)
}

/** The frame every field shares: white, rounded, blue while in use, red when wrong. */
internal fun Modifier.fieldFrame(active: Boolean, error: Boolean) = this
    .fillMaxWidth()
    .clip(DsShapes.medium)
    .background(Color.White)
    .border(
        width = if (active || error) 1.5.dp else 1.dp,
        color = when { error -> DsColors.Danger; active -> DsBrand.Blue; else -> DsBrand.CardBorder },
        shape = DsShapes.medium
    )
    .padding(horizontal = DsSpacing.md, vertical = DsSpacing.sm)

@Composable
internal fun FieldLabel(label: String) {
    Text(label, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, color = DsBrand.Ink)
}

@Composable
internal fun FieldError(error: String?) {
    if (error != null) {
        Text(
            error,
            fontFamily = DsBrand.Font,
            fontWeight = FontWeight.Medium,
            fontSize   = 12.sp,
            color      = DsColors.Danger,
            modifier   = Modifier.padding(start = DsSpacing.xs, top = DsSpacing.xs)
        )
    }
}

/**
 * A field as in the mockup: an icon, the label inside the frame above what is typed, the example as
 * placeholder. [onDone] makes the keyboard's action key submit instead of moving to the next field.
 */
@Composable
internal fun BrandTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    onDone: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(Modifier.fillMaxWidth()) {
        BasicTextField(
            value                = value,
            onValueChange        = onValueChange,
            singleLine           = true,
            textStyle            = TextStyle(fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DsBrand.Ink),
            cursorBrush          = SolidColor(DsBrand.Blue),
            keyboardOptions      = KeyboardOptions(
                capitalization = capitalization,
                keyboardType   = keyboardType,
                imeAction      = if (onDone != null) ImeAction.Done else ImeAction.Next
            ),
            keyboardActions      = if (onDone != null) KeyboardActions(onDone = { onDone() }) else KeyboardActions.Default,
            visualTransformation = visualTransformation,
            interactionSource    = interaction,
            modifier             = Modifier.fillMaxWidth(),
            decorationBox        = { field ->
                Row(Modifier.fieldFrame(active = focused, error = error != null), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = if (focused) DsBrand.Blue else DsBrand.Slate, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(DsSpacing.md))
                    Column(Modifier.weight(1f)) {
                        FieldLabel(label)
                        Spacer(Modifier.height(2.dp))
                        Box {
                            if (value.isEmpty()) {
                                Text(placeholder, fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Placeholder, maxLines = 1)
                            }
                            field()
                        }
                    }
                    trailing?.invoke()
                }
            }
        )
        FieldError(error)
    }
}

/** [BrandTextField] for a password, with the eye that shows it. */
@Composable
internal fun BrandPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    error: String? = null,
    onDone: (() -> Unit)? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    BrandTextField(
        value                = value,
        onValueChange        = onValueChange,
        label                = label,
        placeholder          = placeholder,
        icon                 = Icons.Outlined.Lock,
        error                = error,
        keyboardType         = KeyboardType.Password,
        onDone               = onDone,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing             = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Masquer le mot de passe" else "Afficher le mot de passe",
                    tint               = DsBrand.Slate
                )
            }
        }
    )
}

/** A field whose value is picked from [options], in the same frame as the typed ones. */
@Composable
internal fun BrandChoiceField(
    value: String,
    onSelect: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    options: List<String>,
    error: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
        Box {
            Row(
                modifier          = Modifier
                    .onSizeChanged { width = it.width }
                    .clip(DsShapes.medium)
                    .clickable(role = Role.DropdownList, onClickLabel = label) { open = true }
                    .fieldFrame(active = open, error = error != null),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = if (open) DsBrand.Blue else DsBrand.Slate, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(DsSpacing.md))
                Column(Modifier.weight(1f)) {
                    FieldLabel(label)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        value.ifEmpty { placeholder },
                        fontFamily = DsBrand.Font,
                        fontWeight = if (value.isEmpty()) FontWeight.Medium else FontWeight.SemiBold,
                        fontSize   = if (value.isEmpty()) 14.sp else 15.sp,
                        color      = if (value.isEmpty()) Placeholder else DsBrand.Ink,
                        maxLines   = 1
                    )
                }
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = DsBrand.Slate)
            }
            DropdownMenu(
                expanded         = open,
                onDismissRequest = { open = false },
                modifier         = Modifier
                    .width(with(LocalDensity.current) { width.toDp() })
                    .background(Color.White)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text    = {
                            Text(
                                option,
                                fontFamily = DsBrand.Font,
                                fontWeight = if (option == value) FontWeight.Bold else FontWeight.Medium,
                                fontSize   = 15.sp,
                                color      = if (option == value) DsBrand.Blue else DsBrand.Ink
                            )
                        },
                        onClick = { onSelect(option); open = false }
                    )
                }
            }
        }
        FieldError(error)
    }
}

/**
 * The navy main button with its arrow, as at the end of every form in the mockup. While [busy] it
 * shows a spinner in the same navy and takes no second tap.
 */
@Composable
internal fun BrandButton(label: String, onClick: () -> Unit, busy: Boolean = false) {
    Button(
        onClick  = onClick,
        enabled  = !busy,
        modifier = Modifier.fillMaxWidth().height(ButtonHeight),
        shape    = DsShapes.pill,
        colors   = ButtonDefaults.buttonColors(
            containerColor         = DsBrand.Ink,
            contentColor           = Color.White,
            disabledContainerColor = DsBrand.Ink,
            disabledContentColor   = Color.White
        )
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(label, fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(DsSpacing.sm))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** « Activer avec un code »: the way in without an e-mail, outlined like Google's but in the brand's blue. */
@Composable
internal fun CodeButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().height(ButtonHeight),
        shape    = DsShapes.pill,
        border   = BorderStroke(1.5.dp, DsBrand.Blue),
        colors   = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = DsBrand.Ink)
    ) {
        Icon(Icons.Outlined.Key, contentDescription = null, tint = DsBrand.Blue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(DsSpacing.md))
        Text("Activer avec un code", fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
internal fun OrDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = DsSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = DsBrand.CardBorder)
        Text(
            "ou",
            fontFamily = DsBrand.Font,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 13.sp,
            color      = DsBrand.Slate,
            modifier   = Modifier.padding(horizontal = DsSpacing.md)
        )
        HorizontalDivider(Modifier.weight(1f), color = DsBrand.CardBorder)
    }
}

/** Google's own "G" (sign-in branding guidelines), on a white outlined button. */
@Composable
internal fun GoogleButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().height(ButtonHeight),
        shape    = DsShapes.pill,
        border   = BorderStroke(1.dp, DsBrand.CardBorder),
        colors   = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = DsBrand.Ink)
    ) {
        Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(DsSpacing.md))
        Text("Se connecter avec Google", fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
