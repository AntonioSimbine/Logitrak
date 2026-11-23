package mz.co.logitrack.motorista.ui

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mz.co.logitrack.motorista.BuildConfig
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.databinding.ActivityAtualizacaoBinding
import mz.co.logitrack.motorista.network.Atualizador
import mz.co.logitrack.motorista.network.InfoVersao
import java.io.File

/** Ecrã que descarrega e instala a nova versão sem sair da aplicação. */
class AtualizacaoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAtualizacaoBinding

    private var info: InfoVersao? = null
    private var obrigatoria = false
    private var arquivo: File? = null
    private var downloadJob: Job? = null

    private val pedirPermissao: ActivityResultLauncher<android.content.Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Atualizador.podeInstalar(this)) {
                binding.btnInstalar.text = getString(R.string.update_install)
                binding.tvErro.visibility = View.GONE
                arquivo?.let { apk -> instalar(apk) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAtualizacaoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = getColor(R.color.navy)
        window.navigationBarColor = getColor(R.color.navy)

        @Suppress("DEPRECATION")
        val recebida = intent.getSerializableExtra(EXTRA_INFO) as? InfoVersao
        val manual = intent.getBooleanExtra(EXTRA_MANUAL, false)
        obrigatoria = recebida?.obrigatoria ?: false
        info = recebida

        binding.btnVoltar.visibility = if (obrigatoria) View.GONE else View.VISIBLE
        binding.btnVoltar.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!obrigatoria) finish()
            }
        })

        binding.btnAtualizar.setOnClickListener { iniciarDownload() }
        binding.btnDepois.setOnClickListener { finish() }
        binding.btnFechar.setOnClickListener { finish() }
        binding.btnInstalar.setOnClickListener { arquivo?.let { apk -> instalar(apk) } }

        if (recebida != null) {
            prepararTela(recebida)
        } else {
            verificar()
        }
    }

    private fun verificar() {
        lifecycleScope.launch {
            val resultado = Atualizador.verificar()
            when {
                resultado.erro -> mostrarErro(getString(R.string.update_check_error))
                resultado.info == null || !resultado.disponivel -> {
                    binding.tvMensagem.text = getString(R.string.update_up_to_date)
                    binding.tvVersoes.text = getString(
                        R.string.update_current_version,
                        BuildConfig.VERSION_NAME
                    )
                    binding.tvErro.visibility = View.GONE
                    binding.progresso.visibility = View.GONE
                    binding.tvProgresso.visibility = View.GONE
                    binding.btnAtualizar.visibility = View.GONE
                    binding.btnDepois.visibility = View.GONE
                    binding.btnInstalar.visibility = View.GONE
                    binding.btnFechar.visibility = View.VISIBLE
                }
                else -> {
                    val nova = resultado.info!!
                    info = nova
                    obrigatoria = nova.obrigatoria
                    binding.btnVoltar.visibility = if (obrigatoria) View.GONE else View.VISIBLE
                    prepararTela(nova)
                }
            }
        }
    }

    private fun prepararTela(nova: InfoVersao) {
        val texto = nova.mensagem?.trim()?.takeIf { it.isNotBlank() }
            ?: getString(
                if (nova.obrigatoria) R.string.update_obrigatoria_message
                else R.string.update_message_default
            )
        binding.tvMensagem.text = texto
        binding.tvVersoes.text = getString(
            R.string.update_versions,
            BuildConfig.VERSION_NAME,
            nova.versionName.ifBlank { nova.versionCode.toString() }
        )
        binding.tvErro.visibility = View.GONE
        binding.progresso.visibility = View.GONE
        binding.tvProgresso.visibility = View.GONE
        binding.btnAtualizar.visibility = View.VISIBLE
        binding.btnAtualizar.isEnabled = true
        binding.btnAtualizar.text = getString(R.string.update_atualizar)
        binding.btnDepois.visibility = if (nova.obrigatoria) View.GONE else View.VISIBLE
        binding.btnDepois.isEnabled = true
        binding.btnInstalar.visibility = View.GONE
        binding.btnFechar.visibility = View.GONE
    }

    private fun iniciarDownload() {
        val nova = info ?: return
        binding.tvErro.visibility = View.GONE
        binding.btnAtualizar.isEnabled = false
        binding.btnDepois.isEnabled = false
        binding.btnInstalar.visibility = View.GONE
        binding.progresso.visibility = View.VISIBLE
        binding.progresso.isIndeterminate = true
        binding.progresso.progress = 0
        binding.tvProgresso.visibility = View.VISIBLE
        binding.tvProgresso.text = getString(R.string.update_downloading)

        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch {
            try {
                val apk = Atualizador.descarregar(this@AtualizacaoActivity, nova) { percento ->
                    if (percento >= 0) {
                        binding.progresso.isIndeterminate = false
                        binding.progresso.progress = percento
                        binding.tvProgresso.text = getString(
                            R.string.update_downloading_percent,
                            percento
                        )
                    } else {
                        binding.tvProgresso.text = getString(R.string.update_downloading)
                    }
                }
                prepararInstalacao(apk)
            } catch (e: CancellationException) {
                // O ecrã foi fechado; não há nada a mostrar.
            } catch (e: Exception) {
                mostrarErro(getString(R.string.update_error))
            }
        }
    }

    private fun prepararInstalacao(apk: File) {
        arquivo = apk
        binding.progresso.visibility = View.GONE
        binding.tvProgresso.visibility = View.VISIBLE
        binding.tvProgresso.text = getString(R.string.update_installing)
        binding.btnAtualizar.visibility = View.GONE
        binding.btnDepois.visibility = if (obrigatoria) View.GONE else View.VISIBLE
        binding.btnDepois.isEnabled = true
        binding.btnInstalar.visibility = View.VISIBLE
        binding.btnInstalar.text = getString(R.string.update_install)
        instalar(apk)
    }

    private fun instalar(apk: File) {
        if (!Atualizador.podeInstalar(this)) {
            binding.tvErro.text = getString(R.string.update_permission_message)
            binding.tvErro.visibility = View.VISIBLE
            binding.btnInstalar.text = getString(R.string.update_open_settings)
            pedirPermissao.launch(Atualizador.intentDefinicoesInstalacao(this))
            return
        }
        try {
            startActivity(Atualizador.intentInstalacao(this, apk))
            binding.tvErro.visibility = View.GONE
        } catch (e: Exception) {
            mostrarErro(getString(R.string.update_install_failed))
        }
    }

    private fun mostrarErro(mensagem: String) {
        binding.tvErro.text = mensagem
        binding.tvErro.visibility = View.VISIBLE
        binding.progresso.visibility = View.GONE
        binding.tvProgresso.visibility = View.GONE
        binding.btnAtualizar.visibility = View.VISIBLE
        binding.btnAtualizar.isEnabled = true
        binding.btnAtualizar.text = getString(R.string.update_retry)
        binding.btnDepois.visibility = if (obrigatoria) View.GONE else View.VISIBLE
        binding.btnDepois.isEnabled = true
        binding.btnInstalar.visibility = View.GONE
        binding.btnFechar.visibility = View.GONE
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_INFO = "info_atualizacao"
        const val EXTRA_MANUAL = "atualizacao_manual"
    }
}
