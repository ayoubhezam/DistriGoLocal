package com.distrigo.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
internal val ACTIVITIES = listOf(
    "Alimentation générale",
    "Boissons",
    "Produits laitiers et frais",
    "Hygiène et cosmétiques",
    "Droguerie et entretien",
    "Matériaux et quincaillerie",
    "Pharmacie et parapharmacie",
    "Autre",
)

/** « Nombre de commerciaux », in ranges: the plan a business will need follows from it. */
internal val SALES_REPS = listOf("1", "2 à 5", "6 à 10", "Plus de 10")

/** What the server stores for each label (public.businesses' checks); null for anything else. */
internal fun activityCode(label: String): String? = mapOf(
    "Alimentation générale" to "alimentation",
    "Boissons" to "boissons",
    "Produits laitiers et frais" to "frais",
    "Hygiène et cosmétiques" to "hygiene",
    "Droguerie et entretien" to "droguerie",
    "Matériaux et quincaillerie" to "materiaux",
    "Pharmacie et parapharmacie" to "pharmacie",
    "Autre" to "autre",
)[label]

internal fun salesRepsCode(label: String): String? =
    mapOf("1" to "1", "2 à 5" to "2-5", "6 à 10" to "6-10", "Plus de 10" to "10+")[label]

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
            slideInHorizontally { if (forward) it else -it } togetherWith slideOutHorizontally { if (forward) -it else it }
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
    FormTitle("Créer un compte", "Renseignez vos informations personnelles")
    Spacer(Modifier.height(DsSpacing.xl))
    BrandTextField(
        value          = state.fullName,
        onValueChange  = { state.fullName = it },
        label          = "Nom complet",
        placeholder    = "Ex. : Mohamed Benali",
        icon           = Icons.Outlined.Person,
        error          = if (tried && state.fullName.isBlank()) "Indiquez votre nom complet" else null,
        capitalization = KeyboardCapitalization.Words
    )
    Spacer(Modifier.height(DsSpacing.md))
    BrandTextField(
        value         = state.phone,
        onValueChange = { state.phone = it },
        label         = "Téléphone",
        placeholder   = "Ex. : 07 12 34 56 78",
        icon          = Icons.Outlined.Phone,
        error         = if (tried && !isPhone(state.phone)) "Numéro de téléphone invalide" else null,
        keyboardType  = KeyboardType.Phone
    )
    Spacer(Modifier.height(DsSpacing.md))
    BrandTextField(
        value         = state.email,
        onValueChange = { state.email = it },
        label         = "E-mail",
        placeholder   = "exemple@domaine.com",
        icon          = Icons.Outlined.Email,
        error         = if (tried && !isEmail(state.email)) "Adresse e-mail invalide" else null,
        keyboardType  = KeyboardType.Email
    )
    Spacer(Modifier.height(DsSpacing.md))
    BrandPasswordField(
        value         = state.password,
        onValueChange = { state.password = it },
        label         = "Mot de passe",
        placeholder   = "Minimum $MIN_PASSWORD caractères",
        error         = if (tried && state.password.length < MIN_PASSWORD) "Minimum $MIN_PASSWORD caractères" else null,
        onDone        = onContinue
    )
    Spacer(Modifier.height(DsSpacing.xxl))
    BrandButton("Continuer", onClick = onContinue)
}

@Composable
private fun BusinessStep(state: SignUpState, actions: OnboardingActions, status: OnboardingStatus, onCreate: () -> Unit) {
    val tried = state.triedBusiness
    FormTitle("Votre activité", "Parlez-nous de votre entreprise")
    Spacer(Modifier.height(DsSpacing.xl))
    BrandTextField(
        value          = state.company,
        onValueChange  = { state.company = it },
        label          = "Nom de l'entreprise",
        placeholder    = "Ex. : SARL ABC Distribution",
        icon           = Icons.Outlined.Apartment,
        error          = if (tried && state.company.isBlank()) "Indiquez le nom de l'entreprise" else null,
        capitalization = KeyboardCapitalization.Words
    )
    Spacer(Modifier.height(DsSpacing.md))
    BrandChoiceField(
        value       = state.activity,
        onSelect    = { state.activity = it },
        label       = "Type d'activité",
        placeholder = "Sélectionnez votre activité",
        icon        = Icons.Outlined.Storefront,
        options     = ACTIVITIES,
        error       = if (tried && state.activity.isEmpty()) "Choisissez votre activité" else null
    )
    Spacer(Modifier.height(DsSpacing.md))
    BrandChoiceField(
        value       = state.salesReps,
        onSelect    = { state.salesReps = it },
        label       = "Nombre de commerciaux",
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
    BrandButton("Créer mon compte", onClick = onCreate, busy = status.busy)
}

/** The checkbox, with the two documents as links in its sentence. */
@Composable
private fun TermsLine(accepted: Boolean, onAccept: (Boolean) -> Unit, error: Boolean, onTerms: () -> Unit, onPrivacy: () -> Unit) {
    val link = TextLinkStyles(SpanStyle(color = DsBrand.Blue, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline))
    val sentence = buildAnnotatedString {
        append("J'accepte les ")
        withLink(LinkAnnotation.Clickable("terms", link) { onTerms() }) { append("conditions d'utilisation") }
        append(" et la ")
        withLink(LinkAnnotation.Clickable("privacy", link) { onPrivacy() }) { append("politique de confidentialité") }
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
                "Acceptez les conditions pour créer le compte",
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.Medium,
                fontSize   = 12.sp,
                color      = DsColors.Danger,
                modifier   = Modifier.padding(start = DsSpacing.md)
            )
        }
    }
}
