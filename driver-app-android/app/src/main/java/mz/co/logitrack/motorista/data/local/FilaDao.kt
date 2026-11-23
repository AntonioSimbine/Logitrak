package mz.co.logitrack.motorista.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FilaDao {
    @Query(
        """
        SELECT uuidLocal, guiaNumero, tipoCarga, quantidadeToneladas, fabricaNome,
               veiculoMatricula, estado, erro, carregamentoId, criadoEm, sincronizadoEm
        FROM fila WHERE motoristaId = :motoristaId ORDER BY criadoEm DESC
        """
    )
    fun resumos(motoristaId: Long): Flow<List<FilaResumo>>

    @Query(
        """
        SELECT uuidLocal, guiaNumero, fabricaNome, tipoCarga, quantidadeToneladas,
               veiculoMatricula, estado, carregamentoId, criadoEm
        FROM fila WHERE motoristaId = :motoristaId ORDER BY criadoEm DESC
        """
    )
    fun documentos(motoristaId: Long): Flow<List<DocumentoResumo>>

    @Query(
        "SELECT uuidLocal FROM fila " +
            "WHERE estado IN ('PENDENTE','REJEITADO') AND motoristaId = :motoristaId " +
            "ORDER BY criadoEm ASC"
    )
    suspend fun pendentesUuids(motoristaId: Long): List<String>

    @Query("SELECT * FROM fila WHERE uuidLocal = :uuid AND motoristaId = :motoristaId LIMIT 1")
    suspend fun porUuid(uuid: String, motoristaId: Long): FilaEntity?

    @Query("SELECT * FROM fila WHERE estado = 'PENDENTE' AND motoristaId = :motoristaId ORDER BY criadoEm ASC")
    suspend fun pendentes(motoristaId: Long): List<FilaEntity>

    @Query("SELECT * FROM fila WHERE motoristaId = :motoristaId AND carregamentoId = :carregamentoId LIMIT 1")
    suspend fun porCarregamento(carregamentoId: Long, motoristaId: Long): FilaEntity?

    @Query(
        """
        SELECT * FROM fila
        WHERE motoristaId = :motoristaId AND carregamentoId IS NOT NULL AND carregamentoId > 0
        ORDER BY criadoEm DESC LIMIT 1
        """
    )
    suspend fun ultimoCarregamento(motoristaId: Long): FilaEntity?

    @Query(
        """
        SELECT * FROM fila
        WHERE motoristaId = :motoristaId AND carregamentoId IS NOT NULL AND carregamentoId > 0
          AND fasesSincronizadasEm IS NULL
          AND (dataHoraInicioCarregamento IS NOT NULL
            OR dataHoraFimCarregamento IS NOT NULL
            OR dataHoraInicioTransporte IS NOT NULL)
        ORDER BY criadoEm ASC
        """
    )
    suspend fun fasesPendentes(motoristaId: Long): List<FilaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(entity: FilaEntity)

    @Update
    suspend fun atualizar(entity: FilaEntity)

    @Query("DELETE FROM fila WHERE uuidLocal = :uuid AND motoristaId = :motoristaId")
    suspend fun remover(uuid: String, motoristaId: Long)

    @Query("UPDATE fila SET estado = 'PENDENTE', erro = NULL WHERE uuidLocal = :uuid AND motoristaId = :motoristaId")
    suspend fun reporPendente(uuid: String, motoristaId: Long)

    @Query("DELETE FROM fila WHERE motoristaId = :motoristaId")
    suspend fun limparDoMotorista(motoristaId: Long)
}
