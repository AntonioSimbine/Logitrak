package mz.co.logitrack.controller.api.admin;

import jakarta.validation.Valid;
import mz.co.logitrack.dto.admin.AtivoRequest;
import mz.co.logitrack.dto.admin.ResetPasswordRequest;
import mz.co.logitrack.dto.admin.UtilizadorRequest;
import mz.co.logitrack.dto.auth.UtilizadorResponse;
import mz.co.logitrack.security.AuthenticatedUser;
import mz.co.logitrack.service.admin.UtilizadorService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Gestão de utilizadores e perfis (W8 / RN07). */
@RestController
@RequestMapping({"/api/v1/utilizadores", "/api/utilizadores"})
@PreAuthorize("hasRole('ADMIN')")
public class UtilizadorAdminController {

    private final UtilizadorService utilizadorService;

    public UtilizadorAdminController(UtilizadorService utilizadorService) {
        this.utilizadorService = utilizadorService;
    }

    @GetMapping
    public List<UtilizadorResponse> listar() {
        return utilizadorService.listar();
    }

    @GetMapping("/motoristas")
    public List<UtilizadorResponse> motoristas() {
        return utilizadorService.motoristas();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UtilizadorResponse criar(@Valid @RequestBody UtilizadorRequest pedido) {
        return utilizadorService.criar(pedido);
    }

    @PutMapping("/{id}")
    public UtilizadorResponse atualizar(@PathVariable Long id,
                                        @Valid @RequestBody UtilizadorRequest pedido) {
        return utilizadorService.atualizar(id, pedido);
    }

    @PatchMapping("/{id}/ativo")
    public UtilizadorResponse ativo(@PathVariable Long id,
                                    @Valid @RequestBody AtivoRequest pedido,
                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return utilizadorService.definirAtivo(id, pedido.ativo(), principal.getId());
    }

    @PatchMapping("/{id}/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@PathVariable Long id,
                               @Valid @RequestBody ResetPasswordRequest pedido) {
        utilizadorService.redefinirSenha(id, pedido);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id,
                         @AuthenticationPrincipal AuthenticatedUser principal) {
        utilizadorService.eliminar(id, principal.getId());
    }
}
