package mz.co.logitrack.dto.carregamento;

import jakarta.validation.constraints.NotNull;
import mz.co.logitrack.domain.enums.EstadoCarregamento;

/** Alteração de estado efetuada pelo painel web (ex.: concluir). */
public record AtualizarEstadoRequest(
        @NotNull(message = "O estado é obrigatório") EstadoCarregamento estado,
        String motivoRejeicao
) {
}
