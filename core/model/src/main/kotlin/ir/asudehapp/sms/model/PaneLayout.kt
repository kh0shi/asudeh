package ir.asudehapp.sms.model

/**
 * فهرست و گفتگو کنار هم، یا یکی در هر صفحه (ADR-0020). اندازه‌ها dp پنجره‌اند،
 * با همان مرزهای کلاس اندازهٔ پنجرهٔ Material.
 */
object PaneLayout {

    /** کمتر از این ارتفاع (گوشی افقی) هرگز دوستونی نیست. */
    const val MIN_HEIGHT_DP: Int = 480

    /** از این عرض، دوستونی است. */
    const val EXPANDED_WIDTH_DP: Int = 840

    /** از این عرض، فقط وقتی پنجره افقی است. */
    const val MEDIUM_WIDTH_DP: Int = 600

    fun isTwoPane(widthDp: Int, heightDp: Int): Boolean = when {
        heightDp < MIN_HEIGHT_DP -> false
        widthDp >= EXPANDED_WIDTH_DP -> true
        else -> widthDp >= MEDIUM_WIDTH_DP && widthDp > heightDp
    }
}
