// اینجا عمداً هیچ پلاگینی اعلام نمی‌شود.
//
// اگر پلاگین Kotlin روی classpath ریشه بیاید ولی Android Gradle Plugin نیاید،
// `org.jetbrains.kotlin.android` در ماژول‌های اندرویدی نمی‌تواند کلاس‌های AGP را
// ببیند و با خطای `Could not generate a decorated class for KotlinAndroidTarget`
// شکست می‌خورد. پس هر ماژول پلاگین‌های خودش را اعلام می‌کند و ماژول‌های خالص
// Kotlin بدون Android SDK هم ساخته و آزموده می‌شوند.
