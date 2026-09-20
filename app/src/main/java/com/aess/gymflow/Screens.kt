package com.aess.gymflow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun WorkoutScreen(
    day: WorkoutDay,
    storedState: ActiveWorkoutState?,
    onStateChanged: (ActiveWorkoutState?) -> Unit,
    onBack: () -> Unit,
    onFinished: suspend (WorkoutLog, (Int) -> Unit) -> Unit,
    onHome: () -> Unit
) {
    val language = LocalAppLanguage.current
    val nowAtOpen = remember(day.key) { System.currentTimeMillis() }
    val initialState = remember(day.key, storedState?.startedAt) {
        normalizeActiveWorkoutState(
            day = day,
            state = storedState ?: createActiveWorkoutState(day, nowAtOpen),
            now = nowAtOpen
        )
    }

    var exerciseIndex by remember(day.key) { mutableIntStateOf(initialState.currentExerciseIndex) }
    var setIndex by remember(day.key) { mutableIntStateOf(initialState.currentSetIndex) }
    var completedSets by remember(day.key) { mutableStateOf(initialState.completedSets) }
    var restUntil by remember(day.key) { mutableLongStateOf(initialState.restUntil) }
    var restState by remember(day.key) { mutableStateOf(initialState.restState) }
    var workoutCompleted by remember(day.key) { mutableStateOf(initialState.workoutCompleted) }
    val startedAt = remember(day.key) { initialState.startedAt }
    var now by remember { mutableLongStateOf(nowAtOpen) }
    var showPlan by remember { mutableStateOf(false) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var transitionMessage by remember { mutableStateOf<String?>(null) }

    fun snapshot(): ActiveWorkoutState = ActiveWorkoutState(
        dayKey = day.key,
        currentExerciseIndex = exerciseIndex,
        currentSetIndex = setIndex,
        completedSets = completedSets,
        restUntil = restUntil,
        restState = restState,
        workoutCompleted = workoutCompleted,
        startedAt = startedAt
    )

    fun applyState(state: ActiveWorkoutState, persist: Boolean = true) {
        exerciseIndex = state.currentExerciseIndex
        setIndex = state.currentSetIndex
        completedSets = state.completedSets
        restUntil = state.restUntil
        restState = state.restState
        workoutCompleted = state.workoutCompleted
        if (persist) onStateChanged(state)
    }

    LaunchedEffect(day.key) {
        if (storedState == null || initialState != storedState) onStateChanged(initialState)
    }

    LaunchedEffect(restUntil, restState) {
        if (restState != RestState.RESTING || restUntil <= 0L) return@LaunchedEffect
        while (restState == RestState.RESTING) {
            val tick = System.currentTimeMillis()
            now = tick
            if (tick >= restUntil) {
                applyState(finishWorkoutRest(snapshot()))
                break
            }
            delay((restUntil - tick).coerceIn(100L, 1_000L))
        }
    }

    LaunchedEffect(transitionMessage) {
        if (transitionMessage != null) {
            delay(1_500L)
            transitionMessage = null
        }
    }

    if (showNowPlaying) NowPlayingSheet { showNowPlaying = false }

    if (workoutCompleted) {
        val finishedAt = completedSets.maxOfOrNull { it.completedAt } ?: System.currentTimeMillis()
        val log = WorkoutLog(
            id = startedAt,
            startedAt = startedAt,
            finishedAt = finishedAt,
            dayKey = day.key,
            title = workoutCompactTitle(day, language),
            completedSets = completedSets.size,
            durationMillis = (finishedAt - startedAt).coerceAtLeast(0L),
            exerciseIds = day.exercises.map { it.id },
            completedSetDetails = completedSets
        )
        PostWorkoutExpressiveLoader(
            day = day,
            log = log,
            onPersistAndUpdate = onFinished,
            onHome = onHome
        )
        return
    }

    val exercise = day.exercises[exerciseIndex]
    val totalSets = day.exercises.sumOf { it.sets }
    val progress = completedSets.size.toFloat() / totalSets.coerceAtLeast(1).toFloat()
    val restLeft = if (restState == RestState.RESTING && restUntil > now) ((restUntil - now + 999L) / 1000L).toInt() else 0

    fun finishRest() {
        now = System.currentTimeMillis()
        applyState(finishWorkoutRest(snapshot()))
    }

    if (restState == RestState.RESTING && restLeft > 0) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    MiniPlayerBar(onOpen = { showNowPlaying = true })
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                WorkoutRestScreen(
                    day = day,
                    exerciseIndex = exerciseIndex,
                    setIndex = setIndex,
                    completedSets = completedSets,
                    restLeft = restLeft,
                    onFinishRest = ::finishRest,
                    onAdd30 = {
                        val updated = snapshot().copy(restUntil = restUntil + 30_000L, restState = RestState.RESTING)
                        applyState(updated)
                    }
                )
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 6.dp)
                ) {
                    Icon(Icons.Rounded.ArrowBack, gs(language, R.string.back))
                    Spacer(Modifier.width(4.dp))
                    Text(gs(language, R.string.back))
                }
            }
        }
        return
    }

    val timed = workoutIsTimed(exercise)
    val previousWeight = completedSets.lastOrNull { it.exerciseId == exercise.id }?.weightKg
    var weightText by remember(exercise.id, setIndex) { mutableStateOf(previousWeight?.let(::trimNumber).orEmpty()) }
    var effortText by remember(exercise.id, setIndex) { mutableStateOf(defaultEffort(exercise)) }

    fun completeSet() {
        if (restState == RestState.RESTING || workoutCompleted) return
        val completedAt = System.currentTimeMillis()
        val beforeExerciseIndex = exerciseIndex
        val effort = effortText.toIntOrNull()
        val updated = advanceAfterCompletedSet(
            day = day,
            rawState = snapshot(),
            completedAt = completedAt,
            betweenExercisesRestSec = 180,
            weightKg = if (exercise.weightLabel != null && !timed) weightText.replace(',', '.').toDoubleOrNull() else null,
            reps = if (!timed) effort else null,
            durationSec = if (timed) effort else null
        )
        applyState(updated)
        if (updated.currentExerciseIndex != beforeExerciseIndex && !updated.workoutCompleted) {
            transitionMessage = gs(language, R.string.next_exercise) + exerciseTitle(day.exercises[updated.currentExerciseIndex], language)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                MiniPlayerBar(onOpen = { showNowPlaying = true })
                Spacer(Modifier.height(8.dp))
                SwipeToCompleteControl(
                    resetKey = "${day.key}:${exercise.id}:$setIndex",
                    enabled = restState == RestState.IDLE && !workoutCompleted,
                    onComplete = ::completeSet
                )
                Spacer(Modifier.height(9.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    gs(language, R.string.exercise_progress_count, exerciseIndex + 1, day.exercises.size),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 2.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Icon(Icons.Rounded.ArrowBack, gs(language, R.string.back))
                        Spacer(Modifier.width(4.dp))
                        Text(gs(language, R.string.back))
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = { showPlan = true }) { Text(gs(language, R.string.plan)) }
                }
            }
            item {
                Column {
                    Text(
                        exerciseTitle(exercise, language).uppercase(if (language == "EN") Locale.ENGLISH else Locale("ru")),
                        fontSize = 35.sp,
                        lineHeight = 39.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        workoutMuscleLine(exercise, language),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            item { TechniqueCard(exercise) }
            item {
                WorkoutSetInputCard(
                    exercise = exercise,
                    setIndex = setIndex,
                    weightText = weightText,
                    onWeightChange = { weightText = it },
                    effortText = effortText,
                    onEffortChange = { effortText = it },
                    timed = timed
                )
            }
            if (transitionMessage != null) {
                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(22.dp)) {
                        Text(
                            transitionMessage!!,
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    if (showPlan) PlanSheet(day = day, currentIndex = exerciseIndex, onDismiss = { showPlan = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanSheet(
    day: WorkoutDay,
    currentIndex: Int,
    onDismiss: () -> Unit
) {
    val language = LocalAppLanguage.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(gs(language, R.string.workout_plan), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(workoutTitle(day, language), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            day.exercises.forEachIndexed { index, ex ->
                val done = index < currentIndex
                val current = index == currentIndex
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = when {
                                done -> MaterialTheme.colorScheme.primary
                                current -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (done) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                                else Text("${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(exerciseMuscle(ex, language), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(exerciseTitle(ex, language), fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}


private fun trimNumber(value: Double): String {
    val whole = value.toLong()
    return if (value == whole.toDouble()) whole.toString() else String.format("%.1f", value)
}
