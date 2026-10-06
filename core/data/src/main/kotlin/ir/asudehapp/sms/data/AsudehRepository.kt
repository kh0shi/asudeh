package ir.asudehapp.sms.data

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import ir.asudehapp.sms.classifier.Classifier
import ir.asudehapp.sms.classifier.CompiledRulePack
import ir.asudehapp.sms.classifier.Router
import ir.asudehapp.sms.classifier.receive.ClassifiedMessage
import ir.asudehapp.sms.classifier.receive.MessageIndex
import ir.asudehapp.sms.classifier.receive.RawSms
import ir.asudehapp.sms.model.Addresses
import ir.asudehapp.sms.model.Category
import ir.asudehapp.sms.model.Confidence
import ir.asudehapp.sms.model.DefaultRule
import ir.asudehapp.sms.model.DefaultRules
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.InboxFilter
import ir.asudehapp.sms.model.MessageInput
import ir.asudehapp.sms.model.Origin
import ir.asudehapp.sms.model.ReasonCode
import ir.asudehapp.sms.model.SearchScope
import ir.asudehapp.sms.model.SenderPattern
import ir.asudehapp.sms.model.UserRules
import ir.asudehapp.sms.persian.KeywordMatch
import ir.asudehapp.sms.persian.SearchText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    /** برای دانستن اینکه کاربر کدام قاعدهٔ پیش‌فرض را خاموش کرده است (ADR-0012). */
    private val settings: AsudehSettings = AsudehSettings(appContext),
) {
    val store: TelephonyProviderStore = TelephonyProviderStore(appContext)
    val mms: MmsProviderStore = MmsProviderStore(appContext)
    val classifier: Classifier = Classifier(rules)

    private val dao: MessageDao get() = indexDatabase.messages()
    private val prefDao: ThreadPrefDao get() = indexDatabase.threadPrefs()
    private val trashDao: TrashDao get() = indexDatabase.trash()
    private val starredDao: StarredDao get() = indexDatabase.starred()
    private val reminderDao: ReminderDao get() = indexDatabase.reminders()
    private val scheduleDao: ScheduledMessageDao get() = indexDatabase.scheduled()
    private val ruleDao: SenderRuleDao get() = rulesDatabase.senderRules()
    private val keywordDao: KeywordRuleDao get() = rulesDatabase.keywordRules()
    private val patternDao: SenderPatternRuleDao get() = rulesDatabase.senderPatternRules()

    val index: MessageIndex get() = RoomMessageIndex(dao, rules.pack.version)

    fun threads(folder: Folder): Flow<List<ThreadSummary>> = dao.observeThreads(folder)

    /** بایگانی: خارج از سه پوشهٔ اصلی، هر جا که باشند (PARITY §الف). */
    fun archivedThreads(): Flow<List<ThreadSummary>> = dao.observeArchivedThreads()

    /**
     * همان فهرست [threads]، ولی صفحه‌به‌صفحه (Paging 3): برای رندر خود
     * `LazyColumn`، تا با هزاران گفتگو فقط ردیف‌های دیده‌شده بارگذاری/ترکیب
     * (compose) شوند. `pageSize`/`prefetchDistance` طوری تنظیم شده که یک
     * پیمایش معمولی به‌ندرت منتظر صفحهٔ بعد بماند.
     *
     * [threads] (فهرست کامل) هنوز برای «انتخاب همه» و رزولوشن نام/عکس مخاطب
     * لازم است؛ توضیح در `AsudehViewModel` و `MessageDao.observeThreads`.
     */
    fun pagedThreads(folder: Folder): Flow<PagingData<ThreadSummary>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PAGE_SIZE, enablePlaceholders = true)) {
            dao.pagingSource(folder)
        }.flow

    /** [pagedThreads] با تراشهٔ فیلتر صندوق (ROADMAP D5)؛ `ALL` همان فهرست کامل است. */
    fun pagedThreads(folder: Folder, filter: InboxFilter): Flow<PagingData<ThreadSummary>> {
        val categories = filter.categories?.map { it.name } ?: return pagedThreads(folder)
        return Pager(PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PAGE_SIZE, enablePlaceholders = true)) {
            dao.filteredPagingSource(folder, categories)
        }.flow
    }

    /** همان [archivedThreads]، صفحه‌به‌صفحه. */
    fun pagedArchivedThreads(): Flow<PagingData<ThreadSummary>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PAGE_SIZE, enablePlaceholders = true)) {
            dao.archivedPagingSource()
        }.flow

    fun conversation(threadId: Long): Flow<List<MessageEntity>> = dao.observeConversation(threadId)

    fun unreadCount(folder: Folder): Flow<Int> = dao.observeUnreadCount(folder)

    fun count(folder: Folder): Flow<Int> = dao.observeCount(folder)

    suspend fun countNow(folder: Folder): Int = dao.count(folder)

    /** فرستنده‌های یک پوشه، تازه‌ترین اول (ROADMAP E5). */
    fun senders(folder: Folder): Flow<List<FolderSender>> = dao.observeSenders(folder)

    fun userRules(): Flow<UserRules> =
        combine(
            ruleDao.observeAll(),
            keywordDao.observeAll(),
            patternDao.observeAll(),
            settings.changes(),
        ) { senders, keywords, patterns, _ ->
            buildUserRules(senders, keywords, patterns)
        }

    suspend fun currentUserRules(): UserRules = buildUserRules(ruleDao.all(), keywordDao.all(), patternDao.all())

    /** قواعد کاربر، برای صفحهٔ «قواعد من» (D45، اصل ۵). */
    fun senderRules(): Flow<List<SenderRuleEntity>> = ruleDao.observeAll()

    /** کلیدواژه‌های کاربر، برای صفحهٔ «قواعد من» (ADR-0012). */
    fun keywordRules(): Flow<List<KeywordRuleEntity>> = keywordDao.observeAll()

    /** الگوهای فرستندهٔ کاربر، برای صفحهٔ «قواعد من» (ADR-0016). */
    fun senderPatternRules(): Flow<List<SenderPatternRuleEntity>> = patternDao.observeAll()

    /**
     * قواعد کاربر و قاعده‌های پیش‌فرضِ خاموش‌نشده، یک‌جا. `Router` فرقی بین این
     * دو نمی‌گذارد؛ تفاوتشان فقط در صفحهٔ «قواعد من» دیده می‌شود (ADR-0012).
     */
    private fun buildUserRules(
        senders: List<SenderRuleEntity>,
        keywords: List<KeywordRuleEntity>,
        patterns: List<SenderPatternRuleEntity>,
    ): UserRules {
        val offKeywords = settings.disabledDefaultKeywords
        val defaultAllow = DefaultRules.ALLOW_KEYWORDS
            .filterNot { DefaultRules.allowKeywordId(it) in offKeywords }
        val defaultBlock = DefaultRules.BLOCK_KEYWORDS
            .filterNot { DefaultRules.blockKeywordId(it) in offKeywords }
        return UserRules(
            allowlist = senders.filter { it.kind == SenderRuleKind.ALLOW }.mapTo(mutableSetOf()) { it.address },
            blocklist = senders.filter { it.kind == SenderRuleKind.BLOCK }.mapTo(mutableSetOf()) { it.address },
            allowKeywords = keywords.filter { it.kind == SenderRuleKind.ALLOW }
                .mapTo(mutableSetOf()) { it.keyword } + defaultAllow,
            blockKeywords = keywords.filter { it.kind == SenderRuleKind.BLOCK }
                .mapTo(mutableSetOf()) { it.keyword } + defaultBlock,
            disabledDefaults = settings.disabledDefaultRules.mapNotNullTo(mutableSetOf()) { name ->
                runCatching { DefaultRule.valueOf(name) }.getOrNull()
            },
            allowSenderPatterns = patterns.filter { it.kind == SenderRuleKind.ALLOW }.mapTo(mutableSetOf()) { it.pattern },
            blockSenderPatterns = patterns.filter { it.kind == SenderRuleKind.BLOCK }.mapTo(mutableSetOf()) { it.pattern },
        )
    }

    /**
     * افزودن یک قاعدهٔ کلیدواژه (ADR-0012). خروجی `false` یعنی واژه بعد از
     * یکسان‌سازی چیزی برای جستجو ندارد.
     */
    suspend fun addKeywordRule(keyword: String, kind: SenderRuleKind): Boolean {
        val normalized = KeywordMatch.key(keyword)
        if (normalized.isEmpty()) return false
        keywordDao.put(
            KeywordRuleEntity(
                normalized = normalized,
                keyword = keyword.trim(),
                kind = kind,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    suspend fun forgetKeywordRule(normalized: String) {
        keywordDao.remove(normalized)
    }

    /**
     * افزودن یک الگوی فرستنده، مثل `5000*` (ADR-0016). خروجی `false` یعنی الگو
     * پذیرفتنی نیست (`SenderPattern.normalize`). مثل کلیدواژه، فقط روی پیامک‌های
     * بعدی اثر دارد و هیچ پیامکی را جابه‌جا نمی‌کند.
     */
    suspend fun addSenderPatternRule(pattern: String, kind: SenderRuleKind): Boolean {
        val prefix = SenderPattern.normalize(pattern) ?: return false
        patternDao.put(
            SenderPatternRuleEntity(
                prefix = prefix,
                pattern = pattern.trim(),
                kind = kind,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    suspend fun forgetSenderPatternRule(prefix: String) {
        patternDao.remove(prefix)
    }

    /** افزودن دستی یک سرشماره به فهرست سفید یا سیاه، از صفحهٔ «قواعد من». */
    suspend fun addSenderRule(address: String, kind: SenderRuleKind): Boolean {
        val normalized = Addresses.normalize(address.trim())
        if (normalized.isEmpty()) return false
        ruleDao.put(SenderRuleEntity(normalized, kind, System.currentTimeMillis()))
        return true
    }

    /** پیام‌هایی را که تازه خوانده شدند برمی‌گرداند، برای گزارش خوانده‌شدن MMS (ADR-0009). */
    suspend fun markThreadRead(threadId: Long, folder: Folder): List<MessageKey> {
        val keys = dao.unreadInThread(threadId, folder)
        dao.markThreadRead(threadId, folder)
        store.markRead(keys, read = true)
        return keys
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

    suspend fun setPinned(threadId: Long, pinned: Boolean) = updatePref(threadId) { it.copy(pinned = pinned) }

    suspend fun draft(threadId: Long): String = prefDao.get(threadId)?.draft.orEmpty()

    suspend fun isPinned(threadId: Long): Boolean = prefDao.get(threadId)?.pinned ?: false

    /** بی‌صدا کردن یک گفتگو: پیامک‌های تازه‌اش هیچ اعلانی نمی‌گیرند، ولی جایی پنهان نمی‌شوند. */
    suspend fun setMuted(threadId: Long, muted: Boolean) = updatePref(threadId) { it.copy(muted = muted) }

    suspend fun isMuted(threadId: Long): Boolean = prefDao.get(threadId)?.muted ?: false

    /** بایگانی: گفتگو از فهرست پوشه‌اش برداشته می‌شود، ولی جایی حذف نمی‌شود. */
    suspend fun setArchived(threadId: Long, archived: Boolean) = updatePref(threadId) { it.copy(archived = archived) }

    suspend fun isArchived(threadId: Long): Boolean = prefDao.get(threadId)?.archived ?: false

    /** پیش‌نویس گفتگو (D53). متن خالی پیش‌نویس را برمی‌دارد. */
    suspend fun saveDraft(threadId: Long, text: String) {
        if (threadId <= 0 || draft(threadId) == text) return
        updatePref(threadId) { it.copy(draft = text) }
    }

    /**
     * سیم‌کارتی که این گفتگو آخرین بار با آن فرستاده شد (ROADMAP E7)؛ جعبهٔ
     * نوشتن همان را پیش‌فرض می‌گیرد. منفی یعنی چیزی ذخیره نشده است.
     */
    suspend fun lastSendSim(threadId: Long): Int = prefDao.get(threadId)?.subId ?: -1

    suspend fun rememberSendSim(threadId: Long, subId: Int) {
        if (threadId <= 0 || subId < 0) return
        updatePref(threadId) { it.copy(subId = subId) }
    }

    /** رنگ حباب همین گفتگو (ROADMAP E8)؛ منفی یعنی رنگ سراسری. */
    suspend fun bubbleColor(threadId: Long): Int = prefDao.get(threadId)?.bubbleColor ?: -1

    suspend fun setBubbleColor(threadId: Long, index: Int) {
        if (threadId <= 0) return
        updatePref(threadId) { it.copy(bubbleColor = index.coerceAtLeast(-1)) }
    }

    /** نام گفتگوی گروهی (PARITY §ب-۸)؛ خالی یعنی نام طرف‌ها. */
    suspend fun threadTitle(threadId: Long): String = prefDao.get(threadId)?.title.orEmpty()

    suspend fun setThreadTitle(threadId: Long, title: String) {
        if (threadId <= 0) return
        updatePref(threadId) { it.copy(title = title.trim().take(MAX_TITLE)) }
    }

    /** گفتگوهای نام‌دار با طرف‌هایشان، برای پشتیبان؛ شناسهٔ گفتگو در گوشی دیگر فرق دارد. */
    suspend fun titledThreads(): List<Pair<List<String>, String>> = prefDao.titled().mapNotNull { pref ->
        val last = dao.latestInThread(pref.threadId) ?: return@mapNotNull null
        last.participants to pref.title
    }

    /** ردیفی که همه‌اش پیش‌فرض است نگه داشته نمی‌شود. */
    private suspend fun updatePref(threadId: Long, change: (ThreadPrefEntity) -> ThreadPrefEntity) {
        val current = prefDao.get(threadId) ?: ThreadPrefEntity(threadId, pinned = false, draft = "", updatedAt = 0)
        val next = change(current).copy(updatedAt = System.currentTimeMillis())
        if (next.isDefault) prefDao.remove(threadId) else prefDao.put(next)
    }

    /**
     * جستجو در همهٔ پیامک‌ها، از هر سه پوشه (D20). خروجی خالی یعنی پرسش چیزی
     * برای جستجو نداشت یا چیزی پیدا نشد.
     */
    suspend fun search(query: String, limit: Int = SEARCH_LIMIT): List<MessageEntity> {
        val match = SearchText.ftsQuery(query) ?: return emptyList()
        return runCatching { dao.search(match, limit) }.getOrDefault(emptyList())
    }

    /**
     * جستجو با دامنه (ROADMAP E6). متن فقط از `SearchText.ftsQuery` به `MATCH`
     * می‌رسد؛ دامنه پارامتر جداست. بدون متن، فقط دامنهٔ محدودشده نتیجه دارد.
     */
    suspend fun search(query: String, scope: SearchScope, limit: Int = SEARCH_LIMIT): List<MessageEntity> {
        val match = SearchText.ftsQuery(query)
        if (match == null && !scope.narrowed) return emptyList()
        return runCatching {
            if (match != null) {
                dao.searchScoped(
                    match, scope.threadId, scope.senderKey, scope.onlyAttachments, scope.onlyHidden,
                    scope.categories, limit,
                )
            } else {
                dao.filterScoped(
                    scope.threadId, scope.senderKey, scope.onlyAttachments, scope.onlyHidden, scope.categories, limit,
                )
            }
        }.getOrDefault(emptyList())
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
     * خروجی، تعداد پیامک‌هایی است که تازه به ایندکس اضافه شده‌اند. [onProgress]
     * بعد از هر تکه صدا زده می‌شود (D48). هر تکه پیش از تکهٔ بعد نوشته می‌شود،
     * پس اگر اپ وسط کار بسته شود، اجرای بعدی از همان‌جا ادامه می‌دهد (ADR-0015).
     */
    suspend fun sync(onProgress: (SyncProgress) -> Unit = {}): Int = withContext(Dispatchers.Default) {
        backfillSearchText()
        retryUnsaved()

        // ترتیب مهم است: اول ایندکس، بعد provider (توضیح در `SyncPlan`).
        val indexed = dao.providerIds(MessageEntity.KIND_SMS)
        val plan = SyncPlan.of(indexed, store.readIds())
        val origin = if (plan.firstRun) Origin.SWEEP else Origin.EXTERNAL
        val mmsPlan = mmsPlan()
        val total = plan.missing.size + (mmsPlan?.missing?.size ?: 0)
        onProgress(SyncProgress(0, total))

        val userRules = currentUserRules()
        var added = 0
        for (chunk in plan.missing.chunked(SYNC_CHUNK)) {
            val entities = store.readByIds(chunk).map { sms -> sms.toEntity(userRules, origin) }
            dao.insertMissing(entities)
            added += entities.size
            onProgress(SyncProgress(added, total))
        }
        for (chunk in plan.vanished.chunked(SYNC_CHUNK)) {
            dao.deleteAll(MessageEntity.KIND_SMS, chunk)
        }

        if (mmsPlan != null) {
            added += syncMms(mmsPlan, userRules, origin) { mmsAdded -> onProgress(SyncProgress(added + mmsAdded, total)) }
        }
        reclassifyPending(userRules)
        added
    }

    /** مقایسهٔ ایندکس MMS با provider؛ اگر provider MMS خوانده نشد `null`. */
    private suspend fun mmsPlan(): SyncPlan? {
        val indexed = dao.providerIds(MessageEntity.KIND_MMS)
        val providerIds = runCatching { mms.readIds() }.getOrNull() ?: return null
        return SyncPlan.of(indexed, providerIds)
    }

    /**
     * همان همگام‌سازی برای MMS (D26). طرف‌های گروه از خود provider می‌آیند.
     * MMSی که فقط اعلانش بود و حالا دریافت شده (مثلاً با اپ دیگری)، به‌روز
     * می‌شود.
     */
    private suspend fun syncMms(
        plan: SyncPlan,
        userRules: UserRules,
        sweepOrigin: Origin,
        onAdded: (Int) -> Unit,
    ): Int {
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
            onAdded(added)
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

    // ——— حذف، با سطل بازیافت (ADR-0010) ———

    // ——— نشان‌ها (ROADMAP D6) ———

    fun starredMessages(): Flow<List<MessageEntity>> = starredDao.observeStarred()

    fun starredKeys(): Flow<Set<MessageKey>> =
        starredDao.observeAll().map { rows -> rows.map { MessageKey(it.kind, it.providerId) }.toSet() }

    suspend fun setStarred(key: MessageKey, starred: Boolean) {
        if (starred) {
            starredDao.put(listOf(StarredMessageEntity(key.kind, key.providerId, System.currentTimeMillis())))
        } else {
            starredDao.remove(key.kind, key.providerId)
        }
    }

    /** برای پشتیبان: شناسه‌های نشان‌دار یک نوع پیام. */
    suspend fun starredIds(kind: String): Set<Long> = starredDao.providerIds(kind).toSet()

    /** پس از بازگردانی پشتیبان، نشان پیام‌های تازه‌نوشته‌شده برمی‌گردد. */
    suspend fun restoreStars(kind: String, providerIds: Collection<Long>) {
        if (providerIds.isEmpty()) return
        val now = System.currentTimeMillis()
        starredDao.put(providerIds.map { StarredMessageEntity(kind, it, now) })
    }

    fun trash(): Flow<List<TrashedMessageEntity>> = trashDao.observeAll()

    fun trashCount(): Flow<Int> = trashDao.observeCount()

    /**
     * حذف پیامک‌های انتخاب‌شده، **فقط با اقدام صریح کاربر** (ADR-0003 بند ۳).
     *
     * ترتیب کارها همان `SaveFirst` است، وارونه: اول نسخهٔ سطل نوشته می‌شود و
     * بعد پیامک از provider برداشته می‌شود. اگر بین این دو، اپ کشته شود، بدترین
     * حالت یک ردیف اضافه در سطل است، نه یک پیامک گم‌شده.
     *
     * پیامکی که provider آن را حذف نکرد (مثلاً چون اپ پیش‌فرض نیست) سر جایش
     * می‌ماند و نسخهٔ سطلش هم برداشته می‌شود، تا دوتایی دیده نشود.
     *
     * خروجی، شناسهٔ ردیف‌های سطل است، تا رابط بتواند «بازگرداندن» را همان‌جا
     * پیشنهاد بدهد.
     */
    suspend fun deleteAfterExplicitConfirmation(messages: List<MessageEntity>): List<Long> {
        if (messages.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val trashIds = trashDao.put(messages.map { it.toTrashed(now) })

        val deleted = HashSet<MessageKey>()
        for ((kind, group) in messages.groupBy { it.kind }) {
            // پیامکی که هرگز در provider نوشته نشد (شناسهٔ منفی) فقط در ایندکس است.
            val unsaved = group.filter { it.providerId <= 0 }
            for (message in unsaved) {
                dao.delete(message.kind, message.providerId)
                deleted += MessageKey(message.kind, message.providerId)
            }
            val ids = group.filter { it.providerId > 0 }.map { it.providerId }
            if (ids.isEmpty()) continue
            val gone = if (kind == MessageEntity.KIND_MMS) mms.delete(ids) else store.delete(ids)
            for (chunk in gone.chunked(SYNC_CHUNK)) dao.deleteAll(kind, chunk)
            for (id in gone) deleted += MessageKey(kind, id)
        }

        val (kept, orphans) = messages.indices
            .mapNotNull { index -> trashIds.getOrNull(index)?.let { index to it } }
            .partition { (index, _) ->
                MessageKey(messages[index].kind, messages[index].providerId) in deleted
            }
        if (orphans.isNotEmpty()) trashDao.removeAll(orphans.map { it.second })
        return kept.map { it.second }
    }

    /** «بازگرداندن» بلافاصله پس از حذف: همهٔ ردیف‌های همان حذف برمی‌گردند. */
    suspend fun restoreAllFromTrash(ids: List<Long>): Int = ids.count { restoreFromTrash(it) }

    /** پیامک‌های یک گفتگو در یک پوشه؛ [folder] خالی یعنی کل گفتگو. */
    suspend fun messagesOfThread(threadId: Long, folder: Folder?): List<MessageEntity> =
        if (folder == null) dao.allInThread(threadId) else dao.inThread(threadId, folder)

    /**
     * بازگرداندن یک پیامک از سطل: دوباره در provider نوشته می‌شود و به همان
     * پوشه‌ای برمی‌گردد که پیش از حذف در آن بود. شناسهٔ تازه می‌گیرد، چون
     * شناسهٔ قبلی دیگر مال کسی نیست.
     */
    suspend fun restoreFromTrash(id: Long): Boolean {
        val row = trashDao.get(id) ?: return false
        if (!row.restorable) return false
        val stored = runCatching {
            if (row.outgoing) {
                store.saveOutgoing(row.address, row.body, row.subId, row.date)
            } else {
                store.saveIncoming(
                    RawSms(
                        address = row.address,
                        body = row.body,
                        sentAt = row.date,
                        receivedAt = row.dateReceived,
                        subscriptionId = row.subId,
                    ),
                )
            }
        }.getOrNull() ?: return false
        if (row.outgoing) store.setSendResult(stored.providerId, SendStatus.SENT)
        if (row.read) store.markRead(listOf(MessageKey(row.kind, stored.providerId)), read = true)

        val input = MessageInput(row.address, row.body)
        val verdict = if (row.outgoing) null else runCatching { classifier.classify(input) }.getOrNull()
        dao.upsert(
            MessageEntity(
                kind = row.kind,
                providerId = stored.providerId,
                threadId = stored.threadId,
                address = row.address,
                normalizedAddress = Addresses.normalize(row.address),
                body = row.body,
                date = row.date,
                dateReceived = row.dateReceived,
                subId = row.subId,
                // جای پیامک، انتخاب قبلی خود کاربر است و دوباره پرسیده نمی‌شود (اصل ۸).
                folder = row.folder,
                category = verdict?.category ?: Category.PERSONAL,
                confidence = verdict?.confidence ?: Confidence.LOW,
                reasonCode = ReasonCode.NOT_SURE,
                reasonArgs = "",
                rulesVersion = rules.pack.version,
                origin = Origin.EXTERNAL,
                read = row.read,
                outgoing = row.outgoing,
                sendStatus = if (row.outgoing) SendStatus.SENT else SendStatus.NONE,
                pendingClassify = false,
                risk = verdict?.risk ?: false,
                suggestMove = false,
                recipients = row.recipients,
                attachments = 0,
            ),
        )
        trashDao.remove(id)
        return true
    }

    /** «خالی کردن سطل»: حذف قطعی، فقط بعد از تأیید دومرحلهٔ کاربر. */
    suspend fun emptyTrashAfterExplicitConfirmation(): Int = trashDao.clear()

    // ——— ارسال زمان‌بندی‌شده (ADR-0011) ———

    fun scheduledFor(threadId: Long): Flow<List<ScheduledMessageEntity>> =
        scheduleDao.observeForThread(threadId)

    fun allScheduled(): Flow<List<ScheduledMessageEntity>> = scheduleDao.observeAll()

    /** خروجی، شناسهٔ پیامک زمان‌بندی‌شده است. */
    suspend fun schedule(
        threadId: Long,
        recipients: List<String>,
        body: String,
        subscriptionId: Int,
        sendAt: Long,
    ): ScheduledMessageEntity {
        val entity = ScheduledMessageEntity(
            threadId = threadId,
            recipients = recipients.joinToString(MessageEntity.RECIPIENT_SEPARATOR),
            body = body,
            subId = subscriptionId,
            sendAt = sendAt,
            createdAt = System.currentTimeMillis(),
            state = ScheduleState.WAITING,
        )
        return entity.copy(id = scheduleDao.put(entity))
    }

    suspend fun scheduledMessage(id: Long): ScheduledMessageEntity? = scheduleDao.get(id)

    suspend fun dueScheduled(now: Long): List<ScheduledMessageEntity> = scheduleDao.due(now)

    suspend fun waitingScheduled(): List<ScheduledMessageEntity> = scheduleDao.waiting()

    suspend fun nextScheduledAfter(now: Long): Long? = scheduleDao.nextAfter(now)

    /** شمار پیامک‌های یک گفتگو در ایندکس؛ صفر یعنی گفتگو خالی شده است. */
    suspend fun threadMessageCount(threadId: Long): Int = dao.countInThread(threadId)

    // ——— «یادم بینداز» (ROADMAP E1) ———

    suspend fun addReminder(message: MessageEntity, at: Long): Long = reminderDao.insert(
        ReminderEntity(kind = message.kind, providerId = message.providerId, threadId = message.threadId, remindAt = at),
    )

    suspend fun dueReminders(now: Long): List<ReminderEntity> = reminderDao.due(now)

    suspend fun nextReminderAfter(now: Long): Long? = reminderDao.nextAfter(now)

    suspend fun removeReminder(id: Long) = reminderDao.remove(id)

    fun remindersFor(threadId: Long): Flow<List<ReminderEntity>> = reminderDao.observeForThread(threadId)

    suspend fun markScheduleMissed(id: Long) = scheduleDao.setState(id, ScheduleState.MISSED)

    /** لغو یا برداشتن یک پیامک زمان‌بندی‌شده. متن به پیش‌نویس برمی‌گردد. */
    suspend fun removeScheduled(id: Long) = scheduleDao.remove(id)

    private companion object {
        const val SYNC_CHUNK = 500
        const val SEARCH_LIMIT = 200
        const val PAGE_SIZE = 30

        /** نام گفتگو کوتاه است؛ متن بلندتر بریده می‌شود، نه رد. */
        const val MAX_TITLE = 60
    }
}

/** نسخهٔ سطل از یک پیامک، پیش از حذف شدنش از provider (ADR-0010). */
private fun MessageEntity.toTrashed(deletedAt: Long): TrashedMessageEntity = TrashedMessageEntity(
    kind = kind,
    providerId = providerId,
    address = address,
    recipients = recipients,
    body = body,
    date = date,
    dateReceived = dateReceived,
    subId = subId,
    folder = folder,
    read = read,
    outgoing = outgoing,
    attachments = attachments,
    deletedAt = deletedAt,
)


