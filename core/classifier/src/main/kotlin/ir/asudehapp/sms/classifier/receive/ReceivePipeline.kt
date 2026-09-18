package ir.asudehapp.sms.classifier.receive

import ir.asudehapp.sms.classifier.Router
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.MessageInput
import ir.asudehapp.sms.model.NotificationBehavior
import ir.asudehapp.sms.model.Origin
import ir.asudehapp.sms.model.Placement
import ir.asudehapp.sms.model.Reason
import ir.asudehapp.sms.model.ReasonCode
import ir.asudehapp.sms.model.UserRules
import ir.asudehapp.sms.model.Verdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** پیامک خام، همان‌طور که از PDU بیرون آمده است. */
data class RawSms(
    val address: String,
    val body: String,
    /** زمان ارسال، از PDU. */
    val sentAt: Long,
    /** زمان دریافت روی گوشی؛ مرتب‌سازی بر اساس این است (D24). */
    val receivedAt: Long,
    val subscriptionId: Int = -1,
)

/** نتیجهٔ نوشتن در Telephony Provider سیستم. */
data class StoredMessage(val providerId: Long, val threadId: Long)

/** نوشتن در Telephony Provider سیستم، که منبع حقیقت است (D19). */
interface TelephonyStore {
    suspend fun saveIncoming(raw: RawSms): StoredMessage
}

/** ایندکس محلی Room. پاک شدنش با بازسازی جبران می‌شود و داده‌ای از بین نمی‌رود. */
interface MessageIndex {
    suspend fun upsert(message: ClassifiedMessage)
}

interface Notifier {
    suspend fun notify(message: ClassifiedMessage)
}

/** یک پیامک با جای نهایی‌اش. */
data class ClassifiedMessage(
    val raw: RawSms,
    val verdict: Verdict,
    val placement: Placement,
    val origin: Origin,
    val providerId: Long,
    val threadId: Long,
    /** در Telephony Provider سیستم نوشته شد. */
    val stored: Boolean,
    /** در ایندکس محلی نوشته شد. */
    val indexed: Boolean,
    /**
     * طبقه‌بندی شکست خورد یا از سقف زمانی گذشت. پیامک در `Inbox` است و در
     * شروع بعدی اپ دوباره طبقه‌بندی می‌شود، ولی جابه‌جا نمی‌شود (D23).
     */
    val pendingClassify: Boolean,
)

/**
 * مسیر دریافت پیامک (D23)، به‌عنوان یک کلاس خالص Kotlin تا بشود بدون اندروید
 * آزمونش کرد.
 *
 * ترتیب کارها بخشی از قرارداد است و ADR-0003 آن را تعیین می‌کند:
 * **اول نوشتن در provider**، بعد طبقه‌بندی. هیچ منطقی پیش از ذخیره اجرا
 * نمی‌شود که بتواند پیامک را دور بریزد، و هیچ خطایی در مرحله‌های بعد باعث
 * نمی‌شود پیامک گم شود (`SilentLoss` ممنوع است).
 */
class ReceivePipeline(
    private val store: TelephonyStore,
    private val index: MessageIndex,
    private val notifier: Notifier,
    /** معمولاً `Classifier::classify`؛ در آزمون‌ها با یک تابع ساختگی جایگزین می‌شود. */
    private val classify: suspend (MessageInput) -> Verdict,
    /** آیا این سرشماره در مخاطب‌های گوشی هست (برای قفل ایمنی D31). */
    private val isKnownContact: suspend (String) -> Boolean = { false },
    private val userRules: suspend () -> UserRules = { UserRules.EMPTY },
    private val classifyTimeoutMillis: Long = DEFAULT_CLASSIFY_TIMEOUT_MILLIS,
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) {

    suspend fun onReceive(raw: RawSms, origin: Origin = Origin.LIVE): ClassifiedMessage {
        // ۱. اول ذخیره در سیستم (`SaveFirst`).
        var stored = true
        val saved = try {
            store.saveIncoming(raw)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            stored = false
            onError("saveIncoming", failure)
            StoredMessage(providerId = -1L, threadId = -1L)
        }

        // ۲. طبقه‌بندی، با سقف زمانی. خطا یا دیرکرد یعنی پیامک در `Inbox` می‌ماند.
        var pendingClassify = false
        var verdict = Verdict.unclassified(ReasonCode.CLASSIFIER_FAILED)
        var placement = fallbackPlacement()
        try {
            withTimeout(classifyTimeoutMillis) {
                val input = MessageInput(
                    address = raw.address,
                    body = raw.body,
                    isKnownContact = isKnownContact(raw.address),
                )
                val computed = classify(input)
                verdict = computed
                placement = Router.route(computed, input, userRules(), origin)
            }
        } catch (timeout: TimeoutCancellationException) {
            pendingClassify = true
            onError("classify-timeout", timeout)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            pendingClassify = true
            verdict = Verdict.unclassified(ReasonCode.CLASSIFIER_FAILED)
            placement = fallbackPlacement()
            onError("classify", failure)
        }

        if (pendingClassify) {
            verdict = Verdict.unclassified(ReasonCode.CLASSIFIER_FAILED)
            placement = fallbackPlacement()
        }

        // ۳. نوشتن در ایندکس محلی.
        var indexed = true
        var message = ClassifiedMessage(
            raw = raw,
            verdict = verdict,
            placement = placement,
            origin = origin,
            providerId = saved.providerId,
            threadId = saved.threadId,
            stored = stored,
            indexed = true,
            pendingClassify = pendingClassify,
        )
        try {
            index.upsert(message)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            indexed = false
            onError("index", failure)
        }
        message = message.copy(indexed = indexed)

        // ۴. اعلان. حتی وقتی مرحله‌های بالا شکست خورده‌اند، کاربر باید پیامک را ببیند.
        try {
            notifier.notify(message)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            onError("notify", failure)
        }

        return message
    }

    private fun fallbackPlacement() = Placement(
        folder = Folder.INBOX,
        notification = NotificationBehavior.ALERT,
        reason = Reason(ReasonCode.CLASSIFIER_FAILED),
    )

    companion object {
        /** سقف زمانی طبقه‌بندی داخل `SMS_DELIVER` (D23). */
        const val DEFAULT_CLASSIFY_TIMEOUT_MILLIS: Long = 2_000
    }
}
