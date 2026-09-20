package ir.asudehapp.sms.persian

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** قاعدهٔ تطبیق کلیدواژه‌های «قواعد من» (ADR-0012). */
class KeywordMatchTest {

    private fun matches(keyword: String, body: String): Boolean {
        val pattern = KeywordMatch.compile(keyword) ?: return false
        return pattern.containsMatchIn(KeywordMatch.key(body))
    }

    @Test
    fun `spaces and half spaces do not matter`() {
        assertTrue(matches("لغو ۱۱", "برای لغو11 عدد یک را بفرستید"))
        assertTrue(matches("لغو۱۱", "برای لغو ۱۱ عدد یک را بفرستید"))
        assertTrue(matches("کد ورود", "کدورود شما ۱۲۳۴ است"))
    }

    @Test
    fun `persian and latin digits are the same`() {
        assertTrue(matches("لغو 11", "لغو۱۱"))
        assertTrue(matches("تخفیف ۵۰", "تخفیف 50 درصدی"))
    }

    @Test
    fun `upper and lower case are the same`() {
        assertTrue(matches("Code", "Your CODE is 1234"))
        assertTrue(matches("CODE", "your code is 1234"))
    }

    @Test
    fun `arabic ye and ke are the same as persian`() {
        assertTrue(matches("تخفيف", "تخفیف ویژه"))
    }

    @Test
    fun `a keyword that is not there does not match`() {
        assertFalse(matches("تخفیف", "رمز یکبار مصرف شما ۱۲۳۴ است"))
    }

    @Test
    fun `the link placeholder matches a web address right after the word`() {
        assertTrue(matches("لغو{لینک}", "فروش ویژه! لغو http://ex.ir/u"))
        assertTrue(matches("لغو{لینک}", "برای لغو عضویت به ex.ir بروید"))
    }

    @Test
    fun `the link placeholder does not match a word with no link`() {
        assertFalse(matches("لغو{لینک}", "برای لغو عضویت با پشتیبانی تماس بگیرید"))
    }

    @Test
    fun `the link placeholder does not reach across a whole message`() {
        val far = "لغو" + "الف".repeat(40) + "ex.ir"
        assertFalse("فاصلهٔ لینک از واژه محدود است", matches("لغو{لینک}", far))
    }

    @Test
    fun `a keyword with nothing but spaces has no pattern`() {
        assertNull(KeywordMatch.compile("   "))
        assertNotNull(KeywordMatch.compile("تخفیف"))
    }

    @Test
    fun `a keyword with regex characters is taken literally`() {
        assertTrue(matches("۵۰%", "۵۰% تخفیف"))
        assertFalse(matches("a.c", "abc"))
    }
}
