package ir.asudehapp.sms.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchScopeTest {

    @Test
    fun `default scope narrows nothing`() {
        val scope = SearchScope()
        assertFalse(scope.narrowed)
        assertEquals("", scope.categories)
        assertEquals("", scope.senderKey)
    }

    @Test
    fun `each kind maps to its own condition`() {
        assertEquals(",OTP,", SearchScope(SearchKind.OTP).categories)
        assertEquals(",BANK,", SearchScope(SearchKind.BANK).categories)
        assertTrue(SearchScope(SearchKind.ATTACHMENTS).onlyAttachments)
        assertTrue(SearchScope(SearchKind.HIDDEN).onlyHidden)
        assertTrue(SearchScope(SearchKind.HIDDEN).narrowed)
    }

    @Test
    fun `sender is normalized like the index`() {
        val scope = SearchScope(sender = "0912 123 4567")
        assertEquals(Addresses.normalize("09121234567"), scope.senderKey)
        assertTrue(scope.narrowed)
        assertFalse(SearchScope(sender = "   ").narrowed)
    }

    @Test
    fun `one conversation narrows`() {
        assertTrue(SearchScope(threadId = 7).narrowed)
    }
}
