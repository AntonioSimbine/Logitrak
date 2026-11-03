package mz.co.logitrack.motorista.ui

import android.content.res.ColorStateList
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.local.FilaResumo
import mz.co.logitrack.motorista.databinding.ItemSyncBinding
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Formato

class SyncAdapter : ListAdapter<FilaResumo, SyncAdapter.VH>(Diff()) {

    class VH(val b: ItemSyncBinding) : RecyclerView.ViewHolder(b.root)

    class Diff : DiffUtil.ItemCallback<FilaResumo>() {
        override fun areItemsTheSame(oldItem: FilaResumo, newItem: FilaResumo) =
            oldItem.uuidLocal == newItem.uuidLocal

        override fun areContentsTheSame(oldItem: FilaResumo, newItem: FilaResumo) =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = android.view.LayoutInflater.from(parent.context)
        return VH(ItemSyncBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context
        val b = holder.b

        b.tvGuia.text = item.guiaNumero
        b.tvDetalhe.text = listOfNotNull(
            item.tipoCarga.takeIf { it.isNotBlank() },
            item.fabricaNome?.takeIf { it.isNotBlank() }
        ).joinToString(" · ").ifBlank { ctx.getString(R.string.not_available) }
        b.tvHora.text = Formato.dataHora(item.criadoEm)

        when (item.estado) {
            "REJEITADO" -> {
                b.tvEstado.setText(R.string.status_problem)
                b.tvEstado.setBackgroundResource(R.drawable.bg_pill_amber)
                b.tvEstado.setTextColor(ContextCompat.getColor(ctx, R.color.red))
                b.tvEstado.backgroundTintList = null
            }
            "SINCRONIZADO" -> {
                b.tvEstado.setText(R.string.status_sent)
                b.tvEstado.setBackgroundResource(R.drawable.bg_pill_green)
                b.tvEstado.setTextColor(ContextCompat.getColor(ctx, R.color.green_dark))
            }
            else -> {
                b.tvEstado.setText(R.string.tab_to_send)
                b.tvEstado.setBackgroundResource(R.drawable.bg_pill_amber)
                b.tvEstado.setTextColor(ContextCompat.getColor(ctx, R.color.amber))
            }
        }
        b.tvEstado.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                ctx,
                when (item.estado) {
                    "REJEITADO" -> R.color.red_soft
                    "SINCRONIZADO" -> R.color.green_soft
                    else -> R.color.amber_soft
                }
            )
        )
        Animacoes.revelarItem(b.root, item.uuidLocal)
    }
}
