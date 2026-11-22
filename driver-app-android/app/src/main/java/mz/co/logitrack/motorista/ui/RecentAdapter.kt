package mz.co.logitrack.motorista.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.dto.RecenteDto
import mz.co.logitrack.motorista.databinding.ItemRecenteBinding
import mz.co.logitrack.motorista.util.Formato

class RecentAdapter(
    private val aoAbrir: (RecenteDto) -> Unit
) : ListAdapter<RecenteDto, RecentAdapter.VH>(Diff()) {

    class VH(val b: ItemRecenteBinding) : RecyclerView.ViewHolder(b.root)

    class Diff : DiffUtil.ItemCallback<RecenteDto>() {
        override fun areItemsTheSame(oldItem: RecenteDto, newItem: RecenteDto) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: RecenteDto, newItem: RecenteDto) =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemRecenteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.b
        val contexto = b.root.context
        b.tvGuia.text = item.guiaNumero
        b.tvEstado.text = EstadoUi.etiqueta(contexto, item.estado, item.estadoEtiqueta)
        val chip = EstadoUi.chipClaro(contexto, item.estado)
        b.tvEstado.setBackgroundResource(chip.fundo)
        b.tvEstado.setTextColor(chip.corTexto)
        b.tvTipo.text = "${item.tipoCarga} · ${Formato.numero(item.quantidadeToneladas, 1)} t"
        b.tvFabrica.text = item.fabricaNome?.takeIf { it.isNotBlank() }
            ?: contexto.getString(R.string.factory_origin)
        b.tvRodape.text = Formato.relativo(item.dataHoraInicio)
        b.tvDetalhe.setTextColor(ContextCompat.getColor(contexto, R.color.blue))
        b.root.setOnClickListener { aoAbrir(item) }
    }
}
