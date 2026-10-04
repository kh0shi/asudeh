package ir.asudehapp.sms.persian

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class ConversationExportTest {

    private val utc = ZoneId.of("UTC")

    // 2026-09-18 09:05 و 2026-09-19 22:30 به وقت UTC.
    private val first = 1_789_722_300_000L
    private val second = 1_789_857_000_000L

    @Test
    fun `days get a header and lines keep time and sender`() {
        val text = ConversationExport.format(
            "علی",
            listOf(
                ConversationExport.Line(second, "من", "شب بخیر"),
                ConversationExport.Line(first, "علی", "سلام\nخوبی؟"),
            ),
            jalali = false,
            zone = utc,
        )
        assertEquals(
            "علی\n\n— 2026/09/18 —\n[09:05] علی: سلام\n    خوبی؟\n\n— 2026/09/19 —\n[22:30] من: شب بخیر\n",
            text,
        )
    }

    @Test
    fun `jalali headers use the persian calendar`() {
        val text = ConversationExport.format("x", listOf(ConversationExport.Line(first, "a", "b")), jalali = true, zone = utc)
        assertEquals("x\n\n— ۱۴۰۵/۰۶/۲۷ —\n[09:05] a: b\n", text)
    }

    @Test
    fun `an empty conversation is just its title`() {
        assertEquals("x\n", ConversationExport.format("x", emptyList(), jalali = false, zone = utc))
    }
}
