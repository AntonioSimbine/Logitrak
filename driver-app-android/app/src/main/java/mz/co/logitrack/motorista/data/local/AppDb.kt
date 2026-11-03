package mz.co.logitrack.motorista.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FilaEntity::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun filaDao(): FilaDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null

        private val MIGRACAO_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fila ADD COLUMN fabricaNome TEXT")
                db.execSQL("ALTER TABLE fila ADD COLUMN veiculoMatricula TEXT")
                db.execSQL("ALTER TABLE fila ADD COLUMN tentativas INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE fila ADD COLUMN ultimaTentativa INTEGER")
            }
        }

        fun get(context: Context): AppDb {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    "logitrack_motorista.db"
                )
                    .addMigrations(MIGRACAO_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
