package mz.co.logitrack.motorista.util

import mz.co.logitrack.motorista.data.dto.FabricaDto
import mz.co.logitrack.motorista.data.dto.ReferenciasDto
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class FabricaProxima(
    val fabrica: FabricaDto,
    val distancia: Double,
    val raio: Int,
    val dentro: Boolean
)

/** Cálculos de geofence alinhados com o frontend web. */
object Geo {
    const val RAIO_PADRAO = 500.0
    const val PRECISAO_PADRAO = 15.0
    private const val RAIO_TERRA = 6_371_000.0

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rad = Math.PI / 180.0
        val dLat = (lat2 - lat1) * rad
        val dLon = (lon2 - lon1) * rad
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * rad) * cos(lat2 * rad) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * RAIO_TERRA * atan2(sqrt(a), sqrt(1 - a))
    }

    fun fabricaMaisProxima(fabricas: List<FabricaDto>?, lat: Double, lng: Double): FabricaProxima? {
        var melhor: FabricaProxima? = null
        fabricas.orEmpty().forEach { fabrica ->
            val fLat = fabrica.latitude ?: return@forEach
            val fLng = fabrica.longitude ?: return@forEach
            val distancia = haversine(lat, lng, fLat, fLng)
            val raio = (fabrica.raioGeofenceMetros ?: RAIO_PADRAO.toInt()).coerceAtLeast(1)
            if (melhor == null || distancia < melhor!!.distancia) {
                melhor = FabricaProxima(fabrica, distancia, raio, distancia <= raio)
            }
        }
        return melhor
    }

    fun precisaoMaxima(refs: ReferenciasDto?): Double {
        val bruto = refs?.parametros?.get("PRECISAO_MAXIMA_GPS_METROS")
            ?.replace(',', '.')
            ?.trim()
        val valor = bruto?.toDoubleOrNull() ?: return PRECISAO_PADRAO
        return if (valor <= 0) PRECISAO_PADRAO else valor
    }

    fun intervaloMinutos(refs: ReferenciasDto?): Long {
        val valor = refs?.parametros?.get("INTERVALO_SINCRONIZACAO_AUTOMATICA_MINUTOS")
            ?.trim()
            ?.toLongOrNull() ?: return 5L
        return if (valor < 1) 5L else valor
    }

    fun formatarMetros(valor: Double): String =
        if (valor == Math.rint(valor)) valor.toInt().toString() else String.format(LocalePt.numero, "%.1f", valor)

    private object LocalePt {
        val numero = java.util.Locale("pt", "PT")
    }
}
