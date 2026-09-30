package com.aess.gymflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val profile = GymFlowStore(context).loadProfile()
        if (!profile.onboardingCompleted || !profile.notificationsEnabled) return
        ensureMonthlyCheckInScheduled(context, profile)
        scheduleDailyGymFlowReminders(context)
    }
}
