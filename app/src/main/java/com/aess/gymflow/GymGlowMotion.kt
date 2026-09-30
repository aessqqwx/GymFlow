package com.aess.gymflow

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Shared motion vocabulary. Effects change colour/opacity; spatial specs move bounds.
 * These stable Compose specs keep platform duration-scale handling intact.
 * Gestures must still update directly; use these specs only to settle after release.
 */
internal object GymGlowMotion {
    const val PressScale = 0.985f
    const val CelebrationPeakScale = 1.045f
    const val RootTravelDivisor = 8
    const val TabTravelDivisor = 16
    const val RewardStepMillis = 140L
    const val LoaderCycleMillis = 5_800

    fun <T> fastEffects(): TweenSpec<T> = tween(
        durationMillis = 140,
        easing = LinearOutSlowInEasing
    )

    fun <T> defaultEffects(): TweenSpec<T> = tween(
        durationMillis = 220,
        easing = FastOutSlowInEasing
    )

    fun <T> fastSpatial(): SpringSpec<T> = spring(
        dampingRatio = 1f,
        stiffness = 700f
    )

    fun <T> defaultSpatial(): SpringSpec<T> = spring(
        dampingRatio = 1f,
        stiffness = 420f
    )

    fun <T> emphasizedSpatial(): SpringSpec<T> = spring(
        dampingRatio = 1f,
        stiffness = 240f
    )

    // Reserved for one-shot reward accents, never navigation or large text blocks.
    fun <T> celebration(): SpringSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = 360f
    )

    fun <T> progress(): TweenSpec<T> = tween(
        durationMillis = 360,
        easing = FastOutSlowInEasing
    )
}
