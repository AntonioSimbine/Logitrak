package mz.co.logitrack.motorista.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.databinding.ActivitySplashBinding
import mz.co.logitrack.motorista.network.Atualizador
import mz.co.logitrack.motorista.network.InfoVersao
import mz.co.logitrack.motorista.util.Animacoes

/** Abertura da aplicação: identidade Logitrack e aviso de nova versão. */
class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = getColor(R.color.navy)
        window.navigationBarColor = getColor(R.color.navy)

        animarEntrada()

        lifecycleScope.launch {
            val verificacao = async(Dispatchers.IO) { Atualizador.verificar() }
            delay(1600)
            val resultado = try {
                withTimeout(7000) { verificacao.await() }
            } catch (e: Exception) {
                null
            }
            if (isFinishing || isDestroyed) return@launch

            val nova = resultado?.info?.takeIf { resultado.disponivel }
            if (nova == null) {
                abrirLogin()
                return@launch
            }

            if (nova.obrigatoria) {
                abrirAtualizacao(nova)
            } else {
                MaterialAlertDialogBuilder(this@SplashActivity)
                    .setTitle(R.string.update_title)
                    .setMessage(
                        nova.mensagem?.trim()?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.update_message_default)
                    )
                    .setNegativeButton(R.string.update_depois) { _, _ -> abrirLogin() }
                    .setPositiveButton(R.string.update_atualizar) { _, _ -> abrirAtualizacao(nova) }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun abrirAtualizacao(nova: InfoVersao) {
        startActivity(
            Intent(this, AtualizacaoActivity::class.java)
                .putExtra(AtualizacaoActivity.EXTRA_INFO, nova)
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    private fun abrirLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    private fun animarEntrada() {
        Animacoes.entrada(
            binding.logo,
            binding.wordmark,
            deslocamentoY = 22f,
            atraso = 90L,
            intervalo = 85L,
            duracao = 460L
        )

        if (!Animacoes.ativas(this)) return
        binding.logoReflexo.alpha = 0f
        binding.logoReflexo.animate()
            .alpha(0.18f)
            .setStartDelay(220L)
            .setDuration(520L)
            .start()
        binding.loading.alpha = 0f
        binding.loading.animate()
            .alpha(1f)
            .setStartDelay(420L)
            .setDuration(420L)
            .start()
    }
}
