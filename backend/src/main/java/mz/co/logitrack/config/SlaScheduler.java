package mz.co.logitrack.config;

import mz.co.logitrack.service.ParametroService;
import mz.co.logitrack.service.SlaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Agendador do motor SLA. O tick é de 30 s, mas a execução efetiva respeita o
 * parâmetro global INTERVALO_VERIFICACAO_SLA_MINUTOS, alterável no W10 sem
 * reiniciar a aplicação.
 */
@Component
public class SlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaScheduler.class);

    private final SlaService slaService;
    private final ParametroService parametroService;
    private LocalDateTime ultimaExecucao;

    public SlaScheduler(SlaService slaService, ParametroService parametroService) {
        this.slaService = slaService;
        this.parametroService = parametroService;
    }

    @Scheduled(fixedDelay = 30_000, initialDelayString = "${logitrack.sla.atraso-inicial-ms:12000}")
    public void tick() {
        try {
            int intervaloMinutos = Math.max(1, parametroService.obterInt(
                    ParametroService.INTERVALO_VERIFICACAO_SLA_MINUTOS));
            LocalDateTime agora = LocalDateTime.now();
            if (ultimaExecucao != null
                    && Duration.between(ultimaExecucao, agora).toMinutes() < intervaloMinutos) {
                return;
            }
            ultimaExecucao = agora;
            int atrasos = slaService.verificarAgora();
            if (atrasos > 0) {
                log.info("Verificação SLA concluída: {} novo(s) alerta(s)", atrasos);
            }
        } catch (Exception ex) {
            log.error("Falha na verificação automática de SLA", ex);
        }
    }
}
