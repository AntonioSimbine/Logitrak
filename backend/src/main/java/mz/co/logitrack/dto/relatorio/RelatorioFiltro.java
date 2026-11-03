package mz.co.logitrack.dto.relatorio;

import mz.co.logitrack.domain.enums.EstadoCarregamento;

import java.time.LocalDate;

/** Filtros avançados do W5. */
public record RelatorioFiltro(
        LocalDate de,
        LocalDate ate,
        Long fabricaId,
        Long veiculoId,
        Long motoristaId,
        EstadoCarregamento estado
) {
    public RelatorioFiltro {
        if (de != null && ate != null && de.isAfter(ate)) {
            LocalDate tmp = de;
            de = ate;
            ate = tmp;
        }
    }
}
