package mz.co.logitrack.motorista.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Envia os instantes de fase gravados localmente após a sincronização inicial. */
class FasesWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        FasesManager.sincronizar(applicationContext)
        return Result.success()
    }
}
