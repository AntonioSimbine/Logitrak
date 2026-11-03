package mz.co.logitrack.motorista.network

import com.google.gson.Gson
import mz.co.logitrack.motorista.data.dto.ApiErrorDto
import retrofit2.Response

object ApiErrors {
    private val gson = Gson()

    fun mensagem(resp: Response<*>): String {
        val bruto = try {
            resp.errorBody()?.string()
        } catch (e: Exception) {
            null
        }
        if (bruto.isNullOrBlank()) return mensagemPorCodigo(resp.code())
        return try {
            val dto = gson.fromJson(bruto, ApiErrorDto::class.java)
            val base = dto?.error?.takeIf { it.isNotBlank() }
            val detalhes = dto?.detalhes?.values?.filter { it.isNotBlank() }?.joinToString(" ")
            when {
                !base.isNullOrBlank() && !detalhes.isNullOrBlank() -> "$base $detalhes"
                !base.isNullOrBlank() -> base
                !detalhes.isNullOrBlank() -> detalhes
                else -> mensagemPorCodigo(resp.code())
            }
        } catch (e: Exception) {
            bruto.take(240)
        }
    }

    fun mensagemPorCodigo(codigo: Int): String = when (codigo) {
        400 -> "Dados inválidos. Verifique os campos."
        401 -> "Sessão expirada. Inicie sessão novamente."
        403 -> "Não tem permissão para esta operação."
        404 -> "Registo não encontrado."
        409 -> "Este registo já existe no servidor."
        422 -> "O servidor rejeitou o registo. Verifique os dados e a localização."
        500, 502, 503, 504 -> "O servidor está indisponível. Tente mais tarde."
        else -> "Não foi possível concluir o pedido (HTTP $codigo)."
    }
}
