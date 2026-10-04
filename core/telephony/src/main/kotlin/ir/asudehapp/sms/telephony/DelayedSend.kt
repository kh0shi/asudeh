package ir.asudehapp.sms.telephony

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * «تأخیر پیش از ارسال» با «لغو» (ROADMAP D8).
 *
 * تا پایان تأخیر پیامکی در `OUTBOX` نوشته نمی‌شود، پس «لغو» چیزی را پاک
 * نمی‌کند. در عوض متن **پیش از** شروع تأخیر در پیش‌نویس گفتگو ذخیره می‌شود؛
 * اگر اپ وسط تأخیر بسته شود یا سیستم آن را بکشد، متن در پیش‌نویس می‌ماند و
 * چیزی گم نمی‌شود. پاک کردن پیش‌نویس پس از ارسال با فرستنده است ([start])،
 * و فقط وقتی پیامک دیگری برای همان گفتگو منتظر نباشد.
 *
 * هر بار فقط یک پیامک منتظر است. ارسال تازه در همین حال، منتظر قبلی را همان
 * لحظه می‌فرستد تا ترتیب پیامک‌ها به هم نخورد.
 */
class DelayedSend(
    private val scope: CoroutineScope,
    private val saveDraft: suspend (threadId: Long, text: String) -> Unit,
) {

    /** پیامکی که منتظر پایان تأخیر است. */
    data class Pending(val threadId: Long, val body: String, val delayMillis: Long)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    private var job: Job? = null
    private var sendPending: (suspend () -> Unit)? = null

    /** [send] پس از [delayMillis] اجرا می‌شود، مگر پیش از آن [cancel] شود. */
    fun start(threadId: Long, body: String, delayMillis: Long, send: suspend () -> Unit) {
        flush()
        val pending = Pending(threadId, body, delayMillis)
        _pending.value = pending
        sendPending = send
        job = scope.launch {
            saveDraft(threadId, body)
            delay(delayMillis)
            finish(pending)
        }
    }

    /** لغو؛ متن برمی‌گردد تا دوباره در جعبهٔ نوشتن بنشیند. پیش‌نویس ذخیره‌شده همان متن است. */
    fun cancel(): Pending? {
        val pending = _pending.value ?: return null
        job?.cancel()
        clear()
        return pending
    }

    /** منتظر فعلی را همین حالا بفرست. */
    fun flush() {
        if (_pending.value == null) return
        job?.cancel()
        val send = sendPending
        clear()
        if (send != null) scope.launch { send() }
    }

    private suspend fun finish(pending: Pending) {
        if (_pending.value != pending) return
        val send = sendPending
        clear()
        send?.invoke()
    }

    private fun clear() {
        _pending.value = null
        sendPending = null
        job = null
    }

    companion object {
        /** گزینه‌های تأخیر بر حسب ثانیه؛ صفر یعنی خاموش. */
        val CHOICES_SECONDS: List<Int> = listOf(0, 3, 5, 10)
    }
}
