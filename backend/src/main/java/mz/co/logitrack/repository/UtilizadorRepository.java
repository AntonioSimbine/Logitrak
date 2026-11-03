package mz.co.logitrack.repository;

import mz.co.logitrack.domain.Utilizador;
import mz.co.logitrack.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UtilizadorRepository extends JpaRepository<Utilizador, Long> {

    Optional<Utilizador> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    List<Utilizador> findAllByOrderByNomeAsc();

    List<Utilizador> findByPerfilOrderByNomeAsc(Role perfil);

    long countByPerfil(Role perfil);

    long countByPerfilAndAtivoTrue(Role perfil);
}
