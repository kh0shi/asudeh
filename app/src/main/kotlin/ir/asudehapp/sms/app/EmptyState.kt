package ir.asudehapp.sms.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R

/**
 * حالت خالی هر فهرست تمام‌صفحه (ADR-0021): یک تصویر برداری تک‌رنگ بالای همان
 * جملهٔ همیشگی. تصویر تزئینی است و صفحه‌خوان فقط جمله را می‌خواند؛ رنگش از
 * پوستهٔ جاری می‌آید تا در حالت تاریک هم درست باشد.
 */
@Composable
internal fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painterResource(R.drawable.empty_state),
            contentDescription = null,
            modifier = Modifier.size(104.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = IMAGE_ALPHA),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

private const val IMAGE_ALPHA = 0.6f
