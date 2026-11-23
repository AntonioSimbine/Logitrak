package mz.co.logitrack.motorista.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.local.AppDb
import mz.co.logitrack.motorista.data.local.FilaResumo
import mz.co.logitrack.motorista.databinding.FragmentQueueBinding
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Formato
import mz.co.logitrack.motorista.util.Ligacao
import mz.co.logitrack.motorista.util.Prefs
import mz.co.logitrack.motorista.worker.ResultadoItem
import mz.co.logitrack.motorista.worker.SyncManager

class QueueFragment : Fragment(R.layout.fragment_queue) {
    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs
    private lateinit var adapter: QueueAdapter

    private var todos: List<FilaResumo> = emptyList()
    private var filtro = FILTRO_TODOS
    private var busca = ""
    private var ligado = true
    private var aSincronizar = false
    private var primeiraCarga = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentQueueBinding.bind(view)
        prefs = Prefs(requireContext())

        adapter = QueueAdapter(
            aoSincronizar = { sincronizarItem(it) },
            aoRepetir = { repetirItem(it) },
            aoAbrir = { abrirDetalhe(it) },
            aoRemover = { confirmarRemocao(it) }
        )
        binding.recyclerFila.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerFila.adapter = adapter
        binding.recyclerFila.isNestedScrollingEnabled = false

        binding.btnSync.setOnClickListener { sincronizarTudo() }
        binding.tabTodos.setOnClickListener { selecionarFiltro(FILTRO_TODOS) }
        binding.tabEnviados.setOnClickListener { selecionarFiltro("SINCRONIZADO") }
        binding.tabPorEnviar.setOnClickListener { selecionarFiltro(FILTRO_POR_ENVIAR) }

        binding.etBusca.doAfterTextChanged { texto ->
            busca = texto?.toString().orEmpty().trim()
            _binding?.btnLimparBusca?.visibility =
                if (busca.isBlank()) View.GONE else View.VISIBLE
            atualizarLista()
        }
        binding.btnLimparBusca.setOnClickListener { binding.etBusca.setText("") }

        selecionarFiltro(FILTRO_TODOS)
        observarLigacao()
        observarFila()

        Animacoes.cascata(requireView() as ViewGroup, atraso = 50L, intervalo = 60L)
        Animacoes.pressionar(
            binding.btnSync,
            binding.tabTodos,
            binding.tabEnviados,
            binding.tabPorEnviar
        )
    }

    private fun observarLigacao() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Ligacao.estado.collectLatest {
                    ligado = it
                    atualizarBotaoSync()
                }
            }
        }
    }

    private fun observarFila() {
        if (prefs.userId <= 0L) {
            primeiraCarga = false
            atualizarTudo()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDb.get(requireContext()).filaDao().resumos(prefs.userId).collectLatest { lista ->
                    todos = lista
                    primeiraCarga = false
                    atualizarTudo()
                }
            }
        }
    }

    private fun selecionarFiltro(novo: String) {
        filtro = novo
        val b = _binding ?: return
        estilizarAba(b.tabTodos, b.tvTabTodos, b.badgeTodos, novo == FILTRO_TODOS)
        estilizarAba(b.tabEnviados, b.tvTabEnviados, b.badgeEnviados, novo == "SINCRONIZADO")
        estilizarAba(b.tabPorEnviar, b.tvTabPorEnviar, b.badgePorEnviar, novo == FILTRO_POR_ENVIAR)
        atualizarLista()
    }

    private fun estilizarAba(
        container: LinearLayout,
        texto: TextView,
        badge: TextView,
        ativo: Boolean
    ) {
        container.setBackgroundResource(if (ativo) R.drawable.bg_tab_selected else 0)
        container.elevation = if (ativo) 2f else 0f
        texto.setTextColor(cor(if (ativo) R.color.white else R.color.muted))
        badge.setBackgroundResource(
            if (ativo) R.drawable.bg_badge_on_blue else R.drawable.bg_badge_soft
        )
        badge.setTextColor(cor(if (ativo) R.color.white else R.color.blue_dark))
    }

    private fun atualizarTudo() {
        atualizarContadores()
        atualizarResumo()
        atualizarLista()
    }

    private fun atualizarContadores() {
        val b = _binding ?: return
        b.badgeTodos.text = todos.size.toString()
        b.badgeEnviados.text = todos.count { it.estado == "SINCRONIZADO" }.toString()
        b.badgePorEnviar.text = todos.count {
            it.estado == "PENDENTE" || it.estado == "REJEITADO"
        }.toString()
    }

    private fun atualizarResumo() {
        val b = _binding ?: return
        val pendentes = todos.count { it.estado == "PENDENTE" }
        val rejeitados = todos.count { it.estado == "REJEITADO" }
        val ultima = prefs.ultimaSync
        b.tvResumo.text = when {
            todos.isEmpty() -> getString(R.string.queue_summary_empty)
            pendentes + rejeitados == 0 -> if (ultima > 0L) {
                getString(R.string.queue_summary_synced_at, Formato.hora(ultima))
            } else {
                getString(R.string.queue_summary_synced)
            }
            else -> buildString {
                append(getString(R.string.queue_summary_pending, pendentes))
                if (rejeitados > 0) {
                    append(" · ")
                    append(
                        resources.getQuantityString(
                            R.plurals.queue_summary_rejected, rejeitados, rejeitados
                        )
                    )
                }
            }
        }
    }

    private fun atualizarLista() {
        val b = _binding ?: return
        val termo = busca.lowercase()
        val filtrados = todos.filter { item ->
            val porEstado = when (filtro) {
                FILTRO_TODOS -> true
                FILTRO_POR_ENVIAR -> item.estado == "PENDENTE" || item.estado == "REJEITADO"
                else -> item.estado == filtro
            }
            porEstado && (termo.isBlank() || correspondeBusca(item, termo))
        }
        adapter.submitList(filtrados)
        b.progressoFila.visibility = if (primeiraCarga) View.VISIBLE else View.GONE

        if (filtrados.isNotEmpty()) {
            b.recyclerFila.visibility = View.VISIBLE
            b.emptyFila.visibility = View.GONE
            return
        }
        b.recyclerFila.visibility = View.GONE
        b.emptyFila.visibility = View.VISIBLE
        when {
            busca.isNotBlank() -> {
                b.ivEmptyFila.setImageResource(R.drawable.ic_search)
                b.tvEmptyTitulo.setText(R.string.history_empty_search_title)
                b.tvEmptyTexto.setText(R.string.history_empty_search_text)
            }
            filtro == "SINCRONIZADO" -> {
                b.ivEmptyFila.setImageResource(R.drawable.ic_check)
                b.tvEmptyTitulo.setText(R.string.queue_empty_synced_title)
                b.tvEmptyTexto.setText(R.string.queue_empty_synced_text)
            }
            filtro == "REJEITADO" -> {
                b.ivEmptyFila.setImageResource(R.drawable.ic_alert)
                b.tvEmptyTitulo.setText(R.string.queue_empty_rejected_title)
                b.tvEmptyTexto.setText(R.string.queue_empty_rejected_text)
            }
            filtro == FILTRO_POR_ENVIAR -> {
                b.ivEmptyFila.setImageResource(R.drawable.ic_list)
                b.tvEmptyTitulo.setText(R.string.queue_empty_pending_title)
                b.tvEmptyTexto.setText(R.string.queue_empty_pending_text)
            }
            else -> {
                b.ivEmptyFila.setImageResource(R.drawable.ic_list)
                b.tvEmptyTitulo.setText(R.string.history_empty_title)
                b.tvEmptyTexto.setText(R.string.history_empty_text)
            }
        }
        b.ivEmptyFila.imageTintList = ColorStateList.valueOf(cor(R.color.blue))
    }

    private fun correspondeBusca(item: FilaResumo, termo: String): Boolean {
        val campos = listOf(
            item.guiaNumero,
            item.tipoCarga,
            item.fabricaNome.orEmpty(),
            item.veiculoMatricula.orEmpty(),
            Formato.dataHora(item.criadoEm),
            item.sincronizadoEm?.let { Formato.dataHora(it) }.orEmpty()
        )
        return campos.any { it.lowercase().contains(termo) }
    }

    private fun atualizarBotaoSync() {
        val b = _binding ?: return
        b.btnSync.isEnabled = !aSincronizar
        b.btnSync.alpha = when {
            aSincronizar -> 0.55f
            ligado -> 1f
            else -> 0.75f
        }
    }

    /* ------------------------- Ações ------------------------- */

    private fun sincronizarTudo() {
        if (aSincronizar) {
            avisar(R.string.queue_sync_already)
            return
        }
        if (!ligado) {
            avisar(R.string.sync_offline_toast)
            return
        }
        aSincronizar = true
        atualizarBotaoSync()
        val contexto = requireContext().applicationContext
        lifecycleScope.launch {
            val resultado = try {
                SyncManager.sincronizar(contexto)
            } finally {
                aSincronizar = false
                atualizarBotaoSync()
            }
            if (resultado.sessaoExpirada) return@launch
            when {
                resultado.semRede -> avisar(R.string.sync_offline_toast)
                resultado.nadaPorEnviar -> avisar(R.string.sync_all_done)
                resultado.rejeitados > 0 -> avisar(R.string.sync_rejected_warning)
                resultado.falhas > 0 -> avisar(R.string.sync_incomplete)
                resultado.enviados > 0 -> avisar(R.string.sync_finished)
                else -> avisar(R.string.sync_all_done)
            }
        }
    }

    private fun sincronizarItem(item: FilaResumo) {
        if (aSincronizar) {
            avisar(R.string.queue_sync_already)
            return
        }
        if (!ligado) {
            avisar(R.string.sync_offline_toast)
            return
        }
        val contexto = requireContext().applicationContext
        lifecycleScope.launch {
            val resultado = SyncManager.sincronizarUm(contexto, item.uuidLocal)
            val atualizado = AppDb.get(contexto).filaDao().porUuid(item.uuidLocal, prefs.userId)
            when (resultado) {
                ResultadoItem.ENVIADO ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.queue_sync_one_success, item.guiaNumero),
                        Toast.LENGTH_SHORT
                    ).show()
                ResultadoItem.REJEITADO ->
                    Toast.makeText(
                        requireContext(),
                        getString(
                            R.string.queue_sync_rejected,
                            atualizado?.erro?.takeIf { it.isNotBlank() }
                                ?: getString(R.string.queue_sync_failed)
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                ResultadoItem.SEM_REDE -> avisar(R.string.sync_offline_toast)
                ResultadoItem.FALHA -> avisar(R.string.queue_sync_failed)
                ResultadoItem.SESSAO -> Unit
            }
        }
    }

    private fun repetirItem(item: FilaResumo) {
        val contexto = requireContext().applicationContext
        lifecycleScope.launch {
            AppDb.get(contexto).filaDao().reporPendente(item.uuidLocal, prefs.userId)
            SyncManager.sincronizarUm(contexto, item.uuidLocal).let { resultado ->
                when (resultado) {
                    ResultadoItem.ENVIADO -> Toast.makeText(
                        requireContext(),
                        getString(R.string.queue_sync_one_success, item.guiaNumero),
                        Toast.LENGTH_SHORT
                    ).show()
                    ResultadoItem.REJEITADO -> {
                        val atualizado = AppDb.get(contexto).filaDao().porUuid(item.uuidLocal, prefs.userId)
                        Toast.makeText(
                            requireContext(),
                            getString(
                                R.string.queue_sync_rejected,
                                atualizado?.erro?.takeIf { it.isNotBlank() }
                                    ?: getString(R.string.queue_sync_failed)
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    ResultadoItem.SEM_REDE -> avisar(R.string.sync_offline_toast)
                    ResultadoItem.FALHA -> avisar(R.string.queue_sync_failed)
                    ResultadoItem.SESSAO -> Unit
                }
            }
        }
    }

    private fun abrirDetalhe(item: FilaResumo) {
        val id = item.carregamentoId ?: return
        startActivity(
            Intent(requireContext(), DetailActivity::class.java)
                .putExtra(DetailActivity.EXTRA_ID, id)
        )
    }

    private fun confirmarRemocao(item: FilaResumo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_remove_title)
            .setMessage(getString(R.string.confirm_remove_message, item.guiaNumero))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.action_remove) { _, _ ->
                val contexto = requireContext().applicationContext
                lifecycleScope.launch {
                    try {
                        AppDb.get(contexto).filaDao().remover(item.uuidLocal, prefs.userId)
                        avisar(R.string.remove_done)
                    } catch (e: Exception) {
                        avisar(R.string.queue_remove_failed)
                    }
                }
            }
            .show()
    }

    private fun avisar(texto: Int) {
        Toast.makeText(requireContext(), texto, Toast.LENGTH_SHORT).show()
    }

    private fun cor(recurso: Int): Int = ContextCompat.getColor(requireContext(), recurso)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val FILTRO_TODOS = "TODOS"
        private const val FILTRO_POR_ENVIAR = "POR_ENVIAR"
    }
}
