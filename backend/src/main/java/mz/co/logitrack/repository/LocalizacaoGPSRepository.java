package mz.co.logitrack.repository;

import mz.co.logitrack.domain.LocalizacaoGPS;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocalizacaoGPSRepository extends JpaRepository<LocalizacaoGPS, Long> {

    Optional<LocalizacaoGPS> findTopByCarregamentoIdOrderByTimestampDesc(Long carregamentoId);

    List<LocalizacaoGPS> findByCarregamentoIdOrderByTimestampAsc(Long carregamentoId);

    List<LocalizacaoGPS> findByVeiculoIdOrderByTimestampAsc(Long veiculoId);

    boolean existsByCarregamentoId(Long carregamentoId);

    @EntityGraph(attributePaths = {"veiculo", "carregamento", "carregamento.fabrica", "carregamento.motorista"})
    @Query("""
            select g from LocalizacaoGPS g
            where g.id in (
                select max(g2.id) from LocalizacaoGPS g2
                group by g2.carregamento.id
            )
            order by g.timestamp desc
            """)
    List<LocalizacaoGPS> findUltimaPorCarregamento();
}
