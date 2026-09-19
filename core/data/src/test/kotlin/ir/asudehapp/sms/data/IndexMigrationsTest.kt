package ir.asudehapp.sms.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * هر migration باید دقیقاً همان جدول‌ها، ستون‌ها و triggerهایی را بسازد که
 * Room در schema همان نسخه انتظار دارد؛ وگرنه Room پایگاه داده را نمی‌پذیرد،
 * یا بدتر، FTS بی‌صدا از متن پیامک‌ها عقب می‌ماند.
 *
 * schemaها را خود Room هنگام ساخت در `schemas/` می‌نویسد و CI بررسی می‌کند که
 * با آنچه در مخزن است یکی باشند.
 */
class IndexMigrationsTest {

    private val schema2 = schema(2)
    private val schema3 = schema(3)

    private fun schema(version: Int) =
        File("schemas/ir.asudehapp.sms.data.IndexDatabase/$version.json").readText()
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

    /** جدول‌های تازهٔ نسخهٔ ۳: سطل حذف‌شده‌ها و صف زمان‌بندی (ADR-0010، ADR-0011). */
    @Test
    fun `version three creates exactly what room expects`() {
        val creates = IndexMigrations.V2_V3_SQL.filter { it.startsWith("CREATE") }
        assertTrue(creates.size == IndexMigrations.V2_V3_SQL.size)
        for (statement in creates) {
            val comparable = statement
                .replace("`trashed_message`", "``")
                .replace("`scheduled_message`", "``")
            assertTrue("در schema نیست: $statement", comparable in schema3)
        }
    }

    /** هیچ دستور migration نباید داده‌ای را پاک کند. */
    @Test
    fun `no migration drops or deletes anything`() {
        val all = IndexMigrations.V1_V2_SQL + IndexMigrations.V2_V3_SQL
        for (statement in all) {
            val upper = statement.uppercase()
            assertTrue("migration مخرب: $statement", !upper.startsWith("DROP"))
            assertTrue("migration مخرب: $statement", !upper.startsWith("DELETE"))
        }
    }
}
