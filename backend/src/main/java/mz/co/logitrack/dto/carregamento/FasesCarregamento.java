package mz.co.logitrack.dto.carregamento;

import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.exception.ApiException;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Separação dos três momentos operacionais de um carregamento:
 * espera na fábrica, carregamento e transporte.
 *
 * <p>Registos anteriores à implementação não tinham estes instantes. Nesses
 * casos as durações são {@code null} e a interface apresenta "sem dados" —
 * nunca se inventa um tempo a partir da data de registo.</p>
 */
public record FasesCarregamento(
        LocalDateTime inicioEspera,
        LocalDateTime inicioCarregamento,
        LocalDateTime fimCarregamento,
        LocalDateTime inicioTransporte,
        LocalDateTime fimTransporte,
        Long esperaMinutos,
        Long carregamentoMinutos,
        Long transporteMinutos
) {

    public static FasesCarregamento de(Carregamento c) {
        if (c == null) {
            return null;
        }
        return new FasesCarregamento(
                c.getDataHoraInicio(),
                c.getDataHoraInicioCarregamento(),
                c.getDataHoraFimCarregamento(),
                c.getDataHoraInicioTransporte(),
                c.getDataHoraFim(),
                minutos(c.getDataHoraInicio(), c.getDataHoraInicioCarregamento()),
                minutos(c.getDataHoraInicioCarregamento(), c.getDataHoraFimCarregamento()),
                minutos(c.getDataHoraInicioTransporte(), c.getDataHoraFim())
        );
    }

    /** Valida a ordem dos instantes recebidos, ignorando os que não foram enviados. */
    public static void validar(LocalDateTime inicio, LocalDateTime inicioCarregamento,
                               LocalDateTime fimCarregamento,
                               LocalDateTime inicioTransporte, LocalDateTime fim) {
        LocalDateTime[] pontos = {inicio, inicioCarregamento, fimCarregamento,
                inicioTransporte, fim};
        String[] nomes = {"registo", "início do carregamento", "fim do carregamento",
                "início do transporte", "fim do transporte"};
        for (int i = 0; i < pontos.length; i++) {
            for (int j = i + 1; j < pontos.length; j++) {
                if (pontos[i] != null && pontos[j] != null && pontos[i].isAfter(pontos[j])) {
                    throw ApiException.unprocessable("Sequência inválida: " + nomes[i]
                            + " não pode ser posterior a " + nomes[j] + ".");
                }
            }
        }
    }

    private static Long minutos(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            return null;
        }
        return Duration.between(inicio, fim).toMinutes();
    }
}
