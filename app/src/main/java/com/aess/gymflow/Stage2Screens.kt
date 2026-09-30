package com.aess.gymflow

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun workoutMinutes(day: WorkoutDay, preferredMinutes: Int = 0): Int {
    if (preferredMinutes in 15..180) return preferredMinutes
    val workSeconds = day.exercises.sumOf { it.sets * 45 }
    val restSeconds = day.exercises.sumOf { ex -> (ex.sets - 1).coerceAtLeast(0) * ex.restSec }
    return ((workSeconds + restSeconds) / 60.0).roundToInt().coerceAtLeast(12)
}

private fun daysUntil(from: java.time.DayOfWeek, to: java.time.DayOfWeek): Int =
    ((to.value - from.value) + 7) % 7

private fun fitnessGoalLabel(id: String, l: String): String = when (id) {
    "MUSCLE" -> gs(l, R.string.fitness_goal_muscle_title)
    "STRENGTH" -> gs(l, R.string.fitness_goal_strength_title)
    "ENDURANCE" -> gs(l, R.string.fitness_goal_endurance_title)
    "FAT_LOSS" -> gs(l, R.string.fitness_goal_fat_loss_title)
    "GENERAL" -> gs(l, R.string.fitness_goal_general_title)
    else -> id
}

private fun tipForProfile(profile: UserProfile, today: LocalDate): String {
    val l = profile.appLanguage
    val tips = gsa(l, R.array.daily_tips)
    if (tips.isEmpty()) return ""
    val goalBias = when {
        "STRENGTH" in profile.fitnessGoals -> 1
        "MUSCLE" in profile.fitnessGoals -> 2
        "ENDURANCE" in profile.fitnessGoals -> 3
        "FAT_LOSS" in profile.fitnessGoals -> 4
        "GENERAL" in profile.fitnessGoals -> 5
        else -> 0
    }
    return tips[Math.floorMod(today.dayOfYear + goalBias * 7, tips.size)]
}

private fun dateLabel(date: LocalDate, l: String): String = if (l == "EN") {
    gs(l, R.string.date_month_day, monthName(date.monthValue, l), date.dayOfMonth)
} else {
    gs(l, R.string.date_day_month, date.dayOfMonth, monthName(date.monthValue, l))
}
private fun millisDate(millis: Long, l: String): String { val d=Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate(); return dateLabel(d,l) }
private fun durationLabel(ms: Long, l: String): String {
    val mins = (ms / 60000).coerceAtLeast(1)
    return gs(l, R.string.minutes_short, mins)
}
private fun nutritionOnDate(entries:List<NutritionEntry>, date:LocalDate):List<NutritionEntry> = entries.filter { Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()==date }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    activeState: ActiveWorkoutState?,
    logs: List<WorkoutLog>,
    profile: UserProfile,
    nutrition: List<NutritionEntry>,
    progress: List<ProgressEntry>,
    goals: List<GoalEntry>,
    records: List<PersonalRecord>,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWorkout: (WorkoutDay) -> Unit,
    onAddNutrition: (NutritionEntry) -> Unit,
    onOpenMeasurements: () -> Unit
) {
    val l=profile.appLanguage
    val today=LocalDate.now()
    val workouts=remember(profile) { workoutsFor(profile) }
    val workoutDaysOfWeek = remember(workouts) { workouts.map { it.dayOfWeek }.toSet() }
    var selectedDay by remember(today) { mutableStateOf(today.dayOfWeek) }
    val todayWorkout=workouts.firstOrNull{it.dayOfWeek==today.dayOfWeek}
    val resumableWorkout = activeState?.takeUnless { it.workoutCompleted }?.let { state -> workouts.firstOrNull { it.key == state.dayKey } ?: workoutByKey(state.dayKey) }
    val isRestDay = todayWorkout == null
    val selectedWorkout = workouts.firstOrNull { it.dayOfWeek == selectedDay }
    val nextWorkout = workouts
        .map { it to daysUntil(today.dayOfWeek, it.dayOfWeek).let { d -> if (d == 0) 7 else d } }
        .filter { it.second in 1..7 }
        .minByOrNull { it.second }
        ?.first
    val sessionMins = profile.sessionDurationMinutes.coerceIn(15, 180)
    val yesterday=today.minusDays(1)
    val yesterdayProtein=nutritionOnDate(nutrition,yesterday).sumOf{it.proteinG}.coerceAtLeast(0.0)
    val proteinMissing=yesterdayProtein<=0.0
    val latestProgress=progress.maxByOrNull{it.createdAt}
    val measurementCutoff = today.minusMonths(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val measurementsDue=latestProgress==null || latestProgress.createdAt <= measurementCutoff
    var showProteinSheet by remember{mutableStateOf(false)}
    var proteinInput by remember{mutableStateOf("")}
    var showWaterSheet by remember{mutableStateOf(false)}
    var showQuickProteinSheet by remember{mutableStateOf(false)}

    val streak = remember(logs) { computeWorkoutStreak(logs) }
    val todayEntries = remember(nutrition, today) { nutritionOnDate(nutrition, today) }
    val todayWaterMl = todayEntries.sumOf { it.waterMl }.coerceAtLeast(0)
    val todayProteinG = todayEntries.sumOf { it.proteinG }.coerceAtLeast(0.0)
    val workoutDoneToday = logs.any { Instant.ofEpochMilli(it.finishedAt).atZone(ZoneId.systemDefault()).toLocalDate() == today }
    val waterGoalMet = todayWaterMl >= profile.waterGoalMl.coerceAtLeast(1)
    val proteinGoalMet = profile.proteinGoal <= 0 || todayProteinG >= profile.proteinGoal
    val dayProgressDone = listOf(
        if (isRestDay) true else workoutDoneToday,
        waterGoalMet,
        proteinGoalMet
    ).count { it }
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val weekEnd = weekStart.plusDays(6)
    val weekLogsDone = remember(logs, weekStart) {
        logs.count { log ->
            val d = Instant.ofEpochMilli(log.finishedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            !d.isBefore(weekStart) && !d.isAfter(weekEnd)
        }
    }
    val weekPlanned = profile.trainingDays.size.coerceIn(1, 7).let { if (it == 0) workouts.size.coerceAtLeast(1) else it }
    val tips = remember(l) { gsa(l, R.array.daily_tips).filter(String::isNotBlank).distinct() }
    var tipOffset by androidx.compose.runtime.saveable.rememberSaveable(today.toEpochDay()) { mutableIntStateOf(0) }
    val baseTip = remember(today, l, profile.fitnessGoals) { tipForProfile(profile, today) }
    val dayTip = if (tips.isEmpty()) "" else tips[Math.floorMod(tips.indexOf(baseTip).coerceAtLeast(0) + tipOffset, tips.size)]

    if(showProteinSheet) ModalBottomSheet(onDismissRequest={showProteinSheet=false}) {
        Column(Modifier.fillMaxWidth().padding(22.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(gs(l, R.string.yesterday_s_protein),fontSize=24.sp,fontWeight=FontWeight.Bold)
            OutlinedTextField(proteinInput,{proteinInput=it.filter{c->c.isDigit()||c=='.'||c==','}},Modifier.fillMaxWidth(),label={Text(gs(l, R.string.protein_g))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),shape=RoundedCornerShape(22.dp))
            ExpressiveSurfaceButton(onClick={ val v=proteinInput.replace(',','.').toDoubleOrNull(); if(v!=null&&v>=0){ val noon=yesterday.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); onAddNutrition(NutritionEntry(System.currentTimeMillis(),noon,0,v,0,"protein_backfill"));showProteinSheet=false }},modifier=Modifier.fillMaxWidth()){Text(gs(l, R.string.save),fontWeight=FontWeight.SemiBold)}
        }
    }
    if(showWaterSheet) ModalBottomSheet(onDismissRequest={showWaterSheet=false}) {
        Column(Modifier.fillMaxWidth().padding(22.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(gs(l, R.string.water),fontSize=24.sp,fontWeight=FontWeight.Bold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(250,330,500).forEach { ml ->
                    FilledTonalButton(onClick={ onAddNutrition(NutritionEntry(System.currentTimeMillis(),System.currentTimeMillis(),0,0.0,ml,"quick_adjust")); showWaterSheet=false }) { Text("+$ml ${gs(l, R.string.ml)}") }
                }
            }
        }
    }
    if(showQuickProteinSheet) ModalBottomSheet(onDismissRequest={showQuickProteinSheet=false}) {
        Column(Modifier.fillMaxWidth().padding(22.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(gs(l, R.string.protein),fontSize=24.sp,fontWeight=FontWeight.Bold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(10.0,20.0,30.0).forEach { g ->
                    FilledTonalButton(onClick={ onAddNutrition(NutritionEntry(System.currentTimeMillis(),System.currentTimeMillis(),0,g,0,"quick_adjust")); showQuickProteinSheet=false }) { Text("+${g.roundToInt()} ${gs(l, R.string.g)}") }
                }
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=18.dp),
        contentPadding=PaddingValues(top=16.dp,bottom=24.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GymFlow",fontSize=30.sp,fontWeight=FontWeight.Bold, maxLines=1)
                    Text(dateLabel(today,l),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp)
                    val homeLevel = remember(logs, records) { computeLevel(computeTotalXp(logs, records)) }
                    Text(
                        gs(l, R.string.level_xp_compact, homeLevel.level, homeLevel.xpIntoLevel, homeLevel.xpForNextLevel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick=onOpenSettings){Icon(Icons.Rounded.Settings,gs(l, R.string.settings))}
                        AvatarButton(profile,onOpenProfile)
                    }
                    MotionStreakBadge(streak.current, active = workoutDoneToday)
                }
            }
        }
        item {
            DaySelector(
                language = l,
                selected = selectedDay,
                workoutDays = workoutDaysOfWeek,
                onSelect = { selectedDay = it }
            )
        }
        // TODAY: training day vs rest day
        resumableWorkout?.let { day ->
            item {
                ExpressiveSurfaceButton(onClick = { onOpenWorkout(day) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(gs(l, R.string.continue_workout), fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(workoutCompactTitle(day, l), color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
        }
        if (todayWorkout != null) {
            item {
                ExpressiveCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer, corner = 26.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(dayShort(todayWorkout.dayOfWeek, l), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                            Spacer(Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = .65f)) {
                                Text(
                                    if (workoutDoneToday) gs(l, R.string.done) else gs(l, R.string.today),
                                    Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Text(
                            workoutCompactTitle(todayWorkout, l),
                            fontSize = 24.sp,
                            lineHeight = 27.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            gs(l, R.string.stages_minutes, todayWorkout.exercises.size, workoutMinutes(todayWorkout, sessionMins)),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f),
                            fontSize = 13.sp
                        )
                        if (!workoutDoneToday || activeState?.dayKey == todayWorkout.key) {
                            ExpressiveSurfaceButton(
                                onClick = { onOpenWorkout(todayWorkout) },
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                contentPadding = PaddingValues(vertical = 11.dp)
                            ) {
                                Text(
                                    if (activeState?.dayKey == todayWorkout.key) gs(l, R.string.continue_workout)
                                    else gs(l, R.string.start_workout),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            item {
                ExpressiveCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.secondaryContainer, corner = 26.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(gs(l, R.string.rest_day), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            gs(l, R.string.rest_day_hint),
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .8f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        nextWorkout?.let { next ->
                            val eta = daysUntil(today.dayOfWeek, next.dayOfWeek).let { if (it == 0) 7 else it }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                gs(l, R.string.next_workout_in_days, dayShort(next.dayOfWeek, l), eta),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                "${workoutCompactTitle(next, l)} · ${gs(l, R.string.stages_minutes, next.exercises.size, workoutMinutes(next, sessionMins))}",
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .75f),
                                fontSize = 13.sp
                            )
                            }
                        }
                        if (workouts.isEmpty()) {
                            Text(gs(l, R.string.no_workouts_yet), color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        // Selected day from strip (only if different from today training card)
        if (selectedWorkout != null && selectedWorkout.dayOfWeek != today.dayOfWeek) {
            item {
                ExpressiveCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant, corner = 22.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(dayShort(selectedWorkout.dayOfWeek, l), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Spacer(Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = .7f)) {
                                Text(gs(l, R.string.next), Modifier.padding(horizontal = 11.dp, vertical = 5.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                        Text(workoutCompactTitle(selectedWorkout, l), fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(
                            gs(l, R.string.stages_minutes, selectedWorkout.exercises.size, workoutMinutes(selectedWorkout, sessionMins)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        ExpressiveSurfaceButton(
                            onClick = { onOpenWorkout(selectedWorkout) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text(gs(l, R.string.start_workout), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (profile.fitnessGoals.isNotEmpty()) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profile.fitnessGoals.sorted().forEach { id ->
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(
                                fitnessGoalLabel(id, l),
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }
        item { WeeklyProgressCard(weekLogsDone, weekPlanned, streak.current, l) }
        logs.maxByOrNull { it.finishedAt }?.let { last ->
            item {
                ExpressiveCard(Modifier.fillMaxWidth(), corner = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(gs(l, R.string.repeat_last_workout), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                last.title.ifBlank { gs(l, R.string.workout) },
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(onClick = {
                            workoutDayFromLog(last, profile, l)?.let(onOpenWorkout)
                        }) { Text(gs(l, R.string.repeat_workout)) }
                    }
                }
            }
        }
        item { TodayProgressRow(dayProgressDone, 3, gs(l, R.string.today_s_progress)) }
        item {
            QuickActionsRow(
                waterDone = waterGoalMet,
                proteinDone = proteinGoalMet,
                onWater = { showWaterSheet = true },
                onProtein = { showQuickProteinSheet = true },
                onMeasurements = onOpenMeasurements,
                l = l
            )
        }
        if(proteinMissing) item {
            WarningCard(Icons.Rounded.Restaurant,gs(l, R.string.no_protein_data_for_yesterday),gs(l, R.string.add_a_value_to_keep_your_stats_complete),gs(l, R.string.add)){showProteinSheet=true}
        }
        if(measurementsDue) item {
            WarningCard(Icons.Rounded.Straighten,gs(l, R.string.time_to_update_measurements),gs(l, R.string.your_latest_measurements_were_added_a_while),gs(l, R.string.add_measurements),onOpenMeasurements)
        }
        if (workouts.isNotEmpty()) {
            item { SectionTitle(gs(l, R.string.week)) }
            items(workouts, key = { it.key }) { day ->
                ExpressiveSurfaceButton(onClick = { onOpenWorkout(day) }, modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant, contentPadding = PaddingValues(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                                Text(dayShort(day.dayOfWeek, l), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(workoutCompactTitle(day, l), fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(
                                gs(l, R.string.stages_minutes, day.exercises.size, workoutMinutes(day, sessionMins)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
        }
        item { SectionTitle(gs(l, R.string.try_today)) }
        item { NutritionSuggestionCard(l, compact = true) }
        if (dayTip.isNotBlank()) item { TipOfDayCard(gs(l, R.string.tip_of_the_day), dayTip, l) { if (tips.size > 1) tipOffset = (tipOffset + 1) % tips.size } }
        if(goals.isNotEmpty()) item { DailyInsightCard(gs(l, R.string.goal),goals[(today.dayOfYear%goals.size)].let{"${it.title} — ${it.value} ${it.unit}"},Icons.Rounded.Flag) }
        if(records.isNotEmpty()) item { DailyInsightCard(gs(l, R.string.record),records[(today.dayOfYear%records.size)].let{"${it.title} — ${it.value} ${it.unit}"},Icons.Rounded.EmojiEvents) }
        item { DailyInsightCard(gs(l, R.string.motivation),dailyMotivation(profile,today),Icons.Rounded.Bolt) }
    }
}

@Composable
private fun WeeklyProgressCard(done: Int, planned: Int, streakDays: Int, l: String) {
    val goal = planned.coerceAtLeast(1)
    val pct = ((done.toFloat() / goal) * 100f).toInt().coerceIn(0, 999)
    val remaining = (goal - done).coerceAtLeast(0)
    ExpressiveCard(Modifier.fillMaxWidth(), corner = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(gs(l, R.string.weekly_goal), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    gs(l, R.string.weekly_done_of_planned, done, goal),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            GymGlowProgressIndicator(
                progress = { (done.toFloat() / goal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                strokeCap = StrokeCap.Round
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (done >= goal) gs(l, R.string.weekly_goal_met)
                    else gs(l, R.string.weekly_remaining, remaining),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text("$pct%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun logsByLocalDate(logs: List<WorkoutLog>): Map<LocalDate, List<WorkoutLog>> {
    val zone = ZoneId.systemDefault()
    return logs.groupBy { Instant.ofEpochMilli(it.finishedAt).atZone(zone).toLocalDate() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityCalendarCard(logs: List<WorkoutLog>, l: String) {
    val today = LocalDate.now()
    var month by remember { mutableStateOf(today.withDayOfMonth(1)) }
    val byDate = remember(logs) { logsByLocalDate(logs) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val firstDow = month.dayOfWeek.value // Mon=1..Sun=7
    val daysInMonth = month.lengthOfMonth()
    val cells = remember(month) {
        val lead = firstDow - 1
        List(lead) { null } + (1..daysInMonth).map { month.withDayOfMonth(it) }
    }
    val monthLabel = remember(month, l) {
        val name = monthName(month.monthValue, l)
        "$name ${month.year}"
    }

    ExpressiveCard(Modifier.fillMaxWidth(), corner = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.Rounded.ChevronLeft, gs(l, R.string.back))
                }
                Text(monthLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf(
                    gs(l, R.string.weekday_mon_short),
                    gs(l, R.string.weekday_tue_short),
                    gs(l, R.string.weekday_wed_short),
                    gs(l, R.string.weekday_thu_short),
                    gs(l, R.string.weekday_fri_short),
                    gs(l, R.string.weekday_sat_short),
                    gs(l, R.string.weekday_sun_short)
                ).forEach { d ->
                    Text(d, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                val trained = byDate[date].orEmpty().isNotEmpty()
                                val isToday = date == today
                                val bg = when {
                                    trained -> MaterialTheme.colorScheme.primaryContainer
                                    isToday -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> Color.Transparent
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = bg,
                                    modifier = Modifier
                                        .fillMaxSize(0.88f)
                                        .clickable(
                                            onClick = { selected = date },
                                            onClickLabel = date.toString()
                                        )
                                        .semantics {
                                            contentDescription = date.toString() + if (trained) " workout" else ""
                                        }
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            date.dayOfMonth.toString(),
                                            fontSize = 12.sp,
                                            fontWeight = if (isToday || trained) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                trained -> MaterialTheme.colorScheme.onPrimaryContainer
                                                isToday -> MaterialTheme.colorScheme.onSecondaryContainer
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (byDate.keys.none { it.year == month.year && it.month == month.month }) {
                Text(gs(l, R.string.calendar_empty_month), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    selected?.let { day ->
        val dayLogs = byDate[day].orEmpty()
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(dateLabel(day, l), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (dayLogs.isEmpty()) {
                    Text(gs(l, R.string.calendar_no_workout), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(gs(l, R.string.calendar_workouts_count, dayLogs.size), fontWeight = FontWeight.SemiBold)
                    dayLogs.forEach { log ->
                        ExpressiveCard(Modifier.fillMaxWidth(), corner = 16.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(log.title.ifBlank { gs(l, R.string.workout) }, fontWeight = FontWeight.Bold)
                                Text(
                                    listOfNotNull(
                                        durationLabel(log.durationMillis, l),
                                        gs(l, R.string.sets_short, log.completedSets)
                                    ).joinToString(" · "),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun StreakBadge(days: Int) {
    MotionStreakBadge(days)
}

@Composable private fun TodayProgressRow(done: Int, total: Int, label: String) {
    ExpressiveCard(Modifier.fillMaxWidth(), corner = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("$done/$total", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
            GymGlowProgressIndicator(
                progress = { (done.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable private fun QuickActionsRow(
    waterDone: Boolean,
    proteinDone: Boolean,
    onWater: () -> Unit,
    onProtein: () -> Unit,
    onMeasurements: () -> Unit,
    l: String
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickActionChip(Icons.Rounded.WaterDrop, gs(l, R.string.water), waterDone, Modifier.weight(1f), onWater)
        QuickActionChip(Icons.Rounded.Egg, gs(l, R.string.protein), proteinDone, Modifier.weight(1f), onProtein)
        QuickActionChip(Icons.Rounded.Straighten, gs(l, R.string.measurements), false, Modifier.weight(1f), onMeasurements)
    }
}

@Composable private fun QuickActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    done: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, Modifier.size(19.dp), tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun TipOfDayCard(title: String, tip: String, l: String, onRefresh: ()->Unit) {
    ExpressiveCard(Modifier.fillMaxWidth(), corner = 20.dp) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Lightbulb, null, Modifier.size(20.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(tip, fontSize = 15.sp, lineHeight = 22.sp)
                TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (l == "EN") "Another recommendation" else "Другая рекомендация", maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable private fun AvatarButton(profile:UserProfile,onClick:()->Unit) {
    Surface(Modifier.size(48.dp).semantics { contentDescription = gs(profile.appLanguage, R.string.open_profile) }.clickable(onClick=onClick),shape=CircleShape,color=MaterialTheme.colorScheme.secondaryContainer) {
        val bitmap=rememberUriBitmap(profile.avatarUri)
        if(bitmap!=null) Image(bitmap,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(profile.firstName.take(1).ifBlank{"G"}.uppercase(),fontWeight=FontWeight.Bold)}
    }
}

@Composable private fun StatsSummaryRow(logs: List<WorkoutLog>, records: List<PersonalRecord>) {
    val l = LocalAppLanguage.current
    val streak = remember(logs) { computeWorkoutStreak(logs) }
    val volume = remember(logs) { computeTotalVolumeKg(logs) }
    val stats = listOf(
        logs.size.toString() to gs(l, R.string.workouts_count_label),
        streak.best.toString() to gs(l, R.string.best_streak),
        gs(l, R.string.volume_kg_format, volume.roundToInt().toString()) to gs(l, R.string.total_volume),
        records.size.toString() to gs(l, R.string.record)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (value, label) ->
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun WarningCard(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,text:String,button:String,onClick:()->Unit) {
    ExpressiveCard(Modifier.fillMaxWidth(),containerColor=MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment=Alignment.Top){Icon(icon,null);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold,fontSize=17.sp);Spacer(Modifier.height(3.dp));Text(text,color=MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha=.75f));TextButton(onClick=onClick,contentPadding=PaddingValues(0.dp)){Text(button)}}}
    }
}
@Composable private fun SectionTitle(t:String)=Text(t,fontSize=23.sp,fontWeight=FontWeight.Bold)
@Composable private fun DailyInsightCard(label:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector){ExpressiveCard(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,null,Modifier.size(30.dp));Spacer(Modifier.width(13.dp));Column{Text(label,fontSize=11.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary);Text(value,fontSize=18.sp,fontWeight=FontWeight.SemiBold)}}}}
private fun dailyMotivation(p: UserProfile, d: LocalDate): String {
    val l = p.appLanguage
    val name = p.firstName.takeIf { it.isNotBlank() }?.plus(", ").orEmpty()
    val list = gsa(l, R.array.home_motivation_messages)
    return list[d.dayOfYear % list.size].replace("%1" + '$' + "s", name)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthlyRecapCard(
    logs: List<WorkoutLog>,
    records: List<PersonalRecord>,
    profile: UserProfile
) {
    val l = profile.appLanguage
    var month by remember { mutableStateOf(java.time.YearMonth.now()) }
    val recap = remember(logs, records, profile, month) {
        computeMonthlyRecap(logs, records, profile, l, month)
    }
    val monthLabel = remember(month, l) {
        "${monthName(month.monthValue, l)} ${month.year}"
    }
    ExpressiveCard(Modifier.fillMaxWidth(), corner = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.Rounded.ChevronLeft, gs(l, R.string.back))
                }
                Text(
                    monthLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = {
                    val next = month.plusMonths(1)
                    if (!next.isAfter(java.time.YearMonth.now())) month = next
                }) {
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
            if (recap.workoutCount == 0) {
                Text(gs(l, R.string.monthly_recap_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                Text(monthlyRecapSummary(recap, l), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecapStatChip(Modifier.weight(1f), gs(l, R.string.workouts_count_label), "${recap.workoutCount}")
                    RecapStatChip(Modifier.weight(1f), gs(l, R.string.sets_label), "${recap.totalSets}")
                    RecapStatChip(Modifier.weight(1f), "XP", "${recap.xpGained}")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecapStatChip(Modifier.weight(1f), gs(l, R.string.active_days), "${recap.activeDays}")
                    RecapStatChip(Modifier.weight(1f), gs(l, R.string.streak), "${recap.bestStreakInMonth}")
                    RecapStatChip(Modifier.weight(1f), gs(l, R.string.personal_records), "${recap.prCount}")
                }
                if (recap.totalVolumeKg > 0) {
                    Text(
                        gs(l, R.string.volume_label) + ": " +
                            (if (recap.totalVolumeKg % 1.0 == 0.0) recap.totalVolumeKg.toLong().toString()
                            else "%.0f".format(java.util.Locale.US, recap.totalVolumeKg)) +
                            " " + gs(l, R.string.kg),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                recap.topExerciseTitle?.let { title ->
                    Text(
                        gs(l, R.string.top_exercise) + ": " + title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RecapStatChip(modifier: Modifier, label: String, value: String) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ProgressScreen(
    entries: List<ProgressEntry>,
    logs: List<WorkoutLog>,
    profile: UserProfile,
    goals: List<GoalEntry>,
    records: List<PersonalRecord>,
    onAddEntry: (ProgressEntry) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onDeleteLog: (Long) -> Unit,
    onUpsertGoal: (GoalEntry) -> Unit,
    onDeleteGoal: (Long) -> Unit,
    onUpsertRecord: (PersonalRecord) -> Unit,
    onDeleteRecord: (Long) -> Unit,
    onSnoozeMonthlyCheckIn: () -> Unit,
    onOpenWorkout: (WorkoutDay) -> Unit = {},
    templates: List<WorkoutTemplate> = emptyList(),
    onSaveTemplate: (WorkoutTemplate) -> Unit = {},
    onDeleteTemplate: (Long) -> Unit = {},
    onRenameTemplate: (Long, String) -> Unit = { _, _ -> }
) {
    val l=profile.appLanguage
    var showMeasurement by remember{mutableStateOf(false)}
    var showGoal by remember{mutableStateOf(false)}
    var showRecord by remember{mutableStateOf(false)}
    var selectedMetric by remember{mutableStateOf("WEIGHT")}
    var editGoal by remember{mutableStateOf<GoalEntry?>(null)}
    var editRecord by remember{mutableStateOf<PersonalRecord?>(null)}

    if(showMeasurement) MeasurementSheet(l,{showMeasurement=false},onAddEntry)
    if(showGoal) GoalSheet(l,editGoal,{showGoal=false;editGoal=null}){onUpsertGoal(it);showGoal=false;editGoal=null}
    if(showRecord) RecordSheet(l,editRecord,{showRecord=false;editRecord=null}){onUpsertRecord(it);showRecord=false;editRecord=null}

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=18.dp),contentPadding=PaddingValues(top=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(gs(l, R.string.measurements),fontSize=32.sp,fontWeight=FontWeight.Bold);Text(gs(l, R.string.body_goals_and_workout_history),color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick={showMeasurement=true}){Icon(Icons.Rounded.Add,gs(l, R.string.add))}} }
        item { StatsSummaryRow(logs, records) }
        item {
            val weekGoal = profile.trainingDays.size.coerceIn(1, 7).let { if (it == 0) 3 else it }
            val today = LocalDate.now()
            val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val weekEnd = weekStart.plusDays(6)
            val done = remember(logs, weekStart) {
                logs.count { log ->
                    val d = Instant.ofEpochMilli(log.finishedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    !d.isBefore(weekStart) && !d.isAfter(weekEnd)
                }
            }
            WeeklyProgressCard(done, weekGoal, 0, l)
        }
        item { SectionTitle(gs(l, R.string.activity_calendar)) }
        item { ActivityCalendarCard(logs, l) }
        item { SectionTitle(gs(l, R.string.monthly_recap)) }
        item { MonthlyRecapCard(logs = logs, records = records, profile = profile) }
        if(entries.isEmpty()) item { CompactEmpty(Icons.Rounded.Straighten,gs(l, R.string.no_measurements_yet),gs(l, R.string.add_your_first_measurement_to_see_history),gs(l, R.string.add_entry)){showMeasurement=true} }
        else {
            item {
                ExpressiveCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(gs(l, R.string.measurement_history),fontWeight=FontWeight.Bold,fontSize=19.sp)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            metricOptions(l).forEach{(k,n)->FilterChip(selectedMetric==k,{selectedMetric=k},{Text(n)})}
                        }
                        MetricChart(entries,selectedMetric)
                        val latest=metricValue(entries.first(),selectedMetric)
                        if(latest!=null) Text("${metricOptions(l).first{it.first==selectedMetric}.second}: ${fmt(latest)} ${if(selectedMetric=="WEIGHT")gs(l, R.string.kg) else gs(l, R.string.cm)}",fontWeight=FontWeight.SemiBold)
                    }
                }
            }
            items(entries.take(8),key={it.id}) { e -> MeasurementRow(e,l){onDeleteEntry(e.id)} }
        }
        item { Row(verticalAlignment=Alignment.CenterVertically){SectionTitle(gs(l, R.string.goals));Spacer(Modifier.weight(1f));IconButton(onClick={editGoal=null;showGoal=true}){Icon(Icons.Rounded.Add,gs(l, R.string.create_goal))}} }
        if(goals.isEmpty()) item{CompactEmpty(Icons.Rounded.Flag,gs(l, R.string.no_goals_yet),gs(l, R.string.create_a_goal_with_a_name_value_and_unit),gs(l, R.string.create_goal)){showGoal=true}}
        else items(goals,key={it.id}){g->EditableValueCard(g.title,"${g.value} ${g.unit}",Icons.Rounded.Flag,{editGoal=g;showGoal=true},{onDeleteGoal(g.id)})}
        item { Row(verticalAlignment=Alignment.CenterVertically){SectionTitle(gs(l, R.string.personal_records));Spacer(Modifier.weight(1f));IconButton(onClick={editRecord=null;showRecord=true}){Icon(Icons.Rounded.Add,gs(l, R.string.add_record))}} }
        if(records.isEmpty()) item{CompactEmpty(Icons.Rounded.EmojiEvents,gs(l, R.string.no_records_yet),gs(l, R.string.add_your_current_personal_record),gs(l, R.string.add_record)){showRecord=true}}
        else items(records,key={it.id}){r->EditableValueCard(r.title,"${r.value} ${r.unit}",Icons.Rounded.EmojiEvents,{editRecord=r;showRecord=true},{onDeleteRecord(r.id)})}
        item { SectionTitle(gs(l, R.string.muscle_load)) }
        item { MuscleLoadCard(logs,profile) }
        item { SectionTitle(gs(l, R.string.exercise_history)) }
        item { ExerciseHistoryPanel(logs = logs, profile = profile, records = records) }
        item { SectionTitle(gs(l, R.string.workout_history)) }
        if(logs.isEmpty()) item{CompactEmpty(Icons.Rounded.History,gs(l, R.string.no_workouts_yet),gs(l, R.string.finish_your_first_workout_to_see_history),"",{})}
        else items(logs,key={it.id}){log->WorkoutHistoryRow(log,profile,onDelete={onDeleteLog(log.id)},onRepeat={
            workoutDayFromLog(log, profile, l)?.let(onOpenWorkout)
        },onSaveTemplate={
            val name = log.title.ifBlank { gs(l, R.string.workout) }
            onSaveTemplate(templateFromLog(log, name))
        })}
        item { SectionTitle(gs(l, R.string.my_templates)) }
        if (templates.isEmpty()) {
            item { Text(gs(l, R.string.no_templates_yet), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        } else {
            items(templates, key = { it.id }) { tm ->
                var renaming by remember(tm.id) { mutableStateOf(false) }
                var nameDraft by remember(tm.id, tm.name) { mutableStateOf(tm.name) }
                ExpressiveCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (renaming) {
                            OutlinedTextField(
                                value = nameDraft,
                                onValueChange = { nameDraft = it.take(48) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text(gs(l, R.string.template_name)) },
                                shape = RoundedCornerShape(16.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = {
                                    val n = nameDraft.trim()
                                    if (n.isNotEmpty()) {
                                        onRenameTemplate(tm.id, n)
                                        renaming = false
                                    }
                                }) { Text(gs(l, R.string.save)) }
                                TextButton(onClick = { renaming = false; nameDraft = tm.name }) { Text(gs(l, R.string.close)) }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tm.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(gs(l, R.string.template_exercises_count, tm.exerciseIds.size), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                                TextButton(onClick = {
                                    workoutDayFromTemplate(tm, profile, l)?.let(onOpenWorkout)
                                }) { Text(gs(l, R.string.start_workout)) }
                                IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, gs(l, R.string.edit_profile)) }
                                IconButton(onClick = { onDeleteTemplate(tm.id) }) { Icon(Icons.Rounded.Delete, gs(l, R.string.delete)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseHistoryPanel(
    logs: List<WorkoutLog>,
    profile: UserProfile,
    records: List<PersonalRecord>
) {
    val l = profile.appLanguage
    val ids = remember(logs) { knownExerciseIdsFromLogs(logs) }
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val filtered = remember(ids, query, logs, profile) {
        val q = query.trim().lowercase()
        ids.map { id -> id to exerciseDisplayTitle(id, logs, profile, l) }
            .filter { q.isEmpty() || it.second.lowercase().contains(q) || it.first.lowercase().contains(q) }
    }
    val selected = selectedId
    val summary = remember(logs, selected, profile) {
        selected?.let { exerciseHistorySummary(logs, it, profile, l) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (ids.isEmpty()) {
            Text(gs(l, R.string.exercise_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(gs(l, R.string.search_exercise)) },
                shape = RoundedCornerShape(18.dp)
            )
            filtered.take(12).forEach { (id, title) ->
                val isOn = selectedId == id
                ExpressiveSurfaceButton(
                    onClick = { selectedId = if (isOn) null else id },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (isOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            summary?.let { s ->
                ExpressiveCard(Modifier.fillMaxWidth(), corner = 18.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(s.displayTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        val prBits = listOfNotNull(
                            s.bestWeightKg?.let { "${trimHistoryNum(it)} ${gs(l, R.string.kg)}" },
                            s.bestReps?.let { gs(l, R.string.reps_count, it.toString()) },
                            s.bestDurationSec?.let { gs(l, R.string.seconds_count, it.toString()) },
                            s.bestVolume?.let { "${gs(l, R.string.volume_label)} ${trimHistoryNum(it)}" }
                        )
                        if (prBits.isNotEmpty()) {
                            Text(
                                gs(l, R.string.best_result) + ": " + prBits.joinToString(" · "),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (s.sessions.isEmpty()) {
                            Text(gs(l, R.string.exercise_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        } else {
                            s.sessions.take(12).forEach { session ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(millisDate(session.finishedAt, l), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        listOfNotNull(
                                            session.workoutTitle.takeIf { it.isNotBlank() },
                                            gs(l, R.string.sets_short, session.sets),
                                            session.bestWeightKg?.let { "${trimHistoryNum(it)} ${gs(l, R.string.kg)}" },
                                            session.bestReps?.let { gs(l, R.string.reps_count, it.toString()) },
                                            session.bestDurationSec?.let { gs(l, R.string.seconds_count, it.toString()) }
                                        ).joinToString(" · "),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun trimHistoryNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(java.util.Locale.US, v)


private fun metricOptions(l:String)=listOf("WEIGHT" to gs(l, R.string.weight),"HEIGHT" to gs(l, R.string.height),"CHEST" to gs(l, R.string.chest),"WAIST" to gs(l, R.string.waist),"BICEPS" to gs(l, R.string.biceps),"THIGH" to gs(l, R.string.thigh))
private fun metricValue(e:ProgressEntry,k:String):Double?=when(k){"WEIGHT"->e.weightKg;"HEIGHT"->e.heightCm;"CHEST"->e.chestCm;"WAIST"->e.waistCm;"BICEPS"->e.upperArmCm;"THIGH"->e.thighCm;else->null}
private fun fmt(v:Double)=if(v%1.0==0.0)v.toInt().toString() else "%.1f".format(v)

@Composable private fun MetricChart(entries: List<ProgressEntry>, metric: String) {
    val values = remember(entries, metric) { entries.sortedBy { it.createdAt }.mapNotNull { metricValue(it, metric) }.filter { it.isFinite() }.takeLast(20) }
    if (values.size < 2) { Text(gs(LocalAppLanguage.current, R.string.add_one_more_measurement_for_a_chart), color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    val reveal = remember(metric) { Animatable(0f) }
    LaunchedEffect(metric) { reveal.animateTo(1f, GymGlowMotion.defaultEffects()) }
    val bounds = remember(values) {
        val minimum = values.minOrNull()!!
        minimum to ((values.maxOrNull()!! - minimum).takeIf { it > .001 } ?: 1.0)
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        for (i in 1..3) { val y = size.height * i / 4f; drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f) }
        // Read animation only in the draw phase; grid and surrounding UI remain static.
        clipRect(right = size.width * reveal.value) {
            var last: Offset? = null
            values.forEachIndexed { i, v ->
                val p = Offset(size.width * i / values.lastIndex.toFloat(), size.height - ((v - bounds.first) / bounds.second * size.height * .82 + size.height * .09).toFloat())
                last?.let { drawLine(lineColor, it, p, strokeWidth = 5f, cap = StrokeCap.Round) }
                drawCircle(lineColor, 6f, p)
                last = p
            }
        }
    }
}

@Composable private fun MeasurementRow(e:ProgressEntry,l:String,onDelete:()->Unit){ExpressiveCard(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(millisDate(e.createdAt,l),fontWeight=FontWeight.Bold);Text(listOfNotNull(e.weightKg?.let{"${fmt(it)} ${gs(l, R.string.kg)}"},e.heightCm?.let{"${fmt(it)} ${gs(l, R.string.cm)}"},e.chestCm?.let{"${gs(l, R.string.chest_73481ed)} ${fmt(it)}"},e.waistCm?.let{"${gs(l, R.string.waist_0e51ebf)} ${fmt(it)}"}).joinToString(" • "),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)};IconButton(onClick=onDelete){Icon(Icons.Rounded.Delete,gs(l, R.string.delete))}}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementSheet(l: String, onDismiss: () -> Unit, onSave: (ProgressEntry) -> Unit) {
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var biceps by remember { mutableStateOf("") }
    var thigh by remember { mutableStateOf("") }
    var forearm by remember { mutableStateOf("") }
    var calf by remember { mutableStateOf("") }

    fun clean(value: String) = value.filter { it.isDigit() || it == '.' || it == ',' }
    fun parse(value: String) = value.replace(',', '.').toDoubleOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text(gs(l, R.string.new_measurement_68b97bf), fontSize = 25.sp, fontWeight = FontWeight.Bold) }
            item { OutlinedTextField(weight, { weight = clean(it) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.weight_kg)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(20.dp)) }
            item { OutlinedTextField(height, { height = clean(it) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.height_cm)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(20.dp)) }
            item { OutlinedTextField(chest, { chest = clean(it) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.chest_cm)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(20.dp)) }
            item { OutlinedTextField(waist, { waist = clean(it) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.waist_cm)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(20.dp)) }
            item { OutlinedTextField(biceps, { biceps = clean(it) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.biceps_cm)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(20.dp)) }
            item { OutlinedTextField(thigh, { thigh = clean(it) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.thigh_cm)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(20.dp)) }
            item { OutlinedTextField(forearm, { forearm = clean(it) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.forearm_cm)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(20.dp)) }
            item { OutlinedTextField(calf, { calf = clean(it) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.calf_cm)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(20.dp)) }
            item {
                ExpressiveSurfaceButton(
                    onClick = {
                        val now = System.currentTimeMillis()
                        onSave(ProgressEntry(now, now, parse(weight), parse(height), gs(l, R.string.measurement), parse(biceps), parse(forearm), parse(chest), parse(waist), parse(thigh), parse(calf)))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(gs(l, R.string.save), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun GoalSheet(l:String,current:GoalEntry?,onDismiss:()->Unit,onSave:(GoalEntry)->Unit){var title by remember(current){mutableStateOf(current?.title.orEmpty())};var value by remember(current){mutableStateOf(current?.value.orEmpty())};var unit by remember(current){mutableStateOf(current?.unit.orEmpty())};ModalBottomSheet(onDismissRequest=onDismiss){ValueEditor(l,gs(l, R.string.goal_c58da1c),title,{title=it},value,{value=it},unit,{unit=it},onDismiss){if(title.isNotBlank()&&value.isNotBlank())onSave(GoalEntry(current?.id?:System.currentTimeMillis(),title.trim(),value.trim(),unit.trim(),current?.createdAt?:System.currentTimeMillis()))}}}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RecordSheet(l:String,current:PersonalRecord?,onDismiss:()->Unit,onSave:(PersonalRecord)->Unit){var title by remember(current){mutableStateOf(current?.title.orEmpty())};var value by remember(current){mutableStateOf(current?.value.orEmpty())};var unit by remember(current){mutableStateOf(current?.unit.orEmpty())};ModalBottomSheet(onDismissRequest=onDismiss){ValueEditor(l,gs(l, R.string.personal_record),title,{title=it},value,{value=it},unit,{unit=it},onDismiss){if(title.isNotBlank()&&value.isNotBlank())onSave(PersonalRecord(current?.id?:System.currentTimeMillis(),title.trim(),value.trim(),unit.trim(),System.currentTimeMillis()))}}}
@Composable private fun ValueEditor(l:String,heading:String,title:String,setTitle:(String)->Unit,value:String,setValue:(String)->Unit,unit:String,setUnit:(String)->Unit,onDismiss:()->Unit,onSave:()->Unit){Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(heading,fontSize=24.sp,fontWeight=FontWeight.Bold);OutlinedTextField(title,setTitle,Modifier.fillMaxWidth(),label={Text(gs(l, R.string.name))},shape=RoundedCornerShape(20.dp));OutlinedTextField(value,setValue,Modifier.fillMaxWidth(),label={Text(gs(l, R.string.value_label))},shape=RoundedCornerShape(20.dp));OutlinedTextField(unit,setUnit,Modifier.fillMaxWidth(),label={Text(gs(l, R.string.unit))},shape=RoundedCornerShape(20.dp));ExpressiveSurfaceButton(onSave,Modifier.fillMaxWidth(),enabled=title.isNotBlank()&&value.isNotBlank()){Text(gs(l, R.string.save),fontWeight=FontWeight.Bold)}}}

@Composable private fun EditableValueCard(title:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onEdit:()->Unit,onDelete:()->Unit){
    val l=LocalAppLanguage.current
    ExpressiveCard(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,null);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold);Text(value,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick=onEdit){Icon(Icons.Rounded.Edit,gs(l, R.string.edit))};IconButton(onClick=onDelete){Icon(Icons.Rounded.Delete,gs(l, R.string.delete))}}}
}
@Composable private fun CompactEmpty(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,text:String,action:String,onClick:()->Unit){ExpressiveCard(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=MaterialTheme.colorScheme.secondaryContainer){Box(Modifier.size(46.dp),contentAlignment=Alignment.Center){Icon(icon,null)}};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold);Text(text,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp);if(action.isNotBlank())TextButton(onClick=onClick,contentPadding=PaddingValues(0.dp)){Text(action)}}}}}

@Composable private fun MuscleLoadCard(logs:List<WorkoutLog>,profile:UserProfile){
    val l=profile.appLanguage
    var calculationReady by remember(logs, profile, l) { mutableStateOf(logs.isEmpty()) }
    val counts by produceState<List<Pair<String, Int>>>(emptyList(), logs, profile, l) {
        calculationReady = logs.isEmpty()
        value = if (logs.isEmpty()) emptyList() else withContext(Dispatchers.Default) {
            computeMuscleLoadCounts(logs, profile, l)
        }
        calculationReady = true
    }
    if (!calculationReady) {
        ExpressiveCard(Modifier.fillMaxWidth()) {
            Text(gs(l, R.string.calculating_training_load), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if(counts.isEmpty()){CompactEmpty(Icons.Rounded.FitnessCenter,gs(l, R.string.muscle_load),gs(l, R.string.finish_your_first_workout_to_see_load_distri),"",{});return}
    ExpressiveCard(Modifier.fillMaxWidth()){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){counts.take(8).forEach{(m,c)->Row{Text(m,Modifier.weight(1f));Text(gs(l, R.string.sets_count, c),fontWeight=FontWeight.SemiBold)}}}}
}
@Composable private fun WorkoutHistoryRow(
    log: WorkoutLog,
    profile: UserProfile,
    onDelete: () -> Unit,
    onRepeat: () -> Unit = {},
    onSaveTemplate: () -> Unit = {}
) {
    val l = profile.appLanguage
    val day = workoutsFor(profile).firstOrNull { it.key == log.dayKey } ?: workoutByKey(log.dayKey)
    val title = day?.let { workoutCompactTitle(it, l) } ?: log.title
    ExpressiveCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(millisDate(log.finishedAt, l), fontWeight = FontWeight.Bold)
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${durationLabel(log.durationMillis, l)} • ${gs(l, R.string.sets_count, log.completedSets)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, gs(l, R.string.delete)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onRepeat) { Text(gs(l, R.string.repeat_workout)) }
                TextButton(onClick = onSaveTemplate) { Text(gs(l, R.string.save_as_template)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit,
    onPickPhoto: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    logs: List<WorkoutLog> = emptyList(),
    records: List<PersonalRecord> = emptyList(),
    progress: List<ProgressEntry> = emptyList()
) {
    val l = profile.appLanguage
    var editing by remember { mutableStateOf(false) }
    var showBirthWheel by remember { mutableStateOf(false) }
    var showFullAvatar by remember { mutableStateOf(false) }
    var first by remember(profile.firstName) { mutableStateOf(profile.firstName) }
    var last by remember(profile.lastName) { mutableStateOf(profile.lastName) }
    var desc by remember(profile.profileDescription) { mutableStateOf(profile.profileDescription) }
    var editBirthDate by remember(profile.birthDate) { mutableStateOf(normalizeBirthDate(profile.birthDate)) }

    if (showBirthWheel) {
        BirthDateWheelSheet(
            language = l,
            current = editBirthDate,
            onDismiss = { showBirthWheel = false; editing = true },
            onSelected = { editBirthDate = it }
        )
    }

    if (editing) {
        val birth = parseBirthDate(editBirthDate)
        val birthLabel = birth?.let { date ->
            val month = gsa(l, R.array.months_date)[date.monthValue - 1]
            gs(l, R.string.birth_date_display, date.dayOfMonth, month, date.year)
        } ?: gs(l, R.string.set_date_of_birth)
        ModalBottomSheet(onDismissRequest = { editing = false }) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(gs(l, R.string.edit_profile), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(first, { first = it }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.first_name)) }, shape = RoundedCornerShape(20.dp))
                OutlinedTextField(last, { last = it }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.last_name)) }, shape = RoundedCornerShape(20.dp))
                OutlinedTextField(desc, { desc = it.take(120) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.description)) }, minLines = 2, shape = RoundedCornerShape(20.dp))
                PickerValueCard(gs(l, R.string.birth_date), birthLabel) { editing = false; showBirthWheel = true }
                ExpressiveSurfaceButton(
                    onClick = {
                        onProfileChange(
                            profile.copy(
                                firstName = first.trim(),
                                lastName = last.trim(),
                                profileDescription = desc.trim(),
                                birthDate = normalizeBirthDate(editBirthDate)
                            )
                        )
                        editing = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(gs(l, R.string.save), fontWeight = FontWeight.Bold) }
            }
        }
    }

    val birth = parseBirthDate(profile.birthDate)
    val birthLabel = birth?.let { date ->
        val month = gsa(l, R.array.months_date)[date.monthValue - 1]
        gs(l, R.string.birth_date_display, date.dayOfMonth, month, date.year)
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Telegram-like top bar: back on the left, only settings on the right.
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, gs(l, R.string.back)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, gs(l, R.string.settings)) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(124.dp).clickable(onClick = { if (profile.avatarUri.isNotBlank()) showFullAvatar = true else onPickPhoto() })) {
                        val bmp = rememberUriBitmap(profile.avatarUri)
                        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(profile.firstName.take(1).ifBlank { "G" }.uppercase(), fontSize = 44.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp).clickable(onClick = onPickPhoto)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PhotoCamera, gs(l, R.string.choose_photo), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    listOf(profile.firstName, profile.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { gs(profile.appLanguage, R.string.app_name) },
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                StatusLabel(gs(l, R.string.online_status))
                if (profile.profileDescription.isNotBlank()) {
                    Text(
                        profile.profileDescription,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        item { ProfileStatsRow(logs = logs, records = records) }
        item {
            val level = remember(logs, records) { computeLevel(computeTotalXp(logs, records)) }
            ExpressiveCard(Modifier.fillMaxWidth(), corner = 20.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(gs(l, R.string.level_label, level.level), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(
                            "${level.xpIntoLevel} / ${level.xpForNextLevel} XP",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GymGlowProgressIndicator(
                        progress = { level.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
        item {
            val achievements = remember(logs, records) { computeAchievements(logs, records) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(gs(l, R.string.achievements), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                achievements.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { ach ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                color = if (ach.unlocked) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(ach.icon, fontSize = 18.sp, modifier = Modifier.alpha(if (ach.unlocked) 1f else .35f))
                                    Text(
                                        gs(l, ach.titleRes),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = if (ach.unlocked) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        gs(l, ach.descRes),
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.alpha(if (ach.unlocked) 1f else .55f)
                                    )
                                    if (ach.unlocked && ach.unlockedAt != null) {
                                        Text(
                                            millisDate(ach.unlockedAt, l),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileActionRow(Icons.Rounded.Edit, gs(l, R.string.edit_profile), onClick = { editing = true })
                ProfileActionRow(Icons.Rounded.PhotoCamera, gs(l, R.string.choose_photo), onClick = onPickPhoto)
                ProfileActionRow(Icons.Rounded.Settings, gs(l, R.string.settings), onClick = onOpenSettings)
            }
        }
        // Secondary info — birth date and member-since sit low in the hierarchy.
        if (birthLabel != null) item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Cake, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(birthLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        val memberSince = memberSinceLabel(logs, progress, l)
        if (memberSince != null) item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(memberSince, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }

    if (showFullAvatar && profile.avatarUri.isNotBlank()) {
        FullscreenAvatarViewer(uri = profile.avatarUri, onDismiss = { showFullAvatar = false })
    }
}

private fun memberSinceLabel(logs: List<WorkoutLog>, progress: List<ProgressEntry>, l: String): String? {
    val earliest = (logs.map { it.startedAt } + progress.map { it.createdAt }).minOrNull() ?: return null
    val date = java.time.Instant.ofEpochMilli(earliest).atZone(ZoneId.systemDefault()).toLocalDate()
    val month = gsa(l, R.array.months_date)[date.monthValue - 1]
    return gs(l, R.string.member_since, gs(l, R.string.date_month_day, month, date.dayOfMonth))
}

@Composable private fun ProfileStatsRow(logs: List<WorkoutLog>, records: List<PersonalRecord>) {
    val l = LocalAppLanguage.current
    val streak = remember(logs) { computeWorkoutStreak(logs) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileStatChip(Modifier.weight(1f), "🔥", "${streak.current}", gs(l, R.string.day_streak))
        ProfileStatChip(Modifier.weight(1f), null, "${logs.size}", gs(l, R.string.workouts_count_label))
        ProfileStatChip(Modifier.weight(1f), null, "${records.size}", gs(l, R.string.record))
    }
}

@Composable private fun ProfileStatChip(modifier: Modifier, emoji: String?, value: String, label: String) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (emoji != null) { Text(emoji, fontSize = 14.sp); Spacer(Modifier.width(3.dp)) }
                Text(value, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun FullscreenAvatarViewer(uri: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            val bmp = rememberUriBitmap(uri)
            if (bmp != null) Image(bmp, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            IconButton(onClick = onDismiss, modifier = Modifier.statusBarsPadding().align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Rounded.Close, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun rememberUriBitmap(uriString: String): ImageBitmap? {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uriString) {
        value = if (uriString.isBlank()) null else runCatching {
            val uri = Uri.parse(uriString)
            val bitmap = if (uri.scheme == "file") {
                val file = File(requireNotNull(uri.path))
                if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file))
                } else {
                    @Suppress("DEPRECATION")
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                }
            } else if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            bitmap.asImageBitmap()
        }.getOrNull()
    }
    return bmp
}
