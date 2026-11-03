package mz.co.logitrack.motorista.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.local.AppDb
import mz.co.logitrack.motorista.data.local.FilaResumo
import mz.co.logitrack.motorista.databinding.ActivitySyncBinding
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Formato
import mz.co.logitrack.motorista.util.Ligacao
import mz.co.logitrack.motorista.util.Prefs
import mz.co.logitrack.motorista.worker.SyncManager

class SyncActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySyncBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: SyncAdapter

    private var todos: List<FilaResumo> = emptyList()
    private var ligado = true
    private var aSincronizar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)
        ligado = Ligacao.estado.value

        adapter = SyncAdapter()
        binding.recyclerSync.layoutManager = LinearLayoutManager(this)
        binding.recyclerSync.adapter = adapter
        binding.recyclerSync.isNestedScrollingEnabled = false

        binding.btnVoltar.setOnClickListener { finish() }
        binding.btnEnviar.setOnClickListener { sincronizar() }

        observarLigacao()
        observarFila()
        atualizarTudo()
        prepararAnimacoes()
    }

    private fun prepararAnimacoes() {
        val raiz = binding.root
        (raiz.getChildAt(0) as? View)?.let {
            Animacoes.surgir(it, atraso = 40L, deslocamentoY = 14f)
        }
        Animacoes.cascata(binding.syncContent, atraso = 160L, intervalo = 70L)
        (raiz.getChildAt(raiz.childCount - 1) as? View)?.let {
            Animacoes.surgir(it, atraso = 300L, deslocamentoY = 24f)
        }
        Animacoes.pressionar(binding.btnVoltar, binding.btnEnviar)
    }

    private fun observarLigacao() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Ligacao.estado.collectLatest {
                    ligado = it
                    atualizarTudo()
                }
            }
        }
    }

    private fun observarFila() {
        val id = prefs.userId
        if (id <= 0L) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDb.get(this@SyncActivity).filaDao().resumos(id).collectLatest { lista ->
                    todos = lista
                    atualizarTudo()
                }
            }
        }
    }

    private fun atualizarTudo() {
        val pendentes = todos.count { it.estado == "PENDENTE" }
        val rejeitados = todos.count { it.estado == "REJEITADO" }
        val porEnviar = pendentes + rejeitados

        binding.tvPendentes.text = porEnviar.toString()
        binding.tvPendentes.setTextColor(
            ContextCompat.getColor(
                this,
                when {
                    porEnviar == 0 -> R.color.green_dark
                    ligado -> R.color.amber
                    else -> R.color.muted
                }
            )
        )

        binding.tvOnlinePill.text = if (ligado) {
            getString(R.string.sync_online_label)
        } else {
            getString(R.string.sync_no_internet)
        }
        binding.tvOnlinePill.setBackgroundResource(
            if (ligado) R.drawable.bg_pill_online else R.drawable.bg_pill_amber
        )
        binding.tvOnlinePill.setTextColor(
            ContextCompat.getColor(
                this,
                if (ligado) R.color.white else R.color.amber
            )
        )

        when {
            !ligado -> {
                binding.tvEstadoSync.setText(R.string.sync_no_internet)
                binding.tvEstadoSyncSub.setText(R.string.sync_offline_toast)
                binding.ivEstadoSync.setImageResource(R.drawable.ic_cloud_off)
                binding.ivEstadoSync.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.amber))
            }
            porEnviar == 0 && prefs.ultimaSync > 0L -> {
                binding.tvEstadoSync.setText(R.string.sync_empty_title)
                binding.tvEstadoSyncSub.text = getString(
                    R.string.sync_line_last,
                    Formato.hora(prefs.ultimaSync)
                )
                binding.ivEstadoSync.setImageResource(R.drawable.ic_check)
                binding.ivEstadoSync.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green))
            }
            porEnviar == 0 -> {
                binding.tvEstadoSync.setText(R.string.sync_online_label)
                binding.tvEstadoSyncSub.setText(R.string.sync_all_done)
                binding.ivEstadoSync.setImageResource(R.drawable.ic_wifi)
                binding.ivEstadoSync.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.blue))
            }
            else -> {
                binding.tvEstadoSync.text = getString(R.string.sync_saved_text, porEnviar)
                binding.tvEstadoSyncSub.setText(R.string.sync_info_note)
                binding.ivEstadoSync.setImageResource(R.drawable.ic_cloud_off)
                binding.ivEstadoSync.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.blue))
            }
        }

        val lista = todos
            .filter { it.estado == "PENDENTE" || it.estado == "REJEITADO" }
            .sortedBy { it.criadoEm }
        adapter.submitList(lista)

        binding.recyclerSync.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
        binding.emptySync.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        binding.btnEnviar.isEnabled = !aSincronizar
        binding.btnEnviar.alpha = if (aSincronizar) 0.55f else 1f
        binding.progressoSync.visibility = if (aSincronizar) View.VISIBLE else View.GONE
    }

    private fun sincronizar() {
        if (aSincronizar) return
        if (!ligado) {
            avisar(R.string.sync_offline_toast)
            return
        }
        aSincronizar = true
        atualizarTudo()
        lifecycleScope.launch {
            val resultado = try {
                SyncManager.sincronizar(this@SyncActivity)
            } finally {
                aSincronizar = false
                atualizarTudo()
            }
            if (resultado.sessaoExpirada) {
                finish()
                return@launch
            }
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

    private fun avisar(texto: Int) {
        Toast.makeText(this, texto, Toast.LENGTH_LONG).show()
    }
}
