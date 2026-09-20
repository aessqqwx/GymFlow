package com.aess.gymflow

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    var expandedSection by remember { mutableStateOf<String?>(null) }
    var previewFontScale by remember(profile.fontScale) { mutableFloatStateOf(profile.fontScale) }
    var showResetDialog by remember { mutableStateOf(false) }
    var legalDialog by remember { mutableStateOf<String?>(null) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(gs(language, R.string.delete_all_gymflow_data)) },
            text = { Text(gs(language, R.string.profile_workouts_nutrition_measurements_and)) },
            confirmButton = { TextButton(onClick = { showResetDialog = false; onReset() }) { Text(gs(language, R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text(gs(language, R.string.cancel)) } }
        )
    }

    legalDialog?.let { type ->
        val terms = type == "terms"
        AlertDialog(
            onDismissRequest = { legalDialog = null },
            title = { Text(if (terms) gs(language, R.string.terms_of_use) else gs(language, R.string.privacy_policy)) },
            text = {
                Text(
                    if (terms) gs(language, R.string.terms_full_text) else gs(language, R.string.privacy_full_text),
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    lineHeight = 20.sp
                )
            },
            confirmButton = { TextButton(onClick = { legalDialog = null }) { Text(gs(language, R.string.close)) } }
        )
    }

    fun openMail() {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }.getOrDefault("")
        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:Fl2011you@gmail.com")).apply {
            putExtra(Intent.EXTRA_SUBJECT, gs(language, R.string.gymflow_feedback))
            putExtra(
                Intent.EXTRA_TEXT,
                gs(language, R.string.feedback_body, version)
            )
        }
        runCatching { context.startActivity(mail) }.onFailure {
            Toast.makeText(context, gs(language, R.string.no_email_app_found), Toast.LENGTH_SHORT).show()
        }
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching { context.startActivity(intent) }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, gs(language, R.string.back)) }
                Spacer(Modifier.width(4.dp))
                Text(gs(language, R.string.settings), fontSize = 31.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            SettingsNavTile(
                title = gs(language, R.string.language),
                value = if (profile.appLanguage == "EN") gs(language, R.string.language_english) else gs(language, R.string.language_russian),
                icon = Icons.Rounded.Language,
                expanded = expandedSection == "LANG",
                onClick = { expandedSection = if (expandedSection == "LANG") null else "LANG" }
            )
        }
        item {
            AnimatedVisibility(
                visible = expandedSection == "LANG",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SettingsSection(gs(language, R.string.interface_language), Icons.Rounded.Language) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = profile.appLanguage == "RU",
                            onClick = { onProfileChange(profile.copy(appLanguage = "RU")) },
                            label = { Text(gs(language, R.string.language_russian)) }
                        )
                        FilterChip(
                            selected = profile.appLanguage == "EN",
                            onClick = { onProfileChange(profile.copy(appLanguage = "EN")) },
                            label = { Text(gs(language, R.string.language_english)) }
                        )
                    }
                }
            }
        }

        item {
            SettingsNavTile(
                title = gs(language, R.string.notifications),
                value = if (profile.notificationsEnabled) gs(language, R.string.on) else gs(language, R.string.off),
                icon = Icons.Rounded.Notifications,
                expanded = expandedSection == "NOTIFY",
                onClick = { expandedSection = if (expandedSection == "NOTIFY") null else "NOTIFY" }
            )
        }
        item {
            AnimatedVisibility(
                visible = expandedSection == "NOTIFY",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SettingsSection(gs(language, R.string.notifications), Icons.Rounded.Notifications) {
                    SettingSwitchRow(
                        title = gs(language, R.string.notifications),
                        subtitle = gs(language, R.string.master_switch_for_all_gymflow_notifications),
                        checked = profile.notificationsEnabled,
                        onCheckedChange = { onProfileChange(profile.copy(notificationsEnabled = it, reminderEnabled = it)) }
                    )
                    SettingDivider()
                    SettingSwitchRow(
                        title = gs(language, R.string.workouts),
                        subtitle = gs(language, R.string.reminders_on_selected_workout_days),
                        checked = profile.workoutNotifications,
                        onCheckedChange = { onProfileChange(profile.copy(workoutNotifications = it)) }
                    )
                    SettingDivider()
                    SettingSwitchRow(
                        title = gs(language, R.string.nutrition_protein),
                        subtitle = gs(language, R.string.morning_afternoon_and_evening_based_on_your),
                        checked = profile.proteinNotifications,
                        onCheckedChange = { onProfileChange(profile.copy(proteinNotifications = it)) }
                    )
                    SettingDivider()
                    SettingSwitchRow(
                        title = gs(language, R.string.motivation_14df0e8),
                        subtitle = gs(language, R.string.short_messages_without_spam),
                        checked = profile.motivationNotifications,
                        onCheckedChange = { onProfileChange(profile.copy(motivationNotifications = it)) }
                    )
                    SettingDivider()
                    SettingSwitchRow(
                        title = gs(language, R.string.measurements),
                        subtitle = gs(language, R.string.remind_you_to_update_measurements_when_they),
                        checked = profile.measurementNotifications,
                        onCheckedChange = {
                            onProfileChange(profile.copy(
                                measurementNotifications = it,
                                monthlyCheckInEnabled = it,
                                nextMonthlyCheckInAt = if (it) nextMonthlyCheckInAt() else 0L
                            ))
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = ::openNotificationSettings, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.NotificationsActive, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(gs(language, R.string.system_notification_settings))
                    }
                }
            }
        }

        item {
            SettingsNavTile(
                title = gs(language, R.string.appearance),
                value = when (profile.colorStyle) {
                    "MONO" -> gs(language, R.string.monochrome)
                    "SYSTEM" -> gs(language, R.string.system)
                    "BROWN" -> gs(language, R.string.brown)
                    "GREEN" -> gs(language, R.string.green)
                    "PURPLE" -> gs(language, R.string.purple)
                    else -> gs(language, R.string.blue)
                },
                icon = Icons.Rounded.Palette,
                expanded = expandedSection == "LOOK",
                onClick = { expandedSection = if (expandedSection == "LOOK") null else "LOOK" }
            )
        }
        item {
            AnimatedVisibility(
                visible = expandedSection == "LOOK",
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SettingsSection(gs(language, R.string.appearance), Icons.Rounded.Palette) {
                    Text(gs(language, R.string.theme), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(
                            "SYSTEM" to gs(language, R.string.use_system_setting),
                            "LIGHT" to gs(language, R.string.light),
                            "DARK" to gs(language, R.string.dark)
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = profile.themeMode == value,
                                onClick = { onProfileChange(profile.copy(themeMode = value)) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(gs(language, R.string.text_size) + " · ${(previewFontScale * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(gs(language, R.string.text_scale_preview), fontSize = 12.sp)
                        androidx.compose.material3.Slider(
                            value = previewFontScale,
                            onValueChange = { previewFontScale = it },
                            onValueChangeFinished = { onProfileChange(profile.copy(fontScale = previewFontScale)) },
                            valueRange = 0.85f..1.30f,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text(gs(language, R.string.text_scale_preview), fontSize = 20.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(gs(language, R.string.color_scheme), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        PaletteRow(profile.colorStyle == "MONO", gs(language, R.string.monochrome), gs(language, R.string.default_label), listOf(Color(0xFFF0F0F0), Color(0xFF8B8B8B), Color(0xFF2B2B2B))) {
                            onProfileChange(profile.copy(colorStyle = "MONO"))
                        }
                        PaletteRow(profile.colorStyle == "SYSTEM", gs(language, R.string.system), gs(language, R.string.device_accent_with_gymflow_neutral_surfaces), listOf(Color(0xFF9CB9DD), Color(0xFF8EA69A), Color(0xFFB5A28E))) {
                            onProfileChange(profile.copy(colorStyle = "SYSTEM"))
                        }
                        PaletteRow(profile.colorStyle == "BLUE", gs(language, R.string.blue), null, listOf(Color(0xFFADC6FF), Color(0xFF5879B8), Color(0xFF293B59))) { onProfileChange(profile.copy(colorStyle = "BLUE")) }
                        PaletteRow(profile.colorStyle == "BROWN", gs(language, R.string.brown), null, listOf(Color(0xFFE7C09C), Color(0xFF8A694F), Color(0xFF4C382A))) { onProfileChange(profile.copy(colorStyle = "BROWN")) }
                        PaletteRow(profile.colorStyle == "GREEN", gs(language, R.string.green), null, listOf(Color(0xFFA9D7B5), Color(0xFF62866B), Color(0xFF314C38))) { onProfileChange(profile.copy(colorStyle = "GREEN")) }
                        PaletteRow(profile.colorStyle == "PURPLE", gs(language, R.string.purple), null, listOf(Color(0xFFD6BCFF), Color(0xFF8E6AB2), Color(0xFF4F3A68))) { onProfileChange(profile.copy(colorStyle = "PURPLE")) }
                    }
                }
            }
        }

        item {
            SettingsSection(gs(language, R.string.data_section), Icons.Rounded.Backup) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                        Text(gs(language, R.string.export))
                    }
                    FilledTonalButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                        Text(gs(language, R.string.import_action))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    gs(language, R.string.one_json_file_profile_workouts_nutrition_mea),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = { showResetDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(gs(language, R.string.reset_data))
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { openMail() },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Email, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(gs(language, R.string.feedback), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(gs(language, R.string.support_email), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(20.dp))
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/GymFlowOfficial"))) }
                },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF229ED9).copy(alpha = .14f)) {
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Send,
                                contentDescription = gs(language, R.string.telegram),
                                modifier = Modifier.size(23.dp),
                                tint = Color(0xFF229ED9)
                            )
                        }
                    }
                    Spacer(Modifier.width(13.dp))
                    Text(gs(language, R.string.telegram_gymflow), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Text(
                gs(language, R.string.app_version_display),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        // Legal information intentionally stays at the very bottom of Settings.
        item {
            Text(
                gs(language, R.string.terms_of_use),
                Modifier.fillMaxWidth().clickable { legalDialog = "terms" }.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        item {
            Text(
                gs(language, R.string.privacy_policy),
                Modifier.fillMaxWidth().clickable { legalDialog = "privacy" }.padding(top = 2.dp, bottom = 14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}


@Composable
private fun SettingsNavTile(
    title: String,
    value: String,
    icon: ImageVector,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(13.dp))
            content()
        }
    }
}

@Composable
private fun PaletteRow(
    selected: Boolean,
    title: String,
    subtitle: String?,
    colors: List<Color>,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                colors.forEach { color ->
                    Box(Modifier.size(width = 10.dp, height = 28.dp).background(color, RoundedCornerShape(5.dp)))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
            }
            if (selected) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
                    Box(Modifier.size(10.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingDivider() {
    Spacer(Modifier.height(12.dp))
    Surface(Modifier.fillMaxWidth().height(1.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)) {}
    Spacer(Modifier.height(12.dp))
}
