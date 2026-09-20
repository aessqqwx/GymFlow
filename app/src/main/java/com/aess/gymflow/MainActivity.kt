package com.aess.gymflow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class RootLists(
    val progress: List<ProgressEntry>,
    val logs: List<WorkoutLog>,
    val nutrition: List<NutritionEntry>,
    val goals: List<GoalEntry>,
    val records: List<PersonalRecord>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GymFlowRoot() }
    }
}

@Composable
private fun GymFlowRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { GymFlowStore(context) }
    val scope = rememberCoroutineScope()
    val initialProfile = remember { store.loadProfile() }
    remember(initialProfile) {
        store.loadCachedWorkoutPlan(initialProfile)?.also { primeWorkoutPlanCache(initialProfile, it) }
    }

    var profile by remember { mutableStateOf(initialProfile) }
    var progress by remember { mutableStateOf<List<ProgressEntry>>(emptyList()) }
    var logs by remember { mutableStateOf<List<WorkoutLog>>(emptyList()) }
    var nutrition by remember { mutableStateOf<List<NutritionEntry>>(emptyList()) }
    var goals by remember { mutableStateOf<List<GoalEntry>>(emptyList()) }
    var records by remember { mutableStateOf<List<PersonalRecord>>(emptyList()) }
    var mutationEpoch by remember { mutableIntStateOf(0) }
    var activeState by remember { mutableStateOf(store.loadActiveWorkout()) }
    var activeWorkout by remember {
        mutableStateOf(activeState?.let { state -> workoutsFor(profile).firstOrNull { it.key == state.dayKey } ?: workoutByKey(state.dayKey) })
    }
    var showSettings by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val epoch = mutationEpoch
        val loaded = withContext(Dispatchers.IO) {
            RootLists(
                progress = store.loadProgress(),
                logs = store.loadWorkoutLogs(),
                nutrition = store.loadNutrition(),
                goals = store.loadGoals(),
                records = store.loadRecords()
            )
        }
        if (mutationEpoch == epoch) {
            progress = loaded.progress
            logs = loaded.logs
            nutrition = loaded.nutrition
            goals = loaded.goals
            records = loaded.records
        }
    }

    BackHandler(enabled = activeWorkout != null || showSettings || showProfile) {
        when {
            activeWorkout != null -> activeWorkout = null
            showSettings -> showSettings = false
            showProfile -> showProfile = false
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun updateProfile(updated: UserProfile) {
        val notificationsJustEnabled = !profile.notificationsEnabled && updated.notificationsEnabled
        val enablingMonthly = (!profile.monthlyCheckInEnabled || !profile.measurementNotifications) && updated.monthlyCheckInEnabled && updated.measurementNotifications
        val normalized = if (updated.onboardingCompleted && updated.monthlyCheckInEnabled && updated.nextMonthlyCheckInAt <= 0L) updated.copy(nextMonthlyCheckInAt = nextMonthlyCheckInAt()) else updated
        val planChanged = workoutPlanSignature(profile) != workoutPlanSignature(normalized)
        profile = normalized
        store.saveProfile(normalized)
        if (planChanged) {
            invalidateWorkoutPlanCache()
            scope.launch {
                val plan = withContext(Dispatchers.Default) { workoutsFor(normalized) }
                withContext(Dispatchers.IO) { store.saveCachedWorkoutPlan(normalized, plan) }
            }
        }
        if (normalized.onboardingCompleted && normalized.notificationsEnabled && normalized.measurementNotifications && normalized.monthlyCheckInEnabled) {
            ensureMonthlyCheckInScheduled(context, normalized)
            if (enablingMonthly) requestNotificationPermissionIfNeeded()
        } else cancelMonthlyCheckIn(context)
        if (normalized.onboardingCompleted && normalized.notificationsEnabled) scheduleDailyGymFlowReminders(context) else cancelDailyGymFlowReminders(context)
        if (notificationsJustEnabled) requestNotificationPermissionIfNeeded()
        if (!normalized.notificationsEnabled || !normalized.proteinNotifications) cancelRecoveryReminder(context)
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val stored = withContext(Dispatchers.IO) { persistAvatar(context, uri) }
            if (stored.isNotBlank()) updateProfile(profile.copy(avatarUri = stored))
        }
    }

    fun addProgressEntry(entry: ProgressEntry) {
        progress = (listOf(entry) + progress).sortedByDescending { it.createdAt }
        store.saveProgress(progress)
        if (profile.onboardingCompleted && profile.monthlyCheckInEnabled) {
            val refreshed = profile.copy(nextMonthlyCheckInAt = nextMonthlyCheckInAt())
            profile = refreshed; store.saveProfile(refreshed); scheduleMonthlyCheckIn(context, refreshed.nextMonthlyCheckInAt)
        }
    }
    fun addNutritionEntry(entry: NutritionEntry) { nutrition=(listOf(entry)+nutrition).sortedByDescending{it.createdAt};store.saveNutrition(nutrition) }
    fun upsertGoal(entry:GoalEntry){goals=(listOf(entry)+goals.filterNot{it.id==entry.id}).sortedByDescending{it.createdAt};store.saveGoals(goals)}
    fun upsertRecord(entry:PersonalRecord){records=(listOf(entry)+records.filterNot{it.id==entry.id}).sortedByDescending{it.updatedAt};store.saveRecords(records)}

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val payload = store.exportJson().toByteArray(Charsets.UTF_8)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(payload) } ?: error("open")
                }
            }
            Toast.makeText(
                context,
                if (result.isSuccess) gs(profile.appLanguage, R.string.gymflow_data_exported) else gs(profile.appLanguage, R.string.could_not_export_data),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            mutationEpoch++
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("open")
                        store.importJson(raw)
                    }
                }
                result.onSuccess { backup ->
                    MusicService.suppressPersistenceForStateReplacement()
                    context.stopService(Intent(context, MusicService::class.java))
                    profile = backup.profile
                    progress = backup.progress
                    logs = backup.workouts
                    nutrition = backup.nutrition
                    goals = backup.goals
                    records = backup.records
                    activeState = backup.activeWorkout
                    primeWorkoutPlanCache(profile, backup.trainingPlan)
                    activeWorkout = activeState?.let { state -> backup.trainingPlan.firstOrNull { it.key == state.dayKey } ?: workoutByKey(state.dayKey) }
                    if (profile.notificationsEnabled && profile.measurementNotifications && profile.monthlyCheckInEnabled && profile.nextMonthlyCheckInAt > 0) ensureMonthlyCheckInScheduled(context, profile) else cancelMonthlyCheckIn(context)
                    if (profile.notificationsEnabled) scheduleDailyGymFlowReminders(context) else cancelDailyGymFlowReminders(context)
                    Toast.makeText(context, gs(profile.appLanguage, R.string.gymflow_data_restored), Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    val message = (error as? IllegalArgumentException)?.message
                        ?.takeIf { it.isNotBlank() }
                        ?: gs(profile.appLanguage, R.string.could_not_import_this_file)
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(profile.onboardingCompleted,profile.notificationsEnabled,profile.proteinNotifications,profile.workoutNotifications,profile.motivationNotifications,profile.measurementNotifications,profile.monthlyCheckInEnabled,profile.nextMonthlyCheckInAt) {
        if(profile.onboardingCompleted&&profile.notificationsEnabled&&profile.measurementNotifications&&profile.monthlyCheckInEnabled&&profile.nextMonthlyCheckInAt>0)ensureMonthlyCheckInScheduled(context,profile) else cancelMonthlyCheckIn(context)
        if(profile.onboardingCompleted&&profile.notificationsEnabled)scheduleDailyGymFlowReminders(context) else cancelDailyGymFlowReminders(context)
    }

    GymFlowTheme(profile.themeMode,profile.colorStyle,profile.appLanguage,profile.fontScale) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            val rootScreen=when {
                !profile.onboardingCompleted -> "onboarding"
                activeWorkout!=null -> "workout:${activeWorkout?.key}"
                showSettings -> "settings"
                showProfile -> "profile"
                else -> "main"
            }
            AnimatedContent(targetState=rootScreen,transitionSpec={(slideInHorizontally(tween(320)){it/2}+fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(260)){-it/3}+fadeOut(tween(210)))},label="root_transition") { screen ->
                when {
                    screen=="onboarding" -> OnboardingV2(initial=profile,onImport={importLauncher.launch(arrayOf("application/json","text/plain","*/*"))}) { updated,weight,height,plan ->
                        mutationEpoch++
                        val now = System.currentTimeMillis()
                        val entry = ProgressEntry(now, now, weight, height, gs(updated.appLanguage, R.string.starting_data))
                        val persistedProgress = withContext(Dispatchers.IO) {
                            store.saveProfile(updated)
                            store.saveCachedWorkoutPlan(updated, plan)
                            val merged = (listOf(entry) + store.loadProgress().filterNot { it.id == entry.id }).sortedByDescending { it.createdAt }
                            store.saveProgress(merged)
                            merged
                        }
                        profile = updated
                        progress = persistedProgress
                        primeWorkoutPlanCache(updated, plan)
                        requestNotificationPermissionIfNeeded()
                        scheduleDailyGymFlowReminders(context)
                    }
                    screen.startsWith("workout:") -> {
                        val day=activeWorkout?:workoutsFor(profile).firstOrNull{it.key==screen.substringAfter("workout:")}?:workoutByKey(screen.substringAfter("workout:"))
                        if(day!=null) WorkoutScreen(day=day,storedState=activeState?.takeIf{it.dayKey==day.key},onStateChanged={activeState=it;store.saveActiveWorkout(it)},onBack={activeWorkout=null},onFinished={log,setStage->
                            mutationEpoch++
                            setStage(0)
                            val persistedLogs = withContext(Dispatchers.IO) { store.loadWorkoutLogs() }
                            val updatedLogs = withContext(Dispatchers.Default) { mergeCompletedWorkout(persistedLogs, log) }
                            withContext(Dispatchers.IO) { store.saveWorkoutLogs(updatedLogs) }
                            logs = updatedLogs
                            setStage(1)
                            val persistedRecords = withContext(Dispatchers.IO) { store.loadRecords() }
                            val updatedRecords = withContext(Dispatchers.Default) { updatedRecordsAfterWorkout(persistedRecords, day, log, profile.appLanguage) }
                            setStage(2)
                            withContext(Dispatchers.IO) { store.saveRecords(updatedRecords) }
                            records = updatedRecords
                            setStage(3)
                            withContext(Dispatchers.Default) { computeMuscleLoadCounts(updatedLogs, profile, profile.appLanguage) }
                            setStage(4)
                            val preparedPlan = withContext(Dispatchers.Default) { workoutsFor(profile) }
                            withContext(Dispatchers.IO) { store.saveCachedWorkoutPlan(profile, preparedPlan) }
                            if(profile.notificationsEnabled&&profile.reminderEnabled&&profile.proteinNotifications){requestNotificationPermissionIfNeeded();scheduleRecoveryReminder(context)}
                        },onHome={activeState=null;store.saveActiveWorkout(null);activeWorkout=null;tab=0})
                    }
                    screen=="settings" -> SettingsScreen(profile=profile,onProfileChange=::updateProfile,onBack={showSettings=false},onExport={exportLauncher.launch("GymFlow-backup.json")},onImport={importLauncher.launch(arrayOf("application/json","text/plain","*/*"))},onReset={
                        mutationEpoch++
                        MusicService.suppressPersistenceForReset();context.stopService(Intent(context,MusicService::class.java))
                        scope.launch {
                            withContext(Dispatchers.IO) { store.resetAll() }
                            invalidateWorkoutPlanCache();profile=UserProfile();progress=emptyList();logs=emptyList();nutrition=emptyList();goals=emptyList();records=emptyList();activeState=null;activeWorkout=null;showSettings=false;showProfile=false
                            cancelDailyGymFlowReminders(context);cancelMonthlyCheckIn(context);cancelRecoveryReminder(context)
                        }
                    })
                    screen=="profile" -> ProfileScreen(profile=profile,onProfileChange=::updateProfile,onPickPhoto={photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},onOpenSettings={showProfile=false;showSettings=true},onBack={showProfile=false})
                    else -> MainTabs(
                        profile=profile,tab=tab,onTab={tab=it},activeState=activeState,logs=logs,nutrition=nutrition,progress=progress,goals=goals,records=records,
                        onOpenSettings={showSettings=true},onOpenProfile={showProfile=true},onOpenWorkout={activeWorkout=it},onAddNutrition=::addNutritionEntry,onOpenMeasurements={tab=1},
                        onAddProgress=::addProgressEntry,onDeleteProgress={id->progress=progress.filterNot{it.id==id};store.saveProgress(progress)},onDeleteLog={id->logs=logs.filterNot{it.id==id};store.saveWorkoutLogs(logs)},
                        onUpsertGoal=::upsertGoal,onDeleteGoal={id->goals=goals.filterNot{it.id==id};store.saveGoals(goals)},onUpsertRecord=::upsertRecord,onDeleteRecord={id->records=records.filterNot{it.id==id};store.saveRecords(records)},
                        onSnooze={val p=profile.copy(nextMonthlyCheckInAt=System.currentTimeMillis()+MONTHLY_SNOOZE_MS);profile=p;store.saveProfile(p);scheduleMonthlyCheckIn(context,p.nextMonthlyCheckInAt)},onDeleteNutrition={id->nutrition=nutrition.filterNot{it.id==id};store.saveNutrition(nutrition)},onProfileChange=::updateProfile
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTabs(
    profile:UserProfile,tab:Int,onTab:(Int)->Unit,activeState:ActiveWorkoutState?,logs:List<WorkoutLog>,nutrition:List<NutritionEntry>,progress:List<ProgressEntry>,goals:List<GoalEntry>,records:List<PersonalRecord>,
    onOpenSettings:()->Unit,onOpenProfile:()->Unit,onOpenWorkout:(WorkoutDay)->Unit,onAddNutrition:(NutritionEntry)->Unit,onOpenMeasurements:()->Unit,
    onAddProgress:(ProgressEntry)->Unit,onDeleteProgress:(Long)->Unit,onDeleteLog:(Long)->Unit,onUpsertGoal:(GoalEntry)->Unit,onDeleteGoal:(Long)->Unit,onUpsertRecord:(PersonalRecord)->Unit,onDeleteRecord:(Long)->Unit,onSnooze:()->Unit,onDeleteNutrition:(Long)->Unit,onProfileChange:(UserProfile)->Unit
) {
    var showNowPlaying by remember { mutableStateOf(false) }
    if(showNowPlaying) NowPlayingSheet { showNowPlaying=false }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent,MaterialTheme.colorScheme.background.copy(alpha=.82f),MaterialTheme.colorScheme.background))).navigationBarsPadding().padding(horizontal=18.dp,vertical=8.dp)) {
            MiniPlayerBar(onOpen={showNowPlaying=true})
            Spacer(Modifier.height(6.dp))
            Surface(modifier=Modifier.align(Alignment.CenterHorizontally),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.96f),shape=RoundedCornerShape(38.dp),tonalElevation=3.dp) {
                Row(Modifier.padding(horizontal=7.dp,vertical=7.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                    val items=listOf(Triple(gs(profile.appLanguage, R.string.today_8dd7bf1),Icons.Rounded.Home,0),Triple(gs(profile.appLanguage, R.string.measurements),Icons.Rounded.Straighten,1),Triple(gs(profile.appLanguage, R.string.nutrition_294139b),Icons.Rounded.Restaurant,2),Triple(gs(profile.appLanguage, R.string.music_df4392a),Icons.Rounded.LibraryMusic,3))
                    items.forEach{(label,icon,index)->val width by animateDpAsState(if(tab==index)72.dp else 50.dp,tween(240),label="nav_width");Surface(color=if(tab==index)MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,shape=RoundedCornerShape(if(tab==index)24.dp else 18.dp)){IconButton(onClick={onTab(index)},modifier=Modifier.size(width=width,height=50.dp)){Icon(icon,label,Modifier.size(24.dp).then(if(index==1)Modifier.rotate(-45f)else Modifier),tint=if(tab==index)MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)}}}
                }
            }
        }
    }) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            AnimatedContent(targetState=tab,transitionSpec={val forward=targetState>initialState;(slideInHorizontally(tween(300)){if(forward)it/2 else -it/2}+fadeIn(tween(250))) togetherWith (slideOutHorizontally(tween(250)){if(forward)-it/3 else it/3}+fadeOut(tween(200)))},label="tab_transition") { current ->
                when(current) {
                    0 -> TodayScreen(activeState,logs,profile,nutrition,progress,goals,records,onOpenSettings,onOpenProfile,onOpenWorkout,onAddNutrition,onOpenMeasurements)
                    1 -> ProgressScreen(progress,logs,profile,goals,records,onAddProgress,onDeleteProgress,onDeleteLog,onUpsertGoal,onDeleteGoal,onUpsertRecord,onDeleteRecord,onSnooze)
                    2 -> NutritionScreen(profile,progress,nutrition,onAddNutrition,onDeleteNutrition,onProfileChange)
                    else -> PlayerScreen()
                }
            }
        }
    }
}
