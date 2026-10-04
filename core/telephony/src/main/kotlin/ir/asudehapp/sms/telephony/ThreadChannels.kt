package ir.asudehapp.sms.telephony

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi

/**
 * کانال اعلان جدا برای یک گفتگو (ROADMAP E4)، تا کاربر صدای دلخواهش را برای
 * همان گفتگو بگذارد. فقط وقتی ساخته می‌شود که کاربر از منوی گفتگو آن را بخواهد،
 * نه برای همه؛ و با خالی شدن گفتگو پاک می‌شود. پیش از اندروید ۸ کانالی نیست و
 * همهٔ گفتگوها کانال «شخصی» را دارند.
 *
 * «بی‌صدا»ی گفتگو (M9) همچنان مقدم است: گفتگوی بی‌صدا اصلاً اعلان نمی‌گیرد.
 */
object ThreadChannels {

    private const val PREFIX = "asudeh.thread."

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun id(threadId: Long): String = PREFIX + threadId

    /** کانال خود گفتگو اگر کاربر ساخته باشد، وگرنه `null`. */
    fun existing(context: Context, threadId: Long): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            id(threadId).takeIf { manager(context)?.getNotificationChannel(it) != null }
        } else {
            null
        }

    /** کانال را (اگر نیست) می‌سازد و صفحهٔ تنظیم صدایش را برمی‌گرداند. */
    fun settingsIntent(context: Context, threadId: Long, name: String): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createIfMissing(context, threadId, name)
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, id(threadId))
        } else {
            null
        }

    fun delete(context: Context, threadIds: Collection<Long>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        for (threadId in threadIds) runCatching { manager(context)?.deleteNotificationChannel(id(threadId)) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createIfMissing(context: Context, threadId: Long, name: String) {
        val manager = manager(context) ?: return
        NotificationChannels.ensure(context)
        if (manager.getNotificationChannel(id(threadId)) != null) return
        val base = manager.getNotificationChannel(NotificationChannels.PERSONAL)
        manager.createNotificationChannel(
            NotificationChannel(
                id(threadId),
                context.getString(R.string.channel_thread, name),
                base?.importance ?: NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(NotificationManager::class.java)
}
