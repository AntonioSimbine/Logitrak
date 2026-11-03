package mz.co.logitrack.service.admin;

import mz.co.logitrack.domain.Fabrica;
import mz.co.logitrack.dto.admin.FabricaDTO;
import mz.co.logitrack.dto.admin.FabricaRequest;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.repository.FabricaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD de fábricas (W7). */
@Service
public class FabricaService {

    private final FabricaRepository fabricaRepository;
    public FabricaService(FabricaRepository fabricaRepository) {
        this.fabricaRepository = fabricaRepository;
    }

    @Transactional(readOnly = true)
    public List<FabricaDTO> listar() {
        return fabricaRepository.findAllByOrderByNomeAsc().stream()
                .map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public FabricaDTO obter(Long id) {
        return toDTO(entidade(id));
    }

    @Transactional
    public FabricaDTO criar(FabricaRequest pedido) {
        validarNome(pedido.nome(), null);
        Fabrica fabrica = new Fabrica();
        aplicar(fabrica, pedido);
        return toDTO(fabricaRepository.save(fabrica));
    }

    @Transactional
    public FabricaDTO atualizar(Long id, FabricaRequest pedido) {
        Fabrica fabrica = entidade(id);
        validarNome(pedido.nome(), id);
        aplicar(fabrica, pedido);
        return toDTO(fabricaRepository.save(fabrica));
    }

    @Transactional
    public void eliminar(Long id) {
        // Eliminação lógica: preserva o histórico e retira a unidade das referências
        // enviadas à aplicação móvel e da deteção de geofence.
        Fabrica fabrica = entidade(id);
        fabrica.setAtiva(Boolean.FALSE);
        fabricaRepository.save(fabrica);
    }

    public Fabrica entidade(Long id) {
        return fabricaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Fábrica não encontrada"));
    }

    private void validarNome(String nome, Long idIgnorado) {
        boolean duplicado = idIgnorado == null
                ? fabricaRepository.existsByNomeIgnoreCase(nome.trim())
                : fabricaRepository.existsByNomeIgnoreCaseAndIdNot(nome.trim(), idIgnorado);
        if (duplicado) {
            throw ApiException.unprocessable("Já existe uma fábrica com este nome");
        }
    }

    private void aplicar(Fabrica fabrica, FabricaRequest pedido) {
        fabrica.setNome(pedido.nome().trim());
        fabrica.setEndereco(pedido.endereco().trim());
        fabrica.setLatitude(pedido.latitude());
        fabrica.setLongitude(pedido.longitude());
        fabrica.setRaioGeofenceMetros(pedido.raioGeofenceMetros());
        fabrica.setSlaLimiteHoras(pedido.slaLimiteHoras());
        fabrica.setHorarioAcostagem(pedido.horarioAcostagem().trim());
        fabrica.setAtiva(pedido.ativa() == null ? Boolean.TRUE : pedido.ativa());
    }

    private FabricaDTO toDTO(Fabrica f) {
        return new FabricaDTO(f.getId(), f.getNome(), f.getEndereco(), f.getLatitude(),
                f.getLongitude(), f.getRaioGeofenceMetros(), f.getSlaLimiteHoras(),
                f.getHorarioAcostagem(), f.getAtiva());
    }
}
