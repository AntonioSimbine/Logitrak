package mz.co.logitrack.repository;

import mz.co.logitrack.domain.ParametroGlobal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParametroGlobalRepository extends JpaRepository<ParametroGlobal, Long> {

    Optional<ParametroGlobal> findByChaveIgnoreCase(String chave);

    boolean existsByChaveIgnoreCase(String chave);
}
