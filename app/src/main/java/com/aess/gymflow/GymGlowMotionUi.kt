package com.aess.gymflow

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

internal fun directionalMotion(forward: Boolean, divisor: Int = GymGlowMotion.RootTravelDivisor): ContentTransform {
    val sign = if (forward) 1 else -1
    return (slideInHorizontally(GymGlowMotion.defaultSpatial()) { sign * it / divisor } +
        fadeIn(GymGlowMotion.defaultEffects())) togetherWith
        (slideOutHorizontally(GymGlowMotion.defaultSpatial()) { -sign * it / divisor } +
        fadeOut(GymGlowMotion.fastEffects()))
}

internal fun numberMotion(increasing: Boolean): ContentTransform {
    val sign = if (increasing) 1 else -1
    return (slideInVertically(GymGlowMotion.fastSpatial()) { sign * it / 3 } +
        fadeIn(GymGlowMotion.fastEffects())) togetherWith
        (slideOutVertically(GymGlowMotion.fastSpatial()) { -sign * it / 3 } +
        fadeOut(GymGlowMotion.fastEffects()))
}

internal fun effectsMotion(): ContentTransform =
    fadeIn(GymGlowMotion.defaultEffects()) togetherWith fadeOut(GymGlowMotion.fastEffects())

internal fun expandMotion(): EnterTransition =
    expandVertically(GymGlowMotion.defaultSpatial()) + fadeIn(GymGlowMotion.defaultEffects())

internal fun collapseMotion(): ExitTransition =
    shrinkVertically(GymGlowMotion.defaultSpatial()) + fadeOut(GymGlowMotion.fastEffects())

internal fun LazyItemScope.motionItemModifier(): Modifier = Modifier.animateItem(
    fadeInSpec = GymGlowMotion.defaultEffects(),
    placementSpec = GymGlowMotion.defaultSpatial(),
    fadeOutSpec = GymGlowMotion.fastEffects()
)

/** Read animation state inside the indicator's draw callback, not its parent screen. */
@Composable
internal fun GymGlowProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    strokeCap: StrokeCap = StrokeCap.Round
) {
    val target = progress().let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
    val animated = animateFloatAsState(target, GymGlowMotion.progress(), label = "gymglow_progress")
    LinearProgressIndicator(
        progress = { animated.value.coerceIn(0f, 1f) }, modifier = modifier,
        color = color, trackColor = trackColor, strokeCap = strokeCap
    )
}

/** A bounded reward reveal; saves its event state to avoid replay on recreation. */
@Composable
internal fun RewardReveal(
    event: Long,
    order: Int,
    emphasized: Boolean = false,
    content: @Composable () -> Unit
) {
    var shown by rememberSaveable(event) { mutableStateOf(false) }
    val reveal = remember(event) { Animatable(if (shown) 1f else 0f) }
    val placement = remember(event) { Animatable(if (shown) 1f else 0f) }
    val travel = androidx.compose.ui.platform.LocalDensity.current.run { 8.dp.toPx() }
    LaunchedEffect(event) {
        if (!shown) {
            // If recreated during the sequence, restore final readable content.
            shown = true
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            if (scale > 0f) delay((order * GymGlowMotion.RewardStepMillis * scale).toLong())
            coroutineScope {
                launch { reveal.animateTo(1f, GymGlowMotion.defaultEffects()) }
                launch { placement.animateTo(1f, if (emphasized) GymGlowMotion.emphasizedSpatial() else GymGlowMotion.defaultSpatial()) }
            }
        }
    }
    Box(Modifier.graphicsLayer {
        alpha = reveal.value
        translationY = travel * (1f - placement.value)
        val startScale = if (emphasized) 0.96f else 1f
        scaleX = startScale + (1f - startScale) * placement.value
        scaleY = scaleX
    }, contentAlignment = Alignment.Center) { content() }
}

@Composable
internal fun MotionStreakBadge(days: Int, suffix: String = "", before: Int = days, event: Long? = null, record: Boolean = false, revealOrder: Int = 0) {
    var displayed by rememberSaveable(event) { mutableIntStateOf(before) }
    var played by rememberSaveable(event) { mutableStateOf(false) }
    val flame = remember(event) { Animatable(1f) }
    LaunchedEffect(days, event) {
        val increased = days > displayed
        if (event != null && played) {
            displayed = days
            flame.snapTo(1f)
            return@LaunchedEffect
        }
        if (event != null) played = true
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (event != null && increased && scale > 0f) {
            delay((revealOrder * GymGlowMotion.RewardStepMillis * scale).toLong())
        }
        displayed = days
        flame.snapTo(1f)
        if (increased) {
            flame.animateTo(if (record) GymGlowMotion.CelebrationPeakScale else 1.025f, GymGlowMotion.fastSpatial())
            flame.animateTo(1f, GymGlowMotion.celebration())
        }
    }
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", fontSize = 17.sp, modifier = Modifier.graphicsLayer {
                scaleX = flame.value; scaleY = flame.value
                rotationZ = (flame.value - 1f) * 80f
            })
            Spacer(Modifier.width(5.dp))
            AnimatedContent(displayed, transitionSpec = { numberMotion(targetState > initialState) }, label = "streak_number") { value ->
                Text("$value$suffix", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}
