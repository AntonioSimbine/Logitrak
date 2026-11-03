package mz.co.logitrack.dto.dashboard;

import mz.co.logitrack.dto.carregamento.CarregamentoResumo;

import java.time.LocalDateTime;
import java.util.List;

/** Payload completo do dashboard W2. */
public record DashboardResponse(
        KpiResumo kpis,
        List<CarregamentoResumo> recentes,
        List<PontoMapa> veiculos,
        List<FabricaMapa> fabricas,
        List<AlertaResponse> alertas,
        long alertasNaoLidos,
        LocalDateTime atualizadoEm
) {
}
