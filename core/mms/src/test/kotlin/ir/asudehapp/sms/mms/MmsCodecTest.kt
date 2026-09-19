package ir.asudehapp.sms.mms

import ir.asudehapp.sms.mms.pdu.EncodedStringValue
import ir.asudehapp.sms.mms.pdu.PduComposer
import ir.asudehapp.sms.mms.pdu.PduHeaders
import ir.asudehapp.sms.mms.pdu.PduParser
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
        val response = parsed as ir.asudehapp.sms.mms.pdu.NotifyRespInd
        assertEquals("T12345", String(response.transactionId))
        assertEquals(PduHeaders.STATUS_RETRIEVED, response.status)
    }

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
