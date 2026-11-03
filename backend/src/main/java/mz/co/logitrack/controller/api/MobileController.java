package mz.co.logitrack.controller.api;

import mz.co.logitrack.dto.carregamento.CarregamentoDetalhe;
import mz.co.logitrack.dto.carregamento.CarregamentoResumo;
import mz.co.logitrack.dto.mobile.MobileDashboardResponse;
import mz.co.logitrack.dto.mobile.ReferenciasMobileResponse;
import mz.co.logitrack.security.AuthenticatedUser;
import mz.co.logitrack.service.MobileService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API dedicada à aplicação móvel do motorista (M2, M3 e M6). */
@RestController
@RequestMapping({"/api/v1/mobile", "/api/mobile"})
@PreAuthorize("hasAnyRole('MOTORISTA','ADMIN')")
public class MobileController {

    private final MobileService mobileService;

    public MobileController(MobileService mobileService) {
        this.mobileService = mobileService;
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
}
