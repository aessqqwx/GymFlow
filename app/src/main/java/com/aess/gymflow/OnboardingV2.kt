package com.aess.gymflow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private val onboardingDays = DayOfWeek.values().toList()
private val equipmentOptions = listOf("BODYWEIGHT", "DUMBBELLS", "PULLUP_BAR", "BANDS", "BENCH")

@Composable
fun OnboardingV2(
    initial: UserProfile,
    onImport: () -> Unit,
    onFinish: suspend (UserProfile, Double, Double, List<WorkoutDay>) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var language by remember { mutableStateOf(initial.appLanguage) }
    var languageMenu by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf(initial.trainingPlace) }
    var equipment by remember { mutableStateOf(initial.homeEquipment) }
    var selectedDays by remember { mutableStateOf(initial.trainingDays) }
    var sex by remember { mutableStateOf(if (initial.sex == "UNSPECIFIED") "MALE" else initial.sex) }
    var birthDate by remember { mutableStateOf(normalizeBirthDate(initial.birthDate)) }
    var weightKg by remember { mutableIntStateOf(70) }
    var heightCm by remember { mutableIntStateOf(175) }
    var manualPlan by remember { mutableStateOf(initial.selectedProgram == "CUSTOM") }
    var customName by remember { mutableStateOf(initial.customWorkoutName) }
    var program by remember { mutableStateOf(initial.selectedProgram) }
    var fitnessGoals by remember { mutableStateOf(initial.fitnessGoals) }
    var sessionDuration by remember {
        mutableIntStateOf(
            listOf(20, 30, 45, 60, 90).minByOrNull { kotlin.math.abs(it - initial.sessionDurationMinutes) } ?: 45
        )
    }
    var dayFocus by remember { mutableStateOf(initial.dayFocus) }
    var selectedExercises by remember { mutableStateOf(initial.selectedExerciseIds) }
    var first by remember { mutableStateOf(initial.firstName) }
    var last by remember { mutableStateOf(initial.lastName) }
    var protein by remember { mutableStateOf(if (initial.proteinGoal > 0) initial.proteinGoal.toString() else "") }
    var calories by remember { mutableStateOf(if (initial.calorieGoal > 0) initial.calorieGoal.toString() else "") }
    var water by remember { mutableStateOf(if (initial.waterGoalMl > 0) initial.waterGoalMl.toString() else "2000") }
    var termsAccepted by remember { mutableStateOf(false) }
    var privacyAccepted by remember { mutableStateOf(false) }
    var legalDialog by remember { mutableStateOf<String?>(null) }

    val bodyValid = birthDate != null && parseBirthDate(birthDate) != null && weightKg in 20..350 && heightCm in 100..240
    val nutritionValid = protein.toIntOrNull()?.let { it in 1..500 } == true && calories.toIntOrNull()?.let { it in 500..10000 } == true && water.toIntOrNull()?.let { it in 250..10000 } == true

    val programOptions = remember(place, sex, equipment, selectedDays) {
        buildList {
            if (place == "GYM") {
                add("FULL_BODY")
                if (selectedDays.size >= 2) add("UPPER_LOWER")
                if (selectedDays.size >= 3) add("PPL")
                addAll(listOf("STRENGTH", "BASIC"))
            } else {
                add("BODYWEIGHT")
                if ("DUMBBELLS" in equipment) add("DUMBBELLS")
                if ("PULLUP_BAR" in equipment) add("PULLUP")
                if ("DUMBBELLS" in equipment && "PULLUP_BAR" in equipment) add("DUMBBELLS_PULLUP")
                add("HOME_MIX")
            }
            if (sex == "FEMALE") addAll(listOf("LEGS_GLUTES", "UPPER", "MOBILITY", "STRETCH", "YOGA_MOBILITY"))
        }.distinct()
    }
    LaunchedEffect(programOptions) { if (program !in programOptions) program = programOptions.firstOrNull() ?: "FULL_BODY" }

    fun tempProfile(): UserProfile = initial.copy(
        appLanguage = language,
        trainingPlace = place,
        homeEquipment = equipment,
        trainingDays = selectedDays,
        sex = sex,
        birthDate = normalizeBirthDate(birthDate),
        selectedProgram = if (manualPlan) "CUSTOM" else program,
        customWorkoutName = customName.trim(),
        fitnessGoals = fitnessGoals,
        sessionDurationMinutes = sessionDuration,
        dayFocus = dayFocus,
        selectedExerciseIds = selectedExercises
    )

    fun normalizeExerciseSelection() {
        val p = tempProfile().copy(selectedExerciseIds = emptyMap())
        val slots = exerciseSlotsForDuration(sessionDuration)
        val orderedDays = selectedDays.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.sortedBy { it.value }
        val updated = selectedExercises.toMutableMap()
        orderedDays.forEachIndexed { index, day ->
            if (updated[day.name].isNullOrEmpty()) {
                updated[day.name] = workoutCandidatesFor(p, day, index).take(slots).map { it.id }
            }
        }
        selectedExercises = updated
    }

    fun nextStep() {
        when (step) {
            0 -> step = 2
            2 -> step = if (place == "HOME") 3 else 4
            4 -> step = 5
            8 -> { if (!manualPlan) normalizeExerciseSelection(); step = 9 }
            12 -> step = 13
            else -> step++
        }
    }

    val canContinue = when (step) {
        1 -> language in setOf("RU", "EN")
        2 -> place in setOf("GYM", "HOME")
        3 -> equipment.isNotEmpty()
        4 -> selectedDays.isNotEmpty()
        5 -> bodyValid
        6 -> program.isNotBlank()
        7 -> fitnessGoals.isNotEmpty()
        8 -> sessionDuration in setOf(20, 30, 45, 60, 90)
        9 -> !manualPlan || (customName.isNotBlank() && selectedDays.all { day -> selectedExercises[day].orEmpty().isNotEmpty() && selectedExercises[day].orEmpty().distinct().size == selectedExercises[day].orEmpty().size })
        10 -> first.isNotBlank()
        11 -> nutritionValid
        12 -> termsAccepted && privacyAccepted
        else -> true
    }

    legalDialog?.let { type ->
        val terms = type == "terms"
        LegalDocumentDialog(
            title = if (terms) gs(language, R.string.terms_of_use) else gs(language, R.string.privacy_policy),
            body = if (terms) gs(language, R.string.terms_full_text) else gs(language, R.string.privacy_full_text),
            onClose = { legalDialog = null }
        )
    }

    if (step == 13) {
        val prepared = tempProfile().copy(
            firstName = first.trim(),
            lastName = last.trim(),
            proteinGoal = protein.toInt(),
            calorieGoal = calories.toInt(),
            waterGoalMl = water.toInt(),
            nutritionAutoTargets = false,
            fitnessGoals = fitnessGoals,
            sessionDurationMinutes = sessionDuration,
            termsAccepted = true,
            privacyAccepted = true,
            onboardingCompleted = true,
            nextMonthlyCheckInAt = nextMonthlyCheckInAt()
        )
        ExpressiveLoadingScreen(
            language = language,
            preparedProfile = prepared,
            onReady = { finalProfile, generatedPlan -> onFinish(finalProfile, weightKg.toDouble(), heightCm.toDouble(), generatedPlan) }
        )
        return
    }

    Scaffold(
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .96f), tonalElevation = 2.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExpressiveSurfaceButton(
                        onClick = ::nextStep,
                        enabled = canContinue,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            gs(language, if (step == 0) R.string.get_started else R.string.next_f0059de),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                    if (step == 0) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text(
                            gs(language, R.string.already_used_gymflow),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        TextButton(onClick = onImport) {
                            Icon(Icons.Rounded.UploadFile, null)
                            Spacer(Modifier.width(8.dp))
                            Text(gs(language, R.string.import_data))
                        }
                        }
                    }
                }
            }
        }
    ) { pad ->
        AnimatedContent(targetState = step, modifier = Modifier.fillMaxSize().padding(pad), transitionSpec = { directionalMotion(targetState > initialState) }, label = "onboarding_step") { current ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f)) { OnboardingTitle(titleFor(current, language), subtitleFor(current, language)) }
                        Box {
                            IconButton(onClick = { languageMenu = true }) { Icon(Icons.Rounded.Language, gs(language, R.string.language)) }
                            DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                                DropdownMenuItem(text = { Text("Русский") }, onClick = { language = "RU"; languageMenu = false })
                                DropdownMenuItem(text = { Text("English") }, onClick = { language = "EN"; languageMenu = false })
                            }
                        }
                    }
                }
                when (current) {
                    0 -> introContent(language)
                    2 -> item { PlaceCards(language, place) { place = it } }
                    3 -> item { EquipmentCards(language, equipment) { equipment = it } }
                    4 -> item { DayPicker(language, selectedDays) { selectedDays = it } }
                    5 -> item { BodyFields(language, birthDate, { birthDate = it }, sex, { sex = it }, heightCm, { heightCm = it }, weightKg, { weightKg = it }) }
                    6 -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChoiceCard(if (language == "EN") "Choose automatically" else "Подобрать автоматически", !manualPlan) { manualPlan = false }
                                ChoiceCard(if (language == "EN") "Build my own" else "Собрать самому", manualPlan) {
                                    manualPlan = true
                                    selectedExercises = selectedDays.associateWith { selectedExercises[it].orEmpty() }
                                }
                            }
                        }
                        if (!manualPlan) itemsIndexed(programOptions) { _, option -> ProgramCard(language, option, program == option) { program = option } }
                    }
                    7 -> item { FitnessGoalCards(language, fitnessGoals) { fitnessGoals = it } }
                    8 -> item { SessionDurationCards(language, sessionDuration) { sessionDuration = it } }
                    9 -> item {
                        if (manualPlan) OutlinedTextField(customName, { customName = it.take(70) }, Modifier.fillMaxWidth(), label = { Text(if (language == "EN") "Workout name" else "Название тренировки") }, singleLine = true)
                        WorkoutDayEditor(
                            l = language,
                            profile = tempProfile(),
                            selected = selectedExercises,
                            onSelectedChanged = { selectedExercises = it },
                            onFocusChanged = { day, focus -> dayFocus = dayFocus + (day to focus); selectedExercises = selectedExercises + (day to emptyList()) }
                        )
                    }
                    10 -> item { NameFields(language, first, { first = it }, last, { last = it }) }
                    11 -> item { NutritionGoalFields(language, protein, { protein = digits(it) }, calories, { calories = digits(it) }, water, { water = digits(it) }) }
                    12 -> item {
                        AgreementCard(language, termsAccepted, privacyAccepted, { termsAccepted = it }, { privacyAccepted = it }, { legalDialog = it })
                    }
                    else -> Unit
                }
            }
        }
    }
}

private fun titleFor(step: Int, l: String) = when (step) {
    0 -> "GymFlow"
    1 -> gs(l, R.string.choose_your_language)
    2 -> gs(l, R.string.where_do_you_train)
    3 -> gs(l, R.string.what_equipment_do_you_have)
    4 -> gs(l, R.string.when_do_you_want_to_train)
    5 -> gs(l, R.string.tell_us_about_yourself)
    6 -> gs(l, R.string.choose_a_program)
    7 -> gs(l, R.string.fitness_goals_title)
    8 -> gs(l, R.string.session_duration_title)
    9 -> gs(l, R.string.workouts_by_day)
    10 -> gs(l, R.string.what_should_we_call_you)
    11 -> gs(l, R.string.daily_goals)
    else -> gs(l, R.string.almost_ready)
}

private fun subtitleFor(step: Int, l: String) = when (step) {
    0 -> gs(l, R.string.intro_headline)
    1 -> gs(l, R.string.the_language_applies_immediately_across_the)
    2 -> gs(l, R.string.this_helps_choose_exercises_for_the_equipmen)
    4 -> gs(l, R.string.choose_at_least_one_day)
    5 -> gs(l, R.string.body_data_subtitle)
    7 -> gs(l, R.string.fitness_goals_subtitle)
    8 -> gs(l, R.string.session_duration_subtitle)
    9 -> gs(l, R.string.expand_a_day_to_disable_replace_or_reorder_e)
    11 -> gs(l, R.string.enter_your_own_targets_you_can_change_them_l)
    else -> ""
}

@Composable private fun OnboardingTitle(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) { Spacer(Modifier.height(7.dp)); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp) }
    }
}


private data class IntroTileData(val icon: ImageVector, val titleRes: Int, val textRes: Int)

private fun LazyListScope.introContent(language: String) {
    item { IntroHero(language) }

    item {
        Text(
            gs(language, R.string.intro_features_title),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val tiles = listOf(
        IntroTileData(Icons.Rounded.FitnessCenter, R.string.intro_training_title, R.string.intro_training_text),
        IntroTileData(Icons.Rounded.AvTimer, R.string.intro_workout_title, R.string.intro_workout_text),
        IntroTileData(Icons.Rounded.TrendingUp, R.string.intro_progress_title, R.string.intro_progress_text),
        IntroTileData(Icons.Rounded.Restaurant, R.string.intro_nutrition_title, R.string.intro_nutrition_text),
        IntroTileData(Icons.Rounded.QueueMusic, R.string.intro_music_title, R.string.intro_music_text),
        IntroTileData(Icons.Rounded.NotificationsActive, R.string.intro_reminders_title, R.string.intro_reminders_text),
        IntroTileData(Icons.Rounded.SaveAlt, R.string.intro_data_title, R.string.intro_data_text),
        IntroTileData(Icons.Rounded.Tune, R.string.intro_personalize_title, R.string.intro_personalize_text)
    )
    tiles.chunked(2).forEach { rowTiles ->
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowTiles.forEach { tile ->
                    IntroFeatureTile(language, tile, Modifier.weight(1f))
                }
                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    item {
        ExpressiveCard(
            Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(gs(language, R.string.intro_ready_title), fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Text(
                    gs(language, R.string.intro_ready_text),
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .78f),
                    lineHeight = 21.sp
                )
            }
        }
    }
}

@Composable
private fun IntroHero(language: String) {
    ExpressiveCard(
        Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Image(
                        painter = painterResource(R.drawable.gymflow_foreground_art),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("GymFlow", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        gs(language, R.string.intro_headline),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .85f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            Text(
                gs(language, R.string.intro_summary),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .82f),
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun IntroFeatureTile(language: String, data: IntroTileData, modifier: Modifier = Modifier) {
    ExpressiveCard(modifier, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(data.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Text(gs(language, data.titleRes), fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp)
            Text(
                gs(language, data.textRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable private fun LanguageCards(value: String, set: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoiceCard(gs(value, R.string.language_russian), value == "RU") { set("RU") }
        ChoiceCard(gs(value, R.string.language_english), value == "EN") { set("EN") }
    }
}

@Composable private fun PlaceCards(l: String, value: String, set: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BigChoice(Icons.Rounded.FitnessCenter, if (l == "EN") "Gym" else "Спортзал", gs(l, R.string.barbells_machines_dumbbells_and_other_equipm), value == "GYM") { set("GYM") }
        BigChoice(Icons.Rounded.Home, if (l == "EN") "Home" else "Дома", gs(l, R.string.bodyweight_dumbbells_a_pull_up_bar_and_minim), value == "HOME") { set("HOME") }
    }
}

@Composable private fun BigChoice(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    ExpressiveSurfaceButton(onClick, Modifier.fillMaxWidth().heightIn(min = 116.dp), containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .65f)) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp)) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 19.sp, minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (selected) { Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.Check, null, Modifier.size(20.dp)) }
        }
    }
}

@Composable private fun ChoiceCard(title: String, selected: Boolean, onClick: () -> Unit) = ExpressiveSurfaceButton(onClick, Modifier.fillMaxWidth(), containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold); RadioButton(selected, onClick = onClick) } }

@Composable private fun EquipmentCards(l: String, value: Set<String>, set: (Set<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        equipmentOptions.forEach { key ->
            val name = when (key) { "BODYWEIGHT" -> gs(l, R.string.bodyweight_only); "DUMBBELLS" -> gs(l, R.string.dumbbells); "PULLUP_BAR" -> gs(l, R.string.pull_up_bar); "BANDS" -> gs(l, R.string.resistance_bands); else -> gs(l, R.string.bench) }
            ExpressiveSurfaceButton({ set(if (key in value) value - key else value + key) }, Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(key in value, { checked -> set(if (checked) value + key else value - key) }); Spacer(Modifier.width(8.dp)); Text(name, fontSize = 17.sp) } }
        }
    }
}

@Composable
fun DayPicker(l: String, value: Set<String>, set: (Set<String>) -> Unit) {
    val weekdays = onboardingDays.filter { it.value <= 5 }.map { it.name }.toSet()
    val weekend = onboardingDays.filter { it.value >= 6 }.map { it.name }.toSet()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            onboardingDays.forEach { day ->
                val selected = day.name in value
                DayToggleBox(
                    label = dayShort(day, l),
                    selected = selected,
                    onClick = { set(if (selected) value - day.name else value + day.name) },
                    modifier = Modifier.width(52.dp)
                )
            }
        }
        Text(gs(l, R.string.days_selected, value.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SuggestionChip(onClick = { set(weekdays) }, label = { Text(gs(l, R.string.weekdays)) })
            SuggestionChip(onClick = { set(weekend) }, label = { Text(gs(l, R.string.weekend)) })
            TextButton(onClick = { set(emptySet()) }, enabled = value.isNotEmpty()) {
                Text(gs(l, R.string.clear), maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun DayToggleBox(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable private fun BodyFields(
    l: String,
    birthDate: String?,
    setBirthDate: (String) -> Unit,
    sex: String,
    setSex: (String) -> Unit,
    heightCm: Int,
    setHeight: (Int) -> Unit,
    weightKg: Int,
    setWeight: (Int) -> Unit
) {
    var showBirthDate by remember { mutableStateOf(false) }
    var showHeight by remember { mutableStateOf(false) }
    var showWeight by remember { mutableStateOf(false) }
    val birth = parseBirthDate(birthDate)
    val dateLabel = birth?.let { date ->
        val month = gsa(l, R.array.months_date)[date.monthValue - 1]
        gs(l, R.string.birth_date_display, date.dayOfMonth, month, date.year)
    } ?: gs(l, R.string.set_date_of_birth)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(gs(l, R.string.sex), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("MALE", "FEMALE").forEachIndexed { index, value ->
                SegmentedButton(selected = sex == value, onClick = { setSex(value) }, shape = SegmentedButtonDefaults.itemShape(index, 2), icon = {}) {
                    Text(if (l == "EN") { if (index == 0) "Male" else "Female" } else { if (index == 0) "Мужской" else "Женский" }, maxLines = 1, softWrap = false)
                }
            }
        }
        PickerValueCard(gs(l, R.string.height), gs(l, R.string.height_value, heightCm)) { showHeight = true }
        PickerValueCard(gs(l, R.string.weight), gs(l, R.string.weight_value, weightKg)) { showWeight = true }
        PickerValueCard(gs(l, R.string.birth_date), dateLabel) { showBirthDate = true }
    }

    if (showBirthDate) BirthDateWheelSheet(l, birthDate, { showBirthDate = false }, setBirthDate)
    if (showHeight) IntegerWheelSheet(
        language = l,
        title = gs(l, R.string.select_height),
        values = (100..240).toList(),
        selected = heightCm,
        label = { gs(l, R.string.height_value, it) },
        onDismiss = { showHeight = false },
        onSelected = setHeight
    )
    if (showWeight) IntegerWheelSheet(
        language = l,
        title = gs(l, R.string.select_weight),
        values = (20..350).toList(),
        selected = weightKg,
        label = { gs(l, R.string.weight_value, it) },
        onDismiss = { showWeight = false },
        onSelected = setWeight
    )
}

@Composable private fun SexChoice(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExpressiveSurfaceButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 66.dp),
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable private fun ProgramCard(l: String, program: String, selected: Boolean, onClick: () -> Unit) = ChoiceCard(programName(program, l), selected, onClick)

private val fitnessGoalOptions = listOf(
    Triple("MUSCLE", R.string.fitness_goal_muscle_title, R.string.fitness_goal_muscle_desc),
    Triple("STRENGTH", R.string.fitness_goal_strength_title, R.string.fitness_goal_strength_desc),
    Triple("ENDURANCE", R.string.fitness_goal_endurance_title, R.string.fitness_goal_endurance_desc),
    Triple("FAT_LOSS", R.string.fitness_goal_fat_loss_title, R.string.fitness_goal_fat_loss_desc),
    Triple("GENERAL", R.string.fitness_goal_general_title, R.string.fitness_goal_general_desc)
)

@Composable
private fun FitnessGoalCards(l: String, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        fitnessGoalOptions.forEach { (id, titleRes, descRes) ->
            val isOn = id in selected
            ExpressiveSurfaceButton(
                onClick = {
                    onChange(if (isOn) selected - id else selected + id)
                },
                modifier = Modifier.fillMaxWidth(),
                containerColor = if (isOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(gs(l, titleRes), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(gs(l, descRes), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    Checkbox(checked = isOn, onCheckedChange = {
                        onChange(if (isOn) selected - id else selected + id)
                    })
                }
            }
        }
    }
}

private val sessionDurationOptions = listOf(20, 30, 45, 60, 90)

@Composable
private fun SessionDurationCards(l: String, selected: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sessionDurationOptions.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { mins ->
                    val isOn = selected == mins
                    ExpressiveSurfaceButton(
                        onClick = { onChange(mins) },
                        modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                        containerColor = if (isOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                gs(l, R.string.session_duration_minutes_label, mins),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                when (mins) {
                                    20 -> gs(l, R.string.session_duration_compact)
                                    30 -> gs(l, R.string.session_duration_short)
                                    45 -> gs(l, R.string.session_duration_standard)
                                    60 -> gs(l, R.string.session_duration_extended)
                                    else -> gs(l, R.string.session_duration_long)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable private fun WorkoutDayEditor(l: String, profile: UserProfile, selected: Map<String, List<String>>, onSelectedChanged: (Map<String,List<String>>) -> Unit, onFocusChanged: (String, String) -> Unit) {
    val orderedDays = profile.trainingDays.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.sortedBy { it.value }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        orderedDays.forEachIndexed { index, day ->
            var expanded by remember(day) { mutableStateOf(false) }
            val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, GymGlowMotion.fastSpatial(), label = "day_expand_arrow")
            val all = workoutCandidatesFor(profile.copy(selectedExerciseIds = emptyMap()), day, index)
            val ids = if (selected.containsKey(day.name)) selected[day.name].orEmpty().distinct() else if (profile.selectedProgram == "CUSTOM") emptyList() else all.take(7).map { it.id }
            val chosen = ids.mapNotNull { id -> all.firstOrNull { it.id == id } }
            Surface(Modifier.fillMaxWidth().animateContentSize(GymGlowMotion.defaultSpatial()), shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column {
                    Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(dayName(day, l), fontWeight = FontWeight.Bold, fontSize = 19.sp); Text(chosen.joinToString(" • ") { exerciseMuscle(it, l).lowercase().replaceFirstChar { c -> c.uppercase() } }.take(60), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(arrowRotation))
                    }
                    AnimatedVisibility(expanded, enter = expandMotion(), exit = collapseMotion()) {
                        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (profile.selectedProgram == "CUSTOM") {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Всё тело", "Верх тела", "Грудь · Трицепс", "Спина · Бицепс", "Ноги · Ягодицы", "Мобилити · Растяжка").forEach { focus ->
                                        FilterChip(selected = (profile.dayFocus[day.name] ?: "Всё тело") == focus, onClick = { onFocusChanged(day.name, focus) }, label = { Text(if (l == "EN") mapOf("Всё тело" to "Full body", "Верх тела" to "Upper body", "Грудь · Трицепс" to "Chest · Triceps", "Спина · Бицепс" to "Back · Biceps", "Ноги · Ягодицы" to "Legs · Glutes", "Мобилити · Растяжка" to "Mobility · Stretching").getValue(focus) else focus, maxLines = 1, softWrap = false) })
                                    }
                                }
                            }
                            chosen.forEach { exercise ->
                                val enabled = exercise.id in ids
                                if (enabled) {
                                    val pos = ids.indexOf(exercise.id)
                                    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                                        Column(Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(true, { checked -> if (!checked) onSelectedChanged(selected + (day.name to ids.filterNot { it == exercise.id })) })
                                                Column(Modifier.weight(1f)) { Text(exerciseTitle(exercise,l), fontWeight = FontWeight.SemiBold); Text("${exercise.sets} × ${exerciseReps(exercise,l)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                                                IconButton(onClick = { if (pos > 0) { val m=ids.toMutableList(); val x=m.removeAt(pos); m.add(pos-1,x); onSelectedChanged(selected + (day.name to m)) } }) { Icon(Icons.Rounded.KeyboardArrowUp, gs(l, R.string.move_up)) }
                                                IconButton(onClick = { if (pos in 0 until ids.lastIndex) { val m=ids.toMutableList(); val x=m.removeAt(pos); m.add(pos+1,x); onSelectedChanged(selected + (day.name to m)) } }) { Icon(Icons.Rounded.KeyboardArrowDown, gs(l, R.string.move_down)) }
                                            }
                                            TextButton(onClick = {
                                                val replacement = all.firstOrNull { it.id !in ids }
                                                if (replacement != null && pos >= 0) { val m=ids.toMutableList(); m[pos]=replacement.id; onSelectedChanged(selected + (day.name to m)) }
                                            }) { Icon(Icons.Rounded.SwapHoriz, null); Spacer(Modifier.width(6.dp)); Text(gs(l, R.string.replace)) }
                                        }
                                    }
                                }
                            }
                            val disabled = all.filter { it.id !in ids }
                            if (disabled.isNotEmpty()) {
                                Text(gs(l, R.string.add_exercise), fontWeight = FontWeight.SemiBold)
                                disabled.forEach { ex -> TextButton(onClick = { onSelectedChanged(selected + (day.name to (ids + ex.id))) }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text(exerciseTitle(ex,l)) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun NameFields(l: String, first: String, setFirst: (String)->Unit, last: String, setLast:(String)->Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(first,setFirst,Modifier.fillMaxWidth(),label={Text(gs(l, R.string.first_name))},singleLine=true,shape=RoundedCornerShape(22.dp))
        OutlinedTextField(last,setLast,Modifier.fillMaxWidth(),label={Text(gs(l, R.string.last_name))},singleLine=true,shape=RoundedCornerShape(22.dp))
        Text(gs(l, R.string.your_name_is_used_in_your_profile_home_scree),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
    }
}

@Composable private fun NutritionGoalFields(l:String, protein:String,setProtein:(String)->Unit,calories:String,setCalories:(String)->Unit,water:String,setWater:(String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(protein,setProtein,Modifier.fillMaxWidth(),label={Text(gs(l, R.string.protein_g))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,shape=RoundedCornerShape(22.dp))
        OutlinedTextField(calories,setCalories,Modifier.fillMaxWidth(),label={Text(gs(l, R.string.calories_kcal))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,shape=RoundedCornerShape(22.dp))
        OutlinedTextField(water,setWater,Modifier.fillMaxWidth(),label={Text(gs(l, R.string.water_ml))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,shape=RoundedCornerShape(22.dp))
    }
}

@Composable private fun AgreementCard(l:String, terms:Boolean, privacy:Boolean,setTerms:(Boolean)->Unit,setPrivacy:(Boolean)->Unit, open:(String)->Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LegalAgreementRow(gs(l, R.string.terms_of_use), if (l == "EN") "Rules for using GymFlow" else "Правила использования GymFlow", terms, setTerms, { open("terms") }, l)
        LegalAgreementRow(gs(l, R.string.privacy_policy), if (l == "EN") "How local data is handled" else "Как обрабатываются локальные данные", privacy, setPrivacy, { open("privacy") }, l)
    }
}

@Composable private fun LegalAgreementRow(title: String, description: String, checked: Boolean, onChecked: (Boolean)->Unit, onOpen: ()->Unit, l: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked, onChecked)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                TextButton(onClick = onOpen) { Text(if (l == "EN") "Open" else "Открыть", maxLines = 1) }
            }
        }
    }
}

@Composable internal fun LegalDocumentDialog(title: String, body: String, onClose: ()->Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, title) }
                    Text(title, Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    body.split("\n\n").forEach { paragraph -> Text(paragraph, fontSize = 16.sp, lineHeight = 25.sp) }
                }
            }
        }
    }
}

@Composable private fun ExpressiveLoadingScreen(
    language:String,
    preparedProfile:UserProfile,
    onReady:suspend (UserProfile, List<WorkoutDay>)->Unit
) {
    val messages = listOf(
        gs(language, R.string.analyzing_your_data),
        gs(language, R.string.choosing_your_program),
        gs(language, R.string.distributing_exercises),
        gs(language, R.string.setting_your_goals),
        gs(language, R.string.preparing_gymflow)
    )
    var messageIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(preparedProfile) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        messageIndex = 0
        require(preparedProfile.trainingDays.isNotEmpty())
        require(preparedProfile.proteinGoal > 0 && preparedProfile.calorieGoal > 0 && preparedProfile.waterGoalMl > 0)
        yield()

        messageIndex = 1
        val generated = withContext(Dispatchers.Default) { workoutsFor(preparedProfile) }
        require(generated.isNotEmpty())
        yield()

        messageIndex = 2
        require(generated.all { it.exercises.isNotEmpty() })
        yield()

        messageIndex = 3
        require(preparedProfile.termsAccepted && preparedProfile.privacyAccepted)
        yield()

        messageIndex = 4
        primeWorkoutPlanCache(preparedProfile, generated)
        delay((1500L - (android.os.SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0L))
        onReady(preparedProfile, generated)
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            GymFlowMorphingShape(Modifier.size(126.dp))
            Spacer(Modifier.height(30.dp))
            AnimatedContent(messageIndex,transitionSpec={effectsMotion()},label="loading_message") { i -> Text(messages[i],fontSize=18.sp,fontWeight=FontWeight.SemiBold) }
        }
    }
}

private fun numeric(raw:String)=raw.replace(',','.').filter{it.isDigit()||it=='.'}.take(7)
private fun digits(raw:String)=raw.filter(Char::isDigit).take(6)
