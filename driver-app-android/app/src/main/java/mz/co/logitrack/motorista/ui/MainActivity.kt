package mz.co.logitrack.motorista.ui

import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.local.AppDb
import mz.co.logitrack.motorista.databinding.ActivityMainBinding
import mz.co.logitrack.motorista.network.NetworkUtils
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Ligacao
import mz.co.logitrack.motorista.util.Prefs
import mz.co.logitrack.motorista.util.Sessao
import mz.co.logitrack.motorista.worker.SyncManager

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private var callbackRede: ConnectivityManager.NetworkCallback? = null
    private var ligadoAnterior = false
    private var aSair = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)
        if (prefs.token.isNullOrBlank()) {
            irParaLogin(null)
            return
        }

        intent?.getStringExtra(EXTRA_MENSAGEM)?.takeIf { it.isNotBlank() }?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            intent?.removeExtra(EXTRA_MENSAGEM)
        }

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)
        prepararBadge()

        navController.addOnDestinationChangedListener { _, destino, _ ->
            val comNavegacao = destino.id == R.id.homeFragment ||
                destino.id == R.id.queueFragment ||
                destino.id == R.id.documentsFragment ||
                destino.id == R.id.perfilFragment
            binding.bottomNav.visibility = if (comNavegacao) View.VISIBLE else View.GONE
        }

        binding.connBar.setOnClickListener { abrirBarraLigacao() }
        consumirPedidoHistorico(intent)

        observarFila()
        observarSessao()
        ligadoAnterior = NetworkUtils.estaLigado(this)
        Ligacao.definir(ligadoAnterior)
        atualizarBarra(ligadoAnterior)
        callbackRede = NetworkUtils.observar(this) { ligado ->
            runOnUiThread { tratarLigacao(ligado) }
        }

        if (ligadoAnterior) {
            lifecycleScope.launch {
                delay(900)
                sincronizarSilencioso()
            }
        }

        Animacoes.surgir(binding.bottomNav, atraso = 120L, deslocamentoY = 36f)
        Animacoes.pressionar(binding.connBar)
    }

    private fun prepararBadge() {
        val badge = binding.bottomNav.getOrCreateBadge(R.id.queueFragment)
        badge.backgroundColor = ContextCompat.getColor(this, R.color.red)
        badge.badgeTextColor = Color.WHITE
        badge.maxCharacterCount = 3
        badge.isVisible = false
    }

    private fun observarFila() {
        val id = prefs.userId
        if (id <= 0L) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDb.get(this@MainActivity).filaDao().resumos(id).collectLatest { lista ->
                    val pendentes = lista.count { it.estado == "PENDENTE" }
                    val rejeitados = lista.count { it.estado == "REJEITADO" }
                    val total = pendentes + rejeitados
                    val badge = binding.bottomNav.getOrCreateBadge(R.id.queueFragment)
                    badge.isVisible = total > 0
                    badge.number = total
                }
            }
        }
    }

    private fun observarSessao() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Sessao.expirou.collectLatest { tratarSessaoExpirada() }
            }
        }
    }

    private fun tratarSessaoExpirada() {
        if (aSair) return
        aSair = true
        prefs.limparSessao()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.session_expired_title)
            .setMessage(R.string.session_expired_message)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ -> irParaLogin(getString(R.string.error_session_expired)) }
            .show()
    }

    private fun irParaLogin(mensagem: String?) {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .putExtra(EXTRA_MENSAGEM, mensagem)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun tratarLigacao(ligado: Boolean) {
        Ligacao.definir(ligado)
        atualizarBarra(ligado)
        if (ligado && !ligadoAnterior) {
            sincronizarSilencioso()
        }
        ligadoAnterior = ligado
    }

    private fun atualizarBarra(ligado: Boolean) {
        binding.connBar.setBackgroundResource(
            if (ligado) R.drawable.bg_connectivity_online else R.drawable.bg_connectivity_offline
        )
        binding.connText.setText(
            if (ligado) R.string.connectivity_online else R.string.connectivity_offline
        )
        binding.connBar.alpha = 1f
        binding.connBar.contentDescription = getString(
            if (ligado) R.string.sync_online_label else R.string.offline_title
        )
    }

    private fun abrirBarraLigacao() {
        val destino = if (Ligacao.estado.value) SyncActivity::class.java else OfflineActivity::class.java
        startActivity(Intent(this, destino))
    }

    private fun consumirPedidoHistorico(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ABRIR_HISTORICO, false) != true) return
        intent.removeExtra(EXTRA_ABRIR_HISTORICO)
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as? NavHostFragment
        val navController = navHost?.navController ?: return
        if (navController.currentDestination?.id != R.id.queueFragment) {
            navController.navigate(R.id.queueFragment)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumirPedidoHistorico(intent)
    }

    private fun sincronizarSilencioso() {
        if (prefs.token.isNullOrBlank() || !NetworkUtils.estaLigado(this)) return
        lifecycleScope.launch {
            SyncManager.sincronizar(this@MainActivity)
        }
    }

    override fun onDestroy() {
        callbackRede?.let { NetworkUtils.cancelar(this, it) }
        callbackRede = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MENSAGEM = "mensagem"
        const val EXTRA_ABRIR_HISTORICO = "abrir_historico"
    }
}
