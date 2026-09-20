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
    // فقط برای یکسان‌سازی متن هنگام تطبیق کلیدواژه‌های «قواعد من» (ADR-0012).
    api(project(":core:persian"))

    testImplementation(libs.junit)
}
