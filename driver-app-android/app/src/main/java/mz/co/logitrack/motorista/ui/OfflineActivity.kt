package mz.co.logitrack.motorista.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.local.AppDb
import mz.co.logitrack.motorista.databinding.ActivityOfflineBinding
import mz.co.logitrack.motorista.util.Formato
import mz.co.logitrack.motorista.util.Prefs

class OfflineActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOfflineBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfflineBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.btnFechar.setOnClickListener { finish() }
        binding.btnContinuar.setOnClickListener { finish() }

        atualizarUltimaSync()
        observarFila()
    }

    private fun observarFila() {
        val id = prefs.userId
        if (id <= 0L) {
            binding.tvGuardados.setText(R.string.sync_saved_none)
            return
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDb.get(this@OfflineActivity).filaDao().resumos(id).collectLatest { lista ->
                    val guardados = lista.count {
                        it.estado == "PENDENTE" || it.estado == "REJEITADO"
                    }
                    binding.tvGuardados.text = when (guardados) {
                        0 -> getString(R.string.sync_saved_none)
                        1 -> getString(R.string.offline_saved_one)
                        else -> getString(R.string.offline_saved_count, guardados)
                    }
                }
            }
        }
    }

    private fun atualizarUltimaSync() {
        binding.tvUltimaSync.text = if (prefs.ultimaSync > 0L) {
            getString(R.string.offline_last_sync, Formato.dataHora(prefs.ultimaSync))
        } else {
            getString(R.string.offline_never_sync)
        }
    }
}
