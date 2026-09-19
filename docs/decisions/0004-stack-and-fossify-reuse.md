# ADR-0004: Kotlin + Compose از صفر، با استفاده از لایهٔ تلفنی Fossify؛ مجوز GPL-3.0

- وضعیت: پذیرفته‌شده. بند `mmslib` با ADR-0009 جایگزین شده است.

## زمینه
هیچ پروژهٔ متن‌باز موجودی به‌تنهایی مناسب نیست. Fossify Messages لایهٔ پیامک و MMS آزموده و بدون مجوز INTERNET دارد، ولی رابطش View/XML است. QUIK معماری منسوخ دارد (Realm، RxJava) و مجوز INTERNET دارد. SpamBlocker (MIT) موتور فیلتر خوبی دارد ولی اپ پیش‌فرض نیست.

## تصمیم
- رابط کاربری با Kotlin، Jetpack Compose و Material 3، از صفر.
- لایهٔ تلفنی (ارسال، دریافت، MMS، thread، پشتیبان) از Fossify Messages برداشته می‌شود و وابستگی‌اش به fossify-commons با کد خودمان جایگزین می‌شود. `org.fossify:mmslib` به‌عنوان وابستگی مستقیم استفاده می‌شود (مجوزش پیش از استفاده بررسی شود).
- ایدهٔ موتور قواعد و Naive Bayes از SpamBlocker (MIT) گرفته می‌شود.
- مجوز پروژه: **GPL-3.0**. نام پدیدآورندگان اصلی در فایل‌های برداشته‌شده حفظ می‌شود.
- minSdk 26.
- ماژول‌ها: `:app`، `:core:telephony`، `:core:data`، `:core:classifier` (Kotlin خالص)، `:core:persian`.

## پیامدها
- هر fork از آسوده هم باید GPL بماند.
- `SmsReceiver`، اعلان‌ها و پایگاه داده بازنویسی می‌شوند (به ADR-0003 نگاه کنید).
