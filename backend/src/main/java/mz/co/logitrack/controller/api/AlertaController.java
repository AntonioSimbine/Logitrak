package mz.co.logitrack.controller.api;

import jakarta.validation.Valid;
import mz.co.logitrack.dto.common.PaginaResponse;
import mz.co.logitrack.dto.dashboard.AlertaResponse;
import mz.co.logitrack.dto.dashboard.MarcarLidoRequest;
import mz.co.logitrack.service.AlertaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Alertas de SLA (W4 / CU011). */
@RestController
@RequestMapping({"/api/v1/alertas", "/api/alertas"})
@PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
public class AlertaController {

    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @GetMapping
    public PaginaResponse<AlertaResponse> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page),
                Math.min(Math.max(1, size), 200),
                Sort.by(Sort.Direction.DESC, "dataHoraEmissao"));
        return PaginaResponse.de(alertaService.listar(pageable));
    }

    @GetMapping("/nao-lidos")
    public List<AlertaResponse> naoLidos() {
        return alertaService.naoLidos();
    }

    @PatchMapping("/{id}")
    public AlertaResponse marcarLido(@PathVariable Long id,
                                     @Valid @RequestBody MarcarLidoRequest pedido) {
        return alertaService.marcarLido(id, pedido.lido());
    }
}
