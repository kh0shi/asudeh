package ir.asudehapp.sms.telephony

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import ir.asudehapp.sms.data.ScheduledMessageEntity
import ir.asudehapp.sms.persian.SendSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ارسال زمان‌بندی‌شده (ADR-0011).
 *
 * فقط **یک** هشدار در هر لحظه وجود دارد: هشدار نزدیک‌ترین پیامک. هر بار که
 * هشدار می‌رسد، همهٔ پیامک‌های رسیده فرستاده می‌شوند و هشدار بعدی تنظیم می‌شود.
 * این‌طور صف هر چقدر هم بلند باشد، اپ بیش از یک هشدار از سیستم نمی‌گیرد.
 *
 * اپ مجوز «هشدار دقیق» نمی‌گیرد (فهرست مجوزها ثابت است، D62). روی اندروید ۱۱
 * و پایین‌تر هشدار دقیق است؛ بالاتر، اگر سیستم اجازه ندهد، ممکن است چند دقیقه
 * دیرتر برسد و رابط همین را به کاربر می‌گوید.
 */
object ScheduledSendScheduler {

    /** هشدار را با نزدیک‌ترین زمان صف هماهنگ می‌کند. */
    fun reschedule(context: Context) {
        scope.launch { rescheduleNow(context) }
    }

    suspend fun rescheduleNow(context: Context) {
        val host = context.telephonyHost
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(context)
        val now = System.currentTimeMillis()
        // پیامکی که زمانش رسیده، هشدار نمی‌خواهد: همین حالا فرستاده می‌شود.
        if (host.repository.dueScheduled(now).isNotEmpty()) {
            alarms.cancel(intent)
            fire(context)
            return
        }
        val next = host.repository.nextScheduledAfter(now)
        if (next == null) {
            alarms.cancel(intent)
            return
        }
        setAlarm(alarms, next, intent)
    }

    /**
     * هشدار دقیق فقط وقتی تنظیم می‌شود که سیستم بدون مجوز تازه اجازه بدهد؛
     * وگرنه هشدار معمولی، که ممکن است چند دقیقه دیرتر برسد (ADR-0011).
     *
     * `ScheduleExactAlarm` اینجا خاموش شده چون `canScheduleExactAlarms` همان
     * چیزی است که lint می‌خواهد ببیند، ولی آن را فقط با اعلام مجوز می‌پذیرد و
     * ما عمداً مجوز نمی‌گیریم.
     */
    @SuppressLint("MissingPermission", "ScheduleExactAlarm")
    private fun setAlarm(alarms: AlarmManager, at: Long, intent: PendingIntent) {
        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarms.canScheduleExactAlarms()
        } else {
            true
        }
        runCatching {
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        }.onFailure {
            // سیستم هشدار دقیق را نپذیرفت؛ هشدار معمولی همیشه پذیرفته می‌شود.
            runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent) }
        }
    }

    /** فرستادن همهٔ پیامک‌هایی که زمانشان رسیده است. */
    private fun fire(context: Context) {
        context.sendBroadcast(
            Intent(context, ScheduledSendReceiver::class.java)
                .setPackage(context.packageName)
                .setAction(ScheduledSendReceiver.ACTION_SEND_DUE),
        )
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ScheduledSendReceiver::class.java)
            .setAction(ScheduledSendReceiver.ACTION_SEND_DUE)
            .setData(Uri.parse("asudeh-schedule://due")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

/**
 * رسیدن زمان یک یا چند پیامک زمان‌بندی‌شده. export نشده است؛ فقط هشدار خود اپ
 * و کد خود اپ به آن می‌رسند.
 */
class ScheduledSendReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND_DUE) return
        val pending = goAsync()
        scope.launch {
            try {
                ScheduledSend.sendDue(context)
            } catch (failure: Throwable) {
                Log.e(TAG, "پیامک زمان‌بندی‌شده فرستاده نشد", failure)
            } finally {
                runCatching { ScheduledSendScheduler.rescheduleNow(context) }
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SEND_DUE: String = "ir.asudehapp.sms.SEND_SCHEDULED"
        private const val TAG = "AsudehSchedule"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** خود کار ارسال، جدا از گیرنده، تا اپ هم بتواند هنگام باز شدن صدایش بزند. */
object ScheduledSend {

    /**
     * پیامک‌هایی که زمانشان رسیده است فرستاده می‌شوند. پیامکی که خیلی دیر شده
     * **فرستاده نمی‌شود**: `MISSED` می‌شود، اعلان می‌گیرد و در گفتگو با دکمهٔ
     * «الان بفرست» می‌ماند. فرستادن بی‌خبرِ پیامک دیروز، خودش یک غافلگیری است.
     */
    suspend fun sendDue(context: Context) {
        val host = context.telephonyHost
        val now = System.currentTimeMillis()
        for (message in host.repository.dueScheduled(now)) {
            if (SendSchedule.isTooLate(message.sendAt, now)) {
                host.repository.markScheduleMissed(message.id)
                host.notifier.notifyScheduleMissed(message)
                continue
            }
            sendOne(context, message)
        }
    }

    /** «الان بفرست» برای یک پیامک زمان‌بندی‌شده، یا رسیدن زمانش. */
    suspend fun sendOne(context: Context, message: ScheduledMessageEntity) {
        val host = context.telephonyHost
        val recipients = message.addresses.filter { it.isNotBlank() }
        if (recipients.isEmpty()) {
            host.repository.removeScheduled(message.id)
            return
        }
        val sent = runCatching {
            if (recipients.size > 1) {
                host.mmsSender.send(recipients, message.body, emptyList(), message.subId)
            } else {
                host.smsSender.send(recipients.single(), message.body, message.subId)
            }
        }
        if (sent.isSuccess) {
            // از این به بعد پیامک در provider و ایندکس است؛ صف دیگر لازمش ندارد.
            host.repository.removeScheduled(message.id)
        } else {
            // ثبت نشد (مثلاً اپ دیگر پیش‌فرض نیست)؛ متن نباید گم شود.
            host.repository.markScheduleMissed(message.id)
            host.notifier.notifyScheduleMissed(message)
        }
    }
}
