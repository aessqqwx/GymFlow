package com.aess.gymflow

import androidx.compose.runtime.getValue

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Compact weekly day selector used on the home screen.
 * Shows all 7 days as small rounded chips; the selected day is highlighted,
 * and today gets a subtle dot indicator so it stays recognizable even when
 * it isn't the selected day.
 */
@Composable
fun DaySelector(
    language: String,
    selected: DayOfWeek,
    workoutDays: Set<DayOfWeek> = emptySet(),
    onSelect: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember(language) { LocalDate.now().dayOfWeek }
    val days = DayOfWeek.entries
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { day ->
            DayChip(
                label = dayShort(day, language).take(2),
                isSelected = day == selected,
                isToday = day == today,
                hasWorkout = day in workoutDays,
                onClick = { onSelect(day) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DayChip(
    label: String,
    isSelected: Boolean,
    isToday: Boolean,
    hasWorkout: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = GymGlowMotion.defaultEffects(),
        label = "day_chip_bg"
    )
    val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val height by animateDpAsState(if (isSelected) 56.dp else 50.dp, GymGlowMotion.fastSpatial(), label = "day_chip_height")

    Surface(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .semantics { selected = isSelected }
            .clickable(onClick = onClick),
        color = bg,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, color = fg, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 12.sp)
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .size(4.dp)
                    .background(
                        when {
                            isSelected -> fg.copy(alpha = .9f)
                            isToday -> MaterialTheme.colorScheme.primary
                            hasWorkout -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f)
                            else -> Color.Transparent
                        },
                        CircleShape
                    )
            )
        }
    }
}

/** Small "online"-style status dot + label, used under a profile name. */
@Composable
fun StatusLabel(text: String, modifier: Modifier = Modifier, active: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .background(if (active) Color(0xFF4CD07A) else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * A single compact profile action row (replaces the old 3 oversized square cards).
 * Icon in a small rounded tile on the left, label, chevron on the right.
 */
@Composable
fun ProfileActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = .14f)) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(13.dp))
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            androidx.compose.material3.Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}
