# قواعد R8 برای نسخهٔ release (ROADMAP B6).
#
# گیرنده‌ها، سرویس و Activityها از manifest نگه داشته می‌شوند. Room،
# kotlinx.serialization و androidx قواعد keep خودشان را همراه دارند. اینجا فقط
# چیزهایی می‌آید که R8 خودش نمی‌تواند بفهمد.

# RulePack (D33) از java resource با مسیر مطلق خوانده می‌شود
# (/ir/asudehapp/sms/classifier/rulepack.json). R8 نام فایل‌های resource را عوض
# نمی‌کند مگر -adaptresourcefilenames بیاید؛ اینجا عمداً نیامده است.

# کلاس‌های @Serializable (RulePack و قالب فایل پشتیبان، D57): نام فیلدها
# کلیدهای JSON‌اند. پلاگین serialization نام‌ها را در serializer تولیدی نگه
# می‌دارد، ولی خود serializerها باید پیدا شوند.
-keepclassmembers @kotlinx.serialization.Serializable class ir.asudehapp.sms.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# نام enumها در index.db و rules.db با name() ذخیره و با valueOf() خوانده می‌شوند.
-keepclassmembers enum ir.asudehapp.sms.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# گزارش خطا (D58) نام کلاس و شمارهٔ خط را نشان می‌دهد؛ با mapping.txt خوانا می‌شود.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
