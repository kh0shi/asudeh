package ir.asudehapp.sms.app

import android.content.Context

/** نسخهٔ اپ، برای صفحهٔ «درباره» و سرایند پشتیبان. */
object BuildConfigInfo {
    fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
}
