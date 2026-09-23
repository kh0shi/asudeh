plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ir.asudehapp.sms.benchmark"
    compileSdk = 35

    defaultConfig {
        // آسوده روی این ماژول نصب نمی‌شود؛ فقط برای اندازه‌گیری کارایی روی
        // گوشی واقعی است. حداقل ۲۸ چون سنجش کامل راه‌اندازی
        // (StartupTimingMetric) از اینجا به بعد قابل اعتماد است (D63).
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // این build-type محلی، با همین نام، با build-type «benchmarkRelease» که
    // پلاگین androidx.baselineprofile خودکار به ماژول app اضافه می‌کند جفت
    // می‌شود (غیرقابل دیباگ، minify+shrink مثل release واقعی، profileable —
    // دقیقاً همان چیزی که عدد راه‌اندازی واقعی را می‌دهد). چون
    // app/build.gradle.kts اصلاً signingConfig ندارد، پلاگین خودش
    // «benchmarkRelease» را با کلید دیباگ استاندارد اندروید امضا می‌کند
    // (تأییدشده با signingReport)؛ اینجا هم همان کلید برای ماژول آزمون لازم
    // است تا نصب شود. کلید امضای انتشار دست‌نخورده می‌ماند.
    buildTypes {
        create("benchmarkRelease") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("benchmarkRelease", "release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
