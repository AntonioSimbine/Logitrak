package mz.co.logitrack.service;

import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.LocalizacaoGPS;
import mz.co.logitrack.domain.Veiculo;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.domain.enums.EstadoVeiculo;
import mz.co.logitrack.domain.enums.OrigemNotificacao;
import mz.co.logitrack.domain.enums.Role;
import mz.co.logitrack.domain.enums.TipoNotificacao;
import mz.co.logitrack.dto.carregamento.CarregamentoDetalhe;
import mz.co.logitrack.dto.carregamento.CarregamentoResumo;
import mz.co.logitrack.dto.carregamento.FasesCarregamento;
import mz.co.logitrack.dto.carregamento.PosicaoRequest;
import mz.co.logitrack.dto.carregamento.RegistarFasesRequest;
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
    private final VehicleOperationService vehicleOperations;

    public CarregamentoService(CarregamentoRepository carregamentoRepository,
                               VeiculoRepository veiculoRepository,
                               LocalizacaoGPSRepository gpsRepository,
                               AlertaAtrasoRepository alertaRepository,
                               CarregamentoMapper mapper,
                               NotificacaoService notificacaoService,
                               ParametroService parametroService,
                               VehicleOperationService vehicleOperations) {
        this.carregamentoRepository = carregamentoRepository;
        this.veiculoRepository = veiculoRepository;
        this.gpsRepository = gpsRepository;
        this.alertaRepository = alertaRepository;
        this.mapper = mapper;
        this.notificacaoService = notificacaoService;
        this.parametroService = parametroService;
        this.vehicleOperations = vehicleOperations;
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

    /** Regista os instantes reais das fases; campos omitidos mantêm o valor atual. */
    @Transactional
    public CarregamentoResumo registarFases(Long id, RegistarFasesRequest pedido,
                                            AuthenticatedUser principal) {
        if (pedido == null || (pedido.dataHoraInicioCarregamento() == null
                && pedido.dataHoraFimCarregamento() == null
                && pedido.dataHoraInicioTransporte() == null
                && pedido.dataHoraFimTransporte() == null)) {
            throw ApiException.badRequest(
                    "Faltam dados obrigatórios. Preencha os campos assinalados.");
        }
        Carregamento c = obterComRelacoes(id);
        verificarAcesso(c, principal);
        if (c.getEstado() == EstadoCarregamento.REJEITADO) {
            throw ApiException.unprocessable(
                    "A central não aceitou o registo. Corrija os dados e envie novamente.");
        }
        if (pedido.dataHoraFimTransporte() != null
                && c.getEstado() != EstadoCarregamento.CONCLUIDO) {
            throw ApiException.unprocessable(
                    "Conclua o registo antes de indicar o fim do transporte.");
        }

        LocalDateTime inicioCarregamento = pedido.dataHoraInicioCarregamento() != null
                ? pedido.dataHoraInicioCarregamento() : c.getDataHoraInicioCarregamento();
        LocalDateTime fimCarregamento = pedido.dataHoraFimCarregamento() != null
                ? pedido.dataHoraFimCarregamento() : c.getDataHoraFimCarregamento();
        LocalDateTime inicioTransporte = pedido.dataHoraInicioTransporte() != null
                ? pedido.dataHoraInicioTransporte() : c.getDataHoraInicioTransporte();
        LocalDateTime fimTransporte = pedido.dataHoraFimTransporte() != null
                ? pedido.dataHoraFimTransporte() : c.getDataHoraFim();

        FasesCarregamento.validar(c.getDataHoraInicio(), inicioCarregamento,
                fimCarregamento, inicioTransporte, fimTransporte);

        if (pedido.dataHoraInicioCarregamento() != null) {
            c.setDataHoraInicioCarregamento(inicioCarregamento);
        }
        if (pedido.dataHoraFimCarregamento() != null) {
            c.setDataHoraFimCarregamento(fimCarregamento);
        }
        if (pedido.dataHoraInicioTransporte() != null) {
            c.setDataHoraInicioTransporte(inicioTransporte);
        }
        if (pedido.dataHoraFimTransporte() != null) {
            c.setDataHoraFim(fimTransporte);
        }

        Carregamento guardado = carregamentoRepository.save(c);
        CarregamentoResumo resumo = mapper.toResumo(guardado);
        notificacaoService.publicarCarregamento(resumo);
        log.info("Fases operacionais do carregamento {} actualizadas por {}",
                id, principal != null ? principal.getEmail() : "sistema");
        return resumo;
    }

    @Transactional
    public CarregamentoResumo atualizarEstado(Long id, EstadoCarregamento novoEstado,
                                              String motivoRejeicao,
                                              AuthenticatedUser principal) {
        if (novoEstado == EstadoCarregamento.ATRASADO) {
            throw ApiException.unprocessable(
                    "O prazo definido para a viagem foi ultrapassado. "
                            + "A central atualiza este estado automaticamente.");
        }
        Carregamento c = obterComRelacoes(id);
        verificarAcesso(c, principal);

        if (!c.getEstado().podeTransitarPara(novoEstado)) {
            log.warn("Transição de estado inválida para o registo {}: {} -> {}",
                    id, c.getEstado(), novoEstado);
            throw ApiException.unprocessable(
                    "Não é possível alterar o estado para " + novoEstado.getEtiqueta()
                            + " neste momento.");
        }

        if (novoEstado == EstadoCarregamento.REJEITADO) {
            if (motivoRejeicao == null || motivoRejeicao.isBlank()) {
                throw ApiException.unprocessable(
                        "Faltam dados obrigatórios. Preencha os campos assinalados.");
            }
            c.setMotivoRejeicao(motivoRejeicao.trim());
            c.setDataHoraFim(LocalDateTime.now());
            registarAutoria(c, "REJEICAO", principal);
            vehicleOperations.libertarVeiculo(c.getVeiculo(), c.getId());
        }

        if (novoEstado == EstadoCarregamento.EM_TRANSITO) {
            LocalDateTime agora = LocalDateTime.now();
            if (c.getDataHoraInicio() == null) {
                c.setDataHoraInicio(agora);
            }
            // O momento da transição é o início real do transporte.
            if (c.getDataHoraInicioTransporte() == null) {
                c.setDataHoraInicioTransporte(agora);
            }
            if (c.getDataHoraInicioCarregamento() != null
                    && c.getDataHoraFimCarregamento() == null) {
                c.setDataHoraFimCarregamento(agora);
            }
            FasesCarregamento.validar(c.getDataHoraInicio(),
                    c.getDataHoraInicioCarregamento(), c.getDataHoraFimCarregamento(),
                    c.getDataHoraInicioTransporte(), c.getDataHoraFim());
            if (c.getVeiculo() != null) {
                c.getVeiculo().setEstado(EstadoVeiculo.EM_TRANSITO);
                veiculoRepository.save(c.getVeiculo());
            }
        }

        if (novoEstado == EstadoCarregamento.CONCLUIDO) {
            c.setDataHoraFim(LocalDateTime.now());
            registarAutoria(c, "CONCLUSAO", principal);
            vehicleOperations.libertarVeiculo(c.getVeiculo(), c.getId());
        }

        c.setEstado(novoEstado);
        Carregamento guardado = carregamentoRepository.save(c);
        CarregamentoResumo resumo = mapper.toResumo(guardado);
        notificacaoService.publicarCarregamento(resumo);
        if (novoEstado == EstadoCarregamento.REJEITADO
                || novoEstado == EstadoCarregamento.CONCLUIDO) {
            notificarAccaoManual(guardado, novoEstado, principal);
        }
        log.info("Estado do carregamento {} alterado para {} por {}",
                id, novoEstado, principal != null ? principal.getEmail() : "sistema");
        return resumo;
    }

    /** Grava a autoria verdadeira da acção manual, sem confiar no cliente. */
    private void registarAutoria(Carregamento c, String acao, AuthenticatedUser principal) {
        c.setAutorId(principal != null ? principal.getId() : null);
        c.setAutorNome(principal != null ? principal.getNome() : null);
        c.setAutorPerfil(principal != null && principal.getPerfil() != null
                ? principal.getPerfil().name() : OrigemNotificacao.DESCONHECIDO.name());
        c.setTipoAccao(acao);
        c.setDataHoraAccao(LocalDateTime.now());
    }

    /** Entrega ao motorista um aviso com o autor e o perfil reais da acção. */
    private void notificarAccaoManual(Carregamento c, EstadoCarregamento estado,
                                      AuthenticatedUser principal) {
        if (c.getMotorista() == null) {
            return;
        }
        boolean rejeicao = estado == EstadoCarregamento.REJEITADO;
        TipoNotificacao tipo = rejeicao
                ? TipoNotificacao.REJEICAO : TipoNotificacao.CONCLUSAO;
        String titulo = rejeicao ? "Guia rejeitada" : "Carregamento concluído";
        String mensagem = rejeicao
                ? "A central não aceitou o carregamento da guia " + c.getGuiaNumero()
                        + ". Motivo: " + c.getMotivoRejeicao()
                        + ". Corrija os dados e envie novamente."
                : "O carregamento da guia " + c.getGuiaNumero()
                        + " foi concluído. Já pode iniciar outro registo.";
        notificacaoService.registarAccaoManual(
                principal, tipo, c.getMotorista().getId(), c.getId(), c.getGuiaNumero(),
                titulo, mensagem,
                rejeicao ? "REJEITAR_CARREGAMENTO" : "CONCLUIR_CARREGAMENTO");
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
                    "Este registo já terminou. Não é possível enviar novas posições.");
        }
        if (c.getVeiculo() == null) {
            throw ApiException.unprocessable(
                    "Faltam dados obrigatórios. Preencha os campos assinalados.");
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
                .orElseThrow(() -> ApiException.notFound(
                        "Não foi possível encontrar o registo. Confirme os dados e tente novamente."));
    }

    public void verificarAcesso(Carregamento c, AuthenticatedUser principal) {
        if (principal == null) {
            return;
        }
        if (principal.getPerfil() == Role.MOTORISTA
                && (c.getMotorista() == null
                || !c.getMotorista().getId().equals(principal.getId()))) {
            throw ApiException.forbidden(
                    "Não tem permissão para esta operação. Fale com a central.");
        }
    }

    public void validarPrecisaoGps(double precisao) {
        ValidacaoGps.validar(precisao, parametroService.obterDouble(
                ParametroService.PRECISAO_MAXIMA_GPS_METROS));
    }
}
