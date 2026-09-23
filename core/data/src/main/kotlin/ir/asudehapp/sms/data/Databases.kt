package ir.asudehapp.sms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    ],
    version = 4,
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
            ).addMigrations(IndexMigrations.V1_V2, IndexMigrations.V2_V3, IndexMigrations.V3_V4)
                .build().also { instance = it }
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
