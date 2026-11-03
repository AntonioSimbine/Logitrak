package mz.co.logitrack.controller.api.admin;

import jakarta.validation.Valid;
import mz.co.logitrack.dto.admin.ParametroDTO;
import mz.co.logitrack.dto.admin.ParametroUpdateRequest;
import mz.co.logitrack.service.ParametroService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Parâmetros globais alteráveis em tempo de execução (W10 / RN07). */
@RestController
@RequestMapping({"/api/v1/parametros", "/api/parametros"})
@PreAuthorize("hasRole('ADMIN')")
public class ParametroAdminController {

    private final ParametroService parametroService;

    public ParametroAdminController(ParametroService parametroService) {
        this.parametroService = parametroService;
    }

    @GetMapping
    public List<ParametroDTO> listar() {
        return parametroService.listar().stream()
                .map(p -> new ParametroDTO(p.getId(), p.getChave(), p.getValor(),
                        p.getDescricao()))
                .toList();
    }

    @PutMapping("/{chave}")
    public ParametroDTO atualizar(@PathVariable String chave,
                                  @Valid @RequestBody ParametroUpdateRequest pedido) {
        var parametro = parametroService.atualizar(chave, pedido.valor());
        return new ParametroDTO(parametro.getId(), parametro.getChave(),
                parametro.getValor(), parametro.getDescricao());
    }
}
