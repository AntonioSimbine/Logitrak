package mz.co.logitrack.controller.api;

import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.DocumentoGuia;
import mz.co.logitrack.dto.carregamento.CarregamentoDetalhe;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.mapper.CarregamentoMapper;
import mz.co.logitrack.security.AuthenticatedUser;
import mz.co.logitrack.service.CarregamentoService;
import mz.co.logitrack.service.DocumentoService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

/** Receção e consulta das guias digitalizadas (CU010 / RF010). */
@RestController
@RequestMapping({"/api/v1/documentos", "/api/documentos"})
public class DocumentoController {

    private final DocumentoService documentoService;
    private final CarregamentoService carregamentoService;
    private final CarregamentoMapper mapper;

    public DocumentoController(DocumentoService documentoService,
                               CarregamentoService carregamentoService,
                               CarregamentoMapper mapper) {
        this.documentoService = documentoService;
        this.carregamentoService = carregamentoService;
        this.mapper = mapper;
    }

    @GetMapping("/{id}")
    public CarregamentoDetalhe.DocumentoResumo metadados(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return mapper.toDocumento(documentoService.obterComAcesso(id, principal));
    }

    @GetMapping("/carregamento/{carregamentoId}")
    public CarregamentoDetalhe.DocumentoResumo porCarregamento(
            @PathVariable Long carregamentoId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        DocumentoGuia documento = documentoService
                .obterPorCarregamentoComAcesso(carregamentoId, principal);
        return mapper.toDocumento(documento);
    }

    @GetMapping("/{id}/ficheiro")
    public ResponseEntity<Resource> ficheiro(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        DocumentoService.FicheiroGuia ficheiro =
                documentoService.carregarFicheiro(id, principal);

        MediaType tipo;
        try {
            tipo = MediaType.parseMediaType(ficheiro.mimeType());
        } catch (Exception ex) {
            tipo = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(tipo)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + ficheiro.nomeFicheiro() + "\"")
                .body(ficheiro.recurso());
    }

    @PostMapping(value = "/carregamento/{carregamentoId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MOTORISTA')")
    public ResponseEntity<CarregamentoDetalhe.DocumentoResumo> enviar(
            @PathVariable Long carregamentoId,
            @RequestPart("ficheiro") MultipartFile ficheiro,
            @AuthenticationPrincipal AuthenticatedUser principal) throws IOException {

        if (ficheiro == null || ficheiro.isEmpty()) {
            throw ApiException.unprocessable("Falta a fotografia da guia. Fotografe a guia e tente novamente.");
        }
        Carregamento carregamento = carregamentoService.obterComRelacoes(carregamentoId);
        carregamentoService.verificarAcesso(carregamento, principal);
        DocumentoGuia documento = documentoService.substituir(
                carregamento, ficheiro.getBytes(), ficheiro.getContentType());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDocumento(documento));
    }
}
