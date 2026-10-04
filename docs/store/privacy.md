# سیاست حریم خصوصی آسوده

> **پیش‌نویس (ROADMAP A4).** پیش از انتشار، صاحب پروژه آن را می‌خواند، نشانی تماس را
> کامل می‌کند و در یک نشانی عمومی می‌گذارد (⚑ U4). هر جمله باید با کد یا یک تصمیم
> مستند بخواند؛ منبع هر بند در پرانتز آمده است.

آخرین به‌روزرسانی: [تاریخ انتشار]

## خلاصه

آسوده هیچ داده‌ای از گوشی شما بیرون نمی‌فرستد. اپ اصلاً اجازهٔ دسترسی به اینترنت
ندارد، پس نمی‌تواند چیزی را به سرور ما یا هیچ‌جای دیگر بفرستد. ما سروری نداریم و
هیچ داده‌ای، حتی آمار ناشناس، جمع نمی‌کنیم.

## اینترنت

- آسوده مجوز `INTERNET` اندروید را ندارد. این را هر کسی می‌تواند در فهرست
  مجوزهای اپ در تنظیمات گوشی ببیند. (ADR-0002)
- در هر تغییر کد، یک بررسی خودکار تأیید می‌کند که هیچ کتابخانه‌ای هم این مجوز را
  بی‌صدا اضافه نکرده باشد. (ADR-0002، D62)
- کد آسوده متن‌باز است (GPL-3.0) و هر کسی می‌تواند این ادعاها را بررسی کند.
- **پیام چندرسانه‌ای (MMS):** دریافت و ارسال MMS را خود سیستم اندروید از راه
  اپراتور تلفن همراه شما انجام می‌دهد، همان‌طور که برای هر اپ پیامک دیگری انجام
  می‌دهد. آسوده فقط پیام را به سیستم می‌دهد یا از آن می‌گیرد و خودش به شبکه وصل
  نمی‌شود. (ADR-0009)

## پیامک‌های شما کجا می‌مانند

- پیامک‌ها در حافظهٔ پیامک خود اندروید (Telephony Provider) ذخیره می‌شوند، همان
  جایی که هر اپ پیامک پیش‌فرض استفاده می‌کند. (D19)
- آسوده یک فهرست جست‌وجو و پوشه‌بندی روی خود گوشی نگه می‌دارد و قواعد شما (شماره‌ها
  و واژه‌هایی که مسدود یا مجاز کرده‌اید) را هم همان‌جا ذخیره می‌کند. (D19، ADR-0012)
- تشخیص تبلیغ و کلاهبرداری کاملاً روی گوشی انجام می‌شود. هیچ پیامکی برای
  تشخیص به جایی فرستاده نمی‌شود. (D32، ADR-0002)
- آسوده هرگز به ابتکار خودش پیامکی را پاک نمی‌کند. پیامکی که شما پاک کنید اول
  به «حذف‌شده‌ها» می‌رود و فقط خودتان آنجا را خالی می‌کنید. (ADR-0003، ADR-0010)

## پشتیبان

- **پشتیبان ابری اندروید (Google Drive) برای آسوده خاموش است**، تا پیامک‌ها و
  قواعد شما روی سرور کسی نروند. (D57)
- در انتقال مستقیم از گوشی قدیم به گوشی تازه، فقط قواعد و تنظیمات شما منتقل
  می‌شوند. خود پیامک‌ها را اندروید جداگانه منتقل می‌کند. (D57)
- «پشتیبان فایل» در تنظیمات، پیامک‌ها را در فایلی می‌نویسد که **خودتان** جایش را
  انتخاب می‌کنید. آسوده آن فایل را جای دیگری نمی‌فرستد. (D57)

## گزارش خطا

اگر آسوده بسته شود، شرح فنی خطا روی گوشی ذخیره می‌شود. **در این گزارش هیچ متن
پیامک یا شماره‌ای نیست**؛ فقط نسخهٔ اپ، نسخهٔ اندروید، مدل گوشی و جای خطا در کد.
در اجرای بعد، آسوده می‌پرسد که آیا می‌خواهید آن را بفرستید. فقط اگر خودتان
بخواهید، گزارش کامل را می‌بینید و با منوی «اشتراک‌گذاری» اندروید، با اپی که
خودتان انتخاب می‌کنید (مثلاً ایمیل)، می‌فرستید. (D58)

## مجوزها و دلیل هرکدام

آسوده فقط همین مجوزها را دارد و فهرستشان در کد ثابت است؛ هر مجوز تازه به یک
تصمیم مستند نیاز دارد. (D62)

| مجوز | برای چه |
|---|---|
| دریافت، خواندن و ارسال پیامک (`RECEIVE_SMS`، `READ_SMS`، `SEND_SMS`) | کار اصلی یک اپ پیامک |
| دریافت MMS و WAP Push (`RECEIVE_MMS`، `RECEIVE_WAP_PUSH`) | دریافت پیام چندرسانه‌ای |
| خواندن مخاطب‌ها (`READ_CONTACTS`) | نشان دادن نام به‌جای شماره، و اینکه پیامک یک مخاطب هرگز خودکار به‌عنوان تبلیغ پنهان نشود (D31) |
| اعلان (`POST_NOTIFICATIONS`) | خبر دادن پیامک تازه و خلاصهٔ پیامک‌های پنهان‌شده |
| اجرا پس از روشن شدن گوشی (`RECEIVE_BOOT_COMPLETED`) | زمان‌بندی دوبارهٔ «خلاصه» و ارسال‌های زمان‌بندی‌شده (D40، ADR-0011) |
| وضعیت تلفن (`READ_PHONE_STATE`)، **اختیاری** | فقط وقتی سیم ارسال را عوض می‌کنید خواسته می‌شود: فهرست سیم‌کارت‌ها و تشخیص شمارهٔ خودتان در پیام گروهی (D27) |

مجوز اینترنت، مکان، دوربین، میکروفون، تماس و حساب‌های کاربری را ندارد.

## کودکان

آسوده هیچ داده‌ای از هیچ کاربری، از جمله کودکان، جمع نمی‌کند.

## تغییر این سیاست

اگر این سیاست عوض شود، نسخهٔ تازه در همین نشانی و در یادداشت انتشار آن نسخهٔ اپ
می‌آید. تعهد «هیچ داده‌ای از گوشی خارج نمی‌شود» بخشی از هویت آسوده است و تغییر
نمی‌کند. (مانیفست، اصل ۶)

## تماس

[نشانی ایمیل یا صفحهٔ تماس، پیش از انتشار]

---

# Asudeh privacy policy

> **Draft (ROADMAP A4).** Before release the owner reviews it, fills in the contact
> address and publishes it at a public URL (⚑ U4).

Last updated: [release date]

## Summary

Asudeh sends no data off your phone. The app does not have permission to use the
internet at all, so it cannot send anything to us or anyone else. We have no server
and collect no data, not even anonymous statistics.

## Internet

- Asudeh does not have Android's `INTERNET` permission. Anyone can confirm this in
  the app's permission list in phone settings. (ADR-0002)
- Every code change is checked automatically to make sure no library quietly adds
  that permission. (ADR-0002, D62)
- Asudeh is open source (GPL-3.0), so anyone can verify these claims.
- **Multimedia messages (MMS):** Android itself downloads and sends MMS through your
  mobile carrier, as it does for any SMS app. Asudeh only hands the message to the
  system or receives it from the system; it never connects to the network itself.
  (ADR-0009)

## Where your messages live

- Messages are stored in Android's own message storage (Telephony Provider), the
  same place every default SMS app uses. (D19)
- Asudeh keeps a search and folder index on the phone, along with your rules (the
  numbers and words you have blocked or allowed). (D19, ADR-0012)
- Ad and scam detection runs entirely on the phone. No message is sent anywhere to
  be checked. (D32, ADR-0002)
- Asudeh never deletes a message on its own. A message you delete goes to "Deleted"
  first, and only you can empty it. (ADR-0003, ADR-0010)

## Backups

- **Android cloud backup (Google Drive) is turned off for Asudeh**, so your messages
  and rules never end up on anyone's server. (D57)
- In a direct phone-to-phone transfer, only your rules and settings move. Android
  transfers the messages themselves separately. (D57)
- "File backup" in settings writes your messages to a file in a location **you**
  choose. Asudeh does not send that file anywhere. (D57)

## Crash reports

If Asudeh crashes, a technical description of the error is saved on the phone. **It
contains no message text and no phone numbers**: only the app version, Android
version, phone model and where in the code the error happened. The next time you
open the app, Asudeh asks whether you want to send it. Only if you choose to, you see
the full report and send it through Android's Share menu, with an app you pick (for
example email). (D58)

## Permissions and why

Asudeh has only these permissions, and the list is fixed in the code; any new
permission needs a documented decision. (D62)

| Permission | Why |
|---|---|
| Receive, read and send SMS (`RECEIVE_SMS`, `READ_SMS`, `SEND_SMS`) | The core job of an SMS app |
| Receive MMS and WAP Push (`RECEIVE_MMS`, `RECEIVE_WAP_PUSH`) | Receiving multimedia messages |
| Read contacts (`READ_CONTACTS`) | Showing names instead of numbers, and never auto-hiding a contact's message as an ad (D31) |
| Notifications (`POST_NOTIFICATIONS`) | New-message alerts and the digest of hidden messages |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Rescheduling the digest and scheduled messages after a restart (D40, ADR-0011) |
| Phone state (`READ_PHONE_STATE`), **optional** | Asked only when you change the sending SIM: listing SIM cards and recognizing your own number in group messages (D27) |

It has no internet, location, camera, microphone, call or account permissions.

## Children

Asudeh collects no data from any user, including children.

## Changes

If this policy changes, the new version will appear at this address and in that
release's notes. "No data leaves the phone" is part of what Asudeh is and will not
change. (Manifesto, principle 6)

## Contact

[Email address or contact page, before release]
