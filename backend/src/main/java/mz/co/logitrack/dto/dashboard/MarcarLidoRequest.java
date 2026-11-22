package mz.co.logitrack.dto.dashboard;

import jakarta.validation.constraints.NotNull;

/** Corpo do PATCH /api/v1/alertas/{id}. */
public record MarcarLidoRequest(
        @NotNull(message = "Indique se o alerta foi tratado.") Boolean lido
) {
}
