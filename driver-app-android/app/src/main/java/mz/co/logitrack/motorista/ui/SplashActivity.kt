package mz.co.logitrack.motorista.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.databinding.ActivitySplashBinding
import mz.co.logitrack.motorista.util.Animacoes

/** Abertura da aplicação: identidade Logitrack e passagem automática para o Login. */
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
            delay(1600)
            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }
    }

    override fun onDestroy() {
        Animacoes.parar(binding.ivSplashCamiao)
        super.onDestroy()
    }

    private fun animarEntrada() {
        Animacoes.entrada(
            binding.logo,
            binding.wordmark,
            binding.hero,
            binding.subtitle,
            deslocamentoY = 22f,
            atraso = 90L,
            intervalo = 85L,
            duracao = 460L
        )
        Animacoes.zoomLento(binding.ivSplashCamiao)

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
