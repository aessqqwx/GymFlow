package com.aess.gymflow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private fun nutritionToday(millis: Long): Boolean =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    profile: UserProfile,
    progress: List<ProgressEntry>,
    nutrition: List<NutritionEntry>,
    onAdd: (NutritionEntry) -> Unit,
    onDelete: (Long) -> Unit,
    onProfileChange: (UserProfile) -> Unit
) {
    val l = profile.appLanguage
    var showEditor by remember { mutableStateOf(false) }
    val today = nutrition.filter { nutritionToday(it.createdAt) }
    val visibleEntries = today.filter { it.note != "quick_adjust" }
    val calories = today.sumOf { it.calories }.coerceAtLeast(0)
    val protein = today.sumOf { it.proteinG }.coerceAtLeast(0.0)
    val water = today.sumOf { it.waterMl }.coerceAtLeast(0)
    val pGoal = profile.proteinGoal.coerceAtLeast(1)
    val cGoal = profile.calorieGoal.coerceAtLeast(1)
    val wGoal = profile.waterGoalMl.coerceAtLeast(1)

    fun addDelta(c: Int = 0, p: Double = 0.0, w: Int = 0) {
        val safeC = if (c < 0) -minOf(-c, calories) else c
        val safeP = if (p < 0) -minOf(-p, protein) else p
        val safeW = if (w < 0) -minOf(-w, water) else w
        if (safeC != 0 || safeP != 0.0 || safeW != 0) {
            onAdd(NutritionEntry(System.currentTimeMillis(), System.currentTimeMillis(), safeC, safeP, safeW, "quick_adjust"))
        }
    }

    if (showEditor) {
        NutritionEditorSheet(
            l = l,
            profile = profile,
            onDismiss = { showEditor = false },
            onProfileChange = onProfileChange,
            onAdd = { c, p, w, note ->
                onAdd(NutritionEntry(System.currentTimeMillis(), System.currentTimeMillis(), c, p, w, note))
                showEditor = false
            }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(gs(l, R.string.nutrition_294139b), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(gs(l, R.string.today_protein_calories_and_water_21f9076), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showEditor = true }) { Icon(Icons.Rounded.Edit, gs(l, R.string.edit_a190f4d)) }
            }
        }
        item {
            ExpressiveCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer, corner = 34.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(gs(l, R.string.daily_target), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    NutritionMetric(
                        title = gs(l, R.string.protein),
                        value = "${protein.roundToInt()} ${gs(l, R.string.g)} / $pGoal ${gs(l, R.string.g)}",
                        progress = (protein / pGoal).toFloat(),
                        buttons = listOf(gs(l, R.string.text_10_g_927c88c) to -10.0, gs(l, R.string.text_10_g) to 10.0),
                        onDelta = { addDelta(p = it) }
                    )
                    NutritionMetric(
                        title = gs(l, R.string.calories),
                        value = "$calories / $cGoal ${gs(l, R.string.kcal)}",
                        progress = calories.toFloat() / cGoal,
                        buttons = listOf(gs(l, R.string.text_100_kcal_0579299) to -100.0, gs(l, R.string.text_100_kcal) to 100.0),
                        onDelta = { addDelta(c = it.toInt()) }
                    )
                    NutritionMetric(
                        title = gs(l, R.string.water),
                        value = "$water ${gs(l, R.string.ml)} / $wGoal ${gs(l, R.string.ml)}",
                        progress = water.toFloat() / wGoal,
                        buttons = listOf(gs(l, R.string.text_250_ml_dd3b388) to -250.0, gs(l, R.string.text_250_ml) to 250.0, gs(l, R.string.text_500_ml) to 500.0),
                        onDelta = { addDelta(w = it.toInt()) }
                    )
                }
            }
        }
        item { SectionNutrition(gs(l, R.string.after_workout), Icons.Rounded.Restaurant) }
        item { FoodIdea(gs(l, R.string.eggs_bread_vegetables), gs(l, R.string.protein_carbs_vegetables_666dabf)) }
        item { FoodIdea(gs(l, R.string.cottage_cheese_fruit), gs(l, R.string.protein_carbs)) }
        if (visibleEntries.isNotEmpty()) item { Text(gs(l, R.string.today_s_entries), fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        items(visibleEntries, key = { it.id }) { e ->
            ExpressiveCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.note.ifBlank { gs(l, R.string.nutrition_entry) }, fontWeight = FontWeight.SemiBold)
                        Text("${e.proteinG.roundToInt()} ${gs(l, R.string.g_protein)} • ${e.calories} ${gs(l, R.string.kcal)} • ${e.waterMl} ${gs(l, R.string.ml)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    TextButton(onClick = { onDelete(e.id) }) { Text(gs(l, R.string.delete)) }
                }
            }
        }
    }
}

@Composable
private fun NutritionMetric(title: String, value: String, progress: Float, buttons: List<Pair<String, Double>>, onDelta: (Double) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(value, fontWeight = FontWeight.SemiBold) }
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            buttons.forEach { (label, delta) ->
                FilledTonalButton(onClick = { onDelta(delta) }, shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)) { Text(label) }
            }
        }
    }
}

@Composable private fun SectionNutrition(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.width(9.dp)); Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}
@Composable private fun FoodIdea(title: String, subtitle: String) {
    ExpressiveCard(Modifier.fillMaxWidth()) { Column { Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp); Spacer(Modifier.height(3.dp)); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NutritionEditorSheet(
    l: String,
    profile: UserProfile,
    onDismiss: () -> Unit,
    onProfileChange: (UserProfile) -> Unit,
    onAdd: (Int, Double, Int, String) -> Unit
) {
    var protein by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var water by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var gp by remember { mutableStateOf(profile.proteinGoal.toString()) }
    var gc by remember { mutableStateOf(profile.calorieGoal.toString()) }
    var gw by remember { mutableStateOf(profile.waterGoalMl.toString()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text(gs(l, R.string.edit_nutrition), fontSize = 25.sp, fontWeight = FontWeight.Bold) }
            item { Text(gs(l, R.string.today_8dd7bf1), fontWeight = FontWeight.Bold) }
            item { OutlinedTextField(protein, { protein = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.protein_g)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(20.dp)) }
            item { OutlinedTextField(calories, { calories = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.calories_kcal)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(20.dp)) }
            item { OutlinedTextField(water, { water = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.water_ml)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(20.dp)) }
            item { OutlinedTextField(note, { note = it.take(80) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.note_90fdad8)) }, shape = RoundedCornerShape(20.dp)) }
            item {
                ExpressiveSurfaceButton(onClick = { onAdd(calories.toIntOrNull() ?: 0, protein.replace(',', '.').toDoubleOrNull() ?: 0.0, water.toIntOrNull() ?: 0, note) }, modifier = Modifier.fillMaxWidth()) { Text(gs(l, R.string.add_entry), fontWeight = FontWeight.Bold) }
            }
            item { HorizontalDivider(); Text(gs(l, R.string.daily_goals), fontWeight = FontWeight.Bold, fontSize = 19.sp) }
            item { OutlinedTextField(gp, { gp = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.protein_g)) }, shape = RoundedCornerShape(20.dp)) }
            item { OutlinedTextField(gc, { gc = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.calories_kcal)) }, shape = RoundedCornerShape(20.dp)) }
            item { OutlinedTextField(gw, { gw = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text(gs(l, R.string.water_ml)) }, shape = RoundedCornerShape(20.dp)) }
            item {
                TextButton(
                    onClick = {
                        val p = gp.toIntOrNull(); val c = gc.toIntOrNull(); val w = gw.toIntOrNull()
                        if (p != null && c != null && w != null) onProfileChange(profile.copy(proteinGoal = p, calorieGoal = c, waterGoalMl = w, nutritionAutoTargets = false))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(gs(l, R.string.save_targets)) }
            }
        }
    }
}
