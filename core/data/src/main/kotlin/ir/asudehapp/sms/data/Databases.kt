package ir.asudehapp.sms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Callback as RoomCallback
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ir.asudehapp.sms.model.Category
import ir.asudehapp.sms.model.Confidence
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.Origin
import ir.asudehapp.sms.model.ReasonCode

class AsudehConverters {
    @TypeConverter fun folderToString(value: Folder): String = value.name
    @TypeConverter fun stringToFolder(value: String): Folder = Folder.valueOf(value)

    @TypeConverter fun categoryToString(value: Category): String = value.name
    @TypeConverter fun stringToCategory(value: String): Category = Category.valueOf(value)

    @TypeConverter fun confidenceToString(value: Confidence): String = value.name
    @TypeConverter fun stringToConfidence(value: String): Confidence = Confidence.valueOf(value)

    @TypeConverter fun reasonToString(value: ReasonCode): String = value.name
    @TypeConverter fun stringToReason(value: String): ReasonCode = ReasonCode.valueOf(value)

    @TypeConverter fun originToString(value: Origin): String = value.name
    @TypeConverter fun stringToOrigin(value: String): Origin = Origin.valueOf(value)

    @TypeConverter fun sendStatusToString(value: SendStatus): String = value.name
    @TypeConverter fun stringToSendStatus(value: String): SendStatus = SendStatus.valueOf(value)

    @TypeConverter fun ruleKindToString(value: SenderRuleKind): String = value.name
    @TypeConverter fun stringToRuleKind(value: String): SenderRuleKind = SenderRuleKind.valueOf(value)

    @TypeConverter fun scheduleStateToString(value: ScheduleState): String = value.name
    @TypeConverter fun stringToScheduleState(value: String): ScheduleState = ScheduleState.valueOf(value)
}

/**
 * ایندکس محلی پیامک‌ها (D19 ب).
 *
 * متن پیامک‌ها از provider قابل بازسازی است، ولی جای هر پیامک (`Rescue`، پنهان
 * شدن زنده) نیست. برای همین **هیچ migration مخربی نداریم**: هر تغییر schema یک
 * `Migration` و آزمون خودش را می‌خواهد، و schema در `schemas/` نگه داشته می‌شود.
 *
 * این فایل در انتقال گوشی‌به‌گوشی منتقل نمی‌شود، چون شناسه‌های provider روی
 * گوشی تازه فرق دارند (`data_extraction_rules.xml`).
 */
@Database(
    entities = [
        MessageEntity::class,
        MessageFts::class,
        ThreadPrefEntity::class,
        TrashedMessageEntity::class,
        ScheduledMessageEntity::class,
        ThreadSummaryEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(AsudehConverters::class)
abstract class IndexDatabase : RoomDatabase() {

    abstract fun messages(): MessageDao

    abstract fun threadPrefs(): ThreadPrefDao

    abstract fun trash(): TrashDao

    abstract fun scheduled(): ScheduledMessageDao

    companion object {
        const val NAME = "index.db"

        @Volatile
        private var instance: IndexDatabase? = null

        fun get(context: Context): IndexDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                IndexDatabase::class.java,
                NAME,
            ).addMigrations(
                IndexMigrations.V1_V2,
                IndexMigrations.V2_V3,
                IndexMigrations.V3_V4,
                IndexMigrations.V4_V5,
                IndexMigrations.V5_V6,
            ).addCallback(object : RoomCallback() {
                override fun onOpen(db: SupportSQLiteDatabase) = IndexMigrations.ensureThreadSummary(db)
            }).build().also { instance = it }
        }
    }
}

/**
 * قواعد کاربر (`Allowlist` و `Blocklist`). این‌ها از هیچ جای دیگری قابل بازسازی
 * نیستند، پس جدا از ایندکس نگه داشته می‌شوند: هرگز migration مخرب ندارند و در
 * انتقال گوشی‌به‌گوشی منتقل می‌شوند (D57).
 */
@Database(
    entities = [SenderRuleEntity::class, KeywordRuleEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(AsudehConverters::class)
abstract class RulesDatabase : RoomDatabase() {

    abstract fun senderRules(): SenderRuleDao

    abstract fun keywordRules(): KeywordRuleDao

    companion object {
        const val NAME = "rules.db"

        @Volatile
        private var instance: RulesDatabase? = null

        fun get(context: Context): RulesDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RulesDatabase::class.java,
                NAME,
            ).addMigrations(RulesMigrations.V1_V2).build().also { instance = it }
        }
    }
}

/** migrationهای قواعد. مثل ایندکس، هیچ‌کدام داده‌ای را پاک نمی‌کند. */
object RulesMigrations {

    /** نسخهٔ ۲: کلیدواژه‌های «قواعد من» (ADR-0012). یک جدول تازه، بدون تغییر در جدول قبلی. */
    val V1_V2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (statement in V1_V2_SQL) db.execSQL(statement)
        }
    }

    internal val V1_V2_SQL: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `keyword_rule` (`normalized` TEXT NOT NULL, `keyword` TEXT NOT NULL, " +
            "`kind` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`normalized`))",
    )
}

/**
 * migrationهای ایندکس. هر کدام با schema خروجی Room در `schemas/` مقایسه
 * شده‌اند؛ هیچ‌کدام داده‌ای را پاک نمی‌کند.
 */
object IndexMigrations {

    /**
     * نسخهٔ ۲: جستجو (FTS4)، سنجاق و پیش‌نویس، و ستون‌های MMS. متن جستجوی
     * پیامک‌های قبلی خالی می‌ماند و همگام‌سازی بعدی آن را در Kotlin می‌سازد،
     * چون نرمال‌سازی فارسی در SQL ممکن نیست.
     */
    val V1_V2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (statement in V1_V2_SQL) db.execSQL(statement)
        }
    }

    /**
     * نسخهٔ ۳: سطل حذف‌شده‌ها (ADR-0010) و صف پیامک‌های زمان‌بندی‌شده
     * (ADR-0011). هر دو
     * جدول تازه‌اند؛ هیچ ستونی عوض یا پاک نمی‌شود.
     */
    val V2_V3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (statement in V2_V3_SQL) db.execSQL(statement)
        }
    }

    internal val V2_V3_SQL: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `trashed_message` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`kind` TEXT NOT NULL, `providerId` INTEGER NOT NULL, `address` TEXT NOT NULL, " +
            "`recipients` TEXT NOT NULL, `body` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
            "`dateReceived` INTEGER NOT NULL, `subId` INTEGER NOT NULL, `folder` TEXT NOT NULL, " +
            "`read` INTEGER NOT NULL, `outgoing` INTEGER NOT NULL, `attachments` INTEGER NOT NULL, " +
            "`deletedAt` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_trashed_message_deletedAt` ON `trashed_message` (`deletedAt`)",
        "CREATE TABLE IF NOT EXISTS `scheduled_message` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`threadId` INTEGER NOT NULL, `recipients` TEXT NOT NULL, `body` TEXT NOT NULL, " +
            "`subId` INTEGER NOT NULL, `sendAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "`state` TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_scheduled_message_sendAt` ON `scheduled_message` (`sendAt`)",
        "CREATE INDEX IF NOT EXISTS `index_scheduled_message_threadId` ON `scheduled_message` (`threadId`)",
    )

    /**
     * نسخهٔ ۴: بی‌صدا کردن یک گفتگو (PARITY §الف). یک ستون تازه روی
     * `thread_pref`، هیچ ستونی عوض یا پاک نمی‌شود.
     */
    val V3_V4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (statement in V3_V4_SQL) db.execSQL(statement)
        }
    }

    internal val V3_V4_SQL: List<String> = listOf(
        "ALTER TABLE `thread_pref` ADD COLUMN `muted` INTEGER NOT NULL DEFAULT 0",
    )

    /**
     * نسخهٔ ۵: بایگانی یک گفتگو (PARITY §الف). یک ستون تازه روی
     * `thread_pref`، هیچ ستونی عوض یا پاک نمی‌شود.
     */
    val V4_V5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (statement in V4_V5_SQL) db.execSQL(statement)
        }
    }

    internal val V4_V5_SQL: List<String> = listOf(
        "ALTER TABLE `thread_pref` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0",
    )

    /**
     * نسخهٔ ۶: جدول محاسبه‌شدهٔ (materialized) `thread_summary` (PARITY/STATUS:
     * صفحه‌بندی فهرست گفتگوها با Paging 3). این migration:
     *
     * 1. جدول تازه `thread_summary` را می‌سازد (یک ردیف برای هر جفت
     *    threadId+folder، دقیقاً مثل چیزی که `observeThreads` قبلاً هر بار با
     *    subquery حساب می‌کرد).
     * 2. چهار trigger روی `message` می‌سازد که این جدول را **همان لحظهٔ
     *    نوشتن** به‌روز نگه می‌دارند؛ الگو همان چیزی است که خود Room برای
     *    `message_fts` در نسخهٔ ۲ ساخته (`room_fts_content_sync_*`): چند
     *    trigger روی درج/به‌روزرسانی/حذف، به‌جای صدا زدن یک تابع Kotlin در هر
     *    محل نوشتن (که با تعداد زیاد مسیر نوشتن در `AsudehRepository` شکننده
     *    می‌بود). چون `upsert`/`insertMissing` با `OnConflictStrategy.REPLACE`
     *    در SQLite به یک DELETE و سپس INSERT ترجمه می‌شوند (نه UPDATE) — دقیقاً
     *    همان دلیلی که triggerهای FTS بالا هم BEFORE_DELETE/AFTER_INSERT جدا
     *    دارند — این چهار trigger هر دو مسیر (UPDATE مستقیم SQL، و
     *    INSERT OR REPLACE) را می‌پوشانند:
     *      - AFTER INSERT: ردیف (NEW.threadId, NEW.folder) را حساب می‌کند.
     *      - AFTER UPDATE: همیشه (NEW.threadId, NEW.folder) را دوباره حساب
     *        می‌کند (برای خواندن/نخوانده و ریسک که ممکن است بدون تغییر پوشه
     *        عوض شوند).
     *      - AFTER UPDATE...WHEN جفت عوض شده باشد: ردیف قدیمی
     *        (OLD.threadId, OLD.folder) را هم دوباره حساب می‌کند یا اگر دیگر
     *        پیامکی در آن نمانده حذفش می‌کند (برای `reclassify`/`moveMessage`
     *        که `folder` پیامک را عوض می‌کنند).
     *      - AFTER DELETE: مثل بالا برای (OLD.threadId, OLD.folder).
     *    تضمین سازگاری **فوری** است: triggerها بخشی از همان تراکنش نوشتن‌اند،
     *    نه یک پاک‌سازی بعدی؛ توضیح کامل در doc comment بالای
     *    `ThreadSummaryEntity`.
     * 3. جدول تازه را برای پیامک‌های موجود پر می‌کند (چون triggerها فقط روی
     *    نوشتن‌های *بعدی* اثر دارند).
     *
     * سنجاق/پیش‌نویس/بی‌صدا/بایگانی اینجا تکرار نمی‌شوند؛ کوئری‌های DAO آن‌ها را
     * هنوز با `LEFT JOIN` از `thread_pref` می‌خوانند.
     */
    val V5_V6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (statement in V5_V6_SQL) db.execSQL(statement)
        }
    }

    /** ستون‌های خروجی مشترک بین triggerها و پر کردن اولیهٔ `thread_summary`. */
    private const val THREAD_SUMMARY_COLUMNS =
        "`threadId`,`folder`,`address`,`snippet`,`lastDate`,`unread`,`total`,`hasRisk`,`recipients`,`attachments`"

    /** بدنهٔ SELECT محاسبهٔ ردیف خلاصه برای جفت (tid, fol) از روی `message`. */
    private fun recomputeSelect(tid: String, fol: String): String =
        "SELECT $tid, $fol, " +
            "(SELECT `address` FROM `message` WHERE `threadId` = $tid AND `folder` = $fol ORDER BY `dateReceived` DESC LIMIT 1), " +
            "(SELECT `body` FROM `message` WHERE `threadId` = $tid AND `folder` = $fol ORDER BY `dateReceived` DESC LIMIT 1), " +
            "(SELECT MAX(`dateReceived`) FROM `message` WHERE `threadId` = $tid AND `folder` = $fol), " +
            "(SELECT COUNT(*) FROM `message` WHERE `threadId` = $tid AND `folder` = $fol AND `read` = 0 AND `outgoing` = 0), " +
            "(SELECT COUNT(*) FROM `message` WHERE `threadId` = $tid AND `folder` = $fol), " +
            "(SELECT MAX(`risk`) FROM `message` WHERE `threadId` = $tid AND `folder` = $fol), " +
            "(SELECT `recipients` FROM `message` WHERE `threadId` = $tid AND `folder` = $fol ORDER BY `dateReceived` DESC LIMIT 1), " +
            "(SELECT `attachments` FROM `message` WHERE `threadId` = $tid AND `folder` = $fol ORDER BY `dateReceived` DESC LIMIT 1) " +
            "WHERE EXISTS (SELECT 1 FROM `message` WHERE `threadId` = $tid AND `folder` = $fol)"

    /** حذف ردیف خلاصهٔ (tid, fol) اگر دیگر هیچ پیامکی در آن جفت نمانده باشد. */
    private fun deleteIfEmpty(tid: String, fol: String): String =
        "DELETE FROM `thread_summary` WHERE `threadId` = $tid AND `folder` = $fol " +
            "AND NOT EXISTS (SELECT 1 FROM `message` WHERE `threadId` = $tid AND `folder` = $fol)"

    internal val V5_V6_SQL: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `thread_summary` (`threadId` INTEGER NOT NULL, `folder` TEXT NOT NULL, " +
            "`address` TEXT NOT NULL, `snippet` TEXT NOT NULL, `lastDate` INTEGER NOT NULL, " +
            "`unread` INTEGER NOT NULL, `total` INTEGER NOT NULL, `hasRisk` INTEGER NOT NULL, " +
            "`recipients` TEXT NOT NULL, `attachments` INTEGER NOT NULL, PRIMARY KEY(`threadId`, `folder`))",
        "CREATE INDEX IF NOT EXISTS `index_thread_summary_folder_lastDate` ON `thread_summary` (`folder`, `lastDate`)",
        "CREATE TRIGGER IF NOT EXISTS trg_thread_summary_ai AFTER INSERT ON `message` BEGIN " +
            "INSERT OR REPLACE INTO `thread_summary` ($THREAD_SUMMARY_COLUMNS) " +
            "${recomputeSelect("NEW.`threadId`", "NEW.`folder`")}; END",
        "CREATE TRIGGER IF NOT EXISTS trg_thread_summary_au AFTER UPDATE ON `message` BEGIN " +
            "INSERT OR REPLACE INTO `thread_summary` ($THREAD_SUMMARY_COLUMNS) " +
            "${recomputeSelect("NEW.`threadId`", "NEW.`folder`")}; END",
        "CREATE TRIGGER IF NOT EXISTS trg_thread_summary_au_move AFTER UPDATE ON `message` " +
            "WHEN OLD.`threadId` != NEW.`threadId` OR OLD.`folder` != NEW.`folder` BEGIN " +
            "${deleteIfEmpty("OLD.`threadId`", "OLD.`folder`")}; " +
            "INSERT OR REPLACE INTO `thread_summary` ($THREAD_SUMMARY_COLUMNS) " +
            "${recomputeSelect("OLD.`threadId`", "OLD.`folder`")}; END",
        "CREATE TRIGGER IF NOT EXISTS trg_thread_summary_ad AFTER DELETE ON `message` BEGIN " +
            "${deleteIfEmpty("OLD.`threadId`", "OLD.`folder`")}; " +
            "INSERT OR REPLACE INTO `thread_summary` ($THREAD_SUMMARY_COLUMNS) " +
            "${recomputeSelect("OLD.`threadId`", "OLD.`folder`")}; END",
        // پر کردن اولیه برای پیامک‌های موجود، چون triggerهای بالا فقط روی
        // نوشتن‌های بعدی اثر می‌گذارند. همان منطق انتخاب «آخرین پیامک هر جفت»
        // که `observeThreads` قبلاً داشت (GROUP BY برای هم‌زمانی مساوی).
        "INSERT INTO `thread_summary` ($THREAD_SUMMARY_COLUMNS) " +
            "SELECT m.`threadId`, m.`folder`, m.`address`, m.`body`, m.`dateReceived`, " +
            "(SELECT COUNT(*) FROM `message` u WHERE u.`threadId` = m.`threadId` AND u.`folder` = m.`folder` " +
            "AND u.`read` = 0 AND u.`outgoing` = 0), " +
            "(SELECT COUNT(*) FROM `message` t WHERE t.`threadId` = m.`threadId` AND t.`folder` = m.`folder`), " +
            "(SELECT MAX(r.`risk`) FROM `message` r WHERE r.`threadId` = m.`threadId` AND r.`folder` = m.`folder`), " +
            "m.`recipients`, m.`attachments` " +
            "FROM `message` m " +
            "WHERE m.`dateReceived` = (SELECT MAX(x.`dateReceived`) FROM `message` x " +
            "WHERE x.`threadId` = m.`threadId` AND x.`folder` = m.`folder`) " +
            "GROUP BY m.`threadId`, m.`folder`",
    )

    /**
     * triggerهای `thread_summary` فقط در migration ساخته می‌شوند و Room آن‌ها را
     * نمی‌شناسد؛ پایگاه‌داده‌ای که مستقیم روی نسخهٔ ۶ ساخته شود (نصب تازه، پاک شدن
     * داده) بی‌trigger و با جدول خالی می‌ماند و فهرست گفتگوها خالی نشان داده
     * می‌شود. پس در هر بار باز شدن، triggerهای ناموجود ساخته و اگر جدول خالی ولی
     * `message` پُر است، دوباره از روی `message` پر می‌شود. همه `IF NOT EXISTS`اند.
     */
    fun ensureThreadSummary(db: SupportSQLiteDatabase) {
        val summaryEmpty = db.query("SELECT NOT EXISTS (SELECT 1 FROM `thread_summary`)")
            .use { it.moveToFirst() && it.getInt(0) == 1 }
        val hasMessages = db.query("SELECT EXISTS (SELECT 1 FROM `message`)")
            .use { it.moveToFirst() && it.getInt(0) == 1 }
        for (statement in threadSummaryRepair(summaryEmpty && hasMessages)) db.execSQL(statement)
    }

    /** دستورهای [ensureThreadSummary]؛ جدا شده تا روی sqlite واقعی در JVM آزمود. */
    internal fun threadSummaryRepair(backfill: Boolean): List<String> =
        V5_V6_SQL.filter { it.startsWith("CREATE TRIGGER") } + if (backfill) listOf(V5_V6_SQL.last()) else emptyList()

    internal val V1_V2_SQL: List<String> = listOf(
        "ALTER TABLE `message` ADD COLUMN `recipients` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `message` ADD COLUMN `attachments` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `message` ADD COLUMN `pendingDownload` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `message` ADD COLUMN `searchText` TEXT NOT NULL DEFAULT ''",
        "CREATE TABLE IF NOT EXISTS `thread_pref` (`threadId` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, " +
            "`draft` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`threadId`))",
        "CREATE VIRTUAL TABLE IF NOT EXISTS `message_fts` USING FTS4(`searchText` TEXT NOT NULL, content=`message`)",
        // triggerهای هم‌گام‌سازی FTS، همان‌که Room برای `contentEntity` می‌سازد.
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_message_fts_BEFORE_UPDATE BEFORE UPDATE ON `message` " +
            "BEGIN DELETE FROM `message_fts` WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_message_fts_BEFORE_DELETE BEFORE DELETE ON `message` " +
            "BEGIN DELETE FROM `message_fts` WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_message_fts_AFTER_UPDATE AFTER UPDATE ON `message` " +
            "BEGIN INSERT INTO `message_fts`(`docid`, `searchText`) VALUES (NEW.`rowid`, NEW.`searchText`); END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_message_fts_AFTER_INSERT AFTER INSERT ON `message` " +
            "BEGIN INSERT INTO `message_fts`(`docid`, `searchText`) VALUES (NEW.`rowid`, NEW.`searchText`); END",
    )
}
