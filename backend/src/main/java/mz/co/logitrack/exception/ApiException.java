package mz.co.logitrack.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** Exceção de negócio com código HTTP explícito para a API REST. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> detalhes;

    public ApiException(HttpStatus status, String mensagem) {
        this(status, mensagem, null);
    }

    public ApiException(HttpStatus status, String mensagem, Map<String, String> detalhes) {
        super(mensagem);
        this.status = status;
        this.detalhes = detalhes;
    }

    public static ApiException badRequest(String mensagem) {
        return new ApiException(HttpStatus.BAD_REQUEST, mensagem);
    }

    public static ApiException unauthorized(String mensagem) {
        return new ApiException(HttpStatus.UNAUTHORIZED, mensagem);
    }

    public static ApiException forbidden(String mensagem) {
        return new ApiException(HttpStatus.FORBIDDEN, mensagem);
    }

    public static ApiException notFound(String mensagem) {
        return new ApiException(HttpStatus.NOT_FOUND, mensagem);
    }

    public static ApiException unprocessable(String mensagem) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, mensagem);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, String> getDetalhes() {
        return detalhes;
    }
}
