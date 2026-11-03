package mz.co.logitrack.controller.api.admin;

import jakarta.validation.Valid;
import mz.co.logitrack.dto.admin.FabricaDTO;
import mz.co.logitrack.dto.admin.FabricaRequest;
import mz.co.logitrack.service.admin.FabricaService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** CRUD de fábricas (W7 / RN07). */
@RestController
@RequestMapping({"/api/v1/fabricas", "/api/fabricas"})
@PreAuthorize("hasRole('ADMIN')")
public class FabricaAdminController {

    private final FabricaService fabricaService;

    public FabricaAdminController(FabricaService fabricaService) {
        this.fabricaService = fabricaService;
    }

    @GetMapping
    public List<FabricaDTO> listar() {
        return fabricaService.listar();
    }

    @GetMapping("/{id}")
    public FabricaDTO obter(@PathVariable Long id) {
        return fabricaService.obter(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FabricaDTO criar(@Valid @RequestBody FabricaRequest pedido) {
        return fabricaService.criar(pedido);
    }

    @PutMapping("/{id}")
    public FabricaDTO atualizar(@PathVariable Long id,
                                @Valid @RequestBody FabricaRequest pedido) {
        return fabricaService.atualizar(id, pedido);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        fabricaService.eliminar(id);
    }
}
