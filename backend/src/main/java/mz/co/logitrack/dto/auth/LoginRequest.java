package mz.co.logitrack.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Credenciais de entrada (M1 móvel e W1 web). */
public record LoginRequest(
        @NotBlank(message = "Introduza o e-mail")
        @Email(message = "Verifique o e-mail introduzido")
        String email,

        @NotBlank(message = "Introduza a palavra-passe")
        String senha) {
}
