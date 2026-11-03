package mz.co.logitrack.service;

import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.LocalizacaoGPS;
import mz.co.logitrack.domain.Veiculo;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.domain.enums.EstadoVeiculo;
import mz.co.logitrack.domain.enums.Role;
import mz.co.logitrack.dto.carregamento.CarregamentoDetalhe;
import mz.co.logitrack.dto.carregamento.CarregamentoResumo;
import mz.co.logitrack.dto.carregamento.PosicaoRequest;
import mz.co.logitrack.dto.dashboard.PontoMapa;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.mapper.CarregamentoMapper;
import mz.co.logitrack.repository.AlertaAtrasoRepository;
import mz.co.logitrack.repository.CarregamentoRepository;
import mz.co.logitrack.repository.LocalizacaoGPSRepository;
import mz.co.logitrack.repository.VeiculoRepository;
import mz.co.logitrack.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Consulta e ciclo de vida dos carregamentos (RN04/RN05). */
@Service
public class CarregamentoService {

    private static final Logger log = LoggerFactory.getLogger(CarregamentoService.class);

    private final CarregamentoRepository carregamentoRepository;
    private final VeiculoRepository veiculoRepository;
    private final LocalizacaoGPSRepository gpsRepository;
    private final AlertaAtrasoRepository alertaRepository;
    private final CarregamentoMapper mapper;
    private final NotificacaoService notificacaoService;
    private final ParametroService parametroService;

    public CarregamentoService(CarregamentoRepository carregamentoRepository,
                               VeiculoRepository veiculoRepository,
                               LocalizacaoGPSRepository gpsRepository,
                               AlertaAtrasoRepository alertaRepository,
                               CarregamentoMapper mapper,
                               NotificacaoService notificacaoService,
                               ParametroService parametroService) {
        this.carregamentoRepository = carregamentoRepository;
        this.veiculoRepository = veiculoRepository;
        this.gpsRepository = gpsRepository;
        this.alertaRepository = alertaRepository;
        this.mapper = mapper;
        this.notificacaoService = notificacaoService;
        this.parametroService = parametroService;
    }

    @Transactional(readOnly = true)
    public Page<CarregamentoResumo> listar(EstadoCarregamento estado, Long fabricaId,
                                           Long veiculoId, Long motoristaId,
                                           LocalDate de, LocalDate ate, String q,
                                           Pageable pageable) {
        LocalDateTime inicio = de != null ? de.atStartOfDay() : null;
        LocalDateTime fim = ate != null ? ate.plusDays(1).atStartOfDay().minusNanos(1) : null;
        String pesquisa = q == null || q.isBlank() ? null : q.trim();
        return carregamentoRepository.findByFiltros(estado, fabricaId, veiculoId,
                        motoristaId, inicio, fim, pesquisa, pageable)
                .map(mapper::toResumo);
    }

    @Transactional(readOnly = true)
    public CarregamentoDetalhe detalhe(Long id, AuthenticatedUser principal) {
        Carregamento c = obterComRelacoes(id);
        verificarAcesso(c, principal);
        List<LocalizacaoGPS> posicoes =
                gpsRepository.findByCarregamentoIdOrderByTimestampAsc(id);
        if (posicoes.size() > 200) {
            posicoes = posicoes.subList(posicoes.size() - 200, posicoes.size());
        }
        int alertasAbertos = (int) alertaRepository
                .findByCarregamentoIdOrderByDataHoraEmissaoDesc(id)
                .stream().filter(a -> !Boolean.TRUE.equals(a.getLido())).count();
        return mapper.toDetalhe(c, posicoes, alertasAbertos);
    }

    @Transactional(readOnly = true)
    public CarregamentoResumo resumo(Long id, AuthenticatedUser principal) {
        Carregamento c = obterComRelacoes(id);
        verificarAcesso(c, principal);
        return mapper.toResumo(c);
    }

    @Transactional
    public CarregamentoResumo atualizarEstado(Long id, EstadoCarregamento novoEstado,
                                              String motivoRejeicao,
                                              AuthenticatedUser principal) {
        if (novoEstado == EstadoCarregamento.ATRASADO) {
            throw ApiException.unprocessable(
                    "O estado ATRASADO é atribuído automaticamente pelo motor de SLA");
        }
        Carregamento c = obterComRelacoes(id);
        verificarAcesso(c, principal);

        if (!c.getEstado().podeTransitarPara(novoEstado)) {
            throw ApiException.unprocessable(String.format(
                    "Transição de estado inválida: %s -> %s",
                    c.getEstado().getEtiqueta(), novoEstado.getEtiqueta()));
        }

        if (novoEstado == EstadoCarregamento.REJEITADO) {
            if (motivoRejeicao == null || motivoRejeicao.isBlank()) {
                throw ApiException.unprocessable(
                        "O motivo de rejeição é obrigatório ao rejeitar um carregamento");
            }
            c.setMotivoRejeicao(motivoRejeicao.trim());
            c.setDataHoraFim(LocalDateTime.now());
            libertarVeiculo(c.getVeiculo());
        }

        if (novoEstado == EstadoCarregamento.EM_TRANSITO) {
            if (c.getDataHoraInicio() == null) {
                c.setDataHoraInicio(LocalDateTime.now());
            }
            if (c.getVeiculo() != null) {
                c.getVeiculo().setEstado(EstadoVeiculo.EM_TRANSITO);
                veiculoRepository.save(c.getVeiculo());
            }
        }

        if (novoEstado == EstadoCarregamento.CONCLUIDO) {
            c.setDataHoraFim(LocalDateTime.now());
            libertarVeiculo(c.getVeiculo());
        }

        c.setEstado(novoEstado);
        Carregamento guardado = carregamentoRepository.save(c);
        CarregamentoResumo resumo = mapper.toResumo(guardado);
        notificacaoService.publicarCarregamento(resumo);
        log.info("Estado do carregamento {} alterado para {} por {}",
                id, novoEstado, principal != null ? principal.getEmail() : "sistema");
        return resumo;
    }

    @Transactional
    public CarregamentoDetalhe.PosicaoResumo registarPosicao(Long id, PosicaoRequest pedido,
                                                             AuthenticatedUser principal) {
        validarPrecisaoGps(pedido.precisaoMetros());
        Carregamento c = obterComRelacoes(id);
        verificarAcesso(c, principal);
        if (c.getEstado() == EstadoCarregamento.CONCLUIDO
                || c.getEstado() == EstadoCarregamento.REJEITADO) {
            throw ApiException.unprocessable(
                    "Não é possível registar GPS num carregamento terminado");
        }
        if (c.getVeiculo() == null) {
            throw ApiException.unprocessable("Carregamento sem veículo associado");
        }

        LocalizacaoGPS localizacao = new LocalizacaoGPS(
                pedido.latitude(), pedido.longitude(), pedido.precisaoMetros(),
                pedido.timestamp() != null ? pedido.timestamp() : LocalDateTime.now(),
                c, c.getVeiculo());
        LocalizacaoGPS guardada = gpsRepository.save(localizacao);

        PontoMapa ponto = mapper.toPontoMapa(guardada);
        notificacaoService.publicarMapa(List.of(ponto));
        return mapper.toPosicao(guardada);
    }

    @Transactional(readOnly = true)
    public List<CarregamentoDetalhe.PosicaoResumo> listarPosicoes(Long id,
                                                                 AuthenticatedUser principal) {
        Carregamento c = obterComRelacoes(id);
        verificarAcesso(c, principal);
        return gpsRepository.findByCarregamentoIdOrderByTimestampAsc(id)
                .stream().map(mapper::toPosicao).toList();
    }

    @Transactional(readOnly = true)
    public List<PontoMapa> ultimosPontosMapa() {
        return gpsRepository.findUltimaPorCarregamento().stream()
                .filter(g -> g.getCarregamento() != null
                        && (g.getCarregamento().getEstado() == EstadoCarregamento.EM_TRANSITO
                        || g.getCarregamento().getEstado() == EstadoCarregamento.ATRASADO))
                .map(mapper::toPontoMapa)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CarregamentoResumo> recentes() {
        return carregamentoRepository.findTop10ByOrderByDataHoraInicioDesc()
                .stream().map(mapper::toResumo).toList();
    }

    @Transactional(readOnly = true)
    public CarregamentoDetalhe detalheInterno(Long id) {
        return detalhe(id, null);
    }

    public Carregamento obterComRelacoes(Long id) {
        return carregamentoRepository.findWithRelationsById(id)
                .orElseThrow(() -> ApiException.notFound("Carregamento não encontrado"));
    }

    public void verificarAcesso(Carregamento c, AuthenticatedUser principal) {
        if (principal == null) {
            return;
        }
        if (principal.getPerfil() == Role.MOTORISTA
                && (c.getMotorista() == null
                || !c.getMotorista().getId().equals(principal.getId()))) {
            throw ApiException.forbidden("Sem permissão para aceder a este carregamento");
        }
    }

    public void validarPrecisaoGps(double precisao) {
        double maxima = parametroService.obterDouble(
                ParametroService.PRECISAO_MAXIMA_GPS_METROS);
        if (precisao > maxima) {
            String limite = Math.abs(maxima - Math.rint(maxima)) < 0.0001
                    ? String.format("%.0f", maxima) : String.format("%.1f", maxima);
            throw ApiException.badRequest(String.format(
                    "Precisão de GPS insuficiente para validação (Máx: %sm)", limite));
        }
    }

    private void libertarVeiculo(Veiculo veiculo) {
        if (veiculo != null) {
            veiculo.setEstado(EstadoVeiculo.DISPONIVEL);
            veiculoRepository.save(veiculo);
        }
    }
}
