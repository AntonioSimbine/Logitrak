package mz.co.logitrack.repository;

import mz.co.logitrack.domain.Fabrica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FabricaRepository extends JpaRepository<Fabrica, Long> {

    List<Fabrica> findByAtivaTrueOrderByNomeAsc();

    List<Fabrica> findAllByOrderByNomeAsc();

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
