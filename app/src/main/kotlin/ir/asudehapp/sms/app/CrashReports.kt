package ir.asudehapp.sms.app

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * گزارش خطا بدون اینترنت (D58). کرش در یک فایل محلی نوشته می‌شود و در اجرای
 * بعدی به کاربر پیشنهاد می‌شود که با منوی اشتراک‌گذاری بفرستد. گزارش پیش از
 * ارسال کامل نشان داده می‌شود.
 *
 * **هرگز متن پیامک یا شماره‌ای در گزارش نیست:** فقط نام کلاس خطا و جای آن در
 * کد نوشته می‌شود، نه پیام خطا (که ممکن است متن یا شماره داشته باشد).
 */
class CrashReports(private val context: Context) {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file.writeText(format(error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** گزارش کرش قبلی، اگر باشد. */
    fun pending(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()

    fun discard() {
        runCatching { file.delete() }
    }

    private fun format(error: Throwable): String = buildString {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        appendLine("Asudeh $version")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("time=${System.currentTimeMillis()}")
        appendLine()
        append(redactedTrace(error))
    }

    companion object {
        private const val FILE_NAME = "last-crash.txt"
        private const val MAX_CAUSES = 5

        /** رد کامل خطا، بدون پیام‌های خطا. */
        fun redactedTrace(error: Throwable): String {
            val out = StringWriter()
            val writer = PrintWriter(out)
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth < MAX_CAUSES) {
                writer.println((if (depth == 0) "" else "Caused by: ") + current.javaClass.name)
                for (frame in current.stackTrace) writer.println("\tat $frame")
                current = current.cause
                depth++
            }
            writer.flush()
            return out.toString()
        }
    }
}
