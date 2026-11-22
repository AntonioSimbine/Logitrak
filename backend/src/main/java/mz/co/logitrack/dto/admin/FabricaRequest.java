package mz.co.logitrack.dto.admin;

import jakarta.validation.constraints.*;

/** Formulário W7. */
public record FabricaRequest(
        @NotBlank(message = "Introduza o nome da fábrica.")
        @Size(max = 160, message = "O nome é demasiado longo.")
        String nome,
        @NotBlank(message = "Introduza o endereço da fábrica.")
        @Size(max = 255, message = "O endereço é demasiado longo.")
        String endereco,
        @NotNull(message = "A latitude é obrigatória.")
        @DecimalMin(value = "-90.0", message = "A latitude indicada não é válida.")
        @DecimalMax(value = "90.0", message = "A latitude indicada não é válida.")
        Double latitude,
        @NotNull(message = "A longitude é obrigatória.")
        @DecimalMin(value = "-180.0", message = "A longitude indicada não é válida.")
        @DecimalMax(value = "180.0", message = "A longitude indicada não é válida.")
        Double longitude,
        @NotNull(message = "Indique o tamanho da área da fábrica.")
        @Min(value = 10, message = "O tamanho da área indicado não é válido.")
        @Max(value = 50000, message = "O tamanho da área indicado não é válido.")
        Integer raioGeofenceMetros,
        @NotNull(message = "Indique o prazo definido para as viagens a partir desta fábrica.")
        @Min(value = 1, message = "O prazo indicado não é válido.")
        @Max(value = 720, message = "O prazo indicado não é válido.")
        Integer slaLimiteHoras,
        @NotBlank(message = "Indique o horário de acostagem.")
        @Size(max = 50, message = "O horário de acostagem é demasiado longo.")
        String horarioAcostagem,
        Boolean ativa
) {
}
