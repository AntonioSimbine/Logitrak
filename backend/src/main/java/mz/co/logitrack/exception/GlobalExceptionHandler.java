package mz.co.logitrack.exception;

import jakarta.validation.ConstraintViolationException;
import mz.co.logitrack.dto.common.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tratamento uniforme de erros para todos os endpoints /api/** . */
@RestControllerAdvice(basePackages = "mz.co.logitrack.controller.api")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> tratarApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getMessage(), ex.getDetalhes()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> tratarValidacao(MethodArgumentNotValidException ex) {
        Map<String, String> detalhes = new LinkedHashMap<>();
        for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
            detalhes.putIfAbsent(erro.getField(), erro.getDefaultMessage());
        }
        return ResponseEntity.unprocessableEntity()
                .body(new ApiError("Faltam dados obrigatórios. Preencha os campos assinalados.",
                        detalhes));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> tratarConstraint(ConstraintViolationException ex) {
        Map<String, String> detalhes = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                detalhes.put(v.getPropertyPath().toString(), v.getMessage()));
        return ResponseEntity.unprocessableEntity().body(new ApiError(
                "Faltam dados obrigatórios. Preencha os campos assinalados.", detalhes));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> tratarJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "Os dados não estão corretos. Verifique os campos e tente novamente."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> tratarParametro(MethodArgumentTypeMismatchException ex) {
        log.warn("Parâmetro inválido no pedido: {}", ex.getName());
        return ResponseEntity.badRequest().body(ApiError.of(
                "Não foi possível interpretar um filtro. Verifique os dados e tente novamente."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> tratarAcessoNegado(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                "Não tem permissão para esta operação. Fale com a central."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> tratarErroInterno(Exception ex) {
        log.error("Erro inesperado na API", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("A central não está a responder. Tente novamente dentro de instantes."));
    }
}
