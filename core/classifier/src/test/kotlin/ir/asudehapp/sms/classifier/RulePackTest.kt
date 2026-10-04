package ir.asudehapp.sms.classifier

import ir.asudehapp.sms.model.Addresses
import org.junit.Assert.assertFalse
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
    fun `verified means the whole sender, never a prefix or a fragment`() {
        val rules = CompiledRulePack(RulePack(version = "t", verifiedSenders = listOf("BankMellat", "+98 2000 1234")))
        assertTrue(rules.isVerifiedSender(Addresses.normalize("bankmellat")))
        assertTrue(rules.isVerifiedSender(Addresses.normalize("+9820001234")))
        assertFalse(rules.isVerifiedSender(Addresses.normalize("BankMellatX")))
        assertFalse(rules.isVerifiedSender(Addresses.normalize("20001234567")))
        assertFalse(rules.isVerifiedSender(Addresses.normalize("2000")))
    }

    /** هیچ سرشمارهٔ تأییدشده‌ای نباید در فهرست سرشماره‌های تبلیغاتی هم باشد. */
    @Test
    fun `no verified sender is a promo sender`() {
        val pack = RulePack.bundled()
        val rules = CompiledRulePack(pack)
        for (sender in pack.verifiedSenders) {
            assertFalse("$sender تبلیغاتی است", rules.isPromoSender(Addresses.normalize(sender)))
        }
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
