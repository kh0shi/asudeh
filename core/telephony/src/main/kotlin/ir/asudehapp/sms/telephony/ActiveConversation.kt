package ir.asudehapp.sms.telephony

/**
 * گفتگویی که همین حالا روی صفحه باز است.
 *
 * وقتی پیامک تازه‌ای از همین گفتگو می‌رسد، اعلان چیزی به کاربر اضافه نمی‌کند:
 * خود پیامک جلوی چشمش در فهرست می‌نشیند. پس اعلان فرستاده نمی‌شود و فقط صدای
 * اعلان پخش می‌شود ([AsudehNotifier]).
 *
 * `MainActivity` آن را در `onStart`/`onStop` و با هر جابه‌جایی صفحه به‌روز
 * می‌کند. اگر اپ بسته شود یا فرایند از نو شروع شود، مقدارش صفر است و رفتار
 * همان اعلان همیشگی می‌شود.
 */
object ActiveConversation {

    @Volatile
    private var threadId: Long = 0

    /** [threadId] صفر یعنی هیچ گفتگویی باز نیست. */
    fun set(threadId: Long) {
        this.threadId = threadId
    }

    fun clear() {
        threadId = 0
    }

    fun isOpen(threadId: Long): Boolean = threadId != 0L && this.threadId == threadId
}
