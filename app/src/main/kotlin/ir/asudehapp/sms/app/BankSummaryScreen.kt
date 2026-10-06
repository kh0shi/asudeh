package ir.asudehapp.sms.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.classifier.BankSummary
import ir.asudehapp.sms.classifier.BankTotals
import ir.asudehapp.sms.classifier.BankTransaction
import ir.asudehapp.sms.classifier.TransactionKind
import ir.asudehapp.sms.persian.JalaliDate
import ir.asudehapp.sms.ui.LocalUiIsPersian
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * «خلاصهٔ تراکنش‌های بانکی» (ADR-0018). یک نمای خواندنی است: هر بار که صفحه
 * باز می‌شود از پیامک‌های بانکی همین گوشی ساخته می‌شود، جایی ذخیره یا فرستاده
 * نمی‌شود، و هیچ پیامکی را عوض نمی‌کند. لمس یک تراکنش گفتگوی همان پیامک را
 * باز می‌کند.
 */
@Composable
fun BankSummaryScreen(model: AsudehViewModel) {
    LaunchedEffect(Unit) { model.loadBankEntries() }
    val entries by model.bankEntries.collectAsState()
    val jalali = LocalUseJalali.current
    val loaded = entries
    val months = remember(loaded, jalali) {
        loaded?.let { list -> BankSummary.byMonth(list.map { it.transaction }, jalali) }.orEmpty()
    }
    val byMessage = remember(loaded) { loaded.orEmpty().associateBy { it.transaction.messageId } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Text(
                    stringResource(R.string.bank_summary_hint),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider()
            }
        }
        if (loaded == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.bank_summary_loading), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (months.isEmpty()) {
            item { EmptyState(stringResource(R.string.bank_summary_empty)) }
        }
        for (month in months) {
            item(key = "month-${month.year}-${month.month}") {
                Text(
                    monthLabel(month.year, month.month),
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(month.totals, key = { "total-${month.year}-${month.month}-${it.bank}" }) { totals ->
                TotalsRow(totals)
            }
            item(key = "divider-${month.year}-${month.month}") { HorizontalDivider(Modifier.padding(top = 4.dp)) }
            items(month.transactions, key = { "tx-${it.messageId}" }) { transaction ->
                TransactionRow(transaction) { byMessage[transaction.messageId]?.let(model::openBankEntry) }
            }
        }
    }
}

@Composable
private fun TotalsRow(totals: BankTotals) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                totals.bank,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(Texts.count(R.plurals.bank_transaction_count, totals.count), style = MaterialTheme.typography.labelSmall)
        }
        Text(
            stringResource(R.string.bank_deposits, rials(totals.deposits)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(R.string.bank_withdrawals, rials(totals.withdrawals)), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TransactionRow(transaction: BankTransaction, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                transaction.bank,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(Texts.timestamp(transaction.timestamp, withClock = true), style = MaterialTheme.typography.labelSmall)
        }
        val deposit = transaction.kind == TransactionKind.DEPOSIT
        Text(
            stringResource(
                if (deposit) R.string.bank_deposit else R.string.bank_withdrawal,
                rials(transaction.amountRials),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (deposit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        transaction.balanceRials?.let { balance ->
            Text(stringResource(R.string.bank_balance, rials(balance)), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** مبلغ به ریال با جداکنندهٔ هزارگان؛ با ارقام فارسی، جداکنندهٔ فارسی «٬». */
@Composable
private fun rials(amount: Long): String {
    val grouped = "%,d".format(Locale.US, amount)
    val text = if (LocalPersianDigits.current) grouped.replace(',', '٬') else grouped
    return Texts.digits(stringResource(R.string.bank_amount_rials, text))
}

/** نام ماه به تقویم انتخابی کاربر، مثل «مهر ۱۴۰۵» یا «October 2026». */
@Composable
private fun monthLabel(year: Int, month: Int): String {
    val text = if (LocalUseJalali.current) {
        val names = if (LocalUiIsPersian.current) JalaliDate.MONTH_NAMES else JalaliDate.MONTH_NAMES_LATIN
        "${names[month - 1]} $year"
    } else {
        YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("MMMM yyyy", LocalConfiguration.current.locales[0]))
    }
    return Texts.digits(text)
}
