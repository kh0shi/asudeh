package ir.asudehapp.sms.mms

import ir.asudehapp.sms.mms.pdu.AcknowledgeInd
import ir.asudehapp.sms.mms.pdu.EncodedStringValue
import ir.asudehapp.sms.mms.pdu.NotifyRespInd
import ir.asudehapp.sms.mms.pdu.PduComposer
import ir.asudehapp.sms.mms.pdu.PduHeaders
import ir.asudehapp.sms.mms.pdu.PduParser
import ir.asudehapp.sms.mms.pdu.ReadRecInd
import ir.asudehapp.sms.mms.pdu.SendReq
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsCodecTest {

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0, 0x7F, 0xFF.toByte(), 0xD9.toByte())

    @Test
    fun `send request keeps recipients text and image`() {
        val pdu = MmsCodec.composeSendReq(
            recipients = listOf("09121111111", "+989352222222"),
            text = "سلام، عکس جشن",
            attachments = listOf(MmsPart("image/jpeg", jpeg)),
        )
        val parsed = PduParser(pdu).parse() as SendReq

        assertEquals(PduHeaders.MESSAGE_TYPE_SEND_REQ, parsed.messageType)
        assertEquals(listOf("09121111111", "+989352222222"), parsed.to.map { it.string })
        val types = (0 until parsed.body.partsNum).map { String(parsed.body.getPart(it).contentType) }
        assertEquals(listOf("application/smil", "text/plain", "image/jpeg"), types)
        assertEquals("سلام، عکس جشن", String(parsed.body.getPart(1).data, Charsets.UTF_8))
        assertArrayEquals(jpeg, parsed.body.getPart(2).data)
    }

    @Test
    fun `smil references every part by name`() {
        val pdu = MmsCodec.composeSendReq(listOf("0912"), "متن", listOf(MmsPart("image/png", jpeg)))
        val parsed = PduParser(pdu).parse() as SendReq
        val smil = String(parsed.body.getPart(0).data)
        assertTrue(smil, smil.contains("""<text src="text.txt""""))
        assertTrue(smil, smil.contains("""<img src="part0.png""""))
    }

    @Test
    fun `retrieved message is read with sender, group members and parts`() {
        val parsed = MmsCodec.parseRetrieveConf(retrieveConf())
        assertNotNull(parsed)
        parsed!!
        assertEquals("+989121234567", parsed.from)
        assertEquals(listOf("09350000000", "09360000000"), parsed.to)
        assertEquals("عکس ها", parsed.text)
        assertEquals(1, parsed.attachments.size)
        assertTrue(parsed.attachments.single().isImage)
        assertArrayEquals(jpeg, parsed.attachments.single().data)
        assertEquals(1_700_000_000L, parsed.dateSeconds)
    }

    @Test
    fun `corrupt or unrelated pdu is not a retrieved message`() {
        assertNull(MmsCodec.parseRetrieveConf(byteArrayOf(1, 2, 3)))
        val sendReq = MmsCodec.composeSendReq(listOf("0912"), "x", emptyList())
        assertNull(MmsCodec.parseRetrieveConf(sendReq))
    }

    @Test
    fun `notify response acknowledges the transaction`() {
        val pdu = MmsCodec.composeNotifyResp("T12345", PduHeaders.MMS_VERSION_1_2)
        val parsed = PduParser(pdu).parse()
        assertEquals(PduHeaders.MESSAGE_TYPE_NOTIFYRESP_IND, parsed.messageType)
        val response = parsed as NotifyRespInd
        assertEquals("T12345", String(response.transactionId))
        assertEquals(PduHeaders.STATUS_RETRIEVED, response.status)
    }

    /**
     * بایت‌های مرجع از جدول‌های OMA-TS-MMS-ENC §7 (همان کدهایی که `PduComposer`
     * AOSP می‌نویسد): نوع پیام 0x8C، شناسهٔ تراکنش 0x98، نسخه 0x8D (۱٫۲ یعنی
     * 0x92)، اجازهٔ گزارش 0x91 با «No» یعنی 0x81.
     */
    @Test
    fun `acknowledge matches the reference bytes`() {
        val pdu = MmsCodec.composeAcknowledge("T12345", PduHeaders.MMS_VERSION_1_2)
        val expected = bytes(0x8C, 0x85, 0x98) + "T12345".toByteArray() + bytes(0x00, 0x8D, 0x92, 0x91, 0x81)
        assertArrayEquals(expected, pdu)
        val parsed = PduParser(pdu).parse() as AcknowledgeInd
        assertEquals("T12345", String(parsed.transactionId))
    }

    @Test
    fun `acknowledge falls back to the current version`() {
        val pdu = MmsCodec.composeAcknowledge("T1", 0)
        assertEquals(0x92, pdu[pdu.size - 3].toInt() and 0xFF)
    }

    @Test
    fun `deferred notify response carries the deferred status`() {
        val pdu = MmsCodec.composeNotifyResp("T9", PduHeaders.MMS_VERSION_1_2, PduHeaders.STATUS_DEFERRED)
        val parsed = PduParser(pdu).parse() as NotifyRespInd
        assertEquals(PduHeaders.STATUS_DEFERRED, parsed.status)
    }

    /**
     * گزارش خوانده‌شدن: نسخه، Message-ID (0x8B)، To (0x97، Encoded-string با طول
     * 0x17 و نویسه‌گذاری UTF-8 یعنی 0xEA، و نوع نشانی PLMN)،
     * From (0x89) با insert-address-token یعنی طول ۱ و 0x81، Date (0x85، عدد
     * بلند با یک بایت طول) و X-Mms-Read-Status (0x9B) با «Read» یعنی 0x80.
     */
    @Test
    fun `read report matches the reference bytes`() {
        val pdu = MmsCodec.composeReadRec("MID-1", "09121234567", dateSeconds = 0x01020304L, PduHeaders.MMS_VERSION_1_2)
        val expected = bytes(0x8C, 0x87, 0x8D, 0x92, 0x8B) + "MID-1".toByteArray() + bytes(0x00, 0x97, 0x17, 0xEA) +
            "09121234567/TYPE=PLMN".toByteArray() + bytes(0x00, 0x89, 0x01, 0x81, 0x85, 0x04, 0x01, 0x02, 0x03, 0x04, 0x9B, 0x80)
        assertArrayEquals(expected, pdu)
        val parsed = PduParser(pdu).parse() as ReadRecInd
        assertEquals("MID-1", String(parsed.messageId))
        assertEquals(PduHeaders.READ_STATUS_READ, parsed.readStatus)
    }

    @Test
    fun `read report request is read from the retrieved message`() {
        val plain = MmsCodec.parseRetrieveConf(retrieveConf())
        assertEquals(false, plain?.readReportRequested)
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    /**
     * یک `M-Retrieve.conf` واقعی از روی `M-Send.req`: هر دو بدنهٔ multipart یکسانی
     * دارند و فقط نوع پیام فرق می‌کند (OMA-TS-MMS-ENC §6.3). PduComposer خود
     * `Retrieve.conf` نمی‌سازد، چون گوشی هرگز آن را نمی‌فرستد.
     */
    private fun retrieveConf(): ByteArray {
        val request = SendReq()
        request.from = EncodedStringValue("+989121234567/TYPE=PLMN")
        request.to = arrayOf(EncodedStringValue("09350000000/TYPE=PLMN"), EncodedStringValue("09360000000"))
        request.date = 1_700_000_000L
        val body = PduParser(
            MmsCodec.composeSendReq(listOf("x"), "عکس ها", listOf(MmsPart("image/jpeg", jpeg))),
        ).parse() as SendReq
        request.body = body.body
        val bytes = PduComposer(request).make()
        // سرایند اول همیشه نوع پیام است: 0x8C و بعد مقدار.
        assertEquals(0x8C, bytes[0].toInt() and 0xFF)
        bytes[1] = PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF.toByte()
        return bytes
    }
}
