package mz.co.logitrack.motorista.network

import java.util.concurrent.atomic.AtomicBoolean

/**
 * O backend devolve `X-Idempotente: true` quando um uuidLocal já tinha sido
 * sincronizado. O valor é guardado aqui pelo interceptor e consumido pelo
 * SyncManager para não contar como erro uma resposta 200 duplicada.
 */
object Idempotencia {
    private val pendente = AtomicBoolean(false)

    fun sinalizar(valor: Boolean) {
        if (valor) pendente.set(true)
    }

    /** Devolve e limpa o sinal, para não contaminar o pedido seguinte. */
    fun consumir(): Boolean = pendente.getAndSet(false)
}
