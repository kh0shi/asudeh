package ir.asudehapp.sms.data

/**
 * مقایسهٔ ایندکس محلی با provider (D21).
 *
 * همگام‌سازی بر اساس «بزرگ‌ترین شناسهٔ دیده‌شده» نیست، چون `ReceivePipeline` هم
 * مستقیم در ایندکس می‌نویسد: اگر اولین همگام‌سازی کامل نشده باشد، اولین پیامک
 * زنده سقف را بالا می‌برد و همهٔ پیامک‌های قدیمی‌تر برای همیشه جا می‌مانند
 * (`SilentLoss`). به‌جای آن، دو مجموعهٔ شناسه با هم مقایسه می‌شوند.
 *
 * ترتیب خواندن مهم است: **اول ایندکس، بعد provider**. هر شناسه‌ای که در ایندکس
 * هست، پیش از آن در provider نوشته شده؛ پس اگر در خواندن بعدی provider نباشد،
 * واقعاً بیرون از اپ پاک شده است.
 */
data class SyncPlan(
    /** در provider هست ولی در ایندکس نیست. */
    val missing: List<Long>,
    /** در ایندکس هست ولی دیگر در provider نیست. */
    val vanished: List<Long>,
    /** ایندکس پیش از این همگام‌سازی خالی بود: این اولین اجراست (`HistorySweep`). */
    val firstRun: Boolean,
) {
    companion object {
        fun of(indexed: Collection<Long>, provider: Collection<Long>): SyncPlan {
            val indexedSet = indexed.toHashSet()
            val providerSet = provider.toHashSet()
            return SyncPlan(
                missing = provider.filterNot { it in indexedSet }.sorted(),
                vanished = indexed.filter { it > 0 && it !in providerSet }.sorted(),
                firstRun = indexedSet.isEmpty(),
            )
        }
    }
}

/**
 * پیشرفت یک همگام‌سازی، برای نوار درصددار `HistorySweep` (D48).
 *
 * «checkpoint» جدایی لازم نیست: هر تکه پیش از تکهٔ بعد در ایندکس نوشته می‌شود
 * و [SyncPlan] هر بار از نو «در provider هست، در ایندکس نیست» را حساب می‌کند،
 * پس همگام‌سازیِ نیمه‌کاره بعد از بسته شدن اپ از همان‌جا ادامه می‌یابد
 * (ADR-0015).
 */
data class SyncProgress(val done: Int, val total: Int) {

    /** کسر انجام‌شده بین ۰ و ۱؛ برای کار خالی `null` (نوار بی‌درصد). */
    val fraction: Float?
        get() = if (total <= 0) null else (done.coerceIn(0, total).toFloat() / total)

    /** درصد گردشده به پایین، تا ۱۰۰ فقط وقتی همه تمام شده‌اند دیده شود. */
    val percent: Int?
        get() = if (total <= 0) null else done.coerceIn(0, total) * FULL / total

    private companion object {
        const val FULL = 100
    }
}
