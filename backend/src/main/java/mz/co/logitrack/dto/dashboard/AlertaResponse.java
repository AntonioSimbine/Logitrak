package mz.co.logitrack.dto.dashboard;

import java.time.LocalDateTime;

/** Alerta de SLA enviado por WebSocket e listado no W2/W4. */
public record AlertaResponse(
        Long id,
        Long carregamentoId,
        String guiaNumero,
        String fabrica,
        String motorista,
        String veiculo,
        LocalDateTime horaLimite,
        Integer minutosExcedidos,
        String mensagem,
        Boolean lido,
        LocalDateTime dataHoraEmissao,
        Long tratadoPorId,
        String tratadoPorNome,
        String tratadoPorPerfil,
        LocalDateTime dataHoraTratamento,
        Long atrasoAtualMinutos
) {
}
