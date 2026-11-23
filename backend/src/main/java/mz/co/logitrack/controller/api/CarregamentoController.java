package mz.co.logitrack.controller.api;

import jakarta.validation.Valid;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.dto.carregamento.AtualizarEstadoRequest;
import mz.co.logitrack.dto.carregamento.CarregamentoDetalhe;
import mz.co.logitrack.dto.carregamento.CarregamentoResumo;
import mz.co.logitrack.dto.carregamento.PosicaoRequest;
import mz.co.logitrack.dto.carregamento.RegistarFasesRequest;
import mz.co.logitrack.dto.common.PaginaResponse;
import mz.co.logitrack.security.AuthenticatedUser;
import mz.co.logitrack.service.CarregamentoService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Consulta e ciclo de vida dos carregamentos (W2, W3, W4, M2 e M6). */
@RestController
@RequestMapping({"/api/v1/carregamentos", "/api/carregamentos"})
public class CarregamentoController {

    private final CarregamentoService carregamentoService;

    public CarregamentoController(CarregamentoService carregamentoService) {
        this.carregamentoService = carregamentoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public PaginaResponse<CarregamentoResumo> listar(
            @RequestParam(required = false) EstadoCarregamento estado,
            @RequestParam(required = false) Long fabricaId,
            @RequestParam(required = false) Long veiculoId,
            @RequestParam(required = false) Long motoristaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dataHoraInicio") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction sentido = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(Math.max(0, page),
                Math.min(Math.max(1, size), 200), Sort.by(sentido, sort));
        return PaginaResponse.de(carregamentoService.listar(
                estado, fabricaId, veiculoId, motoristaId, de, ate, q, pageable));
    }

    @GetMapping("/recentes")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public List<CarregamentoResumo> recentes() {
        return carregamentoService.recentes();
    }

    @GetMapping("/{id}")
    public CarregamentoDetalhe detalhe(@PathVariable Long id,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return carregamentoService.detalhe(id, principal);
    }

    @GetMapping("/{id}/posicoes")
    public List<CarregamentoDetalhe.PosicaoResumo> posicoes(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return carregamentoService.listarPosicoes(id, principal);
    }

    @PostMapping("/{id}/posicoes")
    @PreAuthorize("hasRole('MOTORISTA')")
    public ResponseEntity<CarregamentoDetalhe.PosicaoResumo> registarPosicao(
            @PathVariable Long id,
            @Valid @RequestBody PosicaoRequest pedido,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        var resultado = carregamentoService.registarPosicao(id, pedido, principal);
        return ResponseEntity.status(resultado.duplicado()
                        ? HttpStatus.OK : HttpStatus.CREATED)
                .body(resultado.posicao());
    }

    /** Regista os instantes reais das três fases operacionais. */
    @PatchMapping("/{id}/fases")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public CarregamentoResumo registarFases(
            @PathVariable Long id,
            @Valid @RequestBody RegistarFasesRequest pedido,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return carregamentoService.registarFases(id, pedido, principal);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public CarregamentoResumo atualizarEstado(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarEstadoRequest pedido,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return carregamentoService.atualizarEstado(id, pedido, principal);
    }
}
