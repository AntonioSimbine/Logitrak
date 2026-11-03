package mz.co.logitrack.controller.api;

import mz.co.logitrack.dto.relatorio.RelatorioFiltro;
import mz.co.logitrack.dto.relatorio.RelatorioResponse;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.service.RelatorioService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Relatórios operacionais e exportação (W5 / CU012). */
@RestController
@RequestMapping({"/api/v1/relatorios", "/api/relatorios"})
@PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
public class RelatorioController {

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    @PostMapping
    public RelatorioResponse gerar(@RequestBody(required = false) RelatorioFiltro filtro) {
        return relatorioService.gerar(filtro != null ? filtro : new RelatorioFiltro(
                null, null, null, null, null, null));
    }

    @GetMapping
    public RelatorioResponse gerarPorParametros(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Long fabricaId,
            @RequestParam(required = false) Long veiculoId,
            @RequestParam(required = false) Long motoristaId,
            @RequestParam(required = false) EstadoCarregamento estado) {
        return relatorioService.gerar(new RelatorioFiltro(
                de, ate, fabricaId, veiculoId, motoristaId, estado));
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Long fabricaId,
            @RequestParam(required = false) Long veiculoId,
            @RequestParam(required = false) Long motoristaId,
            @RequestParam(required = false) EstadoCarregamento estado,
            @RequestParam(defaultValue = "pdf") String formato) {

        RelatorioService.Exportacao exportacao = relatorioService.exportar(
                new RelatorioFiltro(de, ate, fabricaId, veiculoId, motoristaId, estado), formato);

        ContentDisposition disposicao = ContentDisposition.attachment()
                .filename(exportacao.nomeFicheiro(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exportacao.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(exportacao.conteudo());
    }

    @PostMapping("/exportar")
    public ResponseEntity<byte[]> exportarPost(
            @RequestBody(required = false) RelatorioFiltro filtro,
            @RequestParam(defaultValue = "pdf") String formato) {
        RelatorioService.Exportacao exportacao = relatorioService.exportar(
                filtro != null ? filtro : new RelatorioFiltro(null, null, null, null, null, null),
                formato);
        ContentDisposition disposicao = ContentDisposition.attachment()
                .filename(exportacao.nomeFicheiro(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exportacao.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(exportacao.conteudo());
    }
}
