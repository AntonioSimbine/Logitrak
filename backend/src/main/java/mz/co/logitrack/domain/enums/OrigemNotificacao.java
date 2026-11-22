package mz.co.logitrack.domain.enums;

import mz.co.logitrack.security.AuthenticatedUser;

/**
 * Origem verdadeira de uma notificação. Nunca é escolhida pelo cliente: é
 * deduzida no servidor a partir do autor autenticado ou do componente que
 * gerou o evento.
 */
public enum OrigemNotificacao {

    SISTEMA,
    OPERADOR,
    ADMINISTRADOR,
    /** Registos antigos sem informação de autor. */
    DESCONHECIDO;

    /** O servidor é a única fonte desta decisão. */
    public static OrigemNotificacao de(AuthenticatedUser autor) {
        if (autor == null || autor.getPerfil() == null) {
            return DESCONHECIDO;
        }
        return switch (autor.getPerfil()) {
            case ADMINISTRADOR -> ADMINISTRADOR;
            case OPERADOR_CENTRAL -> OPERADOR;
            case MOTORISTA -> DESCONHECIDO;
        };
    }
}
