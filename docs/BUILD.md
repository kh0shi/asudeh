# ساخت آسوده

## پیش‌نیازها

- JDK 17 تا 21، **با `javac`** (نه فقط JRE). Gradle 8.11 با JDK 25 اجرا نمی‌شود؛
  اگر JDK پیش‌فرض سیستم مناسب نیست، `JAVA_HOME` را روی یک JDK 17 یا 21 بگذارید.
- Android SDK با `compileSdk 35` (متغیر `ANDROID_HOME` یا فایل `local.properties`)

Gradle لازم نیست نصب باشد؛ wrapper آن را خودش می‌آورد.

## دستورها

```bash
# آزمون‌های ماژول‌های خالص Kotlin: مدل، فارسی، طبقه‌بند و PDU پیام چندرسانه‌ای
./gradlew :core:model:test :core:persian:test :core:classifier:test :core:mms:test

# آزمون‌های واحد لایهٔ داده و تلفن (به Android SDK نیاز دارد)
./gradlew :core:data:testDebugUnitTest :core:telephony:testDebugUnitTest

# ساخت اپ
./gradlew :app:assembleDebug

# ADR-0002: بررسی نبود مجوز INTERNET روی manifest ادغام‌شده
./gradlew :app:checkDebugPermissions :app:checkReleasePermissions
```

ماژول‌های `:core:model`، `:core:persian`، `:core:classifier` و `:core:mms` خالص JVM هستند و
**بدون Android SDK** هم ساخته و آزموده می‌شوند. برای همین، Android Gradle Plugin
عمداً روی classpath پروژهٔ ریشه نیست و هر ماژول اندرویدی خودش آن را اعلام می‌کند.

## ماژول‌ها

| ماژول | چیست |
|---|---|
| `:core:model` | `Category`، `Folder`، `Verdict`، `Placement`؛ بدون وابستگی به اندروید |
| `:core:persian` | نرمال‌سازی متن فارسی و تقویم شمسی |
| `:core:classifier` | `Classifier`، `Router`، `LinkGuard`، `RulePack`، `ReceivePipeline` |
| `:core:mms` | خواندن و ساختن PDU پیام چندرسانه‌ای (کد AOSP، Apache 2.0؛ `core/mms/NOTICE.md`) |
| `:core:data` | Room به‌عنوان ایندکس محلی، خواندن و نوشتن Telephony Provider (پیامک و MMS)، تنظیمات و پشتیبان |
| `:core:telephony` | گیرنده‌های `SMS_DELIVER` و `WAP_PUSH_DELIVER`، ارسال پیامک و MMS، اعلان‌ها و `Digest` |
| `:core:ui` | تم و رنگ برند |
| `:app` | رابط کاربری Compose و اجزای اپ پیش‌فرض پیامک |

## فایل قواعد

`RulePack` در `core/classifier/src/main/resources/ir/asudehapp/sms/classifier/rulepack.json`
است و نسخه‌اش (`1405.06`) جدا از نسخهٔ اپ است (D66). انتشار ماهانهٔ فقط-داده تنها
همین فایل را عوض می‌کند.

## پایگاه داده

schema هر دو پایگاه داده (`IndexDatabase` و `RulesDatabase`) در
`core/data/schemas/` ساخته و در مخزن نگه داشته می‌شود. هیچ migration مخربی
نداریم: هر تغییر schema یک `Migration` و آزمون خودش را می‌خواهد
(`IndexMigrationsTest` هر دستور migration را با schema خروجی Room مقایسه می‌کند).
`IndexDatabase` الان نسخهٔ ۲ است.
