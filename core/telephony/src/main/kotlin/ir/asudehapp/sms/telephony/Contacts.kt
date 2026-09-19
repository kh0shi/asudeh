package ir.asudehapp.sms.telephony

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * مخاطب‌ها فقط برای قفل ایمنی D31 خوانده می‌شوند: پیامک از یک مخاطب ذخیره‌شده
 * هرگز خودکار پنهان نمی‌شود.
 *
 * مجوز مخاطب‌ها اختیاری است؛ اگر کاربر آن را نداده باشد، همه‌چیز کار می‌کند و
 * فقط این قفل روی شماره‌های غیرموبایل بی‌اثر می‌ماند.
 */
object Contacts {

    suspend fun isKnown(context: Context, address: String): Boolean {
        if (address.isBlank()) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(address),
                )
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup._ID),
                    null,
                    null,
                    null,
                )?.use { it.count > 0 } ?: false
            }.getOrDefault(false)
        }
    }

    suspend fun displayName(context: Context, address: String): String? {
        if (address.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(address),
                )
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
        }
    }
}
