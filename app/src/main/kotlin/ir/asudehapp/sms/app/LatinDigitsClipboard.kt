package ir.asudehapp.sms.app

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import ir.asudehapp.sms.persian.PersianText

/**
 * کلیپ‌بوردی که ارقام هر چیزی را که کپی می‌شود لاتین می‌کند.
 *
 * متن پیامک در اپ دست نمی‌خورد (D50)، ولی چیزی که کپی می‌شود قرار است جای
 * دیگری چسبانده شود: کد پیگیری در سایت، شمارهٔ کارت در اپ بانک، کد تخفیف در
 * فروشگاه. هیچ‌کدام «۱۲۳۴» را نمی‌پذیرند. پس فقط در همین لحظه، ارقام لاتین
 * می‌شوند و بقیهٔ متن دست‌نخورده می‌ماند.
 *
 * چون [androidx.compose.ui.platform.LocalClipboardManager] را جایگزین می‌کند،
 * هم «کپی متن» خود اپ از این راه می‌گذرد و هم کپی کردن بخشی از متن با
 * دستگیره‌های انتخاب، که کار خود Compose است.
 */
class LatinDigitsClipboard(private val delegate: ClipboardManager) : ClipboardManager {

    override fun setText(annotatedString: AnnotatedString) {
        delegate.setText(AnnotatedString(PersianText.latinDigits(annotatedString.text)))
    }

    override fun getText(): AnnotatedString? = delegate.getText()

    override fun hasText(): Boolean = delegate.hasText()
}
