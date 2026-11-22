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
import mz.co.logitrack.motorista.databinding.ItemNotificacaoBinding
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Formato

class NotificacaoAdapter(
    private val aoAbrir: (NotificacaoUi) -> Unit
) : ListAdapter<NotificacaoUi, NotificacaoAdapter.VH>(Diff()) {

    class VH(val b: ItemNotificacaoBinding) : RecyclerView.ViewHolder(b.root)

    class Diff : DiffUtil.ItemCallback<NotificacaoUi>() {
        override fun areItemsTheSame(oldItem: NotificacaoUi, newItem: NotificacaoUi) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: NotificacaoUi, newItem: NotificacaoUi) =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemNotificacaoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.b
        val contexto = b.root.context

        b.root.setBackgroundResource(
            if (item.lida) R.drawable.bg_recent_item else R.drawable.bg_notif_unread
        )

        // A origem é a recebida do servidor: SISTEMA, OPERADOR, ADMINISTRADOR
        // ou DESCONHECIDO para registos antigos sem autoria gravada.
        val sistema = item.origem == Notificacoes.ORIGEM_SISTEMA
        val desconhecido = item.origem == Notificacoes.ORIGEM_DESCONHECIDO
        b.tvOrigem.text = item.origemEtiqueta
        b.tvOrigem.setBackgroundResource(
            if (sistema || desconhecido) R.drawable.bg_chip_grey else R.drawable.bg_chip_blue
        )
        b.tvOrigem.setTextColor(
            ContextCompat.getColor(
                contexto,
                if (sistema || desconhecido) R.color.chip_text else R.color.blue_dark
            )
        )

        b.tvTitulo.text = item.titulo
        b.tvTexto.text = item.texto
        b.tvTempo.text = Formato.relativo(item.momento)
        b.dotNaoLida.visibility = if (item.lida) View.GONE else View.VISIBLE
        b.tvAbrir.visibility = if (item.carregamentoId != null) View.VISIBLE else View.GONE

        b.ivIcone.setImageResource(
            if (sistema || desconhecido) R.drawable.ic_settings else R.drawable.ic_user
        )
        b.ivIcone.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                contexto,
                if (sistema || desconhecido) R.color.navy else R.color.blue
            )
        )

        b.root.setOnClickListener { aoAbrir(item) }
        Animacoes.revelarItem(b.root, item.id)
    }
}
