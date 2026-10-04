package ir.asudehapp.sms.persian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyableNumbersTest {

    // شمارهٔ کارت آزمایشی با Luhn درست، و شبای نمونه با mod-97 درست.
    private val card = "6037991234567893"
    private val sheba = "IR062960000000100324200001"

    @Test
    fun `luhn and mod97 accept valid numbers`() {
        assertTrue(CopyableNumbers.luhnValid(card))
        assertTrue(CopyableNumbers.shebaValid(sheba.removePrefix("IR")))
    }

    @Test
    fun `card with persian digits and spaces`() {
        val body = "واریز به کارت ۶۰۳۷ ۹۹۱۲ ۳۴۵۶ ۷۸۹۳ انجام شد"
        val found = CopyableNumbers.find(body).single()
        assertEquals(CopyKind.CARD, found.kind)
        assertEquals(card, found.value)
        assertEquals("۶۰۳۷ ۹۹۱۲ ۳۴۵۶ ۷۸۹۳", body.substring(found.start, found.end))
    }

    @Test
    fun `card with dashes and latin digits`() {
        assertEquals(card, CopyableNumbers.find("card 6037-9912-3456-7893.").single().value)
    }

    @Test
    fun `card with wrong luhn gets no chip`() {
        assertTrue(CopyableNumbers.find("6037 9912 3456 7894").isEmpty())
    }

    @Test
    fun `sheba with spaces and persian digits`() {
        val body = "شبا: IR۰۶ ۲۹۶۰ ۰۰۰۰ ۰۰۱۰ ۰۳۲۴ ۲۰۰۰ ۰۱"
        val found = CopyableNumbers.find(body).single()
        assertEquals(CopyKind.SHEBA, found.kind)
        assertEquals(sheba, found.value)
    }

    @Test
    fun `sheba with wrong check digits gets no chip`() {
        assertTrue(CopyableNumbers.find("IR072960000000100324200001").isEmpty())
    }

    @Test
    fun `mobile numbers in every form`() {
        val values = CopyableNumbers.find("۰۹۱۲۱۲۳۴۵۶۷ یا +98 912 123 4567 یا 0912-123-4567")
            .map { it.kind to it.value }
        assertEquals(
            listOf(
                CopyKind.PHONE to "09121234567",
                CopyKind.PHONE to "+989121234567",
                CopyKind.PHONE to "09121234567",
            ),
            values,
        )
    }

    @Test
    fun `landline with area code`() {
        assertEquals("02112345678", CopyableNumbers.find("تلفن: ۰۲۱-۱۲۳۴۵۶۷۸").single().value)
    }

    @Test
    fun `plain numbers and amounts are not chips`() {
        assertTrue(CopyableNumbers.find("مبلغ 1,500,000 ریال، کد 12345، شماره 3000123456").isEmpty())
        assertFalse(CopyableNumbers.find("کد پیگیری 98765432101234").any { it.kind == CopyKind.PHONE })
    }

    @Test
    fun `sorted by position`() {
        val found = CopyableNumbers.find("تماس 09121234567 کارت $card")
        assertEquals(listOf(CopyKind.PHONE, CopyKind.CARD), found.map { it.kind })
    }
}
