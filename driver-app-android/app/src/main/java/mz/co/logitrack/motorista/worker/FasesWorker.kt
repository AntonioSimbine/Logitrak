package mz.co.logitrack.motorista.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Envia os instantes de fase gravados localmente após a sincronização inicial. */
class FasesWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val resultado = FasesManager.sincronizar(applicationContext)
        return when {
            resultado.sessaoExpirada || resultado.semRede || resultado.falhas > 0 -> Result.retry()
            else -> Result.success()
        }
    }
}
