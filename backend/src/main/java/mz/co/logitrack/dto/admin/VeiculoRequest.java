package mz.co.logitrack.dto.admin;

import jakarta.validation.constraints.*;
import mz.co.logitrack.domain.enums.EstadoVeiculo;

/** Formulário W9. */
public record VeiculoRequest(
        @NotBlank @Size(max = 20) String matricula,
        @NotBlank @Size(max = 120) String modelo,
        @NotNull @DecimalMin("0.1") Double capacidadeCargaToneladas,
        @NotNull EstadoVeiculo estado
) {
}
