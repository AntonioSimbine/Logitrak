package mz.co.logitrack.service;

import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.dto.carregamento.CarregamentoResumo;
import mz.co.logitrack.mapper.CarregamentoMapper;
import mz.co.logitrack.repository.CarregamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Motor de verificação de SLA. Compara o tempo decorrido com o limite da
 * fábrica de origem + tolerância global, muda o estado para ATRASADO, cria o
 * alerta (uma única vez por carregamento) e notifica o painel.
 */
@Service
public class SlaService {

    private static final Logger log = LoggerFactory.getLogger(SlaService.class);

    private final CarregamentoRepository carregamentoRepository;
    private final CarregamentoMapper mapper;
    private final AlertaService alertaService;
    private final NotificacaoService notificacaoService;
    private final ParametroService parametroService;

    public SlaService(CarregamentoRepository carregamentoRepository,
                      CarregamentoMapper mapper,
                      AlertaService alertaService,
                      NotificacaoService notificacaoService,
                      ParametroService parametroService) {
        this.carregamentoRepository = carregamentoRepository;
        this.mapper = mapper;
        this.alertaService = alertaService;
        this.notificacaoService = notificacaoService;
        this.parametroService = parametroService;
    }

    /** Executa uma passagem do motor. Devolve o número de novos atrasos. */
    @Transactional
    public int verificarAgora() {
        List<Carregamento> emTransito = carregamentoRepository
                .findByEstadoOrderByDataHoraInicioAsc(EstadoCarregamento.EM_TRANSITO);
        int novosAtrasos = 0;

        for (Carregamento candidato : emTransito) {
            // Releitura com bloqueio: duas execuções concorrentes não podem
            // transformar o mesmo carregamento nem criar alertas duplicados.
            Carregamento c = carregamentoRepository.findByIdForUpdate(candidato.getId())
                    .orElse(null);
            if (c == null || c.getEstado() != EstadoCarregamento.EM_TRANSITO) {
                continue;
            }
            LocalDateTime limite = mapper.horaLimite(c);
            if (limite == null) {
                continue;
            }
            LocalDateTime agora = LocalDateTime.now();
            if (!agora.isAfter(limite)) {
                continue;
            }
            long minutos = Math.max(1,
                    Duration.between(limite, agora).toMinutes());

            c.setEstado(EstadoCarregamento.ATRASADO);
            Carregamento guardado = carregamentoRepository.save(c);

            var alerta = alertaService.criarAlerta(guardado,
                    (int) Math.min(minutos, Integer.MAX_VALUE));

            CarregamentoResumo resumo = mapper.toResumo(guardado);
            notificacaoService.publicarCarregamento(resumo);
            if (alerta != null) {
                novosAtrasos++;
            }

            log.info("SLA excedido: carregamento={} guia={} minutos={}",
                    guardado.getId(), guardado.getGuiaNumero(), minutos);
        }

        if (novosAtrasos > 0) {
            log.info("Motor SLA: {} novo(s) carregamento(s) em atraso", novosAtrasos);
        }
        return novosAtrasos;
    }

    public int toleranciaMinutos() {
        return parametroService.obterInt(ParametroService.TOLERANCIA_ATRASO_MINUTOS);
    }
}
