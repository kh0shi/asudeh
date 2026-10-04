package ir.asudehapp.sms.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** بازه‌های لمس‌شدنی لینک و دامنهٔ واقعی‌شان (D37، ROADMAP C2). */
class LinkSpanTest {

    private fun only(body: String): LinkSpan = LinkGuard.spans(body).single()

    @Test
    fun `link inside persian text keeps its exact range`() {
        val body = "برای پیگیری به tracking.post.ir مراجعه کنید."
        val span = only(body)
        assertEquals("tracking.post.ir", body.substring(span.start, span.end))
        assertEquals("tracking.post.ir", span.text)
        assertEquals("tracking.post.ir", span.realHost)
        assertEquals("http://tracking.post.ir", span.openUrl)
        assertFalse(span.deceptive)
    }

    @Test
    fun `the path is part of the link but the full stop after it is not`() {
        val body = "وارد شوید: https://bmi-ir.com/login?id=12."
        val span = only(body)
        assertEquals("https://bmi-ir.com/login?id=12", span.text)
        assertEquals(span.text, span.openUrl)
        assertEquals("bmi-ir.com", span.realHost)
    }

    @Test
    fun `persian punctuation ends the link`() {
        val span = only("لینک (bmi.ir/a)، فوری")
        assertEquals("bmi.ir/a", span.text)
    }

    @Test
    fun `latin link next to persian guillemets`() {
        val span = only("نشانی «www.digikala.com/p/1» را ببینید")
        assertEquals("www.digikala.com/p/1", span.text)
        assertEquals("digikala.com", span.realHost)
        assertEquals("http://www.digikala.com/p/1", span.openUrl)
    }

    @Test
    fun `punycode host is deceptive and shown as is`() {
        val span = only("وارد شوید xn--80ak6aa92e.com/a")
        assertTrue(span.deceptive)
        assertEquals("xn--80ak6aa92e.com", span.realHost)
    }

    @Test
    fun `cyrillic lookalike is shown in its real punycode form`() {
        // «і» سیریلیک به‌جای i لاتین.
        val span = only("حساب شما مسدود شد https://bmі.ir/x")
        assertTrue(span.deceptive)
        assertTrue(span.realHost.startsWith("xn--"))
        assertFalse(span.realHost == "bmi.ir")
    }

    @Test
    fun `rn instead of m is shown exactly as written`() {
        // «rn» شبیه «m» است؛ برگه همان brni.ir را نشان می‌دهد و شباهتش را LinkGuard.isLookalike می‌گیرد.
        val span = only("کارت شما brni.ir/pay")
        assertEquals("brni.ir", span.realHost)
        assertTrue(LinkGuard.isLookalike(span.realHost, "bmi.ir"))
    }

    @Test
    fun `several links keep their own ranges`() {
        val body = "اول bmi.ir بعد https://sadad.ir/x"
        val spans = LinkGuard.spans(body)
        assertEquals(listOf("bmi.ir", "https://sadad.ir/x"), spans.map { body.substring(it.start, it.end) })
    }

    @Test
    fun `an ordinary sentence has no links`() {
        assertTrue(LinkGuard.spans("سلام.خوبی؟ فردا میبینمت").isEmpty())
    }
}
