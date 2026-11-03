package mz.co.logitrack.dto.admin;

import jakarta.validation.constraints.NotBlank;

/** Alteração do valor de um parâmetro global (W10). */
public record ParametroUpdateRequest(
        @NotBlank(message = "O valor é obrigatório") String valor
) {
}
