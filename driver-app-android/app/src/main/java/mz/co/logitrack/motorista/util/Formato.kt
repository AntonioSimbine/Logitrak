package mz.co.logitrack.motorista.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Formato {
    // Respeita o idioma do dispositivo (pt-MZ quando selecionado), com pt-PT apenas como fallback.
    private val locale = Locale.getDefault().takeIf { it.language.equals("pt", ignoreCase = true) }
        ?: Locale("pt", "MZ")
    private val padroesEntrada = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm:ss"
    )

    fun data(valor: String?): Date? {
        if (valor.isNullOrBlank()) return null
        for (padrao in padroesEntrada) {
            try {
                val formato = SimpleDateFormat(padrao, Locale.US)
                formato.timeZone = TimeZone.getTimeZone("UTC")
                return formato.parse(valor)
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun dataHora(valor: String?): String {
        val data = data(valor) ?: return "—"
        return SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(data)
    }

    fun hora(valor: Long): String {
        if (valor <= 0L) return "—"
        return SimpleDateFormat("HH:mm", locale).format(Date(valor))
    }

    fun dataHora(valor: Long): String {
        if (valor <= 0L) return "—"
        return SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(Date(valor))
    }

    fun relativo(valor: String?): String {
        val data = data(valor) ?: return "—"
        return relativo(data.time)
    }

    fun relativo(momento: Long): String {
        if (momento <= 0L) return "—"
        val minutos = ((System.currentTimeMillis() - momento) / 60000L).coerceAtLeast(0L)
        return when {
            minutos < 1 -> "agora"
            minutos == 1L -> "há 1 min"
            minutos < 60 -> "há $minutos min"
            else -> {
                val horas = minutos / 60
                if (horas == 1L) "há 1 h" else "há $horas h"
            }
        }
    }

    fun numero(valor: Double, casas: Int): String =
        String.format(locale, "%.${casas}f", valor)

    fun inteiro(valor: Long): String = String.format(locale, "%,d", valor).replace(',', ' ')

    fun metros(valor: Double?): String {
        if (valor == null || valor.isNaN()) return "—"
        return "${valor.toInt()} m"
    }

    fun iniciais(nome: String?): String {
        val partes = (nome ?: "").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (partes.isEmpty()) return "M"
        val primeira = partes.first().firstOrNull()?.uppercaseChar() ?: 'M'
        val segunda = if (partes.size > 1) partes.last().firstOrNull()?.uppercaseChar() else null
        return if (segunda != null) "$primeira$segunda" else "$primeira"
    }

    fun agoraIso(): String = iso(Date())

    /** Instante original da leitura de GPS, formatado como o contrato da API. */
    fun iso(instante: Date): String {
        val formato = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        return formato.format(instante)
    }

    fun estadoClasse(estado: String?): String =
        (estado ?: "").lowercase(Locale.ROOT).replace("í", "i").replace("á", "a")
}
