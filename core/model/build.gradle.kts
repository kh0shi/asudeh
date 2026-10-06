plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // فقط برای یکسان‌سازی متن کلیدواژه‌ها و ارقام الگوهای فرستندهٔ «قواعد من» (ADR-0012، ADR-0016).
    api(project(":core:persian"))

    testImplementation(libs.junit)
}
