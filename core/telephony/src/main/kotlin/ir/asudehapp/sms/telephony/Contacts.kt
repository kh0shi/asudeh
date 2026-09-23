package ir.asudehapp.sms.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** یک شمارهٔ مخاطب، برای فهرست «گفتگوی تازه». */
data class ContactPhone(val name: String, val number: String)

/**
 * مخاطب‌ها برای قفل ایمنی D31 خوانده می‌شوند — پیامک از یک مخاطب ذخیره‌شده
 * هرگز خودکار پنهان نمی‌شود — و برای فهرست گیرنده در «گفتگوی تازه».
 *
 * مجوز مخاطب‌ها اختیاری است؛ اگر کاربر آن را نداده باشد، همه‌چیز کار می‌کند:
 * قفل روی شماره‌های غیرموبایل بی‌اثر می‌ماند و «گفتگوی تازه» به انتخابگر خود
 * اندروید تکیه می‌کند، که به مجوز نیاز ندارد. هیچ‌چیز از این داده‌ها جایی
 * ذخیره نمی‌شود.
 */
object Contacts {

    fun canRead(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * همهٔ شماره‌های مخاطب‌ها، مرتب بر اساس نام. یک شماره که در دو ردیف (خانه و
     * موبایل) تکرار شده باشد یک بار می‌آید.
     */
    suspend fun all(context: Context): List<ContactPhone> {
        if (!canRead(context)) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ),
                    null,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC",
                )?.use { cursor ->
                    val found = LinkedHashMap<String, ContactPhone>()
                    while (cursor.moveToNext()) {
                        val number = cursor.getString(1).orEmpty()
                        if (number.isBlank()) continue
                        val key = number.filterNot(Char::isWhitespace)
                        if (key in found) continue
                        found[key] = ContactPhone(cursor.getString(0).orEmpty(), number)
                    }
                    found.values.toList()
                }.orEmpty()
            }.getOrDefault(emptyList())
        }
    }

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

    /** نشانی عکس بند‌انگشتی مخاطب، برای فهرست گفتگوها. */
    suspend fun photoThumbnailUri(context: Context, address: String): Uri? {
        if (address.isBlank() || !canRead(context)) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(address),
                )
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)?.let(Uri::parse)
                    } else {
                        null
                    }
                }
            }.getOrNull()
        }
    }
}
