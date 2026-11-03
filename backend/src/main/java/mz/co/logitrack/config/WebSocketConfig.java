package mz.co.logitrack.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

/**
 * Canal de tempo real (STOMP sobre SockJS). O servidor publica:
 * /topic/alertas-atraso, /topic/carregamentos e /topic/mapa.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;
    private final String origensPermitidas;

    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor,
                           @Value("${logitrack.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
                           String origensPermitidas) {
        this.authInterceptor = authInterceptor;
        this.origensPermitidas = origensPermitidas;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origens = Arrays.stream(origensPermitidas.split(","))
                .map(String::trim)
                .filter(valor -> !valor.isEmpty())
                .toArray(String[]::new);

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origens)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
