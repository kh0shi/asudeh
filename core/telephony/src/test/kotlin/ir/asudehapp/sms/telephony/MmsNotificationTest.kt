package ir.asudehapp.sms.telephony

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class MmsNotificationTest {

    private fun pdu(block: ByteArrayOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().apply(block).toByteArray()

    private fun ByteArrayOutputStream.bytes(vararg values: Int) = values.forEach { write(it) }

    private fun ByteArrayOutputStream.text(value: String) {
        write(value.toByteArray(Charsets.UTF_8))
        write(0)
    }

    private val notification = pdu {
        bytes(0x8C, 0x82) // X-Mms-Message-Type: m-notification-ind
        bytes(0x98); text("T1234") // X-Mms-Transaction-ID
        bytes(0x8D, 0x92) // X-Mms-MMS-Version: 1.2
        val from = "+989121234567/TYPE=PLMN"
        bytes(0x89, from.length + 2, 0x80); text(from) // From
        bytes(0x96); text("سلام") // Subject
        bytes(0x8A, 0x80) // X-Mms-Message-Class: personal
        bytes(0x8E, 0x02, 0x10, 0x00) // X-Mms-Message-Size: 4096
        bytes(0x88, 0x05, 0x81, 0x03, 0x09, 0x3A, 0x80) // X-Mms-Expiry: +604800 ثانیه
        bytes(0x83); text("http://mmsc.example/abc") // X-Mms-Content-Location
    }

    @Test
    fun `a notification is parsed`() {
        val parsed = MmsNotification.parse(notification, nowSeconds = 1_000)!!
        assertEquals("T1234", parsed.transactionId)
        assertEquals("http://mmsc.example/abc", parsed.contentLocation)
        assertEquals("+989121234567", parsed.from)
        assertEquals("سلام", parsed.subject)
        assertEquals(4096L, parsed.messageSize)
        assertEquals(1_000L + 604_800L, parsed.expirySeconds)
        assertEquals(0x12, parsed.mmsVersion)
    }

    @Test
    fun `a subject with an explicit charset is decoded`() {
        val subject = "خبر".toByteArray(Charsets.UTF_8)
        val parsed = MmsNotification.parse(
            pdu {
                bytes(0x8C, 0x82)
                bytes(0x98); text("T9")
                bytes(0x96, subject.size + 2, 0xEA) // طول، UTF-8 (106)
                write(subject); write(0)
                bytes(0x83); text("http://m/x")
            },
            nowSeconds = 0,
        )
        assertEquals("خبر", parsed?.subject)
    }

    @Test
    fun `unknown headers are skipped`() {
        val parsed = MmsNotification.parse(
            pdu {
                bytes(0x8C, 0x82)
                bytes(0x86, 0x81) // X-Mms-Delivery-Report: no
                bytes(0xA5, 0x03, 0x01, 0x02, 0x03) // سرایند ناشناخته با طول
                bytes(0x98); text("T7")
                bytes(0x83); text("http://m/y")
            },
            nowSeconds = 0,
        )
        assertEquals("http://m/y", parsed?.contentLocation)
    }

    @Test
    fun `another message type is not a notification`() {
        assertNull(MmsNotification.parse(pdu { bytes(0x8C, 0x86); bytes(0x98); text("T") }, 0))
    }

    @Test
    fun `a truncated pdu is rejected instead of crashing`() {
        assertNull(MmsNotification.parse(byteArrayOf(0x8C.toByte()), 0))
    }
}
