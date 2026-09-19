package ir.asudehapp.sms.data

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
}
