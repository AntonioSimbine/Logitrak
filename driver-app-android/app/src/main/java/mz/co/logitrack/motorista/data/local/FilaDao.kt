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

    @Query("SELECT uuidLocal FROM fila WHERE estado = 'PENDENTE' ORDER BY criadoEm ASC")
    suspend fun pendentesUuids(): List<String>

    @Query("SELECT * FROM fila WHERE uuidLocal = :uuid LIMIT 1")
    suspend fun porUuid(uuid: String): FilaEntity?

    @Query("SELECT * FROM fila WHERE estado = 'PENDENTE'")
    suspend fun pendentes(): List<FilaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(entity: FilaEntity)

    @Update
    suspend fun atualizar(entity: FilaEntity)

    @Query("DELETE FROM fila WHERE uuidLocal = :uuid")
    suspend fun remover(uuid: String)

    @Query("UPDATE fila SET estado = 'PENDENTE', erro = NULL WHERE uuidLocal = :uuid")
    suspend fun reporPendente(uuid: String)
}
