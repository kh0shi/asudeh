package ir.asudehapp.sms.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsUriTest {

    @Test
    fun `a single recipient`() {
        assertEquals(SmsUri(listOf("09121234567"), null), SmsUri.parse("smsto:09121234567"))
    }

    @Test
    fun `an international number keeps its plus sign`() {
        assertEquals(listOf("+989121234567"), SmsUri.parse("sms:+989121234567")?.recipients)
        assertEquals(listOf("+989121234567"), SmsUri.parse("sms:%2B989121234567")?.recipients)
    }

    @Test
    fun `several recipients and a body`() {
        val parsed = SmsUri.parse("smsto:0912,0935?body=%D8%B3%D9%84%D8%A7%D9%85")
        assertEquals(listOf("0912", "0935"), parsed?.recipients)
        assertEquals("سلام", parsed?.body)
    }

    @Test
    fun `another scheme is not an sms address`() {
        assertNull(SmsUri.parse("tel:0912"))
    }

    @Test
    fun `addresses are unified across formats`() {
        assertEquals("09121234567", Addresses.normalize("+98 912 123 4567"))
        assertEquals("irancell", Addresses.normalize("Irancell"))
    }
}
