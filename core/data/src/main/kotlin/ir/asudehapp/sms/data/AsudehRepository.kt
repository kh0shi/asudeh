package ir.asudehapp.sms.data

import android.content.Context
import ir.asudehapp.sms.classifier.Classifier
import ir.asudehapp.sms.classifier.CompiledRulePack
import ir.asudehapp.sms.classifier.Router
import ir.asudehapp.sms.classifier.receive.ClassifiedMessage
import ir.asudehapp.sms.classifier.receive.MessageIndex
import ir.asudehapp.sms.model.Addresses
import ir.asudehapp.sms.model.Category
import ir.asudehapp.sms.model.Confidence
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.MessageInput
import ir.asudehapp.sms.model.Origin
import ir.asudehapp.sms.model.ReasonCode
import ir.asudehapp.sms.model.UserRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** جداکنندهٔ آرگومان‌های `Reason` داخل یک ستون. */
private const val ARG_SEPARATOR = "|"

/** نوشتن نتیجهٔ طبقه‌بندی در ایندکس محلی. */
class RoomMessageIndex(
    private val dao: MessageDao,
    private val rulesVersion: String,
) : MessageIndex {

    override suspend fun upsert(message: ClassifiedMessage) {
        dao.upsert(message.toEntity(rulesVersion))
    }
}

fun ClassifiedMessage.toEntity(rulesVersion: String): MessageEntity = MessageEntity(
    kind = MessageEntity.KIND_SMS,
    providerId = providerId,
    threadId = threadId,
    address = raw.address.ifBlank { TelephonyProviderStore.UNKNOWN_SENDER },
    body = raw.body,
    date = raw.sentAt,
    dateReceived = raw.receivedAt,
    subId = raw.subscriptionId,
    folder = placement.folder,
    category = verdict.category,
    confidence = verdict.confidence,
    reasonCode = placement.reason.code,
    reasonArgs = placement.reason.args.joinToString(ARG_SEPARATOR),
    rulesVersion = rulesVersion,
    origin = origin,
    read = false,
    outgoing = false,
    pendingClassify = pendingClassify,
    risk = verdict.risk,
    suggestMove = placement.suggestMove,
)

/**
 * تنها راه رسیدن رابط کاربری به داده‌ها. همهٔ صفحه‌ها فقط از ایندکس محلی
 * می‌خوانند (D19 ب)، و منبع حقیقت همچنان Telephony Provider سیستم است.
 */
class AsudehRepository(
    context: Context,
    private val database: AsudehDatabase = AsudehDatabase.get(context),
    val rules: CompiledRulePack = CompiledRulePack.bundled(),
) {
    val store: TelephonyProviderStore = TelephonyProviderStore(context)
    val classifier: Classifier = Classifier(rules)

    private val dao: MessageDao get() = database.messages()
    private val ruleDao: SenderRuleDao get() = database.senderRules()

    val index: MessageIndex get() = RoomMessageIndex(dao, rules.pack.version)

    fun threads(folder: Folder): Flow<List<ThreadSummary>> = dao.observeThreads(folder)

    fun conversation(threadId: Long): Flow<List<MessageEntity>> = dao.observeConversation(threadId)

    fun unreadCount(folder: Folder): Flow<Int> = dao.observeUnreadCount(folder)

    fun count(folder: Folder): Flow<Int> = dao.observeCount(folder)

    fun userRules(): Flow<UserRules> = ruleDao.observeAll().map { it.toUserRules() }

    suspend fun currentUserRules(): UserRules = ruleDao.all().toUserRules()

    suspend fun markThreadRead(threadId: Long, folder: Folder) {
        dao.markThreadRead(threadId, folder)
        val ids = dao.providerIdsIn(threadId, folder)
        store.markRead(ids)
    }

    /**
     * `Rescue`: **همیشه فقط همان پیامک** به `Inbox` برمی‌گردد. رفتن سرشماره به
     * `Allowlist` یک پرسش جداگانه است (ADR-0006 بند ۱).
     */
    suspend fun rescue(message: MessageEntity) {
        dao.moveMessage(message.kind, message.providerId, Folder.INBOX)
    }

    /** پاسخ «بله» به پرسشِ پس از `Rescue`. */
    suspend fun alwaysAllow(address: String) {
        val normalized = Addresses.normalize(address)
        ruleDao.put(SenderRuleEntity(normalized, SenderRuleKind.ALLOW, System.currentTimeMillis()))
        dao.moveAllFrom(normalized, Folder.PROMO, Folder.INBOX)
    }

    /**
     * `Block`: «تبلیغ‌های این فرستنده را همیشه پنهان کن». پیامک `OTP` و `Bank`
     * قطعی همچنان در `Inbox` می‌ماند، چون `Router` آن را پیش از `Blocklist`
     * بررسی می‌کند (ADR-0006 بند ۲).
     */
    suspend fun block(address: String) {
        val normalized = Addresses.normalize(address)
        ruleDao.put(SenderRuleEntity(normalized, SenderRuleKind.BLOCK, System.currentTimeMillis()))
        reclassifyFrom(normalized)
    }

    /** هر دو اقدام برگشت‌پذیرند و در «قواعد من» دیده می‌شوند (اصل ۵). */
    suspend fun forgetRule(address: String) {
        val normalized = Addresses.normalize(address)
        ruleDao.remove(normalized)
        reclassifyFrom(normalized)
    }

    /** آیا این سرشماره هم تبلیغ می‌فرستد و هم پیامک مهم (`MixedSender`)؟ */
    suspend fun isMixedSender(address: String): Boolean =
        dao.statsFor(Addresses.normalize(address))?.isMixed() ?: false

    /** پیشنهاد `Unsub11` فقط برای خطوط انبوه معنی دارد (D28). */
    fun isAdLine(address: String): Boolean = Addresses.isAdLine(address)

    /**
     * دوباره جا دادن پیامک‌های یک سرشماره، بعد از اینکه کاربر قاعده‌ای عوض کرده
     * است. برگرداندن به `Inbox` همیشه انجام می‌شود، چون این جهت امن است (D36).
     * پنهان کردن فقط وقتی انجام می‌شود که خود کاربر `Block` کرده باشد.
     */
    suspend fun reclassifyFrom(address: String) = withContext(Dispatchers.Default) {
        val userRules = currentUserRules()
        val normalized = Addresses.normalize(address)
        for (message in dao.messagesFrom(normalized)) {
            if (message.outgoing) continue
            val input = MessageInput(message.address, message.body)
            val verdict = classifier.classify(input)
            val placement = Router.route(verdict, input, userRules, Origin.EXTERNAL)
            val target = when {
                placement.folder == Folder.INBOX -> Folder.INBOX
                userRules.isBlocked(normalized) -> placement.folder
                else -> continue
            }
            if (target != message.folder) {
                dao.moveMessage(message.kind, message.providerId, target)
            }
        }
    }

    /**
     * `HistorySweep` و همگام‌سازی افزایشی (D21). پیامک‌های قدیمی طبقه‌بندی
     * می‌شوند ولی **بدون تأیید کاربر جابه‌جا نمی‌شوند** (اصل ۸)؛ `Router` این را
     * از روی `origin` رعایت می‌کند.
     *
     * خروجی، تعداد پیامک‌هایی است که تازه به ایندکس اضافه شده‌اند.
     */
    suspend fun sync(origin: Origin = Origin.SWEEP): Int = withContext(Dispatchers.Default) {
        val userRules = currentUserRules()
        val since = dao.lastProviderId(MessageEntity.KIND_SMS) ?: 0L
        val fresh = store.readAll(sinceId = since)
        val entities = fresh.map { sms -> sms.toEntity(userRules, origin) }
        dao.upsertAll(entities)
        entities.size
    }

    private fun ProviderSms.toEntity(userRules: UserRules, origin: Origin): MessageEntity {
        val input = MessageInput(address, body)
        val verdict = if (outgoing) null else classifier.classify(input)
        val placement = verdict?.let { Router.route(it, input, userRules, origin) }
        return MessageEntity(
            kind = MessageEntity.KIND_SMS,
            providerId = id,
            threadId = threadId,
            address = address,
            body = body,
            date = if (dateSent > 0) dateSent else date,
            dateReceived = date,
            subId = subId,
            folder = placement?.folder ?: Folder.INBOX,
            category = verdict?.category ?: Category.PERSONAL,
            confidence = verdict?.confidence ?: Confidence.LOW,
            reasonCode = placement?.reason?.code ?: ReasonCode.NOT_SURE,
            reasonArgs = placement?.reason?.args.orEmpty().joinToString(ARG_SEPARATOR),
            rulesVersion = rules.pack.version,
            origin = origin,
            read = read,
            outgoing = outgoing,
            pendingClassify = false,
            risk = verdict?.risk ?: false,
            suggestMove = placement?.suggestMove ?: false,
        )
    }

    /**
     * تنها حذف گروهی اپ، و همیشه با اقدام صریح کاربر و تأیید دومرحله‌ای (D45).
     * اپ هرگز به ابتکار خودش پیامکی را حذف نمی‌کند (ADR-0003 بند ۳).
     */
    suspend fun emptyFolderAfterExplicitConfirmation(folder: Folder) {
        dao.clearFolder(folder)
    }
}

private fun List<SenderRuleEntity>.toUserRules(): UserRules = UserRules(
    allowlist = filter { it.kind == SenderRuleKind.ALLOW }.mapTo(mutableSetOf()) { it.address },
    blocklist = filter { it.kind == SenderRuleKind.BLOCK }.mapTo(mutableSetOf()) { it.address },
)
