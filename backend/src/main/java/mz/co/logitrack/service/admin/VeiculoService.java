package mz.co.logitrack.service.admin;

import mz.co.logitrack.domain.Veiculo;
import mz.co.logitrack.dto.admin.VeiculoDTO;
import mz.co.logitrack.dto.admin.VeiculoRequest;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.repository.CarregamentoRepository;
import mz.co.logitrack.repository.VeiculoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** CRUD da frota (W9). */
@Service
public class VeiculoService {

    private final VeiculoRepository veiculoRepository;
    private final CarregamentoRepository carregamentoRepository;

    public VeiculoService(VeiculoRepository veiculoRepository,
                          CarregamentoRepository carregamentoRepository) {
        this.veiculoRepository = veiculoRepository;
        this.carregamentoRepository = carregamentoRepository;
    }

    @Transactional(readOnly = true)
    public List<VeiculoDTO> listar() {
        return veiculoRepository.findAllByOrderByMatriculaAsc().stream()
                .map(this::toDTO).toList();
    }

    @Transactional
    public VeiculoDTO criar(VeiculoRequest pedido) {
        String matricula = normalizar(pedido.matricula());
        if (veiculoRepository.existsByMatriculaIgnoreCase(matricula)) {
            throw ApiException.unprocessable("Já existe um veículo com esta matrícula");
        }
        Veiculo veiculo = new Veiculo();
        aplicar(veiculo, pedido, matricula);
        return toDTO(veiculoRepository.save(veiculo));
    }

    @Transactional
    public VeiculoDTO atualizar(Long id, VeiculoRequest pedido) {
        Veiculo veiculo = entidade(id);
        String matricula = normalizar(pedido.matricula());
        if (veiculoRepository.existsByMatriculaIgnoreCaseAndIdNot(matricula, id)) {
            throw ApiException.unprocessable("Já existe um veículo com esta matrícula");
        }
        aplicar(veiculo, pedido, matricula);
        return toDTO(veiculoRepository.save(veiculo));
    }

    @Transactional
    public void eliminar(Long id) {
        Veiculo veiculo = entidade(id);
        if (carregamentoRepository.countByVeiculoId(id) > 0) {
            throw ApiException.unprocessable(
                    "O veículo tem carregamentos associados. Altere o estado para manutenção.");
        }
        veiculoRepository.delete(veiculo);
    }

    public Veiculo entidade(Long id) {
        return veiculoRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Veículo não encontrado"));
    }

    private void aplicar(Veiculo veiculo, VeiculoRequest pedido, String matricula) {
        veiculo.setMatricula(matricula);
        veiculo.setModelo(pedido.modelo().trim());
        veiculo.setCapacidadeCargaToneladas(pedido.capacidadeCargaToneladas());
        veiculo.setEstado(pedido.estado());
    }

    private String normalizar(String matricula) {
        return matricula.trim().toUpperCase(Locale.ROOT);
    }

    private VeiculoDTO toDTO(Veiculo v) {
        return new VeiculoDTO(v.getId(), v.getMatricula(), v.getModelo(),
                v.getCapacidadeCargaToneladas(), v.getEstado(),
                v.getEstado().getEtiqueta());
    }
}
