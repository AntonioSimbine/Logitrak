package mz.co.logitrack.dto.admin;

import jakarta.validation.constraints.*;

/** Formulário W7. */
public record FabricaRequest(
        @NotBlank @Size(max = 160) String nome,
        @NotBlank @Size(max = 255) String endereco,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @Min(10) @Max(50000) Integer raioGeofenceMetros,
        @NotNull @Min(1) @Max(720) Integer slaLimiteHoras,
        @NotBlank @Size(max = 50) String horarioAcostagem,
        Boolean ativa
) {
}
