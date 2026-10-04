package ir.asudehapp.sms.data

import ir.asudehapp.sms.model.SearchKind
import ir.asudehapp.sms.model.SearchScope
import ir.asudehapp.sms.persian.SearchText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * همان SQL جستجوی دامنه‌دار [SearchSql] روی sqlite واقعی در JVM (مثل
 * `ThreadSummaryTriggerTest`)، با یک جدول FTS4 که rowidش با `message` یکی است.
 * نشان می‌دهد هر فیلتر درست کار می‌کند و متن یا سرشماره‌ای که عملگر FTS یا
 * نقل‌قول دارد، نه خطا می‌دهد و نه چیز بیشتری پیدا می‌کند (ROADMAP E6).
 */
class ScopedSearchTest {

    private lateinit var conn: Connection

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE message (
                    kind TEXT NOT NULL, providerId INTEGER NOT NULL, threadId INTEGER NOT NULL,
                    normalizedAddress TEXT NOT NULL, body TEXT NOT NULL, dateReceived INTEGER NOT NULL,
                    folder TEXT NOT NULL, category TEXT NOT NULL, attachments INTEGER NOT NULL,
                    PRIMARY KEY (kind, providerId)
                )
                """.trimIndent(),
            )
            st.execute("CREATE VIRTUAL TABLE message_fts USING fts4(searchText)")
        }
        add(1, thread = 1, sender = "989121111111", body = "سلام فردا میای", folder = "INBOX", category = "PERSONAL")
        add(2, thread = 1, sender = "989121111111", body = "عکس فردا", folder = "INBOX", category = "PERSONAL", attachments = 1)
        add(3, thread = 2, sender = "bank", body = "واریز فردا", folder = "INBOX", category = "BANK")
        add(4, thread = 3, sender = "otp", body = "رمز فردا 1234", folder = "INBOX", category = "OTP")
        add(5, thread = 4, sender = "5000", body = "تخفیف فردا", folder = "PROMO", category = "PROMO")
    }

    @After
    fun tearDown() = conn.close()

    @Suppress("LongParameterList")
    private fun add(
        id: Long,
        thread: Long,
        sender: String,
        body: String,
        folder: String,
        category: String,
        attachments: Int = 0,
    ) {
        conn.prepareStatement("INSERT INTO message VALUES ('SMS', ?, ?, ?, ?, ?, ?, ?, ?)").use {
            it.setLong(1, id)
            it.setLong(2, thread)
            it.setString(3, sender)
            it.setString(4, body)
            it.setLong(5, id)
            it.setString(6, folder)
            it.setString(7, category)
            it.setInt(8, attachments)
            it.executeUpdate()
        }
        val rowid = conn.createStatement().use { st ->
            st.executeQuery("SELECT rowid FROM message WHERE providerId = $id").use { it.next(); it.getLong(1) }
        }
        conn.prepareStatement("INSERT INTO message_fts(rowid, searchText) VALUES (?, ?)").use {
            it.setLong(1, rowid)
            it.setString(2, SearchText.of(body, sender))
            it.executeUpdate()
        }
    }

    private fun search(text: String, scope: SearchScope = SearchScope()): List<Long> {
        val match = SearchText.ftsQuery(text)
        val sql = if (match != null) SearchSql.SCOPED_MATCH else SearchSql.SCOPED_ONLY
        return conn.prepareStatement(sql).use { st ->
            var index = 1
            if (match != null) st.setString(index++, match)
            st.setLong(index++, scope.threadId)
            st.setString(index++, scope.senderKey)
            st.setInt(index++, if (scope.onlyAttachments) 1 else 0)
            st.setInt(index++, if (scope.onlyHidden) 1 else 0)
            st.setString(index++, scope.categories)
            st.setInt(index, LIMIT)
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getLong("providerId")) }
            }
        }
    }

    @Test
    fun `every filter narrows the same text`() {
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), search("فردا"))
        assertEquals(listOf(2L), search("فردا", SearchScope(SearchKind.ATTACHMENTS)))
        assertEquals(listOf(4L), search("فردا", SearchScope(SearchKind.OTP)))
        assertEquals(listOf(3L), search("فردا", SearchScope(SearchKind.BANK)))
        assertEquals(listOf(5L), search("فردا", SearchScope(SearchKind.HIDDEN)))
        assertEquals(listOf(2L, 1L), search("فردا", SearchScope(threadId = 1)))
        assertEquals(listOf(3L), search("فردا", SearchScope(sender = "bank")))
    }

    @Test
    fun `a narrowed scope works without text`() {
        assertEquals(listOf(2L), search("", SearchScope(SearchKind.ATTACHMENTS)))
        assertEquals(listOf(2L, 1L), search("  ", SearchScope(threadId = 1)))
    }

    /** پرسش و سرشمارهٔ پر از عملگر و نقل‌قول: نه خطا، نه نتیجهٔ اضافه. */
    @Test
    fun `fts operators in text or sender cannot be injected`() {
        assertEquals(emptyList<Long>(), search("\"فردا\" OR NEAR(تخفیف)", SearchScope(SearchKind.BANK)))
        assertEquals(emptyList<Long>(), search("فردا", SearchScope(sender = "bank' OR '1'='1")))
        assertEquals(emptyList<Long>(), search("فردا", SearchScope(sender = "* OR bank")))
        assertEquals(emptyList<Long>(), search("فردا*", SearchScope(threadId = 99)))
    }

    private companion object {
        const val LIMIT = 50
    }
}
