package ir.asudehapp.sms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
    entities = [MessageEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(AsudehConverters::class)
abstract class IndexDatabase : RoomDatabase() {

    abstract fun messages(): MessageDao

    companion object {
        const val NAME = "index.db"

        @Volatile
        private var instance: IndexDatabase? = null

        fun get(context: Context): IndexDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                IndexDatabase::class.java,
                NAME,
            ).build().also { instance = it }
        }
    }
}

/**
 * قواعد کاربر (`Allowlist` و `Blocklist`). این‌ها از هیچ جای دیگری قابل بازسازی
 * نیستند، پس جدا از ایندکس نگه داشته می‌شوند: هرگز migration مخرب ندارند و در
 * انتقال گوشی‌به‌گوشی منتقل می‌شوند (D57).
 */
@Database(
    entities = [SenderRuleEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(AsudehConverters::class)
abstract class RulesDatabase : RoomDatabase() {

    abstract fun senderRules(): SenderRuleDao

    companion object {
        const val NAME = "rules.db"

        @Volatile
        private var instance: RulesDatabase? = null

        fun get(context: Context): RulesDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RulesDatabase::class.java,
                NAME,
            ).build().also { instance = it }
        }
    }
}
