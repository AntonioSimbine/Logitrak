package mz.co.logitrack.dto.admin;

import jakarta.validation.constraints.NotNull;

/** Ativação/desativação de conta (W8). */
public record AtivoRequest(
        @NotNull Boolean ativo
) {
}
