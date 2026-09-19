package ir.asudehapp.sms.telephony

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import ir.asudehapp.sms.mms.MmsPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * آماده کردن تصویر برای MMS: کوچک کردن تا زیر سقف حجم اپراتور. همه‌چیز روی
 * خود گوشی انجام می‌شود (`NoNet`).
 */
object MmsImages {

    /** خروجی `null` یعنی تصویر خوانده نشد. */
    suspend fun prepare(context: Context, uri: Uri, subscriptionId: Int): MmsPart? = withContext(Dispatchers.IO) {
        val limit = maxMessageSize(context, subscriptionId) - HEADROOM
        val type = context.contentResolver.getType(uri)?.lowercase()
        // GIF و تصویر کوچک همان‌طور که هست فرستاده می‌شود تا کیفیت یا حرکتش از دست نرود.
        val original = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext null
        if (type != null && type.startsWith("image/") && original.size <= limit &&
            type in setOf("image/jpeg", "image/png", "image/gif")
        ) {
            return@withContext MmsPart(type, original, name = "image.${type.substringAfter('/')}")
        }

        val bitmap = decode(context, uri, MAX_DIMENSION) ?: return@withContext null
        compress(bitmap, limit)?.let { MmsPart("image/jpeg", it, name = "image.jpg") }
    }

    /** اول کیفیت کم می‌شود، بعد اندازه؛ تا حجم زیر [limit] برود. */
    private fun compress(bitmap: Bitmap, limit: Int): ByteArray? {
        var scaled = bitmap
        var quality = START_QUALITY
        while (true) {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= limit) return out.toByteArray()
            if (quality > MIN_QUALITY) {
                quality -= QUALITY_STEP
                continue
            }
            val width = (scaled.width * SHRINK).toInt()
            val height = (scaled.height * SHRINK).toInt()
            if (width < MIN_DIMENSION || height < MIN_DIMENSION) return null
            scaled = Bitmap.createScaledBitmap(scaled, width, height, true)
            quality = START_QUALITY
        }
    }

    /** سقف حجم MMS اپراتور، از پیکربندی خود سیستم. */
    fun maxMessageSize(context: Context, subscriptionId: Int): Int = runCatching {
        CarrierMms.maxSize(context, subscriptionId)
    }.getOrNull()?.takeIf { it > 0 } ?: DEFAULT_MAX_SIZE

    private fun decode(context: Context, uri: Uri, maxDimension: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder چرخش EXIF را خودش اعمال می‌کند.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val size = info.size
                val largest = max(size.width, size.height)
                if (largest > maxDimension) {
                    val ratio = maxDimension.toFloat() / largest
                    decoder.setTargetSize((size.width * ratio).toInt(), (size.height * ratio).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }.getOrNull()

    private const val DEFAULT_MAX_SIZE = 300 * 1024
    /** جای سرایندها، SMIL و متن. */
    private const val HEADROOM = 8 * 1024
    private const val MAX_DIMENSION = 1600
    private const val MIN_DIMENSION = 160
    private const val START_QUALITY = 85
    private const val MIN_QUALITY = 55
    private const val QUALITY_STEP = 10
    private const val SHRINK = 0.75
}

private object CarrierMms {
    fun maxSize(context: Context, subscriptionId: Int): Int =
        SmsSender.smsManagerFor(context, subscriptionId)
            .carrierConfigValues
            .getInt(android.telephony.SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 0)
}
