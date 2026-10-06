package ir.asudehapp.sms.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** الگوی فرستنده (ADR-0016): فقط یک `*` در انتها، با همان یکسان‌سازی سرشماره‌ها. */
class SenderPatternTest {

    @Test
    fun `a trailing star gives the normalized prefix`() {
        assertEquals("5000", SenderPattern.normalize("5000*"))
        assertEquals("5000", SenderPattern.normalize(" ۵۰۰۰* "))
        assertEquals("5000", SenderPattern.normalize("٥٠٠٠*"))
        assertEquals("bank", SenderPattern.normalize("Bank*"))
    }

    @Test
    fun `the country code becomes a leading zero like a full number`() {
        assertEquals("0990", SenderPattern.normalize("+98990*"))
        assertEquals("0990", SenderPattern.normalize("0098990*"))
        assertEquals("0990", SenderPattern.normalize("+98 990*"))
        assertEquals("0990", SenderPattern.normalize("0990*"))
    }

    @Test
    fun `anything but one trailing star is refused`() {
        assertNull(SenderPattern.normalize("5000"))
        assertNull(SenderPattern.normalize("*"))
        assertNull(SenderPattern.normalize(""))
        assertNull(SenderPattern.normalize("50*0*"))
        assertNull(SenderPattern.normalize("*5000"))
        assertNull(SenderPattern.normalize("50?0*"))
        assertNull(SenderPattern.normalize("50|0*"))
        assertNull(SenderPattern.normalize("50**"))
    }

    @Test
    fun `a prefix shorter than three characters is refused`() {
        assertNull(SenderPattern.normalize("50*"))
        assertNull(SenderPattern.normalize("+98*"))
        assertEquals("500", SenderPattern.normalize("500*"))
    }

    @Test
    fun `the longest prefix wins and a tie goes to the allowlist`() {
        val rules = listOf(
            SenderPattern.Rule("5000", "5000*", allow = true),
            SenderPattern.Rule("50001", "50001*", allow = false),
            SenderPattern.Rule("3000", "3000*", allow = false),
            SenderPattern.Rule("3000", "۳۰۰۰*", allow = true),
        )
        assertEquals("50001*", SenderPattern.bestMatch(rules, "500012345")?.pattern)
        assertEquals("5000*", SenderPattern.bestMatch(rules, "50009999")?.pattern)
        assertEquals(true, SenderPattern.bestMatch(rules, "30001234")?.allow)
        assertNull(SenderPattern.bestMatch(rules, "10001234"))
    }

    @Test
    fun `an incoming address is matched in its normalized form`() {
        val rules = listOf(SenderPattern.Rule("0990", "+98990*", allow = false))
        assertEquals("+98990*", SenderPattern.bestMatch(rules, "+989901234567")?.pattern)
        assertEquals("+98990*", SenderPattern.bestMatch(rules, "09901234567")?.pattern)
        assertEquals("+98990*", SenderPattern.bestMatch(rules, "۰۹۹۰۱۲۳۴۵۶۷")?.pattern)
        assertNull(SenderPattern.bestMatch(rules, "09121234567"))
    }
}
