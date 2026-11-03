package mz.co.logitrack.dto.common;

import java.util.Map;

/** Corpo de erro padronizado da API REST. */
public record ApiError(String error, Map<String, String> detalhes) {

    public static ApiError of(String mensagem) {
        return new ApiError(mensagem, null);
    }
}
