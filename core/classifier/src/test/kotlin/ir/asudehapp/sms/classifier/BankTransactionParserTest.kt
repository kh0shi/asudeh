package ir.asudehapp.sms.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * خواندن تراکنش از پیامک بانک (ADR-0018). همهٔ پیامک‌ها ساختگی‌اند و هیچ دادهٔ
 * واقعی کسی در آن‌ها نیست؛ بیشتر آزمون‌ها با بانک ساختگی و سرشمارهٔ ساختگی، و
 * قالب‌های خود `RulePack` همراه اپ.
 */
class BankTransactionParserTest {

    private val bundled = RulePack.bundled()

    private val testBank = Brand(name = "بانک آزمون", senders = listOf("99887766"), mentions = listOf("بانک آزمون"))
    private val otherBank = Brand(name = "بانک دیگر", senders = listOf("99880000"), mentions = listOf("بانک دیگر"))

    private fun parser(templates: List<BankTemplate> = bundled.bankTransactions.templates) = BankTransactionParser(
        CompiledRulePack(
            RulePack(
                version = "t",
                brands = listOf(testBank, otherBank),
                bankTransactions = BankTransactionRules(banks = listOf(testBank.name, otherBank.name), templates = templates),
            ),
        ),
    )

    private fun parse(body: String, sender: String = "99887766") = parser().parse(sender, body, 1L, 7L)

    @Test
    fun `labeled deposit with persian digits and arabic thousands separator`() {
        val t = parse("بانک آزمون\nواریز: ۱٬۰۰۰٬۰۰۰\nمانده: ۵٬۲۵۰٬۰۰۰\n۱۴۰۵/۰۷/۱۴-۱۰:۲۰")!!
        assertEquals(TransactionKind.DEPOSIT, t.kind)
        assertEquals(1_000_000L, t.amountRials)
        assertEquals(5_250_000L, t.balanceRials)
        assertEquals("بانک آزمون", t.bank)
        assertEquals(1L, t.timestamp)
        assertEquals(7L, t.messageId)
    }

    @Test
    fun `labeled withdrawal with trailing minus`() {
        val t = parse("حساب 0101\nبرداشت:250,000-\nمانده:4,750,000\n0714-1020")!!
        assertEquals(TransactionKind.WITHDRAWAL, t.kind)
        assertEquals(250_000L, t.amountRials)
        assertEquals(4_750_000L, t.balanceRials)
    }

    @Test
    fun `sentence form and balance named mojoodi`() {
        val t = parse("مبلغ 500,000 ریال به حساب 0101 واریز شد. موجودی: 2,000,000 ریال")!!
        assertEquals(TransactionKind.DEPOSIT, t.kind)
        assertEquals(500_000L, t.amountRials)
        assertEquals(2_000_000L, t.balanceRials)

        val w = parse("مبلغ ۳۰۰٬۰۰۰ ریال از حساب ۰۱۰۱ برداشت شد")!!
        assertEquals(TransactionKind.WITHDRAWAL, w.kind)
        assertEquals(300_000L, w.amountRials)
        assertNull(w.balanceRials)
    }

    @Test
    fun `toman is converted to rial`() {
        val t = parse("برداشت: 50,000 تومان\nمانده: 1,000,000 تومان")!!
        assertEquals(500_000L, t.amountRials)
        assertEquals(10_000_000L, t.balanceRials)
    }

    @Test
    fun `signed amounts before or after the number`() {
        val deposit = parse("حساب 0101\n+1,200,000\n1405/07/14\nمانده:3,400,000")!!
        assertEquals(TransactionKind.DEPOSIT, deposit.kind)
        assertEquals(1_200_000L, deposit.amountRials)
        assertEquals(3_400_000L, deposit.balanceRials)

        val withdrawal = parse("حساب 0101\n1,200,000-\n1405/07/14\nمانده:2,200,000")!!
        assertEquals(TransactionKind.WITHDRAWAL, withdrawal.kind)
        assertEquals(1_200_000L, withdrawal.amountRials)
    }

    @Test
    fun `negative balance is not a withdrawal and keeps its sign`() {
        val labeled = parse("برداشت: 100,000\nمانده: -50,000")!!
        assertEquals(TransactionKind.WITHDRAWAL, labeled.kind)
        assertEquals(-50_000L, labeled.balanceRials)

        val signed = parse("حساب 0101\n-100,000\nمانده: -50,000")!!
        assertEquals(TransactionKind.WITHDRAWAL, signed.kind)
        assertEquals(100_000L, signed.amountRials)
        assertEquals(-50_000L, signed.balanceRials)

        // فقط ماندهٔ منفی و هیچ واریز و برداشتی: تراکنش نیست.
        assertNull(parse("مانده: -50,000"))
    }

    @Test
    fun `unofficial sender never makes a transaction`() {
        assertNull(parse("واریز: 100,000,000", sender = "09121234567"))
        assertNull(parse("واریز: 100,000,000", sender = "BankAzmoon"))
    }

    @Test
    fun `ambiguous or amountless messages are left out`() {
        assertNull(parse("واریز: 1,000,000 برداشت: 2,000,000"))
        assertNull(parse("+1,000,000 -2,000,000"))
        assertNull(parse("واریز حقوق مهر انجام شد"))
        assertNull(parse("تاریخ واریز 1405/07/14"))
        assertNull(parse("واریز: 0"))
        assertNull(parse("برای واریز با 09121234567 تماس بگیرید یا +989121234567"))
    }

    @Test
    fun `absurd amount is rejected`() {
        assertNull(parse("واریز: 99999999999999999999"))
        assertNull(parse("واریز: 2,000,000,000,000,000,000"))
    }

    @Test
    fun `bank specific template applies only to that bank and wins over a later one`() {
        val special = BankTemplate(
            id = "only-test",
            banks = listOf(testBank.name),
            deposit = listOf("""ورود وجه\s*(?<amount>\d+)"""),
        )
        val generic = BankTemplate(id = "generic", deposit = listOf("""واریز\s*:?\s*(?<amount>\d+)"""))
        val p = parser(listOf(special, generic))
        assertEquals(5_000L, p.parse("99887766", "ورود وجه 5000", 0, 0)!!.amountRials)
        assertNull(p.parse("99880000", "ورود وجه 5000", 0, 0))
        assertEquals("بانک دیگر", p.parse("99880000", "واریز: 6000", 0, 0)!!.bank)
    }

    @Test
    fun `missing toman group is fine`() {
        val p = parser(listOf(BankTemplate(id = "x", withdrawal = listOf("""خرج\s*(?<amount>\d+)"""))))
        assertEquals(42L, p.parse("99887766", "خرج 42", 0, 0)!!.amountRials)
    }

    @Test
    fun `bundled templates compile and capture an amount`() {
        val templates = bundled.bankTransactions.templates
        assertTrue(templates.isNotEmpty())
        for (template in templates) {
            for (pattern in template.deposit + template.withdrawal + template.balance) {
                val regex = runCatching { Regex(pattern) }.getOrNull()
                assertTrue("«$pattern» در قالب ${template.id} کامپایل نمی‌شود", regex != null)
                assertTrue("«$pattern» گروه amount ندارد", "(?<${BankTransactionParser.AMOUNT}>" in pattern)
            }
        }
    }

    @Test
    fun `every bank name is a known brand`() {
        val brands = bundled.brands.map { it.name }.toSet()
        val names = bundled.bankTransactions.banks + bundled.bankTransactions.templates.flatMap { it.banks }
        assertTrue(bundled.bankTransactions.banks.isNotEmpty())
        for (name in names) assertTrue("بانک «$name» در brands نیست", name in brands)
    }

    @Test
    fun `bundled pack reads a bank sender from brands`() {
        val p = BankTransactionParser(CompiledRulePack(bundled))
        val t = p.parse("200011", "بانک ملی\nبرداشت: 1,000,000\nمانده: 9,000,000", 0, 0)!!
        assertEquals("بانک ملی ایران", t.bank)
        assertEquals(TransactionKind.WITHDRAWAL, t.kind)
        // نهادی که بانک نیست، حتی با سرشمارهٔ رسمی خودش، تراکنش نمی‌سازد.
        assertNull(p.parse("110", "واریز: 1,000,000", 0, 0))
    }

    @Test
    fun `summary groups by jalali month and bank`() {
        fun at(year: Int, month: Int, day: Int) =
            ZonedDateTime.of(year, month, day, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

        fun tx(bank: String, kind: TransactionKind, amount: Long, millis: Long) =
            BankTransaction(bank, kind, amount, null, millis, millis)

        val mehr1 = at(2026, 10, 6) // ۱۴ مهر ۱۴۰۵
        val mehr2 = at(2026, 9, 23) // ۱ مهر ۱۴۰۵
        val shahrivar = at(2026, 9, 22) // ۳۱ شهریور ۱۴۰۵
        val items = listOf(
            tx("ب", TransactionKind.DEPOSIT, 100, mehr2),
            tx("الف", TransactionKind.WITHDRAWAL, 30, mehr1),
            tx("الف", TransactionKind.DEPOSIT, 50, mehr2),
            tx("الف", TransactionKind.WITHDRAWAL, 20, mehr1),
            tx("الف", TransactionKind.WITHDRAWAL, 7, shahrivar),
        )

        val months = BankSummary.byMonth(items, jalali = true, zone = ZoneOffset.UTC)
        assertEquals(listOf(1405 to 7, 1405 to 6), months.map { it.year to it.month })
        val mehr = months[0]
        assertEquals(listOf("الف", "ب"), mehr.totals.map { it.bank })
        assertEquals(BankTotals("الف", deposits = 50, withdrawals = 50, count = 3), mehr.totals[0])
        assertEquals(BankTotals("ب", deposits = 100, withdrawals = 0, count = 1), mehr.totals[1])
        assertEquals(mehr1, mehr.transactions.first().timestamp)

        // به میلادی، ۲۲ و ۲۳ سپتامبر یک ماه‌اند.
        val gregorian = BankSummary.byMonth(items, jalali = false, zone = ZoneOffset.UTC)
        assertEquals(listOf(2026 to 10, 2026 to 9), gregorian.map { it.year to it.month })
        assertEquals(3, gregorian[1].transactions.size)
    }

    @Test
    fun `totals saturate instead of overflowing`() {
        val big = BankTransaction("الف", TransactionKind.DEPOSIT, Long.MAX_VALUE - 1, null, 0, 0)
        val month = BankSummary.byMonth(listOf(big, big.copy(messageId = 1)), jalali = false, zone = ZoneOffset.UTC)
        assertEquals(Long.MAX_VALUE, month.single().totals.single().deposits)
    }
}
