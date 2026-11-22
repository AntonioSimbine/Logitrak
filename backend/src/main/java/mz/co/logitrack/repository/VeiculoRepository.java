package mz.co.logitrack.repository;

import jakarta.persistence.LockModeType;
import mz.co.logitrack.domain.Veiculo;
import mz.co.logitrack.domain.enums.EstadoVeiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VeiculoRepository extends JpaRepository<Veiculo, Long> {

    List<Veiculo> findAllByOrderByMatriculaAsc();

    List<Veiculo> findByEstadoNotOrderByMatriculaAsc(EstadoVeiculo estado);

    List<Veiculo> findByEstadoOrderByMatriculaAsc(EstadoVeiculo estado);

    boolean existsByMatriculaIgnoreCase(String matricula);

    boolean existsByMatriculaIgnoreCaseAndIdNot(String matricula, Long id);

    long countByEstado(EstadoVeiculo estado);

    /** Bloqueio pessimista: serialize decisões sobre o mesmo veículo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Veiculo v where v.id = :id")
    Optional<Veiculo> findByIdForUpdate(@Param("id") Long id);
}
