package mz.co.logitrack.controller.api;

import mz.co.logitrack.dto.dashboard.DashboardResponse;
import mz.co.logitrack.dto.dashboard.KpiResponse;
import mz.co.logitrack.dto.dashboard.KpiResumo;
import mz.co.logitrack.dto.dashboard.PontoMapa;
import mz.co.logitrack.service.CarregamentoService;
import mz.co.logitrack.service.DashboardService;
import mz.co.logitrack.service.KpiService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Dashboard, mapa e indicadores (W2 e W6). */
@RestController
@RequestMapping({"/api/v1", "/api"})
@PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
public class DashboardController {

    private final DashboardService dashboardService;
    private final KpiService kpiService;
    private final CarregamentoService carregamentoService;

    public DashboardController(DashboardService dashboardService,
                               KpiService kpiService,
                               CarregamentoService carregamentoService) {
        this.dashboardService = dashboardService;
        this.kpiService = kpiService;
        this.carregamentoService = carregamentoService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return dashboardService.obter();
    }

    @GetMapping("/kpis")
    public KpiResponse kpis() {
        return kpiService.kpis();
    }

    @GetMapping("/kpis/resumo")
    public KpiResumo resumo() {
        return kpiService.resumo();
    }

    @GetMapping("/mapa")
    public List<PontoMapa> mapa() {
        return carregamentoService.ultimosPontosMapa();
    }
}
