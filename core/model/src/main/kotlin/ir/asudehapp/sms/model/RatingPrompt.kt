package ir.asudehapp.sms.model

/**
 * آنچه برای تصمیم «لحظهٔ موفق» (D67) لازم است. زمان‌ها میلی‌ثانیه‌اند.
 */
data class RatingSignals(
    /** نخستین نصب اپ. */
    val firstUseAt: Long,
    /** تبلیغ‌هایی که تا امروز پنهان شده‌اند. */
    val hiddenAds: Int,
    /** آخرین `Rescue`؛ `null` یعنی هرگز. */
    val lastRescueAt: Long?,
    /** کارت پیش‌تر یک بار نشان داده شده است. */
    val alreadyAsked: Boolean,
    /** بازار نصب است؛ بدون آن کارت اصلاً دیده نمی‌شود. */
    val storeInstalled: Boolean,
)

/**
 * درخواست امتیاز فقط در «لحظهٔ موفق» (D67، اصل ۷): پس از ۱۴ روز استفاده **و**
 * دست‌کم ۲۰ تبلیغ پنهان‌شده **و** هیچ `Rescue` در ۷ روز گذشته، یک بار و در
 * `PromoFolder`، نه وسط کار.
 */
object RatingPrompt {

    const val MIN_DAYS_OF_USE: Int = 14
    const val MIN_HIDDEN_ADS: Int = 20
    const val QUIET_DAYS_AFTER_RESCUE: Int = 7

    fun shouldAsk(signals: RatingSignals, now: Long): Boolean {
        if (signals.alreadyAsked || !signals.storeInstalled) return false
        val usedLongEnough = now - signals.firstUseAt >= days(MIN_DAYS_OF_USE)
        val enoughAds = signals.hiddenAds >= MIN_HIDDEN_ADS
        val noRecentRescue = signals.lastRescueAt?.let { now - it >= days(QUIET_DAYS_AFTER_RESCUE) } ?: true
        return usedLongEnough && enoughAds && noRecentRescue
    }

    private fun days(count: Int): Long = count * DAY_MILLIS

    private const val DAY_MILLIS = 24L * 60 * 60 * 1_000
}
