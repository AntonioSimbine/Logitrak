package mz.co.logitrack.repository;

import mz.co.logitrack.domain.AlertaAtraso;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertaAtrasoRepository extends JpaRepository<AlertaAtraso, Long> {

    boolean existsByCarregamentoIdAndLidoFalse(Long carregamentoId);

    long countByLidoFalse();

    @EntityGraph(attributePaths = {"carregamento", "carregamento.fabrica",
            "carregamento.veiculo", "carregamento.motorista"})
    List<AlertaAtraso> findByLidoFalseOrderByDataHoraEmissaoDesc();

    @EntityGraph(attributePaths = {"carregamento", "carregamento.fabrica",
            "carregamento.veiculo", "carregamento.motorista"})
    Page<AlertaAtraso> findAllByOrderByDataHoraEmissaoDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"carregamento", "carregamento.fabrica",
            "carregamento.veiculo", "carregamento.motorista"})
    List<AlertaAtraso> findByCarregamentoIdOrderByDataHoraEmissaoDesc(Long carregamentoId);
}
