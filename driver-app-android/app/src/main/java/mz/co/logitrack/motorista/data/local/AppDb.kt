package mz.co.logitrack.motorista.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FilaEntity::class, PosicaoEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun filaDao(): FilaDao
    abstract fun posicaoDao(): PosicaoDao

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

        private val MIGRACAO_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fila ADD COLUMN dataHoraInicioCarregamento TEXT")
                db.execSQL("ALTER TABLE fila ADD COLUMN dataHoraFimCarregamento TEXT")
                db.execSQL("ALTER TABLE fila ADD COLUMN dataHoraInicioTransporte TEXT")
                db.execSQL("ALTER TABLE fila ADD COLUMN fasesSincronizadasEm INTEGER")
                db.execSQL("ALTER TABLE fila ADD COLUMN tempoGravacaoLocalMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS posicoes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        carregamentoId INTEGER NOT NULL,
                        motoristaId INTEGER NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        precisaoMetros REAL NOT NULL,
                        timestamp TEXT NOT NULL,
                        criadoEm INTEGER NOT NULL,
                        tentativas INTEGER NOT NULL DEFAULT 0,
                        ultimaTentativa INTEGER,
                        erro TEXT,
                        enviadoEm INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_posicoes_pendentes " +
                        "ON posicoes(motoristaId, enviadoEm, criadoEm)"
                )
            }
        }

        fun get(context: Context): AppDb {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    "logitrack_motorista.db"
                )
                    .addMigrations(MIGRACAO_1_2, MIGRACAO_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
