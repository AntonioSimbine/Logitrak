package mz.co.logitrack.controller.api.admin;

import jakarta.validation.Valid;
import mz.co.logitrack.dto.admin.VeiculoDTO;
import mz.co.logitrack.dto.admin.VeiculoRequest;
import mz.co.logitrack.service.admin.VeiculoService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** CRUD da frota (W9 / RN07). */
@RestController
@RequestMapping({"/api/v1/veiculos", "/api/veiculos"})
@PreAuthorize("hasRole('ADMIN')")
public class VeiculoAdminController {

    private final VeiculoService veiculoService;

    public VeiculoAdminController(VeiculoService veiculoService) {
        this.veiculoService = veiculoService;
    }

    @GetMapping
    public List<VeiculoDTO> listar() {
        return veiculoService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VeiculoDTO criar(@Valid @RequestBody VeiculoRequest pedido) {
        return veiculoService.criar(pedido);
    }

    @PutMapping("/{id}")
    public VeiculoDTO atualizar(@PathVariable Long id,
                                @Valid @RequestBody VeiculoRequest pedido) {
        return veiculoService.atualizar(id, pedido);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        veiculoService.eliminar(id);
    }
}
