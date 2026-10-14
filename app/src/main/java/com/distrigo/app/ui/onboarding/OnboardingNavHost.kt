package com.distrigo.app.ui.onboarding

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.distrigo.app.diagnostics.rememberTrackedNavController
import com.distrigo.app.ui.designsystem.DsBrand
import com.distrigo.app.ui.navigation.navEnterTransition
import com.distrigo.app.ui.navigation.navExitTransition
import com.distrigo.app.ui.navigation.navPopEnterTransition
import com.distrigo.app.ui.navigation.navPopExitTransition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the onboarding hands over when it ends. The preview answers each with a message. */
class OnboardingActions(
    val signIn: (email: String, password: String) -> Unit,
    /** Null hides the Google button: no Google Cloud Web client is configured yet. */
    val signInWithGoogle: (() -> Unit)?,
    val forgotPassword: () -> Unit,
    val createAccount: (SignUpForm) -> Unit,
    /** A complete code (no dashes): the answer arrives in [OnboardingStatus]. */
    val redeemCode: (code: String) -> Unit,
    /** The company a code without one belongs to, once [OnboardingStatus.codeNeedsBusiness] asked for it. */
    val completeCodeBusiness: (company: String, phone: String) -> Unit,
    /** Forgets a code's error or pending company, when the code is edited or left. */
    val resetCode: () -> Unit,
    val openTerms: () -> Unit,
    val openPrivacy: () -> Unit,
)

/** What the account side reports back while it works: the onboarding's screens only show it. */
data class OnboardingStatus(
    val busy: Boolean = false,
    /** Why the last attempt failed, in French, for the screen that made it. */
    val error: String? = null,
    /** The code is valid but carries no company: its name and a phone number come next. */
    val codeNeedsBusiness: Boolean = false,
    /** The phone is activated: the success screen. */
    val activation: ActivationSummary? = null,
)

private const val CAROUSEL = "onboarding_carousel"
private const val SIGN_IN  = "onboarding_sign_in"
private const val SIGN_UP  = "onboarding_sign_up"
private const val CODE     = "onboarding_code"
private const val SUCCESS  = "onboarding_success"

/**
 * The onboarding (docs/design/onboarding_ui_review.md): the carousel, then Connexion, Créer un compte or
 * Activer avec un code, and once [status] carries an activation the success screen, which [onFinished]
 * leaves. It draws edge to edge, so its caller places it outside the root's status-bar padding.
 */
@Composable
fun OnboardingNavHost(actions: OnboardingActions, status: OnboardingStatus, onFinished: () -> Unit) {
    val navController = rememberTrackedNavController()
    val pager = rememberPagerState { ONBOARDING_PAGE_COUNT }
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    // Only the welcome page is dark
    OnboardingSystemBars(lightIcons = (route == null || route == CAROUSEL) && pager.currentPage == 0)

    // Activated: the success screen replaces everything, so Back can't return to the forms
    val activation = status.activation
    LaunchedEffect(activation) {
        if (activation != null) navController.navigate(SUCCESS) { popUpTo(CAROUSEL) { inclusive = true } }
    }
    val openCode = { navController.navigate(CODE) { launchSingleTop = true } }

    NavHost(
        navController      = navController,
        startDestination   = CAROUSEL,
        enterTransition    = navEnterTransition,
        exitTransition     = navExitTransition,
        popEnterTransition = navPopEnterTransition,
        popExitTransition  = navPopExitTransition
    ) {
        composable(CAROUSEL) {
            OnboardingCarousel(
                pager           = pager,
                onSignIn        = { navController.navigate(SIGN_IN) { launchSingleTop = true } },
                onCreateAccount = { navController.navigate(SIGN_UP) { launchSingleTop = true } },
                onCode          = openCode,
            )
        }
        composable(SIGN_IN) {
            SignInPage(
                actions         = actions,
                onBack          = { navController.popBackStack() },
                onCreateAccount = { navController.navigate(SIGN_UP) { popUpTo(CAROUSEL) } },
                onCode          = openCode,
            )
        }
        composable(SIGN_UP) {
            SignUpPages(actions = actions, onBack = { navController.popBackStack() })
        }
        composable(CODE) {
            CodePages(
                actions         = actions,
                status          = status,
                onBack          = {
                    actions.resetCode()
                    navController.popBackStack()
                },
                onCreateAccount = { navController.navigate(SIGN_UP) { popUpTo(CAROUSEL) } },
            )
        }
        composable(SUCCESS) {
            activation?.let { SuccessPage(it, onContinue = onFinished) }
        }
    }
}

/**
 * [OnboardingNavHost] on its own, opened from Compte while the onboarding is being built; nothing is sent.
 * - A valid sign-in or sign-up shows the success screen with a sample 30-day trial.
 * - A code is "checked" for a moment: one made of a single repeated character is unknown, any other is
 *   valid without a company, so the company step shows, then a sample 6-month subscription.
 * The rest only says it isn't wired yet. Back from the welcome page, or the success screen's button, closes it.
 */
@Composable
fun OnboardingPreview(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(OnboardingStatus()) }
    val actions = remember(context) {
        val say: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        OnboardingActions(
            signIn               = { _, _ -> status = OnboardingStatus(activation = sample(ActivationOrigin.SIGN_IN, trial = true, days = 30)) },
            signInWithGoogle     = { status = OnboardingStatus(activation = sample(ActivationOrigin.SIGN_IN, trial = true, days = 30)) },
            forgotPassword       = { say("La réinitialisation du mot de passe arrive bientôt.") },
            createAccount        = { status = OnboardingStatus(activation = sample(ActivationOrigin.NEW_ACCOUNT, trial = true, days = 30)) },
            redeemCode           = { code ->
                status = OnboardingStatus(busy = true)
                scope.launch {
                    delay(1_200)
                    status = if (code.all { it == code.first() }) OnboardingStatus(error = "Ce code n'existe pas ou n'est plus valable.")
                             else OnboardingStatus(codeNeedsBusiness = true)
                }
            },
            completeCodeBusiness = { _, _ ->
                status = status.copy(busy = true)
                scope.launch {
                    delay(1_200)
                    status = OnboardingStatus(activation = sample(ActivationOrigin.CODE, trial = false, days = 182))
                }
            },
            resetCode            = { status = OnboardingStatus() },
            openTerms            = { say("Les conditions d'utilisation sont en cours de rédaction.") },
            openPrivacy          = { say("La politique de confidentialité est en cours de rédaction.") },
        )
    }
    // Drawn over the app, so nothing beneath it may be touched through it
    Box(Modifier.fillMaxSize().background(DsBrand.Mist).pointerInput(Unit) {}) {
        OnboardingNavHost(actions, status = status, onFinished = onClose)
    }
}

/** A license starting now and lasting [days], as the preview's activations get it. */
private fun sample(origin: ActivationOrigin, trial: Boolean, days: Int): ActivationSummary {
    val now = System.currentTimeMillis()
    return ActivationSummary(origin, trial = trial, validFromMillis = now, validToMillis = now + days * 24L * 60 * 60 * 1000)
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
