package ir.asudehapp.sms.data

import ir.asudehapp.sms.model.Folder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashAndScheduleTest {

    private fun trashed(kind: String) = TrashedMessageEntity(
        kind = kind,
        providerId = 7,
        address = "0912",
        recipients = "",
        body = "سلام",
        date = 1,
        dateReceived = 1,
        subId = -1,
        folder = Folder.INBOX,
        read = true,
        outgoing = false,
        attachments = 0,
        deletedAt = 2,
    )

    /** پیامک متنی برمی‌گردد؛ MMS نه، چون partهایش با حذف از بین رفته‌اند. */
    @Test
    fun `only sms can be restored`() {
        assertTrue(trashed(MessageEntity.KIND_SMS).restorable)
        assertFalse(trashed(MessageEntity.KIND_MMS).restorable)
    }

    @Test
    fun `a scheduled message splits its recipients`() {
        val one = scheduled("0912")
        assertEquals(listOf("0912"), one.addresses)

        val group = scheduled("0912${MessageEntity.RECIPIENT_SEPARATOR}0913")
        assertEquals(listOf("0912", "0913"), group.addresses)
    }

    private fun scheduled(recipients: String) = ScheduledMessageEntity(
        threadId = 1,
        recipients = recipients,
        body = "سلام",
        subId = -1,
        sendAt = 100,
        createdAt = 1,
        state = ScheduleState.WAITING,
    )
}
