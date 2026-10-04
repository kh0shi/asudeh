package ir.asudehapp.sms.app

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * لرزش کوتاه (ROADMAP E8): رسیدن کشیدن انگشت به آستانه، لمس طولانی و ارسال.
 *
 * `performHapticFeedback` مجوز `VIBRATE` نمی‌خواهد و تنظیم «بازخورد لمسی» خود
 * گوشی را رعایت می‌کند؛ اگر کاربر آن را خاموش کرده باشد، چیزی نمی‌لرزد.
 */
object Haptics {

    fun threshold(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            },
        )
    }

    fun longPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun send(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            },
        )
    }
}
