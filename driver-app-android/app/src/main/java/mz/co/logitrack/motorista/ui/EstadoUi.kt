package mz.co.logitrack.motorista.ui

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import mz.co.logitrack.motorista.R
import java.util.Locale

/** Cores e etiquetas dos estados, alinhadas com o frontend web. */
object EstadoUi {
    data class Chip(val fundo: Int, val corTexto: Int)

    fun chave(estado: String?): String =
        (estado ?: "").lowercase(Locale.ROOT)
            .replace("í", "i")
            .replace("á", "a")
            .replace(" ", "_")

    fun chipClaro(context: Context, estado: String?): Chip = when (chave(estado)) {
        "registado" -> Chip(R.drawable.bg_chip_blue, Color.parseColor("#1D4ED8"))
        "em_transito" -> Chip(R.drawable.bg_chip_amber, ContextCompat.getColor(context, R.color.amber))
        "atrasado" -> Chip(R.drawable.bg_chip_red, ContextCompat.getColor(context, R.color.red))
        "concluido" -> Chip(R.drawable.bg_chip_green, ContextCompat.getColor(context, R.color.green))
        "rejeitado" -> Chip(R.drawable.bg_chip_red, ContextCompat.getColor(context, R.color.red))
        "pendente" -> Chip(R.drawable.bg_chip_amber, ContextCompat.getColor(context, R.color.amber))
        "sincronizado" -> Chip(R.drawable.bg_chip_green, ContextCompat.getColor(context, R.color.green))
        else -> Chip(R.drawable.bg_chip_grey, ContextCompat.getColor(context, R.color.chip_text))
    }

    fun chipEscuro(estado: String?): Chip = when (chave(estado)) {
        "registado" -> Chip(R.drawable.bg_chip_dark_blue, Color.parseColor("#D6E6FF"))
        "em_transito" -> Chip(R.drawable.bg_chip_dark_amber, Color.parseColor("#FFE7A3"))
        "atrasado" -> Chip(R.drawable.bg_chip_dark_red, Color.parseColor("#FFC9C4"))
        "concluido" -> Chip(R.drawable.bg_chip_dark_green, Color.parseColor("#B8F4D8"))
        "rejeitado" -> Chip(R.drawable.bg_chip_dark_red, Color.parseColor("#FFC9C4"))
        "pendente" -> Chip(R.drawable.bg_chip_dark_amber, Color.parseColor("#FFE7A3"))
        "sincronizado" -> Chip(R.drawable.bg_chip_dark_green, Color.parseColor("#B8F4D8"))
        else -> Chip(R.drawable.bg_chip_dark, Color.WHITE)
    }

    fun etiqueta(context: Context, estado: String?, etiqueta: String?): String {
        val valor = etiqueta?.takeIf { it.isNotBlank() }
            ?: estado?.takeIf { it.isNotBlank() }
            ?: return context.getString(R.string.not_available)
        return when (chave(estado).takeIf { it.isNotBlank() } ?: chave(valor)) {
            "pendente" -> context.getString(R.string.status_pending)
            "sincronizado" -> context.getString(R.string.status_synced)
            "rejeitado" -> context.getString(R.string.status_rejected)
            else -> valor
        }
    }
}
