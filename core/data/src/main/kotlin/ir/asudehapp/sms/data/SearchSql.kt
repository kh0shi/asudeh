package ir.asudehapp.sms.data

/**
 * SQL جستجوی دامنه‌دار (ROADMAP E6)، جدا از DAO تا `ScopedSearchTest` همین
 * متن را روی sqlite واقعی اجرا کند.
 *
 * فقط `:query` به `MATCH` می‌رسد و آن هم از `SearchText.ftsQuery` می‌آید.
 * فرستنده، گفتگو، دسته و پوشه پارامترهای عادی‌اند و هیچ‌وقت در عبارت FTS
 * نمی‌نشینند. `:categories` به شکل `,OTP,` است؛ خالی یعنی همهٔ دسته‌ها.
 */
object SearchSql {

    private const val SCOPE = """
           (:threadId = 0 OR message.threadId = :threadId)
       AND (:sender = '' OR message.normalizedAddress = :sender)
       AND (:onlyAttachments = 0 OR message.attachments > 0)
       AND (:onlyHidden = 0 OR message.folder != 'INBOX')
       AND (:categories = '' OR instr(:categories, ',' || message.category || ',') > 0)
    """

    const val SCOPED_MATCH: String = """
        SELECT message.* FROM message
          JOIN message_fts ON message.rowid = message_fts.rowid
         WHERE message_fts MATCH :query
           AND $SCOPE
         ORDER BY message.dateReceived DESC
         LIMIT :limit
    """

    /** دامنه بدون متن: «همهٔ پیامک‌های پیوست‌دار این گفتگو». */
    const val SCOPED_ONLY: String = """
        SELECT message.* FROM message
         WHERE $SCOPE
         ORDER BY message.dateReceived DESC
         LIMIT :limit
    """
}
