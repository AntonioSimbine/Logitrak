package mz.co.logitrack.controller.api;

import jakarta.validation.Valid;
import mz.co.logitrack.dto.carregamento.CarregamentoDetalhe;
import mz.co.logitrack.dto.carregamento.CarregamentoResumo;
import mz.co.logitrack.dto.carregamento.RegistarFasesRequest;
import mz.co.logitrack.dto.dashboard.MarcarLidoRequest;
import mz.co.logitrack.dto.mobile.MobileDashboardResponse;
import mz.co.logitrack.dto.mobile.NotificacaoResponse;
import mz.co.logitrack.dto.mobile.ReferenciasMobileResponse;
import mz.co.logitrack.security.AuthenticatedUser;
import mz.co.logitrack.service.CarregamentoService;
import mz.co.logitrack.service.MobileService;
import mz.co.logitrack.service.NotificacaoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** API dedicada à aplicação móvel do motorista (M2, M3, M4 e M6). */
@RestController
@RequestMapping({"/api/v1/mobile", "/api/mobile"})
@PreAuthorize("hasRole('MOTORISTA')")
public class MobileController {

    private final MobileService mobileService;
    private final CarregamentoService carregamentoService;
    private final NotificacaoService notificacaoService;

    public MobileController(MobileService mobileService, CarregamentoService carregamentoService,
                            NotificacaoService notificacaoService) {
        this.mobileService = mobileService;
        this.carregamentoService = carregamentoService;
        this.notificacaoService = notificacaoService;
    }

    @GetMapping("/dashboard")
    public MobileDashboardResponse dashboard(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return mobileService.dashboard(principal);
    }

    @GetMapping("/referencias")
    public ReferenciasMobileResponse referencias() {
        return mobileService.referencias();
    }

    @GetMapping("/carregamentos")
    public List<CarregamentoResumo> meusCarregamentos(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return mobileService.meusCarregamentos(principal);
    }

    @GetMapping("/carregamentos/{id}")
    public CarregamentoDetalhe detalhe(@PathVariable Long id,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return mobileService.detalhe(id, principal);
    }

    /** Notificações destinadas exclusivamente à conta autenticada. */
    @GetMapping("/notificacoes")
    public List<NotificacaoResponse> notificacoes(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return notificacaoService.listarPara(principal.getId());
    }

    @GetMapping("/notificacoes/nao-lidas")
    public Map<String, Long> notificacoesNaoLidas(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return Map.of("naoLidas", notificacaoService.contarNaoLidas(principal.getId()));
    }

    @PatchMapping("/notificacoes/{id}/lida")
    public NotificacaoResponse marcarLida(
            @PathVariable Long id,
            @Valid @RequestBody MarcarLidoRequest pedido,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return notificacaoService.marcarLida(
                id, principal.getId(), Boolean.TRUE.equals(pedido.lido()));
    }

    @PatchMapping("/notificacoes/lidas")
    public Map<String, Integer> marcarTodasLidas(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return Map.of("marcadas", notificacaoService.marcarTodasLidas(principal.getId()));
    }

    /**
     * Registo dos instantes reais das fases a partir da app do motorista.
     * O serviço garante que o motorista só altera os seus próprios carregamentos.
     */
    @PatchMapping("/carregamentos/{id}/fases")
    public CarregamentoResumo registarFases(
            @PathVariable Long id,
            @Valid @RequestBody RegistarFasesRequest pedido,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return carregamentoService.registarFases(id, pedido, principal);
    }
}
