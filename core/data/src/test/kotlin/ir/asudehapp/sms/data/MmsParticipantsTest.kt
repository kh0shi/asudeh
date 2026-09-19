package ir.asudehapp.sms.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MmsParticipantsTest {

    @Test
    fun `one recipient is the user, so the conversation is one to one`() {
        assertEquals(listOf("09121111111"), MmsParticipants.of("09121111111", listOf("09350000000"), emptySet()))
    }

    @Test
    fun `known own number is removed from a group`() {
        assertEquals(
            listOf("09121111111", "09362222222"),
            MmsParticipants.of("09121111111", listOf("+989350000000", "09362222222"), setOf("09350000000")),
        )
    }

    @Test
    fun `unknown own number keeps every member rather than dropping one`() {
        assertEquals(
            listOf("09121111111", "09350000000", "09362222222"),
            MmsParticipants.of("09121111111", listOf("09350000000", "09362222222"), emptySet()),
        )
    }

    @Test
    fun `sender and duplicates are not repeated`() {
        assertEquals(
            listOf("09121111111", "09362222222"),
            MmsParticipants.of("09121111111", listOf("+989121111111", "09362222222", "+989362222222", "09350000000"), setOf("09350000000")),
        )
    }
}
