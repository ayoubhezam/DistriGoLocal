package com.distrigo.app.ui.onboarding

import com.distrigo.app.data.license.licText
import com.distrigo.app.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.sp
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.designsystem.DsColors
import com.distrigo.app.ui.designsystem.DsSpacing

/** Everything the two sign-up steps collect. */
data class SignUpForm(
    val fullName: String,
    val phone: String,
    val email: String,
    val password: String,
    val company: String,
    val activity: String,
    val salesReps: String,
)

/** « Type d'activité »: the trades DistriGo's distributors and wholesalers work in. */
internal val ACTIVITIES: List<String> get() = ACTIVITY_CODES.keys.toList()

/** « Nombre de commerciaux », in ranges: the plan a business will need follows from it. */
internal val SALES_REPS: List<String> get() = SALES_REPS_CODES.keys.toList()

/** What the server stores for each label (public.businesses' checks). */
private val ACTIVITY_CODES: Map<String, String>
    get() = linkedMapOf(
        licText(R.string.onb_act_alimentation) to "alimentation",
        licText(R.string.onb_act_boissons) to "boissons",
        licText(R.string.onb_act_frais) to "frais",
        licText(R.string.onb_act_hygiene) to "hygiene",
        licText(R.string.onb_act_droguerie) to "droguerie",
        licText(R.string.onb_act_materiaux) to "materiaux",
        licText(R.string.onb_act_pharmacie) to "pharmacie",
        licText(R.string.onb_act_autre) to "autre",
    )
private val SALES_REPS_CODES: Map<String, String>
    get() = linkedMapOf("1" to "1", licText(R.string.onb_reps_2_5) to "2-5", licText(R.string.onb_reps_6_10) to "6-10", licText(R.string.onb_reps_10_plus) to "10+")

/** The server's code for a label; null for anything else. */
internal fun activityCode(label: String): String? = ACTIVITY_CODES[label]
internal fun salesRepsCode(label: String): String? = SALES_REPS_CODES[label]

/** The label for a code the server stores, for Compte; null for none or an unknown one. */
internal fun activityLabel(code: String?): String? = ACTIVITY_CODES.entries.firstOrNull { it.value == code }?.key
internal fun salesRepsLabel(code: String?): String? = SALES_REPS_CODES.entries.firstOrNull { it.value == code }?.key

/** The two steps' fields, kept together so going back to step 1 and forward again loses nothing. */
private class SignUpState(
    fullName: String = "", phone: String = "", email: String = "", password: String = "",
    company: String = "", activity: String = "", salesReps: String = SALES_REPS.first(), accepted: Boolean = false,
) {
    var fullName by mutableStateOf(fullName)
    var phone by mutableStateOf(phone)
    var email by mutableStateOf(email)
    var password by mutableStateOf(password)
    var company by mutableStateOf(company)
    var activity by mutableStateOf(activity)
    var salesReps by mutableStateOf(salesReps)
    var accepted by mutableStateOf(accepted)

    // Each step shows its errors from its first try on
    var triedPersonal by mutableStateOf(false)
    var triedBusiness by mutableStateOf(false)

    val personalValid get() = fullName.isNotBlank() && isPhone(phone) && isEmail(email) && password.length >= MIN_PASSWORD
    val businessValid get() = company.isNotBlank() && activity.isNotEmpty() && accepted

    fun form() = SignUpForm(fullName.trim(), phone.trim(), email.trim(), password, company.trim(), activity, salesReps)

    companion object {
        val Saver = listSaver<SignUpState, Any>(
            save    = { listOf(it.fullName, it.phone, it.email, it.password, it.company, it.activity, it.salesReps, it.accepted) },
            restore = {
                SignUpState(
                    it[0] as String, it[1] as String, it[2] as String, it[3] as String,
                    it[4] as String, it[5] as String, it[6] as String, it[7] as Boolean
                )
            }
        )
    }
}

/** Créer un compte: step 1, who you are; step 2, your business. Back from step 2 returns to step 1. */
@Composable
internal fun SignUpPages(actions: OnboardingActions, status: OnboardingStatus, onBack: () -> Unit) {
    val state = rememberSaveable(saver = SignUpState.Saver) { SignUpState() }
    var step by rememberSaveable { mutableIntStateOf(1) }
    BackHandler(enabled = step == 2) { step = 1 }

    AnimatedContent(
        targetState    = step,
        transitionSpec = {
            val forward = targetState > initialState
            // Start/End: forward comes in from the end edge, the left one in Arabic
            val towards = if (forward) AnimatedContentTransitionScope.SlideDirection.Start else AnimatedContentTransitionScope.SlideDirection.End
            slideIntoContainer(towards) togetherWith slideOutOfContainer(towards)
        },
        label          = "sign-up step"
    ) { shown ->
        Column(
            Modifier
                .fillMaxSize()
                .background(DsBrand.Mist)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            FormTopBar(onBack = { if (shown == 2) step = 1 else onBack() }, step = shown)
            Column(Modifier.padding(start = FormGutter, end = FormGutter, bottom = DsSpacing.lg)) {
                Spacer(Modifier.height(DsSpacing.sm))
                if (shown == 1) {
                    PersonalStep(state, onContinue = {
                        state.triedPersonal = true
                        if (state.personalValid) step = 2
                    })
                } else {
                    BusinessStep(state, actions, status, onCreate = {
                        state.triedBusiness = true
                        if (state.businessValid && !status.busy) actions.createAccount(state.form())
                    })
                }
            }
        }
    }
}

@Composable
private fun PersonalStep(state: SignUpState, onContinue: () -> Unit) {
    val tried = state.triedPersonal
    FormTitle(licText(R.string.onb_create_account), licText(R.string.onb_personal_info))
    Spacer(Modifier.height(DsSpacing.xl))
    BrandTextField(
        value          = state.fullName,
        onValueChange  = { state.fullName = it },
        label          = licText(R.string.onb_full_name),
        placeholder    = licText(R.string.onb_name_example),
        icon           = Icons.Outlined.Person,
        error          = if (tried && state.fullName.isBlank()) licText(R.string.onb_enter_full_name) else null,
        capitalization = KeyboardCapitalization.Words
    )
    Spacer(Modifier.height(DsSpacing.md))
    BrandTextField(
        value         = state.phone,
        onValueChange = { state.phone = it },
        label         = licText(R.string.acc_phone),
        placeholder   = licText(R.string.onb_phone_example),
        icon          = Icons.Outlined.Phone,
        error         = if (tried && !isPhone(state.phone)) licText(R.string.onb_invalid_phone) else null,
        keyboardType  = KeyboardType.Phone
    )
    Spacer(Modifier.height(DsSpacing.md))
    BrandTextField(
        value         = state.email,
        onValueChange = { state.email = it },
        label         = licText(R.string.onb_email_label),
        placeholder   = licText(R.string.onb_email_example),
        icon          = Icons.Outlined.Email,
        error         = if (tried && !isEmail(state.email)) licText(R.string.onb_invalid_email) else null,
        keyboardType  = KeyboardType.Email
    )
    Spacer(Modifier.height(DsSpacing.md))
    BrandPasswordField(
        value         = state.password,
        onValueChange = { state.password = it },
        label         = licText(R.string.acc_password),
        placeholder   = licText(R.string.onb_password_min, MIN_PASSWORD),
        error         = if (tried && state.password.length < MIN_PASSWORD) licText(R.string.onb_password_min, MIN_PASSWORD) else null,
        onDone        = onContinue
    )
    Spacer(Modifier.height(DsSpacing.xxl))
    BrandButton(licText(R.string.acc_continue), onClick = onContinue)
}

@Composable
private fun BusinessStep(state: SignUpState, actions: OnboardingActions, status: OnboardingStatus, onCreate: () -> Unit) {
    val tried = state.triedBusiness
    FormTitle(licText(R.string.onb_your_activity), licText(R.string.onb_about_business))
    Spacer(Modifier.height(DsSpacing.xl))
    BrandTextField(
        value          = state.company,
        onValueChange  = { state.company = it },
        label          = licText(R.string.acc_business_name),
        placeholder    = licText(R.string.acc_business_example),
        icon           = Icons.Outlined.Apartment,
        error          = if (tried && state.company.isBlank()) licText(R.string.onb_enter_business) else null,
        capitalization = KeyboardCapitalization.Words
    )
    Spacer(Modifier.height(DsSpacing.md))
    BrandChoiceField(
        value       = state.activity,
        onSelect    = { state.activity = it },
        label       = licText(R.string.acc_activity),
        placeholder = licText(R.string.onb_select_activity),
        icon        = Icons.Outlined.Storefront,
        options     = ACTIVITIES,
        error       = if (tried && state.activity.isEmpty()) licText(R.string.onb_choose_activity) else null
    )
    Spacer(Modifier.height(DsSpacing.md))
    BrandChoiceField(
        value       = state.salesReps,
        onSelect    = { state.salesReps = it },
        label       = licText(R.string.acc_sales_reps),
        placeholder = SALES_REPS.first(),
        icon        = Icons.Outlined.Groups,
        options     = SALES_REPS
    )
    Spacer(Modifier.height(DsSpacing.lg))
    TermsLine(
        accepted  = state.accepted,
        onAccept  = { state.accepted = it },
        error     = tried && !state.accepted,
        onTerms   = actions.openTerms,
        onPrivacy = actions.openPrivacy
    )
    Spacer(Modifier.height(DsSpacing.xl))
    status.error?.let {
        FormError(it)
        Spacer(Modifier.height(DsSpacing.md))
    }
    BrandButton(licText(R.string.onb_create_my_account), onClick = onCreate, busy = status.busy)
}

/** The checkbox, with the two documents as links in its sentence. */
@Composable
private fun TermsLine(accepted: Boolean, onAccept: (Boolean) -> Unit, error: Boolean, onTerms: () -> Unit, onPrivacy: () -> Unit) {
    val link = TextLinkStyles(SpanStyle(color = DsBrand.Blue, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline))
    val sentence = buildAnnotatedString {
        append(licText(R.string.onb_accept_1) + " ")
        withLink(LinkAnnotation.Clickable("terms", link) { onTerms() }) { append(licText(R.string.onb_terms)) }
        append(" " + licText(R.string.onb_accept_2) + " ")
        withLink(LinkAnnotation.Clickable("privacy", link) { onPrivacy() }) { append(licText(R.string.onb_privacy)) }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked         = accepted,
                onCheckedChange = onAccept,
                colors          = CheckboxDefaults.colors(
                    checkedColor   = DsBrand.Blue,
                    uncheckedColor = if (error) DsColors.Danger else DsBrand.Slate
                )
            )
            Text(sentence, fontFamily = DsBrand.Font, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 19.sp, color = DsBrand.Slate)
        }
        if (error) {
            Text(
                licText(R.string.onb_accept_terms),
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.Medium,
                fontSize   = 12.sp,
                color      = DsColors.Danger,
                modifier   = Modifier.padding(start = DsSpacing.md)
            )
        }
    }
}
