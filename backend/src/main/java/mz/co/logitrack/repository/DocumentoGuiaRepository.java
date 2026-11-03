package mz.co.logitrack.repository;

import mz.co.logitrack.domain.DocumentoGuia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentoGuiaRepository extends JpaRepository<DocumentoGuia, Long> {

    Optional<DocumentoGuia> findByCarregamentoId(Long carregamentoId);

    boolean existsByCarregamentoId(Long carregamentoId);
}
