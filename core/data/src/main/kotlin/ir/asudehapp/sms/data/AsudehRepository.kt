package ir.asudehapp.sms.data

import android.content.Context
import ir.asudehapp.sms.classifier.Classifier
import ir.asudehapp.sms.classifier.CompiledRulePack
import ir.asudehapp.sms.classifier.Router
import ir.asudehapp.sms.classifier.receive.ClassifiedMessage
import ir.asudehapp.sms.classifier.receive.MessageIndex
import ir.asudehapp.sms.classifier.receive.RawSms
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
    normalizedAddress = Addresses.normalize(raw.address.ifBlank { TelephonyProviderStore.UNKNOWN_SENDER }),
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
    private val indexDatabase: IndexDatabase = IndexDatabase.get(context),
    private val rulesDatabase: RulesDatabase = RulesDatabase.get(context),
    val rules: CompiledRulePack = CompiledRulePack.bundled(),
) {
    val store: TelephonyProviderStore = TelephonyProviderStore(context)
    val classifier: Classifier = Classifier(rules)

    private val dao: MessageDao get() = indexDatabase.messages()
    private val ruleDao: SenderRuleDao get() = rulesDatabase.senderRules()

    val index: MessageIndex get() = RoomMessageIndex(dao, rules.pack.version)

    fun threads(folder: Folder): Flow<List<ThreadSummary>> = dao.observeThreads(folder)

    fun conversation(threadId: Long): Flow<List<MessageEntity>> = dao.observeConversation(threadId)

    fun unreadCount(folder: Folder): Flow<Int> = dao.observeUnreadCount(folder)

    fun count(folder: Folder): Flow<Int> = dao.observeCount(folder)

    suspend fun countNow(folder: Folder): Int = dao.count(folder)

    fun userRules(): Flow<UserRules> = ruleDao.observeAll().map { it.toUserRules() }

    suspend fun currentUserRules(): UserRules = ruleDao.all().toUserRules()

    suspend fun markThreadRead(threadId: Long, folder: Folder) {
        dao.markThreadRead(threadId, folder)
        val ids = dao.providerIdsIn(threadId, folder)
        store.markRead(ids)
    }

    fun suggestions(): Flow<MoveSuggestion> = dao.observeSuggestions()

    suspend fun suggestedSample(limit: Int): List<MessageEntity> = dao.suggestedSample(limit)

    /**
     * جابه‌جایی پیامک‌های قدیمی، **فقط با تأیید صریح کاربر** (اصل ۸). جابه‌جایی
     * فقط در ایندکس است و چیزی از provider حذف نمی‌شود.
     */
    suspend fun acceptSuggestionsAfterUserConfirmation(): Int = dao.acceptSuggestions()

    suspend fun dismissSuggestions(): Int = dao.dismissSuggestions()

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
     * پنهان کردن فقط وقتی انجام می‌شود که خود کاربر `Block` کرده باشد؛ همین
     * اقدام صریح، تأیید کاربر است و برای همین `Router` با `LIVE` صدا زده می‌شود.
     */
    suspend fun reclassifyFrom(address: String) = withContext(Dispatchers.Default) {
        val userRules = currentUserRules()
        val normalized = Addresses.normalize(address)
        for (message in dao.messagesFrom(normalized)) {
            if (message.outgoing) continue
            val input = MessageInput(message.address, message.body)
            val verdict = classifier.classify(input)
            val placement = Router.route(verdict, input, userRules, Origin.LIVE)
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
     * همگام‌سازی با provider (D21، D22)، که `HistorySweep` اولین اجرا هم هست.
     *
     * 1. پیامک‌هایی که نوشتنشان در provider شکست خورده بود دوباره نوشته می‌شوند.
     * 2. هر پیامکی که در provider هست و در ایندکس نیست اضافه می‌شود، هر قدر هم
     *    قدیمی باشد ([SyncPlan]). این پیامک‌ها طبقه‌بندی می‌شوند ولی **بدون تأیید
     *    کاربر جابه‌جا نمی‌شوند** (اصل ۸)؛ `Router` این را از روی `origin` رعایت
     *    می‌کند.
     * 3. پیامک‌هایی که بیرون از اپ پاک شده‌اند از ایندکس برداشته می‌شوند.
     * 4. پیامک‌هایی که طبقه‌بندی‌شان در زمان دریافت تمام نشده بود، دوباره
     *    طبقه‌بندی می‌شوند؛ این هم آن‌ها را جابه‌جا نمی‌کند (D23).
     *
     * خروجی، تعداد پیامک‌هایی است که تازه به ایندکس اضافه شده‌اند.
     */
    suspend fun sync(): Int = withContext(Dispatchers.Default) {
        retryUnsaved()

        // ترتیب مهم است: اول ایندکس، بعد provider (توضیح در `SyncPlan`).
        val indexed = dao.providerIds(MessageEntity.KIND_SMS)
        val plan = SyncPlan.of(indexed, store.readIds())
        val origin = if (plan.firstRun) Origin.SWEEP else Origin.EXTERNAL

        val userRules = currentUserRules()
        var added = 0
        for (chunk in plan.missing.chunked(SYNC_CHUNK)) {
            val entities = store.readByIds(chunk).map { sms -> sms.toEntity(userRules, origin) }
            dao.insertMissing(entities)
            added += entities.size
        }
        for (chunk in plan.vanished.chunked(SYNC_CHUNK)) {
            dao.deleteAll(MessageEntity.KIND_SMS, chunk)
        }

        reclassifyPending(userRules)
        added
    }

    /** پیامکی که در زمان دریافت در provider نوشته نشد، حالا نوشته می‌شود (`SaveFirst`). */
    private suspend fun retryUnsaved() {
        for (message in dao.unsaved(MessageEntity.KIND_SMS)) {
            if (message.outgoing) continue
            val stored = runCatching {
                store.saveIncoming(
                    RawSms(
                        address = message.address,
                        body = message.body,
                        sentAt = message.date,
                        receivedAt = message.dateReceived,
                        subscriptionId = message.subId,
                    ),
                )
            }.getOrNull() ?: continue
            dao.upsert(message.copy(providerId = stored.providerId, threadId = stored.threadId))
            dao.delete(message.kind, message.providerId)
        }
    }

    /**
     * طبقه‌بندی دوبارهٔ پیامک‌هایی که در زمان دریافت از سقف زمانی گذشتند (D23).
     * پیامک سر جایش (`Inbox`) می‌ماند و اگر تبلیغ باشد فقط پیشنهاد جابه‌جایی
     * می‌گیرد، چون کاربر احتمالاً آن را آنجا دیده است.
     */
    private suspend fun reclassifyPending(userRules: UserRules) {
        for (message in dao.pendingClassify()) {
            if (message.outgoing) continue
            val input = MessageInput(message.address, message.body)
            val verdict = runCatching { classifier.classify(input) }.getOrNull() ?: continue
            val placement = Router.route(verdict, input, userRules, Origin.SWEEP)
            dao.upsert(
                message.copy(
                    category = verdict.category,
                    confidence = verdict.confidence,
                    reasonCode = placement.reason.code,
                    reasonArgs = placement.reason.args.joinToString(ARG_SEPARATOR),
                    rulesVersion = rules.pack.version,
                    pendingClassify = false,
                    risk = verdict.risk,
                    suggestMove = placement.suggestMove,
                ),
            )
        }
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
            normalizedAddress = Addresses.normalize(address),
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
            sendStatus = sendStatus,
            pendingClassify = false,
            risk = verdict?.risk ?: false,
            suggestMove = placement?.suggestMove ?: false,
        )
    }

    /** شناسهٔ گفتگو برای سرشماره‌ای که کاربر می‌خواهد به آن پیامک بدهد. */
    suspend fun threadIdFor(address: String): Long = store.threadIdFor(address)

    /**
     * پیامک ارسالی، پیش از ارسال، هم در provider و هم در ایندکس نوشته می‌شود
     * (`SaveFirst`). خروجی، شناسهٔ آن در provider است.
     */
    suspend fun recordOutgoing(
        address: String,
        body: String,
        subscriptionId: Int,
        folder: Folder,
    ): MessageEntity {
        val now = System.currentTimeMillis()
        val stored = store.saveOutgoing(address, body, subscriptionId, now)
        val entity = MessageEntity(
            kind = MessageEntity.KIND_SMS,
            providerId = stored.providerId,
            threadId = stored.threadId,
            address = address,
            normalizedAddress = Addresses.normalize(address),
            body = body,
            date = now,
            dateReceived = now,
            subId = subscriptionId,
            folder = folder,
            category = Category.PERSONAL,
            confidence = Confidence.LOW,
            reasonCode = ReasonCode.NOT_SURE,
            reasonArgs = "",
            rulesVersion = rules.pack.version,
            origin = Origin.LIVE,
            read = true,
            outgoing = true,
            sendStatus = SendStatus.PENDING,
            pendingClassify = false,
            risk = false,
            suggestMove = false,
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun findSms(providerId: Long): MessageEntity? = dao.find(MessageEntity.KIND_SMS, providerId)

    /** نتیجهٔ ارسال، هم در provider و هم در ایندکس. */
    suspend fun setSendStatus(providerId: Long, status: SendStatus) {
        store.setSendResult(providerId, status)
        dao.setSendStatus(providerId, status)
    }

    /**
     * «خالی کردن پوشه»: تنها حذف گروهی اپ، و فقط بعد از تأیید دومرحلهٔ کاربر
     * با نمایش تعداد (D45). اپ هرگز به ابتکار خودش پیامکی را حذف نمی‌کند
     * (ADR-0003 بند ۳). حذف واقعی است، از provider؛ فقط پیامک‌هایی که واقعاً از
     * provider حذف شدند از ایندکس هم برداشته می‌شوند.
     *
     * خروجی، تعداد پیامک‌های حذف‌شده است.
     */
    suspend fun emptyFolderAfterExplicitConfirmation(folder: Folder): Int {
        require(folder == Folder.PROMO) { "فقط پوشهٔ تبلیغات گروهی خالی می‌شود (D45)" }
        val ids = dao.providerIdsInFolder(MessageEntity.KIND_SMS, folder)
        val deleted = store.delete(ids)
        for (chunk in deleted.chunked(SYNC_CHUNK)) {
            dao.deleteAll(MessageEntity.KIND_SMS, chunk)
        }
        return deleted.size
    }

    private companion object {
        const val SYNC_CHUNK = 500
    }
}

private fun List<SenderRuleEntity>.toUserRules(): UserRules = UserRules(
    allowlist = filter { it.kind == SenderRuleKind.ALLOW }.mapTo(mutableSetOf()) { it.address },
    blocklist = filter { it.kind == SenderRuleKind.BLOCK }.mapTo(mutableSetOf()) { it.address },
)
