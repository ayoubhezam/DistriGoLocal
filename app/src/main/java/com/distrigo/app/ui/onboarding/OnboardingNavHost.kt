package com.distrigo.app.ui.onboarding

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.distrigo.app.diagnostics.rememberTrackedNavController
import com.distrigo.app.ui.account.GoogleSignIn
import com.distrigo.app.ui.account.findActivity
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.navigation.navEnterTransition
import com.distrigo.app.ui.navigation.navExitTransition
import com.distrigo.app.ui.navigation.navPopEnterTransition
import com.distrigo.app.ui.navigation.navPopExitTransition
import kotlinx.coroutines.launch

/** What the onboarding hands over: its screens only collect, [OnboardingViewModel] does the rest. */
class OnboardingActions(
    val signIn: (email: String, password: String) -> Unit,
    /** Null hides the Google button: no Google Cloud Web client is configured yet. */
    val signInWithGoogle: (() -> Unit)?,
    val forgotPassword: () -> Unit,
    val createAccount: (SignUpForm) -> Unit,
    /** A complete code (no dashes): the answer arrives in [OnboardingStatus]. */
    val redeemCode: (code: String) -> Unit,
    /** The company the server asked for ([OnboardingStatus.needsBusiness]). */
    val completeBusiness: (company: String, phone: String) -> Unit,
    /** Forgets an error or a pending answer, when a code is edited or a step left. */
    val resetCode: () -> Unit,
    val confirmTransfer: () -> Unit,
    val openTerms: () -> Unit,
    val openPrivacy: () -> Unit,
)

/** What the account side reports back while it works: the onboarding's screens only show it. */
data class OnboardingStatus(
    val busy: Boolean = false,
    /** Why the last attempt failed, in French, for the screen that made it. */
    val error: String? = null,
    /** A code naming no company, or an account without a business: its name and a phone number come next. */
    val needsBusiness: Boolean = false,
    /** The account holds its seat on another phone, these models: move it here? */
    val seatTaken: List<String>? = null,
    /** An e-mail sign-up on a phone that already had its trial: only a code can pay for it now. */
    val codeRequired: Boolean = false,
    /** The phone is activated: the success screen. */
    val activation: ActivationSummary? = null,
) {
    /** A step only the onboarding shows: a sign-in made in Compte opens it there (MainActivity). */
    val followUp: Boolean get() = activation != null || needsBusiness || seatTaken != null || codeRequired
}

/** Where the onboarding opens: the welcome screen on a fresh install, a form when Compte asks for one. */
enum class OnboardingStart { WELCOME, SIGN_IN, SIGN_UP, CODE }

private const val CAROUSEL = "onboarding_carousel"
private const val SIGN_IN  = "onboarding_sign_in"
private const val SIGN_UP  = "onboarding_sign_up"
private const val CODE     = "onboarding_code"
private const val BUSINESS = "onboarding_business"
private const val SUCCESS  = "onboarding_success"

/**
 * The onboarding (docs/design/onboarding_ui_review.md, docs/activation_codes.md): the carousel, then Connexion,
 * Créer un compte or Activer avec un code, and once [status] carries an activation the success screen, which
 * [onFinished] leaves; [onClose] is Back from its first screen. It draws edge to edge, so its caller places it outside the root's status-bar padding.
 */
@Composable
fun OnboardingNavHost(
    actions: OnboardingActions,
    status: OnboardingStatus,
    onFinished: () -> Unit,
    onClose: () -> Unit,
    start: OnboardingStart = OnboardingStart.WELCOME,
    onIntroSeen: () -> Unit = {},
) {
    val navController = rememberTrackedNavController()
    val pager = rememberPagerState { ONBOARDING_PAGE_COUNT }
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    // Only the welcome page is dark
    OnboardingSystemBars(lightIcons = (route == null || route == CAROUSEL) && pager.currentPage == 0)

    // Activated: the success screen replaces everything, so Back can't return to the forms
    val activation = status.activation
    LaunchedEffect(activation) {
        if (activation != null) navController.navigate(SUCCESS) { popUpTo(navController.graph.id) { inclusive = true } }
    }
    // The server wants a company: the code screen asks it itself, the other ways in come here
    LaunchedEffect(status.needsBusiness) {
        if (status.needsBusiness && navController.currentDestination?.route != CODE) {
            navController.navigate(BUSINESS) { launchSingleTop = true }
        }
    }
    LaunchedEffect(status.codeRequired) {
        if (status.codeRequired) navController.navigate(CODE) { launchSingleTop = true }
    }
    val openCode = { navController.navigate(CODE) { launchSingleTop = true } }
    // From the tour to a form: back to that form when the tour was opened from it (« Découvrir DistriGo »)
    val leaveTour = { form: String ->
        onIntroSeen()
        if (!navController.popBackStack(form, inclusive = false)) navController.navigate(form) { launchSingleTop = true }
    }
    val leave = {
        actions.resetCode()
        // Back from the first screen leaves the onboarding, as the system Back does
        if (!navController.popBackStack()) onClose()
    }

    NavHost(
        navController      = navController,
        startDestination   = when (start) {
            OnboardingStart.WELCOME -> CAROUSEL
            OnboardingStart.SIGN_IN -> SIGN_IN
            OnboardingStart.SIGN_UP -> SIGN_UP
            OnboardingStart.CODE -> CODE
        },
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(CAROUSEL) {
            OnboardingCarousel(
                pager           = pager,
                onSignIn        = { leaveTour(SIGN_IN) },
                onCreateAccount = { leaveTour(SIGN_UP) },
                onCode          = { leaveTour(CODE) },
            )
        }
        composable(SIGN_IN) {
            SignInPage(
                actions         = actions,
                status          = status,
                onBack          = leave,
                onCreateAccount = { navController.navigate(SIGN_UP) { popUpTo(SIGN_IN) { inclusive = true } } },
                onCode          = openCode,
                // Opened at Connexion (a sign-out, Compte), the tour is one tap away; from the tour, Back is
                onDiscover      = if (start == OnboardingStart.WELCOME) null else ({ navController.navigate(CAROUSEL) { launchSingleTop = true } }),
            )
        }
        composable(SIGN_UP) {
            SignUpPages(actions = actions, status = status, onBack = leave)
        }
        composable(CODE) {
            CodePages(
                actions         = actions,
                status          = status,
                onBack          = leave,
                onCreateAccount = { navController.navigate(SIGN_UP) { popUpTo(CODE) { inclusive = true } } },
            )
        }
        composable(BUSINESS) {
            BusinessPage(actions = actions, status = status, onBack = leave)
        }
        composable(SUCCESS) {
            activation?.let { SuccessPage(it, onContinue = onFinished) }
        }
    }

    status.seatTaken?.let { models ->
        TransferDialog(models = models, busy = status.busy, onConfirm = actions.confirmTransfer, onCancel = actions.resetCode)
    }
}

/** « Ce compte est utilisé sur un autre téléphone »: move it here, at the cost of one of the month's 3 moves. */
@Composable
private fun TransferDialog(models: List<String>, busy: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor   = Color.White,
        title            = {
            Text("Utiliser DistriGo sur ce téléphone ?", fontFamily = DsBrand.Font, fontWeight = FontWeight.ExtraBold, color = DsBrand.Ink)
        },
        text             = {
            Text(
                "Ce compte est actif sur ${models.joinToString(", ")}. En le transférant ici, l'autre téléphone ne " +
                    "pourra plus l'utiliser. Vous pouvez changer de téléphone 3 fois par mois.",
                fontFamily = DsBrand.Font,
                fontWeight = FontWeight.Medium,
                color      = DsBrand.Slate
            )
        },
        confirmButton    = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text("Transférer ici", fontFamily = DsBrand.Font, fontWeight = FontWeight.Bold, color = DsBrand.Blue)
            }
        },
        dismissButton    = {
            TextButton(onClick = onCancel, enabled = !busy) {
                Text("Annuler", fontFamily = DsBrand.Font, fontWeight = FontWeight.SemiBold, color = DsBrand.Slate)
            }
        }
    )
}

/**
 * The onboarding with its account side: [viewModel] signs in, signs up, takes codes and activates the phone.
 * [onClose] is Back from the first screen: closing it when Compte opened it, leaving the app on a fresh install.
 */
@Composable
fun Onboarding(
    viewModel: OnboardingViewModel,
    start: OnboardingStart,
    onFinished: () -> Unit,
    onClose: () -> Unit,
    onIntroSeen: () -> Unit = {},
) {
    BackHandler(onBack = onClose)
    val status by viewModel.status.collectAsState()
    val actions = rememberOnboardingActions(viewModel)
    // Drawn over the app, so nothing beneath it may be touched through it
    Box(Modifier.fillMaxSize().background(DsBrand.Mist).pointerInput(Unit) {}) {
        OnboardingNavHost(actions, status = status, onFinished = onFinished, onClose = onClose, start = start, onIntroSeen = onIntroSeen)
    }
}

/** What the onboarding's screens hand to [viewModel]; Compte's sign-in form uses the same. */
@Composable
fun rememberOnboardingActions(viewModel: OnboardingViewModel): OnboardingActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(viewModel, context) {
        val say: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        val activity = context.findActivity()
        OnboardingActions(
            signIn               = viewModel::signIn,
            signInWithGoogle     = if (GoogleSignIn.available && activity != null) {
                {
                    scope.launch {
                        when (val result = GoogleSignIn.request(activity)) {
                            is GoogleSignIn.Result.Token -> viewModel.signInWithGoogle(result.idToken, result.rawNonce)
                            is GoogleSignIn.Result.Failed -> say(result.message)
                            GoogleSignIn.Result.Cancelled -> Unit
                        }
                    }
                }
            } else {
                null
            },
            forgotPassword       = { say("La réinitialisation du mot de passe arrive bientôt. En attendant, contactez DistriGo.") },
            createAccount        = viewModel::createAccount,
            redeemCode           = viewModel::redeemCode,
            completeBusiness     = viewModel::completeBusiness,
            resetCode            = viewModel::reset,
            confirmTransfer      = viewModel::confirmTransfer,
            openTerms            = { say("Les conditions d'utilisation sont en cours de rédaction.") },
            openPrivacy          = { say("La politique de confidentialité est en cours de rédaction.") },
        )
    }
}

/**
 * White system-bar icons over the dark welcome page, dark ones over the light screens, and no system
 * scrim behind the three-button navigation bar (it would draw a band across the pages). What the
 * theme set comes back when the onboarding leaves.
 */
@Composable
private fun OnboardingSystemBars(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window     = (view.context as Activity).window
    val controller = remember(view) { WindowCompat.getInsetsController(window, view) }
    SideEffect {
        controller.isAppearanceLightStatusBars     = !lightIcons
        controller.isAppearanceLightNavigationBars = !lightIcons
    }
    DisposableEffect(view) {
        val contrastEnforced = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && window.isNavigationBarContrastEnforced
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        onDispose {
            controller.isAppearanceLightStatusBars     = true
            controller.isAppearanceLightNavigationBars = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = contrastEnforced
        }
    }
}
