package mz.co.logitrack.dto.auth;

/** Resposta do login: token JWT + dados do utilizador. */
public record AuthResponse(String token, String tipo, long expiresInSegundos,
                           UtilizadorResponse utilizador) {
}
