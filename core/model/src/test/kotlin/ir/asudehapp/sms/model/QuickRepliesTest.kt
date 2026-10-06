package ir.asudehapp.sms.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** پاسخ‌های آماده‌ای که خود کاربر می‌نویسد (ADR-0017). */
class QuickRepliesTest {

    @Test
    fun `text is trimmed and blank or overlong text is refused`() {
        assertEquals("رسیدم", QuickReplies.clean("  رسیدم \n"))
        assertEquals("خط اول\nخط دوم", QuickReplies.clean("خط اول\r\nخط دوم"))
        assertNull(QuickReplies.clean(""))
        assertNull(QuickReplies.clean(" \n\t "))
        assertEquals(QuickReplies.MAX_LENGTH, QuickReplies.clean("x".repeat(QuickReplies.MAX_LENGTH))?.length)
        assertNull(QuickReplies.clean("x".repeat(QuickReplies.MAX_LENGTH + 1)))
    }

    @Test
    fun `adding appends and refuses blanks, duplicates and a full list`() {
        val one = QuickReplies.add(emptyList(), " رسیدم ")
        assertEquals(listOf("رسیدم"), one)
        assertEquals(listOf("رسیدم", "بعداً زنگ می‌زنم"), QuickReplies.add(one, "بعداً زنگ می‌زنم"))

        assertEquals(one, QuickReplies.add(one, "   "))
        assertEquals(one, QuickReplies.add(one, "رسیدم  "))
        assertEquals(QuickReplies.Problem.BLANK, QuickReplies.problem(one, " "))
        assertEquals(QuickReplies.Problem.DUPLICATE, QuickReplies.problem(one, "رسیدم"))
        assertEquals(QuickReplies.Problem.TOO_LONG, QuickReplies.problem(one, "x".repeat(QuickReplies.MAX_LENGTH + 1)))

        val full = (1..QuickReplies.MAX_COUNT).map { "پاسخ $it" }
        assertEquals(QuickReplies.Problem.FULL, QuickReplies.problem(full, "یکی دیگر"))
        assertEquals(full, QuickReplies.add(full, "یکی دیگر"))
    }

    @Test
    fun `editing keeps the place and allows keeping the same text`() {
        val replies = listOf("الف", "ب", "پ")
        assertEquals(listOf("الف", "ب ب", "پ"), QuickReplies.edit(replies, "ب", " ب ب "))
        assertNull(QuickReplies.problem(replies, "ب ", replacing = "ب"))
        assertEquals(QuickReplies.Problem.DUPLICATE, QuickReplies.problem(replies, "پ", replacing = "ب"))
        assertEquals(replies, QuickReplies.edit(replies, "ب", "پ"))
        assertEquals(replies, QuickReplies.edit(replies, "ت", "ث"))

        // ویرایش در فهرست پر هم ممکن است، چون چیزی اضافه نمی‌شود.
        val full = (1..QuickReplies.MAX_COUNT).map { "پاسخ $it" }
        assertNull(QuickReplies.problem(full, "تازه", replacing = "پاسخ 1"))
        assertEquals("تازه", QuickReplies.edit(full, "پاسخ 1", "تازه").first())
    }

    @Test
    fun `removing and moving stay inside the list`() {
        val replies = listOf("الف", "ب", "پ")
        assertEquals(listOf("الف", "پ"), QuickReplies.remove(replies, "ب"))
        assertEquals(replies, QuickReplies.remove(replies, "ت"))

        assertEquals(listOf("ب", "الف", "پ"), QuickReplies.move(replies, "ب", -1))
        assertEquals(listOf("الف", "پ", "ب"), QuickReplies.move(replies, "ب", 1))
        assertEquals(replies, QuickReplies.move(replies, "الف", -1))
        assertEquals(replies, QuickReplies.move(replies, "پ", 1))
        assertEquals(listOf("ب", "پ", "الف"), QuickReplies.move(replies, "الف", 10))
        assertEquals(replies, QuickReplies.move(replies, "ت", 1))
    }

    @Test
    fun `sanitize and merge keep the first copy and the cap`() {
        assertEquals(listOf("الف", "ب"), QuickReplies.sanitize(listOf(" الف", "", "الف ", "ب")))
        val many = (1..QuickReplies.MAX_COUNT + 5).map { "پاسخ $it" }
        assertEquals(many.take(QuickReplies.MAX_COUNT), QuickReplies.sanitize(many))

        // بازگردانی: پاسخ‌های همین گوشی اول و دست‌نخورده، تازه‌های فایل بعد.
        assertEquals(listOf("ب", "الف", "پ"), QuickReplies.merge(listOf("ب", "الف"), listOf("الف", "پ")))
        assertEquals(emptyList<String>(), QuickReplies.merge(emptyList(), emptyList()))
    }

    @Test
    fun `a reply goes in at the cursor with a space where words would touch`() {
        assertEquals(QuickReplies.Insertion("رسیدم", 5), QuickReplies.insert("", 0, 0, "رسیدم"))
        assertEquals(QuickReplies.Insertion("سلام رسیدم", 10), QuickReplies.insert("سلام", 4, 4, "رسیدم"))
        assertEquals(QuickReplies.Insertion("سلام رسیدم", 10), QuickReplies.insert("سلام ", 5, 5, "رسیدم"))
        assertEquals(QuickReplies.Insertion("ab X cd", 5), QuickReplies.insert("abcd", 2, 2, "X"))
        assertEquals(QuickReplies.Insertion("a X d", 3), QuickReplies.insert("a bc d", 2, 4, "X"))
        assertEquals(QuickReplies.Insertion("X ab", 2), QuickReplies.insert("ab", 0, 0, "X"))
    }

    @Test
    fun `a backwards or out of range selection is tolerated`() {
        assertEquals(QuickReplies.Insertion("a X d", 3), QuickReplies.insert("a bc d", 4, 2, "X"))
        assertEquals(QuickReplies.Insertion("ab X", 4), QuickReplies.insert("ab", 7, 9, "X"))
        assertEquals(QuickReplies.Insertion("X ab", 2), QuickReplies.insert("ab", -3, -1, "X"))
    }
}
