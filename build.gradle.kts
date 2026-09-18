// پلاگین‌های Kotlin اینجا روی classpath ریشه می‌آیند تا در همهٔ ماژول‌ها یک بار
// بارگذاری شوند. Android Gradle Plugin عمداً اینجا نیست، تا ماژول‌های خالص
// Kotlin بدون Android SDK هم ساخته و آزموده شوند.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
