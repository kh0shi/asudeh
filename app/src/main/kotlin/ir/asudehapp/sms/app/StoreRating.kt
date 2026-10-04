package ir.asudehapp.sms.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * صفحهٔ امتیاز آسوده در بازار (D67). این فقط باز کردن اپ دیگری است و مجوز
 * INTERNET نمی‌خواهد. برای دیدن بازار، manifest آن را در `<queries>` نام برده
 * است؛ اگر بازار نصب نباشد، کارت اصلاً نشان داده نمی‌شود.
 */
object StoreRating {

    private const val BAZAAR_PACKAGE = "com.farsitel.bazaar"
    private const val TAG = "AsudehRating"

    private fun intent(context: Context): Intent =
        Intent(Intent.ACTION_EDIT, Uri.parse("bazaar://details?id=${context.packageName}"))
            .setPackage(BAZAAR_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun available(context: Context): Boolean =
        runCatching { context.packageManager.resolveActivity(intent(context), 0) != null }.getOrDefault(false)

    fun open(context: Context) {
        try {
            context.startActivity(intent(context))
        } catch (failure: ActivityNotFoundException) {
            Log.w(TAG, "بازار باز نشد", failure)
        }
    }
}
