package ir.asudehapp.sms.telephony

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import ir.asudehapp.sms.mms.MmsPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * پیوست‌های غیر از عکس برای MMS: ویدیو، صدا و کارت مخاطب (PARITY §الف).
 * برخلاف [MmsImages]، این‌ها کوچک‌شدنی نیستند؛ اگر از سقف حجم اپراتور
 * بگذرند، پیوست رد می‌شود (خروجی `null`) به‌جای فرستادن ناقص.
 */
object MmsAttachments {

    suspend fun prepare(context: Context, uri: Uri, subscriptionId: Int): MmsPart? = withContext(Dispatchers.IO) {
        val limit = MmsImages.maxMessageSize(context, subscriptionId) - HEADROOM
        val type = context.contentResolver.getType(uri)?.lowercase() ?: return@withContext null
        val data = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext null
        if (data.size > limit) return@withContext null
        MmsPart(type, data, name = displayName(context, uri, type))
    }

    private fun displayName(context: Context, uri: Uri, type: String): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        if (!fromProvider.isNullOrBlank()) return fromProvider
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: "bin"
        return "attachment.$extension"
    }

    /** جای سرایندها، SMIL و متن؛ همان سهمی که [MmsImages] برای خودش می‌گذارد. */
    private const val HEADROOM = 8 * 1024
}
