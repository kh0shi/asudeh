package ir.asudehapp.sms.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkGuardTest {

    @Test
    fun `finds a link inside persian text`() {
        val links = LinkGuard.extract("برای پیگیری به tracking.post.ir مراجعه کنید.")
        assertEquals(listOf("tracking.post.ir"), links.map { it.host })
    }

    @Test
    fun `finds a link with a scheme and a path`() {
        val links = LinkGuard.extract("وارد شوید https://bmi-ir.com/login و رمز را بزنید")
        assertEquals(listOf("bmi-ir.com"), links.map { it.host })
    }

    @Test
    fun `does not treat an ordinary sentence as a link`() {
        assertTrue(LinkGuard.extract("سلام.خوبی؟ فردا میبینمت").isEmpty())
    }

    @Test
    fun `flags punycode hosts`() {
        val link = LinkGuard.extract("وارد شوید xn--80ak6aa92e.com/a").single()
        assertTrue(link.isPunycode)
    }

    @Test
    fun `the official domain and its subdomains are not lookalikes`() {
        assertFalse(LinkGuard.isLookalike("bmi.ir", "bmi.ir"))
        assertFalse(LinkGuard.isLookalike("www.bmi.ir", "bmi.ir"))
        assertFalse(LinkGuard.isLookalike("login.bmi.ir", "bmi.ir"))
    }

    @Test
    fun `a dash instead of a dot is a lookalike`() {
        assertTrue(LinkGuard.isLookalike("bmi-ir.com", "bmi.ir"))
    }

    @Test
    fun `the official domain used as a subdomain of another one is a lookalike`() {
        assertTrue(LinkGuard.isLookalike("bmi.ir.secure-login.com", "bmi.ir"))
    }

    @Test
    fun `a close misspelling is a lookalike`() {
        assertTrue(LinkGuard.isLookalike("irancel.ir", "irancell.ir"))
    }

    @Test
    fun `an unrelated domain is not a lookalike`() {
        assertFalse(LinkGuard.isLookalike("digikala.com", "bmi.ir"))
        assertFalse(LinkGuard.isLookalike("shatel.ir", "irancell.ir"))
    }

    @Test
    fun `registrable domain understands iranian second level suffixes`() {
        assertEquals("post.ir", LinkGuard.registrableDomain("tracking.post.ir"))
        assertEquals("sharif.ac.ir", LinkGuard.registrableDomain("ce.sharif.ac.ir"))
    }

    @Test
    fun `rn is normalised to m for comparison`() {
        assertEquals(LinkGuard.normalizeForComparison("rnci.ir"), "mci.ir")
    }
}
