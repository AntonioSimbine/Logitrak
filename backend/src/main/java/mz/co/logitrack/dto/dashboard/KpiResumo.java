package mz.co.logitrack.dto.dashboard;

/** Cartões KPI do topo do W2. */
public record KpiResumo(
        long emTransito,
        long atrasados,
        long concluidosHoje,
        double tempoMedioEsperaHoras
) {
}
