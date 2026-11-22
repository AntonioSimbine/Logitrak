package mz.co.logitrack.dto.admin;

import jakarta.validation.constraints.*;
import mz.co.logitrack.domain.enums.EstadoVeiculo;

/** Formulário W9. */
public record VeiculoRequest(
        @NotBlank(message = "Introduza a matrícula.")
        @Size(max = 20, message = "A matrícula é demasiado longa.")
        String matricula,
        @NotBlank(message = "Introduza o modelo.")
        @Size(max = 120, message = "O modelo é demasiado longo.")
        String modelo,
        @NotNull(message = "Indique a capacidade de carga.")
        @DecimalMin(value = "0.1", message = "A capacidade deve ser superior a zero.")
        Double capacidadeCargaToneladas,
        @NotNull(message = "Selecione o estado do veículo.") EstadoVeiculo estado
) {
}
