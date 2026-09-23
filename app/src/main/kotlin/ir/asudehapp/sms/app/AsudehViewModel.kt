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
import ir.asudehapp.sms.data.KeywordRuleEntity
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.MmsPartInfo
import ir.asudehapp.sms.data.MessageKey
import ir.asudehapp.sms.data.MoveSuggestion
import ir.asudehapp.sms.data.ScheduledMessageEntity
import ir.asudehapp.sms.data.SenderRuleEntity
import ir.asudehapp.sms.data.SenderRuleKind
import ir.asudehapp.sms.data.TrashedMessageEntity
import ir.asudehapp.sms.data.ThemeMode
import ir.asudehapp.sms.data.ThreadSummary
import ir.asudehapp.sms.mms.MmsPart
import ir.asudehapp.sms.model.Addresses
import ir.asudehapp.sms.model.DefaultRule
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.persian.ScheduleChoice
import ir.asudehapp.sms.persian.SendSchedule
import ir.asudehapp.sms.telephony.ActiveConversation
import ir.asudehapp.sms.telephony.ContactPhone
import ir.asudehapp.sms.telephony.Contacts
import ir.asudehapp.sms.telephony.DigestScheduler
import ir.asudehapp.sms.telephony.MmsDownloader
import ir.asudehapp.sms.telephony.MmsImages
import ir.asudehapp.sms.telephony.ScheduledSend
import ir.asudehapp.sms.telephony.ScheduledSendScheduler
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

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

    /** «حذف‌شده‌ها» (ADR-0010). */
    data object Trash : Destination
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

/**
 * حذف انتخاب‌شده‌ها (ADR-0010). یک مرحله کافی است، چون حذف به سطل می‌رود و
 * برگشت‌پذیر است؛ «خالی کردن سطل» است که دو مرحله دارد.
 */
data class DeleteRequest(
    val messages: List<MessageEntity>,
    /** شمار گفتگوهایی که کاملاً حذف می‌شوند؛ صفر یعنی حذف چند پیامک. */
    val threads: Int,
)

/** «خالی کردن سطل»: حذف قطعی، پس مثل D45 دو مرحله دارد. */
data class EmptyTrashRequest(val count: Int, val confirmed: Boolean = false)

/**
 * «اسپم» روی یک یا چند گفتگوی فهرست: همان `Block` (ADR-0006 بند ۲) روی همهٔ
 * فرستنده‌های ورودیِ آن گفتگوها. چیزی حذف نمی‌شود، پس یک مرحله تأیید بس است؛
 * متن تأیید می‌گوید دقیقاً چه اتفاقی می‌افتد (اصل ۵).
 */
data class SpamRequest(val threads: Int, val senders: List<String>)

/** برگهٔ انتخاب زمان ارسال (ADR-0011). */
data class ScheduleRequest(
    val presets: List<ScheduleChoice>,
    /** متنی که زمان‌بندی می‌شود؛ همان پیش‌نویس لحظهٔ باز شدن برگه. */
    val body: String,
)

/** تنظیمات، به‌صورت یک عکس فوری برای رابط (D52). */
data class SettingsState(
    val digest: DigestFrequency = DigestFrequency.DAILY,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val persianDigits: Boolean = true,
    val deliveryReports: Boolean = false,
    val showReasonEverywhere: Boolean = false,
    /** ساعت هر پیامک، زیر خودش. */
    val showMessageClock: Boolean = true,
    val mmsAutoDownload: Boolean = true,
    val mmsSending: Boolean = true,
    /** نام‌های `DefaultRule`ی که خاموش شده‌اند (ADR-0012). */
    val disabledDefaultRules: Set<String> = emptySet(),
    /** شناسهٔ کلیدواژه‌های پیش‌فرضی که خاموش شده‌اند (ADR-0012). */
    val disabledDefaultKeywords: Set<String> = emptySet(),
)

/** پیام کوتاهی که پس از یک کار نشان داده می‌شود (Snackbar). */
sealed interface UiNotice {
    /** [trashIds] برای دکمهٔ «بازگرداندن» همان لحظه است (ADR-0010). */
    data class Deleted(val count: Int, val trashIds: List<Long>) : UiNotice
    data class Restored(val count: Int) : UiNotice
    data object RestoreFailed : UiNotice
    data object NothingDeleted : UiNotice
    data class Scheduled(val atMillis: Long) : UiNotice
    data object ScheduleTooSoon : UiNotice
    data object ScheduleSending : UiNotice
    data class BackupExported(val messages: Int) : UiNotice
    data class BackupImported(val added: Int, val duplicates: Int, val corrupt: Int) : UiNotice
    data class BackupFailed(val reason: BackupException.Reason?) : UiNotice
    data class MarkedSpam(val senders: Int) : UiNotice
    data object SpamNothing : UiNotice
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

    // ——— انتخاب و حذف (ADR-0010) ———

    /** پیامک‌های انتخاب‌شده در گفتگو. خالی یعنی حالت انتخاب روشن نیست. */
    private val _selectedMessages = MutableStateFlow<Set<MessageKey>>(emptySet())
    val selectedMessages: StateFlow<Set<MessageKey>> = _selectedMessages.asStateFlow()

    /** گفتگوهای انتخاب‌شده در فهرست. */
    private val _selectedThreads = MutableStateFlow<Set<Long>>(emptySet())
    val selectedThreads: StateFlow<Set<Long>> = _selectedThreads.asStateFlow()

    private val _deleteRequest = MutableStateFlow<DeleteRequest?>(null)
    val deleteRequest: StateFlow<DeleteRequest?> = _deleteRequest.asStateFlow()

    private val _emptyTrash = MutableStateFlow<EmptyTrashRequest?>(null)
    val emptyTrash: StateFlow<EmptyTrashRequest?> = _emptyTrash.asStateFlow()

    /** «اسپم» روی گفتگوهای انتخاب‌شده، پیش از تأیید. */
    private val _spamRequest = MutableStateFlow<SpamRequest?>(null)
    val spamRequest: StateFlow<SpamRequest?> = _spamRequest.asStateFlow()

    val trash: StateFlow<List<TrashedMessageEntity>> = repository.trash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val trashCount: StateFlow<Int> = repository.trashCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), 0)

    // ——— ارسال زمان‌بندی‌شده (ADR-0011) ———

    private val _scheduleRequest = MutableStateFlow<ScheduleRequest?>(null)
    val scheduleRequest: StateFlow<ScheduleRequest?> = _scheduleRequest.asStateFlow()

    /** پیامک‌های زمان‌بندی‌شدهٔ همین گفتگو، زیر آخرین پیام دیده می‌شوند. */
    val scheduledHere: StateFlow<List<ScheduledMessageEntity>> = _conversation
        .map { it.threadId }
        .flatMapLatest { threadId ->
            if (threadId > 0) repository.scheduledFor(threadId) else kotlinx.coroutines.flow.flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

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

    /** عکس بند‌انگشتی مخاطب هر سرشماره، اگر اجازهٔ مخاطب‌ها داده شده باشد. */
    private val _contactPhotos = MutableStateFlow<Map<String, Uri>>(emptyMap())
    val contactPhotos: StateFlow<Map<String, Uri>> = _contactPhotos.asStateFlow()

    /** فهرست مخاطب‌ها برای «گفتگوی تازه»؛ بدون مجوز خالی می‌ماند. */
    private val _contacts = MutableStateFlow<List<ContactPhone>>(emptyList())
    val contacts: StateFlow<List<ContactPhone>> = _contacts.asStateFlow()

    /** راهنمای انتخاب بخشی از متن، فقط بار اول (AppPrefs). */
    private val _textPickHintSeen = MutableStateFlow(prefs.textPickHintSeen)
    val textPickHintSeen: StateFlow<Boolean> = _textPickHintSeen.asStateFlow()
    private val lookedUp = HashSet<String>()
    private val lookedUpPhotos = HashSet<String>()

    val settings: StateFlow<SettingsState> = settingsStore.changes()
        .map { snapshotSettings() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, snapshotSettings())

    val senderRules: StateFlow<List<SenderRuleEntity>> = repository.senderRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    /** کلیدواژه‌های «قواعد من» (ADR-0012). */
    val keywordRules: StateFlow<List<KeywordRuleEntity>> = repository.keywordRules()
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
        // پیامکی که زمانش وقتی اپ بسته بود رسیده، همین حالا فرستاده می‌شود.
        ScheduledSendScheduler.reschedule(application)
        viewModelScope.launch {
            combine(inboxThreads, promoThreads, scamThreads) { a, b, c -> a + b + c }.collect { threads ->
                val addresses = threads.flatMap { summary -> participantsOf(summary.address, summary.recipients) }
                resolveNames(addresses)
                resolvePhotos(addresses)
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
        showMessageClock = settingsStore.showMessageClock,
        mmsAutoDownload = settingsStore.mmsAutoDownload,
        mmsSending = settingsStore.mmsSending,
        disabledDefaultRules = settingsStore.disabledDefaultRules,
        disabledDefaultKeywords = settingsStore.disabledDefaultKeywords,
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
        // متنی که منتظر گیرنده بود، با بیرون آمدن از «گفتگوی تازه» منتظر نمی‌ماند.
        if (stack.last() is Destination.NewConversation) _pendingShare.value = null
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

    /**
     * متن یا تصویری که منتظر گیرنده است: اشتراک‌گذاری از اپ دیگر، یا فوروارد.
     * «گفتگوی تازه» آن را نشان می‌دهد تا کاربر بداند چه چیزی در راه است.
     */
    private val _pendingShare = MutableStateFlow<Share?>(null)
    val pendingShare: StateFlow<Share?> = _pendingShare.asStateFlow()

    fun shareIntoNewConversation(share: Share) {
        _pendingShare.value = share
        resetTo(Destination.NewConversation)
    }

    /**
     * فوروارد: متن پیامک‌ها به «گفتگوی تازه» می‌رود و آنجا فقط گیرنده پرسیده
     * می‌شود. برخلاف اشتراک‌گذاری از اپ دیگر، پشتهٔ ناوبری نگه داشته می‌شود تا
     * «بازگشت» به همان گفتگو برگردد. هیچ پیامکی بدون فشردن «فرستادن» نمی‌رود.
     */
    fun forward(text: String) {
        if (text.isEmpty()) return
        _pendingShare.value = Share(text, null)
        navigate(Destination.NewConversation)
    }

    /** فوروارد پیامک‌های انتخاب‌شدهٔ همین گفتگو، به ترتیب زمانی. */
    fun forwardSelectedMessages() {
        val keys = _selectedMessages.value
        if (keys.isEmpty()) return
        val text = _conversation.value.messages
            .filter { MessageKey(it.kind, it.providerId) in keys }
            .map { it.body }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
        clearMessageSelection()
        forward(text)
    }

    /** «شروع گفتگو»: متن یا تصویر اشتراک‌گذاشته‌شده، اگر باشد، به پیش‌نویس می‌رود. */
    fun startConversation(recipients: List<String>) {
        val share = _pendingShare.value
        _pendingShare.value = null
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
        // تا وقتی این گفتگو روی صفحه است، پیامک تازه‌اش اعلان نمی‌گیرد.
        ActiveConversation.set(target.threadId)
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
                resolvePhotos(participants)
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
        ActiveConversation.clear()
        _selectedMessages.value = emptySet()
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

    // ——— «قواعد من»: افزودن دستی و قواعد پیش‌فرض (ADR-0012) ———

    /** افزودن سرشماره به فهرست سفید یا سیاه، از خود صفحهٔ «قواعد من». */
    fun addSenderRule(address: String, kind: SenderRuleKind) {
        if (address.isBlank()) return
        viewModelScope.launch { repository.addSenderRule(address, kind) }
    }

    fun addKeywordRule(keyword: String, kind: SenderRuleKind) {
        if (keyword.isBlank()) return
        viewModelScope.launch { repository.addKeywordRule(keyword, kind) }
    }

    fun forgetKeywordRule(normalized: String) {
        viewModelScope.launch { repository.forgetKeywordRule(normalized) }
    }

    /** خاموش و روشن کردن یک قاعدهٔ پیش‌فرض. هیچ پیامکی با این کار جابه‌جا نمی‌شود. */
    fun setDefaultRuleEnabled(rule: DefaultRule, enabled: Boolean) {
        val current = settingsStore.disabledDefaultRules
        settingsStore.disabledDefaultRules = if (enabled) current - rule.name else current + rule.name
    }

    fun setDefaultKeywordEnabled(id: String, enabled: Boolean) {
        val current = settingsStore.disabledDefaultKeywords
        settingsStore.disabledDefaultKeywords = if (enabled) current - id else current + id
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

    // ——— انتخاب پیامک و گفتگو (ADR-0010) ———

    fun toggleMessage(message: MessageEntity) {
        val key = MessageKey(message.kind, message.providerId)
        val current = _selectedMessages.value
        _selectedMessages.value = if (key in current) current - key else current + key
    }

    fun selectAllMessages() {
        _selectedMessages.value = _conversation.value.messages
            .mapTo(mutableSetOf()) { MessageKey(it.kind, it.providerId) }
    }

    fun clearMessageSelection() {
        _selectedMessages.value = emptySet()
    }

    fun toggleThread(threadId: Long) {
        val current = _selectedThreads.value
        _selectedThreads.value = if (threadId in current) current - threadId else current + threadId
    }

    fun selectAllThreads(threadIds: Collection<Long>) {
        _selectedThreads.value = threadIds.toSet()
    }

    fun clearThreadSelection() {
        _selectedThreads.value = emptySet()
    }

    // ——— کارهای گروهی روی گفتگوهای انتخاب‌شده ———

    fun setPinnedThreads(threadIds: Set<Long>, pinned: Boolean) {
        if (threadIds.isEmpty()) return
        for (threadId in threadIds) setPinned(threadId, pinned)
        clearThreadSelection()
    }

    fun markThreadsRead(threadIds: Set<Long>, folder: Folder) {
        if (threadIds.isEmpty()) return
        for (threadId in threadIds) markRead(threadId, folder)
        clearThreadSelection()
    }

    fun markThreadsUnread(threadIds: Set<Long>, folder: Folder) {
        if (threadIds.isEmpty()) return
        for (threadId in threadIds) markUnread(threadId, folder)
        clearThreadSelection()
    }

    /**
     * مرحلهٔ اول «اسپم»: پیدا کردن فرستنده‌های ورودیِ این گفتگوها. گفتگوی
     * گروهی و پیامک‌های خود کاربر حساب نمی‌شوند، چون `Block` روی آن‌ها کاری
     * نمی‌کند (D26).
     */
    fun requestSpamThreads(threadIds: Set<Long>) {
        if (threadIds.isEmpty()) return
        viewModelScope.launch {
            val senders = threadIds
                .flatMap { repository.messagesOfThread(it, folder = null) }
                .filter { !it.outgoing && !it.isGroup && it.address.isNotBlank() }
                .map { it.address }
                .distinctBy(Addresses::normalize)
            _spamRequest.value = SpamRequest(threads = threadIds.size, senders = senders)
        }
    }

    fun cancelSpam() {
        _spamRequest.value = null
    }

    /**
     * «اسپم»، فقط پس از تأیید صریح: همان `Block` روی هر فرستنده. قاعده در
     * «قواعد من» دیده می‌شود و برداشتنی است (اصل ۵، ADR-0006 بند ۲).
     */
    fun confirmSpam() {
        val request = _spamRequest.value ?: return
        _spamRequest.value = null
        clearThreadSelection()
        if (request.senders.isEmpty()) {
            _notice.value = UiNotice.SpamNothing
            return
        }
        viewModelScope.launch {
            for (address in request.senders) repository.block(address)
            _notice.value = UiNotice.MarkedSpam(request.senders.size)
        }
    }

    // ——— حذف، با سطل (ADR-0010) ———

    /** «حذف» روی پیامک‌های انتخاب‌شدهٔ همین گفتگو. */
    fun requestDeleteSelectedMessages() {
        val keys = _selectedMessages.value
        if (keys.isEmpty()) return
        val messages = _conversation.value.messages.filter { MessageKey(it.kind, it.providerId) in keys }
        if (messages.isEmpty()) return
        _deleteRequest.value = DeleteRequest(messages, threads = 0)
    }

    /** «حذف گفتگو»، یکی یا چندتا. همهٔ پیامک‌های آن‌ها در همین پوشه حذف می‌شوند. */
    fun requestDeleteThreads(threadIds: Set<Long>, folder: Folder) {
        if (threadIds.isEmpty()) return
        viewModelScope.launch {
            val messages = threadIds.flatMap { repository.messagesOfThread(it, folder) }
            _deleteRequest.value = DeleteRequest(messages, threads = threadIds.size)
        }
    }

    fun cancelDelete() {
        _deleteRequest.value = null
    }

    /** تنها راه حذف: تأیید صریح کاربر (ADR-0003 بند ۳). */
    fun confirmDelete() {
        val request = _deleteRequest.value ?: return
        _deleteRequest.value = null
        clearMessageSelection()
        clearThreadSelection()
        viewModelScope.launch {
            val trashed = repository.deleteAfterExplicitConfirmation(request.messages)
            _notice.value = if (trashed.isEmpty()) {
                UiNotice.NothingDeleted
            } else {
                UiNotice.Deleted(trashed.size, trashed)
            }
        }
    }

    /** «بازگرداندن» از Snackbar یا از صفحهٔ «حذف‌شده‌ها». */
    fun restoreFromTrash(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val restored = repository.restoreAllFromTrash(ids)
            _notice.value = if (restored > 0) UiNotice.Restored(restored) else UiNotice.RestoreFailed
        }
    }

    fun requestEmptyTrash() {
        val count = trashCount.value
        if (count > 0) _emptyTrash.value = EmptyTrashRequest(count)
    }

    fun confirmEmptyTrashFirstStep() {
        _emptyTrash.value = _emptyTrash.value?.copy(confirmed = true)
    }

    /** حذف قطعی، فقط بعد از هر دو مرحله (D45). */
    fun emptyTrashConfirmed() {
        val request = _emptyTrash.value ?: return
        _emptyTrash.value = null
        if (!request.confirmed) return
        viewModelScope.launch { repository.emptyTrashAfterExplicitConfirmation() }
    }

    fun cancelEmptyTrash() {
        _emptyTrash.value = null
    }

    // ——— ارسال زمان‌بندی‌شده (ADR-0011) ———

    /** برگهٔ «بعداً بفرست»، با زمان‌های پیشنهادی همین لحظه. */
    fun requestSchedule() {
        val body = _draft.value
        if (body.isBlank() || _conversation.value.participants.isEmpty()) return
        _scheduleRequest.value = ScheduleRequest(
            presets = SendSchedule.presets(System.currentTimeMillis(), ZoneId.systemDefault()),
            body = body,
        )
    }

    fun cancelSchedule() {
        _scheduleRequest.value = null
    }

    /**
     * زمان‌بندی خود ارسال. پیامک تا لحظهٔ ارسال در Telephony Provider نوشته
     * نمی‌شود، چون هنوز پیامکی نیست؛ متنش در صف است و در گفتگو دیده می‌شود.
     */
    fun scheduleSend(atMillis: Long) {
        val request = _scheduleRequest.value ?: return
        val state = _conversation.value
        val at = SendSchedule.validate(atMillis, System.currentTimeMillis())
        if (at == null) {
            _scheduleRequest.value = null
            _notice.value = UiNotice.ScheduleTooSoon
            return
        }
        _scheduleRequest.value = null
        _draft.value = ""
        viewModelScope.launch {
            val threadId = state.threadId.takeIf { it > 0 }
                ?: runCatching {
                    if (state.participants.size == 1) {
                        repository.threadIdFor(state.address)
                    } else {
                        repository.threadIdFor(state.participants.toSet())
                    }
                }.getOrDefault(0L)
            repository.schedule(threadId, state.participants, request.body, state.subscriptionId, at)
            repository.saveDraft(threadId, "")
            if (threadId > 0 && threadId != state.threadId) {
                replaceTop(Destination.Conversation(threadId, state.folder, state.participants))
            }
            ScheduledSendScheduler.reschedule(getApplication())
            _notice.value = UiNotice.Scheduled(at)
        }
    }

    /** «لغو»: پیامک زمان‌بندی‌شده برداشته می‌شود و متنش به پیش‌نویس برمی‌گردد. */
    fun cancelScheduled(message: ScheduledMessageEntity) {
        viewModelScope.launch {
            repository.removeScheduled(message.id)
            if (_draft.value.isEmpty()) _draft.value = message.body
            ScheduledSendScheduler.reschedule(getApplication())
        }
    }

    /** «الان بفرست»، چه پیش از زمانش و چه برای پیامکی که زمانش گذشته است. */
    fun sendScheduledNow(message: ScheduledMessageEntity) {
        _notice.value = UiNotice.ScheduleSending
        viewModelScope.launch {
            ScheduledSend.sendOne(getApplication(), message)
            ScheduledSendScheduler.reschedule(getApplication())
        }
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

    fun setShowMessageClock(enabled: Boolean) {
        settingsStore.showMessageClock = enabled
    }

    fun setMmsAutoDownload(enabled: Boolean) {
        settingsStore.mmsAutoDownload = enabled
    }

    fun setMmsSending(enabled: Boolean) {
        settingsStore.mmsSending = enabled
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

    private fun resolvePhotos(addresses: List<String>) {
        val fresh = addresses.filter { it.isNotBlank() && lookedUpPhotos.add(it) }
        if (fresh.isEmpty()) return
        viewModelScope.launch {
            val found = HashMap<String, Uri>()
            for (address in fresh) {
                Contacts.photoThumbnailUri(getApplication(), address)?.let { found[address] = it }
            }
            if (found.isNotEmpty()) _contactPhotos.value = _contactPhotos.value + found
        }
    }

    /** وقتی کاربر اجازهٔ مخاطب‌ها را تازه داده است. */
    /** یک بار خواندن فهرست مخاطب‌ها، وقتی «گفتگوی تازه» باز می‌شود. */
    fun refreshContacts() {
        viewModelScope.launch { _contacts.value = Contacts.all(getApplication()) }
    }

    fun markTextPickHintSeen() {
        if (_textPickHintSeen.value) return
        prefs.textPickHintSeen = true
        _textPickHintSeen.value = true
    }

    fun refreshContactNames() {
        lookedUp.clear()
        lookedUpPhotos.clear()
        val all = (inboxThreads.value + promoThreads.value + scamThreads.value)
            .flatMap { participantsOf(it.address, it.recipients) }
        resolveNames(all)
        resolvePhotos(all)
    }

    /** گفتگوی بازِ همین لحظه؛ صفر یعنی هیچ‌کدام (`ActiveConversation`). */
    fun openThreadId(): Long =
        if (_stack.value.last() is Destination.Conversation) _conversation.value.threadId else 0L

    override fun onCleared() {
        ActiveConversation.clear()
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
