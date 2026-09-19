package ir.asudehapp.sms.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpCodeTest {

    @Test
    fun `single code is extracted`() {
        assertEquals("48213", OtpCode.extract("کد تایید شما: 48213\nبانک ملت"))
    }

    @Test
    fun `persian digits are copied as latin digits`() {
        assertEquals("48213", OtpCode.extract("رمز یکبار مصرف ۴۸۲۱۳ است"))
    }

    @Test
    fun `amount is not mistaken for the code`() {
        assertEquals(
            "5521",
            OtpCode.extract("خرید 250000 ریال\nرمز پویا: 5521\nاعتبار تا 12:30"),
        )
    }

    @Test
    fun `code nearest after the keyword wins`() {
        assertEquals("739104", OtpCode.extract("سفارش 1234 ثبت شد. کد ورود: 739104"))
    }

    @Test
    fun `card and time fragments are not codes`() {
        assertNull(OtpCode.extract("کارت 6037-9971 ساعت 12:30"))
    }

    @Test
    fun `ambiguous numbers without keyword give no button`() {
        assertNull(OtpCode.extract("1234 و 5678"))
    }
}
