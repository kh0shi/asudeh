package ir.asudehapp.sms.app

import android.app.Application
import android.content.Context
import ir.asudehapp.sms.data.AsudehRepository
import ir.asudehapp.sms.telephony.NotificationChannels
import ir.asudehapp.sms.telephony.SmsDeliverReceiver

/**
 * تزریق وابستگی دستی. D55 پیشنهاد Hilt داده بود، ولی آن تصمیم هنوز پیش‌نویس
 * است و برای این اندازه از اپ یک ظرف ساده کافی است.
 */
class AsudehApplication : Application() {

    val repository: AsudehRepository by lazy { AsudehRepository(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        SmsDeliverReceiver.conversationIntent = { context, threadId ->
            MainActivity.intentFor(context, threadId)
        }
    }

    companion object {
        fun repositoryOf(context: Context): AsudehRepository =
            (context.applicationContext as AsudehApplication).repository
    }
}
