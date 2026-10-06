package ir.asudehapp.sms.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.asudehapp.sms.R
import ir.asudehapp.sms.ui.BubblePalette

/** پوستهٔ فعلی تیره است؟ از رنگ زمینه، چون پوسته از تنظیمات اپ می‌آید نه از گوشی. */
@Composable
@ReadOnlyComposable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < HALF

/** رنگ حباب فرستاده برای شمارهٔ [index]؛ منفی یا ناشناخته یعنی رنگ پوسته. */
@Composable
@ReadOnlyComposable
fun outgoingBubbleColor(index: Int): Color =
    BubblePalette.color(index, isDarkScheme()) ?: MaterialTheme.colorScheme.primaryContainer

/**
 * ردیف رنگ‌های پالت (ROADMAP E8). خانهٔ اول «پیش‌فرض» است: در تنظیمات یعنی رنگ
 * پوسته، و در یک گفتگو یعنی همان رنگ سراسری ([defaultColor]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BubbleSwatches(selected: Int, defaultColor: Color, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Swatch(defaultColor, selected < 0, stringResource(R.string.bubble_color_default)) { onPick(-1) }
        BubblePalette.swatches.forEachIndexed { index, _ ->
            Swatch(outgoingBubbleColor(index), selected == index, stringResource(R.string.bubble_color_n, index + 1)) {
                onPick(index)
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(4.dp)
            .background(color, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** «رنگ حباب» از منوی گفتگو: فقط همین گفتگو. */
@Composable
fun ThreadBubbleColorDialog(selected: Int, globalColor: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bubble_color_thread)) },
        text = { BubbleSwatches(selected, outgoingBubbleColor(globalColor), onPick) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private const val HALF = 0.5f
