package mz.co.logitrack.motorista.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.dto.ApiErrorDto
import retrofit2.Response

object ApiErrors {
    private val gson = Gson()

    fun mensagem(context: Context, resp: Response<*>): String {
        val bruto = try {
            resp.errorBody()?.string()
        } catch (e: Exception) {
            null
        }
        if (bruto.isNullOrBlank()) return mensagemPorCodigo(context, resp.code())
        return try {
            val dto = gson.fromJson(bruto, ApiErrorDto::class.java)
            val base = dto?.error?.takeIf { it.isNotBlank() }
            val detalhes = dto?.detalhes?.values?.filter { it.isNotBlank() }?.joinToString(" ")
            when {
                !base.isNullOrBlank() && !detalhes.isNullOrBlank() -> "$base $detalhes"
                !base.isNullOrBlank() -> base
                !detalhes.isNullOrBlank() -> detalhes
                else -> mensagemPorCodigo(context, resp.code())
            }
        } catch (e: Exception) {
            // Detalhe técnico fica só no registo interno para apoio.
            Log.w("ApiErrors", "Resposta da central não interpretada (HTTP ${resp.code()})", e)
            mensagemPorCodigo(context, resp.code())
        }
    }

    fun mensagemPorCodigo(context: Context, codigo: Int): String = when (codigo) {
        400 -> context.getString(R.string.api_error_400)
        401 -> context.getString(R.string.api_error_401)
        403 -> context.getString(R.string.api_error_403)
        404 -> context.getString(R.string.api_error_404)
        409 -> context.getString(R.string.api_error_409)
        422 -> context.getString(R.string.api_error_422)
        500, 502, 503, 504 -> context.getString(R.string.api_error_500)
        else -> context.getString(R.string.api_error_other)
    }
}
