package mz.co.logitrack.motorista.ui

import android.content.Context
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.dto.NotificacaoDto
import mz.co.logitrack.motorista.data.local.FilaResumo
import mz.co.logitrack.motorista.util.Formato
import mz.co.logitrack.motorista.util.Prefs

/** Notificação apresentada ao motorista, já pronta para a lista. */
data class NotificacaoUi(
    val id: String,
    val origem: String,
    val origemEtiqueta: String,
    val titulo: String,
    val texto: String,
    val momento: Long,
    val carregamentoId: Long?,
    val lida: Boolean,
    /** Identificador no servidor; nulo para avisos gerados apenas no dispositivo. */
    val remotaId: Long? = null
)

/**
 * Convertas as notificações persistidas pelo servidor e, quando não há rede,
 * complementa com avisos derivados do cache local. A origem e a autoria de
 * cada aviso vêm sempre da API — o dispositivo nunca inventa um remetente.
 */
object Notificacoes {
    const val ORIGEM_SISTEMA = "SISTEMA"
    const val ORIGEM_OPERADOR = "OPERADOR"
    const val ORIGEM_ADMIN = "ADMINISTRADOR"
    const val ORIGEM_DESCONHECIDO = "DESCONHECIDO"

    fun listar(
        contexto: Context,
        prefs: Prefs,
        fila: List<FilaResumo>,
        servidor: List<NotificacaoDto> = prefs.lerNotificacoes()
    ): List<NotificacaoUi> {
        val lidas = prefs.notificacoesLidas()
        val itens = mutableListOf<NotificacaoUi>()

        servidor.forEach { n ->
            val origem = n.origem?.uppercase()?.takeIf { it.isNotBlank() }
                ?: ORIGEM_DESCONHECIDO
            val autor = n.autorNome?.trim().orEmpty()
            val texto = buildString {
                append(n.mensagem?.trim().orEmpty())
                if (autor.isNotBlank() && origem != ORIGEM_SISTEMA) {
                    if (isNotEmpty()) append('\n')
                    append(contexto.getString(R.string.notif_autor, autor))
                }
            }.ifBlank { contexto.getString(R.string.notif_sem_texto) }
            itens += NotificacaoUi(
                id = "servidor:${n.id}",
                origem = origem,
                origemEtiqueta = etiquetaOrigem(contexto, origem, n.autorPerfil),
                titulo = n.titulo?.takeIf { it.isNotBlank() }
                    ?: contexto.getString(R.string.notifications_title),
                texto = texto,
                momento = momento(n.dataHoraCriacao),
                carregamentoId = n.carregamentoId,
                lida = n.lido || "servidor:${n.id}" in lidas,
                remotaId = n.id
            )
        }

        // Avisos locais só existem enquanto o servidor não foi contactado;
        // usam origens conservadoras (SISTEMA/DESCONHECIDO), nunca Administração.
        val temServidor = servidor.isNotEmpty()
        val cache = prefs.lerDashboard()
        if (!temServidor && cache != null) {
            val rejeicoes = LinkedHashMap<String, NotificacaoUi>()
            val guiasRejeitadas = mutableSetOf<String>()
            cache.recentes.orEmpty().forEach { recente ->
                if (recente.estado.equals("REJEITADO", ignoreCase = true)) {
                    val chave = "carregamento:${recente.id}"
                    val motivo = recente.motivoRejeicao?.trim().orEmpty()
                    rejeicoes[chave] = NotificacaoUi(
                        id = "rejeicao:${recente.id}:${motivo.hashCode()}",
                        origem = ORIGEM_DESCONHECIDO,
                        origemEtiqueta = contexto.getString(R.string.notif_origem_desconhecida),
                        titulo = contexto.getString(R.string.notif_rejected_title, recente.guiaNumero),
                        texto = motivo.ifBlank { contexto.getString(R.string.notif_rejected_fallback) },
                        momento = momento(recente.dataHoraFim ?: recente.dataHoraInicio),
                        carregamentoId = recente.id,
                        lida = false
                    )
                    guiasRejeitadas += recente.guiaNumero
                }
                val atraso = recente.atrasoMinutos.coerceAtLeast(0L)
                if ((recente.atrasado || atraso > 0L) &&
                    !recente.estado.equals("CONCLUIDO", ignoreCase = true)
                ) {
                    itens += NotificacaoUi(
                        id = "atraso:${recente.id}",
                        origem = ORIGEM_SISTEMA,
                        origemEtiqueta = contexto.getString(R.string.notif_sistema),
                        titulo = contexto.getString(R.string.notif_late_title, recente.guiaNumero),
                        texto = if (atraso > 0L) {
                            contexto.getString(R.string.notif_late_text, atraso)
                        } else {
                            contexto.getString(R.string.notif_late_text_generic)
                        },
                        momento = momento(recente.dataHoraInicio),
                        carregamentoId = recente.id,
                        lida = false
                    )
                }
            }
            fila.filter { it.estado.equals("REJEITADO", ignoreCase = true) }.forEach { item ->
                val chave = item.carregamentoId?.let { "carregamento:$it" }
                    ?: "guia:${item.guiaNumero}"
                if (!rejeicoes.containsKey(chave) && item.guiaNumero !in guiasRejeitadas) {
                    val erro = item.erro?.trim().orEmpty()
                    rejeicoes[chave] = NotificacaoUi(
                        id = "rejeicao:fila:${item.uuidLocal}:${erro.hashCode()}",
                        origem = ORIGEM_DESCONHECIDO,
                        origemEtiqueta = contexto.getString(R.string.notif_origem_desconhecida),
                        titulo = contexto.getString(R.string.notif_rejected_title, item.guiaNumero),
                        texto = erro.ifBlank { contexto.getString(R.string.notif_rejected_fallback) },
                        momento = item.sincronizadoEm ?: item.criadoEm,
                        carregamentoId = item.carregamentoId,
                        lida = false
                    )
                }
            }
            itens += rejeicoes.values

            // Falha de sincronização é gerada pelo sistema, não por uma pessoa.
            fila.filter { item ->
                item.erro?.isNotBlank() == true
                    && !item.estado.equals("SINCRONIZADO", ignoreCase = true)
                    && !item.estado.equals("REJEITADO", ignoreCase = true)
            }.forEach { item ->
                itens += NotificacaoUi(
                    id = "sync:${item.uuidLocal}:${item.erro.hashCode()}",
                    origem = ORIGEM_SISTEMA,
                    origemEtiqueta = contexto.getString(R.string.notif_sistema),
                    titulo = contexto.getString(R.string.notif_sync_title, item.guiaNumero),
                    texto = item.erro.orEmpty(),
                    momento = item.sincronizadoEm ?: item.criadoEm,
                    carregamentoId = item.carregamentoId,
                    lida = false
                )
            }
        }

        return itens
            .map { it.copy(lida = it.lida || it.id in lidas) }
            .sortedByDescending { it.momento }
    }

    fun etiquetaOrigem(contexto: Context, origem: String, perfil: String? = null): String =
        when (origem.uppercase()) {
            ORIGEM_SISTEMA -> contexto.getString(R.string.notif_sistema)
            ORIGEM_OPERADOR -> contexto.getString(R.string.notif_operador)
            ORIGEM_ADMIN -> contexto.getString(R.string.notif_administracao)
            else -> contexto.getString(R.string.notif_origem_desconhecida)
        }

    private fun momento(iso: String?): Long = Formato.data(iso)?.time ?: 0L
}
