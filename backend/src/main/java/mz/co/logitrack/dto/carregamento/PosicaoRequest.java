package mz.co.logitrack.dto.carregamento;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Ponto de GPS transmitido periodicamente pela app móvel. */
public record PosicaoRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull
        @DecimalMin(value = "0.0", message = "A precisão do GPS não pode ser negativa")
        @DecimalMax(value = "15.0",
                message = "Precisão de GPS insuficiente para validação (Máx: 15m). "
                        + "Active o GPS e aguarde uma fix melhor antes de registar.")
        Double precisaoMetros,
        LocalDateTime timestamp
) {
}
