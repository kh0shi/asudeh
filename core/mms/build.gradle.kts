plugins {
    alias(libs.plugins.kotlin.jvm)
}

/*
 * کدگذاری و خواندن PDU پیام چندرسانه‌ای (D26). این ماژول خالص JVM است و هیچ
 * وابستگی به اندروید یا شبکه ندارد؛ دریافت و ارسال واقعی را خود سیستم با
 * `SmsManager` انجام می‌دهد، پس اپ مجوز INTERNET لازم ندارد (ADR-0002).
 */

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
    testImplementation(libs.junit)
}
