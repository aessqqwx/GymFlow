package com.aess.gymflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate

class DailyReminderReceiver: BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val profile=GymFlowStore(context).loadProfile();if(!profile.onboardingCompleted||!profile.notificationsEnabled)return
        val l=profile.appLanguage;val name=profile.firstName.takeIf{it.isNotBlank()}?.plus(", ").orEmpty()
        val proteinGoal=profile.proteinGoal.coerceAtLeast(0)
        val proteinMorning=(proteinGoal*.27).toInt()
        val proteinDay=(proteinGoal*.38).toInt()
        val proteinEvening=(proteinGoal-proteinMorning-proteinDay).coerceAtLeast(0)
        val protein=when(intent.action){ACTION_PROTEIN_MORNING->proteinMorning;ACTION_PROTEIN_DAY->proteinDay;else->proteinEvening}
        val proteinAction=intent.action in setOf(ACTION_PROTEIN_MORNING,ACTION_PROTEIN_DAY,ACTION_PROTEIN_EVENING)
        if(proteinAction&&!profile.proteinNotifications)return
        if (proteinAction && proteinGoal <= 0) return
        val isTrainingDay=LocalDate.now().dayOfWeek.name in profile.trainingDays
        if(intent.action==ACTION_WORKOUT&&(!profile.workoutNotifications||!isTrainingDay))return
        if(intent.action==ACTION_MOTIVATION&&(!profile.motivationNotifications||isTrainingDay&&profile.workoutNotifications))return
        val motivation = gsa(l, R.array.motivation_messages)
        val template = motivation[LocalDate.now().dayOfYear % motivation.size]
        val daily = String.format(GymFlowApplication.instance.resourcesFor(l).configuration.locales[0], template, name)
        val meal=when(intent.action){ACTION_PROTEIN_MORNING->gs(l, R.string.morning);ACTION_PROTEIN_DAY->gs(l, R.string.day);else->gs(l, R.string.evening)}
        val text=when{
            proteinAction->gs(l, R.string.protein_reminder_text, name, meal, protein)
            intent.action==ACTION_WORKOUT->gs(l, R.string.workout_today_named, name)
            else->daily
        }
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;val channel="gymflow_daily_v2"
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel(channel,gs(l, R.string.gymflow_reminders),NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(context,91,Intent(context,MainActivity::class.java).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP},PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b=if(Build.VERSION.SDK_INT>=26)Notification.Builder(context,channel) else @Suppress("DEPRECATION") Notification.Builder(context)
        val id=when{proteinAction->920+(intent.action.hashCode() and 7);intent.action==ACTION_WORKOUT->929;else->930}
        val title=when{proteinAction->gs(l, R.string.today_s_protein);intent.action==ACTION_WORKOUT->gs(l, R.string.workout_today);else->gs(l, R.string.time_to_move)}
        manager.notify(id,b.setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text).setContentIntent(open).setAutoCancel(true).build())
    }
}
