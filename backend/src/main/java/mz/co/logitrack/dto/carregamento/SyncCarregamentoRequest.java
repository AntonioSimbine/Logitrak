package mz.co.logitrack.dto.carregamento;

import jakarta.validation.constraints.*;
import mz.co.logitrack.domain.enums.EstadoCarregamento;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payload de envio enviado pela app móvel. O {@code uuidLocal} é a
 * chave de idempotência e a fotografia segue comprimida em Base64.
 */
public record SyncCarregamentoRequest(
        @NotNull(message = "O identificador do registo é obrigatório")
        UUID uuidLocal,

        @NotBlank(message = "O número da guia é obrigatório")
        @Size(max = 60, message = "O número da guia é demasiado longo.")
        String guiaNumero,

        @NotBlank(message = "O tipo de carga é obrigatório")
        @Size(max = 80, message = "O tipo de carga é demasiado longo.")
        String tipoCarga,

        @NotNull(message = "A quantidade é obrigatória")
        @DecimalMin(value = "0.01", message = "A quantidade deve ser superior a zero")
        Double quantidadeToneladas,

        Long fabricaId,

        @NotNull(message = "O veículo é obrigatório")
        Long veiculoId,

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

        LocalDateTime dataHoraInicio,

        LocalDateTime dataHoraInicioCarregamento,

        LocalDateTime dataHoraFimCarregamento,

        LocalDateTime dataHoraInicioTransporte,

        @NotBlank(message = "Falta a fotografia da guia.")
        String fotoBase64,

        Boolean confirmarForaGeofence
) {
    public EstadoCarregamento estadoInicial() {
        return EstadoCarregamento.REGISTADO;
    }
}
