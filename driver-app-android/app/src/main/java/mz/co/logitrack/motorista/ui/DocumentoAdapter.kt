package mz.co.logitrack.motorista.ui

import android.graphics.Bitmap
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.local.DocumentoResumo
import mz.co.logitrack.motorista.databinding.ItemDocumentoBinding
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Formato

class DocumentoAdapter(
    private val scope: CoroutineScope,
    private val carregarMiniatura: suspend (String) -> Bitmap?,
    private val aoAbrir: (DocumentoResumo) -> Unit
) : ListAdapter<DocumentoResumo, DocumentoAdapter.VH>(Diff()) {

    private val cache = LruCache<String, Bitmap>(14 * 1024)
    private val aCarregar = HashSet<String>()

    class VH(val b: ItemDocumentoBinding) : RecyclerView.ViewHolder(b.root)

    class Diff : DiffUtil.ItemCallback<DocumentoResumo>() {
        override fun areItemsTheSame(oldItem: DocumentoResumo, newItem: DocumentoResumo) =
            oldItem.uuidLocal == newItem.uuidLocal

        override fun areContentsTheSame(oldItem: DocumentoResumo, newItem: DocumentoResumo) =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemDocumentoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.b
        val ctx = b.root.context

        b.tvGuia.text = item.guiaNumero
        b.tvFabrica.text = item.fabricaNome?.takeIf { it.isNotBlank() }
            ?: ctx.getString(R.string.factory_origin)
        b.tvData.text = Formato.dataHora(item.criadoEm)

        val estado = item.estado.uppercase()
        when (estado) {
            "SINCRONIZADO" -> {
                b.tvEstado.setBackgroundResource(R.drawable.bg_chip_green)
                b.tvEstado.setTextColor(ContextCompat.getColor(ctx, R.color.green_dark))
                b.tvEstado.text = ctx.getString(R.string.documents_status_synced)
            }
            "REJEITADO" -> {
                b.tvEstado.setBackgroundResource(R.drawable.bg_chip_red)
                b.tvEstado.setTextColor(ContextCompat.getColor(ctx, R.color.red))
                b.tvEstado.text = ctx.getString(R.string.documents_status_rejected)
            }
            else -> {
                b.tvEstado.setBackgroundResource(R.drawable.bg_chip_amber)
                b.tvEstado.setTextColor(ContextCompat.getColor(ctx, R.color.amber))
                b.tvEstado.text = ctx.getString(R.string.documents_status_pending)
            }
        }

        b.ivFoto.tag = item.uuidLocal
        b.ivFoto.setImageDrawable(null)
        b.ivPlaceholder.visibility = View.VISIBLE

        val emCache = cache.get(item.uuidLocal)
        if (emCache != null) {
            mostrar(b, emCache)
        } else if (aCarregar.add(item.uuidLocal)) {
            scope.launch {
                val bitmap = carregarMiniatura(item.uuidLocal)
                aCarregar.remove(item.uuidLocal)
                if (bitmap != null) {
                    cache.put(item.uuidLocal, bitmap)
                    if (b.ivFoto.tag == item.uuidLocal) mostrar(b, bitmap)
                }
            }
        }

        b.root.setOnClickListener { aoAbrir(item) }
        Animacoes.revelarItem(b.root, item.uuidLocal)
    }

    private fun mostrar(b: ItemDocumentoBinding, bitmap: Bitmap) {
        b.ivFoto.setImageBitmap(bitmap)
        b.ivPlaceholder.visibility = View.GONE
    }
}
