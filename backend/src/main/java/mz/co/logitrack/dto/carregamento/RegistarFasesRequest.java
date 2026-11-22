package mz.co.logitrack.dto.carregamento;

import java.time.LocalDateTime;

/** Registo manual dos instantes operacionais pelo painel web. */
public record RegistarFasesRequest(
        LocalDateTime dataHoraInicioCarregamento,
        LocalDateTime dataHoraFimCarregamento,
        LocalDateTime dataHoraInicioTransporte,
        LocalDateTime dataHoraFimTransporte
) {
}
