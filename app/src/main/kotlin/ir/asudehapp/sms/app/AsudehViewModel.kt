package ir.asudehapp.sms.app

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.asudehapp.sms.data.BackupException
import ir.asudehapp.sms.data.DigestFrequency
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.MmsPartInfo
import ir.asudehapp.sms.data.MoveSuggestion
import ir.asudehapp.sms.data.SenderRuleEntity
import ir.asudehapp.sms.data.ThemeMode
import ir.asudehapp.sms.data.ThreadSummary
import ir.asudehapp.sms.mms.MmsPart
import ir.asudehapp.sms.model.Addresses
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.telephony.Contacts
import ir.asudehapp.sms.telephony.DigestScheduler
import ir.asudehapp.sms.telephony.MmsDownloader
import ir.asudehapp.sms.telephony.MmsImages
import ir.asudehapp.sms.telephony.SimCard
import ir.asudehapp.sms.telephony.SimCards
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ناوبری با یک پشتهٔ ساده از مقصدها که در ViewModel نگه داشته می‌شود و با
 * چرخش صفحه از دست نمی‌رود (D56، STATUS).
 */
sealed interface Destination {
    data object Home : Destination
    data class FolderView(val folder: Folder) : Destination

    /**
     * [recipients] برای گفتگوی تازه‌ای است که هنوز پیامکی ندارد (`sms:` یا
     * «گفتگوی تازه»)؛ برای گروه بیش از یکی.
     */
    data class Conversation(
        val threadId: Long,
        val folder: Folder,
        val recipients: List<String> = emptyList(),
    ) : Destination

    data object Search : Destination
    data object Settings : Destination
    data object Rules : Destination
    data object NewConversation : Destination
}

/** یک `UiState` تغییرناپذیر برای هر صفحه (D56). */
data class ConversationUiState(
    val threadId: Long = 0,
    val folder: Folder = Folder.INBOX,
    /** طرف‌های دیگر گفتگو؛ برای گروه بیش از یکی. */
    val participants: List<String> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val isMixedSender: Boolean = false,
    val isAdLine: Boolean = false,
    val pinned: Boolean = false,
    /** پاسخ از سیم‌کارتی می‌رود که پیامک آخر به آن رسیده است، مگر کاربر عوضش کند (D27). */
    val subscriptionId: Int = -1,
) {
    val address: String get() = participants.firstOrNull().orEmpty()
    val isGroup: Boolean get() = participants.size > 1
}

/** پیوستی که کاربر انتخاب کرده و هنوز فرستاده نشده است. */
data class PendingAttachment(val uri: Uri, val part: MmsPart)

/** پرسشی که بعد از `Rescue` پرسیده می‌شود (ADR-0006 بند ۱). */
data class RescueFollowUp(
    val address: String,
    /** برای `MixedSender` پاسخ برجسته «فقط همین» است. */
    val mixedSender: Boolean,
)

/** «خالی کردن پوشه»: دو مرحله، با تعداد (D45). */
data class EmptyFolderRequest(
    val folder: Folder,
    val count: Int,
    /** مرحلهٔ دوم، یعنی تأیید نهایی. */
    val confirmed: Boolean = false,
)

/** تنظیمات، به‌صورت یک عکس فوری برای رابط (D52). */
data class SettingsState(
    val digest: DigestFrequency = DigestFrequency.DAILY,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val persianDigits: Boolean = true,
    val deliveryReports: Boolean = false,
    val showReasonEverywhere: Boolean = false,
)

/** پیام کوتاهی که پس از یک کار نشان داده می‌شود (Snackbar). */
sealed interface UiNotice {
    data class BackupExported(val messages: Int) : UiNotice
    data class BackupImported(val added: Int, val duplicates: Int, val corrupt: Int) : UiNotice
    data class BackupFailed(val reason: BackupException.Reason?) : UiNotice
    data object AttachmentFailed : UiNotice
    data object DownloadRequested : UiNotice
    data object DownloadFailed : UiNotice
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AsudehViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AsudehApplication.containerOf(application)
    private val repository = container.repository
    private val settingsStore = container.settings

    private val _stack = MutableStateFlow<List<Destination>>(listOf(Destination.Home))
    val destination: StateFlow<Destination> = _stack
        .map { it.last() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Destination.Home)

    val inboxThreads: StateFlow<List<ThreadSummary>> = threadsIn(Folder.INBOX)
    val promoThreads: StateFlow<List<ThreadSummary>> = threadsIn(Folder.PROMO)
    val scamThreads: StateFlow<List<ThreadSummary>> = threadsIn(Folder.SCAM)

    val promoUnread: StateFlow<Int> = repository.unreadCount(Folder.PROMO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), 0)
    val scamCount: StateFlow<Int> = repository.count(Folder.SCAM)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), 0)

    private val _conversation = MutableStateFlow(ConversationUiState())
    val conversation: StateFlow<ConversationUiState> = _conversation.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _attachments = MutableStateFlow<List<PendingAttachment>>(emptyList())
    val attachments: StateFlow<List<PendingAttachment>> = _attachments.asStateFlow()

    private val _preparingAttachment = MutableStateFlow(false)
    val preparingAttachment: StateFlow<Boolean> = _preparingAttachment.asStateFlow()

    /** خطایی که باید به کاربر نشان داده شود؛ بعد از نمایش پاک می‌شود. */
    private val _sendError = MutableStateFlow(false)
    val sendError: StateFlow<Boolean> = _sendError.asStateFlow()

    private val _notice = MutableStateFlow<UiNotice?>(null)
    val notice: StateFlow<UiNotice?> = _notice.asStateFlow()

    private val _rescueFollowUp = MutableStateFlow<RescueFollowUp?>(null)
    val rescueFollowUp: StateFlow<RescueFollowUp?> = _rescueFollowUp.asStateFlow()

    private val _emptyFolder = MutableStateFlow<EmptyFolderRequest?>(null)
    val emptyFolder: StateFlow<EmptyFolderRequest?> = _emptyFolder.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncFailed = MutableStateFlow(false)
    val syncFailed: StateFlow<Boolean> = _syncFailed.asStateFlow()

    val rulesVersion: String = repository.rules.pack.version

    private val prefs = AppPrefs(application)

    /** اولین اجرا (D47): تا وقتی false است، صفحهٔ خوش‌آمد دیده می‌شود. */
    private val _onboarded = MutableStateFlow(prefs.onboarded)
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    /** پیامک‌های قدیمی که پیشنهاد جابه‌جایی دارند (اصل ۸، D16، D22). */
    val suggestions: StateFlow<MoveSuggestion> = repository.suggestions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), MoveSuggestion.NONE)

    private val _sweepOfferAnswered = MutableStateFlow(prefs.sweepOfferAnswered)

    /**
     * برگهٔ نتیجهٔ `HistorySweep` (D47 مرحلهٔ ۴): فقط یک بار، بعد از اینکه
     * بررسی تمام شد و چیزی برای پیشنهاد بود.
     */
    val sweepOffer: StateFlow<MoveSuggestion?> = combine(
        suggestions,
        _onboarded,
        _sweepOfferAnswered,
        _syncing,
    ) { found, onboarded, answered, syncing ->
        found.takeIf { onboarded && !answered && !syncing && it.messages > 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    /** نمونه‌های پیشنهاد، برای «نمونه‌ها را نشان بده». */
    private val _suggestionSample = MutableStateFlow<List<MessageEntity>?>(null)
    val suggestionSample: StateFlow<List<MessageEntity>?> = _suggestionSample.asStateFlow()

    /** نام مخاطب هر سرشماره، اگر اجازهٔ مخاطب‌ها داده شده باشد. */
    private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactNames: StateFlow<Map<String, String>> = _contactNames.asStateFlow()
    private val lookedUp = HashSet<String>()

    val settings: StateFlow<SettingsState> = settingsStore.changes()
        .map { snapshotSettings() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, snapshotSettings())

    val senderRules: StateFlow<List<SenderRuleEntity>> = repository.senderRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<MessageEntity>> = _searchQuery
        .debounce(SEARCH_DEBOUNCE)
        .mapLatest { query -> if (query.isBlank()) emptyList() else repository.search(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    /** سیم‌کارت‌های فعال (D27)؛ خالی یعنی یکی است یا اجازه‌اش داده نشده. */
    private val _sims = MutableStateFlow<List<SimCard>>(emptyList())
    val sims: StateFlow<List<SimCard>> = _sims.asStateFlow()

    /** گزارش کرش قبلی (D58)، برای پیشنهاد ارسال. */
    private val _crashReport = MutableStateFlow(container.crashReports.pending())
    val crashReport: StateFlow<String?> = _crashReport.asStateFlow()

    private var conversationJob: Job? = null
    private val partsCache = HashMap<Long, List<MmsPartInfo>>()

    /**
     * تغییرهای بیرون از اپ وقتی اپ باز است (D21)، مثلاً پیامکی که اپ دیگری
     * نوشته یا پاک کرده. همگام‌سازی با کمی تأخیر اجرا می‌شود تا چند تغییر پشت
     * سر هم یک همگام‌سازی بشوند.
     */
    private val providerChanges = MutableStateFlow(0L)
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            providerChanges.value = providerChanges.value + 1
        }
    }

    init {
        runCatching {
            application.contentResolver.registerContentObserver(Telephony.MmsSms.CONTENT_URI, true, observer)
        }
        viewModelScope.launch {
            providerChanges.debounce(OBSERVER_DEBOUNCE).collect { if (it > 0) sync() }
        }
        sync()
        refreshSims()
        viewModelScope.launch {
            combine(inboxThreads, promoThreads, scamThreads) { a, b, c -> a + b + c }.collect { threads ->
                resolveNames(threads.flatMap { summary -> participantsOf(summary.address, summary.recipients) })
            }
        }
    }

    private fun snapshotSettings() = SettingsState(
        digest = settingsStore.digest,
        theme = settingsStore.theme,
        dynamicColor = settingsStore.dynamicColor,
        persianDigits = settingsStore.persianDigits,
        deliveryReports = settingsStore.deliveryReports,
        showReasonEverywhere = settingsStore.showReasonEverywhere,
    )

    // ——— اولین اجرا و پیشنهاد جابه‌جایی ———

    /** مرحلهٔ اول و دوم اولین اجرا تمام شد؛ چه اپ پیش‌فرض شده باشد چه نه. */
    fun finishOnboarding() {
        prefs.onboarded = true
        _onboarded.value = true
    }

    fun showSuggestionSample() {
        viewModelScope.launch {
            _suggestionSample.value = repository.suggestedSample(SAMPLE_SIZE)
        }
    }

    fun hideSuggestionSample() {
        _suggestionSample.value = null
    }

    /** «منتقل کن»: تنها راهی که پیامک قدیمی جابه‌جا می‌شود (اصل ۸). */
    fun acceptSuggestions() {
        answerSweepOffer()
        viewModelScope.launch { repository.acceptSuggestionsAfterUserConfirmation() }
    }

    /** «در صندوق بماند»: پیشنهاد برداشته می‌شود. */
    fun keepSuggestionsInInbox() {
        answerSweepOffer()
        viewModelScope.launch { repository.dismissSuggestions() }
    }

    /** «فعلاً نه»: پیشنهاد در `PromoFolder` می‌ماند و این برگه تکرار نمی‌شود. */
    fun postponeSuggestions() = answerSweepOffer()

    private fun answerSweepOffer() {
        _suggestionSample.value = null
        prefs.sweepOfferAnswered = true
        _sweepOfferAnswered.value = true
    }

    private fun threadsIn(folder: Folder): StateFlow<List<ThreadSummary>> =
        repository.threads(folder)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    // ——— ناوبری ———

    fun navigate(destination: Destination) {
        leaveConversation()
        val stack = _stack.value
        // گفتگوی تازه‌ای که حالا شناسه گرفته، جای خودش را عوض می‌کند، نه روی آن.
        val base = if (stack.last() is Destination.NewConversation && destination is Destination.Conversation) {
            stack.dropLast(1)
        } else {
            stack
        }
        _stack.value = base + destination
        enter(destination)
    }

    /** خروجی false یعنی به صفحهٔ اصلی رسیده‌ایم و Activity باید بسته شود. */
    fun back(): Boolean {
        val stack = _stack.value
        if (stack.size <= 1) return false
        leaveConversation()
        _stack.value = stack.dropLast(1)
        enter(_stack.value.last())
        return true
    }

    private fun replaceTop(destination: Destination) {
        _stack.value = _stack.value.dropLast(1) + destination
        enter(destination)
    }

    private fun enter(destination: Destination) {
        when (destination) {
            is Destination.Conversation -> openConversation(destination)
            else -> conversationJob?.cancel()
        }
    }

    /** رفتن مستقیم به یک مقصد از بیرون اپ (اعلان، اشتراک‌گذاری)، بدون گم شدن پیش‌نویس. */
    private fun resetTo(destination: Destination) {
        leaveConversation()
        _stack.value = listOf(Destination.Home)
        navigate(destination)
    }

    fun openFromNotification(threadId: Long) = resetTo(Destination.Conversation(threadId, Folder.INBOX))

    fun openFolderFromNotification(folder: Folder) = resetTo(Destination.FolderView(folder))

    /** `sms:` از اپ‌های دیگر، یا «گفتگوی تازه»: گفتگو با این سرشماره‌ها، با متن پیشنهادی. */
    fun composeTo(recipients: List<String>, body: String?, attachment: Uri? = null) {
        val cleaned = recipients.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy(Addresses::normalize)
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            val threadId = runCatching {
                if (cleaned.size == 1) repository.threadIdFor(cleaned.single()) else repository.threadIdFor(cleaned.toSet())
            }.getOrDefault(0L)
            navigate(Destination.Conversation(threadId, Folder.INBOX, cleaned))
            if (!body.isNullOrEmpty()) _draft.value = body
            attachment?.let(::attach)
        }
    }

    /** متن یا تصویری که از اپ دیگری به اشتراک گذاشته شده و منتظر گیرنده است. */
    private var pendingShare: Share? = null

    fun shareIntoNewConversation(share: Share) {
        pendingShare = share
        resetTo(Destination.NewConversation)
    }

    /** «شروع گفتگو»: متن یا تصویر اشتراک‌گذاشته‌شده، اگر باشد، به پیش‌نویس می‌رود. */
    fun startConversation(recipients: List<String>) {
        val share = pendingShare
        pendingShare = null
        composeTo(recipients, share?.text, share?.image)
    }

    // ——— همگام‌سازی ———

    /**
     * همگام‌سازی با provider، که `HistorySweep` اولین اجرا هم هست: پیامک‌های
     * قدیمی طبقه‌بندی می‌شوند ولی بدون تأیید کاربر جابه‌جا نمی‌شوند (اصل ۸).
     */
    fun sync() {
        if (_syncing.value) {
            // تغییری که وسط همگام‌سازی رسیده، پس از آن دوباره دیده می‌شود.
            syncAgain = true
            return
        }
        _syncing.value = true
        viewModelScope.launch {
            do {
                syncAgain = false
                val result = runCatching { repository.sync() }
                _syncFailed.value = result.isFailure
            } while (syncAgain)
            _syncing.value = false
        }
    }

    private var syncAgain = false

    // ——— گفتگو ———

    private fun openConversation(target: Destination.Conversation) {
        viewModelScope.launch {
            repository.markThreadRead(target.threadId, target.folder)
            container.notifier.cancelThread(target.threadId)
        }
        conversationJob?.cancel()
        _attachments.value = emptyList()
        _conversation.value = ConversationUiState(
            threadId = target.threadId,
            folder = target.folder,
            participants = target.recipients,
        )
        conversationJob = viewModelScope.launch {
            val savedDraft = repository.draft(target.threadId)
            if (savedDraft.isNotEmpty() && _draft.value.isEmpty()) _draft.value = savedDraft
            val pinned = repository.isPinned(target.threadId)
            repository.conversation(target.threadId).collect { messages ->
                val last = messages.lastOrNull()
                val participants = when {
                    last == null -> target.recipients
                    last.isGroup -> last.participants
                    else -> listOf(messages.firstOrNull { !it.outgoing }?.address ?: last.address)
                }
                resolveNames(participants)
                val lastIncoming = messages.lastOrNull { !it.outgoing }
                val address = participants.firstOrNull().orEmpty()
                val previous = _conversation.value
                _conversation.value = ConversationUiState(
                    threadId = target.threadId,
                    folder = target.folder,
                    participants = participants,
                    messages = messages,
                    isMixedSender = address.isNotBlank() && participants.size == 1 && repository.isMixedSender(address),
                    isAdLine = participants.size == 1 && repository.isAdLine(address),
                    pinned = if (previous.threadId == target.threadId) previous.pinned || pinned else pinned,
                    subscriptionId = previous.subscriptionId.takeIf { it >= 0 && previous.threadId == target.threadId }
                        ?: lastIncoming?.subId
                        ?: last?.subId
                        ?: -1,
                )
            }
        }
    }

    /** پیش‌نویس گفتگو، وقتی کاربر از آن بیرون می‌رود (D53). */
    private fun leaveConversation() {
        val state = _conversation.value
        val current = _stack.value.last()
        if (current !is Destination.Conversation) return
        val text = _draft.value
        _draft.value = ""
        _attachments.value = emptyList()
        if (state.threadId > 0) viewModelScope.launch { repository.saveDraft(state.threadId, text) }
    }

    /** وقتی اپ به پس‌زمینه می‌رود، پیش‌نویس هم ذخیره می‌شود. */
    fun saveDraftNow() {
        val state = _conversation.value
        if (_stack.value.last() !is Destination.Conversation || state.threadId <= 0) return
        val text = _draft.value
        viewModelScope.launch { repository.saveDraft(state.threadId, text) }
    }

    fun updateDraft(text: String) {
        _draft.value = text
    }

    /** انتخاب تصویر برای MMS؛ تصویر روی گوشی کوچک می‌شود تا زیر سقف اپراتور برود. */
    fun attach(uri: Uri) {
        viewModelScope.launch {
            _preparingAttachment.value = true
            val part = runCatching {
                MmsImages.prepare(getApplication(), uri, _conversation.value.subscriptionId)
            }.getOrNull()
            _preparingAttachment.value = false
            if (part == null) {
                _notice.value = UiNotice.AttachmentFailed
            } else {
                _attachments.value = _attachments.value + PendingAttachment(uri, part)
            }
        }
    }

    fun removeAttachment(attachment: PendingAttachment) {
        _attachments.value = _attachments.value - attachment
    }

    fun send() {
        val state = _conversation.value
        val body = _draft.value
        val attachments = _attachments.value
        if ((body.isBlank() && attachments.isEmpty()) || state.participants.isEmpty()) return
        _draft.value = ""
        _attachments.value = emptyList()
        viewModelScope.launch {
            runCatching {
                // پیوست یا گروه یعنی MMS (D26)؛ بقیه پیامک عادی است.
                if (attachments.isNotEmpty() || state.isGroup) {
                    container.mmsSender.send(
                        recipients = state.participants,
                        text = body.takeIf { it.isNotBlank() },
                        attachments = attachments.map { it.part },
                        subscriptionId = state.subscriptionId,
                    )
                } else {
                    container.smsSender.send(state.address, body, state.subscriptionId)
                }
            }.onFailure {
                // پیامک ثبت نشد؛ متن و پیوست برمی‌گردند تا از دست نروند.
                _draft.value = body
                _attachments.value = attachments
                _sendError.value = true
            }.onSuccess { sent ->
                repository.saveDraft(sent.threadId, "")
                // گفتگوی تازه (`sms:`) حالا شناسهٔ واقعی دارد.
                if (state.threadId != sent.threadId) {
                    replaceTop(Destination.Conversation(sent.threadId, state.folder, state.participants))
                }
            }
        }
    }

    fun resend(message: MessageEntity) {
        viewModelScope.launch {
            if (message.kind == MessageEntity.KIND_MMS) {
                container.mmsSender.resend(message)
            } else {
                container.smsSender.resend(message)
            }
        }
    }

    /** «دریافت» برای MMSی که فقط اعلانش رسیده است. */
    fun downloadMms(message: MessageEntity) {
        viewModelScope.launch {
            val ok = MmsDownloader.retry(getApplication(), repository, message.providerId)
            _notice.value = if (ok) UiNotice.DownloadRequested else UiNotice.DownloadFailed
        }
    }

    /** partهای یک MMS؛ یک بار خوانده و نگه داشته می‌شوند. */
    suspend fun mmsParts(message: MessageEntity): List<MmsPartInfo> {
        partsCache[message.providerId]?.let { return it }
        val parts = runCatching { repository.mms.parts(message.providerId) }.getOrDefault(emptyList())
        partsCache[message.providerId] = parts
        return parts
    }

    fun partUri(partId: Long): Uri = repository.mms.partUri(partId)

    fun dismissSendError() {
        _sendError.value = false
    }

    fun dismissNotice() {
        _notice.value = null
    }

    fun setPinned(threadId: Long, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(threadId, pinned) }
        if (_conversation.value.threadId == threadId) {
            _conversation.value = _conversation.value.copy(pinned = pinned)
        }
    }

    /** «علامت خوانده‌نشده» (D53). */
    fun markUnread(threadId: Long, folder: Folder) {
        viewModelScope.launch { repository.markThreadUnread(threadId, folder) }
    }

    fun markRead(threadId: Long, folder: Folder) {
        viewModelScope.launch { repository.markThreadRead(threadId, folder) }
    }

    /** «همه خوانده شد» در `PromoFolder` (D45). */
    fun markFolderRead(folder: Folder) {
        viewModelScope.launch { repository.markFolderRead(folder) }
    }

    // ——— سیم‌کارت (D27) ———

    fun refreshSims() {
        _sims.value = SimCards.active(getApplication()).takeIf { it.size > 1 }.orEmpty()
    }

    fun chooseSim(subscriptionId: Int) {
        _conversation.value = _conversation.value.copy(subscriptionId = subscriptionId)
    }

    // ——— Rescue، Block، Unsub11 ———

    /** «این تبلیغ نیست»: فقط همین پیامک به `Inbox` برمی‌گردد (ADR-0006 بند ۱). */
    fun rescue(message: MessageEntity) {
        viewModelScope.launch {
            repository.rescue(message)
            _rescueFollowUp.value = RescueFollowUp(
                address = message.address,
                mixedSender = repository.isMixedSender(message.address),
            )
        }
    }

    fun answerRescueFollowUp(alwaysAllow: Boolean) {
        val pending = _rescueFollowUp.value ?: return
        _rescueFollowUp.value = null
        if (!alwaysAllow) return
        viewModelScope.launch { repository.alwaysAllow(pending.address) }
    }

    /** «تبلیغ‌های این فرستنده را همیشه پنهان کن». */
    fun block(address: String) {
        viewModelScope.launch { repository.block(address) }
    }

    fun forgetRule(address: String) {
        viewModelScope.launch { repository.forgetRule(address) }
    }

    /** `Unsub11`، فقط پس از تأیید صریح کاربر (D28). */
    fun unsubscribe() {
        val state = _conversation.value
        if (!state.isAdLine) return
        viewModelScope.launch {
            runCatching { container.smsSender.unsubscribe(state.address, state.subscriptionId) }
                .onFailure { _sendError.value = true }
        }
    }

    // ——— خالی کردن پوشه (D45) ———

    /** مرحلهٔ اول «خالی کردن پوشه»: شمردن پیامک‌ها (D45). */
    fun requestEmptyFolder(folder: Folder) {
        viewModelScope.launch {
            _emptyFolder.value = EmptyFolderRequest(folder, repository.countNow(folder))
        }
    }

    /** مرحلهٔ دوم: تأیید نهایی، با همان تعداد. */
    fun confirmEmptyFolderFirstStep() {
        _emptyFolder.value = _emptyFolder.value?.copy(confirmed = true)
    }

    /** تنها حذف گروهی اپ، فقط بعد از هر دو مرحله. */
    fun emptyFolderConfirmed() {
        val request = _emptyFolder.value ?: return
        _emptyFolder.value = null
        if (!request.confirmed) return
        viewModelScope.launch { repository.emptyFolderAfterExplicitConfirmation(request.folder) }
    }

    fun cancelEmptyFolder() {
        _emptyFolder.value = null
    }

    // ——— جستجو ———

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun openSearchResult(message: MessageEntity) {
        navigate(Destination.Conversation(message.threadId, message.folder))
    }

    // ——— تنظیمات (D52) ———

    fun setDigest(frequency: DigestFrequency) {
        settingsStore.digest = frequency
        DigestScheduler.schedule(getApplication())
    }

    fun setTheme(mode: ThemeMode) {
        settingsStore.theme = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        settingsStore.dynamicColor = enabled
    }

    fun setPersianDigits(enabled: Boolean) {
        settingsStore.persianDigits = enabled
    }

    fun setDeliveryReports(enabled: Boolean) {
        settingsStore.deliveryReports = enabled
    }

    fun setShowReasonEverywhere(enabled: Boolean) {
        settingsStore.showReasonEverywhere = enabled
    }

    // ——— پشتیبان (D57) ———

    fun exportBackup(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _notice.value = runCatching {
                val output = app.contentResolver.openOutputStream(uri, "wt") ?: error("فایل باز نشد")
                val result = container.backup.export(output, BuildConfigInfo.versionName(app), settingsStore)
                UiNotice.BackupExported(result.messages)
            }.getOrElse { UiNotice.BackupFailed((it as? BackupException)?.reason) }
        }
    }

    fun importBackup(uri: Uri, isDefaultApp: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _syncing.value = true
            _notice.value = runCatching {
                val input = app.contentResolver.openInputStream(uri) ?: error("فایل باز نشد")
                val result = container.backup.import(input, settingsStore, isDefaultApp)
                UiNotice.BackupImported(result.added, result.duplicates, result.corrupt)
            }.getOrElse { UiNotice.BackupFailed((it as? BackupException)?.reason) }
            _syncing.value = false
        }
    }

    // ——— گزارش خطا (D58) ———

    fun dismissCrashReport() {
        container.crashReports.discard()
        _crashReport.value = null
    }

    // ——— نام مخاطب‌ها ———

    private fun participantsOf(address: String, recipients: String): List<String> =
        if (recipients.isEmpty()) listOf(address) else recipients.split(MessageEntity.RECIPIENT_SEPARATOR)

    private fun resolveNames(addresses: List<String>) {
        val fresh = addresses.filter { it.isNotBlank() && lookedUp.add(it) }
        if (fresh.isEmpty()) return
        viewModelScope.launch {
            val found = HashMap<String, String>()
            for (address in fresh) {
                Contacts.displayName(getApplication(), address)?.let { found[address] = it }
            }
            if (found.isNotEmpty()) _contactNames.value = _contactNames.value + found
        }
    }

    /** وقتی کاربر اجازهٔ مخاطب‌ها را تازه داده است. */
    fun refreshContactNames() {
        lookedUp.clear()
        val all = (inboxThreads.value + promoThreads.value + scamThreads.value)
            .flatMap { participantsOf(it.address, it.recipients) }
        resolveNames(all)
    }

    override fun onCleared() {
        saveDraftNow()
        runCatching { getApplication<Application>().contentResolver.unregisterContentObserver(observer) }
        super.onCleared()
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val SAMPLE_SIZE = 5
        const val SEARCH_DEBOUNCE = 250L
        const val OBSERVER_DEBOUNCE = 1_500L
    }
}
