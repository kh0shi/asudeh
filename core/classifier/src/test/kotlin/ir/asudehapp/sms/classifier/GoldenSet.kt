package ir.asudehapp.sms.classifier

import ir.asudehapp.sms.model.Category

/**
 * پیکرهٔ آزمون عمومی برای معیارهای پذیرش D35 (`GoldenSetTest`).
 *
 * مثل `ClassifierTest`، همهٔ پیامک‌های این فایل دست‌نویس‌اند و از پیامک واقعی
 * کسی برداشته نشده‌اند (D34). پیکرهٔ کامل و واقعی در یک مخزن خصوصی جداگانه
 * نگه داشته می‌شود؛ این فقط یک زیرمجموعهٔ کوچکِ ساختگی و قابل بازبینی است که
 * مرزهای تصمیم طبقه‌بند را — با نام‌های بانک، کدهای تایید، سرشماره و متن
 * تبلیغاتی همگی ساختگی — تمرین می‌کند.
 *
 * هر ردیف یک پیامک برچسب‌خورده است. `note` برای بازبینی انسانی است: چرا این
 * نمونه انتخاب شده، کدام قاعده را تمرین می‌کند.
 */
data class GoldenExample(
    val address: String,
    val body: String,
    val isKnownContact: Boolean = false,
    val expectedCategory: Category,
    val note: String = "",
)

object GoldenSet {

    // --- Personal: پیامک آدم واقعی. هرگز نباید Promo یا Phishing شمرده شود. ---
    private val personal = listOf(
        GoldenExample("09121234567", "سلام خوبی؟ امشب میای بیرون؟", expectedCategory = Category.PERSONAL),
        GoldenExample("09351112233", "رسیدم خونه، مواظب خودت باش", expectedCategory = Category.PERSONAL),
        GoldenExample("09193334455", "فردا ساعت ۹ دفتر منتظرتم", expectedCategory = Category.PERSONAL),
        GoldenExample(
            "4040", "قرار فردا سر جاشه", isKnownContact = true, expectedCategory = Category.PERSONAL,
            note = "مخاطب ذخیره‌شده از سرشمارهٔ کوتاه هم شخصی است",
        ),
        GoldenExample("09121112233", "بچه‌ها امشب جمع میشیم خونه من", expectedCategory = Category.PERSONAL),
        GoldenExample("09122223344", "کتابو گرفتم، فردا میارم دانشگاه", expectedCategory = Category.PERSONAL),
        GoldenExample("09354445566", "مامان گفت شام بیا خونه", expectedCategory = Category.PERSONAL),
        GoldenExample(
            "09195556677", "لینک عکسای مسافرت رو گذاشتم https://t.me/album",
            expectedCategory = Category.PERSONAL, note = "لینک شناخته‌شده، نباید Suspect شود",
        ),
        GoldenExample("09126667788", "بلیط قطار پنجشنبه رو گرفتم، ساعت هفت صبح حرکت می‌کنیم", expectedCategory = Category.PERSONAL),
        GoldenExample("09127778899", "دیشب فیلمی که گفتی رو دیدم، عالی بود", expectedCategory = Category.PERSONAL),
        GoldenExample("09128889900", "یادت نره فردا جلسه داریم", expectedCategory = Category.PERSONAL),
        GoldenExample("09129990011", "تولدت مبارک عزیزم", expectedCategory = Category.PERSONAL),
        GoldenExample("09121112200", "امتحانت چطور بود؟", expectedCategory = Category.PERSONAL),
        GoldenExample("09122223300", "بارون شدید میاد، مواظب رانندگیت باش", expectedCategory = Category.PERSONAL),
        GoldenExample("09123334400", "عکس رو برات فرستادم، دیدی؟", expectedCategory = Category.PERSONAL),
        GoldenExample("09124445500", "فردا میریم کوه، ساعت ۶ آماده باش", expectedCategory = Category.PERSONAL),
        GoldenExample(
            "09125556600", "اینو ببین https://maps.app.goo.gl/qfQyxx25ug1hnsUA6",
            expectedCategory = Category.PERSONAL, note = "نقشهٔ گوگل میزبان شناخته‌شده است",
        ),
        GoldenExample(
            "09121234567", "بانک ملی رو دیدی؟ این لینکش bmi.ir", isKnownContact = true,
            expectedCategory = Category.PERSONAL,
            note = "مخاطب لینک رسمی بانک را فوروارد کرده؛ نباید جعل سرشماره شمرده شود",
        ),
    )

    // --- OTP: رمز یکبار مصرف. باید همیشه در صندوق بماند. ---
    private val otp = listOf(
        GoldenExample("10001", "رمز یکبار مصرف شما ۴۵۸۲۱ است. بانک ملت", expectedCategory = Category.OTP),
        GoldenExample("30001234", "کد تایید شما: ۹۹۱۲۳ - فروشگاه", expectedCategory = Category.OTP),
        GoldenExample("200011", "کد ورود به حساب کاربری شما: 3391", expectedCategory = Category.OTP),
        GoldenExample("9990", "کد فعال سازی: 758214", expectedCategory = Category.OTP),
        GoldenExample("7575", "کد احراز هویت شما ۶۶۲۱ است", expectedCategory = Category.OTP),
        GoldenExample("10001100", "رمز پویا: ۱۲۳۴۵۶ - بانک ملی", expectedCategory = Category.OTP),
        GoldenExample("My_Irancell", "کد ورود به حساب کاربری ایرانسل من: 2575", expectedCategory = Category.OTP),
        GoldenExample("BaIrancell", "کد تایید شما ۴۸۲۱۳ است", expectedCategory = Category.OTP, note = "رمز یکبار حتی از سرشمارهٔ تبلیغاتی برنده می‌شود"),
        GoldenExample(
            "09999920000", "بانک سامان\nخريد\nپاسچی\nمبلغ 592,250 ريال\nرمز 081771",
            expectedCategory = Category.OTP, note = "رمز پویا بدون کلیدواژه، فقط از شکل کد",
        ),
        GoldenExample("20002030", "کد تأیید شماره موبایل شما: 4471", expectedCategory = Category.OTP),
        GoldenExample("SnappPay", "کد ورود اسنپ‌پی: 9981", expectedCategory = Category.OTP),
        GoldenExample("10001", "verification code: 552134", expectedCategory = Category.OTP),
        GoldenExample("5000123", "one time password: 774821", expectedCategory = Category.OTP),
        GoldenExample("9990", "کد فعالسازی سیمکارت: 118820", expectedCategory = Category.OTP),
        GoldenExample("200020", "رمز عبور موقت شما ۵۵۱۲۳ است", expectedCategory = Category.OTP),
        GoldenExample(
            "10001", "کد تایید شما 48213 است. برای دریافت جایزه وارد bit.ly/abc شوید",
            expectedCategory = Category.OTP, note = "طعمهٔ کلاهبرداری کنار رمز؛ باید Suspect شود ولی دسته همان OTP بماند",
        ),
        GoldenExample("10001", "OTP: 552413 برای ورود به حساب کاربری", expectedCategory = Category.OTP),
        GoldenExample("50004321", "کد ورود: 6321", expectedCategory = Category.OTP),
    )

    // --- Bank: تراکنش بانکی. باید همیشه در صندوق بماند. ---
    private val bank = listOf(
        GoldenExample("200011", "بانک ملی\nبرداشت ۱٬۵۰۰٬۰۰۰ ریال\nمانده: ۱۲٬۳۰۰٬۰۰۰ ریال", expectedCategory = Category.BANK),
        GoldenExample("2000", "بانک ملت\nواریز ۲٬۰۰۰٬۰۰۰ ریال\nمانده حساب: ۵٬۰۰۰٬۰۰۰ ریال", expectedCategory = Category.BANK),
        GoldenExample("20002030", "بانک صادرات\nانتقال وجه ۵۰۰٬۰۰۰ تومان\nمانده: ۳٬۲۰۰٬۰۰۰ تومان", expectedCategory = Category.BANK),
        GoldenExample("100020", "کارت به کارت ۱٬۰۰۰٬۰۰۰ ریال انجام شد. مانده: ۸٬۰۰۰٬۰۰۰ ریال", expectedCategory = Category.BANK),
        GoldenExample("200011", "تراکنش خرید ۳۵۰٬۰۰۰ ریال از کارت شما. مانده: ۱٬۱۰۰٬۰۰۰ ریال", expectedCategory = Category.BANK),
        GoldenExample("2000", "برداشت ۸۰۰٬۰۰۰ ریال از خودپرداز. موجودی: ۲٬۵۰۰٬۰۰۰ ریال", expectedCategory = Category.BANK),
        GoldenExample(
            "200011", "برداشت 1,500,000 ریال از حساب شما. برای لغو کلیک کنید bit.ly/x1",
            expectedCategory = Category.BANK, note = "لینک طعمه؛ باید Suspect شود ولی دسته بانکی بماند",
        ),
        GoldenExample("10001100", "واریز حقوق ۱۵٬۰۰۰٬۰۰۰ ریال به حسابتان انجام شد. مانده: ۱۸٬۰۰۰٬۰۰۰ ریال", expectedCategory = Category.BANK),
        GoldenExample("20002030", "صورتحساب کارت اعتباری شما صادر شد. سررسید: ۱۰ روز دیگر", expectedCategory = Category.BANK),
        GoldenExample(
            "2000", "واریز ۵,۰۰۰,۰۰۰ ریال به حساب شما. جشنواره تخفیف ویژه!",
            expectedCategory = Category.BANK, note = "بانکی که تبلیغ هم می‌کند؛ نباید اطمینان بالا بگیرد",
        ),
        GoldenExample("200011", "اقساط وام مسکن شما امروز سررسید شد. مانده بدهی: ۲۰٬۰۰۰٬۰۰۰ تومان", expectedCategory = Category.BANK),
        GoldenExample("100020030", "چک شما به مبلغ ۱٬۰۰۰٬۰۰۰ تومان نقد شد", expectedCategory = Category.BANK),
        GoldenExample("2000", "کارت به کارت ناموفق. موجودی کافی نیست", expectedCategory = Category.BANK),
        GoldenExample("200011", "بانک ملی\nواریز ۹۹۹٬۰۰۰ ریال\nمانده: ۴٬۴۴۴٬۰۰۰ ریال", expectedCategory = Category.BANK),
        GoldenExample("20002030", "برداشت از حساب شما ۲۰۰٬۰۰۰ تومان توسط پایانه فروش", expectedCategory = Category.BANK),
        GoldenExample("100020", "تراکنش ناموفق کارت شما. موجودی: صفر", expectedCategory = Category.BANK),
        GoldenExample(
            "200011", "بانک ملی: صورتحساب خود را در bmi.ir/sms مشاهده کنید",
            expectedCategory = Category.BANK,
            note = "نام بانک + دامنهٔ رسمی خودش؛ نباید فیشینگ شمرده شود",
        ),
        GoldenExample("10001100", "انتقال وجه ۱٬۲۰۰٬۰۰۰ ریال به شماره حساب ۱۲۳۴۵۶ انجام شد", expectedCategory = Category.BANK),
    )

    // --- Service: خدماتی (مرسوله، قبض، نوبت…). باید همیشه در صندوق بماند. ---
    private val service = listOf(
        GoldenExample("200030", "مرسوله شما با کد رهگیری ۱۲۳۴۵۶۷۸ ارسال شد", expectedCategory = Category.SERVICE),
        GoldenExample("3000", "قبض آب شما صادر شد. سررسید قبض: ۱۵ ام", expectedCategory = Category.SERVICE),
        GoldenExample("9990", "نوبت شما نزدیک است، لطفا آماده باشید", expectedCategory = Category.SERVICE),
        GoldenExample("200110", "خلافی جدید برای خودرو شما ثبت شد", expectedCategory = Category.SERVICE),
        GoldenExample("200020", "استعلام شما با موفقیت انجام شد", expectedCategory = Category.SERVICE),
        GoldenExample("3000", "ثبت نام شما در سامانه تکمیل شد", expectedCategory = Category.SERVICE),
        GoldenExample("200030", "سفارش شما ثبت شد و به زودی ارسال می‌شود", expectedCategory = Category.SERVICE),
        GoldenExample("3000", "بیمه نامه شما تمدید شد", expectedCategory = Category.SERVICE),
        GoldenExample("200030", "مرسوله پستی شما به مقصد رسید", expectedCategory = Category.SERVICE),
        GoldenExample("9990", "قبض موبایل شما صادر شد", expectedCategory = Category.SERVICE),
        GoldenExample("200110", "نتیجه استعلام خلافی: بدون خلافی", expectedCategory = Category.SERVICE),
        GoldenExample("3000", "کد پیگیری سفارش شما: ۹۹۸۸۷۷۶۶", expectedCategory = Category.SERVICE),
        GoldenExample("200030", "بسته شما ارسال شد، کد رهگیری: TRK12345", expectedCategory = Category.SERVICE),
        GoldenExample("9990", "نوبت شما در صف پشتیبانی نزدیک است", expectedCategory = Category.SERVICE),
        GoldenExample("3000", "ثبت نام شما در آزمون تایید شد", expectedCategory = Category.SERVICE),
        GoldenExample(
            "200030", "پست ایران: مرسوله شما را در tracking.post.ir پیگیری کنید",
            expectedCategory = Category.SERVICE,
            note = "زیردامنهٔ دامنهٔ رسمی؛ نباید فیشینگ شمرده شود",
        ),
        GoldenExample("3000", "بیمه نامه شخص ثالث شما تا پایان ماه معتبر است", expectedCategory = Category.SERVICE),
        GoldenExample("200110", "خلافی خودرو با پلاک شما ثبت شده است", expectedCategory = Category.SERVICE),
    )

    // --- Promo: تبلیغاتی. باید بازیابی بیش از ۸۵٪ داشته باشد. ---
    private val promo = listOf(
        GoldenExample("30001234", "جشنواره فروش ویژه! تا ۵۰٪ تخفیف روی همه محصولات. لغو۱۱", expectedCategory = Category.PROMO),
        GoldenExample("20002030", "وام فوری بدون ضامن با تخفیف ویژه! همین حالا تماس بگیرید", expectedCategory = Category.PROMO, note = "تبلیغ وام از سرشمارهٔ بانکی، بانکی نیست"),
        GoldenExample("BaIrancell", "سلام! خبر تازه از ما.", expectedCategory = Category.PROMO, note = "سرشمارهٔ صرفاً تبلیغاتی؛ بدون کلیدواژه هم تبلیغ است"),
        GoldenExample("IrancelleTo", "شارژ کن، جایزه ببر! با هر شارژ یک امتیاز در قرعه‌کشی", expectedCategory = Category.PROMO),
        GoldenExample("50004321", "خرید کن و از ارسال رایگان لذت ببر، فرصت محدود", expectedCategory = Category.PROMO),
        GoldenExample("30001234", "حراج بزرگ فروشگاه ما، تخفیف تا ۷۰٪", expectedCategory = Category.PROMO),
        GoldenExample(
            "09121234567", "سلام، فردا میای؟ یه کد تخفیف برات دارم",
            expectedCategory = Category.PROMO, note = "کلیدواژهٔ تبلیغ از خط شخصی؛ نباید اطمینان بالا بگیرد",
        ),
        GoldenExample("30001234", "تخفیف ۱۶۰ هزار تومانی اُکالا\nکد: QXB8C7\nتا ۴ روز", expectedCategory = Category.PROMO),
        GoldenExample("30001234", "جشنواره فروش! کالای کوچک فقط 250,000 تومان، 3 روز باقی مانده", expectedCategory = Category.PROMO),
        GoldenExample("30001234", "فروش اقساطی بدون ضامن، قیمت 12,000,000 تومان", expectedCategory = Category.PROMO),
        GoldenExample("50001234", "پیشنهاد شگفت انگیز! فقط امروز، همین حالا خرید کنید", expectedCategory = Category.PROMO),
        GoldenExample("20001234", "هدیه ویژه برای مشتریان قدیمی، با ما تماس بگیرید", expectedCategory = Category.PROMO),
        GoldenExample("30005678", "بلیت رایگان کنسرت برای ۱۰۰ نفر اول، شرکت کنید", expectedCategory = Category.PROMO),
        GoldenExample("5000123", "۳۰٪ تخفیف ویژه اعضای باشگاه مشتریان", expectedCategory = Category.PROMO),
        GoldenExample("30001234", "کانال ما رو دنبال کن و از تخفیف‌های ویژه باخبر شو", expectedCategory = Category.PROMO),
        GoldenExample("90001234", "نمایندگی جدید افتتاح شد، مشاوره رایگان برای عضویت", expectedCategory = Category.PROMO),
        GoldenExample("30001234", "فروشگاه اینترنتی ما محصولات جدید رو با قیمت ویژه عرضه کرد", expectedCategory = Category.PROMO),
        GoldenExample("1000123", "جوایز نقدی هفتگی، همین امروز عضو شو", expectedCategory = Category.PROMO),
        GoldenExample("20003456", "شعبه جدید افتتاح شد، فرصت طلایی خرید با تخفیف", expectedCategory = Category.PROMO),
        GoldenExample(
            "30001234", "ت​خفیف ویژه ح​راج",
            expectedCategory = Category.PROMO, note = "نویسه‌های نامرئی نباید کلیدواژه را پنهان کنند",
        ),
    )

    // --- Phishing: شاهد ساختاری قطعی (جعل دامنه یا جعل سرشماره). ---
    private val phishing = listOf(
        GoldenExample(
            "09121234567", "بانک ملی: حساب شما موقتاً مسدود شده. برای رفع مسدودی به bmi-ir.com/login بروید",
            expectedCategory = Category.PHISHING, note = "جعل دامنه: نقطه با خط‌تیره عوض شده",
        ),
        GoldenExample(
            "200011", "بانک ملی اطلاعیه: برای فعالسازی به bmi.ir.secure-login.com مراجعه کنید",
            expectedCategory = Category.PHISHING, note = "جعل دامنه: دامنهٔ رسمی زیردامنهٔ یک دامنهٔ دیگر شده",
        ),
        GoldenExample(
            "9990", "همراه اول: بسته اینترنت رایگان شما آماده است mymci-ir.com/gift",
            expectedCategory = Category.PHISHING, note = "جعل دامنه، حتی برای نهاد غیرحساس",
        ),
        GoldenExample(
            "7575", "ایرانسل: صورتحساب شما آماده است irancel.ir/bill",
            expectedCategory = Category.PHISHING, note = "جعل دامنه: غلط املایی نزدیک",
        ),
        GoldenExample(
            "200030", "پست ایران: بسته شما را در tracking-post.ir پیگیری کنید",
            expectedCategory = Category.PHISHING, note = "جعل دامنه: نقطه با خط‌تیره عوض شده",
        ),
        GoldenExample(
            "9990", "همراه اول: صورتحساب شما rnci.ir/bill",
            expectedCategory = Category.PHISHING, note = "جعل دامنه: rn به‌جای m",
        ),
        GoldenExample(
            "10001100", "بانک ملی: برای ادامه وارد brni.ir/login شوید",
            expectedCategory = Category.PHISHING, note = "جعل دامنه: rn به‌جای m",
        ),
        GoldenExample(
            "7575", "ایرانسل: فعال‌سازی سرویس در irancell.ir.activate-now.com",
            expectedCategory = Category.PHISHING, note = "جعل دامنه: دامنهٔ رسمی زیردامنهٔ دامنهٔ دیگر",
        ),
        GoldenExample(
            "200011", "بانک ملی: پرداخت خود را در sadad-ir.com تکمیل کنید",
            expectedCategory = Category.PHISHING, note = "جعل دامنه روی sadad.ir",
        ),
        GoldenExample(
            "09121234567", "بانک ملی: حساب شما مسدود شد. وارد melli-pay.com شوید",
            expectedCategory = Category.PHISHING, note = "جعل سرشماره: خط شخصی به‌جای سرشمارهٔ رسمی بانک حساس",
        ),
        GoldenExample(
            "09351112233", "قوه قضاییه: ابلاغیه قضایی جدید دارید. مشاهده: adl-notice.click",
            expectedCategory = Category.PHISHING, note = "جعل سرشماره: نهاد حساس دولتی",
        ),
        GoldenExample(
            "09193334455", "بانک ملت: یارانه شما واریز نشده، برای دریافت وجه کلیک کنید mellat-yarane.ir",
            expectedCategory = Category.PHISHING, note = "جعل سرشماره",
        ),
        GoldenExample(
            "09122223344", "پلیس راهور: خلافی خودرو شما مسدود خواهد شد، همین الان وارد شوید rahvar-pay.com",
            expectedCategory = Category.PHISHING, note = "جعل سرشماره: نهاد حساس دولتی",
        ),
        GoldenExample(
            "09126667788", "بانک صادرات: حساب شما مسدود، احراز هویت فوری در saderat-verify.com",
            expectedCategory = Category.PHISHING, note = "جعل سرشماره",
        ),
        GoldenExample(
            "09374111222", "سامانه ثنا: ابلاغیه قضایی صادر شد، برای دریافت وجه کلیک کنید sana-abl.ir",
            expectedCategory = Category.PHISHING, note = "جعل سرشماره",
        ),
        GoldenExample(
            "09355556677", "بانک ملت: حساب شما مسدود شد، احراز هویت فوری کنید mellat-secure.com",
            expectedCategory = Category.PHISHING, note = "جعل سرشماره",
        ),
        GoldenExample(
            "AlertBank", "بانک ملی: کلیک کنید همین الان وارد شوید تا حساب شما مسدود نشود meli-alert.com",
            expectedCategory = Category.PHISHING, note = "جعل سرشماره از فرستندهٔ حرفی",
        ),
        GoldenExample(
            "09999888777", "بانک صادرات: سهام عدالت شما آماده واریز است، احراز هویت فوری در saderat-sahm.com",
            expectedCategory = Category.PHISHING, note = "جعل سرشماره با طعمهٔ سهام عدالت",
        ),
    )

    /**
     * نمونه‌های اضافه که برای هیچ‌یک از شش دستهٔ اصلی لازم نیستند، ولی پوشش
     * «هرگز فیشینگ نشو» را روی پیامک‌های نامشخص هم می‌سنجند — این‌ها معمولاً
     * جایی هستند که یک کلاهبردار می‌توانست الگوی مشابهی بسازد ولی شاهد کافی
     * نیست.
     */
    private val extraNeverPhishing = listOf(
        GoldenExample(
            "MissedCalls", "شما ۱ تماس از ۰۹۱۲۱۲۳۴۵۶۷ داشته‌اید.\nاپلیکیشن ایرانسل من را نصب کنید: i3l.ir/abc",
            expectedCategory = Category.UNKNOWN,
            note = "نام اپراتور + لینک غیررسمی از سرشمارهٔ حرفی غیرحساس، به‌تنهایی شاهد نیست",
        ),
        GoldenExample("6655", "اطلاعیه: ساعت کاری روز پنجشنبه تغییر کرده است", expectedCategory = Category.UNKNOWN),
        GoldenExample("6789", "جلسه هیئت مدیره به تعویق افتاد", expectedCategory = Category.UNKNOWN),
        GoldenExample(
            "HAMRAHAVAL", "همراه اول: اپلیکیشن همراه من را از https://mymci.app بگیرید",
            expectedCategory = Category.UNKNOWN, note = "دامنهٔ اپلیکیشن رسمی نهاد؛ جعل نیست",
        ),
    )

    val all: List<GoldenExample> =
        personal + otp + bank + service + promo + phishing + extraNeverPhishing
}
