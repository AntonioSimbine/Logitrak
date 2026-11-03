package mz.co.logitrack.motorista.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.local.FilaResumo
import mz.co.logitrack.motorista.databinding.ItemFilaBinding
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Formato

class QueueAdapter(
    private val aoSincronizar: (FilaResumo) -> Unit,
    private val aoRepetir: (FilaResumo) -> Unit,
    private val aoAbrir: (FilaResumo) -> Unit,
    private val aoRemover: (FilaResumo) -> Unit
) : ListAdapter<FilaResumo, QueueAdapter.VH>(Diff()) {

    class VH(val b: ItemFilaBinding) : RecyclerView.ViewHolder(b.root)

    class Diff : DiffUtil.ItemCallback<FilaResumo>() {
        override fun areItemsTheSame(oldItem: FilaResumo, newItem: FilaResumo) =
            oldItem.uuidLocal == newItem.uuidLocal

        override fun areContentsTheSame(oldItem: FilaResumo, newItem: FilaResumo) =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFilaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.b
        val ctx = b.root.context
        val estado = item.estado.uppercase()

        b.root.setBackgroundResource(
            when (estado) {
                "SINCRONIZADO" -> R.drawable.bg_queue_item_synced
                "REJEITADO" -> R.drawable.bg_queue_item_rejected
                else -> R.drawable.bg_queue_item_pending
            }
        )

        val cor = when (estado) {
            "SINCRONIZADO" -> R.color.green
            "REJEITADO" -> R.color.red
            else -> R.color.amber
        }
        val corEstado = ContextCompat.getColor(ctx, cor)
        b.dotEstado.backgroundTintList = ColorStateList.valueOf(corEstado)
        b.tvEstado.setTextColor(corEstado)
        b.tvEstado.text = when (estado) {
            "SINCRONIZADO" -> ctx.getString(R.string.status_sent)
            "REJEITADO" -> ctx.getString(R.string.status_problem)
            else -> ctx.getString(R.string.tab_to_send)
        }

        b.tvHora.text = if (estado == "SINCRONIZADO" && item.sincronizadoEm != null) {
            ctx.getString(R.string.sent_at, Formato.dataHora(item.sincronizadoEm))
        } else {
            Formato.dataHora(item.criadoEm)
        }

        b.tvGuia.text = item.guiaNumero
        b.tvTipo.text = "${item.tipoCarga} · ${Formato.numero(item.quantidadeToneladas, 1)} t"
        b.tvFabrica.text = item.fabricaNome?.takeIf { it.isNotBlank() }
            ?: ctx.getString(R.string.factory_origin)
        b.tvVeiculo.text = item.veiculoMatricula?.takeIf { it.isNotBlank() } ?: "—"
        b.tvCapturado.text = ctx.getString(R.string.captured_at, Formato.dataHora(item.criadoEm))

        val erro = item.erro?.takeIf { it.isNotBlank() }
        b.tvErro.visibility = if (erro != null) View.VISIBLE else View.GONE
        b.tvErro.text = erro

        b.btnAcaoSync.visibility = if (estado == "PENDENTE") View.VISIBLE else View.GONE
        b.btnAcaoRetry.visibility = if (estado == "REJEITADO") View.VISIBLE else View.GONE
        b.btnAcaoAbrir.visibility =
            if (estado == "SINCRONIZADO" && item.carregamentoId != null) View.VISIBLE else View.GONE
        b.ivSeta.visibility =
            if (estado == "SINCRONIZADO" && item.carregamentoId != null) View.VISIBLE else View.INVISIBLE

        b.wrapAcoes.visibility = View.VISIBLE

        b.btnAcaoSync.setOnClickListener { aoSincronizar(item) }
        b.btnAcaoRetry.setOnClickListener { aoRepetir(item) }
        b.btnAcaoAbrir.setOnClickListener { aoAbrir(item) }
        b.btnRemover.setOnClickListener { aoRemover(item) }

        Animacoes.revelarItem(b.root, item.uuidLocal)
        Animacoes.pressionar(b.btnAcaoSync, b.btnAcaoRetry, b.btnAcaoAbrir, b.btnRemover)
    }
}
