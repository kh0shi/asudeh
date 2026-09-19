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
import ir.asudehapp.sms.persian.SearchText
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
    kind = if (raw.isMms) MessageEntity.KIND_MMS else MessageEntity.KIND_SMS,
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
    recipients = if (raw.isGroup) raw.recipients.joinToString(MessageEntity.RECIPIENT_SEPARATOR) else "",
    attachments = raw.attachments,
)

/**
 * تنها راه رسیدن رابط کاربری به داده‌ها. همهٔ صفحه‌ها فقط از ایندکس محلی
 * می‌خوانند (D19 ب)، و منبع حقیقت همچنان Telephony Provider سیستم است.
 */
class AsudehRepository(
    private val appContext: Context,
    private val indexDatabase: IndexDatabase = IndexDatabase.get(appContext),
    private val rulesDatabase: RulesDatabase = RulesDatabase.get(appContext),
    val rules: CompiledRulePack = CompiledRulePack.bundled(),
) {
    val store: TelephonyProviderStore = TelephonyProviderStore(appContext)
    val mms: MmsProviderStore = MmsProviderStore(appContext)
    val classifier: Classifier = Classifier(rules)

    private val dao: MessageDao get() = indexDatabase.messages()
    private val prefDao: ThreadPrefDao get() = indexDatabase.threadPrefs()
    private val ruleDao: SenderRuleDao get() = rulesDatabase.senderRules()

    val index: MessageIndex get() = RoomMessageIndex(dao, rules.pack.version)

    fun threads(folder: Folder): Flow<List<ThreadSummary>> = dao.observeThreads(folder)

    fun conversation(threadId: Long): Flow<List<MessageEntity>> = dao.observeConversation(threadId)

    fun unreadCount(folder: Folder): Flow<Int> = dao.observeUnreadCount(folder)

    fun count(folder: Folder): Flow<Int> = dao.observeCount(folder)

    suspend fun countNow(folder: Folder): Int = dao.count(folder)

    fun userRules(): Flow<UserRules> = ruleDao.observeAll().map { it.toUserRules() }

    suspend fun currentUserRules(): UserRules = ruleDao.all().toUserRules()

    /** قواعد کاربر، برای صفحهٔ «قواعد من» (D45، اصل ۵). */
    fun senderRules(): Flow<List<SenderRuleEntity>> = ruleDao.observeAll()

    suspend fun markThreadRead(threadId: Long, folder: Folder) {
        val keys = dao.unreadInThread(threadId, folder)
        dao.markThreadRead(threadId, folder)
        store.markRead(keys, read = true)
    }

    /** «همه خوانده شد» در `PromoFolder` (D45). */
    suspend fun markFolderRead(folder: Folder) {
        val keys = dao.unreadIn(folder)
        dao.markFolderRead(folder)
        store.markRead(keys, read = true)
    }

    /**
     * «علامت خوانده‌نشده» (D53): آخرین پیامک دریافتی گفتگو دوباره خوانده‌نشده
     * می‌شود، هم در ایندکس و هم در provider.
     */
    suspend fun markThreadUnread(threadId: Long, folder: Folder) {
        val last = dao.lastIncoming(threadId, folder) ?: return
        dao.setRead(last.kind, last.providerId, false)
        if (last.providerId > 0) {
            store.markRead(listOf(MessageKey(last.kind, last.providerId)), read = false)
        }
    }

    suspend fun setPinned(threadId: Long, pinned: Boolean) {
        val current = prefDao.get(threadId)
        savePref(threadId, pinned = pinned, draft = current?.draft.orEmpty())
    }

    suspend fun draft(threadId: Long): String = prefDao.get(threadId)?.draft.orEmpty()

    suspend fun isPinned(threadId: Long): Boolean = prefDao.get(threadId)?.pinned ?: false

    /** پیش‌نویس گفتگو (D53). متن خالی پیش‌نویس را برمی‌دارد. */
    suspend fun saveDraft(threadId: Long, text: String) {
        if (threadId <= 0) return
        val current = prefDao.get(threadId)
        if ((current?.draft ?: "") == text) return
        savePref(threadId, pinned = current?.pinned ?: false, draft = text)
    }

    private suspend fun savePref(threadId: Long, pinned: Boolean, draft: String) {
        if (!pinned && draft.isEmpty()) {
            prefDao.remove(threadId)
        } else {
            prefDao.put(ThreadPrefEntity(threadId, pinned, draft, System.currentTimeMillis()))
        }
    }

    /**
     * جستجو در همهٔ پیامک‌ها، از هر سه پوشه (D20). خروجی خالی یعنی پرسش چیزی
     * برای جستجو نداشت یا چیزی پیدا نشد.
     */
    suspend fun search(query: String, limit: Int = SEARCH_LIMIT): List<MessageEntity> {
        val match = SearchText.ftsQuery(query) ?: return emptyList()
        return runCatching { dao.search(match, limit) }.getOrDefault(emptyList())
    }

    /** شمار پیامک‌های پنهان‌شده از [since]، برای `Digest` (D40). */
    suspend fun hiddenSince(since: Long): HiddenCount = dao.hiddenSince(since)

    /** متن جستجوی پیامک‌هایی که پیش از نسخهٔ ۲ ایندکس شده بودند. */
    private suspend fun backfillSearchText() {
        while (true) {
            val batch = dao.missingSearchText(SYNC_CHUNK)
            if (batch.isEmpty()) return
            for (message in batch) {
                val text = SearchText.of(message.body, message.address).ifEmpty { " " }
                dao.setSearchText(message.kind, message.providerId, text)
            }
        }
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
            // گفتگوی گروهی هرگز پنهان نمی‌شود (D26)، حتی با `Block`.
            if (message.outgoing || message.isGroup || message.pendingDownload) continue
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
        backfillSearchText()
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

        added += syncMms(userRules, origin)
        reclassifyPending(userRules)
        added
    }

    /**
     * همان همگام‌سازی برای MMS (D26). طرف‌های گروه از خود provider می‌آیند.
     * MMSی که فقط اعلانش بود و حالا دریافت شده (مثلاً با اپ دیگری)، به‌روز
     * می‌شود.
     */
    private suspend fun syncMms(userRules: UserRules, sweepOrigin: Origin): Int {
        val indexed = dao.providerIds(MessageEntity.KIND_MMS)
        val providerIds = runCatching { mms.readIds() }.getOrNull() ?: return 0
        val plan = SyncPlan.of(indexed, providerIds)
        val pending = dao.pendingDownloads().filter { it.providerId > 0 }
        if (plan.missing.isEmpty() && plan.vanished.isEmpty() && pending.isEmpty()) return 0

        val members = mms.threadRecipients()
        // اولین همگام‌سازی MMS همان `HistorySweep` است، حتی اگر پیامک‌ها قبلاً ایندکس شده باشند.
        val origin = if (plan.firstRun) Origin.SWEEP else sweepOrigin
        var added = 0
        for (chunk in plan.missing.chunked(SYNC_CHUNK)) {
            val entities = mms.readByIds(chunk, members).map { it.toEntity(userRules, origin) }
            dao.insertMissing(entities)
            added += entities.size
        }
        for (chunk in plan.vanished.chunked(SYNC_CHUNK)) {
            dao.deleteAll(MessageEntity.KIND_MMS, chunk)
        }
        val refreshed = mms.readByIds(pending.map { it.providerId }, members).associateBy { it.id }
        for (old in pending) {
            val now = refreshed[old.providerId] ?: continue
            if (now.pendingDownload) continue
            dao.upsert(now.toEntity(userRules, Origin.EXTERNAL).copy(folder = old.folder, read = old.read))
        }
        return added
    }

    private fun ProviderMms.toEntity(userRules: UserRules, origin: Origin): MessageEntity {
        val input = MessageInput(address, body)
        val group = recipients.size > 1
        // MMS گروهی و MMS بی‌متن طبقه‌بندی نمی‌شوند: گروه هرگز پنهان نمی‌شود (D26).
        val verdict = if (outgoing || group || body.isBlank()) null else classifier.classify(input)
        val placement = verdict?.let { Router.route(it, input, userRules, origin) }
        return MessageEntity(
            kind = MessageEntity.KIND_MMS,
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
            recipients = if (group) recipients.joinToString(MessageEntity.RECIPIENT_SEPARATOR) else "",
            attachments = attachments,
            pendingDownload = pendingDownload,
        )
    }

    /**
     * اعلان MMS در ایندکس، پیش از دریافت خود پیام. در گفتگو با «در حال
     * دریافت» یا دکمهٔ «دریافت» دیده می‌شود، پس اگر دریافت شکست بخورد هم گم
     * نمی‌شود.
     */
    suspend fun recordMmsNotification(stored: StoredMms, from: String, subject: String?, subscriptionId: Int, now: Long) {
        val address = from.ifBlank { TelephonyProviderStore.UNKNOWN_SENDER }
        dao.upsert(
            MessageEntity(
                kind = MessageEntity.KIND_MMS,
                providerId = stored.providerId,
                threadId = stored.threadId,
                address = address,
                normalizedAddress = Addresses.normalize(address),
                body = subject.orEmpty(),
                date = now,
                dateReceived = now,
                subId = subscriptionId,
                folder = Folder.INBOX,
                category = Category.UNKNOWN,
                confidence = Confidence.LOW,
                reasonCode = ReasonCode.NOT_SURE,
                reasonArgs = "",
                rulesVersion = rules.pack.version,
                origin = Origin.LIVE,
                read = false,
                outgoing = false,
                pendingClassify = false,
                risk = false,
                suggestMove = false,
                pendingDownload = true,
            ),
        )
    }

    /** MMS ارسالی، پیش از ارسال، در provider و ایندکس (`SaveFirst`). */
    suspend fun recordOutgoingMms(
        recipients: List<String>,
        text: String?,
        attachments: List<ir.asudehapp.sms.mms.MmsPart>,
        subscriptionId: Int,
    ): MessageEntity {
        val now = System.currentTimeMillis()
        val stored = mms.saveOutgoing(recipients, text, attachments, subscriptionId, now)
        val address = recipients.first()
        val entity = MessageEntity(
            kind = MessageEntity.KIND_MMS,
            providerId = stored.providerId,
            threadId = stored.threadId,
            address = address,
            normalizedAddress = Addresses.normalize(address),
            body = text.orEmpty(),
            date = now,
            dateReceived = now,
            subId = subscriptionId,
            folder = Folder.INBOX,
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
            recipients = if (recipients.size > 1) recipients.joinToString(MessageEntity.RECIPIENT_SEPARATOR) else "",
            attachments = attachments.size,
        )
        dao.upsert(entity)
        return entity
    }

    /** نتیجهٔ ارسال MMS، هم در provider و هم در ایندکس. */
    suspend fun setMmsSendStatus(providerId: Long, status: SendStatus, messageId: String? = null) {
        mms.setBox(providerId, status, messageId)
        dao.setSendStatus(MessageEntity.KIND_MMS, providerId, status)
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
            if (message.outgoing || message.isGroup) continue
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

    /** شناسهٔ گفتگوی گروهی. */
    suspend fun threadIdFor(addresses: Set<String>): Long = withContext(Dispatchers.IO) {
        android.provider.Telephony.Threads.getOrCreateThreadId(appContext, addresses)
    }

    /** جای پیامک‌های پنهان، برای پشتیبان (D57). */
    suspend fun foldersByProviderId(kind: String): Map<Long, Folder> =
        dao.hiddenPlacements(kind).associate { it.providerId to it.folder }

    /**
     * برگرداندن جایی که کاربر پیش از پشتیبان‌گیری برای پیامک‌ها انتخاب کرده بود.
     * این انتخاب خود کاربر است، پس تأیید دوباره نمی‌خواهد (اصل ۸).
     */
    suspend fun restoreFolders(kind: String, placements: Map<Long, Folder>) {
        for ((providerId, folder) in placements) dao.moveMessage(kind, providerId, folder)
    }

    /** نوشتن یک پیامک در ایندکس، برای MMS که پس از دریافت کامل می‌شود. */
    suspend fun upsert(message: MessageEntity) = dao.upsert(message)

    /** پیامک‌های یک گفتگو، یک بار (نه به‌صورت Flow). */
    suspend fun conversationNow(threadId: Long): List<MessageEntity> = dao.conversationNow(threadId)

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

    suspend fun find(kind: String, providerId: Long): MessageEntity? = dao.find(kind, providerId)

    /** نتیجهٔ ارسال پیامک، هم در provider و هم در ایندکس. */
    suspend fun setSendStatus(providerId: Long, status: SendStatus) {
        store.setSendResult(providerId, status)
        dao.setSendStatus(MessageEntity.KIND_SMS, providerId, status)
    }

    /** وضعیت ارسال در ایندکس، برای MMS که provider آن را جدا ثبت می‌کند. */
    suspend fun setIndexedSendStatus(kind: String, providerId: Long, status: SendStatus) {
        dao.setSendStatus(kind, providerId, status)
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
        val mmsIds = dao.providerIdsInFolder(MessageEntity.KIND_MMS, folder)
        val deletedMms = mms.delete(mmsIds)
        for (chunk in deletedMms.chunked(SYNC_CHUNK)) {
            dao.deleteAll(MessageEntity.KIND_MMS, chunk)
        }
        return deleted.size + deletedMms.size
    }

    private companion object {
        const val SYNC_CHUNK = 500
        const val SEARCH_LIMIT = 200
    }
}

private fun List<SenderRuleEntity>.toUserRules(): UserRules = UserRules(
    allowlist = filter { it.kind == SenderRuleKind.ALLOW }.mapTo(mutableSetOf()) { it.address },
    blocklist = filter { it.kind == SenderRuleKind.BLOCK }.mapTo(mutableSetOf()) { it.address },
)
