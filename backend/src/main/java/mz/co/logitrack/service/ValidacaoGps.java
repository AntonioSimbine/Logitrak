package mz.co.logitrack.service;

import mz.co.logitrack.exception.ApiException;

/** Validação comum da precisão GPS (RNF005), usada pela API e pela fila offline. */
public final class ValidacaoGps {

    private ValidacaoGps() {
    }

    public static void validar(double precisao, double maxima) {
        if (Double.isNaN(precisao) || Double.isInfinite(precisao)
                || precisao < 0 || precisao > maxima) {
            String limite = Math.abs(maxima - Math.rint(maxima)) < 0.0001
                    ? String.format("%.0f", maxima) : String.format("%.1f", maxima);
            throw ApiException.badRequest(
                    "A localização ainda não é precisa (máximo " + limite + "m). "
                            + ParametroService.ORIENTACAO_GPS);
        }
    }
}
