package ir.asudehapp.sms.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ir.asudehapp.sms.model.Category
import ir.asudehapp.sms.model.Confidence
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.Origin
import ir.asudehapp.sms.model.ReasonCode

/**
 * ایندکس محلی. **منبع حقیقت، Telephony Provider سیستم است** (D19 ب) و این
 * جدول کپی خواندنی است؛ پاک شدنش با بازسازی جبران می‌شود و هیچ پیامکی از بین
 * نمی‌رود (ADR-0003).
 */
@Entity(
    tableName = "message",
    primaryKeys = ["kind", "providerId"],
    indices = [
        Index(value = ["threadId", "folder", "dateReceived"]),
        Index(value = ["folder", "dateReceived"]),
        Index(value = ["address"]),
    ],
)
data class MessageEntity(
    /** `SMS` یا `MMS`. کلید اصلی با `providerId` جفت است، چون شمارنده‌ها جدا هستند. */
    val kind: String,
    val providerId: Long,
    val threadId: Long,
    val address: String,
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
    /** طبقه‌بندی ناتمام مانده است؛ در شروع بعدی اپ دوباره انجام می‌شود (D23). */
    val pendingClassify: Boolean,
    /** نشانهٔ `Suspect`: نوار هشدار و لینک غیرفعال (D46). */
    val risk: Boolean,
    /** تبلیغ قطعی که خودکار جابه‌جا نشده و فقط پیشنهاد جابه‌جایی دارد (اصل ۸). */
    val suggestMove: Boolean,
) {
    companion object {
        const val KIND_SMS: String = "SMS"
        const val KIND_MMS: String = "MMS"
    }
}

enum class SenderRuleKind { ALLOW, BLOCK }

/** `Allowlist` و `Blocklist` کاربر، که در «قواعد من» دیده می‌شوند (D20). */
@Entity(tableName = "sender_rule")
data class SenderRuleEntity(
    @PrimaryKey val address: String,
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
)

/** شمار دسته‌های هر سرشماره، برای تشخیص `MixedSender` (ADR-0006). */
data class SenderStats(
    val address: String,
    val promoCount: Int,
    val importantCount: Int,
) {
    /** سرشماره‌ای که هم تبلیغ می‌فرستد و هم پیامک مهم. */
    fun isMixed(): Boolean = promoCount > 0 && importantCount > 0
}
