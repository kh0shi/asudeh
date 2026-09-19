package ir.asudehapp.sms.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.ThreadSummary
import ir.asudehapp.sms.model.Folder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ناوبری در این نسخه فقط سه مقصد دارد، پس یک `sealed` ساده کافی است (D56). */
sealed interface Destination {
    data object Home : Destination
    data class FolderView(val folder: Folder) : Destination

    /** [address] برای گفتگوی تازه‌ای است که هنوز پیامکی ندارد (`sms:`). */
    data class Conversation(
        val threadId: Long,
        val folder: Folder,
        val address: String = "",
    ) : Destination
}

/** یک `UiState` تغییرناپذیر برای هر صفحه (D56). */
data class ConversationUiState(
    val threadId: Long = 0,
    val folder: Folder = Folder.INBOX,
    val address: String = "",
    val messages: List<MessageEntity> = emptyList(),
    val isMixedSender: Boolean = false,
    val isAdLine: Boolean = false,
    /** پاسخ از سیم‌کارتی می‌رود که پیامک آخر به آن رسیده است (D27). */
    val replySubscriptionId: Int = -1,
)

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

class AsudehViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AsudehApplication.containerOf(application)
    private val repository = container.repository

    private val _destination = MutableStateFlow<Destination>(Destination.Home)
    val destination: StateFlow<Destination> = _destination.asStateFlow()

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

    /** خطایی که باید به کاربر نشان داده شود؛ بعد از نمایش پاک می‌شود. */
    private val _sendError = MutableStateFlow(false)
    val sendError: StateFlow<Boolean> = _sendError.asStateFlow()

    private val _rescueFollowUp = MutableStateFlow<RescueFollowUp?>(null)
    val rescueFollowUp: StateFlow<RescueFollowUp?> = _rescueFollowUp.asStateFlow()

    private val _emptyFolder = MutableStateFlow<EmptyFolderRequest?>(null)
    val emptyFolder: StateFlow<EmptyFolderRequest?> = _emptyFolder.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncFailed = MutableStateFlow(false)
    val syncFailed: StateFlow<Boolean> = _syncFailed.asStateFlow()

    val rulesVersion: String = repository.rules.pack.version

    private var conversationJob: Job? = null

    init {
        sync()
    }

    private fun threadsIn(folder: Folder): StateFlow<List<ThreadSummary>> =
        repository.threads(folder)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    fun navigate(destination: Destination) {
        _destination.value = destination
        if (destination is Destination.Conversation) {
            openConversation(destination)
        } else {
            conversationJob?.cancel()
        }
    }

    fun back() = navigate(Destination.Home)

    fun openFromNotification(threadId: Long) =
        navigate(Destination.Conversation(threadId, Folder.INBOX))

    /** `sms:` از اپ‌های دیگر: گفتگو با این سرشماره، با متن پیشنهادی. */
    fun composeTo(address: String, body: String?) {
        viewModelScope.launch {
            val threadId = runCatching { repository.threadIdFor(address) }.getOrDefault(0L)
            if (!body.isNullOrEmpty()) _draft.value = body
            navigate(Destination.Conversation(threadId, Folder.INBOX, address))
        }
    }

    /**
     * همگام‌سازی با provider، که `HistorySweep` اولین اجرا هم هست: پیامک‌های
     * قدیمی طبقه‌بندی می‌شوند ولی بدون تأیید کاربر جابه‌جا نمی‌شوند (اصل ۸).
     */
    fun sync() {
        if (_syncing.value) return
        _syncing.value = true
        viewModelScope.launch {
            val result = runCatching { repository.sync() }
            _syncFailed.value = result.isFailure
            _syncing.value = false
        }
    }

    private fun openConversation(target: Destination.Conversation) {
        viewModelScope.launch {
            repository.markThreadRead(target.threadId, target.folder)
        }
        conversationJob?.cancel()
        _conversation.value = ConversationUiState(
            threadId = target.threadId,
            folder = target.folder,
            address = target.address,
        )
        conversationJob = viewModelScope.launch {
            repository.conversation(target.threadId).collect { messages ->
                val address = messages.firstOrNull()?.address ?: target.address
                val lastIncoming = messages.lastOrNull { !it.outgoing }
                _conversation.value = ConversationUiState(
                    threadId = target.threadId,
                    folder = target.folder,
                    address = address,
                    messages = messages,
                    isMixedSender = address.isNotBlank() && repository.isMixedSender(address),
                    isAdLine = address.isNotBlank() && repository.isAdLine(address),
                    replySubscriptionId = lastIncoming?.subId ?: -1,
                )
            }
        }
    }

    fun updateDraft(text: String) {
        _draft.value = text
    }

    fun send() {
        val state = _conversation.value
        val body = _draft.value
        if (body.isBlank() || state.address.isBlank()) return
        _draft.value = ""
        viewModelScope.launch {
            runCatching {
                container.smsSender.send(state.address, body, state.replySubscriptionId)
            }.onFailure {
                // پیامک ثبت نشد؛ متن به جعبه برمی‌گردد تا از دست نرود.
                _draft.value = body
                _sendError.value = true
            }.onSuccess { sent ->
                // گفتگوی تازه (`sms:`) حالا شناسهٔ واقعی دارد.
                if (state.threadId != sent.threadId) {
                    navigate(Destination.Conversation(sent.threadId, state.folder, state.address))
                }
            }
        }
    }

    fun resend(message: MessageEntity) {
        viewModelScope.launch { container.smsSender.resend(message) }
    }

    fun dismissSendError() {
        _sendError.value = false
    }

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

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
