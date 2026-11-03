package mz.co.logitrack.motorista.ui

import android.content.Context
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.local.FilaResumo
import mz.co.logitrack.motorista.util.Formato
import mz.co.logitrack.motorista.util.Prefs

/** Notificação apresentada ao motorista, já pronta para a lista. */
data class NotificacaoUi(
    val id: String,
    val origem: String,
    val titulo: String,
    val texto: String,
    val momento: Long,
    val carregamentoId: Long?,
    val lida: Boolean
)

/**
 * Agrega os avisos do operador e da administração a partir dos dados já
 * guardados no dispositivo (dashboard em cache e fila local). Não faz
 * pedidos extra à rede, por isso funciona também offline.
 */
object Notificacoes {
    const val ORIGEM_OPERADOR = "OPERADOR"
    const val ORIGEM_ADMIN = "ADMINISTRACAO"
    const val ORIGEM_SISTEMA = "SISTEMA"

    fun listar(
        contexto: Context,
        prefs: Prefs,
        fila: List<FilaResumo>
    ): List<NotificacaoUi> {
        val lidas = prefs.notificacoesLidas()
        val itens = mutableListOf<Item>()
        val rejeicoes = LinkedHashMap<String, Item>()
        val guiasRejeitadas = mutableSetOf<String>()

        prefs.lerDashboard()?.recentes.orEmpty().forEach { recente ->
            val motivo = recente.motivoRejeicao?.trim().orEmpty()
            if (recente.estado.equals("REJEITADO", ignoreCase = true)) {
                rejeicoes["carregamento:${recente.id}"] = Item(
                    id = "rejeicao:${recente.id}:${motivo.hashCode()}",
                    origem = ORIGEM_OPERADOR,
                    titulo = contexto.getString(R.string.notif_rejected_title, recente.guiaNumero),
                    texto = motivo.ifBlank { contexto.getString(R.string.notif_rejected_fallback) },
                    momento = momento(recente.dataHoraFim ?: recente.dataHoraInicio),
                    carregamentoId = recente.id
                )
                guiasRejeitadas += recente.guiaNumero
            }

            val atraso = recente.atrasoMinutos.coerceAtLeast(0L)
            if ((recente.atrasado || atraso > 0L) &&
                !recente.estado.equals("CONCLUIDO", ignoreCase = true)
            ) {
                itens += Item(
                    id = "atraso:${recente.id}",
                    origem = ORIGEM_ADMIN,
                    titulo = contexto.getString(R.string.notif_late_title, recente.guiaNumero),
                    texto = if (atraso > 0L) {
                        contexto.getString(R.string.notif_late_text, atraso)
                    } else {
                        contexto.getString(R.string.notif_late_text_generic)
                    },
                    momento = momento(recente.dataHoraInicio),
                    carregamentoId = recente.id
                )
            }
        }

        fila.filter { it.estado.equals("REJEITADO", ignoreCase = true) }.forEach { item ->
            val chave = item.carregamentoId?.let { "carregamento:$it" }
                ?: "guia:${item.guiaNumero}"
            if (!rejeicoes.containsKey(chave) && item.guiaNumero !in guiasRejeitadas) {
                val erro = item.erro?.trim().orEmpty()
                rejeicoes[chave] = Item(
                    id = "rejeicao:fila:${item.uuidLocal}:${erro.hashCode()}",
                    origem = ORIGEM_OPERADOR,
                    titulo = contexto.getString(R.string.notif_rejected_title, item.guiaNumero),
                    texto = erro.ifBlank { contexto.getString(R.string.notif_rejected_fallback) },
                    momento = item.sincronizadoEm ?: item.criadoEm,
                    carregamentoId = item.carregamentoId
                )
            }
        }

        itens += rejeicoes.values
        return itens
            .sortedByDescending { it.momento }
            .map {
                NotificacaoUi(
                    id = it.id,
                    origem = it.origem,
                    titulo = it.titulo,
                    texto = it.texto,
                    momento = it.momento,
                    carregamentoId = it.carregamentoId,
                    lida = it.id in lidas
                )
            }
    }

    private fun momento(iso: String?): Long = Formato.data(iso)?.time ?: 0L

    private data class Item(
        val id: String,
        val origem: String,
        val titulo: String,
        val texto: String,
        val momento: Long,
        val carregamentoId: Long?
    )
}
