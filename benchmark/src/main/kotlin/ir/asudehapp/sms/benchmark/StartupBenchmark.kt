package ir.asudehapp.sms.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** شناسهٔ پکیج در app/build.gradle.kts؛ ADR-0001 تغییرش نمی‌دهد. */
private const val TARGET_PACKAGE = "ir.asudehapp.sms"

/**
 * زمان راه‌اندازی سرد و گرم آسوده (D63، پیوست M5/M9 در docs/PARITY.md).
 *
 * این آزمون روی یک گوشی وصل‌شده اجرا می‌شود، نه CI (docs/DESIGN-REVIEW.md#D63)؛
 * دستور اجرا: `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCold() = startup(CompilationMode.None())

    @Test
    fun startupWithBaselineProfile() = startup(CompilationMode.Partial())

    private fun startup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = compilationMode,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
