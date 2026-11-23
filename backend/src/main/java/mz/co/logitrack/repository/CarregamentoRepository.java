package mz.co.logitrack.repository;

import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarregamentoRepository extends JpaRepository<Carregamento, Long> {

    Optional<Carregamento> findByUuidLocal(UUID uuidLocal);

    /** Reverificação do uuidLocal dentro da transação, depois do bloqueio do veículo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Carregamento c where c.uuidLocal = :uuidLocal")
    Optional<Carregamento> findByUuidLocalForUpdate(@Param("uuidLocal") UUID uuidLocal);

    /** Serializa a passagem do motor SLA para o mesmo carregamento. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Carregamento c where c.id = :id")
    Optional<Carregamento> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUuidLocal(UUID uuidLocal);

    long countByUuidLocal(UUID uuidLocal);

    @EntityGraph(attributePaths = {"fabrica", "veiculo", "motorista"})
    Optional<Carregamento> findWithRelationsById(Long id);

    @EntityGraph(attributePaths = {"fabrica", "veiculo", "motorista"})
    List<Carregamento> findByEstadoOrderByDataHoraInicioAsc(EstadoCarregamento estado);

    @EntityGraph(attributePaths = {"fabrica", "veiculo", "motorista"})
    List<Carregamento> findByEstadoOrderByDataHoraInicioDesc(EstadoCarregamento estado);

    @EntityGraph(attributePaths = {"fabrica", "veiculo", "motorista"})
    List<Carregamento> findByEstadoInOrderByDataHoraInicioDesc(List<EstadoCarregamento> estados);

    @EntityGraph(attributePaths = {"fabrica", "veiculo", "motorista"})
    List<Carregamento> findTop20ByMotoristaIdOrderByDataHoraInicioDesc(Long motoristaId);

    @EntityGraph(attributePaths = {"fabrica", "veiculo", "motorista"})
    List<Carregamento> findTop10ByOrderByDataHoraInicioDesc();

    long countByEstado(EstadoCarregamento estado);

    long countByEstadoAndDataHoraFimBetween(EstadoCarregamento estado, LocalDateTime inicio, LocalDateTime fim);

    long countByMotoristaIdAndEstado(Long motoristaId, EstadoCarregamento estado);

    @EntityGraph(attributePaths = {"fabrica", "veiculo", "motorista"})
    List<Carregamento> findByEstadoAndDataHoraFimIsNotNullOrderByDataHoraFimDesc(EstadoCarregamento estado);

    long countByFabricaId(Long fabricaId);

    long countByVeiculoId(Long veiculoId);

    List<Carregamento> findByVeiculoIdAndEstadoIn(Long veiculoId,
                                                  Collection<EstadoCarregamento> estados);

    @Query("""
            select coalesce(sum(c.quantidadeToneladas), 0)
            from Carregamento c
            where c.veiculo.id = :veiculoId
              and c.estado in :estados
              and (:excluir is null or c.id <> :excluir)
            """)
    double somarToneladasAtivas(@Param("veiculoId") Long veiculoId,
                                @Param("estados") Collection<EstadoCarregamento> estados,
                                @Param("excluir") Long excluir);

    long countByMotoristaId(Long motoristaId);

    /**
     * Pesquisa paginada usada pelo painel web (W3) e pelos relatórios (W5).
     * Todos os filtros são opcionais.
     */
    @EntityGraph(attributePaths = {"fabrica", "veiculo", "motorista"})
    @Query("""
            select c from Carregamento c
              left join c.veiculo v
              left join c.fabrica f
              left join c.motorista m
            where (:estado is null or c.estado = :estado)
              and (:fabricaId is null or f.id = :fabricaId)
              and (:veiculoId is null or v.id = :veiculoId)
              and (:motoristaId is null or m.id = :motoristaId)
              and (:de is null or c.dataHoraInicio >= :de)
              and (:ate is null or c.dataHoraInicio <= :ate)
              and (:q is null or :q = ''
                   or lower(c.guiaNumero) like lower(concat('%', :q, '%'))
                   or lower(v.matricula) like lower(concat('%', :q, '%'))
                   or lower(f.nome) like lower(concat('%', :q, '%'))
                   or lower(m.nome) like lower(concat('%', :q, '%')))
            """)
    Page<Carregamento> findByFiltros(@Param("estado") EstadoCarregamento estado,
                                     @Param("fabricaId") Long fabricaId,
                                     @Param("veiculoId") Long veiculoId,
                                     @Param("motoristaId") Long motoristaId,
                                     @Param("de") LocalDateTime de,
                                     @Param("ate") LocalDateTime ate,
                                     @Param("q") String q,
                                     Pageable pageable);

    @Query("""
            select f.nome, coalesce(sum(c.quantidadeToneladas), 0)
            from Carregamento c join c.fabrica f
            where c.estado = mz.co.logitrack.domain.enums.EstadoCarregamento.CONCLUIDO
            group by f.id, f.nome
            order by sum(c.quantidadeToneladas) desc
            """)
    List<Object[]> volumePorFabrica();

    @Query("""
            select count(c) from Carregamento c
            where c.estado = mz.co.logitrack.domain.enums.EstadoCarregamento.ATRASADO
            """)
    long countAtrasados();
}
