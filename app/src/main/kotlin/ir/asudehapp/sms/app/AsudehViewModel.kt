package ir.asudehapp.sms.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.ThreadSummary
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.Origin
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** یک `UiState` تغییرناپذیر برای هر صفحه (D56). */
data class ConversationUiState(
    val threadId: Long = 0,
    val folder: Folder = Folder.INBOX,
    val address: String = "",
    val messages: List<MessageEntity> = emptyList(),
    val isMixedSender: Boolean = false,
    val isAdLine: Boolean = false,
)

/** پرسشی که بعد از `Rescue` پرسیده می‌شود (ADR-0006 بند ۱). */
data class RescueFollowUp(
    val address: String,
    /** برای `MixedSender` پاسخ برجسته «فقط همین» است. */
    val mixedSender: Boolean,
)

class AsudehViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AsudehApplication.repositoryOf(application)

    val inboxThreads: StateFlow<List<ThreadSummary>> = threadsIn(Folder.INBOX)
    val promoThreads: StateFlow<List<ThreadSummary>> = threadsIn(Folder.PROMO)
    val scamThreads: StateFlow<List<ThreadSummary>> = threadsIn(Folder.SCAM)

    val promoUnread: StateFlow<Int> = repository.unreadCount(Folder.PROMO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), 0)
    val scamCount: StateFlow<Int> = repository.count(Folder.SCAM)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), 0)

    private val _conversation = MutableStateFlow(ConversationUiState())
    val conversation: StateFlow<ConversationUiState> = _conversation.asStateFlow()

    private val _rescueFollowUp = MutableStateFlow<RescueFollowUp?>(null)
    val rescueFollowUp: StateFlow<RescueFollowUp?> = _rescueFollowUp.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    val rulesVersion: String = repository.rules.pack.version

    private fun threadsIn(folder: Folder): StateFlow<List<ThreadSummary>> =
        repository.threads(folder)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    /**
     * `HistorySweep`: پیامک‌های قدیمی طبقه‌بندی می‌شوند ولی بدون تأیید کاربر
     * جابه‌جا نمی‌شوند (اصل ۸).
     */
    fun sync(origin: Origin = Origin.SWEEP) {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            runCatching { repository.sync(origin) }
            _syncing.value = false
        }
    }

    private var conversationJob: Job? = null

    fun openConversation(threadId: Long, folder: Folder) {
        viewModelScope.launch {
            repository.markThreadRead(threadId, folder)
        }
        conversationJob?.cancel()
        conversationJob = viewModelScope.launch {
            repository.conversation(threadId).collect { messages ->
                val address = messages.firstOrNull()?.address.orEmpty()
                _conversation.value = ConversationUiState(
                    threadId = threadId,
                    folder = folder,
                    address = address,
                    messages = messages,
                    isMixedSender = address.isNotBlank() && repository.isMixedSender(address),
                    isAdLine = address.isNotBlank() && repository.isAdLine(address),
                )
            }
        }
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

    /** تنها حذف گروهی اپ، همیشه دستی و با تأیید دومرحله‌ای (D45). */
    fun emptyFolder(folder: Folder) {
        viewModelScope.launch { repository.emptyFolderAfterExplicitConfirmation(folder) }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
