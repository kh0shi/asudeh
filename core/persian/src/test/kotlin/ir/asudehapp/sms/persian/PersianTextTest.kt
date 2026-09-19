package ir.asudehapp.sms.persian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersianTextTest {

    @Test
    fun `unifies arabic yeh and kaf`() {
        assertEquals(PersianText.normalize("کیفیت"), PersianText.normalize("كيفيت"))
    }

    @Test
    fun `converts persian and arabic digits to latin`() {
        assertEquals("3000123456", PersianText.normalize("۳۰۰۰۱۲۳۴۵۶"))
        assertEquals("1234", PersianText.normalize("١٢٣٤"))
    }

    @Test
    fun `removes diacritics and tatweel`() {
        assertEquals("سلام", PersianText.normalize("سـلامٌ"))
    }

    @Test
    fun `turns zero width non joiner into a space and collapses runs`() {
        assertEquals("می خواهم بروم", PersianText.normalize("می‌خواهم   بروم"))
    }

    @Test
    fun `latin digits leaves the rest of the text untouched`() {
        assertEquals("رمز: 4821 را وارد کنید", PersianText.latinDigits("رمز: ۴۸۲۱ را وارد کنید"))
    }

    @Test
    fun `persian digits are used for the interface only`() {
        assertEquals("۱۴۰۵/۰۶/۲۷", PersianText.persianDigits("1405/06/27"))
    }

    @Test
    fun `direction follows the first strong character`() {
        assertTrue(PersianText.isRightToLeft("سلام hello"))
        assertFalse(PersianText.isRightToLeft("hello سلام"))
        assertTrue(PersianText.isRightToLeft("۱۲۳ سلام"))
    }

    @Test
    fun `invisible characters are removed`() {
        assertEquals("تخفیف", PersianText.normalize("ت\u200Bخ\u2060فی\u00ADف\u2067"))
    }

    @Test
    fun `whole words only`() {
        assertTrue(PersianText.containsWord("مانده حساب", "مانده"))
        assertTrue(PersianText.containsWord("کد تخفیف!", "تخفیف"))
        assertFalse(PersianText.containsWord("کالای کوچک", "چک"))
        assertFalse(PersianText.containsWord("please submit", "bmi"))
        assertFalse(PersianText.containsWord("فروش اقساطی", "اقساط"))
    }
}
