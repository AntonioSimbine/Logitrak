package mz.co.logitrack.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Emissão e validação de tokens JWT assinados (HMAC-SHA256).
 * O token transporta o ID, e-mail e perfil do utilizador.
 */
@Service
public class JwtService {

    @Value("${logitrack.jwt.secret}")
    private String segredo;

    @Value("${logitrack.jwt.expiration-minutes:720}")
    private long expiracaoMinutos;

    private SecretKey chave;

    @PostConstruct
    void init() {
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET é obrigatório e deve ter pelo menos 32 caracteres.");
        }
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
    }

    public String gerarToken(AuthenticatedUser utilizador) {
        Instant agora = Instant.now();
        Instant expiracao = agora.plus(expiracaoMinutos, ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(utilizador.getUsername())
                .claim("uid", utilizador.getId())
                .claim("nome", utilizador.getNome())
                .claim("perfil", utilizador.getPerfil().name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiracao))
                .signWith(chave)
                .compact();
    }

    public Claims extrairClaims(String token) {
        return Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extrairEmail(String token) {
        return extrairClaims(token).getSubject();
    }

    public long getExpiracaoSegundos() {
        return expiracaoMinutos * 60;
    }
}
