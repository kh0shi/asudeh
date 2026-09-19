package ir.asudehapp.sms.telephony

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * فایل‌های موقت PDU برای `SmsManager`. سرویس MMS سیستم (نه خود اپ) پیام را از
 * MMSC می‌گیرد و در این فایل می‌نویسد، یا PDU ارسالی را از آن می‌خواند؛ برای
 * همین آسوده مجوز INTERNET لازم ندارد (ADR-0002).
 *
 * export نشده است؛ `SmsManager` خودش دسترسی به همین یک نشانی را به سرویس
 * تلفن می‌دهد (`grantUriPermissions`).
 */
class MmsFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = context ?: throw FileNotFoundException()
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        // فقط نام ساده، تا هیچ مسیری بیرون از پوشهٔ PDUها باز نشود.
        if (!NAME.matches(name)) throw FileNotFoundException()
        val file = File(directory(context), name)
        val flags = if (mode.contains('w')) {
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        } else {
            ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(file, flags)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String = "application/vnd.wap.mms-message"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private val NAME = Regex("""[a-z]+-[0-9]+\.pdu""")

        fun directory(context: Context): File = File(context.cacheDir, "mms").apply { mkdirs() }

        fun file(context: Context, name: String): File = File(directory(context), name)

        fun uri(context: Context, name: String): Uri = Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.mmsfiles")
            .appendPath(name)
            .build()
    }
}
