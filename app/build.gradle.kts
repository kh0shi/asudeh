import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ir.asudehapp.sms"
    compileSdk = 35

    defaultConfig {
        // ADR-0001: شناسهٔ پکیج بعد از اولین انتشار قابل تغییر نیست.
        applicationId = "ir.asudehapp.sms"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
)

abstract class CheckManifestPermissions : DefaultTask() {

    @get:org.gradle.api.tasks.InputFile
    abstract val mergedManifest: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.Input
    abstract val allowed: org.gradle.api.provider.SetProperty<String>

    @org.gradle.api.tasks.TaskAction
    fun check() {
        val text = mergedManifest.get().asFile.readText()
        val declared = Regex("""uses-permission[^>]*android:name="([^"]+)"""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toSortedSet()

        val problems = mutableListOf<String>()
        if ("android.permission.INTERNET" in declared) {
            problems += "manifest ادغام‌شده مجوز INTERNET دارد. وعدهٔ NoNet (ADR-0002) شکسته می‌شود."
        }
        val unexpected = declared - allowed.get()
        if (unexpected.isNotEmpty()) {
            problems += "مجوزهای تأییدنشده در manifest ادغام‌شده: ${unexpected.joinToString()}"
        }
        if (problems.isNotEmpty()) {
            throw GradleException(problems.joinToString(separator = "\n"))
        }
        logger.lifecycle("NoNet: مجوز INTERNET وجود ندارد. مجوزهای اعلام‌شده: ${declared.joinToString()}")
    }
}

androidComponents {
    onVariants { variant ->
        val task = tasks.register(
            "check${variant.name.replaceFirstChar(Char::uppercase)}Permissions",
            CheckManifestPermissions::class.java,
        ) {
            group = "verification"
            description = "بررسی نبود مجوز INTERNET در manifest ادغام‌شده (ADR-0002)"
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            allowed.set(allowedPermissions)
        }
        tasks.named("check").configure { dependsOn(task) }
    }
}
