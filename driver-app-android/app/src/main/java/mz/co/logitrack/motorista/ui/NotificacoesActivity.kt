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
import mz.co.logitrack.motorista.data.local.AppDb
import mz.co.logitrack.motorista.databinding.ActivityNotificacoesBinding
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Prefs

/** Caixa de entrada com os avisos enviados pelo operador e pela administração. */
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
            val fila = if (prefs.userId > 0L) {
                try {
                    AppDb.get(this@NotificacoesActivity)
                        .filaDao()
                        .resumos(prefs.userId)
                        .first()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            notificacoes = Notificacoes.listar(this@NotificacoesActivity, prefs, fila)
            render()
        }
    }

    private fun render() {
        adapter.submitList(notificacoes)
        val naoLidas = notificacoes.count { !it.lida }

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

        val vazia = notificacoes.isEmpty()
        binding.recyclerNotificacoes.visibility = if (vazia) View.GONE else View.VISIBLE
        binding.emptyNotificacoes.visibility = if (vazia) View.VISIBLE else View.GONE
    }

    private fun abrir(item: NotificacaoUi) {
        prefs.marcarNotificacaoLida(item.id)
        item.carregamentoId?.let { id ->
            startActivity(
                Intent(this, DetailActivity::class.java)
                    .putExtra(DetailActivity.EXTRA_ID, id)
            )
        }
        carregar()
    }

    private fun marcarTodas() {
        prefs.marcarNotificacoesLidas(notificacoes.map { it.id })
        carregar()
        Toast.makeText(this, R.string.notifications_marked_all, Toast.LENGTH_SHORT).show()
    }
}
