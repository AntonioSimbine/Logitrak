package mz.co.logitrack.security;

import mz.co.logitrack.domain.Utilizador;
import mz.co.logitrack.domain.enums.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Principal autenticado. Implementa UserDetails para ser usado tanto na
 * sessão do painel web como na autenticação stateless por JWT.
 */
public class AuthenticatedUser implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String nome;
    private final String email;
    private final String senhaHash;
    private final boolean ativo;
    private final Role perfil;

    public AuthenticatedUser(Utilizador utilizador) {
        this.id = utilizador.getId();
        this.nome = utilizador.getNome();
        this.email = utilizador.getEmail();
        this.senhaHash = utilizador.getSenhaHash();
        this.ativo = Boolean.TRUE.equals(utilizador.getAtivo());
        this.perfil = utilizador.getPerfil();
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public Role getPerfil() {
        return perfil;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(perfil.getAuthority()));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ativo;
    }
}
