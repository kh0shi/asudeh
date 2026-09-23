package ir.asudehapp.sms.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * آزمون واقعیِ triggerهای `thread_summary` (`IndexMigrations.V5_V6_SQL`) روی
 * یک sqlite واقعی در JVM، با راه‌انداز `org.xerial:sqlite-jdbc` — نه
 * Room/اندروید. این ماژول تا امروز هیچ آزمون Room در سطح JVM نداشت (نه
 * Robolectric، نه `androidx.room:room-testing`)، و راه انداختنش فقط برای این
 * یک آزمون کار زیادی می‌بود؛ چون خود چیزی که باید بررسی شود «آیا aggregateهای
 * نگه‌داشته‌شده با محاسبهٔ مستقیم از `message` یکی می‌مانند» است، نه رفتار
 * Room، اجرای مستقیم همان دستورهای SQL migration روی یک اتصال sqlite واقعی
 * دقیقاً همان چیزی را می‌سنجد که مهم است: خود triggerها.
 *
 * `IndexMigrationsTest` جداگانه بررسی می‌کند که `CREATE TABLE`/`CREATE INDEX`
 * با schema صادرشدهٔ Room یکی‌اند؛ اینجا فقط رفتار triggerها روی داده‌های
 * واقعی سنجیده می‌شود.
 */
class ThreadSummaryTriggerTest {

    private lateinit var conn: Connection

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use { st ->
            // فقط ستون‌هایی از `message` که triggerهای V5_V6 به آن‌ها نیاز دارند؛
            // بقیهٔ ستون‌های MessageEntity برای این آزمون لازم نیستند.
            st.execute(
                """
                CREATE TABLE `message` (
                    `kind` TEXT NOT NULL,
                    `providerId` INTEGER NOT NULL,
                    `threadId` INTEGER NOT NULL,
                    `address` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `dateReceived` INTEGER NOT NULL,
                    `folder` TEXT NOT NULL,
                    `read` INTEGER NOT NULL,
                    `outgoing` INTEGER NOT NULL,
                    `risk` INTEGER NOT NULL,
                    `recipients` TEXT NOT NULL DEFAULT '',
                    `attachments` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (`kind`, `providerId`)
                )
                """.trimIndent(),
            )
            for (statement in IndexMigrations.V5_V6_SQL) st.execute(statement)
        }
    }

    @After
    fun tearDown() {
        conn.close()
    }

    private fun insert(
        kind: String,
        providerId: Long,
        threadId: Long,
        address: String,
        body: String,
        dateReceived: Long,
        folder: String,
        read: Boolean,
        outgoing: Boolean = false,
        risk: Boolean = false,
        recipients: String = "",
        attachments: Int = 0,
    ) {
        // `INSERT OR REPLACE` عمداً است، نه `INSERT`: همان چیزی است که
        // `dao.upsert` (`OnConflictStrategy.REPLACE`) در SQLite تولید می‌کند —
        // یک DELETE واقعی به‌دنبالش یک INSERT، نه یک UPDATE. اگر روی همان
        // (kind, providerId) دوباره صدا زده شود، دقیقاً همین مسیر را می‌آزماید.
        conn.prepareStatement(
            "INSERT OR REPLACE INTO `message` " +
                "(`kind`,`providerId`,`threadId`,`address`,`body`,`dateReceived`,`folder`,`read`,`outgoing`," +
                "`risk`,`recipients`,`attachments`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
        ).use { ps ->
            ps.setString(1, kind)
            ps.setLong(2, providerId)
            ps.setLong(3, threadId)
            ps.setString(4, address)
            ps.setString(5, body)
            ps.setLong(6, dateReceived)
            ps.setString(7, folder)
            ps.setInt(8, if (read) 1 else 0)
            ps.setInt(9, if (outgoing) 1 else 0)
            ps.setInt(10, if (risk) 1 else 0)
            ps.setString(11, recipients)
            ps.setInt(12, attachments)
            ps.executeUpdate()
        }
    }

    private fun exec(sql: String) {
        conn.createStatement().use { it.execute(sql) }
    }

    private data class Row(val unread: Int, val total: Int, val hasRisk: Boolean, val snippet: String, val lastDate: Long)

    private fun summaryOf(threadId: Long, folder: String): Row? {
        conn.prepareStatement(
            "SELECT `unread`,`total`,`hasRisk`,`snippet`,`lastDate` FROM `thread_summary` " +
                "WHERE `threadId` = ? AND `folder` = ?",
        ).use { ps ->
            ps.setLong(1, threadId)
            ps.setString(2, folder)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return Row(rs.getInt(1), rs.getInt(2), rs.getInt(3) != 0, rs.getString(4), rs.getLong(5))
            }
        }
    }

    /** محاسبهٔ مستقیم همان چیزی که قبلاً `observeThreads` هر بار با subquery حساب می‌کرد. */
    private fun recomputeFromMessage(threadId: Long, folder: String): Row {
        conn.prepareStatement(
            """
            SELECT
              (SELECT COUNT(*) FROM `message` WHERE `threadId` = ? AND `folder` = ? AND `read` = 0 AND `outgoing` = 0),
              (SELECT COUNT(*) FROM `message` WHERE `threadId` = ? AND `folder` = ?),
              (SELECT MAX(`risk`) FROM `message` WHERE `threadId` = ? AND `folder` = ?),
              (SELECT `body` FROM `message` WHERE `threadId` = ? AND `folder` = ? ORDER BY `dateReceived` DESC LIMIT 1),
              (SELECT MAX(`dateReceived`) FROM `message` WHERE `threadId` = ? AND `folder` = ?)
            """.trimIndent(),
        ).use { ps ->
            var i = 1
            repeat(5) {
                ps.setLong(i++, threadId)
                ps.setString(i++, folder)
            }
            ps.executeQuery().use { rs ->
                rs.next()
                return Row(rs.getInt(1), rs.getInt(2), rs.getInt(3) != 0, rs.getString(4), rs.getLong(5))
            }
        }
    }

    @Test
    fun `insert creates a summary row immediately`() {
        insert("SMS", 1, 100, "0912", "hi", 1000, "INBOX", read = false)
        val row = summaryOf(100, "INBOX")
        assertEquals(Row(1, 1, false, "hi", 1000), row)
    }

    @Test
    fun `marking a message read updates unread without touching total`() {
        insert("SMS", 1, 100, "0912", "hi", 1000, "INBOX", read = false)
        insert("SMS", 2, 100, "0912", "hi again", 2000, "INBOX", read = false)
        exec("UPDATE `message` SET `read` = 1 WHERE `kind` = 'SMS' AND `providerId` = 1")
        val row = summaryOf(100, "INBOX")!!
        assertEquals(1, row.unread)
        assertEquals(2, row.total)
    }

    @Test
    fun `reclassify (real UPDATE of folder) moves the row between folders`() {
        insert("SMS", 1, 100, "0912", "hi", 1000, "PROMO", read = false)
        exec("UPDATE `message` SET `folder` = 'INBOX' WHERE `kind` = 'SMS' AND `providerId` = 1")
        assertNull("ردیف پوشهٔ قدیمی باید حذف شود چون دیگر پیامکی در آن نیست", summaryOf(100, "PROMO"))
        val row = summaryOf(100, "INBOX")!!
        assertEquals(1, row.total)
    }

    @Test
    fun `deleting the last message of a pair removes its summary row`() {
        insert("SMS", 1, 100, "0912", "hi", 1000, "INBOX", read = false)
        exec("DELETE FROM `message` WHERE `kind` = 'SMS' AND `providerId` = 1")
        assertNull(summaryOf(100, "INBOX"))
    }

    @Test
    fun `deleting one of several messages keeps the rest reflected`() {
        insert("SMS", 1, 100, "0912", "first", 1000, "INBOX", read = false)
        insert("SMS", 2, 100, "0912", "second", 2000, "INBOX", read = false)
        exec("DELETE FROM `message` WHERE `kind` = 'SMS' AND `providerId` = 2")
        val row = summaryOf(100, "INBOX")!!
        assertEquals(1, row.total)
        assertEquals("first", row.snippet)
        assertEquals(1000L, row.lastDate)
    }

    @Test
    fun `risk flag set via insert-or-replace (dao_upsert path) is reflected`() {
        insert("SMS", 1, 100, "0912", "hi", 1000, "INBOX", read = true, risk = false)
        // شبیه‌سازی reclassifyPending: dao.upsert روی همان کلید، که در SQLite
        // یک DELETE و سپس INSERT است، نه UPDATE (به همین دلیل trg_thread_summary_au
        // به‌تنهایی کافی نیست و باید AFTER INSERT هم این حالت را بپوشاند).
        insert("SMS", 1, 100, "0912", "hi", 1000, "INBOX", read = true, risk = true)
        val row = summaryOf(100, "INBOX")!!
        assertEquals(true, row.hasRisk)
        assertEquals(1, row.total)
    }

    @Test
    fun `split thread keeps each folder's row independent (ADR-0005)`() {
        insert("SMS", 1, 100, "0912", "a", 1000, "INBOX", read = false)
        insert("SMS", 2, 100, "0913", "b", 2000, "PROMO", read = false)
        val inbox = summaryOf(100, "INBOX")!!
        val promo = summaryOf(100, "PROMO")!!
        assertEquals(1, inbox.total)
        assertEquals(1, promo.total)
        assertEquals("a", inbox.snippet)
        assertEquals("b", promo.snippet)
    }

    /**
     * رشته‌ای واقعی از عملیات‌ها (درج، خواندن، جابه‌جایی پوشه، حذف) — دقیقاً
     * نوع کارهایی که `AsudehRepository.sync()`/`markThreadRead`/`reclassifyFrom`
     * روی `message` انجام می‌دهند — و مقایسهٔ نتیجهٔ نگه‌داشته‌شده در
     * `thread_summary` با یک محاسبهٔ مستقل از روی خود `message`.
     */
    @Test
    fun `after a sequence of writes the stored summary matches a fresh recompute`() {
        insert("SMS", 1, 200, "0912", "one", 1000, "INBOX", read = false)
        insert("SMS", 2, 200, "0912", "two", 2000, "INBOX", read = false)
        exec("UPDATE `message` SET `read` = 1 WHERE `kind` = 'SMS' AND `providerId` = 1")
        insert("SMS", 3, 200, "0912", "three", 3000, "PROMO", read = false)
        exec("UPDATE `message` SET `folder` = 'INBOX' WHERE `kind` = 'SMS' AND `providerId` = 3")
        exec("DELETE FROM `message` WHERE `kind` = 'SMS' AND `providerId` = 2")

        assertEquals(recomputeFromMessage(200, "INBOX"), summaryOf(200, "INBOX"))
        assertNull(summaryOf(200, "PROMO"))
    }
}
