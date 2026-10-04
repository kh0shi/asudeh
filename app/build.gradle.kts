import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
}

/**
 * D65 (ROADMAP A3): کلید امضای release بیرون از مخزن است. مقدارها از
 * `~/.gradle/gradle.properties` یا متغیر محیطی همنام خوانده می‌شوند؛ هیچ کلید یا
 * رمزی در مخزن نیست. بدون `ASUDEH_KEYSTORE` ساخت release مثل قبل است (بدون
 * signingConfig، پس `app-release-unsigned.apk`) و CI و بنچمارک نمی‌شکنند.
 * جزئیات در docs/BUILD.md، بخش «امضای release».
 */
fun signingValue(name: String): String? =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull?.takeIf { it.isNotBlank() }

val releaseKeystore: String? = signingValue("ASUDEH_KEYSTORE")

android {
    namespace = "ir.asudehapp.sms"
    compileSdk = 35

    defaultConfig {
        // ADR-0001: شناسهٔ پکیج بعد از اولین انتشار قابل تغییر نیست.
        applicationId = "ir.asudehapp.sms"
        minSdk = 26
        targetSdk = 35
        // D66: versionName نسخهٔ SemVer است. versionCode شمارنده‌ای است که با هر
        // انتشار (بتا هم) یکی بالا می‌رود و هرگز پایین نمی‌آید؛ اندروید نصب
        // نسخه‌ای با versionCode کمتر را روی نسخهٔ نصب‌شده نمی‌پذیرد.
        versionCode = 2
        versionName = "1.0.0-beta1"

        // D64: آزمون ابزاری مسیر دریافت با Application آزمون (app/src/androidTest).
        testInstrumentationRunner = "ir.asudehapp.sms.app.AsudehTestRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            fun required(name: String): String = signingValue(name)
                ?: throw GradleException("ASUDEH_KEYSTORE تنظیم شده ولی $name نه (docs/BUILD.md)")
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = required("ASUDEH_KEYSTORE_PASSWORD")
                keyAlias = required("ASUDEH_KEY_ALIAS")
                keyPassword = required("ASUDEH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // B6: R8 کد و منابع بی‌استفاده را برمی‌دارد. قواعد keep در
            // proguard-rules.pro است؛ Room، kotlinx.serialization و androidx
            // قواعد خودشان را همراه دارند. فایل mapping هر نسخه
            // (app/build/outputs/mapping/release/mapping.txt) برای خواندن گزارش
            // خطای کاربران (D58) لازم است و باید کنار APK منتشرشده نگه داشته شود.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:persian"))
    implementation(project(":core:classifier"))
    implementation(project(":core:data"))
    implementation(project(":core:telephony"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.profileinstaller)

    baselineProfile(project(":baselineprofile"))

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

// D63: پلاگین androidx.baselineprofile خودش build-typeهای «benchmarkRelease»
// و «nonMinifiedRelease» را به این ماژول اضافه می‌کند (بدون کوچک‌سازی یا
// غیرقابل دیباگ به‌ترتیب لازم، و profileable)، دقیقاً برای اینکه لازم نباشد
// اینجا signingConfig یا build-type دستی اضافه شود؛ امضا همان کلید دیباگ
// استاندارد اندروید است.
baselineProfile {
    // چون این اپ فقط یک build-type اصلی دارد و flavor ندارد، پروفایل زیر
    // src/release/generated/... (یک به‌ازای هر build-type) ذخیره نشود؛ به‌جایش
    // در app/src/main/generated/baselineProfiles/baseline-prof.txt کنار کد
    // اصلی بماند (مسیر واقعی پلاگین با mergeIntoMain، نه یک فایل تخت).
    saveInSrc = true
    mergeIntoMain = true
}

/**
 * ADR-0002: اپ مجوز `INTERNET` ندارد، و این روی **manifest ادغام‌شده** بررسی
 * می‌شود، چون ممکن است یک کتابخانه بی‌صدا آن را اضافه کند.
 *
 * D62: فهرست مجوزها هم ثابت است و هر مجوز تازه باید عمداً به این فهرست اضافه شود.
 */
val allowedPermissions = setOf(
    "android.permission.RECEIVE_SMS",
    "android.permission.READ_SMS",
    "android.permission.SEND_SMS",
    "android.permission.RECEIVE_MMS",
    "android.permission.RECEIVE_WAP_PUSH",
    "android.permission.READ_CONTACTS",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    // اختیاری؛ فقط برای انتخاب سیم ارسال و شناختن شمارهٔ خود کاربر در MMS گروهی (D27).
    "android.permission.READ_PHONE_STATE",
    // مجوز امضایی خودِ اپ که androidx.core به manifest اضافه می‌کند، برای
    // گیرنده‌های ثبت‌شده در زمان اجرا. چیزی بیرون از اپ به آن دسترسی ندارد.
    "ir.asudehapp.sms.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
)

androidComponents {
    onVariants { variant ->
        val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val checkTask = tasks.register(
            "check${variant.name.replaceFirstChar(Char::uppercase)}Permissions",
        ) {
            group = "verification"
            description = "بررسی نبود مجوز INTERNET در manifest ادغام‌شده (ADR-0002)"
            inputs.file(mergedManifest)
            doLast {
                val text = mergedManifest.get().asFile.readText()
                val declared = Regex("""uses-permission[^>]*android:name="([^"]+)"""")
                    .findAll(text)
                    .map { it.groupValues[1] }
                    .toSortedSet()

                val problems = mutableListOf<String>()
                if ("android.permission.INTERNET" in declared) {
                    problems += "manifest ادغام‌شده مجوز INTERNET دارد. " +
                        "وعدهٔ NoNet (ADR-0002) شکسته می‌شود."
                }
                val unexpected = declared - allowedPermissions
                if (unexpected.isNotEmpty()) {
                    problems += "مجوزهای تأییدنشده در manifest ادغام‌شده: " +
                        unexpected.joinToString()
                }
                if (problems.isNotEmpty()) {
                    throw GradleException(problems.joinToString(separator = "\n"))
                }
                logger.lifecycle(
                    "NoNet: مجوز INTERNET وجود ندارد. " +
                        "مجوزهای اعلام‌شده: ${declared.joinToString()}",
                )
            }
        }
        tasks.named("check").configure { dependsOn(checkTask) }
    }
}
