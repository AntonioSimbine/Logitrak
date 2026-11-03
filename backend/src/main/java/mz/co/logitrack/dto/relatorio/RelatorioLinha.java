package mz.co.logitrack.dto.relatorio;

import java.time.LocalDateTime;

/** Linha agregada do relatório operacional. */
public record RelatorioLinha(
        Long id,
        String guiaNumero,
        String fabrica,
        String veiculo,
        String motorista,
        String tipoCarga,
        double toneladas,
        String estado,
        LocalDateTime dataHoraInicio,
        LocalDateTime dataHoraFim,
        long atrasoMinutos,
        String motivoRejeicao
) {
}
