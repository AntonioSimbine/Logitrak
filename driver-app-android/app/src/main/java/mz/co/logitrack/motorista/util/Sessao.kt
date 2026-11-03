package mz.co.logitrack.motorista.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Eventos de sessão para a UI reagir sem depender de broadcasts. */
object Sessao {
    private val _expirou = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expirou: SharedFlow<Unit> = _expirou

    fun sinalizarExpiracao() {
        _expirou.tryEmit(Unit)
    }
}
