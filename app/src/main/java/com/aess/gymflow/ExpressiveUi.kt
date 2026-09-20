package com.aess.gymflow

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun ExpressiveSurfaceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    corner: Dp = 26.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 480f),
        label = "expressive_press"
    )
    Surface(
        modifier = modifier.scale(scale),
        shape = RoundedCornerShape(corner),
        color = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        contentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = if (pressed) 1.dp else 3.dp
    ) {
        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = interaction,
                    indication = androidx.compose.foundation.ripple.ripple(),
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) { content() }
    }
}

@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    corner: Dp = 30.dp,
    content: @Composable () -> Unit
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(corner), color = containerColor, tonalElevation = 1.dp) {
        Box(Modifier.padding(18.dp)) { content() }
    }
}

/**
 * Shared GymFlow expressive loader shape.
 * Order: circle -> blob -> rounded square -> 4-point shape -> star -> circle.
 */
@Composable
fun GymFlowMorphingShape(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "gymflow_morph")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gymflow_morph_phase"
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val base = minOf(size.width, size.height) * .42f
        val from = phase.toInt().coerceIn(0, 4)
        val t = phase - from

        fun radius(type: Int, angle: Double): Double = when (type) {
            0 -> 1.0 // circle
            1 -> 1.0 + .12 * sin(3 * angle + phase * .75) + .05 * sin(5 * angle - phase) // blob
            2 -> { // rounded square / superellipse
                val c = abs(cos(angle)).pow(4.2)
                val s = abs(sin(angle)).pow(4.2)
                1.0 / max(0.001, (c + s).pow(1.0 / 4.2)) * .83
            }
            3 -> .72 + .28 * abs(cos(2 * angle)) // four-point shape
            else -> .67 + .33 * ((cos(5 * angle) + 1) / 2) // star-like
        }

        val path = Path()
        val count = 96
        for (i in 0..count) {
            val angle = 2 * PI * i / count - PI / 2
            val r1 = radius(from, angle)
            val r2 = radius((from + 1) % 5, angle)
            val r = (r1 * (1 - t) + r2 * t) * base
            val point = Offset(
                cx + (cos(angle) * r).toFloat(),
                cy + (sin(angle) * r).toFloat()
            )
            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        drawPath(path, color)
    }
}
