package com.distrigo.app.ui.onboarding

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import kotlinx.coroutines.launch

/** The welcome screen, then one page per entry of [FEATURE_PAGES]. */
internal val ONBOARDING_PAGE_COUNT = 1 + FEATURE_PAGES.size

/**
 * The onboarding's first part (docs/design/onboarding_ui_review.md): the welcome screen and the four
 * feature pages, swiped or stepped through. It draws edge to edge, so its caller places it outside the
 * root's status-bar padding.
 */
@Composable
fun OnboardingCarousel(onSignIn: () -> Unit, onCreateAccount: () -> Unit) {
    val pager = rememberPagerState { ONBOARDING_PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val next: () -> Unit = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }

    // Back steps through the pages before it leaves the carousel
    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }
    // The welcome photo is dark and the feature pages light: the system-bar icons follow the page in view
    OnboardingSystemBars(lightIcons = pager.currentPage == 0)

    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        if (page == 0) {
            WelcomeScreen(onStart = next, onSignIn = onSignIn)
        } else {
            FeaturePage(FEATURE_PAGES[page - 1], page = page, onSkip = onCreateAccount, onNext = next, onCreateAccount = onCreateAccount)
        }
    }
}

/**
 * [OnboardingCarousel] on its own, opened from Compte while the rest of the onboarding is being
 * built: the buttons that lead out of it only say so, and Back closes it.
 */
@Composable
fun OnboardingPreview(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val notBuiltYet = {
        Toast.makeText(context, "Aperçu : la connexion et l'inscription ne sont pas encore construites.", Toast.LENGTH_SHORT).show()
    }
    // Drawn over the app, so nothing beneath it may be touched through it
    Box(Modifier.fillMaxSize().pointerInput(Unit) {}) {
        OnboardingCarousel(onSignIn = notBuiltYet, onCreateAccount = notBuiltYet)
    }
}

/** Where the reader is among the [ONBOARDING_PAGE_COUNT] pages: the current one is drawn longer. */
@Composable
internal fun PageDots(current: Int, active: Color, inactive: Color, modifier: Modifier = Modifier) {
    Row(
        modifier              = modifier.clearAndSetSemantics { contentDescription = "Page ${current + 1} sur $ONBOARDING_PAGE_COUNT" },
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(ONBOARDING_PAGE_COUNT) { page ->
            Box(
                Modifier
                    .height(8.dp)
                    .width(if (page == current) 22.dp else 8.dp)
                    .clip(DsShapes.pill)
                    .background(if (page == current) active else inactive)
            )
        }
    }
}

/**
 * White system-bar icons over the dark welcome page, dark ones over the light pages, and no system
 * scrim behind the three-button navigation bar (it would draw a band across the pages). What the
 * theme set comes back when the carousel leaves.
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
