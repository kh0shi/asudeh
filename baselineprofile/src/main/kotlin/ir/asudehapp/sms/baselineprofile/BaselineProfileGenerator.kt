package ir.asudehapp.sms.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "ir.asudehapp.sms"

/**
 * تولید `app/src/main/baseline-prof.txt` (D63): مسیر بحرانی کاربر یک بار
 * طی می‌شود -- باز کردن اپ تا رسیدن به فهرست گفتگوها -- تا مسیر داغ
 * راه‌اندازی و اولین ترسیم برای AOT از پیش کامپایل شود. این یک مجموعهٔ
 * آزمون UI کامل نیست، فقط همین یک سفر کوتاه.
 *
 * دستور اجرا: `./gradlew :app:generateBaselineProfile` (به گوشی وصل‌شده و
 * باز/بی‌قفل نیاز دارد).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        maxIterations = 3,
    ) {
        pressHome()
        startActivityAndWait()
        // فهرست گفتگوها (یا صفحهٔ خوش‌آمد در اولین اجرا) واقعاً روی صفحه
        // ترسیم شود، نه فقط اینکه اکتیویتی شروع شده باشد.
        device.wait(Until.hasObject(androidx.test.uiautomator.By.pkg(TARGET_PACKAGE).depth(0)), 5_000)
    }
}
