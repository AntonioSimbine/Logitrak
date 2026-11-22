package mz.co.logitrack.repository;

import mz.co.logitrack.domain.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findTop100ByDestinatarioIdOrderByDataHoraCriacaoDesc(Long destinatarioId);

    List<Notificacao> findByDestinatarioIdAndLidoFalse(Long destinatarioId);

    long countByDestinatarioIdAndLidoFalse(Long destinatarioId);

    Optional<Notificacao> findByIdAndDestinatarioId(Long id, Long destinatarioId);
}
