package ir.asudehapp.sms.app

import ir.asudehapp.sms.model.MessageInput
import ir.asudehapp.sms.model.Verdict

/**
 * `Application` آزمون ابزاری مسیر دریافت (D64). همان ظرف وابستگی اپ است
 * (ADR-0007)، فقط طبقه‌بند را می‌شود از آزمون عوض کرد تا کرش طبقه‌بند
 * شبیه‌سازی شود. کد تولید هیچ flagی برای این ندارد.
 */
class TestAsudehApplication : AsudehApplication() {

    @Volatile
    var classifyOverride: ((MessageInput) -> Verdict)? = null

    override fun classify(input: MessageInput): Verdict =
        classifyOverride?.invoke(input) ?: super.classify(input)
}
