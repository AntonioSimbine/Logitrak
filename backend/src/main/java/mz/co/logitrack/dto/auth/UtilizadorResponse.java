package mz.co.logitrack.dto.auth;

import mz.co.logitrack.domain.enums.Role;

import java.time.LocalDateTime;

/** Representação pública do utilizador autenticado. */
public record UtilizadorResponse(Long id, String nome, String email, Role perfil,
                                 String perfilEtiqueta, Boolean ativo, LocalDateTime dataCriacao) {
}
