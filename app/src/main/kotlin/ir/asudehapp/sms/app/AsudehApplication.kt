package ir.asudehapp.sms.app

import android.app.Application
import android.content.Context
import android.content.Intent
import ir.asudehapp.sms.data.AsudehRepository
import ir.asudehapp.sms.data.AsudehSettings
import ir.asudehapp.sms.data.BackupManager
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.telephony.AsudehNotifier
import ir.asudehapp.sms.telephony.DigestScheduler
import ir.asudehapp.sms.telephony.MmsSender
import ir.asudehapp.sms.telephony.NotificationChannels
import ir.asudehapp.sms.telephony.SmsSender
import ir.asudehapp.sms.telephony.TelephonyHost

/**
 * ظرف وابستگی‌های اپ: تزریق وابستگی دستی (ADR-0007). هر وابستگی یک بار و
 * تنبل ساخته می‌شود؛ گیرنده‌ها و سرویس از راه [TelephonyHost] به آن می‌رسند و
 * ViewModel از راه [containerOf].
 */
class AsudehApplication : Application(), TelephonyHost {

    override val settings: AsudehSettings by lazy { AsudehSettings(this) }

    override val repository: AsudehRepository by lazy { AsudehRepository(this, settings = settings) }

    override val notifier: AsudehNotifier by lazy {
        AsudehNotifier(
            context = this,
            openConversation = { threadId -> conversationIntent(threadId) },
            openFolder = { folder -> folderIntent(folder) },
            isMuted = { threadId -> repository.isMuted(threadId) },
        )
    }

    override val smsSender: SmsSender by lazy { SmsSender(this, repository) }

    override val mmsSender: MmsSender by lazy { MmsSender(this, repository) }

    val backup: BackupManager by lazy { BackupManager(this, repository) }

    val crashReports: CrashReports by lazy { CrashReports(this) }

    override fun conversationIntent(threadId: Long): Intent = MainActivity.intentFor(this, threadId)

    override fun folderIntent(folder: Folder): Intent = MainActivity.folderIntent(this, folder)

    override fun onCreate() {
        super.onCreate()
        crashReports.install()
        NotificationChannels.ensure(this)
        DigestScheduler.schedule(this)
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
