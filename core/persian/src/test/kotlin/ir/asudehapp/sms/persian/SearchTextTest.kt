package ir.asudehapp.sms.persian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTextTest {

    @Test
    fun `punctuation does not stick to words`() {
        assertEquals("سلام دوست من", SearchText.of("«سلام»، دوست من!"))
    }

    @Test
    fun `yeh kaf digits and zwnj are unified`() {
        assertEquals(
            SearchText.of("كيك ۱۲۳ می‌روم"),
            SearchText.of("کیک 123 می روم"),
        )
    }

    @Test
    fun `body and address are both searchable`() {
        assertEquals("کد 1234 09121234567", SearchText.of("کد ۱۲۳۴", "09121234567"))
    }

    @Test
    fun `query uses prefix match on every word`() {
        assertEquals("تخف* 50*", SearchText.ftsQuery(" تخف  ۵۰ "))
    }

    @Test
    fun `fts operators and quotes cannot be injected`() {
        assertEquals("a* or* b*", SearchText.ftsQuery("\"a\" OR b"))
        assertEquals("near* x*", SearchText.ftsQuery("NEAR(x)"))
    }

    @Test
    fun `empty query has nothing to search`() {
        assertNull(SearchText.ftsQuery("  ،؛ !  "))
    }
}
