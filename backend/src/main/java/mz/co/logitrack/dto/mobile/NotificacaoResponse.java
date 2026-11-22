package mz.co.logitrack.dto.mobile;

import java.time.LocalDateTime;

/**
 * Contrato da notificação entregue à aplicação do motorista. A origem e a
 * autoria são calculadas no servidor e não podem ser enviadas pelo cliente.
 */
public record NotificacaoResponse(
        Long id,
        String tipo,
        String origem,
        Long autorId,
        String autorNome,
        String autorPerfil,
        String acaoTipo,
        LocalDateTime dataHoraCriacao,
        Long carregamentoId,
        String guiaNumero,
        String titulo,
        String mensagem,
        Boolean lido,
        LocalDateTime dataHoraLeitura
) {
}
