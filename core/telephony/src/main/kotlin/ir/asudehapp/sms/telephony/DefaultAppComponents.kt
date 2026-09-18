package ir.asudehapp.sms.telephony

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * اندروید برای اینکه یک اپ بتواند اپ پیامک پیش‌فرض شود، وجود این چهار جزء را
 * لازم می‌داند: گیرندهٔ `SMS_DELIVER`، گیرندهٔ `WAP_PUSH_DELIVER`، سرویس
 * «پاسخ با پیامک» و یک Activity برای `sendto`.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}

/**
 * MMS در MVP جزو دامنه است (D26) ولی هنوز پیاده نشده است. این گیرنده فقط اعلام
 * وجود می‌کند تا نقش اپ پیش‌فرض گرفته شود و پیام در لاگ ثبت شود؛ پیام‌های MMS
 * دست‌نخورده در provider سیستم می‌مانند، پس چیزی گم نمی‌شود.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("AsudehMms", "پیام MMS دریافت شد؛ نمایش MMS هنوز پیاده نشده است")
    }
}
