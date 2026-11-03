package mz.co.logitrack.motorista.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.databinding.ActivitySuccessBinding
import mz.co.logitrack.motorista.util.Animacoes

class SuccessActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvCodigo.text = intent.getStringExtra(EXTRA_CODIGO)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.not_available)
        binding.tvData.text = intent.getStringExtra(EXTRA_DATA)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.not_available)

        binding.btnInicio.setOnClickListener { finish() }
        prepararAnimacoes()
    }

    private fun prepararAnimacoes() {
        Animacoes.pop(binding.successCheck, escalaInicial = 0.42f, atraso = 120L)
        Animacoes.entrada(
            binding.successReceipt,
            binding.successInfo,
            atraso = 260L,
            intervalo = 90L,
            deslocamentoY = 20f
        )
        Animacoes.surgir(binding.successAction, atraso = 420L, deslocamentoY = 30f)
        Animacoes.pressionar(binding.btnInicio)
    }

    companion object {
        const val EXTRA_CODIGO = "codigo"
        const val EXTRA_DATA = "data"
    }
}
