package mz.co.logitrack.dto.dashboard;

import java.time.LocalDateTime;

/** Posição mais recente de um veículo em rota. */
public record PontoMapa(
        Long carregamentoId,
        String guiaNumero,
        Long veiculoId,
        String matricula,
        String motorista,
        String fabrica,
        String estado,
        double latitude,
        double longitude,
        double precisaoMetros,
        LocalDateTime timestamp
) {
}
