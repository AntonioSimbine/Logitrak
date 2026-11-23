package mz.co.logitrack.config;

import mz.co.logitrack.security.AuthenticatedUser;
import mz.co.logitrack.security.CustomUserDetailsService;
import mz.co.logitrack.security.JwtService;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Autentica a frame STOMP CONNECT com o mesmo JWT da API. O JavaScript do
 * painel envia o token no cabeçalho nativo Authorization.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public WebSocketAuthInterceptor(JwtService jwtService,
                                    CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand comando = accessor.getCommand();
        if (StompCommand.CONNECT.equals(comando)) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new MessagingException("Token JWT em falta no canal WebSocket");
            }
            try {
                String email = jwtService.extrairEmail(header.substring(7).trim());
                UserDetails utilizador = userDetailsService.loadUserByUsername(email);
                if (!utilizador.isEnabled()) {
                    throw new MessagingException("Utilizador inativo");
                }
                accessor.setUser(new UsernamePasswordAuthenticationToken(
                        utilizador, null, utilizador.getAuthorities()));
            } catch (Exception ex) {
                throw new MessagingException("Token JWT inválido no canal WebSocket", ex);
            }
            return message;
        }

        if (StompCommand.DISCONNECT.equals(comando) || StompCommand.UNSUBSCRIBE.equals(comando)) {
            return message;
        }

        Authentication autenticacao = accessor.getUser() instanceof Authentication auth
                ? auth : null;
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof AuthenticatedUser)) {
            throw new MessagingException("Sessão WebSocket não autenticada");
        }

        // Em STOMP, a publicação do cliente é SEND. Não existem @MessageMapping
        // nem tópicos de aplicação: todas as decisões pertencem ao servidor.
        if (StompCommand.SEND.equals(comando)) {
            throw new MessagingException("Publicação de mensagens não permitida");
        }

        if (StompCommand.SUBSCRIBE.equals(comando)) {
            String destino = accessor.getDestination();
            if (destino == null || !subscricaoPermitida(destino, autenticacao)) {
                throw new MessagingException("Subscrição não autorizada");
            }
        }
        return message;
    }

    private boolean subscricaoPermitida(String destino, Authentication autenticacao) {
        if (destino.contains("*")) {
            return false;
        }
        Long id = ((AuthenticatedUser) autenticacao.getPrincipal()).getId();
        if (id != null && destino.equals("/topic/notificacoes." + id)) {
            return true;
        }
        if (CANAIS_CENTRAL.contains(destino)) {
            return autenticacao.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> "ROLE_ADMIN".equals(a) || "ROLE_OPERADOR".equals(a));
        }
        return false;
    }

    private static final Set<String> CANAIS_CENTRAL = Set.of(
            "/topic/alertas-atraso",
            "/topic/alertas-contador",
            "/topic/carregamentos",
            "/topic/mapa");
}
