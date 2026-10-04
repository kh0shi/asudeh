package ir.asudehapp.sms.model

/**
 * تراشه‌های فیلتر بالای صندوق (ROADMAP D5). فیلتر فقط نمایشی است: `Folder` و
 * `Router` دست نمی‌خورند و هیچ پیامکی جابه‌جا نمی‌شود. گفتگویی در فیلتر دیده
 * می‌شود که دست‌کم یک پیامک صندوقش از این دسته‌ها باشد. «شخصی» پیامک‌های
 * `UNKNOWN` را هم دارد، چون طبق `ShowOnDoubt` همان‌ها هم در صندوق مانده‌اند.
 */
enum class InboxFilter(val categories: Set<Category>?) {
    ALL(null),
    PERSONAL(setOf(Category.PERSONAL, Category.UNKNOWN)),
    BANK(setOf(Category.BANK)),
    OTP(setOf(Category.OTP)),
    SERVICE(setOf(Category.SERVICE)),
}
