package mz.co.logitrack.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reposição administrativa de palavra-passe. */
public record ResetPasswordRequest(
        @NotBlank @Size(min = 6, max = 72) String novaSenha
) {
}
