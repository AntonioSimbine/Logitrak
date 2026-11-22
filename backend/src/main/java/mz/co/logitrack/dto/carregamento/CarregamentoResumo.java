package mz.co.logitrack.dto.carregamento;

import mz.co.logitrack.domain.enums.EstadoCarregamento;

import java.time.LocalDateTime;

/** Dados resumidos usados em listas, tabelas e respostas WebSocket. */
public record CarregamentoResumo(
        Long id,
        String uuidLocal,
        String guiaNumero,
        String tipoCarga,
        double quantidadeToneladas,
        EstadoCarregamento estado,
        String estadoEtiqueta,
        LocalDateTime dataHoraInicio,
        LocalDateTime dataHoraFim,
        String motivoRejeicao,
        Long fabricaId,
        String fabricaNome,
        Long veiculoId,
        String veiculoMatricula,
        Long motoristaId,
        String motoristaNome,
        long atrasoMinutos,
        boolean atrasado,
        boolean temDocumento,
        FasesCarregamento fases
) {
}
