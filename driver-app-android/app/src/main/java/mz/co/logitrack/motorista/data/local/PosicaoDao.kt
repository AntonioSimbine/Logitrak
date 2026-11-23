package mz.co.logitrack.motorista.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PosicaoDao {
    @Query(
        """
        SELECT * FROM posicoes
        WHERE motoristaId = :motoristaId AND enviadoEm IS NULL
        ORDER BY criadoEm ASC LIMIT 200
        """
    )
    suspend fun pendentes(motoristaId: Long): List<PosicaoEntity>

    @Insert
    suspend fun inserir(entity: PosicaoEntity)

    @Update
    suspend fun atualizar(entity: PosicaoEntity)

    @Query("DELETE FROM posicoes WHERE enviadoEm IS NOT NULL AND criadoEm < :antes")
    suspend fun limparEnviadas(antes: Long)

    @Query("DELETE FROM posicoes WHERE motoristaId = :motoristaId")
    suspend fun limparDoMotorista(motoristaId: Long)
}
