package ir.asudehapp.sms.classifier

import org.junit.Assert.assertTrue
import org.junit.Test

class RulePackTest {

    @Test
    fun `the bundled rule pack parses`() {
        val pack = RulePack.bundled()
        assertTrue(pack.version.isNotBlank())
        assertTrue(pack.brands.isNotEmpty())
        assertTrue(pack.promoStrong.keywords.isNotEmpty())
    }

    @Test
    fun `every brand has at least one official domain or sender`() {
        for (brand in RulePack.bundled().brands) {
            assertTrue(
                "نهاد ${brand.name} نه دامنهٔ رسمی دارد نه سرشماره",
                brand.domains.isNotEmpty() || brand.senders.isNotEmpty(),
            )
            assertTrue("نهاد ${brand.name} هیچ عبارت شناسایی ندارد", brand.mentions.isNotEmpty())
        }
    }
}
