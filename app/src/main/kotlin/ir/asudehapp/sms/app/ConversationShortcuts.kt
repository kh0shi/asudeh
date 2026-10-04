package ir.asudehapp.sms.app

import android.content.Context
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import ir.asudehapp.sms.R

/**
 * میان‌بر گفتگو (ROADMAP E3): لمس طولانی آیکون اپ، برگهٔ اشتراک‌گذاری، و بخش
 * «گفتگوها»ی اعلان‌ها در اندروید ۱۱ به بالا. فقط گفتگوهایی که پیامک شخصی
 * گرفته یا فرستاده‌اند میان‌بر می‌گیرند؛ سیستم خودش پرکاربردترین‌ها را نگه
 * می‌دارد و قدیمی‌ترها را کنار می‌گذارد (`pushDynamicShortcut`).
 *
 * هیچ مجوزی لازم نیست و هیچ چیزی از اپ بیرون نمی‌رود: میان‌بر فقط شناسهٔ
 * گفتگو و نامی است که همین حالا در اعلان هم دیده می‌شود.
 */
object ConversationShortcuts {

    /** همان دستهٔ `share-target` در `res/xml/shortcuts.xml`. */
    const val SHARE_CATEGORY: String = "ir.asudehapp.sms.category.SHARE_TARGET"

    private const val PREFIX = "thread-"

    fun id(threadId: Long): String = PREFIX + threadId

    /** گفتگوی یک میان‌بر، برای اشتراک‌گذاری مستقیم از برگهٔ اشتراک. */
    fun threadIdOf(shortcutId: String?): Long? =
        shortcutId?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toLongOrNull()

    /** میان‌بر را می‌سازد یا بالا می‌برد و شناسه‌اش را برای `MessagingStyle` برمی‌گرداند. */
    fun push(context: Context, threadId: Long, name: String): String? {
        if (threadId <= 0) return null
        val id = id(threadId)
        val info = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(name)
            .setLongLabel(name)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(MainActivity.intentFor(context, threadId).setAction(MainActivity.ACTION_OPEN_CONVERSATION))
            .setLongLived(true)
            .setPerson(Person.Builder().setName(name).setKey(id).build())
            .setLocusId(LocusIdCompat(id))
            .setCategories(setOf(SHARE_CATEGORY))
            .build()
        return id.takeIf { runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, info) }.isSuccess }
    }

    /** گفتگویی که دیگر پیامکی ندارد، میان‌برش هم نمی‌ماند. */
    fun remove(context: Context, threadIds: Collection<Long>) {
        if (threadIds.isEmpty()) return
        runCatching { ShortcutManagerCompat.removeLongLivedShortcuts(context, threadIds.map(::id)) }
    }
}
