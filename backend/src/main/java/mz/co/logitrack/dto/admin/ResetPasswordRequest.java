package mz.co.logitrack.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reposição administrativa de palavra-passe. */
public record ResetPasswordRequest(
        @NotBlank(message = "Introduza a nova palavra-passe.")
        @Size(min = 6, max = 72,
                message = "A nova palavra-passe deve ter entre 6 e 72 caracteres.")
        String novaSenha
) {
}
