package ir.asudehapp.sms.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingPromptTest {

    private val day = 24L * 60 * 60 * 1_000
    private val now = 1_000 * day

    /** همهٔ شرط‌ها برقرار: ۱۴ روز، ۲۰ تبلیغ، بی `Rescue`، بازار نصب، هنوز پرسیده نشده. */
    private val ready = RatingSignals(
        firstUseAt = now - 14 * day,
        hiddenAds = 20,
        lastRescueAt = null,
        alreadyAsked = false,
        storeInstalled = true,
    )

    @Test
    fun `asks when every condition holds`() {
        assertTrue(RatingPrompt.shouldAsk(ready, now))
    }

    @Test
    fun `waits for fourteen days of use`() {
        assertFalse(RatingPrompt.shouldAsk(ready.copy(firstUseAt = now - 14 * day + 1), now))
    }

    @Test
    fun `waits for twenty hidden ads`() {
        assertFalse(RatingPrompt.shouldAsk(ready.copy(hiddenAds = 19), now))
    }

    @Test
    fun `stays quiet for seven days after a rescue`() {
        assertFalse(RatingPrompt.shouldAsk(ready.copy(lastRescueAt = now - 7 * day + 1), now))
        assertTrue(RatingPrompt.shouldAsk(ready.copy(lastRescueAt = now - 7 * day), now))
    }

    @Test
    fun `asks only once`() {
        assertFalse(RatingPrompt.shouldAsk(ready.copy(alreadyAsked = true), now))
    }

    @Test
    fun `never shows without the store`() {
        assertFalse(RatingPrompt.shouldAsk(ready.copy(storeInstalled = false), now))
    }
}
