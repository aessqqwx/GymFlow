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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    onHome: () -> Unit,
    onMoodRated: (Long, Int) -> Unit = { _, _ -> },
    priorLogs: List<WorkoutLog> = emptyList(),
    currentRecords: List<PersonalRecord> = emptyList()
) {
    val language = LocalAppLanguage.current
    val haptic = LocalHapticFeedback.current
    if (day.exercises.isEmpty()) {
        Box(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(gs(language, R.string.no_workouts_yet), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(workoutCompactTitle(day, language), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                TextButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, gs(language, R.string.back))
                    Spacer(Modifier.width(4.dp))
                    Text(gs(language, R.string.back))
                }
            }
        }
        LaunchedEffect(day.key) { onStateChanged(null) }
        return
    }

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
    var isPaused by remember(day.key) { mutableStateOf(initialState.isPaused) }
    var restRemainingMs by remember(day.key) { mutableLongStateOf(initialState.restRemainingMs) }
    var exerciseNotes by remember(day.key) { mutableStateOf(initialState.exerciseNotes) }
    val startedAt = remember(day.key) { initialState.startedAt }
    val logsBeforeSession = remember(day.key, startedAt) {
        priorLogs.filterNot { it.dayKey == day.key && it.startedAt == startedAt }
    }
    val recordsBeforeSession = remember(day.key, startedAt) { currentRecords }
    var now by remember { mutableLongStateOf(nowAtOpen) }
    var showPlan by remember { mutableStateOf(false) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var transitionMessage by remember { mutableStateOf<String?>(null) }
    var lastTransitionMessage by remember { mutableStateOf("") }
    SideEffect { transitionMessage?.let { lastTransitionMessage = it } }
    var showNoteEditor by remember { mutableStateOf(false) }

    fun snapshot(): ActiveWorkoutState = ActiveWorkoutState(
        dayKey = day.key,
        currentExerciseIndex = exerciseIndex,
        currentSetIndex = setIndex,
        completedSets = completedSets,
        restUntil = restUntil,
        restState = restState,
        workoutCompleted = workoutCompleted,
        startedAt = startedAt,
        isPaused = isPaused,
        restRemainingMs = restRemainingMs,
        exerciseNotes = exerciseNotes,
        workoutDay = day
    )

    fun applyState(state: ActiveWorkoutState, persist: Boolean = true) {
        exerciseIndex = state.currentExerciseIndex
        setIndex = state.currentSetIndex
        completedSets = state.completedSets
        restUntil = state.restUntil
        restState = state.restState
        workoutCompleted = state.workoutCompleted
        isPaused = state.isPaused
        restRemainingMs = state.restRemainingMs
        exerciseNotes = state.exerciseNotes
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
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
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
            completedSetDetails = completedSets,
            exerciseNotes = exerciseNotes.filterValues { it.isNotBlank() }
        )
        val streakBefore = remember(log.id) { computeWorkoutStreak(logsBeforeSession) }
        var persistedDone by remember(log.id) { mutableStateOf(false) }
        if (!persistedDone) {
            PostWorkoutExpressiveLoader(
                day = day,
                log = log,
                onPersistAndUpdate = onFinished,
                onHome = { persistedDone = true }
            )
        } else {
            val streakAfter = remember(log.id) { computeWorkoutStreak(mergeCompletedWorkout(logsBeforeSession, log)) }
            WorkoutCompletedScreen(
                day = day,
                log = log,
                streakBefore = streakBefore.current,
                streakAfter = streakAfter.current,
                isNewStreakRecord = streakAfter.current > streakBefore.best,
                priorLogs = logsBeforeSession,
                priorRecords = recordsBeforeSession,
                currentRecords = currentRecords,
                onRate = { rating -> onMoodRated(log.id, rating) },
                onContinue = onHome
            )
        }
        return
    }

    val exercise = day.exercises[exerciseIndex]
    val totalSets = day.exercises.sumOf { it.sets }
    val progress = completedSets.size.toFloat() / totalSets.coerceAtLeast(1).toFloat()
    val restLeft = when {
        restState == RestState.RESTING && restUntil > now -> ((restUntil - now + 999L) / 1000L).toInt()
        restState == RestState.PAUSED && restRemainingMs > 0L -> ((restRemainingMs + 999L) / 1000L).toInt()
        else -> 0
    }

    fun finishRest() {
        now = System.currentTimeMillis()
        applyState(finishWorkoutRest(snapshot()))
    }

    if ((restState == RestState.RESTING || restState == RestState.PAUSED) && restLeft > 0 && !isPaused) {
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
                    restPaused = restState == RestState.PAUSED,
                    onFinishRest = ::finishRest,
                    onPauseRest = { applyState(pauseRestTimer(snapshot())) },
                    onResumeRest = { applyState(resumeRestTimer(snapshot())) },
                    onRestart = { applyState(setRestDuration(snapshot(), 90)) },
                    onSetDuration = { sec -> applyState(setRestDuration(snapshot(), sec)) },
                    onAdd30 = {
                        val base = if (restState == RestState.PAUSED) System.currentTimeMillis() + restRemainingMs else restUntil
                        applyState(snapshot().copy(restUntil = base + 30_000L, restState = RestState.RESTING, restRemainingMs = 0L))
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
    val historyHint = remember(exercise.id, priorLogs) {
        priorLogs.asSequence()
            .flatMap { log -> log.completedSetDetails.asSequence().map { set -> log.finishedAt to set } }
            .filter { it.second.exerciseId == exercise.id }
            .maxByOrNull { it.first }
            ?.second
    }
    val seedWeight = previousWeight ?: historyHint?.weightKg
    val seedEffort = when {
        timed -> historyHint?.durationSec?.toString()
        else -> historyHint?.reps?.toString()
    }
    var weightText by remember(exercise.id, setIndex) { mutableStateOf(seedWeight?.let(::trimNumber).orEmpty()) }
    var effortText by remember(exercise.id, setIndex) { mutableStateOf(seedEffort ?: defaultEffort(exercise)) }

    if (showNoteEditor) {
        val currentNote = exerciseNotes[exercise.id].orEmpty()
        var draft by remember(exercise.id) { mutableStateOf(currentNote) }
        AlertDialog(
            onDismissRequest = { showNoteEditor = false },
            title = { Text(gs(language, R.string.exercise_note)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(200) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text(gs(language, R.string.note)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = draft.trim()
                    val next = if (trimmed.isEmpty()) exerciseNotes - exercise.id else exerciseNotes + (exercise.id to trimmed)
                    exerciseNotes = next
                    onStateChanged(snapshot().copy(exerciseNotes = next))
                    showNoteEditor = false
                }) { Text(gs(language, R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNoteEditor = false }) { Text(gs(language, R.string.close)) }
            }
        )
    }

    var setCompleting by remember(day.key) { mutableStateOf(false) }
    fun completeSet() {
        if (setCompleting || restState == RestState.RESTING || restState == RestState.PAUSED || workoutCompleted || isPaused) return
        setCompleting = true
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
        setCompleting = false
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
                    enabled = restState == RestState.IDLE && !workoutCompleted && !isPaused && !setCompleting,
                    onComplete = ::completeSet,
                    isFinalSet = completedSets.size + 1 >= totalSets
                )
                Spacer(Modifier.height(9.dp))
                GymGlowProgressIndicator(
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
            Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 2.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Icon(Icons.Rounded.ArrowBack, gs(language, R.string.back))
                        Spacer(Modifier.width(4.dp))
                        Text(gs(language, R.string.back))
                    }
                    Spacer(Modifier.weight(1f))
                    if (isPaused) {
                        FilledTonalButton(onClick = { applyState(resumeWorkout(snapshot())) }) {
                            Text(gs(language, R.string.resume))
                        }
                    } else {
                        TextButton(onClick = { applyState(pauseWorkout(snapshot())) }) {
                            Text(gs(language, R.string.pause))
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { showNoteEditor = true }) {
                        Text(gs(language, R.string.note))
                    }
                    Spacer(Modifier.width(6.dp))
                    FilledTonalButton(onClick = { showPlan = true }) { Text(gs(language, R.string.plan)) }
                }
            }
            item {
                AnimatedContent(exerciseIndex, transitionSpec = { effectsMotion() }, label = "workout_exercise") { current ->
                    val headerExercise = day.exercises[current.coerceIn(day.exercises.indices)]
                    Column {
                        Text(
                            exerciseTitle(headerExercise, language).uppercase(if (language == "EN") Locale.ENGLISH else Locale("ru")),
                            fontSize = 27.sp,
                            lineHeight = 31.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            workoutMuscleLine(headerExercise, language),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item { TechniqueCard(exercise) }
            if (isPaused) {
                item {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            gs(language, R.string.workout_paused_hint),
                            Modifier.padding(14.dp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            if (exerciseNotes[exercise.id].orEmpty().isNotBlank()) {
                item {
                    Text(
                        "📝 " + exerciseNotes[exercise.id].orEmpty(),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
            item(key = "exercise_transition") {
                AnimatedVisibility(transitionMessage != null, enter = expandMotion(), exit = collapseMotion()) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(22.dp)) {
                        Text(transitionMessage ?: lastTransitionMessage,
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Medium)
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
