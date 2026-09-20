package com.aess.gymflow

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

const val ACTION_RECOVERY_REMINDER = "com.aess.gymflow.RECOVERY_REMINDER"
const val ACTION_MONTHLY_CHECKIN = "com.aess.gymflow.MONTHLY_CHECKIN"
const val ACTION_PROTEIN_MORNING = "com.aess.gymflow.PROTEIN_MORNING"
const val ACTION_PROTEIN_DAY = "com.aess.gymflow.PROTEIN_DAY"
const val ACTION_PROTEIN_EVENING = "com.aess.gymflow.PROTEIN_EVENING"
const val ACTION_MOTIVATION = "com.aess.gymflow.MOTIVATION"
const val ACTION_WORKOUT = "com.aess.gymflow.WORKOUT"
const val MONTHLY_SNOOZE_MS = 3L * 24L * 60L * 60L * 1000L

fun nextMonthlyCheckInAt(fromMillis: Long = System.currentTimeMillis()): Long = Instant.ofEpochMilli(fromMillis).atZone(ZoneId.systemDefault()).plusMonths(2).toInstant().toEpochMilli()
fun scheduleRecoveryReminder(context: Context) = scheduleOneShot(context,ACTION_RECOVERY_REMINDER,1001,System.currentTimeMillis()+45L*60L*1000L,ReminderReceiver::class.java)
fun scheduleMonthlyCheckIn(context: Context, triggerAt: Long) = scheduleOneShot(context,ACTION_MONTHLY_CHECKIN,2001,triggerAt,ReminderReceiver::class.java)
fun cancelRecoveryReminder(context: Context)=cancelAlarm(context,ACTION_RECOVERY_REMINDER,1001,ReminderReceiver::class.java)
fun cancelMonthlyCheckIn(context: Context)=cancelAlarm(context,ACTION_MONTHLY_CHECKIN,2001,ReminderReceiver::class.java)

fun ensureMonthlyCheckInScheduled(context: Context, profile: UserProfile) {
    if(!profile.onboardingCompleted||!profile.notificationsEnabled||!profile.measurementNotifications||!profile.monthlyCheckInEnabled||profile.nextMonthlyCheckInAt<=0L)return
    val now=System.currentTimeMillis();scheduleMonthlyCheckIn(context,if(profile.nextMonthlyCheckInAt>now)profile.nextMonthlyCheckInAt else nextMonthlyCheckInAt(now))
}

fun scheduleDailyGymFlowReminders(context: Context) {
    val p=GymFlowStore(context).loadProfile()
    cancelDailyGymFlowReminders(context)
    if(!p.onboardingCompleted||!p.notificationsEnabled)return
    if(p.proteinNotifications){
        scheduleDaily(context,8,15,ACTION_PROTEIN_MORNING,3101)
        scheduleDaily(context,13,30,ACTION_PROTEIN_DAY,3102)
        scheduleDaily(context,19,30,ACTION_PROTEIN_EVENING,3103)
    }
    if(p.workoutNotifications)scheduleDaily(context,16,0,ACTION_WORKOUT,3200)
    if(p.motivationNotifications)scheduleDaily(context,11,30,ACTION_MOTIVATION,3201)
}
fun cancelDailyGymFlowReminders(context: Context) {
    listOf(ACTION_PROTEIN_MORNING to 3101,ACTION_PROTEIN_DAY to 3102,ACTION_PROTEIN_EVENING to 3103,ACTION_WORKOUT to 3200,ACTION_MOTIVATION to 3201).forEach{(a,c)->cancelAlarm(context,a,c,DailyReminderReceiver::class.java)}
}

private fun scheduleDaily(context:Context,hour:Int,minute:Int,action:String,code:Int){
    val now=ZonedDateTime.now();var next=now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);if(!next.isAfter(now))next=next.plusDays(1)
    val manager=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pending=PendingIntent.getBroadcast(context,code,Intent(context,DailyReminderReceiver::class.java).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    manager.setInexactRepeating(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),AlarmManager.INTERVAL_DAY,pending)
}
private fun scheduleOneShot(context:Context,action:String,code:Int,trigger:Long,receiver:Class<out android.content.BroadcastReceiver>){val manager=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager;val pending=PendingIntent.getBroadcast(context,code,Intent(context,receiver).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M)manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pending)else manager.set(AlarmManager.RTC_WAKEUP,trigger,pending)}
private fun cancelAlarm(context:Context,action:String,code:Int,receiver:Class<out android.content.BroadcastReceiver>){val manager=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager;manager.cancel(PendingIntent.getBroadcast(context,code,Intent(context,receiver).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))}
