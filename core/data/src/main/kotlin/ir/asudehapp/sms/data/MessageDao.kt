package ir.asudehapp.sms.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ir.asudehapp.sms.model.Folder
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    /**
     * همگام‌سازی فقط پیامک‌هایی را اضافه می‌کند که هنوز در ایندکس نیستند. اگر
     * `ReceivePipeline` هم‌زمان همان پیامک را با `origin = LIVE` نوشته باشد،
     * نسخهٔ او می‌ماند.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(messages: List<MessageEntity>)

    /**
     * خلاصهٔ گفتگوها **در یک پوشه**. یک گفتگو می‌تواند هم‌زمان در چند پوشه دیده
     * شود (`SplitThread`)، و در هر پوشه فقط پیامک‌های همان پوشه در پیش‌نمایش،
     * تعداد خوانده‌نشده و زمان آخرین پیامک حساب می‌شوند (ADR-0005).
     */
    @Query(
        """
        SELECT m.threadId AS threadId,
               m.folder AS folder,
               m.address AS address,
               m.body AS snippet,
               m.dateReceived AS lastDate,
               (SELECT COUNT(*) FROM message u
                 WHERE u.threadId = m.threadId AND u.folder = m.folder
                   AND u.read = 0 AND u.outgoing = 0) AS unread,
               (SELECT COUNT(*) FROM message t
                 WHERE t.threadId = m.threadId AND t.folder = m.folder) AS total,
               (SELECT MAX(r.risk) FROM message r
                 WHERE r.threadId = m.threadId AND r.folder = m.folder) AS hasRisk,
               m.recipients AS recipients,
               m.attachments AS attachments,
               COALESCE(p.pinned, 0) AS pinned,
               COALESCE(p.draft, '') AS draft
          FROM message m
          LEFT JOIN thread_pref p ON p.threadId = m.threadId
         WHERE m.folder = :folder
           AND m.dateReceived = (SELECT MAX(x.dateReceived) FROM message x
                                  WHERE x.threadId = m.threadId AND x.folder = m.folder)
         GROUP BY m.threadId
         ORDER BY pinned DESC, lastDate DESC
        """,
    )
    fun observeThreads(folder: Folder): Flow<List<ThreadSummary>>

    /** همهٔ پیامک‌های یک گفتگو، از همهٔ پوشه‌ها، برای نمایش `HiddenRun` (D43). */
    @Query("SELECT * FROM message WHERE threadId = :threadId ORDER BY dateReceived ASC")
    fun observeConversation(threadId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE threadId = :threadId ORDER BY dateReceived ASC")
    suspend fun conversationNow(threadId: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM message WHERE folder = :folder AND read = 0 AND outgoing = 0")
    fun observeUnreadCount(folder: Folder): Flow<Int>

    @Query("SELECT COUNT(*) FROM message WHERE folder = :folder")
    fun observeCount(folder: Folder): Flow<Int>

    @Query("SELECT COUNT(*) FROM message WHERE folder = :folder")
    suspend fun count(folder: Folder): Int

    @Query("UPDATE message SET read = 1 WHERE threadId = :threadId AND folder = :folder")
    suspend fun markThreadRead(threadId: Long, folder: Folder)

    /** «همه خوانده شد» در یک پوشه (D45). */
    @Query("UPDATE message SET read = 1 WHERE folder = :folder")
    suspend fun markFolderRead(folder: Folder)

    @Query("SELECT kind, providerId FROM message WHERE folder = :folder AND read = 0 AND outgoing = 0 AND providerId > 0")
    suspend fun unreadIn(folder: Folder): List<MessageKey>

    @Query("SELECT kind, providerId FROM message WHERE threadId = :threadId AND folder = :folder AND read = 0 AND outgoing = 0 AND providerId > 0")
    suspend fun unreadInThread(threadId: Long, folder: Folder): List<MessageKey>

    /** آخرین پیامک دریافتی یک گفتگو در یک پوشه، برای «علامت خوانده‌نشده». */
    @Query(
        """
        SELECT * FROM message
         WHERE threadId = :threadId AND folder = :folder AND outgoing = 0
         ORDER BY dateReceived DESC
         LIMIT 1
        """,
    )
    suspend fun lastIncoming(threadId: Long, folder: Folder): MessageEntity?

    @Query("UPDATE message SET read = :read WHERE kind = :kind AND providerId = :providerId")
    suspend fun setRead(kind: String, providerId: Long, read: Boolean)

    /**
     * جستجوی متن کامل (D20). [query] از `SearchText.ftsQuery` می‌آید و فقط
     * کلمه‌های نرمال‌شده با پیشوند دارد، پس عملگر FTS در آن نیست.
     */
    @Query(
        """
        SELECT message.* FROM message
          JOIN message_fts ON message.rowid = message_fts.rowid
         WHERE message_fts MATCH :query
         ORDER BY message.dateReceived DESC
         LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int): List<MessageEntity>

    /** ایندکس‌های نسخهٔ ۱ متن جستجو نداشتند؛ این‌ها در همگام‌سازی پر می‌شوند. */
    @Query("SELECT * FROM message WHERE searchText = '' AND (body != '' OR address != '') LIMIT :limit")
    suspend fun missingSearchText(limit: Int): List<MessageEntity>

    @Query("UPDATE message SET searchText = :searchText WHERE kind = :kind AND providerId = :providerId")
    suspend fun setSearchText(kind: String, providerId: Long, searchText: String)

    /** پیامک‌های پنهانی که از [since] به بعد زنده رسیده‌اند، برای `Digest` (D40). */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN folder = 'PROMO' THEN 1 ELSE 0 END), 0) AS promo,
               COALESCE(SUM(CASE WHEN folder = 'SCAM' THEN 1 ELSE 0 END), 0) AS scam
          FROM message
         WHERE origin = 'LIVE' AND outgoing = 0 AND dateReceived >= :since
           AND folder IN ('PROMO', 'SCAM')
        """,
    )
    suspend fun hiddenSince(since: Long): HiddenCount

    @Query("UPDATE message SET folder = :folder, suggestMove = 0 WHERE kind = :kind AND providerId = :providerId")
    suspend fun moveMessage(kind: String, providerId: Long, folder: Folder)

    @Query("UPDATE message SET folder = :to WHERE normalizedAddress = :normalizedAddress AND folder = :from")
    suspend fun moveAllFrom(normalizedAddress: String, from: Folder, to: Folder)

    /**
     * پیامک‌هایی که اگر زنده رسیده بودند پنهان می‌شدند، ولی چون قدیمی‌اند فقط
     * پیشنهاد جابه‌جایی دارند (اصل ۸، D16، D22).
     */
    @Query(
        """
        SELECT COUNT(*) AS messages,
               COUNT(DISTINCT threadId) AS threads,
               COALESCE(SUM(CASE WHEN category = 'PHISHING' THEN 1 ELSE 0 END), 0) AS scams
          FROM message
         WHERE suggestMove = 1 AND folder = 'INBOX'
        """,
    )
    fun observeSuggestions(): Flow<MoveSuggestion>

    @Query(
        """
        SELECT * FROM message
         WHERE suggestMove = 1 AND folder = 'INBOX'
         ORDER BY dateReceived DESC
         LIMIT :limit
        """,
    )
    suspend fun suggestedSample(limit: Int): List<MessageEntity>

    /**
     * پذیرفتن پیشنهاد: فیشینگ به `ScamFolder` و بقیه به `PromoFolder`. فقط
     * پیامک‌هایی که هنوز در `Inbox` هستند و پیشنهاد دارند.
     */
    @Query(
        """
        UPDATE message
           SET folder = CASE WHEN category = 'PHISHING' THEN 'SCAM' ELSE 'PROMO' END,
               suggestMove = 0
         WHERE suggestMove = 1 AND folder = 'INBOX'
        """,
    )
    suspend fun acceptSuggestions(): Int

    /** «در صندوق بماند»: پیشنهاد برداشته می‌شود و پیامک سر جایش می‌ماند. */
    @Query("UPDATE message SET suggestMove = 0 WHERE suggestMove = 1 AND folder = 'INBOX'")
    suspend fun dismissSuggestions(): Int

    @Query("SELECT * FROM message WHERE kind = :kind AND providerId = :providerId")
    suspend fun find(kind: String, providerId: Long): MessageEntity?

    /** جای پیامک‌هایی که در `Inbox` نیستند، برای پشتیبان (D57). */
    @Query("SELECT providerId, folder FROM message WHERE kind = :kind AND folder != 'INBOX' AND providerId > 0")
    suspend fun hiddenPlacements(kind: String): List<ProviderFolder>

    @Query("SELECT * FROM message WHERE kind = 'MMS' AND pendingDownload = 1")
    suspend fun pendingDownloads(): List<MessageEntity>

    @Query("SELECT * FROM message WHERE pendingClassify = 1")
    suspend fun pendingClassify(): List<MessageEntity>

    /** شناسه‌های provider که در ایندکس هستند. شناسهٔ منفی (نوشته‌نشده) جزو آن‌ها نیست. */
    @Query("SELECT providerId FROM message WHERE kind = :kind AND providerId > 0")
    suspend fun providerIds(kind: String): List<Long>

    /** پیامک‌هایی که نوشتنشان در provider شکست خورده است (`providerId` منفی). */
    @Query("SELECT * FROM message WHERE kind = :kind AND providerId < 0")
    suspend fun unsaved(kind: String): List<MessageEntity>

    @Query("DELETE FROM message WHERE kind = :kind AND providerId = :providerId")
    suspend fun delete(kind: String, providerId: Long)

    /**
     * پیامک‌هایی که بیرون از اپ از provider پاک شده‌اند از ایندکس هم برداشته
     * می‌شوند. این حذف از ایندکس است، نه از provider.
     */
    @Query("DELETE FROM message WHERE kind = :kind AND providerId IN (:providerIds)")
    suspend fun deleteAll(kind: String, providerIds: List<Long>)

    @Query("SELECT * FROM message WHERE normalizedAddress = :normalizedAddress")
    suspend fun messagesFrom(normalizedAddress: String): List<MessageEntity>

    @Query("SELECT providerId FROM message WHERE kind = :kind AND folder = :folder AND providerId > 0")
    suspend fun providerIdsInFolder(kind: String, folder: Folder): List<Long>

    /** پیامک‌های یک گفتگو در یک پوشه، برای حذف کل گفتگو (ADR-0010). */
    @Query("SELECT * FROM message WHERE threadId = :threadId AND folder = :folder")
    suspend fun inThread(threadId: Long, folder: Folder): List<MessageEntity>

    /** همهٔ پیامک‌های یک گفتگو، از هر سه پوشه. */
    @Query("SELECT * FROM message WHERE threadId = :threadId")
    suspend fun allInThread(threadId: Long): List<MessageEntity>

    /**
     * وضعیت ارسال. «ناموفق» برگشت‌ناپذیر است: اگر یک تکه از پیامک چندتکه
     * نرسیده باشد، رسیدن تکهٔ بعدی آن را «ارسال‌شده» نمی‌کند.
     */
    @Query(
        """
        UPDATE message SET sendStatus = :status
         WHERE kind = :kind AND providerId = :providerId
           AND (sendStatus != 'FAILED' OR :status = 'PENDING')
           AND NOT (sendStatus = 'DELIVERED' AND :status = 'SENT')
        """,
    )
    suspend fun setSendStatus(kind: String, providerId: Long, status: SendStatus)

    /**
     * شمار دسته‌های یک سرشماره، برای تشخیص `MixedSender`: سرشماره‌ای که هم
     * تبلیغ می‌فرستد و هم پیامک مهم (ADR-0006).
     */
    @Query(
        """
        SELECT normalizedAddress AS address,
               SUM(CASE WHEN category = 'PROMO' THEN 1 ELSE 0 END) AS promoCount,
               SUM(CASE WHEN category IN ('OTP', 'BANK', 'SERVICE') THEN 1 ELSE 0 END)
                 AS importantCount
          FROM message
         WHERE normalizedAddress = :normalizedAddress
         GROUP BY normalizedAddress
        """,
    )
    suspend fun statsFor(normalizedAddress: String): SenderStats?
}

/** سنجاق و پیش‌نویس هر گفتگو. */
@Dao
interface ThreadPrefDao {

    @Query("SELECT * FROM thread_pref WHERE threadId = :threadId")
    suspend fun get(threadId: Long): ThreadPrefEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(pref: ThreadPrefEntity)

    @Query("DELETE FROM thread_pref WHERE threadId = :threadId")
    suspend fun remove(threadId: Long)
}

@Dao
interface SenderRuleDao {

    @Query("SELECT * FROM sender_rule ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SenderRuleEntity>>

    @Query("SELECT * FROM sender_rule")
    suspend fun all(): List<SenderRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(rule: SenderRuleEntity)

    @Query("DELETE FROM sender_rule WHERE address = :address")
    suspend fun remove(address: String)
}

/**
 * سطل حذف‌شده‌ها (ADR-0010). هیچ کوئری‌ای در این DAO خودکار صدا زده نمی‌شود:
 * هم پر شدن سطل و هم خالی شدنش با اقدام صریح کاربر است.
 */
@Dao
interface TrashDao {

    /** خروجی، شناسهٔ ردیف‌های تازه است، به همان ترتیب ورودی. */
    @Insert
    suspend fun put(rows: List<TrashedMessageEntity>): List<Long>

    @Query("SELECT * FROM trashed_message ORDER BY deletedAt DESC, id DESC")
    fun observeAll(): Flow<List<TrashedMessageEntity>>

    @Query("SELECT COUNT(*) FROM trashed_message")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM trashed_message WHERE id = :id")
    suspend fun get(id: Long): TrashedMessageEntity?

    @Query("DELETE FROM trashed_message WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("DELETE FROM trashed_message WHERE id IN (:ids)")
    suspend fun removeAll(ids: List<Long>)

    /** «خالی کردن سطل»: فقط با تأیید دومرحلهٔ کاربر. */
    @Query("DELETE FROM trashed_message")
    suspend fun clear(): Int
}

/** صف پیامک‌های زمان‌بندی‌شده (ADR-0011). */
@Dao
interface ScheduledMessageDao {

    @Insert
    suspend fun put(message: ScheduledMessageEntity): Long

    @Query("SELECT * FROM scheduled_message WHERE threadId = :threadId ORDER BY sendAt ASC")
    fun observeForThread(threadId: Long): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_message ORDER BY sendAt ASC")
    fun observeAll(): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_message WHERE id = :id")
    suspend fun get(id: Long): ScheduledMessageEntity?

    /** پیامک‌هایی که زمانشان رسیده است. */
    @Query("SELECT * FROM scheduled_message WHERE state = 'WAITING' AND sendAt <= :now ORDER BY sendAt ASC")
    suspend fun due(now: Long): List<ScheduledMessageEntity>

    /** نزدیک‌ترین زمان پیش رو، برای تنظیم هشدار بعدی. */
    @Query("SELECT MIN(sendAt) FROM scheduled_message WHERE state = 'WAITING' AND sendAt > :now")
    suspend fun nextAfter(now: Long): Long?

    @Query("SELECT * FROM scheduled_message WHERE state = 'WAITING'")
    suspend fun waiting(): List<ScheduledMessageEntity>

    @Query("UPDATE scheduled_message SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: ScheduleState)

    @Query("DELETE FROM scheduled_message WHERE id = :id")
    suspend fun remove(id: Long)
}
