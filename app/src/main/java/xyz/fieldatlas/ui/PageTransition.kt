@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package xyz.fieldatlas.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Moving between the tabs and a detail page (the answer, a source) animates instead of cutting: the page fades in,
 * the navigation bar slides away, and the Research home's question box travels down into the answer page's question
 * box, so Start research reads as the same box settling at the bottom.
 */
internal val LocalSharedTransition = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalPageVisibility = compositionLocalOf<AnimatedVisibilityScope?> { null }

private const val QUESTION_BOX = "question-box"
private const val PAGE_MILLIS = 320

/** The question box, shared between the Research home and the answer page. Nothing outside a page transition. */
@Composable
internal fun Modifier.sharedQuestionBox(): Modifier {
    val shared = LocalSharedTransition.current ?: return this
    val page = LocalPageVisibility.current ?: return this
    return with(shared) {
        this@sharedQuestionBox.sharedBounds(
            rememberSharedContentState(QUESTION_BOX),
            animatedVisibilityScope = page,
            boundsTransform = { _, _ -> tween(PAGE_MILLIS, easing = FastOutSlowInEasing) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

/** The navigation bar leaves downwards when a detail page opens, and comes back up when it closes. */
@Composable
internal fun Modifier.navigationBarTransition(): Modifier {
    val page = LocalPageVisibility.current ?: return this
    return with(page) {
        this@navigationBarTransition.animateEnterExit(
            enter = slideInVertically(tween(PAGE_MILLIS)) { it } + fadeIn(tween(PAGE_MILLIS)),
            exit = slideOutVertically(tween(PAGE_MILLIS)) { it } + fadeOut(tween(PAGE_MILLIS / 2)),
        )
    }
}

/** Which page is shown: the tabs, the answer, or a source (moving between sources is not a page change). */
internal fun pageKey(detail: DetailDestination?): String = when (detail) {
    null -> "tabs"
    DetailDestination.Answer -> "answer"
    is DetailDestination.Source -> "source"
}

// The page leaving fades for as long as the box travels: the shared box stops where it is once its page is gone.
internal fun AnimatedContentTransitionScope<DetailDestination?>.pageTransition(): ContentTransform =
    (fadeIn(tween(PAGE_MILLIS, delayMillis = PAGE_MILLIS / 4)) togetherWith fadeOut(tween(PAGE_MILLIS)))
        .using(SizeTransform(clip = false))
