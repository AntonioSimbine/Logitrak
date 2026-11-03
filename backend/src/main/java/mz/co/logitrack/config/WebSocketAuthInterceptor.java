package mz.co.logitrack.config;

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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

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

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
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
        }
        return message;
    }
}
