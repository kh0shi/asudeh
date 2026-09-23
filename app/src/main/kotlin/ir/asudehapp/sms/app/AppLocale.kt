package ir.asudehapp.sms.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import ir.asudehapp.sms.data.AppLanguage
import ir.asudehapp.sms.data.AsudehSettings
import java.util.Locale

/**
 * زبان رابط را از تنظیمات کاربر روی [Context] می‌نشاند؛ «مثل گوشی» (پیش‌فرض)
 * چیزی را عوض نمی‌کند. جهت چیدمان از خود زبان می‌آید، پس انگلیسی چپ‌به‌راست است.
 */
object AppLocale {

    fun wrap(base: Context): Context {
        val config = configFor(base, AsudehSettings.storedLanguage(base)) ?: return base
        return base.createConfigurationContext(config)
    }

    /**
     * `Application` (و اعلان‌هایی که با آن ساخته می‌شوند) بعد از تغییر زبان هم
     * باید با زبان تازه بنویسد، بی‌آنکه فرایند از نو شروع شود.
     */
    @Suppress("DEPRECATION")
    fun refresh(application: Application, language: AppLanguage) {
        val config = configFor(application, language) ?: return
        application.resources.updateConfiguration(config, application.resources.displayMetrics)
    }

    private fun configFor(base: Context, language: AppLanguage): Configuration? {
        val locale = when (language) {
            AppLanguage.SYSTEM -> return null
            AppLanguage.FA -> Locale("fa")
            AppLanguage.EN -> Locale.ENGLISH
        }
        return Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
}
