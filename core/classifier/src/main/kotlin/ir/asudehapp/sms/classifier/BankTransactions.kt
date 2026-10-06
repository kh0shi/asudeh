package ir.asudehapp.sms.classifier

import ir.asudehapp.sms.model.Addresses
import ir.asudehapp.sms.persian.JalaliDate
import ir.asudehapp.sms.persian.PersianText
import java.time.Instant
import java.time.ZoneId

enum class TransactionKind { DEPOSIT, WITHDRAWAL }

/**
 * یک تراکنش که از متن یک پیامک بانک خوانده شده (ADR-0018). فقط یک نمای مشتق
 * است: پیامک خودش دست نمی‌خورد و این شیء جایی ذخیره نمی‌شود.
 *
 * همهٔ مبلغ‌ها به **ریال**اند. [bank] همان `Brand.name` بانک است.
 */
data class BankTransaction(
    val bank: String,
    val kind: TransactionKind,
    val amountRials: Long,
    val balanceRials: Long?,
    val timestamp: Long,
    val messageId: Long,
)

/**
 * خواندن تراکنش از پیامک بانک با قالب‌های `RulePack.bankTransactions`
 * (ADR-0018). تابع خالص است و هیچ قالبی در کد ندارد.
 *
 * فقط سرشمارهٔ رسمی یکی از بانک‌های فهرست تراکنش می‌سازد. اینکه پیامک
 * `BANK` طبقه‌بندی شده باشد کار فراخواننده است؛ این کلاس فقط متن و سرشماره را
 * می‌بیند. هر ابهامی (هیچ نوع، یا هم واریز هم برداشت) یعنی `null`.
 */
class BankTransactionParser(rules: CompiledRulePack) {

    private val banks: List<CompiledBrand> = rules.pack.bankTransactions.banks.mapNotNull { name ->
        rules.brands.firstOrNull { it.brand.name == name }
    }

    private val templates: List<CompiledTemplate> = rules.pack.bankTransactions.templates.map(::CompiledTemplate)

    /** بانکی که این سرشماره سرشمارهٔ رسمی آن است، یا `null`. */
    fun bankOf(address: String): String? {
        val sender = Addresses.normalize(PersianText.latinDigits(address))
        return banks.firstOrNull { it.isOfficialSender(sender) }?.brand?.name
    }

    fun parse(address: String, body: String, timestamp: Long, messageId: Long): BankTransaction? {
        val bank = bankOf(address) ?: return null
        val text = PersianText.normalize(body)
        val reading = templates.asSequence()
            .filter { it.appliesTo(bank) }
            .firstNotNullOfOrNull { it.read(text) }
        return reading?.let { BankTransaction(bank, it.kind, it.amount, it.balance, timestamp, messageId) }
    }

    private class Reading(val kind: TransactionKind, val amount: Long, val balance: Long?)

    private class CompiledTemplate(template: BankTemplate) {
        private val banks: Set<String> = template.banks.toSet()
        private val deposit: List<AmountPattern> = template.deposit.map(::AmountPattern)
        private val withdrawal: List<AmountPattern> = template.withdrawal.map(::AmountPattern)
        private val balance: List<AmountPattern> = template.balance.map(::AmountPattern)

        fun appliesTo(bank: String): Boolean = banks.isEmpty() || bank in banks

        fun read(text: String): Reading? {
            // «مانده: -۵۰۰٬۰۰۰» برداشت نیست؛ پس بازهٔ مانده پیش از جستجوی واریز
            // و برداشت با فاصله پوشانده می‌شود (طول متن عوض نمی‌شود).
            val balanceFound = balance.firstNotNullOfOrNull { it.find(text) }
            val rest = balanceFound?.let { text.replaceRange(it.range, " ".repeat(it.range.count())) } ?: text
            val depositFound = deposit.firstNotNullOfOrNull { it.find(rest) }
            val withdrawalFound = withdrawal.firstNotNullOfOrNull { it.find(rest) }
            return when {
                depositFound != null && withdrawalFound == null ->
                    Reading(TransactionKind.DEPOSIT, depositFound.amount, balanceFound?.signed)
                withdrawalFound != null && depositFound == null ->
                    Reading(TransactionKind.WITHDRAWAL, withdrawalFound.amount, balanceFound?.signed)
                else -> null
            }
        }
    }

    /** [negative]: پیش از مبلغ، درون همان الگو، یک «-» آمده (ماندهٔ منفی). */
    private class Found(val amount: Long, val range: IntRange, val negative: Boolean) {
        val signed: Long get() = if (negative) -amount else amount
    }

    private class AmountPattern(pattern: String) {
        private val regex = Regex(pattern, RegexOption.IGNORE_CASE)

        // گرفتن گروه نام‌داری که در الگو نیست خطا می‌دهد، پس از پیش می‌دانیم.
        private val hasToman = "(?<$TOMAN>" in pattern

        /** اولین جای متن که الگو جور است و مبلغی پذیرفتنی دارد. */
        fun find(text: String): Found? = regex.findAll(text).firstNotNullOfOrNull { found(text, it) }

        private fun found(text: String, match: MatchResult): Found? {
            val groups = match.groups as? MatchNamedGroupCollection
            val amount = groups?.get(AMOUNT)
            val raw = amount?.value?.filter { it in '0'..'9' }?.toLongOrNull()?.takeIf { it in 1..MAX_AMOUNT }
            if (amount == null || raw == null) return null
            val toman = hasToman && groups[TOMAN] != null
            val negative = text.substring(match.range.first, amount.range.first).trimEnd().endsWith('-')
            return Found(if (toman) raw * RIALS_PER_TOMAN else raw, match.range, negative)
        }
    }

    companion object {
        const val AMOUNT: String = "amount"
        const val TOMAN: String = "toman"

        /** هر مبلغی بیش از این (۱۰^۱۵ ریال) خطای خواندن است، نه تراکنش. */
        const val MAX_AMOUNT: Long = 1_000_000_000_000_000L
        private const val RIALS_PER_TOMAN = 10L
    }
}

/** جمع واریز و برداشت یک بانک در یک ماه، به ریال. */
data class BankTotals(val bank: String, val deposits: Long, val withdrawals: Long, val count: Int)

/** یک ماه (شمسی یا میلادی، به انتخاب کاربر) در خلاصه؛ تازه‌ترین تراکنش اول. */
data class MonthSummary(
    val year: Int,
    val month: Int,
    val totals: List<BankTotals>,
    val transactions: List<BankTransaction>,
)

/** گروه‌بندی تراکنش‌ها بر اساس ماه و بانک (ADR-0018). خالص، بدون حالت. */
object BankSummary {

    fun byMonth(
        transactions: List<BankTransaction>,
        jalali: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MonthSummary> =
        transactions
            .groupBy { monthOf(it.timestamp, jalali, zone) }
            .entries
            .sortedWith(compareByDescending<Map.Entry<Pair<Int, Int>, List<BankTransaction>>> { it.key.first }
                .thenByDescending { it.key.second })
            .map { (month, items) ->
                MonthSummary(
                    year = month.first,
                    month = month.second,
                    totals = items.groupBy { it.bank }.map { (bank, ofBank) -> totals(bank, ofBank) }
                        .sortedBy { it.bank },
                    transactions = items.sortedByDescending { it.timestamp },
                )
            }

    private fun totals(bank: String, items: List<BankTransaction>): BankTotals {
        var deposits = 0L
        var withdrawals = 0L
        for (item in items) {
            when (item.kind) {
                TransactionKind.DEPOSIT -> deposits = saturatingAdd(deposits, item.amountRials)
                TransactionKind.WITHDRAWAL -> withdrawals = saturatingAdd(withdrawals, item.amountRials)
            }
        }
        return BankTotals(bank, deposits, withdrawals, items.size)
    }

    private fun saturatingAdd(a: Long, b: Long): Long = if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b

    private fun monthOf(millis: Long, jalali: Boolean, zone: ZoneId): Pair<Int, Int> {
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        if (!jalali) return date.year to date.monthValue
        val shamsi = JalaliDate.of(date.year, date.monthValue, date.dayOfMonth)
        return shamsi.year to shamsi.month
    }
}
