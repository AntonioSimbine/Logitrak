package mz.co.logitrack.controller.api;

import mz.co.logitrack.service.SlaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Permite forçar uma passagem do motor de SLA (útil em testes e operação). */
@RestController
@RequestMapping({"/api/v1/sla", "/api/sla"})
@PreAuthorize("hasRole('ADMIN')")
public class SlaController {

    private final SlaService slaService;

    public SlaController(SlaService slaService) {
        this.slaService = slaService;
    }

    @PostMapping("/verificar")
    public Map<String, Object> verificar() {
        int novos = slaService.verificarAgora();
        return Map.of("novosAtrasos", novos, "executadoEm", java.time.LocalDateTime.now(),
                "toleranciaMinutos", slaService.toleranciaMinutos());
    }
}
