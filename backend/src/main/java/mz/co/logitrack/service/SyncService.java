package mz.co.logitrack.service;

import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.DocumentoGuia;
import mz.co.logitrack.domain.Fabrica;
import mz.co.logitrack.domain.LocalizacaoGPS;
import mz.co.logitrack.domain.Utilizador;
import mz.co.logitrack.domain.Veiculo;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.domain.enums.EstadoVeiculo;
import mz.co.logitrack.dto.carregamento.CarregamentoResumo;
import mz.co.logitrack.dto.carregamento.FasesCarregamento;
import mz.co.logitrack.dto.carregamento.SyncCarregamentoRequest;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.mapper.CarregamentoMapper;
import mz.co.logitrack.repository.CarregamentoRepository;
import mz.co.logitrack.repository.LocalizacaoGPSRepository;
import mz.co.logitrack.repository.UtilizadorRepository;
import mz.co.logitrack.repository.VeiculoRepository;
import mz.co.logitrack.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Núcleo do mecanismo offline-first:
 * <ul>
 *   <li>idempotência por uuidLocal;</li>
 *   <li>validação da precisão GPS (&le; parâmetro global);</li>
 *   <li>deteção de geofence por Haversine;</li>
 *   <li>gravação do carregamento, guia e localização numa única transação;</li>
 *   <li>transição REGISTADO -&gt; EM_TRANSITO.</li>
 * </ul>
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final int TAMANHO_MAXIMO_FOTO = 10 * 1024 * 1024;
    private static final String FOTOGRAFIA_INVALIDA =
            "A fotografia não serve. Tire outra com boa luz e o documento inteiro dentro da moldura.";

    private final CarregamentoRepository carregamentoRepository;
    private final VeiculoRepository veiculoRepository;
    private final UtilizadorRepository utilizadorRepository;
    private final LocalizacaoGPSRepository gpsRepository;
    private final GeofenceService geofenceService;
    private final ParametroService parametroService;
    private final DocumentoService documentoService;
    private final CarregamentoMapper mapper;
    private final NotificacaoService notificacaoService;
    private final VehicleOperationService vehicleOperations;
    private final TransactionTemplate transacao;

    public SyncService(CarregamentoRepository carregamentoRepository,
                       VeiculoRepository veiculoRepository,
                       UtilizadorRepository utilizadorRepository,
                       LocalizacaoGPSRepository gpsRepository,
                       GeofenceService geofenceService,
                       ParametroService parametroService,
                       DocumentoService documentoService,
                       CarregamentoMapper mapper,
                       NotificacaoService notificacaoService,
                       VehicleOperationService vehicleOperations,
                       PlatformTransactionManager transactionManager) {
        this.carregamentoRepository = carregamentoRepository;
        this.veiculoRepository = veiculoRepository;
        this.utilizadorRepository = utilizadorRepository;
        this.gpsRepository = gpsRepository;
        this.geofenceService = geofenceService;
        this.parametroService = parametroService;
        this.documentoService = documentoService;
        this.mapper = mapper;
        this.notificacaoService = notificacaoService;
        this.vehicleOperations = vehicleOperations;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    public record ResultadoSincronizacao(CarregamentoResumo carregamento, boolean duplicado,
                                         HttpStatus status) {
    }

    public ResultadoSincronizacao sincronizar(SyncCarregamentoRequest requisicao,
                                              AuthenticatedUser motorista) {

        // 1) Idempotência: o mesmo uuidLocal devolve o registo já processado,
        // desde que pertença ao motorista autenticado.
        Optional<ResultadoSincronizacao> existente =
                procurarDuplicado(requisicao.uuidLocal(), motorista);
        if (existente.isPresent()) {
            log.info("Sincronização idempotente: uuidLocal={} já existe",
                    requisicao.uuidLocal());
            return existente.get();
        }

        try {
            ResultadoSincronizacao resultado =
                    transacao.execute(status -> criarCarregamento(requisicao, motorista));
            if (resultado == null) {
                throw new IllegalStateException("Não foi possível enviar o registo à central");
            }
            return resultado;
        } catch (DataIntegrityViolationException ex) {
            // Duas sincronizações concorrentes do mesmo uuidLocal: a restrição
            // única da base de dados decide qual vence. A perdedora devolve
            // exatamente o mesmo contrato idempotente (HTTP 200).
            Optional<ResultadoSincronizacao> duplicado =
                    procurarDuplicado(requisicao.uuidLocal(), motorista);
            if (duplicado.isPresent()) {
                log.info("Sincronização concorrente resolvida por idempotência: uuidLocal={}",
                        requisicao.uuidLocal());
                return duplicado.get();
            }
            throw ex;
        }
    }

    private Optional<ResultadoSincronizacao> procurarDuplicado(UUID uuidLocal,
                                                                AuthenticatedUser motorista) {
        ResultadoSincronizacao resultado = transacao.execute(status ->
                carregamentoRepository.findByUuidLocal(uuidLocal)
                        .map(c -> {
                            verificarPropriedade(c, motorista);
                            return new ResultadoSincronizacao(
                                    mapper.toResumo(c), true, HttpStatus.OK);
                        })
                        .orElse(null));
        return Optional.ofNullable(resultado);
    }

    /**
     * Impede que a idempotência por uuidLocal seja usada para ler o registo de
     * outro motorista. O reenvio legítimo continua a devolver o mesmo registo
     * (HTTP 200, X-Idempotente: true) sem criar duplicados.
     */
    private void verificarPropriedade(Carregamento carregamento, AuthenticatedUser motorista) {
        Long autenticadoId = motorista != null ? motorista.getId() : null;
        Utilizador proprietario = carregamento.getMotorista();
        Long proprietarioId = proprietario != null ? proprietario.getId() : null;

        if (autenticadoId == null || !autenticadoId.equals(proprietarioId)) {
            log.warn("Acesso negado a carregamento de outro motorista: uuidLocal={} "
                            + "proprietarioId={} autenticadoId={}",
                    carregamento.getUuidLocal(), proprietarioId, autenticadoId);
            throw ApiException.forbidden(
                    "Não tem permissão para esta operação. Fale com a central.");
        }
    }

    private ResultadoSincronizacao criarCarregamento(SyncCarregamentoRequest requisicao,
                                                     AuthenticatedUser motorista) {

        // 2) Precisão do GPS (RNF005 / RN02) — teto fixo de 15 m.
        ValidacaoGps.validar(requisicao.precisaoMetros(), parametroService.obterDouble(
                ParametroService.PRECISAO_MAXIMA_GPS_METROS));

        // 3) Veículo e motorista.
        Veiculo veiculo = veiculoRepository.findByIdForUpdate(requisicao.veiculoId())
                .orElseThrow(() -> ApiException.unprocessable(
                        "Não foi possível encontrar o veículo selecionado. "
                                + "Confirme os dados e tente novamente."));
        if (veiculo.getEstado() == EstadoVeiculo.MANUTENCAO) {
            log.warn("Veículo {} em manutenção ao enviar registo {}",
                    veiculo.getMatricula(), requisicao.uuidLocal());
            throw ApiException.unprocessable(
                    "O veículo selecionado está em manutenção. Escolha outro veículo e tente novamente.");
        }
        vehicleOperations.validarNovaOperacao(veiculo, requisicao.quantidadeToneladas());
        Utilizador motoristaEntidade = utilizadorRepository.findById(motorista.getId())
                .orElseThrow(() -> ApiException.unauthorized(
                        "Não foi possível entrar. Verifique o e-mail e a palavra-passe."));

        // 4) Geofence / fábrica de origem (RN03).
        Fabrica fabrica = geofenceService.resolver(
                requisicao.fabricaId(),
                requisicao.latitude(),
                requisicao.longitude(),
                Boolean.TRUE.equals(requisicao.confirmarForaGeofence()));

        // 5) Fotografia obrigatória (RN01).
        byte[] fotografia = descodificarFotografia(requisicao.fotoBase64());

        LocalDateTime inicio = requisicao.dataHoraInicio() != null
                ? requisicao.dataHoraInicio() : LocalDateTime.now();
        FasesCarregamento.validar(inicio, requisicao.dataHoraInicioCarregamento(),
                requisicao.dataHoraFimCarregamento(),
                requisicao.dataHoraInicioTransporte(), null);

        // 6) Registo central.
        Carregamento carregamento = new Carregamento();
        carregamento.setUuidLocal(requisicao.uuidLocal());
        carregamento.setGuiaNumero(requisicao.guiaNumero().trim());
        carregamento.setTipoCarga(requisicao.tipoCarga().trim());
        carregamento.setQuantidadeToneladas(requisicao.quantidadeToneladas());
        carregamento.setEstado(EstadoCarregamento.REGISTADO);
        carregamento.setDataHoraInicio(inicio);
        carregamento.setDataHoraInicioCarregamento(requisicao.dataHoraInicioCarregamento());
        carregamento.setDataHoraFimCarregamento(requisicao.dataHoraFimCarregamento());
        carregamento.setDataHoraInicioTransporte(requisicao.dataHoraInicioTransporte());
        carregamento.setFabrica(fabrica);
        carregamento.setVeiculo(veiculo);
        carregamento.setMotorista(motoristaEntidade);
        // Flush imediato: a restrição UNIQUE de uuidLocal resolve corridas
        // entre sincronizações concorrentes antes de gravar a fotografia.
        carregamento = carregamentoRepository.saveAndFlush(carregamento);

        // 7) Guia digitalizada (validada e comprimida no dispositivo).
        DocumentoGuia documento = documentoService.salvarFoto(
                carregamento, fotografia, null, inicio);

        // 8) GPS de origem.
        LocalizacaoGPS localizacao = new LocalizacaoGPS(
                requisicao.latitude(), requisicao.longitude(),
                requisicao.precisaoMetros(), LocalDateTime.now(),
                carregamento, veiculo);
        gpsRepository.save(localizacao);

        // 9) Transição de sucesso: REGISTADO -> EM_TRANSITO.
        carregamento.setEstado(EstadoCarregamento.EM_TRANSITO);
        carregamento = carregamentoRepository.save(carregamento);
        veiculo.setEstado(EstadoVeiculo.EM_TRANSITO);
        veiculoRepository.save(veiculo);

        CarregamentoResumo resumo = mapper.toResumo(carregamento);
        notificacaoService.publicarCarregamento(resumo);

        log.info("Carregamento sincronizado: id={} guia={} uuidLocal={} fabrica={}",
                carregamento.getId(), carregamento.getGuiaNumero(),
                carregamento.getUuidLocal(), fabrica.getNome());

        return new ResultadoSincronizacao(resumo, false, HttpStatus.CREATED);
    }

    private byte[] descodificarFotografia(String fotoBase64) {
        if (fotoBase64 == null || fotoBase64.isBlank()) {
            throw ApiException.unprocessable("Falta a fotografia da guia.");
        }
        String conteudo = fotoBase64.trim();
        if (conteudo.startsWith("data:")) {
            int virgula = conteudo.indexOf(',');
            if (virgula < 0) {
                log.warn("Fotografia enviada com cabeçalho de dados inválido");
                throw ApiException.unprocessable(FOTOGRAFIA_INVALIDA);
            }
            conteudo = conteudo.substring(virgula + 1);
        }
        conteudo = conteudo.replaceAll("\\s", "");
        try {
            byte[] bytes = Base64.getDecoder().decode(conteudo);
            if (bytes.length == 0) {
                throw ApiException.unprocessable("Falta a fotografia da guia.");
            }
            if (bytes.length > TAMANHO_MAXIMO_FOTO) {
                log.warn("Fotografia com {} bytes excede o limite de {} bytes",
                        bytes.length, TAMANHO_MAXIMO_FOTO);
                throw ApiException.unprocessable(FOTOGRAFIA_INVALIDA);
            }
            return bytes;
        } catch (IllegalArgumentException ex) {
            log.warn("Fotografia com codificação Base64 malformada", ex);
            throw ApiException.unprocessable(FOTOGRAFIA_INVALIDA);
        }
    }

    private String formatarMetros(double valor) {
        if (Math.abs(valor - Math.rint(valor)) < 0.0001) {
            return String.format("%.0f", valor);
        }
        return String.format("%.1f", valor);
    }
}
