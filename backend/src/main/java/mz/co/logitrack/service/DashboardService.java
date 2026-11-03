package mz.co.logitrack.service;

import mz.co.logitrack.domain.Fabrica;
import mz.co.logitrack.dto.dashboard.AlertaResponse;
import mz.co.logitrack.dto.dashboard.DashboardResponse;
import mz.co.logitrack.dto.dashboard.FabricaMapa;
import mz.co.logitrack.repository.FabricaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Agrega todos os dados do dashboard W2 numa única chamada. */
@Service
public class DashboardService {

    private final KpiService kpiService;
    private final CarregamentoService carregamentoService;
    private final AlertaService alertaService;
    private final FabricaRepository fabricaRepository;

    public DashboardService(KpiService kpiService,
                            CarregamentoService carregamentoService,
                            AlertaService alertaService,
                            FabricaRepository fabricaRepository) {
        this.kpiService = kpiService;
        this.carregamentoService = carregamentoService;
        this.alertaService = alertaService;
        this.fabricaRepository = fabricaRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse obter() {
        List<AlertaResponse> alertas = alertaService.naoLidos().stream()
                .limit(20).toList();
        List<FabricaMapa> fabricas = fabricaRepository.findByAtivaTrueOrderByNomeAsc().stream()
                .map(this::toFabricaMapa)
                .toList();

        return new DashboardResponse(
                kpiService.resumo(),
                carregamentoService.recentes(),
                carregamentoService.ultimosPontosMapa(),
                fabricas,
                alertas,
                alertaService.contarNaoLidos(),
                LocalDateTime.now()
        );
    }

    private FabricaMapa toFabricaMapa(Fabrica f) {
        return new FabricaMapa(f.getId(), f.getNome(), f.getEndereco(),
                f.getLatitude(), f.getLongitude(),
                f.getRaioGeofenceMetros(), Boolean.TRUE.equals(f.getAtiva()));
    }
}
