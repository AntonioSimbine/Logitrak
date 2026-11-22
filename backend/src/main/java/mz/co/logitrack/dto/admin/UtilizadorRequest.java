package mz.co.logitrack.dto.admin;

import jakarta.validation.constraints.*;
import mz.co.logitrack.domain.enums.Role;

/** Formulário W8. A senha é obrigatória na criação e opcional na edição. */
public record UtilizadorRequest(
        @NotBlank(message = "Introduza o nome do utilizador.")
        @Size(max = 120, message = "O nome é demasiado longo.")
        String nome,
        @NotBlank(message = "Introduza o e-mail.")
        @Email(message = "Verifique o e-mail introduzido.")
        @Size(max = 160, message = "O e-mail é demasiado longo.")
        String email,
        @NotNull(message = "Selecione o perfil de acesso.") Role perfil,
        Boolean ativo,
        @Size(min = 6, max = 72, message = "A palavra-passe deve ter entre 6 e 72 caracteres.")
        String senha
) {
}
