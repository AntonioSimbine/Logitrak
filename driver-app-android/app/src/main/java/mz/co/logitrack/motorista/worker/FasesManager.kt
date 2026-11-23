package mz.co.logitrack.motorista.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mz.co.logitrack.motorista.data.dto.CarregamentoDto
import mz.co.logitrack.motorista.data.dto.RegistarFasesRequest
import mz.co.logitrack.motorista.data.local.AppDb
import mz.co.logitrack.motorista.data.local.FilaEntity
import mz.co.logitrack.motorista.network.ApiErrors
import mz.co.logitrack.motorista.network.NetworkUtils
import mz.co.logitrack.motorista.network.RetrofitClient
import mz.co.logitrack.motorista.util.Formato
import mz.co.logitrack.motorista.util.Prefs
import mz.co.logitrack.motorista.util.Sessao

enum class FaseOperacional {
    INICIO_CARREGAMENTO,
    FIM_CARREGAMENTO,
    INICIO_TRANSPORTE
}

data class FasesResultado(
    val enviados: Int = 0,
    val falhas: Int = 0,
    val sessaoExpirada: Boolean = false,
    val semRede: Boolean = false
)

/** Grava os instantes reais no dispositivo antes de qualquer envio. */
object FasesManager {
    private val mutex = Mutex()

    /** @return `false` quando a fase já tinha instante registado (não se substitui o real). */
    suspend fun registar(
        context: Context,
        carregamentoId: Long?,
        uuidLocal: String?,
        fase: FaseOperacional
    ): Boolean {
        val dao = AppDb.get(context).filaDao()
        val motoristaId = Prefs(context).userId
        val entidade = when {
            !uuidLocal.isNullOrBlank() -> dao.porUuid(uuidLocal, motoristaId)
            carregamentoId != null && carregamentoId > 0L ->
                dao.porCarregamento(carregamentoId, motoristaId)
            else -> null
        } ?: return false

        val agora = Formato.agoraIso()
        val atualizado = when (fase) {
            FaseOperacional.INICIO_CARREGAMENTO -> {
                if (entidade.dataHoraInicioCarregamento != null) return false
                entidade.copy(dataHoraInicioCarregamento = agora)
            }
            FaseOperacional.FIM_CARREGAMENTO -> {
                if (entidade.dataHoraFimCarregamento != null) return false
                entidade.copy(dataHoraFimCarregamento = agora)
            }
            FaseOperacional.INICIO_TRANSPORTE -> {
                if (entidade.dataHoraInicioTransporte != null) return false
                entidade.copy(dataHoraInicioTransporte = agora)
            }
        }

        val inicio = System.nanoTime()
        dao.atualizar(
            atualizado.copy(
                fasesSincronizadasEm = null,
                tempoGravacaoLocalMs = 0L,
                erro = null
            )
        )
        val milissegundos = (System.nanoTime() - inicio) / 1_000_000L
        dao.atualizar(
            atualizado.copy(
                fasesSincronizadasEm = null,
                tempoGravacaoLocalMs = milissegundos,
                erro = null
            )
        )

        if (entidade.estado == "SINCRONIZADO" || entidade.carregamentoId != null) {
            agendarEnvio(context)
        }
        return true
    }

    /** Devolve o registo local do motorista, com as fases já gravadas. */
    suspend fun local(context: Context, carregamentoId: Long): FilaEntity? =
        AppDb.get(context).filaDao().porCarregamento(carregamentoId, Prefs(context).userId)

    /**
     * Cria, se necessário, a linha local de um carregamento aberto a partir do
     * dashboard remoto. A linha já nasce SINCRONIZADO, pelo que nunca entra na
     * fila de envio de carregamentos — serve apenas para gravar fases com a hora
     * real e enviá-las depois por PATCH.
     */
    suspend fun garantirLocal(context: Context, dados: CarregamentoDto): FilaEntity {
        val dao = AppDb.get(context).filaDao()
        val motoristaId = Prefs(context).userId
        dao.porCarregamento(dados.id, motoristaId)?.let { return it }
        val posicao = dados.ultimaPosicao
        val entidade = FilaEntity(
            uuidLocal = "remoto-$motoristaId-${dados.id}",
            motoristaId = motoristaId,
            guiaNumero = dados.guiaNumero,
            tipoCarga = dados.tipoCarga,
            quantidadeToneladas = dados.quantidadeToneladas,
            fabricaId = dados.fabrica?.id,
            fabricaNome = dados.fabrica?.nome,
            veiculoId = dados.veiculo?.id ?: 0L,
            veiculoMatricula = dados.veiculo?.matricula,
            latitude = posicao?.latitude ?: 0.0,
            longitude = posicao?.longitude ?: 0.0,
            precisaoMetros = posicao?.precisaoMetros ?: 0.0,
            fotoBase64 = "",
            dataHoraInicio = dados.dataHoraInicio,
            dataHoraInicioCarregamento = dados.fases?.inicioCarregamento,
            dataHoraFimCarregamento = dados.fases?.fimCarregamento,
            dataHoraInicioTransporte = dados.fases?.inicioTransporte,
            estado = "SINCRONIZADO",
            carregamentoId = dados.id,
            sincronizadoEm = System.currentTimeMillis()
        )
        dao.inserir(entidade)
        return entidade
    }

    fun agendarEnvio(context: Context) {
        val pedido = OneTimeWorkRequestBuilder<FasesWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(pedido)
    }

    suspend fun sincronizar(context: Context): FasesResultado = mutex.withLock {
        val prefs = Prefs(context)
        if (prefs.token.isNullOrBlank()) return@withLock FasesResultado()
        if (!NetworkUtils.estaLigado(context)) {
            val temPendentes = try {
                AppDb.get(context).filaDao().fasesPendentes(prefs.userId).isNotEmpty()
            } catch (_: Exception) { false }
            return@withLock FasesResultado(semRede = temPendentes)
        }
        val dao = AppDb.get(context).filaDao()
        val pendentes = dao.fasesPendentes(prefs.userId)
        if (pendentes.isEmpty()) return@withLock FasesResultado()

        val api = RetrofitClient.getApi(context)
        var enviados = 0
        var falhas = 0
        var sessaoExpirada = false
        for (item in pendentes) {
            val carregamentoId = item.carregamentoId ?: continue
            val pedido = RegistarFasesRequest(
                dataHoraInicioCarregamento = item.dataHoraInicioCarregamento,
                dataHoraFimCarregamento = item.dataHoraFimCarregamento,
                dataHoraInicioTransporte = item.dataHoraInicioTransporte
            )
            try {
                val resp = api.registarFases(carregamentoId, pedido)
                when {
                    resp.isSuccessful -> {
                        dao.atualizar(item.copy(fasesSincronizadasEm = System.currentTimeMillis(), erro = null))
                        enviados++
                    }
                    resp.code() == 401 -> {
                        prefs.sessionExpired = true
                        Sessao.sinalizarExpiracao()
                        sessaoExpirada = true
                        break
                    }
                    resp.code() == 400 || resp.code() == 422 || resp.code() == 403 -> {
                        // Sequência inválida ou sem permissão: não pode repetir para sempre.
                        dao.atualizar(
                            item.copy(
                                fasesSincronizadasEm = System.currentTimeMillis(),
                                erro = ApiErrors.mensagem(context, resp)
                            )
                        )
                    }
                    else -> {
                        dao.atualizar(item.copy(erro = ApiErrors.mensagem(context, resp)))
                        falhas++
                    }
                }
            } catch (_: Exception) {
                // Permanece pendente e será reenviado no ciclo seguinte.
                falhas++
            }
        }
        FasesResultado(enviados = enviados, falhas = falhas, sessaoExpirada = sessaoExpirada)
    }
}
