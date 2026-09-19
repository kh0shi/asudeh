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
                 WHERE r.threadId = m.threadId AND r.folder = m.folder) AS hasRisk
          FROM message m
         WHERE m.folder = :folder
           AND m.dateReceived = (SELECT MAX(x.dateReceived) FROM message x
                                  WHERE x.threadId = m.threadId AND x.folder = m.folder)
         GROUP BY m.threadId
         ORDER BY lastDate DESC
        """,
    )
    fun observeThreads(folder: Folder): Flow<List<ThreadSummary>>

    /** همهٔ پیامک‌های یک گفتگو، از همهٔ پوشه‌ها، برای نمایش `HiddenRun` (D43). */
    @Query("SELECT * FROM message WHERE threadId = :threadId ORDER BY dateReceived ASC")
    fun observeConversation(threadId: Long): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM message WHERE folder = :folder AND read = 0 AND outgoing = 0")
    fun observeUnreadCount(folder: Folder): Flow<Int>

    @Query("SELECT COUNT(*) FROM message WHERE folder = :folder")
    fun observeCount(folder: Folder): Flow<Int>

    @Query("SELECT COUNT(*) FROM message WHERE folder = :folder")
    suspend fun count(folder: Folder): Int

    @Query("UPDATE message SET read = 1 WHERE threadId = :threadId AND folder = :folder")
    suspend fun markThreadRead(threadId: Long, folder: Folder)

    @Query("UPDATE message SET folder = :folder, suggestMove = 0 WHERE kind = :kind AND providerId = :providerId")
    suspend fun moveMessage(kind: String, providerId: Long, folder: Folder)

    @Query("UPDATE message SET folder = :to WHERE normalizedAddress = :normalizedAddress AND folder = :from")
    suspend fun moveAllFrom(normalizedAddress: String, from: Folder, to: Folder)

    @Query("SELECT * FROM message WHERE kind = :kind AND providerId = :providerId")
    suspend fun find(kind: String, providerId: Long): MessageEntity?

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

    @Query("SELECT providerId FROM message WHERE threadId = :threadId AND folder = :folder AND providerId > 0")
    suspend fun providerIdsIn(threadId: Long, folder: Folder): List<Long>

    @Query("SELECT providerId FROM message WHERE kind = :kind AND folder = :folder AND providerId > 0")
    suspend fun providerIdsInFolder(kind: String, folder: Folder): List<Long>

    /**
     * وضعیت ارسال. «ناموفق» برگشت‌ناپذیر است: اگر یک تکه از پیامک چندتکه
     * نرسیده باشد، رسیدن تکهٔ بعدی آن را «ارسال‌شده» نمی‌کند.
     */
    @Query(
        """
        UPDATE message SET sendStatus = :status
         WHERE kind = 'SMS' AND providerId = :providerId
           AND (sendStatus != 'FAILED' OR :status = 'PENDING')
        """,
    )
    suspend fun setSendStatus(providerId: Long, status: SendStatus)

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
