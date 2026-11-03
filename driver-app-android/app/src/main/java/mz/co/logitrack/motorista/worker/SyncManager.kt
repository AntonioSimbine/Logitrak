package mz.co.logitrack.motorista.worker

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import mz.co.logitrack.motorista.data.dto.SyncRequest
import mz.co.logitrack.motorista.data.local.AppDb
import mz.co.logitrack.motorista.data.local.FilaEntity
import mz.co.logitrack.motorista.network.ApiErrors
import mz.co.logitrack.motorista.network.NetworkUtils
import mz.co.logitrack.motorista.network.RetrofitClient
import mz.co.logitrack.motorista.util.Prefs
import mz.co.logitrack.motorista.util.Sessao
import java.io.IOException

data class SyncResultado(
    val enviados: Int = 0,
    val rejeitados: Int = 0,
    val falhas: Int = 0,
    val semRede: Boolean = false,
    val sessaoExpirada: Boolean = false,
    val nadaPorEnviar: Boolean = false
)

enum class ResultadoItem { ENVIADO, REJEITADO, FALHA, SESSAO, SEM_REDE }

/**
 * Motor de sincronização partilhado pelo ecrã Pendentes, pelo painel e pelo
 * worker periódico. Só existe um envio em curso de cada vez.
 */
object SyncManager {
    private val mutex = Mutex()

    private val _eventos = MutableSharedFlow<SyncResultado>(extraBufferCapacity = 4)
    /** Avisos de sincronizações concluídas, para o painel e a fila se atualizarem. */
    val eventos: SharedFlow<SyncResultado> = _eventos

    suspend fun sincronizar(context: Context): SyncResultado = mutex.withLock {
        val prefs = Prefs(context)
        if (prefs.token.isNullOrBlank()) {
            return@withLock SyncResultado(sessaoExpirada = true)
        }
        if (!NetworkUtils.estaLigado(context)) {
            return@withLock SyncResultado(semRede = true)
        }
        val dao = AppDb.get(context).filaDao()
        val uuids = try {
            dao.pendentesUuids()
        } catch (e: Exception) {
            return@withLock SyncResultado(falhas = 1)
        }
        if (uuids.isEmpty()) {
            return@withLock SyncResultado(nadaPorEnviar = true)
        }

        var enviados = 0
        var rejeitados = 0
        var falhas = 0
        var sessao = false
        for (uuid in uuids) {
            val item = dao.porUuid(uuid) ?: continue
            when (enviar(context, item)) {
                ResultadoItem.ENVIADO -> enviados++
                ResultadoItem.REJEITADO -> rejeitados++
                ResultadoItem.SESSAO -> {
                    sessao = true
                    break
                }
                ResultadoItem.SEM_REDE -> {
                    val resultado = SyncResultado(
                        enviados = enviados,
                        rejeitados = rejeitados,
                        falhas = falhas,
                        semRede = true
                    )
                    if (enviados > 0 || rejeitados > 0) _eventos.tryEmit(resultado)
                    return@withLock resultado
                }
                ResultadoItem.FALHA -> falhas++
            }
        }
        if (enviados > 0) {
            prefs.ultimaSync = System.currentTimeMillis()
        }
        val resultado = SyncResultado(
            enviados = enviados,
            rejeitados = rejeitados,
            falhas = falhas,
            sessaoExpirada = sessao
        )
        if (enviados > 0 || rejeitados > 0 || falhas > 0) {
            _eventos.tryEmit(resultado)
        }
        resultado
    }

    suspend fun sincronizarUm(context: Context, uuid: String): ResultadoItem = mutex.withLock {
        if (!NetworkUtils.estaLigado(context)) return@withLock ResultadoItem.SEM_REDE
        val item = AppDb.get(context).filaDao().porUuid(uuid) ?: return@withLock ResultadoItem.FALHA
        enviar(context, item).also { resultado ->
            if (resultado == ResultadoItem.ENVIADO) {
                Prefs(context).ultimaSync = System.currentTimeMillis()
            }
            when (resultado) {
                ResultadoItem.ENVIADO -> _eventos.tryEmit(SyncResultado(enviados = 1))
                ResultadoItem.REJEITADO -> _eventos.tryEmit(SyncResultado(rejeitados = 1))
                else -> Unit
            }
        }
    }

    private suspend fun enviar(context: Context, item: FilaEntity): ResultadoItem {
        val prefs = Prefs(context)
        val dao = AppDb.get(context).filaDao()
        val api = RetrofitClient.getApi(context)
        val pedido = SyncRequest(
            uuidLocal = item.uuidLocal,
            guiaNumero = item.guiaNumero,
            tipoCarga = item.tipoCarga,
            quantidadeToneladas = item.quantidadeToneladas,
            fabricaId = item.fabricaId,
            veiculoId = item.veiculoId,
            latitude = item.latitude,
            longitude = item.longitude,
            precisaoMetros = item.precisaoMetros,
            dataHoraInicio = item.dataHoraInicio,
            fotoBase64 = item.fotoBase64,
            confirmarForaGeofence = item.confirmarForaGeofence
        )
        val tentativa = item.copy(
            tentativas = item.tentativas + 1,
            ultimaTentativa = System.currentTimeMillis()
        )
        return try {
            // Impedir que o botão fique indefinidamente em "A sincronizar".
            // O pedido continua pendente e pode ser repetido se o servidor não responder.
            val resp = withTimeout(45_000L) { api.syncCarregamento(pedido) }
            when {
                resp.isSuccessful && resp.body() != null -> {
                    val body = resp.body()!!
                    dao.atualizar(
                        tentativa.copy(
                            estado = "SINCRONIZADO",
                            erro = null,
                            carregamentoId = body.id,
                            sincronizadoEm = System.currentTimeMillis()
                        )
                    )
                    ResultadoItem.ENVIADO
                }
                resp.code() == 401 -> {
                    prefs.sessionExpired = true
                    dao.atualizar(tentativa.copy(erro = ApiErrors.mensagem(resp)))
                    Sessao.sinalizarExpiracao()
                    ResultadoItem.SESSAO
                }
                resp.code() == 400 || resp.code() == 422 || resp.code() == 409 -> {
                    dao.atualizar(
                        tentativa.copy(estado = "REJEITADO", erro = ApiErrors.mensagem(resp))
                    )
                    ResultadoItem.REJEITADO
                }
                else -> {
                    dao.atualizar(tentativa.copy(erro = ApiErrors.mensagem(resp)))
                    ResultadoItem.FALHA
                }
            }
        } catch (e: TimeoutCancellationException) {
            dao.atualizar(tentativa.copy(erro = "Tempo limite excedido (45 s). O carregamento continua pendente e será tentado novamente."))
            ResultadoItem.FALHA
        } catch (e: IOException) {
            dao.atualizar(tentativa.copy(erro = "Sem ligação ao servidor. Será tentado novamente."))
            ResultadoItem.SEM_REDE
        } catch (e: Exception) {
            dao.atualizar(tentativa.copy(erro = e.message ?: "Falha de comunicação."))
            ResultadoItem.FALHA
        }
    }
}
