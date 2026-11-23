package mz.co.logitrack.motorista.worker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import mz.co.logitrack.motorista.data.local.AppDb
import mz.co.logitrack.motorista.data.local.PosicaoEntity
import mz.co.logitrack.motorista.util.Formato
import mz.co.logitrack.motorista.util.Geo
import mz.co.logitrack.motorista.util.Prefs
import java.util.Date
import java.util.UUID
import kotlin.coroutines.resume

/** Capta pontos de GPS e guarda o instante original antes de os enviar. */
class TrackingWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val contexto = applicationContext
        val permissao = ContextCompat.checkSelfPermission(
            contexto, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                contexto, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (!permissao) return Result.success()

        val prefs = Prefs(contexto)
        if (prefs.token.isNullOrBlank()) return Result.success()
        val fila = AppDb.get(contexto).filaDao().ultimoCarregamento(prefs.userId)
            ?: return Result.success()

        val localizacao = try {
            suspendCancellableCoroutine<Location?> { continuar ->
                LocationServices.getFusedLocationProviderClient(contexto).lastLocation
                    .addOnSuccessListener { continuar.resume(it) }
                    .addOnFailureListener { continuar.resume(null) }
            }
        } catch (e: Exception) {
            null
        } ?: return Result.success()

        val precisao = localizacao.accuracy.toDouble()
        if (precisao > Geo.precisaoMaxima(prefs.lerReferencias())) {
            return Result.success()
        }
        val idadeMs = System.currentTimeMillis() - localizacao.time
        if (idadeMs > Geo.IDADE_MAXIMA_POSICAO_MS) {
            return Result.success()
        }

        val carregamentoId = fila.carregamentoId ?: return Result.success()
        AppDb.get(contexto).posicaoDao().inserir(
            PosicaoEntity(
                carregamentoId = carregamentoId,
                motoristaId = prefs.userId,
                latitude = localizacao.latitude,
                longitude = localizacao.longitude,
                precisaoMetros = precisao,
                uuidPonto = UUID.randomUUID().toString(),
                // Instante da leitura GPS, não o momento em que a rede voltar.
                timestamp = Formato.iso(Date(localizacao.time))
            )
        )
        SyncManager.sincronizar(contexto)
        return Result.success()
    }
}
