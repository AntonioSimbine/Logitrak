package mz.co.logitrack.motorista.worker

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.dto.PosicaoRequest
import mz.co.logitrack.motorista.data.dto.SyncRequest
import mz.co.logitrack.motorista.data.local.AppDb
import mz.co.logitrack.motorista.data.local.FilaEntity
import mz.co.logitrack.motorista.network.ApiErrors
import mz.co.logitrack.motorista.network.Idempotencia
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
    val nadaPorEnviar: Boolean = false,
    val duplicados: Int = 0,
    val posicoesEnviadas: Int = 0
)

enum class ResultadoItem { ENVIADO, REJEITADO, FALHA, SESSAO, SEM_REDE }

private data class EnvioPosicoes(
    val enviados: Int = 0,
    val semRede: Boolean = false,
    val sessaoExpirada: Boolean = false
)

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
        val motoristaId = prefs.userId
        val dao = AppDb.get(context).filaDao()

        val posicoes = enviarPosicoes(context, motoristaId)
        val uuids = try {
            dao.pendentesUuids(motoristaId)
        } catch (e: Exception) {
            return@withLock SyncResultado(falhas = 1)
        }
        if (uuids.isEmpty() && posicoes.enviados == 0 && !posicoes.semRede && !posicoes.sessaoExpirada) {
            return@withLock SyncResultado(nadaPorEnviar = true)
        }

        var enviados = posicoes.enviados
        var rejeitados = 0
        var falhas = 0
        var duplicados = 0
        var sessao = posicoes.sessaoExpirada
        for (uuid in uuids) {
            val item = dao.porUuid(uuid, motoristaId) ?: continue
            when (val resultado = enviar(context, item)) {
                ResultadoItem.ENVIADO -> {
                    enviados++
                    if (Idempotencia.consumir()) duplicados++
                }
                ResultadoItem.REJEITADO -> rejeitados++
                ResultadoItem.SESSAO -> {
                    sessao = true
                    break
                }
                ResultadoItem.SEM_REDE -> {
                    val resultadoFinal = SyncResultado(
                        enviados = enviados,
                        rejeitados = rejeitados,
                        falhas = falhas,
                        semRede = true,
                        duplicados = duplicados,
                        posicoesEnviadas = posicoes.enviados
                    )
                    _eventos.tryEmit(resultadoFinal)
                    return@withLock resultadoFinal
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
            sessaoExpirada = sessao,
            duplicados = duplicados,
            posicoesEnviadas = posicoes.enviados
        )
        if (enviados > 0 || rejeitados > 0 || falhas > 0) {
            _eventos.tryEmit(resultado)
        }
        resultado
    }

    suspend fun sincronizarUm(context: Context, uuid: String): ResultadoItem = mutex.withLock {
        if (!NetworkUtils.estaLigado(context)) return@withLock ResultadoItem.SEM_REDE
        val motoristaId = Prefs(context).userId
        val item = AppDb.get(context).filaDao().porUuid(uuid, motoristaId)
            ?: return@withLock ResultadoItem.FALHA
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
            dataHoraInicioCarregamento = item.dataHoraInicioCarregamento,
            dataHoraFimCarregamento = item.dataHoraFimCarregamento,
            dataHoraInicioTransporte = item.dataHoraInicioTransporte,
            fotoBase64 = item.fotoBase64,
            confirmarForaGeofence = item.confirmarForaGeofence
        )
        val tentativa = item.copy(
            tentativas = item.tentativas + 1,
            ultimaTentativa = System.currentTimeMillis()
        )
        Idempotencia.consumir()
        return try {
            // Impedir que o botão fique indefinidamente em "Envio em curso".
            // O pedido continua pendente e pode ser repetido se o servidor não responder.
            val resp = withTimeout(45_000L) { api.syncCarregamento(pedido) }
            when {
                resp.isSuccessful && resp.body() != null -> {
                    val body = resp.body()!!
                    val temFases = item.dataHoraInicioCarregamento != null ||
                        item.dataHoraFimCarregamento != null ||
                        item.dataHoraInicioTransporte != null
                    dao.atualizar(
                        tentativa.copy(
                            estado = "SINCRONIZADO",
                            erro = null,
                            carregamentoId = body.id,
                            sincronizadoEm = System.currentTimeMillis(),
                            // As fases enviadas no sync não precisam de PATCH posterior.
                            fasesSincronizadasEm = if (temFases) System.currentTimeMillis() else null
                        )
                    )
                    ResultadoItem.ENVIADO
                }
                resp.code() == 401 -> {
                    prefs.sessionExpired = true
                    dao.atualizar(tentativa.copy(erro = ApiErrors.mensagem(context, resp)))
                    Sessao.sinalizarExpiracao()
                    ResultadoItem.SESSAO
                }
                resp.code() == 400 || resp.code() == 422 || resp.code() == 409 -> {
                    dao.atualizar(
                        tentativa.copy(estado = "REJEITADO", erro = ApiErrors.mensagem(context, resp))
                    )
                    ResultadoItem.REJEITADO
                }
                else -> {
                    dao.atualizar(tentativa.copy(erro = ApiErrors.mensagem(context, resp)))
                    ResultadoItem.FALHA
                }
            }
        } catch (e: TimeoutCancellationException) {
            dao.atualizar(tentativa.copy(erro = context.getString(R.string.sync_error_timeout)))
            ResultadoItem.FALHA
        } catch (e: IOException) {
            dao.atualizar(tentativa.copy(erro = context.getString(R.string.sync_error_offline)))
            ResultadoItem.SEM_REDE
        } catch (e: Exception) {
            dao.atualizar(tentativa.copy(erro = context.getString(R.string.sync_error_generic)))
            ResultadoItem.FALHA
        }
    }

    /** Envia os pontos de GPS guardados com o timestamp original da captura. */
    private suspend fun enviarPosicoes(context: Context, motoristaId: Long): EnvioPosicoes {
        val dao = AppDb.get(context).posicaoDao()
        val pendentes = try {
            dao.pendentes(motoristaId)
        } catch (e: Exception) {
            return EnvioPosicoes()
        }
        if (pendentes.isEmpty()) return EnvioPosicoes()

        val api = RetrofitClient.getApi(context)
        var enviados = 0
        for (ponto in pendentes) {
            val pedido = PosicaoRequest(
                latitude = ponto.latitude,
                longitude = ponto.longitude,
                precisaoMetros = ponto.precisaoMetros,
                timestamp = ponto.timestamp
            )
            try {
                val resp = withTimeout(30_000L) { api.registarPosicao(ponto.carregamentoId, pedido) }
                when {
                    resp.isSuccessful || resp.code() == 409 -> {
                        dao.atualizar(ponto.copy(enviadoEm = System.currentTimeMillis()))
                        enviados++
                    }
                    resp.code() == 401 -> {
                        Prefs(context).sessionExpired = true
                        Sessao.sinalizarExpiracao()
                        return EnvioPosicoes(enviados = enviados, sessaoExpirada = true)
                    }
                    resp.code() == 400 || resp.code() == 422 -> {
                        // Instante inválido não pode bloquear a fila para sempre.
                        dao.atualizar(
                            ponto.copy(
                                enviadoEm = System.currentTimeMillis(),
                                erro = ApiErrors.mensagem(context, resp)
                            )
                        )
                    }
                    else -> {
                        dao.atualizar(
                            ponto.copy(
                                tentativas = ponto.tentativas + 1,
                                ultimaTentativa = System.currentTimeMillis(),
                                erro = ApiErrors.mensagem(context, resp)
                            )
                        )
                    }
                }
            } catch (e: IOException) {
                return EnvioPosicoes(enviados = enviados, semRede = true)
            } catch (_: TimeoutCancellationException) {
                return EnvioPosicoes(enviados = enviados, semRede = true)
            } catch (e: Exception) {
                dao.atualizar(
                    ponto.copy(
                        tentativas = ponto.tentativas + 1,
                        ultimaTentativa = System.currentTimeMillis(),
                        erro = context.getString(R.string.sync_error_position)
                    )
                )
            }
        }
        return EnvioPosicoes(enviados = enviados)
    }
}
