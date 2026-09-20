package com.aess.gymflow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeToCompleteControl(
    resetKey: String,
    enabled: Boolean,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember(resetKey) { Animatable(0f) }
    var locked by remember(resetKey) { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxWidth().height(78.dp)) {
        val density = LocalDensity.current
        val thumbSize = 62.dp
        val horizontalInset = 8.dp
        val maxDragPx = with(density) { (maxWidth - thumbSize - horizontalInset * 2).toPx().coerceAtLeast(1f) }
        val progress = (offset.value / maxDragPx).coerceIn(0f, 1f)
        val usable = enabled && !locked

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = gs(language, R.string.swipe_to_complete_set) }
                    .pointerInput(resetKey, usable, maxDragPx) {
                        if (!usable) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                scope.launch {
                                    offset.snapTo((offset.value + amount).coerceIn(0f, maxDragPx))
                                }
                            },
                            onDragCancel = {
                                scope.launch { offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            },
                            onDragEnd = {
                                if (offset.value >= maxDragPx * .85f && !locked) {
                                    locked = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        offset.animateTo(maxDragPx, spring(stiffness = Spring.StiffnessMedium))
                                        onComplete()
                                    }
                                } else {
                                    scope.launch { offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                }
                            }
                        )
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .20f))
                )
                Row(
                    Modifier.align(Alignment.Center).alpha((1f - progress * .72f).coerceAtLeast(.22f)),
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
                        .offset { IntOffset(offset.value.roundToInt(), 0) },
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
            Text(
                gs(language, R.string.set_of, setIndex + 1, exercise.sets),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
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
    onFinishRest: () -> Unit,
    onAdd30: () -> Unit
) {
    val language = LocalAppLanguage.current
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
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Timer, null, Modifier.size(42.dp)) }
        }
        Spacer(Modifier.height(18.dp))
        Text(gs(language, R.string.rest), fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            String.format(java.util.Locale.US, "%02d:%02d", restLeft / 60, restLeft % 60),
            fontSize = 58.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(22.dp))
        ExpressiveCard(Modifier.fillMaxWidth()) {
            Column {
                Text(gs(language, R.string.next_set), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(exerciseTitle(exercise, language), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    gs(language, R.string.set_of, setIndex + 1, exercise.sets),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(effortSummary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        ExpressiveSurfaceButton(onClick = onFinishRest, modifier = Modifier.fillMaxWidth()) {
            Text(gs(language, R.string.finish_rest), fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onAdd30) { Text(gs(language, R.string.text_30_sec)) }
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

    LaunchedEffect(log.id) {
        stage = 0
        if (!persisted) {
            onPersistAndUpdate(log) { realStage ->
                stage = realStage.coerceIn(0, messages.lastIndex)
            }
            persisted = true
        }
        stage = messages.lastIndex
        onHome()
    }

    Box(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GymFlowMorphingShape(Modifier.size(132.dp))
            Spacer(Modifier.height(30.dp))
            AnimatedContent(stage, label = "post_workout_stage") { index ->
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
