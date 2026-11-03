package mz.co.logitrack.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** Envelope de paginação utilizado pelo painel web. */
public record PaginaResponse<T>(List<T> content, int page, int size, long total,
                                int totalPages, boolean first, boolean last) {

    public static <T> PaginaResponse<T> de(Page<T> pagina) {
        return new PaginaResponse<>(
                pagina.getContent(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages(),
                pagina.isFirst(),
                pagina.isLast());
    }
}
