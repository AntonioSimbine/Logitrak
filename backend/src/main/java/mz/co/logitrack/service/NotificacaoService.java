package mz.co.logitrack.service;

import mz.co.logitrack.dto.carregamento.CarregamentoResumo;
import mz.co.logitrack.dto.dashboard.AlertaResponse;
import mz.co.logitrack.dto.dashboard.PontoMapa;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Publicação de eventos no canal STOMP (padrão Observer). O painel web
 * subscreve os tópicos sem polling.
 */
@Service
public class NotificacaoService {

    public static final String TOPICO_ALERTAS = "/topic/alertas-atraso";
    public static final String TOPICO_CARREGAMENTOS = "/topic/carregamentos";
    public static final String TOPICO_MAPA = "/topic/mapa";

    private final SimpMessagingTemplate messagingTemplate;

    public NotificacaoService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publicarAlerta(AlertaResponse alerta) {
        messagingTemplate.convertAndSend(TOPICO_ALERTAS, alerta);
    }

    public void publicarCarregamento(CarregamentoResumo carregamento) {
        messagingTemplate.convertAndSend(TOPICO_CARREGAMENTOS, carregamento);
    }

    public void publicarMapa(List<PontoMapa> pontos) {
        messagingTemplate.convertAndSend(TOPICO_MAPA, pontos);
    }
}
