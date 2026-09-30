package com.aess.gymflow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@Composable
fun SwipeToCompleteControl(
    resetKey: String,
    enabled: Boolean,
    onComplete: () -> Unit,
    isFinalSet: Boolean = false,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val latestOnComplete by rememberUpdatedState(onComplete)
    val latestEnabled by rememberUpdatedState(enabled)
    var dragOffset by remember(resetKey) { mutableFloatStateOf(0f) }
    var settleJob by remember(resetKey) { mutableStateOf<Job?>(null) }
    var locked by remember(resetKey) { mutableStateOf(false) }
    DisposableEffect(resetKey) { onDispose { settleJob?.cancel() } }

    BoxWithConstraints(modifier.fillMaxWidth().height(78.dp)) {
        val density = LocalDensity.current
        val thumbSize = 62.dp
        val horizontalInset = 8.dp
        val maxDragPx = with(density) { (maxWidth - thumbSize - horizontalInset * 2).toPx().coerceAtLeast(1f) }
        val usable = enabled && !locked
        val actionLabel = gs(language, R.string.swipe_to_complete_set)
        val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = .20f)
        fun settle(target: Float, complete: Boolean = false) {
            settleJob?.cancel()
            settleJob = scope.launch {
                animate(dragOffset, target, animationSpec = GymGlowMotion.fastSpatial()) { value, _ ->
                    dragOffset = value.coerceIn(0f, maxDragPx)
                }
                if (complete) {
                    if (latestEnabled) latestOnComplete()
                    else { locked = false; dragOffset = 0f }
                }
            }
        }
        fun complete() {
            if (!latestEnabled || locked) return
            locked = true
            haptic.performHapticFeedback(if (isFinalSet) HapticFeedbackType.Confirm else HapticFeedbackType.GestureEnd)
            settle(maxDragPx, complete = true)
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = actionLabel
                        if (!usable) disabled()
                        onClick(actionLabel) { if (usable) { complete(); true } else false }
                    }
                    .pointerInput(resetKey, usable, maxDragPx) {
                        if (!usable) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { settleJob?.cancel() },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                dragOffset = (dragOffset + amount).coerceIn(0f, maxDragPx)
                            },
                            onDragCancel = {
                                if (!locked) settle(0f)
                            },
                            onDragEnd = {
                                if (dragOffset >= maxDragPx * .85f && !locked) complete()
                                else if (!locked) settle(0f)
                            }
                        )
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(fillColor, size = size.copy(width = size.width * (dragOffset / maxDragPx).coerceIn(0f, 1f)))
                        }
                )
                Row(
                    Modifier.align(Alignment.Center).graphicsLayer {
                        alpha = (1f - (dragOffset / maxDragPx) * .55f).coerceIn(.45f, 1f)
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        gs(language, R.string.swipe_to_complete_set),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp))
                }
                Surface(
                    modifier = Modifier
                        .padding(horizontal = horizontalInset, vertical = 8.dp)
                        .size(thumbSize)
                        .graphicsLayer { translationX = dragOffset },
                    shape = CircleShape,
                    color = if (locked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (locked) Icons.Rounded.Check else Icons.Rounded.ArrowForward, null, Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun WorkoutSetInputCard(
    exercise: Exercise,
    setIndex: Int,
    weightText: String,
    onWeightChange: (String) -> Unit,
    effortText: String,
    onEffortChange: (String) -> Unit,
    timed: Boolean
) {
    val language = LocalAppLanguage.current
    ExpressiveCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedContent(setIndex, transitionSpec = { numberMotion(targetState > initialState) }, label = "workout_set_number") { current ->
                Text(gs(language, R.string.set_of, current + 1, exercise.sets),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            val summary = when {
                timed -> gs(language, R.string.seconds_count, effortText.ifBlank { defaultEffort(exercise) })
                exercise.weightLabel != null -> "${weightText.ifBlank { "—" }} ${gs(language, R.string.kg)} × ${effortText.ifBlank { defaultEffort(exercise) }}"
                else -> gs(language, R.string.reps_count, effortText.ifBlank { defaultEffort(exercise) })
            }
            Text(summary, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (exercise.weightLabel != null && !timed) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { onWeightChange(numericWorkout(it)) },
                        modifier = Modifier.weight(1f),
                        label = { Text(gs(language, R.string.weight_kg)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )
                }
                OutlinedTextField(
                    value = effortText,
                    onValueChange = { onEffortChange(it.filter(Char::isDigit).take(4)) },
                    modifier = Modifier.weight(1f),
                    label = { Text(if (timed) gs(language, R.string.seconds) else gs(language, R.string.reps)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
            }
        }
    }
}

@Composable
fun WorkoutRestScreen(
    day: WorkoutDay,
    exerciseIndex: Int,
    setIndex: Int,
    completedSets: List<CompletedSet>,
    restLeft: Int,
    restPaused: Boolean = false,
    onFinishRest: () -> Unit,
    onPauseRest: () -> Unit = {},
    onResumeRest: () -> Unit = {},
    onRestart: () -> Unit = {},
    onSetDuration: (Int) -> Unit = {},
    onAdd30: () -> Unit
) {
    val language = LocalAppLanguage.current
    val haptic = LocalHapticFeedback.current
    val exercise = day.exercises[exerciseIndex]
    val previous = completedSets.lastOrNull { it.exerciseId == exercise.id }
    val effortSummary = when {
        workoutIsTimed(exercise) -> previous?.durationSec?.let { gs(language, R.string.seconds_count, it.toString()) } ?: exerciseReps(exercise, language)
        exercise.weightLabel != null && previous?.weightKg != null -> {
            val weight = if (previous.weightKg % 1.0 == 0.0) previous.weightKg.toInt().toString() else String.format(java.util.Locale.US, "%.1f", previous.weightKg)
            val reps = previous.reps?.toString() ?: defaultEffort(exercise)
            "$weight ${gs(language, R.string.kg)} × $reps"
        }
        previous?.reps != null -> gs(language, R.string.reps_count, previous.reps.toString())
        else -> exerciseReps(exercise, language)
    }
    val presets = listOf(30, 60, 90, 120, 180)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Timer, null, Modifier.size(42.dp)) }
        }
        Spacer(Modifier.height(18.dp))
        Text(gs(language, if (restPaused) R.string.rest_paused else R.string.rest), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        val timerColor by animateColorAsState(
            if (!restPaused && restLeft in 1..5) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            GymGlowMotion.defaultEffects(), label = "rest_urgency"
        )
        val timerText = String.format(java.util.Locale.US, "%02d:%02d", restLeft.coerceAtLeast(0) / 60, restLeft.coerceAtLeast(0) % 60)
        Row(verticalAlignment = Alignment.CenterVertically) {
            timerText.forEachIndexed { index, digit ->
                androidx.compose.runtime.key(index) {
                    AnimatedContent(digit, transitionSpec = { numberMotion(false) }, label = "rest_digit") { value ->
                        Text(value.toString(), fontSize = 58.sp, fontWeight = FontWeight.Medium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = timerColor)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { sec ->
                FilterChip(
                    selected = false,
                    onClick = { onSetDuration(sec) },
                    label = { Text("${sec}s") }
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        ExpressiveCard(Modifier.fillMaxWidth()) {
            Column {
                Text(gs(language, R.string.next_set), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(exerciseTitle(exercise, language), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(gs(language, R.string.set_of, setIndex + 1, exercise.sets), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(effortSummary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (restPaused) {
                FilledTonalButton(onClick = onResumeRest, modifier = Modifier.weight(1f)) { Text(gs(language, R.string.resume)) }
            } else {
                FilledTonalButton(onClick = onPauseRest, modifier = Modifier.weight(1f)) { Text(gs(language, R.string.pause)) }
            }
            OutlinedButton(onClick = onAdd30, modifier = Modifier.weight(1f)) { Text("+30") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRestart, modifier = Modifier.weight(1f)) { Text(gs(language, R.string.restart_timer)) }
            ExpressiveSurfaceButton(onClick = onFinishRest, modifier = Modifier.weight(1f)) {
                Text(gs(language, R.string.skip_rest), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WorkoutCompletedScreen(
    day: WorkoutDay,
    log: WorkoutLog,
    streakBefore: Int,
    streakAfter: Int,
    isNewStreakRecord: Boolean = false,
    priorLogs: List<WorkoutLog> = emptyList(),
    priorRecords: List<PersonalRecord> = emptyList(),
    currentRecords: List<PersonalRecord> = priorRecords,
    onRate: (Int) -> Unit,
    onContinue: () -> Unit
) {
    val language = LocalAppLanguage.current
    val haptic = LocalHapticFeedback.current
    var selectedMood by remember(log.id) { mutableStateOf<Int?>(null) }
    val streakIncreased = streakAfter > streakBefore
    val durationMin = (log.durationMillis / 60000L).toInt().coerceAtLeast(1)
    val xpGained = remember(log.id) { xpGainedForWorkout(priorLogs, log, priorRecords, currentRecords) }
    val levelBefore = remember(log.id) { computeLevel(computeTotalXp(priorLogs, priorRecords)) }
    val levelAfter = remember(log.id) { computeLevel(computeTotalXp(priorLogs + log, currentRecords)) }
    val newAchievements = remember(log.id) {
        newlyUnlockedAchievements(priorLogs, priorLogs + log, priorRecords, currentRecords)
    }
    val leveledUp = levelAfter.level > levelBefore.level

    val newPrs = remember(log.id, language) { newPersonalRecordTitles(priorLogs, log, day, language) }
    // The final swipe already confirms the action; do not vibrate again on composition.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 26.dp, vertical = 12.dp)) {
                ExpressiveSurfaceButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(gs(language, R.string.continue_action), fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RewardReveal(log.id, 0, emphasized = true) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text(gs(language, R.string.workout_completed_label), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(workoutCompactTitle(day, language), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
            RewardReveal(log.id, 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResultChip(Icons.Rounded.Timer, gs(language, R.string.minutes_short, durationMin))
                    ResultChip(Icons.Rounded.Check, gs(language, R.string.sets_short, log.completedSets))
                }
            }
            if (xpGained > 0) {
                RewardReveal(log.id, 2) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(gs(language, R.string.xp_gained, xpGained), Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(gs(language, R.string.level_xp_compact, levelAfter.level, levelAfter.xpIntoLevel, levelAfter.xpForNextLevel),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (leveledUp) {
                    RewardReveal(log.id, 3, emphasized = true) {
                        Text(gs(language, R.string.level_up, levelAfter.level), fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (streakAfter > 0) {
                RewardReveal(log.id, if (leveledUp) 4 else 3, emphasized = isNewStreakRecord) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MotionStreakBadge(streakAfter, " " + gs(language, R.string.days_short_suffix),
                            before = if (streakIncreased) streakBefore else streakAfter, event = log.id, record = isNewStreakRecord,
                            revealOrder = if (leveledUp) 4 else 3)
                        if (streakIncreased) {
                            Text(gs(language, if (isNewStreakRecord) R.string.new_streak_record else R.string.streak_extended),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (newAchievements.isNotEmpty() || newPrs.isNotEmpty()) {
                RewardReveal(log.id, 5) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        newAchievements.take(2).forEach { ach ->
                            Text("${ach.icon} ${gs(language, ach.titleRes)}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        newPrs.take(3).forEach { title ->
                            Text(gs(language, R.string.new_pr_label, title), fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(gs(language, R.string.how_was_your_workout), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val moods = listOf(
                    "😞" to gs(language, R.string.mood_terrible), "😐" to gs(language, R.string.mood_bad),
                    "🙂" to gs(language, R.string.mood_ok), "😍" to gs(language, R.string.mood_great),
                    "🔥" to gs(language, R.string.mood_fire)
                )
                moods.forEachIndexed { index, (emoji, label) ->
                    val rating = index + 1
                    val isSelected = selectedMood == rating
                    val moodColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        GymGlowMotion.defaultEffects(), label = "mood_color"
                    )
                    val moodScale = animateFloatAsState(if (isSelected) 1.04f else 1f, GymGlowMotion.fastSpatial(), label = "mood_scale")
                    Surface(shape = CircleShape, color = moodColor, modifier = Modifier.size(48.dp)
                        .clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.RadioButton) {
                            if (selectedMood != rating) {
                                selectedMood = rating
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onRate(rating)
                            }
                        }.semantics { contentDescription = label; selected = isSelected }) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 20.sp, modifier = Modifier.graphicsLayer {
                                scaleX = moodScale.value; scaleY = moodScale.value
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PostWorkoutExpressiveLoader(
    day: WorkoutDay,
    log: WorkoutLog,
    onPersistAndUpdate: suspend (WorkoutLog, (Int) -> Unit) -> Unit,
    onHome: () -> Unit
) {
    val language = LocalAppLanguage.current
    val messages = listOf(
        gs(language, R.string.saving_workout),
        gs(language, R.string.updating_statistics),
        gs(language, R.string.checking_personal_records),
        gs(language, R.string.calculating_training_load),
        gs(language, R.string.preparing_home)
    )
    var stage by remember(log.id) { mutableIntStateOf(0) }
    var persisted by remember(log.id) { mutableStateOf(false) }

    var retry by remember(log.id) { mutableIntStateOf(0) }
    var saveFailed by remember(log.id) { mutableStateOf(false) }

    LaunchedEffect(log.id, retry) {
        stage = 0
        saveFailed = false
        if (!persisted) {
            try {
                onPersistAndUpdate(log) { realStage ->
                    stage = realStage.coerceIn(0, messages.lastIndex)
                }
                persisted = true
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                saveFailed = true
                return@LaunchedEffect
            }
        }
        stage = messages.lastIndex
        onHome()
    }

    Box(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (saveFailed) {
                Text(gs(language, R.string.workout_save_failed))
                TextButton(onClick = { retry++ }) { Text(gs(language, R.string.retry_save)) }
            } else GymFlowMorphingShape(Modifier.size(132.dp))
            Spacer(Modifier.height(30.dp))
            AnimatedContent(stage, transitionSpec = { effectsMotion() }, label = "post_workout_stage") { index ->
                Text(messages[index], fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Text(workoutCompactTitle(day, language), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

fun workoutIsTimed(exercise: Exercise): Boolean {
    val raw = (exercise.reps + " " + exercise.title).lowercase()
    return raw.contains("сек") || raw.contains("sec") || raw.contains("мин") || raw.contains("min") || raw.contains("планк")
}

fun defaultEffort(exercise: Exercise): String = Regex("\\d+").find(exercise.reps)?.value ?: "10"

private fun numericWorkout(raw: String): String {
    var dotSeen = false
    return buildString {
        raw.replace(',', '.').forEach { c ->
            if (c.isDigit() && length < 6) append(c)
            else if (c == '.' && !dotSeen && isNotEmpty()) { append(c); dotSeen = true }
        }
    }
}


fun workoutMuscleLine(exercise: Exercise, language: String): String {
    val ru = mapOf(
        "su_bench" to "Грудь • Трицепс • Передняя дельта",
        "su_incline" to "Верх груди • Трицепс • Передняя дельта",
        "tu_lats" to "Широчайшие • Бицепс • Предплечья",
        "tu_row" to "Спина • Бицепс • Задняя дельта",
        "fr_legpress" to "Квадрицепс • Ягодицы",
        "fr_hip" to "Ягодицы • Задняя поверхность бедра",
        "tu_curl" to "Бицепс • Предплечья",
        "tu_latraise" to "Средняя дельта • Плечи",
        "su_pushdown" to "Трицепс",
        "fr_calf" to "Икры • Голень"
    )
    val en = mapOf(
        "su_bench" to "Chest • Triceps • Front delts",
        "su_incline" to "Upper chest • Triceps • Front delts",
        "tu_lats" to "Lats • Biceps • Forearms",
        "tu_row" to "Back • Biceps • Rear delts",
        "fr_legpress" to "Quads • Glutes",
        "fr_hip" to "Glutes • Hamstrings",
        "tu_curl" to "Biceps • Forearms",
        "tu_latraise" to "Side delts • Shoulders",
        "su_pushdown" to "Triceps",
        "fr_calf" to "Calves • Lower legs"
    )
    return (if (language == "EN") en else ru)[exercise.id] ?: exerciseMuscle(exercise, language)
}

@Composable
fun TechniqueCard(exercise: Exercise) {
    val language = LocalAppLanguage.current
    val cues = exerciseCues(exercise, language)
    val instructions = cues.take(5).ifEmpty {
        listOf(gs(language, R.string.move_smoothly_and_under_control))
    }
    val mistakes = exerciseMistakes(exercise, language)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExpressiveCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(gs(language, R.string.quick_instructions), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                instructions.forEachIndexed { index, cue ->
                    Text("${index + 1}. $cue", fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
        }
        ExpressiveCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(gs(language, R.string.common_mistakes), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                mistakes.take(5).forEach { Text("• $it", fontSize = 15.sp, lineHeight = 21.sp) }
            }
        }
    }
}

private fun exerciseMistakes(exercise: Exercise, language: String): List<String> {
    val knownRu = mapOf(
        "su_bench" to listOf("Отрыв ягодиц от скамьи", "Слишком сильный прогиб в пояснице", "Локти разведены строго в стороны", "Отбивание штанги от груди", "Потеря контроля при опускании"),
        "su_incline" to listOf("Слишком вертикальный угол скамьи", "Плечи уходят вперёд", "Отрыв стоп от пола", "Рывок из нижней точки", "Слишком быстрый спуск"),
        "tu_lats" to listOf("Раскачивание корпуса", "Тяга руками вместо локтей", "Сильное отклонение назад", "Бросание веса вверх", "Сведение плеч к ушам"),
        "tu_row" to listOf("Округление поясницы", "Рывок корпусом", "Плечи поднимаются к ушам", "Слишком короткая амплитуда", "Бросание рукояти вперёд"),
        "fr_legpress" to listOf("Отрыв поясницы от спинки", "Колени заваливаются внутрь", "Слишком глубокое опускание таза", "Резкое выпрямление коленей", "Толчок носками вместо всей стопы"),
        "fr_hip" to listOf("Переразгибание поясницы", "Толчок носками вместо пяток", "Колени заваливаются внутрь", "Слишком быстрый темп", "Нет фиксации верхней точки"),
        "tu_curl" to listOf("Раскачивание корпуса", "Локти уходят вперёд", "Рывок в начале подъёма", "Неполное опускание", "Слишком большой рабочий вес"),
        "tu_latraise" to listOf("Подъём плеч к ушам", "Сильный мах корпусом", "Кисти значительно выше локтей", "Слишком тяжёлый вес", "Быстрое падение рук вниз"),
        "su_pushdown" to listOf("Локти отходят от корпуса", "Движение плечом вместо предплечья", "Наклон всем корпусом", "Рывок вниз", "Бросание веса вверх"),
        "fr_calf" to listOf("Пружинистые движения", "Неполная амплитуда", "Слишком быстрый спуск", "Перенос веса на внешний край стопы", "Отсутствие паузы наверху")
    )
    val knownEn = mapOf(
        "su_bench" to listOf("Lifting the hips off the bench", "Excessive lower-back arch", "Flaring elbows straight out", "Bouncing the bar off the chest", "Losing control on the descent"),
        "su_incline" to listOf("Bench angle set too upright", "Shoulders rolling forward", "Feet lifting from the floor", "Jerking out of the bottom", "Lowering too quickly"),
        "tu_lats" to listOf("Swinging the torso", "Pulling mostly with the hands", "Leaning too far back", "Letting the stack snap upward", "Shrugging the shoulders"),
        "tu_row" to listOf("Rounding the lower back", "Jerking with the torso", "Shrugging the shoulders", "Using too short a range", "Letting the handle snap forward"),
        "fr_legpress" to listOf("Lower back lifting from the pad", "Knees collapsing inward", "Lowering too deep for hip control", "Locking the knees aggressively", "Pushing only through the toes"),
        "fr_hip" to listOf("Overextending the lower back", "Driving through the toes", "Knees collapsing inward", "Moving too quickly", "Skipping the top pause"),
        "tu_curl" to listOf("Swinging the torso", "Elbows drifting forward", "Jerking the weight up", "Not lowering fully", "Using too much weight"),
        "tu_latraise" to listOf("Shrugging the shoulders", "Swinging the torso", "Hands rising far above elbows", "Using too much weight", "Dropping the arms too quickly"),
        "su_pushdown" to listOf("Elbows moving away from the torso", "Moving the upper arm", "Leaning with the whole body", "Jerking downward", "Letting the weight snap back"),
        "fr_calf" to listOf("Bouncing", "Using a short range", "Dropping too quickly", "Rolling onto the outer foot", "Skipping the top pause")
    )
    (if (language == "EN") knownEn else knownRu)[exercise.id]?.let { return it }

    val cues = exerciseCues(exercise, language)
    return cues.map { cue ->
        if (language == "EN") {
            when {
                cue.startsWith("Do not", true) || cue.startsWith("No ", true) -> cue
                else -> "Losing the position: ${cue.replaceFirstChar { it.lowercase() }}"
            }
        } else {
            when {
                cue.startsWith("Не ", true) || cue.startsWith("Без ", true) -> cue
                else -> "Потеря техники: ${cue.replaceFirstChar { it.lowercase() }}"
            }
        }
    }.distinct().take(5).ifEmpty {
        listOf(gs(language, R.string.jerking_or_losing_control_of_the_movement))
    }
}
