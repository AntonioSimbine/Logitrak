package mz.co.logitrack.dto.mobile;

import mz.co.logitrack.dto.carregamento.CarregamentoResumo;

import java.util.List;

/** Dashboard do motorista (M2). */
public record MobileDashboardResponse(
        Long motoristaId,
        String motoristaNome,
        List<CarregamentoResumo> recentes,
        long total,
        long emTransito,
        long atrasados,
        long concluidos
) {
}
