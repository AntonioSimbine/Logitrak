package mz.co.logitrack.dto.carregamento;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Ponto de GPS transmitido periodicamente pela app móvel. */
public record PosicaoRequest(
        @NotNull(message = "A latitude é obrigatória")
        @DecimalMin(value = "-90.0", message = "A latitude indicada não é válida.")
        @DecimalMax(value = "90.0", message = "A latitude indicada não é válida.")
        Double latitude,
        @NotNull(message = "A longitude é obrigatória")
        @DecimalMin(value = "-180.0", message = "A longitude indicada não é válida.")
        @DecimalMax(value = "180.0", message = "A longitude indicada não é válida.")
        Double longitude,
        @NotNull(message = "A localização é obrigatória.")
        @DecimalMin(value = "0.0",
                message = "A localização ainda não é precisa. Vá para um local aberto e tente novamente.")
        @DecimalMax(value = "50.0",
                message = "A localização ainda não é precisa. Vá para um local aberto e tente novamente.")
        Double precisaoMetros,
        /** Hora real da captura no dispositivo; ausente nos clientes antigos. */
        LocalDateTime timestamp,
        /** Alias explícito de timestamp, para clientes novos. */
        LocalDateTime dataHoraCaptura,
        /** Identificador do ponto no dispositivo, para reenvio idempotente. */
        String uuidPonto
) {
    public LocalDateTime captura() {
        return dataHoraCaptura != null ? dataHoraCaptura : timestamp;
    }
}
