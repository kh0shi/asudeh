# ساخت آسوده

## پیش‌نیازها

- JDK 17
- Android SDK با `compileSdk 35` (متغیر `ANDROID_HOME` یا فایل `local.properties`)

Gradle لازم نیست نصب باشد؛ wrapper آن را خودش می‌آورد.

## دستورها

```bash
# آزمون‌های ماژول‌های خالص Kotlin: مدل، فارسی و طبقه‌بند
./gradlew :core:model:test :core:persian:test :core:classifier:test

# ساخت اپ
./gradlew :app:assembleDebug

# ADR-0002: بررسی نبود مجوز INTERNET روی manifest ادغام‌شده
./gradlew :app:checkDebugPermissions
```

ماژول‌های `:core:model`، `:core:persian` و `:core:classifier` خالص Kotlin هستند و
**بدون Android SDK** هم ساخته و آزموده می‌شوند. برای همین، Android Gradle Plugin
عمداً روی classpath پروژهٔ ریشه نیست و هر ماژول اندرویدی خودش آن را اعلام می‌کند.

## ماژول‌ها

| ماژول | چیست |
|---|---|
| `:core:model` | `Category`، `Folder`، `Verdict`، `Placement`؛ بدون وابستگی به اندروید |
| `:core:persian` | نرمال‌سازی متن فارسی و تقویم شمسی |
| `:core:classifier` | `Classifier`، `Router`، `LinkGuard`، `RulePack`، `ReceivePipeline` |
| `:core:data` | Room به‌عنوان ایندکس محلی، و خواندن و نوشتن Telephony Provider |
| `:core:telephony` | گیرندهٔ `SMS_DELIVER`، ارسال، اعلان‌ها |
| `:core:ui` | تم و رنگ برند |
| `:app` | رابط کاربری Compose و اجزای اپ پیش‌فرض پیامک |

## فایل قواعد

`RulePack` در `core/classifier/src/main/resources/ir/asudehapp/sms/classifier/rulepack.json`
است و نسخه‌اش (`1405.06`) جدا از نسخهٔ اپ است (D66). انتشار ماهانهٔ فقط-داده تنها
همین فایل را عوض می‌کند.
