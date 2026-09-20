package com.aess.gymflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MONTHLY_CHECKIN -> showMonthlyCheckIn(context)
            else -> showRecovery(context)
        }
    }

    private fun showRecovery(context: Context) {
        val profile = GymFlowStore(context).loadProfile()
        if(!profile.notificationsEnabled||!profile.proteinNotifications)return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "recovery_v2"
        val lang = profile.appLanguage
        createChannel(
            manager,
            channelId,
            gs(lang, R.string.workout_recovery),
            gs(lang, R.string.nutrition_and_recovery_reminders_after_worko)
        )
        notificationBuilder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(gs(lang, R.string.recovery))
            .setContentText(gs(lang, R.string.recovery_text, profile.proteinGoal))
            .setContentIntent(openAppIntent(context, 1101))
            .setAutoCancel(true)
            .also { manager.notify(1001, it.build()) }
    }

    private fun showMonthlyCheckIn(context: Context) {
        val store = GymFlowStore(context)
        val profile = store.loadProfile()
        if (!profile.onboardingCompleted || !profile.notificationsEnabled || !profile.measurementNotifications || !profile.monthlyCheckInEnabled) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "monthly_progress_v1"
        val lang = profile.appLanguage
        createChannel(
            manager,
            channelId,
            gs(lang, R.string.monthly_progress),
            gs(lang, R.string.reminder_to_update_weight_height_and_body_me)
        )
        notificationBuilder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(gs(lang, R.string.time_to_update_progress))
            .setContentText(gs(lang, R.string.add_your_current_weight_and_height_you_can_u))
            .setContentIntent(openAppIntent(context, 2101))
            .setAutoCancel(true)
            .also { manager.notify(2001, it.build()) }

        // Keep a monthly nudge alive even if the user does not open the app.
        // The due date itself is not moved: the in-app banner stays visible until
        // the user updates data or explicitly snoozes it.
        scheduleMonthlyCheckIn(context, nextMonthlyCheckInAt())
    }

    private fun createChannel(
        manager: NotificationManager,
        id: String,
        name: String,
        description: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    this.description = description
                }
            )
        }
    }

    private fun notificationBuilder(context: Context, channelId: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, channelId)
        else @Suppress("DEPRECATION") Notification.Builder(context)

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
