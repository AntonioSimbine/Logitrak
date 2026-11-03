package mz.co.logitrack.motorista.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.dto.DashboardDto
import mz.co.logitrack.motorista.data.local.AppDb
import mz.co.logitrack.motorista.data.local.FilaResumo
import mz.co.logitrack.motorista.databinding.FragmentHomeBinding
import mz.co.logitrack.motorista.network.RetrofitClient
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Formato
import mz.co.logitrack.motorista.util.Ligacao
import mz.co.logitrack.motorista.util.Prefs
import mz.co.logitrack.motorista.util.Sessao
import mz.co.logitrack.motorista.worker.SyncManager

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs
    private lateinit var recentesAdapter: RecentAdapter

    private var resumos: List<FilaResumo> = emptyList()
    private var ligado = true
    private var temCache = false
    private var ligacaoAnterior: Boolean? = null
    private var ultimoBadge = -1
    private var ultimoTotalFila = -1
    private var aRespirar = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)
        prefs = Prefs(requireContext())

        val nomeMotorista = prefs.userName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.driver_fallback)
        binding.tvNome.text = getString(R.string.home_greeting_name, nomeMotorista)
        binding.tvAvatar.text = Formato.iniciais(prefs.userName)

        recentesAdapter = RecentAdapter { recente ->
            startActivity(
                Intent(requireContext(), DetailActivity::class.java)
                    .putExtra(DetailActivity.EXTRA_ID, recente.id)
            )
        }
        binding.recyclerRecentes.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.recyclerRecentes.adapter = recentesAdapter
        binding.recyclerRecentes.isNestedScrollingEnabled = false

        binding.btnNovo.setOnClickListener {
            findNavController().navigate(R.id.novoFragment)
        }
        binding.cardCarregamentos.setOnClickListener {
            findNavController().navigate(R.id.queueFragment)
        }
        binding.cardDocumentos.setOnClickListener {
            findNavController().navigate(R.id.documentsFragment)
        }
        binding.cardSync.setOnClickListener {
            startActivity(Intent(requireContext(), SyncActivity::class.java))
        }
        binding.cardFila.setOnClickListener {
            startActivity(Intent(requireContext(), SyncActivity::class.java))
        }
        binding.btnNotificacoes.setOnClickListener {
            startActivity(Intent(requireContext(), NotificacoesActivity::class.java))
        }
        binding.tvAvatar.setOnClickListener {
            findNavController().navigate(R.id.perfilFragment)
        }

        observarLigacao()
        observarFila()
        observarSincronizacao()
        carregarDashboard()
        prepararAnimacoes()
        prepararBarra()
    }

    private fun prepararAnimacoes() {
        Animacoes.cascata(binding.homeBody, atraso = 120L, intervalo = 55L)
        Animacoes.zoomLento(binding.ivHeroCamiao)
        Animacoes.entrada(
            binding.btnNotificacoes,
            atraso = 40L,
            intervalo = 60L,
            deslocamentoY = 8f
        )
        Animacoes.pressionar(
            binding.btnNovo,
            binding.cardCarregamentos,
            binding.cardDocumentos,
            binding.cardSync,
            binding.cardFila,
            binding.btnNotificacoes
        )
    }

    /**
     * Cabeçalho recolhível: ao deslizar, a barra fixa ganha o azul do sistema
     * enquanto a informação sobre a fotografia desaparece.
     */
    private fun prepararBarra() {
        val b = _binding ?: return
        b.scrollHome.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            aplicarBarra(scrollY)
        })
        b.scrollHome.post {
            if (_binding != null) aplicarBarra(b.scrollHome.scrollY)
        }
    }

    private fun aplicarBarra(scrollY: Int) {
        val b = _binding ?: return
        val densidade = resources.displayMetrics.density
        val limite = 92f * densidade
        val progresso = (scrollY / limite).coerceIn(0f, 1f)

        b.homeBarFundo.alpha = progresso
        b.homeHeroVeu.alpha = progresso
        b.homeTopBar.elevation = progresso * 6f * densidade

        val saida = (progresso * 1.35f).coerceAtMost(1f)
        b.homeHeroInfo.alpha = 1f - saida
        b.homeHeroInfo.translationY = -20f * densidade * saida

        // Ao recolher, o lado esquerdo revela o logótipo e o título da secção.
        // As iniciais desvanecem; ficam INVISIBLE (não GONE) para o sino não saltar.
        b.homeCollapsedLeft.alpha = progresso
        b.homeCollapsedLeft.translationX = -10f * densidade * (1f - progresso)
        b.tvAvatar.alpha = 1f - progresso
        val recolhido = progresso >= 0.98f
        b.tvAvatar.visibility = if (recolhido) View.INVISIBLE else View.VISIBLE
    }

    /* ------------------------- Estado da ligação ------------------------- */

    private fun observarLigacao() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Ligacao.estado.collectLatest { atual ->
                    ligado = atual
                    atualizarLinhaSync()
                    if (ligacaoAnterior == false && atual) {
                        carregarDashboard()
                    }
                    ligacaoAnterior = atual
                }
            }
        }
    }

    private fun observarFila() {
        if (prefs.userId <= 0L) return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDb.get(requireContext()).filaDao().resumos(prefs.userId).collectLatest { lista ->
                    resumos = lista
                    atualizarResumoFila()
                    atualizarLinhaSync()
                }
            }
        }
    }

    private fun observarSincronizacao() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SyncManager.eventos.collectLatest { resultado ->
                    if (resultado.enviados > 0) {
                        carregarDashboard()
                    }
                }
            }
        }
    }

    private fun atualizarResumoFila() {
        val b = _binding ?: return
        val pendentes = resumos.count { it.estado == "PENDENTE" }
        val rejeitados = resumos.count { it.estado == "REJEITADO" }
        val sincronizados = resumos.count { it.estado == "SINCRONIZADO" }
        val total = pendentes + rejeitados
        val ultima = prefs.ultimaSync

        b.tvPendentesHome.text = total.toString()
        b.tvPendentesHome.setTextColor(
            cor(if (total > 0) R.color.orange_dark else R.color.muted)
        )
        b.tvPendentesHome.setBackgroundResource(
            if (total > 0) R.drawable.bg_badge_orange else R.drawable.bg_badge_grey
        )
        b.tvSyncSub.text = if (total > 0) {
            getString(R.string.home_sync_pending, total)
        } else {
            getString(R.string.home_grid_sync_sub)
        }
        b.tvSyncSub.setTextColor(cor(if (total > 0) R.color.amber else R.color.muted))

        when {
            pendentes > 0 -> {
                b.tvFilaTitulo.text = if (pendentes == 1) {
                    getString(R.string.queue_one_pending)
                } else {
                    getString(R.string.queue_many_pending, pendentes)
                }
                b.tvFilaTexto.setText(R.string.queue_pending_text)
                b.wrapFilaIcone.setBackgroundResource(R.drawable.bg_queue_icon_amber)
                b.ivFilaIcone.setImageResource(R.drawable.ic_cloud_off)
                b.ivFilaIcone.imageTintList = ColorStateList.valueOf(cor(R.color.amber))
            }
            rejeitados > 0 -> {
                b.tvFilaTitulo.text = getString(R.string.queue_rejected_title, rejeitados)
                b.tvFilaTexto.setText(R.string.queue_rejected_text)
                b.wrapFilaIcone.setBackgroundResource(R.drawable.bg_queue_icon_red)
                b.ivFilaIcone.setImageResource(R.drawable.ic_alert)
                b.ivFilaIcone.imageTintList = ColorStateList.valueOf(cor(R.color.red))
            }
            sincronizados > 0 || ultima > 0L -> {
                b.tvFilaTitulo.setText(R.string.queue_synced_title)
                b.tvFilaTexto.text = if (ultima > 0L) {
                    getString(R.string.queue_synced_at, Formato.hora(ultima))
                } else {
                    getString(R.string.queue_synced_text)
                }
                b.wrapFilaIcone.setBackgroundResource(R.drawable.bg_queue_icon_green)
                b.ivFilaIcone.setImageResource(R.drawable.ic_check)
                b.ivFilaIcone.imageTintList = ColorStateList.valueOf(cor(R.color.green))
            }
            else -> {
                b.tvFilaTitulo.setText(R.string.queue_zero_title)
                b.tvFilaTexto.setText(R.string.queue_zero_text)
                b.wrapFilaIcone.setBackgroundResource(R.drawable.bg_queue_icon_amber)
                b.ivFilaIcone.setImageResource(R.drawable.ic_cloud_off)
                b.ivFilaIcone.imageTintList = ColorStateList.valueOf(cor(R.color.amber))
            }
        }
        b.cardFila.contentDescription = b.tvFilaTitulo.text
        if (total == 0) {
            b.cardFila.alpha = 1f
        }
        if (total != ultimoTotalFila) {
            if (total > 0) Animacoes.pop(b.tvPendentesHome, 0.78f)
            ultimoTotalFila = total
        }
        atualizarSino()
    }

    private fun atualizarLinhaSync() {
        val b = _binding ?: return
        val pendentes = resumos.count { it.estado == "PENDENTE" }
        val rejeitados = resumos.count { it.estado == "REJEITADO" }
        val porSincronizar = pendentes + rejeitados
        b.dotSync.backgroundTintList = ColorStateList.valueOf(
            cor(if (ligado) R.color.green else R.color.offline_yellow)
        )
        if (!ligado && !aRespirar) {
            Animacoes.respirar(b.dotSync)
            aRespirar = true
        } else if (ligado && aRespirar) {
            Animacoes.parar(b.dotSync)
            b.dotSync.alpha = 1f
            aRespirar = false
        }
        b.tvSync.text = when {
            !ligado && porSincronizar > 0 ->
                getString(R.string.sync_line_offline_pending, porSincronizar)
            !ligado -> getString(R.string.sync_line_offline)
            pendentes == 1 -> getString(R.string.sync_line_pending_one)
            pendentes > 1 -> getString(R.string.sync_line_pending_many, pendentes)
            rejeitados == 1 -> getString(R.string.sync_line_rejected_one)
            rejeitados > 1 -> getString(R.string.sync_line_rejected_many, rejeitados)
            prefs.ultimaSync > 0L ->
                getString(R.string.sync_line_last, Formato.hora(prefs.ultimaSync))
            else -> getString(R.string.sync_line_online)
        }
    }

    /* ------------------------- Painel ------------------------- */

    private fun carregarDashboard() {
        val b = _binding ?: return
        val cache = prefs.lerDashboard()
        if (cache != null) {
            temCache = true
            renderDashboard(cache)
        } else if (!temCache) {
            mostrarCarregando()
        }

        if (!ligado) {
            if (cache == null && !temCache) renderOffline()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = RetrofitClient.getApi(requireContext()).dashboard()
                val corpo = resp.body()
                if (resp.isSuccessful && corpo != null) {
                    prefs.guardarDashboard(corpo)
                    temCache = true
                    if (_binding != null) renderDashboard(corpo)
                } else if (resp.code() == 401) {
                    Sessao.sinalizarExpiracao()
                } else if (cache == null && !temCache) {
                    renderErro()
                }
            } catch (e: Exception) {
                if (cache == null && !temCache) renderErro()
            }
        }
    }

    private fun renderDashboard(dados: DashboardDto) {
        val b = _binding ?: return
        b.tvTotal.text = getString(R.string.recent_total, dados.total)

        val recentes = dados.recentes.orEmpty()
        recentesAdapter.submitList(recentes)
        b.progressoRecentes.visibility = View.GONE
        if (recentes.isEmpty()) {
            b.recyclerRecentes.visibility = View.GONE
            b.emptyRecentes.visibility = View.VISIBLE
            b.ivEmptyIcone.setImageResource(R.drawable.ic_truck)
            b.ivEmptyIcone.imageTintList = ColorStateList.valueOf(cor(R.color.empty_icon))
            b.tvEmptyTitulo.setText(R.string.recent_empty_title)
            b.tvEmptyTexto.setText(R.string.recent_empty_text)
        } else {
            b.recyclerRecentes.visibility = View.VISIBLE
            b.emptyRecentes.visibility = View.GONE
        }
        atualizarSino()
    }

    private fun renderOffline() {
        val b = _binding ?: return
        recentesAdapter.submitList(emptyList())
        b.progressoRecentes.visibility = View.GONE
        b.recyclerRecentes.visibility = View.GONE
        b.emptyRecentes.visibility = View.VISIBLE
        b.ivEmptyIcone.setImageResource(R.drawable.ic_cloud_off)
        b.ivEmptyIcone.imageTintList = ColorStateList.valueOf(cor(R.color.empty_icon))
        b.tvEmptyTitulo.setText(R.string.recent_offline_title)
        b.tvEmptyTexto.setText(R.string.recent_offline_text)
        b.tvTotal.setText(R.string.recent_offline_total)
    }

    private fun renderErro() {
        val b = _binding ?: return
        recentesAdapter.submitList(emptyList())
        b.progressoRecentes.visibility = View.GONE
        b.recyclerRecentes.visibility = View.GONE
        b.emptyRecentes.visibility = View.VISIBLE
        b.ivEmptyIcone.setImageResource(R.drawable.ic_cloud_off)
        b.ivEmptyIcone.imageTintList = ColorStateList.valueOf(cor(R.color.empty_icon))
        b.tvEmptyTitulo.setText(R.string.recent_error_title)
        b.tvEmptyTexto.setText(R.string.recent_error_text)
        b.tvTotal.setText(R.string.not_available)
    }

    private fun mostrarCarregando() {
        val b = _binding ?: return
        recentesAdapter.submitList(emptyList())
        b.recyclerRecentes.visibility = View.GONE
        b.emptyRecentes.visibility = View.VISIBLE
        b.ivEmptyIcone.setImageResource(R.drawable.ic_truck)
        b.ivEmptyIcone.imageTintList = ColorStateList.valueOf(cor(R.color.empty_icon))
        b.tvEmptyTitulo.setText(R.string.recent_loading)
        b.tvEmptyTexto.setText(R.string.recent_loading)
        b.progressoRecentes.visibility = View.VISIBLE
        b.tvTotal.setText(R.string.not_available)
    }

    /* ------------------------- Ações ------------------------- */

    private fun atualizarSino() {
        val b = _binding ?: return
        val porLer = Notificacoes.listar(requireContext(), prefs, resumos).count { !it.lida }
        b.tvSinoBadge.visibility = if (porLer > 0) View.VISIBLE else View.GONE
        b.tvSinoBadge.text = if (porLer > 9) "9+" else porLer.toString()
        if (porLer > 0 && porLer != ultimoBadge) {
            Animacoes.pop(b.tvSinoBadge, 0.58f)
        }
        ultimoBadge = porLer
        b.btnNotificacoes.contentDescription = if (porLer > 0) {
            getString(R.string.home_notifications_unread, porLer)
        } else {
            getString(R.string.home_notifications)
        }
    }

    private fun cor(recurso: Int): Int = ContextCompat.getColor(requireContext(), recurso)

    override fun onResume() {
        super.onResume()
        atualizarSino()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.scrollHome.setOnScrollChangeListener(null as NestedScrollView.OnScrollChangeListener?)
        Animacoes.parar(binding.ivHeroCamiao)
        Animacoes.parar(binding.dotSync)
        aRespirar = false
        _binding = null
    }
}
