package mz.co.logitrack.service;

import mz.co.logitrack.domain.Utilizador;
import mz.co.logitrack.dto.auth.AuthResponse;
import mz.co.logitrack.dto.auth.LoginRequest;
import mz.co.logitrack.dto.auth.UtilizadorResponse;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.repository.UtilizadorRepository;
import mz.co.logitrack.security.AuthenticatedUser;
import mz.co.logitrack.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Autenticação unificada das três categorias de utilizador (CU002). */
@Service
public class AuthService {

    private static final String CREDENCIAIS_INVALIDAS = "Credenciais inválidas";

    private final AuthenticationManager authenticationManager;
    private final UtilizadorRepository utilizadorRepository;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager,
                       UtilizadorRepository utilizadorRepository,
                       JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.utilizadorRepository = utilizadorRepository;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest pedido) {
        try {
            Authentication autenticacao = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            pedido.email().trim(), pedido.senha()));
            AuthenticatedUser principal = (AuthenticatedUser) autenticacao.getPrincipal();
            Utilizador utilizador = utilizadorRepository.findByEmailIgnoreCase(principal.getEmail())
                    .orElseThrow(() -> ApiException.unauthorized(CREDENCIAIS_INVALIDAS));
            String token = jwtService.gerarToken(principal);
            return new AuthResponse(token, "Bearer", jwtService.getExpiracaoSegundos(),
                    toResponse(utilizador));
        } catch (AuthenticationException ex) {
            // Mensagem unificada: nunca revelar se o e-mail existe ou não.
            throw ApiException.unauthorized(CREDENCIAIS_INVALIDAS);
        }
    }

    @Transactional(readOnly = true)
    public UtilizadorResponse perfil(AuthenticatedUser principal) {
        Utilizador utilizador = utilizadorRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.unauthorized(CREDENCIAIS_INVALIDAS));
        return toResponse(utilizador);
    }

    public UtilizadorResponse toResponse(Utilizador u) {
        return new UtilizadorResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(),
                u.getPerfil().getEtiqueta(), u.getAtivo(), u.getDataCriacao());
    }
}
