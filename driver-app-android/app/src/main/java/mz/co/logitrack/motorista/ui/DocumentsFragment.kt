package mz.co.logitrack.motorista.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.local.AppDb
import mz.co.logitrack.motorista.data.local.DocumentoResumo
import mz.co.logitrack.motorista.databinding.FragmentDocumentsBinding
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Formato
import mz.co.logitrack.motorista.util.Imagem
import mz.co.logitrack.motorista.util.Prefs

/** Galeria das fotografias de guias guardadas localmente. */
class DocumentsFragment : Fragment(R.layout.fragment_documents) {
    private var _binding: FragmentDocumentsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs
    private lateinit var adapter: DocumentoAdapter
    private var primeiraCarga = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDocumentsBinding.bind(view)
        prefs = Prefs(requireContext())

        adapter = DocumentoAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            carregarMiniatura = ::carregarMiniatura,
            aoAbrir = ::abrir
        )
        binding.recyclerDocumentos.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerDocumentos.adapter = adapter

        binding.btnSync.setOnClickListener {
            startActivity(Intent(requireContext(), SyncActivity::class.java))
        }
        binding.btnNovoDocumento.setOnClickListener {
            findNavController().navigate(R.id.novoFragment)
        }

        observarDocumentos()

        Animacoes.cascata(requireView() as android.view.ViewGroup, atraso = 50L, intervalo = 70L)
        Animacoes.pressionar(binding.btnSync, binding.btnNovoDocumento)
    }

    private fun observarDocumentos() {
        val id = prefs.userId
        if (id <= 0L) {
            primeiraCarga = false
            atualizarEcrã(emptyList())
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDb.get(requireContext()).filaDao().documentos(id).collectLatest { lista ->
                    primeiraCarga = false
                    atualizarEcrã(lista)
                }
            }
        }
    }

    private fun atualizarEcrã(lista: List<DocumentoResumo>) {
        val b = _binding ?: return
        adapter.submitList(lista)
        b.progressoDocumentos.visibility = if (primeiraCarga) View.VISIBLE else View.GONE
        b.emptyDocumentos.visibility =
            if (lista.isEmpty() && !primeiraCarga) View.VISIBLE else View.GONE
        b.recyclerDocumentos.visibility =
            if (lista.isEmpty() && !primeiraCarga) View.GONE else View.VISIBLE
        b.tvContagem.text = resources.getQuantityString(
            R.plurals.documents_count,
            lista.size,
            lista.size
        )
    }

    private suspend fun carregarMiniatura(uuid: String): Bitmap? {
        val contexto = requireContext().applicationContext
        return withContext(Dispatchers.IO) {
            val foto = AppDb.get(contexto).filaDao().porUuid(uuid)?.fotoBase64
            Imagem.deBase64Miniatura(foto)
        }
    }

    private fun abrir(item: DocumentoResumo) {
        val id = item.carregamentoId
        if (id != null) {
            startActivity(
                Intent(requireContext(), DetailActivity::class.java)
                    .putExtra(DetailActivity.EXTRA_ID, id)
            )
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = carregarMiniatura(item.uuidLocal)
            if (bitmap == null) {
                Toast.makeText(
                    requireContext(),
                    R.string.documents_photo_missing,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val margem = (18 * resources.displayMetrics.density).toInt()
            val imagem = ImageView(requireContext()).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val contentor = FrameLayout(requireContext()).apply {
                setPadding(margem, margem, margem, margem)
                addView(
                    imagem,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.guiaNumero)
                .setMessage(
                    getString(
                        R.string.documents_local_detail,
                        Formato.dataHora(item.criadoEm),
                        item.tipoCarga
                    )
                )
                .setView(contentor)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    override fun onDestroyView() {
        binding.recyclerDocumentos.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
