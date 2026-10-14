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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
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

/** What the onboarding hands over when it ends. The preview answers each with a message. */
class OnboardingActions(
    val signIn: (email: String, password: String) -> Unit,
    /** Null hides the Google button: no Google Cloud Web client is configured yet. */
    val signInWithGoogle: (() -> Unit)?,
    val forgotPassword: () -> Unit,
    val createAccount: (SignUpForm) -> Unit,
    val openTerms: () -> Unit,
    val openPrivacy: () -> Unit,
)

private const val CAROUSEL = "onboarding_carousel"
private const val SIGN_IN  = "onboarding_sign_in"
private const val SIGN_UP  = "onboarding_sign_up"

/**
 * The onboarding (docs/design/onboarding_ui_review.md): the carousel, then Connexion or Créer un
 * compte. It draws edge to edge, so its caller places it outside the root's status-bar padding.
 */
@Composable
fun OnboardingNavHost(actions: OnboardingActions) {
    val navController = rememberTrackedNavController()
    val pager = rememberPagerState { ONBOARDING_PAGE_COUNT }
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    // Only the welcome page is dark
    OnboardingSystemBars(lightIcons = (route == null || route == CAROUSEL) && pager.currentPage == 0)

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
            )
        }
        composable(SIGN_IN) {
            SignInPage(
                actions         = actions,
                onBack          = { navController.popBackStack() },
                onCreateAccount = { navController.navigate(SIGN_UP) { popUpTo(CAROUSEL) } },
            )
        }
        composable(SIGN_UP) {
            SignUpPages(actions = actions, onBack = { navController.popBackStack() })
        }
    }
}

/**
 * [OnboardingNavHost] on its own, opened from Compte while the onboarding is being built: what would
 * leave it only says so, and Back from the welcome page closes it.
 */
@Composable
fun OnboardingPreview(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val actions = remember(context) {
        val say: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        OnboardingActions(
            signIn           = { _, _ -> say("Aperçu : la connexion n'est pas encore branchée.") },
            signInWithGoogle = { say("Aperçu : la connexion Google n'est pas encore branchée.") },
            forgotPassword   = { say("La réinitialisation du mot de passe arrive bientôt.") },
            createAccount    = { say("Aperçu : la création du compte n'est pas encore branchée.") },
            openTerms        = { say("Les conditions d'utilisation sont en cours de rédaction.") },
            openPrivacy      = { say("La politique de confidentialité est en cours de rédaction.") },
        )
    }
    // Drawn over the app, so nothing beneath it may be touched through it
    Box(Modifier.fillMaxSize().background(DsBrand.Mist).pointerInput(Unit) {}) {
        OnboardingNavHost(actions)
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
