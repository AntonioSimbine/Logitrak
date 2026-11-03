package mz.co.logitrack.controller.api;

import jakarta.validation.Valid;
import mz.co.logitrack.dto.auth.AuthResponse;
import mz.co.logitrack.dto.auth.LoginRequest;
import mz.co.logitrack.dto.auth.UtilizadorResponse;
import mz.co.logitrack.security.AuthenticatedUser;
import mz.co.logitrack.service.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Login unificado (M1 e W1) e consulta do perfil autenticado. */
@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest pedido) {
        return authService.login(pedido);
    }

    @GetMapping("/me")
    public UtilizadorResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return authService.perfil(principal);
    }
}
