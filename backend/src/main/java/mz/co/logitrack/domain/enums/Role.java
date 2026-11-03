package mz.co.logitrack.domain.enums;

/**
 * Perfis de acesso do sistema. O nome técnico da autoridade Spring Security é
 * derivado do perfil para cumprir o requisito RBAC (ROLE_ADMIN, ROLE_OPERADOR,
 * ROLE_MOTORISTA).
 */
public enum Role {

    ADMINISTRADOR("ROLE_ADMIN", "Administrador"),
    OPERADOR_CENTRAL("ROLE_OPERADOR", "Operador da Central"),
    MOTORISTA("ROLE_MOTORISTA", "Motorista");

    private final String authority;
    private final String etiqueta;

    Role(String authority, String etiqueta) {
        this.authority = authority;
        this.etiqueta = etiqueta;
    }

    public String getAuthority() {
        return authority;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public boolean isAdmin() {
        return this == ADMINISTRADOR;
    }

    public boolean isOperador() {
        return this == OPERADOR_CENTRAL;
    }

    public boolean isMotorista() {
        return this == MOTORISTA;
    }
}
