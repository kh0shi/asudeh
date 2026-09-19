package ir.asudehapp.sms.telephony

import android.content.Context
import android.content.Intent
import ir.asudehapp.sms.data.AsudehRepository
import ir.asudehapp.sms.data.AsudehSettings
import ir.asudehapp.sms.model.Folder

/**
 * آنچه اجزای اپ پیش‌فرض (گیرنده‌ها و سرویس) از اپ لازم دارند. `Application`
 * آن را پیاده می‌کند؛ این همان ظرف وابستگی دستی ADR-0007 است، بدون اینکه
 * `:core:telephony` به کلاس‌های `:app` وابسته شود.
 */
interface TelephonyHost {
    val repository: AsudehRepository
    val settings: AsudehSettings
    val smsSender: SmsSender
    val mmsSender: MmsSender
    val notifier: AsudehNotifier

    /** Intent باز کردن یک گفتگو، برای لمس اعلان. */
    fun conversationIntent(threadId: Long): Intent

    /** Intent باز کردن یک پوشه، برای لمس اعلان `Digest`. */
    fun folderIntent(folder: Folder): Intent
}

internal val Context.telephonyHost: TelephonyHost
    get() = applicationContext as TelephonyHost
