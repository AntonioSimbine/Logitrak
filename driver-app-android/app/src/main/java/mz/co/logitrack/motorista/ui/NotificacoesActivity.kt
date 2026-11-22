package mz.co.logitrack.motorista.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.dto.MarcarLidoRequest
import mz.co.logitrack.motorista.data.local.AppDb
import mz.co.logitrack.motorista.databinding.ActivityNotificacoesBinding
import mz.co.logitrack.motorista.network.RetrofitClient
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Ligacao
import mz.co.logitrack.motorista.util.Prefs
import mz.co.logitrack.motorista.util.Sessao

/** Caixa de entrada das notificações destinadas à conta do motorista. */
class NotificacoesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificacoesBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: NotificacaoAdapter

    private var notificacoes: List<NotificacaoUi> = emptyList()
    private var ultimoContador = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificacoesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        adapter = NotificacaoAdapter(::abrir)
        binding.recyclerNotificacoes.layoutManager = LinearLayoutManager(this)
        binding.recyclerNotificacoes.adapter = adapter

        binding.btnVoltar.setOnClickListener { finish() }
        binding.btnMarcarLidas.setOnClickListener { marcarTodas() }

        Animacoes.cascata(binding.root, atraso = 50L, intervalo = 65L)
        Animacoes.pressionar(binding.btnVoltar, binding.btnMarcarLidas)
    }

    override fun onResume() {
        super.onResume()
        carregar()
    }

    private fun carregar() {
        lifecycleScope.launch {
            render(Notificacoes.listar(this@NotificacoesActivity, prefs, lerFila()))
            if (Ligacao.estado.value && prefs.userId > 0L) {
                try {
                    val resp = RetrofitClient.getApi(this@NotificacoesActivity).notificacoes()
                    val corpo = resp.body()
                    when {
                        resp.isSuccessful && corpo != null -> {
                            prefs.guardarNotificacoes(corpo)
                            render(Notificacoes.listar(this@NotificacoesActivity, prefs, lerFila(), corpo))
                        }
                        resp.code() == 401 -> Sessao.sinalizarExpiracao()
                    }
                } catch (_: Exception) {
                    // Sem rede, mantém o cache: a listagem local já foi apresentada.
                }
            }
        }
    }

    private suspend fun lerFila() = if (prefs.userId > 0L) {
        try {
            AppDb.get(this@NotificacoesActivity).filaDao().resumos(prefs.userId).first()
        } catch (_: Exception) {
            emptyList()
        }
    } else {
        emptyList()
    }

    private fun render(itens: List<NotificacaoUi>) {
        notificacoes = itens
        adapter.submitList(itens)
        val naoLidas = itens.count { !it.lida }

        binding.tvResumoTexto.text = if (naoLidas > 0) {
            getString(R.string.notifications_unread, naoLidas)
        } else {
            getString(R.string.notifications_all_read)
        }
        binding.tvContador.visibility = if (naoLidas > 0) View.VISIBLE else View.GONE
        binding.tvContador.text = if (naoLidas > 9) "9+" else naoLidas.toString()
        if (naoLidas > 0 && naoLidas != ultimoContador) {
            Animacoes.pop(binding.tvContador, 0.6f)
        }
        ultimoContador = naoLidas

        val podeMarcar = naoLidas > 0
        binding.btnMarcarLidas.isEnabled = podeMarcar
        binding.btnMarcarLidas.alpha = if (podeMarcar) 1f else 0.45f

        val vazia = itens.isEmpty()
        binding.recyclerNotificacoes.visibility = if (vazia) View.GONE else View.VISIBLE
        binding.emptyNotificacoes.visibility = if (vazia) View.VISIBLE else View.GONE
    }

    private fun abrir(item: NotificacaoUi) {
        marcarLida(item)
        item.carregamentoId?.let { id ->
            startActivity(
                Intent(this, DetailActivity::class.java)
                        .putExtra(DetailActivity.EXTRA_ID, id)
            )
        }
    }

    private fun marcarLida(item: NotificacaoUi) {
        prefs.marcarNotificacaoLida(item.id)
        val remotaId = item.remotaId
        if (remotaId == null || prefs.userId <= 0L || !Ligacao.estado.value) {
            render(notificacoes.map {
                if (it.id == item.id) it.copy(lida = true) else it
            })
            return
        }
        lifecycleScope.launch {
            try {
                RetrofitClient.getApi(this@NotificacoesActivity).marcarNotificacaoLida(
                    remotaId, MarcarLidoRequest(true)
                )
            } catch (_: Exception) {
                // A leitura fica registada localmente e será reconciliada no próximo acesso.
            }
            carregar()
        }
    }

    private fun marcarTodas() {
        prefs.marcarNotificacoesLidas(notificacoes.filter { !it.lida }.map { it.id })
        val remotas = notificacoes.filter { !it.lida }.mapNotNull { it.remotaId }
        if (remotas.isNotEmpty() && prefs.userId > 0L && Ligacao.estado.value) {
            lifecycleScope.launch {
                try {
                    RetrofitClient.getApi(this@NotificacoesActivity).marcarNotificacoesLidas()
                } catch (_: Exception) {
                    // Mantém o estado local; o contador do servidor será atualizado depois.
                }
                carregar()
            }
        } else {
            carregar()
        }
        Toast.makeText(this, R.string.notifications_marked_all, Toast.LENGTH_SHORT).show()
    }
}
