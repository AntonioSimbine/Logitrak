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

    Optional<LocalizacaoGPS> findByCarregamentoIdAndUuidPonto(Long carregamentoId,
                                                              String uuidPonto);

    boolean existsByCarregamentoIdAndTimestampAndLatitudeAndLongitude(
            Long carregamentoId, java.time.LocalDateTime timestamp,
            Double latitude, Double longitude);

    /**
     * Ordena por hora de captura — não por id — para que um ponto antigo
     * enviado depois de uma falha de rede não apareça como posição actual.
     */
    @EntityGraph(attributePaths = {"veiculo", "carregamento", "carregamento.fabrica", "carregamento.motorista"})
    @Query("select g from LocalizacaoGPS g order by g.timestamp desc, g.id desc")
    List<LocalizacaoGPS> findUltimaPorCarregamento();
}
