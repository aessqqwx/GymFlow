package com.aess.gymflow

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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

private fun workoutMinutes(day: WorkoutDay): Int {
    val workSeconds = day.exercises.sumOf { it.sets * 45 }
    val restSeconds = day.exercises.sumOf { ex -> (ex.sets - 1).coerceAtLeast(0) * ex.restSec }
    return ((workSeconds + restSeconds) / 60.0).roundToInt().coerceAtLeast(12)
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
    val workouts=workoutsFor(profile)
    val todayWorkout=workouts.firstOrNull{it.dayOfWeek==today.dayOfWeek}
    val primary=todayWorkout ?: workouts.minByOrNull { ((it.dayOfWeek.value-today.dayOfWeek.value)+7)%7 }
    val yesterday=today.minusDays(1)
    val yesterdayProtein=nutritionOnDate(nutrition,yesterday).sumOf{it.proteinG}.coerceAtLeast(0.0)
    val proteinMissing=yesterdayProtein<=0.0
    val latestProgress=progress.maxByOrNull{it.createdAt}
    val measurementCutoff = today.minusMonths(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val measurementsDue=latestProgress==null || latestProgress.createdAt <= measurementCutoff
    var showProteinSheet by remember{mutableStateOf(false)}
    var proteinInput by remember{mutableStateOf("")}

    if(showProteinSheet) ModalBottomSheet(onDismissRequest={showProteinSheet=false}) {
        Column(Modifier.fillMaxWidth().padding(22.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(gs(l, R.string.yesterday_s_protein),fontSize=24.sp,fontWeight=FontWeight.Bold)
            OutlinedTextField(proteinInput,{proteinInput=it.filter{c->c.isDigit()||c=='.'||c==','}},Modifier.fillMaxWidth(),label={Text(gs(l, R.string.protein_g))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),shape=RoundedCornerShape(22.dp))
            ExpressiveSurfaceButton(onClick={ val v=proteinInput.replace(',','.').toDoubleOrNull(); if(v!=null&&v>=0){ val noon=yesterday.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); onAddNutrition(NutritionEntry(System.currentTimeMillis(),noon,0,v,0,"protein_backfill"));showProteinSheet=false }},modifier=Modifier.fillMaxWidth()){Text(gs(l, R.string.save),fontWeight=FontWeight.SemiBold)}
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=18.dp),
        contentPadding=PaddingValues(top=14.dp,bottom=132.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(gs(l, R.string.app_name),fontSize=34.sp,fontWeight=FontWeight.Bold)
                    Text(dateLabel(today,l),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=16.sp)
                    Text(dayName(today.dayOfWeek,l),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp)
                }
                IconButton(onClick=onOpenSettings){Icon(Icons.Rounded.Settings,gs(l, R.string.settings))}
                Spacer(Modifier.width(4.dp))
                AvatarButton(profile,onOpenProfile)
            }
        }
        if(primary!=null) item {
            ExpressiveCard(Modifier.fillMaxWidth(),containerColor=MaterialTheme.colorScheme.primaryContainer,corner=34.dp) {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(dayShort(primary.dayOfWeek,l),fontWeight=FontWeight.ExtraBold,fontSize=18.sp)
                        Spacer(Modifier.weight(1f))
                        Surface(shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.surface.copy(alpha=.65f)){Text(if(todayWorkout!=null)gs(l, R.string.today) else gs(l, R.string.next),Modifier.padding(horizontal=11.dp,vertical=5.dp),fontWeight=FontWeight.Bold,fontSize=11.sp)}
                    }
                    Text(workoutCompactTitle(primary,l),fontSize=28.sp,lineHeight=31.sp,fontWeight=FontWeight.Bold)
                    Text(gs(l, R.string.stages_minutes, primary.exercises.size, workoutMinutes(primary)),color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=.75f))
                    val mainRest=primary.exercises.filter{it.sets>1}.map{it.restSec}.maxOrNull()?.div(60)?.coerceAtLeast(1)?:1
                    Text(gs(l, R.string.rest_between_sets_minutes, mainRest),color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=.75f))
                    Spacer(Modifier.height(5.dp))
                    ExpressiveSurfaceButton(onClick={onOpenWorkout(primary)},modifier=Modifier.fillMaxWidth(),containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary) {
                        Text(if(activeState?.dayKey==primary.key)gs(l, R.string.continue_workout) else gs(l, R.string.start_workout),fontWeight=FontWeight.Bold)
                    }
                }
            }
        }
        if(proteinMissing) item {
            WarningCard(Icons.Rounded.Restaurant,gs(l, R.string.no_protein_data_for_yesterday),gs(l, R.string.add_a_value_to_keep_your_stats_complete),gs(l, R.string.add)){showProteinSheet=true}
        }
        if(measurementsDue) item {
            WarningCard(Icons.Rounded.Straighten,gs(l, R.string.time_to_update_measurements),gs(l, R.string.your_latest_measurements_were_added_a_while),gs(l, R.string.add_measurements),onOpenMeasurements)
        }
        item { SectionTitle(gs(l, R.string.week)) }
        items(workouts,key={it.key}) { day ->
            ExpressiveSurfaceButton(onClick={onOpenWorkout(day)},modifier=Modifier.fillMaxWidth(),containerColor=MaterialTheme.colorScheme.surfaceVariant,contentPadding=PaddingValues(16.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Surface(shape=CircleShape,color=MaterialTheme.colorScheme.secondaryContainer){Box(Modifier.size(50.dp),contentAlignment=Alignment.Center){Text(dayShort(day.dayOfWeek,l),fontWeight=FontWeight.Bold)}}
                    Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(workoutCompactTitle(day,l),fontWeight=FontWeight.Bold,fontSize=17.sp);Text(gs(l, R.string.stages_minutes, day.exercises.size, workoutMinutes(day)),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)};Icon(Icons.Rounded.ChevronRight,null)
                }
            }
        }
        if(goals.isNotEmpty()) item { DailyInsightCard(gs(l, R.string.goal),goals[(today.dayOfYear%goals.size)].let{"${it.title} — ${it.value} ${it.unit}"},Icons.Rounded.Flag) }
        if(records.isNotEmpty()) item { DailyInsightCard(gs(l, R.string.record),records[(today.dayOfYear%records.size)].let{"${it.title} — ${it.value} ${it.unit}"},Icons.Rounded.EmojiEvents) }
        item { DailyInsightCard(gs(l, R.string.motivation),dailyMotivation(profile,today),Icons.Rounded.Bolt) }
    }
}

@Composable private fun AvatarButton(profile:UserProfile,onClick:()->Unit) {
    Surface(Modifier.size(48.dp).semantics { contentDescription = gs(profile.appLanguage, R.string.open_profile) }.clickable(onClick=onClick),shape=CircleShape,color=MaterialTheme.colorScheme.secondaryContainer) {
        val bitmap=rememberUriBitmap(profile.avatarUri)
        if(bitmap!=null) Image(bitmap,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(profile.firstName.take(1).ifBlank{"G"}.uppercase(),fontWeight=FontWeight.Bold)}
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
    onSnoozeMonthlyCheckIn: () -> Unit
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

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=18.dp),contentPadding=PaddingValues(top=18.dp,bottom=132.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(gs(l, R.string.measurements),fontSize=32.sp,fontWeight=FontWeight.Bold);Text(gs(l, R.string.body_goals_and_workout_history),color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick={showMeasurement=true}){Icon(Icons.Rounded.Add,gs(l, R.string.add))}} }
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
        item { SectionTitle(gs(l, R.string.workout_history)) }
        if(logs.isEmpty()) item{CompactEmpty(Icons.Rounded.History,gs(l, R.string.no_workouts_yet),gs(l, R.string.finish_your_first_workout_to_see_history),"",{})}
        else items(logs,key={it.id}){log->WorkoutHistoryRow(log,profile){onDeleteLog(log.id)}}
    }
}

private fun metricOptions(l:String)=listOf("WEIGHT" to gs(l, R.string.weight),"HEIGHT" to gs(l, R.string.height),"CHEST" to gs(l, R.string.chest),"WAIST" to gs(l, R.string.waist),"BICEPS" to gs(l, R.string.biceps),"THIGH" to gs(l, R.string.thigh))
private fun metricValue(e:ProgressEntry,k:String):Double?=when(k){"WEIGHT"->e.weightKg;"HEIGHT"->e.heightCm;"CHEST"->e.chestCm;"WAIST"->e.waistCm;"BICEPS"->e.upperArmCm;"THIGH"->e.thighCm;else->null}
private fun fmt(v:Double)=if(v%1.0==0.0)v.toInt().toString() else "%.1f".format(v)

@Composable private fun MetricChart(entries:List<ProgressEntry>,metric:String) {
    val values=entries.sortedBy{it.createdAt}.mapNotNull{metricValue(it,metric)}.takeLast(20)
    if(values.size<2){Text(gs(LocalAppLanguage.current, R.string.add_one_more_measurement_for_a_chart),color=MaterialTheme.colorScheme.onSurfaceVariant);return}
    val lineColor=MaterialTheme.colorScheme.primary
    val grid=MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        for(i in 1..3){val y=size.height*i/4f;drawLine(grid,Offset(0f,y),Offset(size.width,y),strokeWidth=1f)}
        val min=values.minOrNull()?:0.0; val max=values.maxOrNull()?:1.0; val span=(max-min).takeIf{it>0.001}?:1.0
        var last:Offset?=null
        values.forEachIndexed{i,v->val x=if(values.size==1)0f else size.width*i/(values.lastIndex.toFloat());val y=size.height-((v-min)/span*size.height*.82+size.height*.09).toFloat();val p=Offset(x,y);last?.let{drawLine(lineColor,it,p,strokeWidth=5f,cap=StrokeCap.Round)};drawCircle(lineColor,6f,p);last=p}
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
@Composable private fun WorkoutHistoryRow(log:WorkoutLog,profile:UserProfile,onDelete:()->Unit){
    val l=profile.appLanguage
    val day=workoutsFor(profile).firstOrNull{it.key==log.dayKey} ?: workoutByKey(log.dayKey)
    val title=day?.let{workoutCompactTitle(it,l)} ?: log.title
    ExpressiveCard(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(millisDate(log.finishedAt,l),fontWeight=FontWeight.Bold);Text(title,fontSize=17.sp,fontWeight=FontWeight.SemiBold);Text("${durationLabel(log.durationMillis,l)} • ${gs(l, R.string.sets_count, log.completedSets)}",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)};IconButton(onClick=onDelete){Icon(Icons.Rounded.Delete,gs(l, R.string.delete))}}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit,
    onPickPhoto: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    val l = profile.appLanguage
    var editing by remember { mutableStateOf(false) }
    var showBirthWheel by remember { mutableStateOf(false) }
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

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, gs(l, R.string.back)) } }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(136.dp)) {
                    val bmp = rememberUriBitmap(profile.avatarUri)
                    if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(profile.firstName.take(1).ifBlank { "G" }.uppercase(), fontSize = 50.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    listOf(profile.firstName, profile.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { gs(profile.appLanguage, R.string.app_name) },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                if (profile.profileDescription.isNotBlank()) {
                    Text(profile.profileDescription, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileAction(Icons.Rounded.PhotoCamera, gs(l, R.string.choose_photo), Modifier.weight(1f), onPickPhoto)
                ProfileAction(Icons.Rounded.Edit, gs(l, R.string.edit), Modifier.weight(1f)) { editing = true }
                ProfileAction(Icons.Rounded.Settings, gs(l, R.string.settings), Modifier.weight(1f), onOpenSettings)
            }
        }
    }
}

@Composable private fun ProfileAction(icon:androidx.compose.ui.graphics.vector.ImageVector,text:String,modifier:Modifier,onClick:()->Unit){ExpressiveSurfaceButton(onClick,modifier.height(118.dp),containerColor=MaterialTheme.colorScheme.surfaceVariant,corner=30.dp,contentPadding=PaddingValues(10.dp)){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,null,Modifier.size(31.dp));Spacer(Modifier.height(9.dp));Text(text,fontSize=12.sp,fontWeight=FontWeight.SemiBold)}}}

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
