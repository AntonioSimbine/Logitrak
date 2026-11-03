package mz.co.logitrack.dto.carregamento;

import jakarta.validation.constraints.*;
import mz.co.logitrack.domain.enums.EstadoCarregamento;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payload de sincronização enviado pela app móvel. O {@code uuidLocal} é a
 * chave de idempotência e a fotografia segue comprimida em Base64.
 */
public record SyncCarregamentoRequest(
        @NotNull(message = "uuidLocal é obrigatório")
        UUID uuidLocal,

        @NotBlank(message = "O número da guia é obrigatório")
        @Size(max = 60)
        String guiaNumero,

        @NotBlank(message = "O tipo de carga é obrigatório")
        @Size(max = 80)
        String tipoCarga,

        @NotNull(message = "A quantidade é obrigatória")
        @DecimalMin(value = "0.01", message = "A quantidade deve ser superior a zero")
        Double quantidadeToneladas,

        Long fabricaId,

        @NotNull(message = "O veículo é obrigatório")
        Long veiculoId,

        @NotNull(message = "A latitude é obrigatória")
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,

        @NotNull(message = "A longitude é obrigatória")
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude,

        @NotNull(message = "A precisão do GPS é obrigatória")
        @DecimalMin("0.0")
        Double precisaoMetros,

        LocalDateTime dataHoraInicio,

        @NotBlank(message = "A fotografia da guia é obrigatória")
        String fotoBase64,

        Boolean confirmarForaGeofence
) {
    public EstadoCarregamento estadoInicial() {
        return EstadoCarregamento.REGISTADO;
    }
}
