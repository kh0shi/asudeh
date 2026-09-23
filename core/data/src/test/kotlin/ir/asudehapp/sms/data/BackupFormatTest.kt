package ir.asudehapp.sms.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter

class BackupFormatTest {

    private val header = BackupFormat.Header(
        createdAt = 1_700_000_000_000,
        appVersion = "0.2.0",
        settings = mapOf("digest" to "WEEKLY"),
        rules = listOf(BackupFormat.Rule("30001234", "BLOCK", 5)),
    )

    @Test
    fun `backup round trips header and messages, including newlines in bodies`() {
        val out = StringWriter()
        BackupFormat.writeHeader(out, header)
        val sms = listOf(
            BackupFormat.Sms("09121111111", "سلام\nخط دوم «نقل‌قول»", 10, 9, type = 1, read = false, folder = "INBOX"),
            BackupFormat.Sms("30001234", "تخفیف", 20, type = 1, folder = "PROMO"),
        )
        sms.forEach { BackupFormat.writeSms(out, it) }

        val reader = StringReader(out.toString()).buffered()
        assertEquals(header, BackupFormat.readHeader(reader))
        assertEquals(sms, BackupFormat.readSms(reader).toList())
    }

    @Test
    fun `a corrupt line is skipped and counted, not fatal`() {
        val text = buildString {
            val out = StringWriter()
            BackupFormat.writeHeader(out, header)
            BackupFormat.writeSms(out, BackupFormat.Sms("a", "b", 1, type = 1))
            append(out)
            append("{not json\n")
            val tail = StringWriter()
            BackupFormat.writeSms(tail, BackupFormat.Sms("c", "d", 2, type = 2))
            append(tail)
        }
        val reader = StringReader(text).buffered()
        BackupFormat.readHeader(reader)
        var corrupt = 0
        val read = BackupFormat.readSms(reader) { corrupt++ }.toList()
        assertEquals(listOf("a", "c"), read.map { it.address })
        assertEquals(1, corrupt)
    }

    @Test
    fun `other files and newer versions are refused`() {
        val notBackup = StringReader("{\"hello\":1}\n").buffered()
        assertEquals(
            BackupException.Reason.NOT_A_BACKUP,
            assertThrows(BackupException::class.java) { BackupFormat.readHeader(notBackup) }.reason,
        )
        val newer = StringWriter().also { BackupFormat.writeHeader(it, header.copy(version = 99)) }
        assertEquals(
            BackupException.Reason.NEWER_VERSION,
            assertThrows(BackupException::class.java) {
                BackupFormat.readHeader(StringReader(newer.toString()).buffered())
            }.reason,
        )
    }

    @Test
    fun `the same message has the same key whatever the number format`() {
        assertEquals(
            BackupFormat.dedupeKey("+989121111111", 5, 1, "x"),
            BackupFormat.dedupeKey("09121111111", 5, 1, "x"),
        )
    }

    @Test
    fun `an mms round trips including a multi-part attachment`() {
        val out = StringWriter()
        BackupFormat.writeHeader(out, header)
        val photoBytes = byteArrayOf(1, 2, 3, 4, 5, -1, 0, 127)
        val mms = BackupFormat.Mms(
            address = "09121111111",
            recipients = "09121111111|09123333333",
            date = 100,
            dateSent = 90,
            outgoing = false,
            read = false,
            subId = 1,
            folder = "INBOX",
            parts = listOf(
                BackupFormat.Part(
                    contentType = "text/plain",
                    data = BackupFormat.encodePartData("سلام".toByteArray(Charsets.UTF_8)),
                    name = "text.txt",
                ),
                BackupFormat.Part(
                    contentType = "image/jpeg",
                    data = BackupFormat.encodePartData(photoBytes),
                    name = "photo.jpg",
                    contentId = "<photo>",
                    charset = 0,
                ),
            ),
        )
        BackupFormat.writeMms(out, mms)

        val reader = StringReader(out.toString()).buffered()
        assertEquals(header, BackupFormat.readHeader(reader))
        val read = BackupFormat.readMms(reader).toList()
        assertEquals(listOf(mms), read)

        val restoredPhoto = BackupFormat.decodePartData(read.single().parts[1].data)
        assertArrayEquals(photoBytes, restoredPhoto)
        assertEquals("سلام", String(BackupFormat.decodePartData(read.single().parts[0].data), Charsets.UTF_8))
    }

    @Test
    fun `sms and mms lines interleave correctly through readMessages`() {
        val out = StringWriter()
        BackupFormat.writeHeader(out, header)
        val sms = BackupFormat.Sms("09121111111", "متن", 1, type = 1)
        val mms = BackupFormat.Mms(address = "09123333333", date = 2, outgoing = true)
        BackupFormat.writeSms(out, sms)
        BackupFormat.writeMms(out, mms)

        val reader = StringReader(out.toString()).buffered()
        BackupFormat.readHeader(reader)
        val messages = BackupFormat.readMessages(reader).toList()
        assertEquals(listOf(sms, mms), messages)
    }

    @Test
    fun `a version-1 sms-only backup line without kind still decodes as Sms`() {
        // شبیه‌سازی پشتیبان نسخهٔ ۱: خط پیامک بدون کلید «kind».
        val v1Line = """{"address":"09121111111","body":"سلام","date":1,"dateSent":0,"type":1,"read":true,"subId":-1,"folder":null}"""
        val reader = StringReader("$v1Line\n").buffered()
        val messages = BackupFormat.readMessages(reader).toList()
        assertEquals(1, messages.size)
        assertEquals(BackupFormat.Sms("09121111111", "سلام", 1, type = 1), messages.single())
    }
}
