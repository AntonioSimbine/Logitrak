package mz.co.logitrack.controller.api;

import jakarta.validation.Valid;
import mz.co.logitrack.dto.carregamento.CarregamentoResumo;
import mz.co.logitrack.dto.carregamento.SyncCarregamentoRequest;
import mz.co.logitrack.security.AuthenticatedUser;
import mz.co.logitrack.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receção dos registos offline da app móvel. A rota cumpre
 * POST /api/v1/carregamentos (e um alias /api/v1/sync/carregamentos).
 */
@RestController
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping({"/api/v1/carregamentos", "/api/v1/sync/carregamentos"})
    @PreAuthorize("hasAnyRole('MOTORISTA','ADMIN')")
    public ResponseEntity<CarregamentoResumo> sincronizar(
            @Valid @RequestBody SyncCarregamentoRequest pedido,
            @AuthenticationPrincipal AuthenticatedUser motorista) {

        SyncService.ResultadoSincronizacao resultado =
                syncService.sincronizar(pedido, motorista);

        return ResponseEntity.status(resultado.status())
                .header("X-Idempotente", Boolean.toString(resultado.duplicado()))
                .body(resultado.carregamento());
    }
}
