package mz.co.logitrack.dto.dashboard;

import java.util.List;

/** Dados dos gráficos do W6. */
public record KpiResponse(
        KpiResumo resumo,
        List<VolumeFabrica> volumePorFabrica,
        List<SerieDia> tempoMedioPorDia,
        CumprimentoPrazos cumprimentoPrazos,
        long totalCarregamentos,
        double totalToneladas,
        Double tempoMedioHoras
) {
    public record VolumeFabrica(String fabrica, double toneladas) {
    }

    public record SerieDia(String dia, Double horas) {
    }

    public record CumprimentoPrazos(long noPrazo, long atrasados, Double percentagem) {
    }
}
