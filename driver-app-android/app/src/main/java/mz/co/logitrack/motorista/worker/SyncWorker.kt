package mz.co.logitrack.motorista.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import mz.co.logitrack.motorista.util.Prefs

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (prefs.token.isNullOrBlank()) return Result.success()
        val resultado = SyncManager.sincronizar(applicationContext)
        return when {
            resultado.sessaoExpirada || resultado.semRede || resultado.falhas > 0 -> Result.retry()
            else -> Result.success()
        }
    }
}
