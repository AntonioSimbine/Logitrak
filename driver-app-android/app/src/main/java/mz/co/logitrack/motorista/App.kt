package mz.co.logitrack.motorista

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import mz.co.logitrack.motorista.util.Geo
import mz.co.logitrack.motorista.util.Prefs
import mz.co.logitrack.motorista.worker.SyncWorker
import mz.co.logitrack.motorista.worker.TrackingWorker
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        agendarTrabalhos()
    }

    private fun agendarTrabalhos() {
        val conexao = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val minutosSync = intervaloSincronizacao()
        val sincronizacao = PeriodicWorkRequestBuilder<SyncWorker>(
            minutosSync, TimeUnit.MINUTES
        )
            .setConstraints(conexao)
            .build()
        val localizacao = PeriodicWorkRequestBuilder<TrackingWorker>(
            minutosSync, TimeUnit.MINUTES
        )
            .setConstraints(conexao)
            .build()
        val work = WorkManager.getInstance(this)
        work.enqueueUniquePeriodicWork(
            "logitrack_sync", ExistingPeriodicWorkPolicy.UPDATE, sincronizacao
        )
        work.enqueueUniquePeriodicWork(
            "logitrack_tracking", ExistingPeriodicWorkPolicy.UPDATE, localizacao
        )
    }

    /** O WorkManager exige intervalo mínimo de 15 minutos, mesmo que o parâmetro seja 5. */
    private fun intervaloSincronizacao(): Long =
        maxOf(15L, Geo.intervaloMinutos(Prefs(this).lerReferencias()))
}
