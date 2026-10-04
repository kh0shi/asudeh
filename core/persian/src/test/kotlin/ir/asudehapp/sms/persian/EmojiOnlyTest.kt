package ir.asudehapp.sms.persian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmojiOnlyTest {

    @Test
    fun `one to three emoji count`() {
        assertEquals(1, EmojiOnly.count("😀"))
        assertEquals(2, EmojiOnly.count("😀❤️"))
        assertEquals(3, EmojiOnly.count("🌹 🌹 🌹"))
        assertEquals(1, EmojiOnly.count("  👍\n"))
    }

    @Test
    fun `a cluster counts once`() {
        assertEquals(1, EmojiOnly.count("👍🏽"))
        assertEquals(1, EmojiOnly.count("👨‍👩‍👧"))
        assertEquals(1, EmojiOnly.count("🇮🇷"))
        assertEquals(1, EmojiOnly.count("1️⃣"))
        assertEquals(2, EmojiOnly.count("🏳️‍🌈🇮🇷"))
    }

    @Test
    fun `more than three or any text is a normal message`() {
        assertNull(EmojiOnly.count("😀😀😀😀"))
        assertNull(EmojiOnly.count("سلام 😀"))
        assertNull(EmojiOnly.count("ok👍"))
        assertNull(EmojiOnly.count("1"))
        assertNull(EmojiOnly.count("۱"))
        assertNull(EmojiOnly.count(""))
        assertNull(EmojiOnly.count("   "))
        assertNull(EmojiOnly.count("🇮"))
        assertNull(EmojiOnly.count("‍"))
    }
}
