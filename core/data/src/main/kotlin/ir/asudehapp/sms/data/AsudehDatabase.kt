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

    @TypeConverter fun ruleKindToString(value: SenderRuleKind): String = value.name
    @TypeConverter fun stringToRuleKind(value: String): SenderRuleKind = SenderRuleKind.valueOf(value)
}

@Database(
    entities = [MessageEntity::class, SenderRuleEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(AsudehConverters::class)
abstract class AsudehDatabase : RoomDatabase() {

    abstract fun messages(): MessageDao

    abstract fun senderRules(): SenderRuleDao

    companion object {
        private const val NAME = "asudeh-index.db"

        @Volatile
        private var instance: AsudehDatabase? = null

        fun get(context: Context): AsudehDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AsudehDatabase::class.java,
                NAME,
            )
                // ایندکس محلی همیشه از provider قابل بازسازی است، پس پاک کردن آن
                // هزینه‌ای جز یک بازسازی ندارد و هیچ پیامکی از بین نمی‌رود.
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
