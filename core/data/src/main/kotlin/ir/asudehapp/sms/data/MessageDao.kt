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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

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

    @Query("UPDATE message SET read = 1 WHERE threadId = :threadId AND folder = :folder")
    suspend fun markThreadRead(threadId: Long, folder: Folder)

    @Query("UPDATE message SET folder = :folder WHERE kind = :kind AND providerId = :providerId")
    suspend fun moveMessage(kind: String, providerId: Long, folder: Folder)

    @Query("UPDATE message SET folder = :to WHERE address = :address AND folder = :from")
    suspend fun moveAllFrom(address: String, from: Folder, to: Folder)

    @Query("SELECT * FROM message WHERE kind = :kind AND providerId = :providerId")
    suspend fun find(kind: String, providerId: Long): MessageEntity?

    @Query("SELECT * FROM message WHERE pendingClassify = 1")
    suspend fun pendingClassify(): List<MessageEntity>

    @Query("SELECT MAX(providerId) FROM message WHERE kind = :kind")
    suspend fun lastProviderId(kind: String): Long?

    @Query("SELECT * FROM message WHERE address = :address")
    suspend fun messagesFrom(address: String): List<MessageEntity>

    @Query("SELECT providerId FROM message WHERE threadId = :threadId AND folder = :folder")
    suspend fun providerIdsIn(threadId: Long, folder: Folder): List<Long>

    /**
     * شمار دسته‌های یک سرشماره، برای تشخیص `MixedSender`: سرشماره‌ای که هم
     * تبلیغ می‌فرستد و هم پیامک مهم (ADR-0006).
     */
    @Query(
        """
        SELECT address AS address,
               SUM(CASE WHEN category = 'PROMO' THEN 1 ELSE 0 END) AS promoCount,
               SUM(CASE WHEN category IN ('OTP', 'BANK', 'SERVICE') THEN 1 ELSE 0 END)
                 AS importantCount
          FROM message
         WHERE address = :address
         GROUP BY address
        """,
    )
    suspend fun statsFor(address: String): SenderStats?

    @Query("DELETE FROM message WHERE folder = :folder")
    suspend fun clearFolder(folder: Folder)
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
