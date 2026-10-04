package ir.asudehapp.sms.persian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsLengthTest {

    @Test
    fun `persian fits seventy in one part`() {
        val length = SmsLength.of("س".repeat(70))
        assertTrue(length.unicode)
        assertEquals(SmsLength(70, 70, 1, true), length)
    }

    @Test
    fun `seventy one persian characters take two parts of sixty seven`() {
        assertEquals(SmsLength(71, 134, 2, true), SmsLength.of("س".repeat(71)))
    }

    @Test
    fun `one hundred thirty four still two parts, one more makes three`() {
        assertEquals(2, SmsLength.of("س".repeat(134)).segments)
        assertEquals(SmsLength(135, 201, 3, true), SmsLength.of("س".repeat(135)))
    }

    @Test
    fun `latin uses gsm seven limits`() {
        assertEquals(SmsLength(160, 160, 1, false), SmsLength.of("a".repeat(160)))
        assertEquals(SmsLength(161, 306, 2, false), SmsLength.of("a".repeat(161)))
    }

    @Test
    fun `one persian letter in latin text makes the whole message unicode`() {
        val length = SmsLength.of("a".repeat(69) + "ب")
        assertTrue(length.unicode)
        assertEquals(1, length.segments)
        assertEquals(2, SmsLength.of("a".repeat(70) + "ب").segments)
    }

    @Test
    fun `persian digits are unicode, latin digits are not`() {
        assertTrue(SmsLength.of("۱۲۳").unicode)
        assertFalse(SmsLength.of("123").unicode)
    }

    @Test
    fun `gsm extension characters count twice`() {
        assertEquals(2, SmsLength.of("€").used)
        assertEquals(SmsLength(162, 306, 2, false), SmsLength.of("a".repeat(158) + "{}"))
    }

    @Test
    fun `emoji counts as two utf16 units`() {
        assertEquals(SmsLength(2, 70, 1, true), SmsLength.of("😀"))
    }

    @Test
    fun `empty text is one empty part`() {
        assertEquals(SmsLength(0, 160, 1, false), SmsLength.of(""))
    }
}
