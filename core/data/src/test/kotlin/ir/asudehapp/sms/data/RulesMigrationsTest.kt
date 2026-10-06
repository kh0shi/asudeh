package ir.asudehapp.sms.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * پایگاه دادهٔ قواعد از هیچ جای دیگری قابل بازسازی نیست (`rules.db`)، پس
 * migrationش باید دقیقاً همان چیزی را بسازد که Room انتظار دارد و چیزی را هم
 * پاک نکند.
 */
class RulesMigrationsTest {

    private val schema2 =
        File("schemas/ir.asudehapp.sms.data.RulesDatabase/2.json").readText()
            .replace("\\\"", "\"")
            .replace("\${TABLE_NAME}", "")

    /** تنبل، تا اگر schema نسخهٔ ۳ هنوز ساخته نشده، فقط آزمون خودش بشکند. */
    private val schema3 by lazy {
        File("schemas/ir.asudehapp.sms.data.RulesDatabase/3.json").readText()
            .replace("\\\"", "\"")
            .replace("\${TABLE_NAME}", "")
    }

    @Test
    fun `version two creates exactly what room expects`() {
        val creates = RulesMigrations.V1_V2_SQL.filter { it.startsWith("CREATE") }
        assertTrue(creates.size == RulesMigrations.V1_V2_SQL.size)
        for (statement in creates) {
            val comparable = statement.replace("`keyword_rule`", "``")
            assertTrue("در schema نیست: $statement", comparable in schema2)
        }
    }

    @Test
    fun `version three creates exactly what room expects`() {
        val creates = RulesMigrations.V2_V3_SQL.filter { it.startsWith("CREATE") }
        assertTrue(creates.size == RulesMigrations.V2_V3_SQL.size)
        for (statement in creates) {
            val comparable = statement.replace("`sender_pattern_rule`", "``")
            assertTrue("در schema نیست: $statement", comparable in schema3)
        }
    }

    /** جدول قاعده‌های فرستنده دست نمی‌خورد: قواعد کاربر با به‌روزرسانی نمی‌پرند. */
    @Test
    fun `no rules migration drops or deletes anything`() {
        for (statement in RulesMigrations.V1_V2_SQL + RulesMigrations.V2_V3_SQL) {
            val upper = statement.uppercase()
            assertTrue("migration مخرب: $statement", !upper.startsWith("DROP"))
            assertTrue("migration مخرب: $statement", !upper.startsWith("DELETE"))
            assertTrue("migration مخرب: $statement", !upper.startsWith("ALTER TABLE `SENDER_RULE`"))
            assertTrue("migration مخرب: $statement", !upper.startsWith("ALTER TABLE `KEYWORD_RULE`"))
        }
    }
}
