package ir.asudehapp.sms.telephony

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DelayedSendTest {

    private val drafts = mutableMapOf<Long, String>()
    private val sent = mutableListOf<String>()

    private fun TestScope.delayedSend(scope: kotlinx.coroutines.CoroutineScope = backgroundScope) =
        DelayedSend(scope) { threadId, text -> drafts[threadId] = text }

    private fun DelayedSend.send(threadId: Long, body: String) =
        start(threadId, body, DELAY) {
            sent += body
            drafts[threadId] = ""
        }

    @Test
    fun `sends after the delay and not before`() = runTest {
        val delayed = delayedSend()
        delayed.send(7, "سلام")
        advanceTimeBy(DELAY - 1)
        runCurrent()
        assertEquals(emptyList<String>(), sent)
        assertEquals("سلام", delayed.pending.value?.body)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("سلام"), sent)
        assertNull(delayed.pending.value)
        assertEquals("", drafts[7])
    }

    @Test
    fun `cancel returns the text and nothing is sent`() = runTest {
        val delayed = delayedSend()
        delayed.send(7, "سلام")
        runCurrent()
        assertEquals("سلام", delayed.cancel()?.body)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), sent)
        assertEquals("سلام", drafts[7])
    }

    /** اپ وسط تأخیر بسته شد: کار ارسال با scope از بین می‌رود، متن در پیش‌نویس می‌ماند. */
    @Test
    fun `closing the app mid-delay keeps the text in the draft`() = runTest {
        val appScope = TestScope(testScheduler)
        val delayed = delayedSend(appScope)
        delayed.send(7, "فردا می‌بینمت")
        advanceTimeBy(DELAY / 2)
        runCurrent()
        appScope.cancel()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), sent)
        assertEquals("فردا می‌بینمت", drafts[7])
    }

    @Test
    fun `a second send flushes the first so order is kept`() = runTest {
        val delayed = delayedSend()
        delayed.send(7, "اول")
        runCurrent()
        delayed.send(7, "دوم")
        runCurrent()
        assertEquals(listOf("اول"), sent)
        assertEquals("دوم", delayed.pending.value?.body)
        advanceTimeBy(DELAY + 1)
        runCurrent()
        assertEquals(listOf("اول", "دوم"), sent)
    }

    private companion object {
        const val DELAY = 5_000L
    }
}
