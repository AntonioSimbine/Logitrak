package mz.co.logitrack.motorista.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import mz.co.logitrack.motorista.util.CriptoConverters

@Entity(tableName = "fila")
data class FilaEntity(
    @PrimaryKey val uuidLocal: String,
    val motoristaId: Long,
    val guiaNumero: String,
    val tipoCarga: String,
    val quantidadeToneladas: Double,
    val fabricaId: Long?,
    val fabricaNome: String?,
    val veiculoId: Long,
    val veiculoMatricula: String?,
    val latitude: Double,
    val longitude: Double,
    val precisaoMetros: Double,
    @TypeConverters(CriptoConverters::class)
    val fotoBase64: String,
    val dataHoraInicio: String?,
    val dataHoraInicioCarregamento: String? = null,
    val dataHoraFimCarregamento: String? = null,
    val dataHoraInicioTransporte: String? = null,
    val fasesSincronizadasEm: Long? = null,
    val tempoGravacaoLocalMs: Long = 0L,
    val estado: String = "PENDENTE",
    val erro: String? = null,
    val carregamentoId: Long? = null,
    val criadoEm: Long = System.currentTimeMillis(),
    val sincronizadoEm: Long? = null,
    val tentativas: Int = 0,
    val ultimaTentativa: Long? = null,
    val confirmarForaGeofence: Boolean = false
)

/** Projeção sem a fotografia para o ecrã de documentos. */
data class DocumentoResumo(
    val uuidLocal: String,
    val guiaNumero: String,
    val fabricaNome: String?,
    val tipoCarga: String,
    val quantidadeToneladas: Double,
    val veiculoMatricula: String?,
    val estado: String,
    val carregamentoId: Long?,
    val criadoEm: Long
)

/** Projeção sem a fotografia para nunca encher o CursorWindow. */
data class FilaResumo(
    val uuidLocal: String,
    val guiaNumero: String,
    val tipoCarga: String,
    val quantidadeToneladas: Double,
    val fabricaNome: String?,
    val veiculoMatricula: String?,
    val estado: String,
    val erro: String?,
    val carregamentoId: Long?,
    val criadoEm: Long,
    val sincronizadoEm: Long?
)
