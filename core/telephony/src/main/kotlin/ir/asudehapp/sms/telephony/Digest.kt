package ir.asudehapp.sms.telephony

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ir.asudehapp.sms.data.DigestFrequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * زمان `Digest` (D40): روزانه ساعت ۲۱، یا هفتگی جمعه ساعت ۲۱. تابع خالص است
 * تا بدون اندروید آزمون شود.
 */
object DigestSchedule {

    const val HOUR: Int = 21

    /** اولین زمانِ پس از [nowMillis]؛ برای «خاموش» `null`. */
    fun nextAt(nowMillis: Long, frequency: DigestFrequency, zone: ZoneId): Long? {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val todayAt = now.withHour(HOUR).withMinute(0).withSecond(0).withNano(0)
        val next = when (frequency) {
            DigestFrequency.OFF -> return null
            DigestFrequency.DAILY -> if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
            DigestFrequency.WEEKLY -> {
                val friday = todayAt.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
                if (friday.isAfter(now)) friday else friday.plusWeeks(1)
            }
        }
        return next.toInstant().toEpochMilli()
    }
}

/**
 * زمان‌بندی `Digest` با `AlarmManager`، بدون WorkManager و بدون مجوز
 * «هشدار دقیق»: چند دقیقه دیرتر رسیدن خلاصه اشکالی ندارد.
 */
object DigestScheduler {

    fun schedule(context: Context) {
        val host = context.telephonyHost
        val settings = host.settings
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(context)
        val now = System.currentTimeMillis()
        // شمارش از لحظهٔ روشن شدن خلاصه است، نه از ابتدای تاریخ.
        if (settings.lastDigestAt == 0L) settings.lastDigestAt = now
        val next = DigestSchedule.nextAt(now, settings.digest, ZoneId.systemDefault())
        if (next == null) {
            alarms.cancel(intent)
            return
        }
        alarms.setWindow(AlarmManager.RTC_WAKEUP, next, WINDOW_MILLIS, intent)
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, DigestReceiver::class.java).setAction(DigestReceiver.ACTION_DIGEST),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val WINDOW_MILLIS = 15 * 60 * 1_000L
}

/**
 * زمان‌بندی دوباره پس از روشن شدن گوشی یا عوض شدن ساعت. export شده است چون
 * این پیام‌ها از سیستم می‌آیند، ولی همه «protected broadcast»اند و اپ دیگری
 * نمی‌تواند آن‌ها را بفرستد.
 */
class DigestRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> DigestScheduler.schedule(context)
        }
    }
}

/** رسیدن زمان `Digest`. export نشده است؛ فقط هشدار خود اپ به آن می‌رسد. */
class DigestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DIGEST -> {
                val host = context.telephonyHost
                val pending = goAsync()
                scope.launch {
                    try {
                        val settings = host.settings
                        val now = System.currentTimeMillis()
                        val count = host.repository.hiddenSince(settings.lastDigestAt)
                        host.notifier.notifyDigest(count, settings.digest)
                        settings.lastDigestAt = now
                    } catch (failure: Throwable) {
                        Log.e(TAG, "خلاصه ساخته نشد", failure)
                    } finally {
                        DigestScheduler.schedule(context)
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_DIGEST = "ir.asudehapp.sms.DIGEST"
        private const val TAG = "AsudehDigest"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
