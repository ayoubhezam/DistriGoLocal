package com.distrigo.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.distrigo.app.ui.designsystem.DsShapes
import com.distrigo.app.ui.designsystem.DsSpacing
import kotlinx.coroutines.launch

/** The welcome screen, then one page per entry of [FEATURE_PAGES]. */
internal val ONBOARDING_PAGE_COUNT = 1 + FEATURE_PAGES.size

/**
 * The onboarding's first part (docs/design/onboarding_ui_review.md): the welcome screen and the four
 * feature pages, swiped or stepped through. [pager] belongs to the caller, so the page survives a trip
 * to sign-in and back, and the system bars can follow it.
 */
@Composable
internal fun OnboardingCarousel(pager: PagerState, onSignIn: () -> Unit, onCreateAccount: () -> Unit, onCode: () -> Unit) {
    val scope = rememberCoroutineScope()
    val next: () -> Unit = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }

    // Back steps through the pages before it leaves the carousel
    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        if (page == 0) {
            WelcomeScreen(onStart = next, onSignIn = onSignIn, onCode = onCode)
        } else {
            FeaturePage(FEATURE_PAGES[page - 1], page = page, onSkip = onCreateAccount, onNext = next, onCreateAccount = onCreateAccount)
        }
    }
}

/** Where the reader is among [count] pages or steps: the current one is drawn longer. */
@Composable
internal fun PageDots(
    current: Int,
    active: Color,
    inactive: Color,
    modifier: Modifier = Modifier,
    count: Int = ONBOARDING_PAGE_COUNT,
) {
    Row(
        modifier              = modifier.clearAndSetSemantics { contentDescription = "Page ${current + 1} sur $count" },
        horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(count) { page ->
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
