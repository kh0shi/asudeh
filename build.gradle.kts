// اینجا عمداً هیچ پلاگینی اعلام نمی‌شود.
//
// اگر پلاگین Kotlin روی classpath ریشه بیاید ولی Android Gradle Plugin نیاید،
// `org.jetbrains.kotlin.android` در ماژول‌های اندرویدی نمی‌تواند کلاس‌های AGP را
// ببیند و با خطای `Could not generate a decorated class for KotlinAndroidTarget`
// شکست می‌خورد. پس هر ماژول پلاگین‌های خودش را اعلام می‌کند و ماژول‌های خالص
// Kotlin بدون Android SDK هم ساخته و آزموده می‌شوند.

// D62: detekt روی همهٔ کد Kotlin. با خط فرمان detekt اجرا می‌شود، نه پلاگین
// Gradle، به همان دلیل بالا: هیچ پلاگینی روی classpath ریشه نمی‌آید. نیازی به
// Android SDK هم ندارد، پس در job «core» اجرا می‌شود. تخلف‌های کد موجود در
// config/detekt/baseline.xml ثبت شده‌اند و فقط کد تازه سنجیده می‌شود؛ برای
// ساختن دوبارهٔ baseline: ./gradlew detektBaseline
val detektCli: Configuration by configurations.creating

dependencies {
    detektCli(libs.detekt.cli)
}

val detektSources: List<File> = listOf(
    "app", "core/model", "core/persian", "core/classifier", "core/data",
    "core/telephony", "core/mms", "core/ui", "benchmark", "baselineprofile",
).flatMap { module ->
    listOf("main", "test", "androidTest").map { file("$module/src/$it/kotlin") }
}.filter { it.isDirectory }

fun JavaExec.detektArgs(vararg extra: String) {
    group = "verification"
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    classpath = detektCli
    inputs.files(detektSources)
    inputs.file("config/detekt/detekt.yml")
    args(
        "--input", detektSources.joinToString(",") { it.path },
        "--config", file("config/detekt/detekt.yml").path,
        "--build-upon-default-config",
        "--baseline", file("config/detekt/baseline.xml").path,
        "--parallel",
        *extra,
    )
}

tasks.register<JavaExec>("detekt") {
    description = "بررسی کد Kotlin با detekt، در برابر baseline (D62)."
    detektArgs()
}

tasks.register<JavaExec>("detektBaseline") {
    description = "ساختن دوبارهٔ config/detekt/baseline.xml از کد فعلی."
    detektArgs("--create-baseline")
}
