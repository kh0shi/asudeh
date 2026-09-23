package ir.asudehapp.sms.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import ir.asudehapp.sms.model.Category
import ir.asudehapp.sms.model.Confidence
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.Origin
import ir.asudehapp.sms.model.ReasonCode
import ir.asudehapp.sms.persian.SearchText

/**
 * ایندکس محلی. **منبع حقیقت، Telephony Provider سیستم است** (D19 ب) و این
 * جدول کپی خواندنی است؛ پاک شدنش با بازسازی جبران می‌شود و هیچ پیامکی از بین
 * نمی‌رود (ADR-0003).
 *
 * `providerId` منفی یعنی نوشتن در provider شکست خورده و پیامک فعلاً فقط اینجاست.
 * همگام‌سازی بعدی دوباره آن را در provider می‌نویسد.
 */
@Entity(
    tableName = "message",
    primaryKeys = ["kind", "providerId"],
    indices = [
        Index(value = ["threadId", "folder", "dateReceived"]),
        Index(value = ["folder", "dateReceived"]),
        Index(value = ["normalizedAddress"]),
        Index(value = ["pendingClassify"]),
    ],
)
data class MessageEntity(
    /** `SMS` یا `MMS`. کلید اصلی با `providerId` جفت است، چون شمارنده‌ها جدا هستند. */
    val kind: String,
    val providerId: Long,
    val threadId: Long,
    /** سرشماره همان‌طور که در provider هست؛ برای نمایش. */
    val address: String,
    /**
     * سرشمارهٔ یکسان‌شده با `Addresses.normalize`. همهٔ کوئری‌های «پیامک‌های این
     * فرستنده» روی این ستون‌اند، تا `+98912…` و `0912…` و `Irancell` و
     * `irancell` یک فرستنده شمرده شوند (ADR-0006).
     */
    val normalizedAddress: String,
    val body: String,
    /** زمان ارسال، از PDU. */
    val date: Long,
    /** زمان دریافت روی گوشی؛ مرتب‌سازی بر اساس این است (D24). */
    val dateReceived: Long,
    val subId: Int,
    /** `Folder` مال هر پیامک است، نه هر گفتگو (ADR-0005). */
    val folder: Folder,
    val category: Category,
    val confidence: Confidence,
    val reasonCode: ReasonCode,
    val reasonArgs: String,
    /** نسخهٔ `RulePack` که این تصمیم با آن گرفته شده، برای طبقه‌بندی دوباره (D36). */
    val rulesVersion: String,
    val origin: Origin,
    val read: Boolean,
    val outgoing: Boolean,
    /** وضعیت ارسال، فقط برای پیامک ارسالی. ارسال ناموفق هرگز بی‌صدا نیست. */
    val sendStatus: SendStatus = SendStatus.NONE,
    /** طبقه‌بندی ناتمام مانده است؛ در همگام‌سازی بعدی دوباره انجام می‌شود (D23). */
    val pendingClassify: Boolean,
    /** نشانهٔ `Suspect`: نوار هشدار و لینک غیرفعال (D46). */
    val risk: Boolean,
    /** تبلیغ قطعی که خودکار جابه‌جا نشده و فقط پیشنهاد جابه‌جایی دارد (اصل ۸). */
    val suggestMove: Boolean,
    /**
     * طرف‌های دیگر یک MMS گروهی، جدا با «|». برای پیامک عادی خالی است. MMS
     * گروهی هرگز پنهان نمی‌شود (D26).
     */
    @ColumnInfo(defaultValue = "''")
    val recipients: String = "",
    /** شمار پیوست‌های MMS (تصویر، صدا، ویدیو، ...). */
    @ColumnInfo(defaultValue = "0")
    val attachments: Int = 0,
    /**
     * MMSی که فقط اعلانش رسیده و خودش هنوز از MMSC دریافت نشده است. در گفتگو
     * با دکمهٔ «دریافت» دیده می‌شود؛ پس بی‌صدا گم نمی‌شود (`SilentLoss`).
     */
    @ColumnInfo(defaultValue = "0")
    val pendingDownload: Boolean = false,
    /**
     * متن نرمال‌شده برای جستجو (`message_fts`، D20). هرگز نمایش داده نمی‌شود.
     * خالی یعنی هنوز ساخته نشده؛ ایندکس‌های نسخهٔ ۱ در همگام‌سازی پر می‌شوند.
     */
    @ColumnInfo(defaultValue = "''")
    val searchText: String = SearchText.of(body, address),
) {
    /** پیامک یا MMS گروهی: بیش از یک طرف دیگر. */
    val isGroup: Boolean get() = recipients.contains(RECIPIENT_SEPARATOR)

    /** همهٔ طرف‌های دیگر گفتگو. برای پیامک عادی فقط [address]. */
    val participants: List<String>
        get() = if (recipients.isEmpty()) listOf(address) else recipients.split(RECIPIENT_SEPARATOR)

    companion object {
        const val KIND_SMS: String = "SMS"
        const val KIND_MMS: String = "MMS"
        const val RECIPIENT_SEPARATOR: String = "|"
    }
}

/**
 * جستجوی متن کامل (D20). محتوای آن از ستون `searchText` جدول `message` می‌آید
 * و Room با trigger آن را هم‌گام نگه می‌دارد.
 */
@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "message_fts")
data class MessageFts(
    val searchText: String,
)

/**
 * ترجیح‌های کاربر برای هر گفتگو: سنجاق و پیش‌نویس (D53). این‌ها مثل خود ایندکس
 * در انتقال گوشی‌به‌گوشی منتقل نمی‌شوند، چون شناسهٔ گفتگوها آنجا فرق دارد.
 */
@Entity(tableName = "thread_pref")
data class ThreadPrefEntity(
    @PrimaryKey val threadId: Long,
    val pinned: Boolean,
    /** متن نوشته‌شده ولی فرستاده‌نشده. خالی یعنی پیش‌نویسی نیست. */
    val draft: String,
    val updatedAt: Long,
    /** بی‌صدا: اعلانی برای پیامک‌های تازهٔ این گفتگو نمی‌آید، ولی هیچ‌چیز پنهان نمی‌شود. */
    @ColumnInfo(defaultValue = "0")
    val muted: Boolean = false,
    /**
     * بایگانی: گفتگو از فهرست پوشه‌اش (صندوق، تبلیغات یا کلاهبرداری) برداشته
     * می‌شود ولی جایی حذف نمی‌شود؛ از صفحهٔ «بایگانی» قابل بازگشت است. این با
     * `folder` روی خود پیامک فرق دارد: طبقه‌بندی نیست، فقط ترجیح کاربر است.
     */
    @ColumnInfo(defaultValue = "0")
    val archived: Boolean = false,
)

enum class SendStatus {
    /** پیامک دریافتی. */
    NONE,

    /** در صف ارسال. */
    PENDING,
    SENT,

    /** ارسال نشد؛ در گفتگو با دکمهٔ «دوباره بفرست» دیده می‌شود. */
    FAILED,

    /** گزارش تحویل رسیده است (D53؛ فقط اگر کاربر گزارش تحویل را روشن کرده باشد). */
    DELIVERED,
}

enum class SenderRuleKind { ALLOW, BLOCK }

/** `Allowlist` و `Blocklist` کاربر، که در «قواعد من» دیده می‌شوند (D20). */
@Entity(tableName = "sender_rule")
data class SenderRuleEntity(
    @PrimaryKey val address: String,
    val kind: SenderRuleKind,
    val createdAt: Long,
)

/**
 * کلیدواژه‌های `Allowlist` و `Blocklist` کاربر (ADR-0012): «هر پیامکی که این
 * واژه را دارد تبلیغ است» یا «همیشه در صندوق بماند».
 *
 * کلید اصلی، **شکل یکسان‌شدهٔ** واژه است (`KeywordMatch.key`)، تا «لغو ۱۱» و
 * «لغو۱۱» دو قاعدهٔ جدا نشوند. متن خامِ آن‌طور که کاربر نوشته در [keyword]
 * می‌ماند و همان نشان داده می‌شود.
 */
@Entity(tableName = "keyword_rule")
data class KeywordRuleEntity(
    @PrimaryKey val normalized: String,
    val keyword: String,
    val kind: SenderRuleKind,
    val createdAt: Long,
)

/** خلاصهٔ یک گفتگو **در یک پوشه**. یک گفتگو می‌تواند در چند پوشه دیده شود (`SplitThread`). */
data class ThreadSummary(
    val threadId: Long,
    val folder: Folder,
    val address: String,
    val snippet: String,
    val lastDate: Long,
    val unread: Int,
    val total: Int,
    val hasRisk: Boolean,
    /** طرف‌های دیگر آخرین پیامک، برای گفتگوی گروهی. */
    val recipients: String = "",
    /** شمار پیوست‌های آخرین پیامک؛ برای پیش‌نمایش MMS بی‌متن. */
    val attachments: Int = 0,
    val pinned: Boolean = false,
    val draft: String = "",
    val muted: Boolean = false,
    val archived: Boolean = false,
)

/** خلاصهٔ پیشنهاد جابه‌جایی پیامک‌های قدیمی (اصل ۸). */
data class MoveSuggestion(
    val messages: Int,
    val threads: Int,
    /** چندتا از [messages] مشکوک به کلاهبرداری‌اند و به `ScamFolder` می‌روند. */
    val scams: Int,
) {
    companion object {
        val NONE: MoveSuggestion = MoveSuggestion(0, 0, 0)
    }
}

/** شمار دسته‌های هر سرشماره، برای تشخیص `MixedSender` (ADR-0006). */
data class SenderStats(
    val address: String,
    val promoCount: Int,
    val importantCount: Int,
) {
    /** سرشماره‌ای که هم تبلیغ می‌فرستد و هم پیامک مهم. */
    fun isMixed(): Boolean = promoCount > 0 && importantCount > 0
}

/** کلید یک پیامک در ایندکس. */
data class MessageKey(val kind: String, val providerId: Long)

/** شمار پیامک‌های پنهان‌شده، برای `Digest` (D40). */
data class HiddenCount(val promo: Int, val scam: Int) {
    val total: Int get() = promo + scam
}

/** جای یک پیامک، برای پشتیبان. */
data class ProviderFolder(val providerId: Long, val folder: Folder)

/**
 * پیامکی که کاربر حذف کرده است (ADR-0010). حذف واقعی است: پیامک از Telephony
 * Provider سیستم برداشته می‌شود و این ردیف تنها نسخهٔ باقی‌ماندهٔ آن است، تا
 * «حذف اشتباهی» هم مثل بقیهٔ کارهای اپ برگشت‌پذیر باشد (اصل ۵).
 *
 * سطل **هرگز خودکار خالی نمی‌شود** (مانیفست بند ۵-۴): فقط خود کاربر آن را
 * خالی می‌کند.
 */
@Entity(tableName = "trashed_message", indices = [Index(value = ["deletedAt"])])
data class TrashedMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    /** شناسهٔ قبلی در provider، فقط برای ردگیری؛ با بازگرداندن عوض می‌شود. */
    val providerId: Long,
    val address: String,
    val recipients: String,
    val body: String,
    val date: Long,
    val dateReceived: Long,
    val subId: Int,
    /** پوشه‌ای که پیامک پیش از حذف در آن بود؛ با بازگرداندن به همان‌جا برمی‌گردد. */
    val folder: Folder,
    val read: Boolean,
    val outgoing: Boolean,
    val attachments: Int,
    val deletedAt: Long,
) {
    /**
     * MMS برنمی‌گردد: partهایش با حذف از provider از بین می‌روند و نوشتن دوبارهٔ
     * متنش به‌جای خود پیام، یک پیام جعلی می‌سازد. متنش اینجا می‌ماند تا کاربر
     * ببیند چه چیزی را حذف کرده است.
     */
    val restorable: Boolean get() = kind == MessageEntity.KIND_SMS
}

/** وضعیت یک پیامک زمان‌بندی‌شده (ADR-0011). */
enum class ScheduleState {
    /** منتظر رسیدن زمانش. */
    WAITING,

    /**
     * زمانش آن‌قدر گذشته که ارسال خودکار درست نیست (گوشی خاموش بوده). پیامک
     * **بی‌صدا فرستاده نمی‌شود**؛ در گفتگو با «الان بفرست» دیده می‌شود.
     */
    MISSED,
}

/**
 * پیامکی که کاربر برای بعد زمان‌بندی کرده است (ADR-0011).
 *
 * تا زمان ارسال در Telephony Provider نوشته نمی‌شود، چون هنوز پیامکی نیست:
 * `SaveFirst` همان لحظهٔ ارسال اجرا می‌شود. این ردیف در `index.db` است، پس با
 * پاک شدن اپ از بین می‌رود؛ برای همین همین‌جا دیده می‌شود و در گفتگو هم.
 */
@Entity(
    tableName = "scheduled_message",
    indices = [Index(value = ["sendAt"]), Index(value = ["threadId"])],
)
data class ScheduledMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    /** گیرنده‌ها، جدا با «|»؛ برای گروه بیش از یکی. */
    val recipients: String,
    val body: String,
    val subId: Int,
    val sendAt: Long,
    val createdAt: Long,
    val state: ScheduleState,
) {
    val addresses: List<String> get() = recipients.split(MessageEntity.RECIPIENT_SEPARATOR)
}
