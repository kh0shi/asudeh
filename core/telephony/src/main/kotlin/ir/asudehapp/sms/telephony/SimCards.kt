package ir.asudehapp.sms.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/** یک سیم‌کارت فعال (D27). */
data class SimCard(
    val subscriptionId: Int,
    /** شمارهٔ شیار، از ۱. */
    val slot: Int,
    val label: String,
)

/**
 * سیم‌کارت‌های فعال (D27). خواندن فهرست سیم‌کارت‌ها مجوز `READ_PHONE_STATE`
 * می‌خواهد؛ این مجوز اختیاری است و فقط وقتی خواسته می‌شود که کاربر سیم
 * ارسال را عوض کند. بدون آن، پاسخ از همان سیمی می‌رود که پیامک آخر به آن
 * رسیده است.
 */
object SimCards {

    fun canRead(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun active(context: Context): List<SimCard> {
        if (!canRead(context)) return emptyList()
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return runCatching {
            manager.activeSubscriptionInfoList.orEmpty().map { info ->
                SimCard(
                    subscriptionId = info.subscriptionId,
                    slot = info.simSlotIndex + 1,
                    label = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                        ?: info.carrierName?.toString().orEmpty(),
                )
            }.sortedBy { it.slot }
        }.getOrDefault(emptyList())
    }

    /**
     * شمارهٔ شیار یک سیم، برای نشان کوچک روی پیامک؛ `null` اگر معلوم نباشد.
     * از اندروید ۱۰ بدون هیچ مجوزی در دسترس است.
     */
    fun slotOf(context: Context, subscriptionId: Int): Int? {
        if (subscriptionId < 0) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val slot = SubscriptionManager.getSlotIndex(subscriptionId)
            return if (slot >= 0) slot + 1 else null
        }
        return active(context).firstOrNull { it.subscriptionId == subscriptionId }?.slot
    }

    /** شماره‌های خود کاربر، برای جدا کردنش از اعضای MMS گروهی؛ اگر معلوم باشد. */
    @SuppressLint("MissingPermission", "HardwareIds")
    @Suppress("DEPRECATION")
    fun ownNumbers(context: Context): Set<String> {
        if (!canRead(context)) return emptySet()
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptySet()
        return runCatching {
            manager.activeSubscriptionInfoList.orEmpty()
                .mapNotNull { it.number?.takeIf { number -> number.isNotBlank() } }
                .toSet()
        }.getOrDefault(emptySet())
    }
}
