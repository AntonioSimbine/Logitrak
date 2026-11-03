package mz.co.logitrack.repository;

import mz.co.logitrack.domain.Veiculo;
import mz.co.logitrack.domain.enums.EstadoVeiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VeiculoRepository extends JpaRepository<Veiculo, Long> {

    List<Veiculo> findAllByOrderByMatriculaAsc();

    List<Veiculo> findByEstadoNotOrderByMatriculaAsc(EstadoVeiculo estado);

    List<Veiculo> findByEstadoOrderByMatriculaAsc(EstadoVeiculo estado);

    boolean existsByMatriculaIgnoreCase(String matricula);

    boolean existsByMatriculaIgnoreCaseAndIdNot(String matricula, Long id);

    long countByEstado(EstadoVeiculo estado);
}
