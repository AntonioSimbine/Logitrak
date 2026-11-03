package mz.co.logitrack.dto.admin;

import jakarta.validation.constraints.*;
import mz.co.logitrack.domain.enums.Role;

/** Formulário W8. A senha é obrigatória na criação e opcional na edição. */
public record UtilizadorRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Email @Size(max = 160) String email,
        @NotNull Role perfil,
        Boolean ativo,
        @Size(min = 6, max = 72, message = "A palavra-passe deve ter entre 6 e 72 caracteres")
        String senha
) {
}
