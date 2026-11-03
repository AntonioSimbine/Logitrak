package mz.co.logitrack.motorista.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Estado de ligação observável pelos ecrãs. */
object Ligacao {
    private val _estado = MutableStateFlow(true)
    val estado: StateFlow<Boolean> = _estado

    fun definir(ligado: Boolean) {
        _estado.value = ligado
    }
}
