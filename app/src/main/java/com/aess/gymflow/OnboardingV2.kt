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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var place by remember { mutableStateOf(initial.trainingPlace) }
    var equipment by remember { mutableStateOf(initial.homeEquipment) }
    var selectedDays by remember { mutableStateOf(initial.trainingDays) }
    var sex by remember { mutableStateOf(if (initial.sex == "UNSPECIFIED") "MALE" else initial.sex) }
    var birthDate by remember { mutableStateOf(normalizeBirthDate(initial.birthDate)) }
    var weightKg by remember { mutableIntStateOf(70) }
    var heightCm by remember { mutableIntStateOf(175) }
    var program by remember { mutableStateOf(initial.selectedProgram) }
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
        selectedProgram = program,
        dayFocus = dayFocus,
        selectedExerciseIds = selectedExercises
    )

    fun normalizeExerciseSelection() {
        val p = tempProfile().copy(selectedExerciseIds = emptyMap())
        val orderedDays = selectedDays.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.sortedBy { it.value }
        val updated = selectedExercises.toMutableMap()
        orderedDays.forEachIndexed { index, day ->
            if (updated[day.name].isNullOrEmpty()) updated[day.name] = workoutCandidatesFor(p, day, index).take(7).map { it.id }
        }
        selectedExercises = updated
    }

    fun nextStep() {
        when (step) {
            2 -> step = if (place == "HOME") 3 else 4
            4 -> step = 5
            6 -> { normalizeExerciseSelection(); step = 7 }
            10 -> step = 11
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
        8 -> first.isNotBlank()
        9 -> nutritionValid
        10 -> termsAccepted && privacyAccepted
        else -> true
    }

    legalDialog?.let { type ->
        val terms = type == "terms"
        AlertDialog(
            onDismissRequest = { legalDialog = null },
            confirmButton = { TextButton(onClick = { legalDialog = null }) { Text(gs(language, R.string.close)) } },
            title = { Text(if (terms) gs(language, R.string.terms_of_use) else gs(language, R.string.privacy_policy)) },
            text = {
                Text(
                    if (terms) gs(language, R.string.terms_full_text) else gs(language, R.string.privacy_full_text),
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    lineHeight = 20.sp
                )
            }
        )
    }

    if (step == 11) {
        val prepared = tempProfile().copy(
            firstName = first.trim(),
            lastName = last.trim(),
            proteinGoal = protein.toInt(),
            calorieGoal = calories.toInt(),
            waterGoalMl = water.toInt(),
            nutritionAutoTargets = false,
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
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveSurfaceButton(
                        onClick = ::nextStep,
                        enabled = canContinue,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
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
                        Text(
                            gs(language, R.string.already_used_gymflow),
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        TextButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.UploadFile, null)
                            Spacer(Modifier.width(8.dp))
                            Text(gs(language, R.string.import_data))
                        }
                    }
                }
            }
        }
    ) { pad ->
        AnimatedContent(targetState = step, modifier = Modifier.fillMaxSize().padding(pad), label = "onboarding_step") { current ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { OnboardingTitle(titleFor(current, language), subtitleFor(current, language)) }
                when (current) {
                    0 -> introContent(language)
                    1 -> item { LanguageCards(language) { language = it } }
                    2 -> item { PlaceCards(language, place) { place = it } }
                    3 -> item { EquipmentCards(language, equipment) { equipment = it } }
                    4 -> item { DayPicker(language, selectedDays) { selectedDays = it } }
                    5 -> item { BodyFields(language, birthDate, { birthDate = it }, sex, { sex = it }, heightCm, { heightCm = it }, weightKg, { weightKg = it }) }
                    6 -> itemsIndexed(programOptions) { _, option -> ProgramCard(language, option, program == option) { program = option } }
                    7 -> item {
                        WorkoutDayEditor(
                            l = language,
                            profile = tempProfile(),
                            selected = selectedExercises,
                            onSelectedChanged = { selectedExercises = it }
                        )
                    }
                    8 -> item { NameFields(language, first, { first = it }, last, { last = it }) }
                    9 -> item { NutritionGoalFields(language, protein, { protein = digits(it) }, calories, { calories = digits(it) }, water, { water = digits(it) }) }
                    10 -> item {
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
    7 -> gs(l, R.string.workouts_by_day)
    8 -> gs(l, R.string.what_should_we_call_you)
    9 -> gs(l, R.string.daily_goals)
    else -> gs(l, R.string.almost_ready)
}

private fun subtitleFor(step: Int, l: String) = when (step) {
    0 -> gs(l, R.string.intro_headline)
    1 -> gs(l, R.string.the_language_applies_immediately_across_the)
    2 -> gs(l, R.string.this_helps_choose_exercises_for_the_equipmen)
    4 -> gs(l, R.string.choose_at_least_one_day)
    5 -> gs(l, R.string.body_data_subtitle)
    7 -> gs(l, R.string.expand_a_day_to_disable_replace_or_reorder_e)
    9 -> gs(l, R.string.enter_your_own_targets_you_can_change_them_l)
    else -> ""
}

@Composable private fun OnboardingTitle(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) { Spacer(Modifier.height(7.dp)); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp) }
    }
}

private fun LazyListScope.introContent(language: String) {
    item {
        ExpressiveCard(
            Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Spacer(Modifier.width(13.dp))
                    Text(
                        gs(language, R.string.intro_why_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    gs(language, R.string.intro_summary),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .82f),
                    lineHeight = 21.sp
                )
            }
        }
    }

    item {
        IntroFeatureCard(
            icon = Icons.Rounded.FitnessCenter,
            title = gs(language, R.string.intro_training_title),
            text = gs(language, R.string.intro_training_text),
            badges = listOf(
                gs(language, R.string.gym),
                gs(language, R.string.home),
                gs(language, R.string.dumbbells),
                gs(language, R.string.pull_up_bar)
            ),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
    item {
        IntroFeatureCard(
            icon = Icons.Rounded.Timer,
            title = gs(language, R.string.intro_workout_title),
            text = gs(language, R.string.intro_workout_text),
            badges = listOf(
                gs(language, R.string.intro_set_preview),
                gs(language, R.string.quick_instructions),
                gs(language, R.string.rest)
            ),
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    }
    item {
        IntroFeatureCard(
            icon = Icons.Rounded.Monitoring,
            title = gs(language, R.string.intro_progress_title),
            text = gs(language, R.string.intro_progress_text),
            badges = listOf(
                gs(language, R.string.measurements),
                gs(language, R.string.personal_records),
                gs(language, R.string.goals),
                gs(language, R.string.workout_history)
            ),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
    item {
        IntroFeatureCard(
            icon = Icons.Rounded.Restaurant,
            title = gs(language, R.string.intro_nutrition_title),
            text = gs(language, R.string.intro_nutrition_text),
            badges = listOf(
                gs(language, R.string.protein),
                gs(language, R.string.calories),
                gs(language, R.string.water)
            ),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    }
    item {
        IntroFeatureCard(
            icon = Icons.Rounded.MusicNote,
            title = gs(language, R.string.intro_music_title),
            text = gs(language, R.string.intro_music_text),
            badges = listOf(
                gs(language, R.string.mini_player),
                gs(language, R.string.queue),
                gs(language, R.string.shuffle),
                gs(language, R.string.repeat)
            ),
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    }
    item {
        IntroFeatureCard(
            icon = Icons.Rounded.Notifications,
            title = gs(language, R.string.intro_reminders_title),
            text = gs(language, R.string.intro_reminders_text),
            badges = listOf(
                gs(language, R.string.workouts),
                gs(language, R.string.protein),
                gs(language, R.string.measurements),
                gs(language, R.string.motivation_14df0e8)
            ),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
    item {
        IntroFeatureCard(
            icon = Icons.Rounded.Backup,
            title = gs(language, R.string.intro_data_title),
            text = gs(language, R.string.intro_data_text),
            badges = listOf(
                gs(language, R.string.export),
                gs(language, R.string.import_action),
                gs(language, R.string.intro_local_storage_badge)
            ),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    }
    item {
        IntroFeatureCard(
            icon = Icons.Rounded.Palette,
            title = gs(language, R.string.intro_personalize_title),
            text = gs(language, R.string.intro_personalize_text),
            badges = listOf(
                "RU / EN",
                gs(language, R.string.theme),
                gs(language, R.string.color_scheme),
                gs(language, R.string.text_size)
            ),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
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
private fun IntroFeatureCard(
    icon: ImageVector,
    title: String,
    text: String,
    badges: List<String>,
    containerColor: androidx.compose.ui.graphics.Color
) {
    ExpressiveCard(Modifier.fillMaxWidth(), containerColor = containerColor) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                badges.forEach { badge ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)
                    ) {
                        Text(
                            badge,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
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
        BigChoice(Icons.Rounded.FitnessCenter, gs(l, R.string.gym), gs(l, R.string.barbells_machines_dumbbells_and_other_equipm), value == "GYM") { set("GYM") }
        BigChoice(Icons.Rounded.Home, gs(l, R.string.home), gs(l, R.string.bodyweight_dumbbells_a_pull_up_bar_and_minim), value == "HOME") { set("HOME") }
    }
}

@Composable private fun BigChoice(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    ExpressiveSurfaceButton(onClick, Modifier.fillMaxWidth(), containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(34.dp)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp); Spacer(Modifier.height(4.dp)); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 19.sp) }; RadioButton(selected, onClick = onClick) }
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

@Composable private fun DayPicker(l: String, value: Set<String>, set: (Set<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        onboardingDays.forEach { day -> FilterChip(selected = day.name in value, onClick = { set(if (day.name in value) value - day.name else value + day.name) }, label = { Text(dayShort(day, l)) }) }
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
        PickerValueCard(gs(l, R.string.birth_date), dateLabel) { showBirthDate = true }
        Text(gs(l, R.string.sex), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SexChoice(gs(l, R.string.male), sex == "MALE", { setSex("MALE") }, Modifier.weight(1f))
            SexChoice(gs(l, R.string.female), sex == "FEMALE", { setSex("FEMALE") }, Modifier.weight(1f))
        }
        PickerValueCard(gs(l, R.string.height), gs(l, R.string.height_value, heightCm)) { showHeight = true }
        PickerValueCard(gs(l, R.string.weight), gs(l, R.string.weight_value, weightKg)) { showWeight = true }
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

@Composable private fun WorkoutDayEditor(l: String, profile: UserProfile, selected: Map<String, List<String>>, onSelectedChanged: (Map<String,List<String>>) -> Unit) {
    val orderedDays = profile.trainingDays.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.sortedBy { it.value }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        orderedDays.forEachIndexed { index, day ->
            var expanded by remember(day) { mutableStateOf(false) }
            val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(220), label = "day_expand_arrow")
            val all = workoutCandidatesFor(profile.copy(selectedExerciseIds = emptyMap()), day, index)
            val ids = selected[day.name].orEmpty().ifEmpty { all.take(7).map { it.id } }
            val chosen = ids.mapNotNull { id -> all.firstOrNull { it.id == id } }
            Surface(Modifier.fillMaxWidth().animateContentSize(tween(220)), shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column {
                    Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(dayName(day, l), fontWeight = FontWeight.Bold, fontSize = 19.sp); Text(chosen.joinToString(" • ") { exerciseMuscle(it, l).lowercase().replaceFirstChar { c -> c.uppercase() } }.take(60), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(arrowRotation))
                    }
                    AnimatedVisibility(expanded) {
                        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            all.forEach { exercise ->
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
                                disabled.take(3).forEach { ex -> TextButton(onClick = { onSelectedChanged(selected + (day.name to (ids + ex.id))) }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text(exerciseTitle(ex,l)) } }
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
    ExpressiveCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(terms,setTerms);Column(Modifier.weight(1f)){Text(gs(l, R.string.i_accept_the_terms_of_use));TextButton(onClick={open("terms")},contentPadding=PaddingValues(0.dp)){Text(gs(l, R.string.open_terms))}}}
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(privacy,setPrivacy);Column(Modifier.weight(1f)){Text(gs(l, R.string.i_have_read_the_privacy_policy));TextButton(onClick={open("privacy")},contentPadding=PaddingValues(0.dp)){Text(gs(l, R.string.open_privacy_policy))}}}
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
        onReady(preparedProfile, generated)
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            GymFlowMorphingShape(Modifier.size(126.dp))
            Spacer(Modifier.height(30.dp))
            AnimatedContent(messageIndex,label="loading_message") { i -> Text(messages[i],fontSize=18.sp,fontWeight=FontWeight.SemiBold) }
        }
    }
}

private fun numeric(raw:String)=raw.replace(',','.').filter{it.isDigit()||it=='.'}.take(7)
private fun digits(raw:String)=raw.filter(Char::isDigit).take(6)
