package ir.asudehapp.sms.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "ir.asudehapp.sms"

/**
 * کارایی پیمایش فهرست گفتگوها، همان لیست Paging 3 روی
 * `thread_summary` که تازه اضافه شده. فقط چند بار پیمایش سادهٔ صفحهٔ خانه؛
 * هدف چک‌کردن پرش فریم است، نه دنبالهٔ تعاملی پیچیده (D63).
 *
 * دستور اجرا: `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ThreadListScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scroll() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        val list = device.findObject(By.scrollable(true)) ?: return@measureRepeated
        repeat(3) {
            list.scroll(Direction.DOWN, 0.8f)
            device.waitForIdle()
        }
        device.wait(Until.hasObject(By.scrollable(true)), 1_000)
    }
}
