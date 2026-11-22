package mz.co.logitrack.service;

import mz.co.logitrack.domain.AlertaAtraso;
import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.enums.OrigemNotificacao;
import mz.co.logitrack.domain.enums.TipoNotificacao;
import mz.co.logitrack.dto.dashboard.AlertaResponse;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.mapper.CarregamentoMapper;
import mz.co.logitrack.repository.AlertaAtrasoRepository;
import mz.co.logitrack.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Gestão dos alertas de SLA (W4) e notificação em tempo real. */
@Service
public class AlertaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);

    private final AlertaAtrasoRepository alertaRepository;
    private final CarregamentoMapper mapper;
    private final NotificacaoService notificacaoService;

    public AlertaService(AlertaAtrasoRepository alertaRepository,
                         CarregamentoMapper mapper,
                         NotificacaoService notificacaoService) {
        this.alertaRepository = alertaRepository;
        this.mapper = mapper;
        this.notificacaoService = notificacaoService;
    }

    @Transactional(readOnly = true)
    public Page<AlertaResponse> listar(Pageable pageable) {
        return alertaRepository.findAllByOrderByDataHoraEmissaoDesc(pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<AlertaResponse> naoLidos() {
        return alertaRepository.findByLidoFalseOrderByDataHoraEmissaoDesc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertaResponse> porCarregamento(Long carregamentoId) {
        return alertaRepository.findByCarregamentoIdOrderByDataHoraEmissaoDesc(carregamentoId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long contarNaoLidos() {
        return alertaRepository.countByLidoFalse();
    }

    @Transactional
    public AlertaResponse marcarLido(Long id, boolean lido) {
        return marcarLido(id, lido, null);
    }

    /** Regista quem tratou o alerta; a identidade vem sempre da autenticação. */
    @Transactional
    public AlertaResponse marcarLido(Long id, boolean lido, AuthenticatedUser principal) {
        AlertaAtraso alerta = alertaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(
                        "Não foi possível encontrar o alerta. Confirme os dados e tente novamente."));
        alerta.setLido(lido);
        if (lido) {
            alerta.setTratadoPorId(principal != null ? principal.getId() : null);
            alerta.setTratadoPorNome(principal != null ? principal.getNome() : null);
            alerta.setTratadoPorPerfil(principal != null && principal.getPerfil() != null
                    ? principal.getPerfil().name()
                    : OrigemNotificacao.DESCONHECIDO.name());
            alerta.setDataHoraTratamento(LocalDateTime.now());
        } else {
            alerta.setTratadoPorId(null);
            alerta.setTratadoPorNome(null);
            alerta.setTratadoPorPerfil(null);
            alerta.setDataHoraTratamento(null);
        }
        AlertaAtraso guardado = alertaRepository.save(alerta);
        notificacaoService.publicarContadorAlertas(alertaRepository.countByLidoFalse());
        return toResponse(guardado);
    }

    /**
     * Cria no máximo um alerta por carregamento (RN05), mesmo após leitura,
     * para que verificações repetidas não dupliquem notificações.
     */
    @Transactional
    public AlertaResponse criarAlerta(Carregamento carregamento, int minutosExcedidos) {
        if (alertaRepository.existsByCarregamentoId(carregamento.getId())) {
            return null;
        }
        String fabrica = carregamento.getFabrica() != null
                ? carregamento.getFabrica().getNome() : "origem";
        String mensagem = String.format(
                "O carregamento %s (%s) ultrapassou o prazo definido para a viagem por %d minutos. "
                        + "Acção necessária: continue a viagem e avise a central de qualquer paragem.",
                carregamento.getGuiaNumero(), fabrica, minutosExcedidos);

        AlertaAtraso alerta = new AlertaAtraso(LocalDateTime.now(), minutosExcedidos,
                mensagem, carregamento);
        AlertaAtraso guardado = alertaRepository.save(alerta);
        AlertaResponse resposta = toResponse(guardado);
        notificacaoService.publicarAlerta(resposta);
        notificacaoService.publicarContadorAlertas(alertaRepository.countByLidoFalse());

        // Atraso é detectado pelo motor de SLA: origem SISTEMA, nunca Administração.
        Long destinatario = carregamento.getMotorista() != null
                ? carregamento.getMotorista().getId() : null;
        if (destinatario != null) {
            notificacaoService.registarSistema(
                    TipoNotificacao.ATRASO, destinatario, carregamento.getId(),
                    carregamento.getGuiaNumero(), "Carregamento atrasado", mensagem,
                    "DETECAO_AUTOMATICA_SLA");
        }
        log.info("Alerta de SLA criado: carregamento={} minutos={}", carregamento.getId(),
                minutosExcedidos);
        return resposta;
    }

    public AlertaResponse toResponse(AlertaAtraso alerta) {
        Carregamento c = alerta.getCarregamento();
        return new AlertaResponse(
                alerta.getId(),
                c != null ? c.getId() : null,
                c != null ? c.getGuiaNumero() : null,
                c != null && c.getFabrica() != null ? c.getFabrica().getNome() : null,
                c != null && c.getMotorista() != null ? c.getMotorista().getNome() : null,
                c != null && c.getVeiculo() != null ? c.getVeiculo().getMatricula() : null,
                c != null ? mapper.horaLimite(c) : null,
                alerta.getMinutosExcedidos(),
                alerta.getMensagem(),
                alerta.getLido(),
                alerta.getDataHoraEmissao(),
                alerta.getTratadoPorId(),
                alerta.getTratadoPorNome(),
                alerta.getTratadoPorPerfil(),
                alerta.getDataHoraTratamento()
        );
    }
}
