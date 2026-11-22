package mz.co.logitrack.service;

import mz.co.logitrack.domain.Notificacao;
import mz.co.logitrack.domain.enums.OrigemNotificacao;
import mz.co.logitrack.domain.enums.TipoNotificacao;
import mz.co.logitrack.dto.carregamento.CarregamentoResumo;
import mz.co.logitrack.dto.dashboard.AlertaResponse;
import mz.co.logitrack.dto.dashboard.PontoMapa;
import mz.co.logitrack.dto.mobile.NotificacaoResponse;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.repository.NotificacaoRepository;
import mz.co.logitrack.security.AuthenticatedUser;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Publicação de eventos no canal STOMP (padrão Observer) e registo persistente
 * das notificações entregues aos utilizadores. A origem e a autoria são
 * definidas aqui, no servidor; o cliente apenas consulta e marca como lida.
 */
@Service
public class NotificacaoService {

    public static final String TOPICO_ALERTAS = "/topic/alertas-atraso";
    public static final String TOPICO_CARREGAMENTOS = "/topic/carregamentos";
    public static final String TOPICO_MAPA = "/topic/mapa";
    public static final String TOPICO_CONTADOR_ALERTAS = "/topic/alertas-contador";
    public static final String TOPICO_NOTIFICACOES = "/topic/notificacoes";

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificacaoRepository notificacaoRepository;

    public NotificacaoService(SimpMessagingTemplate messagingTemplate,
                              NotificacaoRepository notificacaoRepository) {
        this.messagingTemplate = messagingTemplate;
        this.notificacaoRepository = notificacaoRepository;
    }

    public void publicarAlerta(AlertaResponse alerta) {
        messagingTemplate.convertAndSend(TOPICO_ALERTAS, alerta);
    }

    /** Contador de alertas não lidos, para manter o painel sincronizado. */
    public void publicarContadorAlertas(long total) {
        messagingTemplate.convertAndSend(TOPICO_CONTADOR_ALERTAS, total);
    }

    public void publicarCarregamento(CarregamentoResumo carregamento) {
        messagingTemplate.convertAndSend(TOPICO_CARREGAMENTOS, carregamento);
    }

    public void publicarMapa(List<PontoMapa> pontos) {
        messagingTemplate.convertAndSend(TOPICO_MAPA, pontos);
    }

    public void publicarNotificacao(NotificacaoResponse notificacao) {
        messagingTemplate.convertAndSend(TOPICO_NOTIFICACOES, notificacao);
    }

    /** Evento gerado automaticamente: a origem é sempre SISTEMA. */
    @Transactional
    public NotificacaoResponse registarSistema(TipoNotificacao tipo, Long destinatarioId,
                                               Long carregamentoId, String guiaNumero,
                                               String titulo, String mensagem, String acaoTipo) {
        return registar(null, OrigemNotificacao.SISTEMA, tipo, destinatarioId, carregamentoId,
                guiaNumero, titulo, mensagem, acaoTipo);
    }

    /**
     * Acção manual: a identidade do autor vem exclusivamente do principal
     * autenticado. Nenhum campo enviado pelo cliente é aceite como remetente.
     */
    @Transactional
    public NotificacaoResponse registarAccaoManual(AuthenticatedUser autor,
                                                   TipoNotificacao tipo, Long destinatarioId,
                                                   Long carregamentoId, String guiaNumero,
                                                   String titulo, String mensagem, String acaoTipo) {
        return registar(autor, OrigemNotificacao.de(autor), tipo, destinatarioId, carregamentoId,
                guiaNumero, titulo, mensagem, acaoTipo);
    }

    private NotificacaoResponse registar(AuthenticatedUser autor, OrigemNotificacao origem,
                                         TipoNotificacao tipo, Long destinatarioId,
                                         Long carregamentoId, String guiaNumero, String titulo,
                                         String mensagem, String acaoTipo) {
        if (destinatarioId == null) {
            return null;
        }
        Notificacao notificacao = new Notificacao();
        notificacao.setDestinatarioId(destinatarioId);
        notificacao.setTipo(tipo);
        notificacao.setOrigem(origem);
        if (autor != null) {
            notificacao.setAutorId(autor.getId());
            notificacao.setAutorNome(autor.getNome());
            notificacao.setAutorPerfil(autor.getPerfil() != null
                    ? autor.getPerfil().name() : null);
        }
        notificacao.setAcaoTipo(acaoTipo);
        notificacao.setDataHoraCriacao(LocalDateTime.now());
        notificacao.setCarregamentoId(carregamentoId);
        notificacao.setGuiaNumero(guiaNumero);
        notificacao.setTitulo(titulo);
        notificacao.setMensagem(mensagem);
        notificacao.setLido(Boolean.FALSE);

        Notificacao guardada = notificacaoRepository.save(notificacao);
        NotificacaoResponse resposta = toResponse(guardada);
        publicarNotificacao(resposta);
        return resposta;
    }

    @Transactional(readOnly = true)
    public List<NotificacaoResponse> listarPara(Long destinatarioId) {
        return notificacaoRepository
                .findTop100ByDestinatarioIdOrderByDataHoraCriacaoDesc(destinatarioId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(Long destinatarioId) {
        return notificacaoRepository.countByDestinatarioIdAndLidoFalse(destinatarioId);
    }

    @Transactional
    public NotificacaoResponse marcarLida(Long id, Long destinatarioId, boolean lido) {
        Notificacao notificacao = notificacaoRepository
                .findByIdAndDestinatarioId(id, destinatarioId)
                .orElseThrow(() -> ApiException.notFound("Notificação não encontrada"));
        notificacao.setLido(lido);
        notificacao.setDataHoraLeitura(lido ? LocalDateTime.now() : null);
        Notificacao guardada = notificacaoRepository.save(notificacao);
        NotificacaoResponse resposta = toResponse(guardada);
        publicarNotificacao(resposta);
        return resposta;
    }

    @Transactional
    public int marcarTodasLidas(Long destinatarioId) {
        List<Notificacao> pendentes = notificacaoRepository
                .findByDestinatarioIdAndLidoFalse(destinatarioId);
        LocalDateTime agora = LocalDateTime.now();
        pendentes.forEach(n -> {
            n.setLido(Boolean.TRUE);
            n.setDataHoraLeitura(agora);
        });
        notificacaoRepository.saveAll(pendentes);
        return pendentes.size();
    }

    private NotificacaoResponse toResponse(Notificacao n) {
        return new NotificacaoResponse(
                n.getId(),
                n.getTipo() != null ? n.getTipo().name() : null,
                n.getOrigem() != null ? n.getOrigem().name() : OrigemNotificacao.DESCONHECIDO.name(),
                n.getAutorId(),
                n.getAutorNome(),
                n.getAutorPerfil(),
                n.getAcaoTipo(),
                n.getDataHoraCriacao(),
                n.getCarregamentoId(),
                n.getGuiaNumero(),
                n.getTitulo(),
                n.getMensagem(),
                Boolean.TRUE.equals(n.getLido()),
                n.getDataHoraLeitura()
        );
    }
}
