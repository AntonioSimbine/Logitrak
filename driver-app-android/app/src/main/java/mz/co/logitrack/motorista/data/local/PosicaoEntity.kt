package mz.co.logitrack.motorista.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ponto de GPS guardado com o instante original da captura, mesmo sem rede. */
@Entity(tableName = "posicoes")
data class PosicaoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val carregamentoId: Long,
    val motoristaId: Long,
    val latitude: Double,
    val longitude: Double,
    val precisaoMetros: Double,
    val timestamp: String,
    val criadoEm: Long = System.currentTimeMillis(),
    val tentativas: Int = 0,
    val ultimaTentativa: Long? = null,
    val erro: String? = null,
    val enviadoEm: Long? = null
)
