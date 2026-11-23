package mz.co.logitrack.dto.relatorio;

import java.util.List;

/** Resultado da pré-visualização do relatório (W5). */
public record RelatorioResponse(
        List<RelatorioLinha> linhas,
        ResumoRelatorio resumo,
        RelatorioFiltro filtro
) {
    public record ResumoRelatorio(long total, double toneladas, long concluidos,
                                  long atrasados, Double tempoMedioHoras,
                                  Double esperaMediaHoras, Double carregamentoMedioHoras,
                                  Double transporteMedioHoras, long registosComFases) {
    }
}
