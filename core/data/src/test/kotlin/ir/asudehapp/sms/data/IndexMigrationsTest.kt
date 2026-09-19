package ir.asudehapp.sms.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * migration نسخهٔ ۱ به ۲ باید دقیقاً همان جدول‌ها، ستون‌ها و triggerهایی را
 * بسازد که Room در schema نسخهٔ ۲ انتظار دارد؛ وگرنه Room پایگاه داده را
 * نمی‌پذیرد، یا بدتر، FTS بی‌صدا از متن پیامک‌ها عقب می‌ماند.
 */
class IndexMigrationsTest {

    private val schema2 = File("schemas/ir.asudehapp.sms.data.IndexDatabase/2.json").readText()
        .replace("\\\"", "\"")
        .replace("\${TABLE_NAME}", "")

    @Test
    fun `every created table and trigger matches the exported schema`() {
        val creates = IndexMigrations.V1_V2_SQL.filter { it.startsWith("CREATE") }
        for (statement in creates) {
            val comparable = statement
                .replace("`thread_pref`", "``")
                .replace("VIRTUAL TABLE IF NOT EXISTS `message_fts`", "VIRTUAL TABLE IF NOT EXISTS ``")
            assertTrue("در schema نیست: $statement", comparable in schema2)
        }
    }

    @Test
    fun `every added column matches the exported schema`() {
        val columns = IndexMigrations.V1_V2_SQL
            .filter { it.startsWith("ALTER TABLE `message` ADD COLUMN ") }
            .map { it.removePrefix("ALTER TABLE `message` ADD COLUMN ") }
        assertTrue(columns.isNotEmpty())
        for (column in columns) {
            assertTrue("ستون در schema نیست: $column", column in schema2)
        }
    }

    @Test
    fun `all four fts sync triggers are created`() {
        val triggers = IndexMigrations.V1_V2_SQL.count { it.startsWith("CREATE TRIGGER") }
        assertTrue(triggers == 4)
    }
}
