package ir.asudehapp.sms.classifier

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `rulepack.json` با JSON Schema خودش سنجیده می‌شود (D62)، تا یک انتشار
 * فقط-داده (D66) با کلید غلط، نوع اشتباه یا regex خراب به اپ نرسد.
 * `RulePack.parse` کلید ناشناخته را هم رد می‌کند، ولی regex خراب را فقط وقت
 * ساختن `CompiledRulePack` می‌فهمد و قالب نسخه را اصلاً نمی‌سنجد.
 */
class RulePackSchemaTest {

    private val mapper = ObjectMapper()

    private val schema: JsonSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(javaClass.getResourceAsStream("/rulepack.schema.json"))

    private fun bundled(): ObjectNode =
        mapper.readTree(javaClass.getResourceAsStream("/ir/asudehapp/sms/classifier/rulepack.json")) as ObjectNode

    /** خطاهای schema، به‌علاوهٔ هر regex که موتور Regex خود Kotlin کامپایلش نمی‌کند. */
    private fun problems(pack: JsonNode): List<String> =
        schema.validate(pack).map { it.message } + regexProblems(pack)

    private fun regexProblems(pack: JsonNode): List<String> =
        pack.properties().mapNotNull { (key, value) -> value.get("regexes")?.let { key to it } }
            .flatMap { (key, regexes) ->
                regexes.filter { it.isTextual }.mapNotNull { pattern ->
                    runCatching { Regex(pattern.asText()) }.exceptionOrNull()
                        ?.let { "$key.regexes: «${pattern.asText()}» کامپایل نمی‌شود: ${it.message}" }
                }
            }

    @Test
    fun `bundled rule pack matches the schema`() {
        assertEquals(emptyList<String>(), problems(bundled()))
    }

    @Test
    fun `unknown key is rejected`() {
        val pack = bundled().apply { putArray("promoo").add("تخفیف") }
        assertTrue(problems(pack).isNotEmpty())
    }

    @Test
    fun `unknown key inside a brand is rejected`() {
        val pack = bundled()
        (pack["brands"][0] as ObjectNode).put("sender", "2000")
        assertTrue(problems(pack).isNotEmpty())
    }

    @Test
    fun `wrong type is rejected`() {
        val pack = bundled()
        (pack["otp"] as ObjectNode).put("keywords", "رمز")
        assertTrue(problems(pack).isNotEmpty())
    }

    @Test
    fun `invalid regex is rejected`() {
        val pack = bundled()
        (pack["otp"]["regexes"] as ArrayNode).add("(رمز")
        assertTrue(problems(pack).isNotEmpty())
    }

    @Test
    fun `version must be year and month`() {
        for (version in listOf("1405.7", "1405-07", "2026.10", "1405.13", "1405.07.1")) {
            val pack = bundled().put("version", version)
            assertTrue("نسخهٔ «$version» باید رد شود", problems(pack).isNotEmpty())
        }
        assertEquals(emptyList<String>(), problems(bundled().put("version", "1405.07")))
    }

    @Test
    fun `non numeric ad line prefix is rejected`() {
        val pack = bundled()
        (pack["adLinePrefixes"] as ArrayNode).add("50x0")
        assertTrue(problems(pack).isNotEmpty())
    }
}
