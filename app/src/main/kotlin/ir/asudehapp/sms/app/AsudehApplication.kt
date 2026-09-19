package ir.asudehapp.sms.app

import android.app.Application
import android.content.Context
import android.content.Intent
import ir.asudehapp.sms.data.AsudehRepository
import ir.asudehapp.sms.telephony.AsudehNotifier
import ir.asudehapp.sms.telephony.NotificationChannels
import ir.asudehapp.sms.telephony.SmsSender
import ir.asudehapp.sms.telephony.TelephonyHost

/**
 * ظرف وابستگی‌های اپ: تزریق وابستگی دستی (ADR-0007). هر وابستگی یک بار و
 * تنبل ساخته می‌شود؛ گیرنده‌ها و سرویس از راه [TelephonyHost] به آن می‌رسند و
 * ViewModel از راه [containerOf].
 */
class AsudehApplication : Application(), TelephonyHost {

    override val repository: AsudehRepository by lazy { AsudehRepository(this) }

    override val notifier: AsudehNotifier by lazy {
        AsudehNotifier(this) { threadId -> conversationIntent(threadId) }
    }

    override val smsSender: SmsSender by lazy { SmsSender(this, repository) }

    override fun conversationIntent(threadId: Long): Intent = MainActivity.intentFor(this, threadId)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        // ایندکس نسخه‌های پیش از انتشار، با schema قدیمی. فقط کپی provider بود و
        // همگام‌سازی بعدی آن را در `index.db` از نو می‌سازد.
        deleteDatabase(LEGACY_INDEX)
    }

    companion object {
        private const val LEGACY_INDEX = "asudeh-index.db"

        fun containerOf(context: Context): AsudehApplication =
            context.applicationContext as AsudehApplication
    }
}
