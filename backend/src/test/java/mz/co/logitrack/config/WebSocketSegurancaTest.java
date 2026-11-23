package mz.co.logitrack.config;

import mz.co.logitrack.domain.Utilizador;
import mz.co.logitrack.domain.enums.Role;
import mz.co.logitrack.dto.mobile.NotificacaoResponse;
import mz.co.logitrack.repository.NotificacaoRepository;
import mz.co.logitrack.security.AuthenticatedUser;
import mz.co.logitrack.security.CustomUserDetailsService;
import mz.co.logitrack.security.JwtService;
import mz.co.logitrack.service.NotificacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Regressões da entrega privada e da autorização STOMP (Fase 1). */
class WebSocketSegurancaTest {

    private static final String EMAIL = "motorista@logitrack.co.mz";

    private JwtService jwtService;
    private CustomUserDetailsService userDetailsService;
    private WebSocketAuthInterceptor interceptor;
    private MessageChannel canal;

    @BeforeEach
    void configurar() {
        jwtService = mock(JwtService.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        interceptor = new WebSocketAuthInterceptor(jwtService, userDetailsService);
        canal = mock(MessageChannel.class);
    }

    @Test
    @DisplayName("CONNECT sem token é recusado")
    void connectSemToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        assertThatThrownBy(() -> interceptor.preSend(mensagem(accessor), canal))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Token JWT em falta");
    }

    @Test
    @DisplayName("Motorista A não subscreve as notificações do Motorista B")
    void motoristaNaoSubscereCanalDeOutroMotorista() {
        Authentication motoristaA = autenticacao(1L, Role.MOTORISTA);
        assertThatThrownBy(() -> interceptor.preSend(
                mensagem(StompCommand.SUBSCRIBE, "/topic/notificacoes.2", motoristaA), canal))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Subscrição não autorizada");
    }

    @Test
    @DisplayName("Motorista subscreve apenas o seu próprio canal privado")
    void motoristaSubscereProprioCanal() {
        Authentication motoristaA = autenticacao(1L, Role.MOTORISTA);
        assertThatCode(() -> interceptor.preSend(
                mensagem(StompCommand.SUBSCRIBE, "/topic/notificacoes.1", motoristaA), canal))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Motorista não tem acesso aos canais globais da central")
    void motoristaSemCanaisGlobais() {
        Authentication motorista = autenticacao(1L, Role.MOTORISTA);
        for (String destino : canaisCentrais()) {
            assertThatThrownBy(() -> interceptor.preSend(
                    mensagem(StompCommand.SUBSCRIBE, destino, motorista), canal))
                    .as(destino)
                    .isInstanceOf(MessagingException.class)
                    .hasMessageContaining("Subscrição não autorizada");
        }
    }

    @Test
    @DisplayName("Administrador e Operador subscrevem os canais permitidos")
    void centralSubscereCanaisPermitidos() {
        for (Authentication central : new Authentication[]{
                autenticacao(10L, Role.ADMINISTRADOR),
                autenticacao(11L, Role.OPERADOR_CENTRAL)}) {
            for (String destino : canaisCentrais()) {
                assertThatCode(() -> interceptor.preSend(
                        mensagem(StompCommand.SUBSCRIBE, destino, central), canal))
                        .as(destino)
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    @DisplayName("Cliente não pode publicar mensagens")
    void clienteNaoPodePublicar() {
        Authentication motorista = autenticacao(1L, Role.MOTORISTA);
        assertThatThrownBy(() -> interceptor.preSend(
                mensagem(StompCommand.SEND, "/topic/carregamentos", motorista), canal))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Publicação de mensagens não permitida");
    }

    @Test
    @DisplayName("Notificação é publicada apenas no canal do destinatário")
    void notificacaoPublicadaPorDestinatario() {
        var template = mock(org.springframework.messaging.simp.SimpMessagingTemplate.class);
        var servico = new NotificacaoService(template, mock(NotificacaoRepository.class));
        NotificacaoResponse resposta = mock(NotificacaoResponse.class);

        servico.publicarNotificacao(77L, resposta);

        verify(template).convertAndSend(
                (String) eq("/topic/notificacoes.77"), (Object) eq(resposta));
        verify(template, org.mockito.Mockito.never())
                .convertAndSend((String) eq("/topic/notificacoes"), any(Object.class));
    }

    private static String[] canaisCentrais() {
        return new String[]{"/topic/alertas-atraso", "/topic/alertas-contador",
                "/topic/carregamentos", "/topic/mapa"};
    }

    private Authentication autenticacao(Long id, Role perfil) {
        Utilizador utilizador = new Utilizador("Teste", EMAIL, "hash", perfil);
        utilizador.setId(id);
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(utilizador), null,
                new AuthenticatedUser(utilizador).getAuthorities());
    }

    private Message<byte[]> mensagem(StompCommand comando, String destino,
                                     Authentication autenticacao) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(comando);
        accessor.setDestination(destino);
        accessor.setUser(autenticacao);
        return mensagem(accessor);
    }

    private Message<byte[]> mensagem(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
