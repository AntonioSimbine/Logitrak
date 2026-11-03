package mz.co.logitrack.service;

import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.domain.enums.EstadoVeiculo;
import mz.co.logitrack.dto.carregamento.CarregamentoDetalhe;
import mz.co.logitrack.dto.carregamento.CarregamentoResumo;
import mz.co.logitrack.dto.mobile.MobileDashboardResponse;
import mz.co.logitrack.dto.mobile.ReferenciasMobileResponse;
import mz.co.logitrack.mapper.CarregamentoMapper;
import mz.co.logitrack.repository.CarregamentoRepository;
import mz.co.logitrack.repository.FabricaRepository;
import mz.co.logitrack.repository.VeiculoRepository;
import mz.co.logitrack.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Endpoints de apoio à aplicação móvel (M2–M6). */
@Service
public class MobileService {

    private static final List<String> TIPOS_CARGA = List.of(
            "Cimento", "Clínquer", "Carvão", "Gesso", "Areia", "Brita", "Cal");

    private final CarregamentoRepository carregamentoRepository;
    private final FabricaRepository fabricaRepository;
    private final VeiculoRepository veiculoRepository;
    private final ParametroService parametroService;
    private final CarregamentoService carregamentoService;
    private final CarregamentoMapper mapper;

    public MobileService(CarregamentoRepository carregamentoRepository,
                         FabricaRepository fabricaRepository,
                         VeiculoRepository veiculoRepository,
                         ParametroService parametroService,
                         CarregamentoService carregamentoService,
                         CarregamentoMapper mapper) {
        this.carregamentoRepository = carregamentoRepository;
        this.fabricaRepository = fabricaRepository;
        this.veiculoRepository = veiculoRepository;
        this.parametroService = parametroService;
        this.carregamentoService = carregamentoService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ReferenciasMobileResponse referencias() {
        List<ReferenciasMobileResponse.FabricaMobile> fabricas =
                fabricaRepository.findByAtivaTrueOrderByNomeAsc().stream()
                        .map(f -> new ReferenciasMobileResponse.FabricaMobile(
                                f.getId(), f.getNome(), f.getEndereco(), f.getLatitude(),
                                f.getLongitude(), f.getRaioGeofenceMetros(),
                                f.getSlaLimiteHoras(), f.getHorarioAcostagem()))
                        .toList();
        List<ReferenciasMobileResponse.VeiculoMobile> veiculos =
                veiculoRepository.findByEstadoNotOrderByMatriculaAsc(EstadoVeiculo.MANUTENCAO)
                        .stream()
                        .map(v -> new ReferenciasMobileResponse.VeiculoMobile(
                                v.getId(), v.getMatricula(), v.getModelo(),
                                v.getCapacidadeCargaToneladas(), v.getEstado().name()))
                        .toList();
        return new ReferenciasMobileResponse(fabricas, veiculos,
                parametroService.mapaParametros(), TIPOS_CARGA);
    }

    @Transactional(readOnly = true)
    public MobileDashboardResponse dashboard(AuthenticatedUser motorista) {
        List<CarregamentoResumo> recentes = carregamentoRepository
                .findTop20ByMotoristaIdOrderByDataHoraInicioDesc(motorista.getId())
                .stream().map(mapper::toResumo).toList();
        return new MobileDashboardResponse(
                motorista.getId(),
                motorista.getNome(),
                recentes,
                carregamentoRepository.countByMotoristaId(motorista.getId()),
                carregamentoRepository.countByMotoristaIdAndEstado(
                        motorista.getId(), EstadoCarregamento.EM_TRANSITO),
                carregamentoRepository.countByMotoristaIdAndEstado(
                        motorista.getId(), EstadoCarregamento.ATRASADO),
                carregamentoRepository.countByMotoristaIdAndEstado(
                        motorista.getId(), EstadoCarregamento.CONCLUIDO)
        );
    }

    @Transactional(readOnly = true)
    public List<CarregamentoResumo> meusCarregamentos(AuthenticatedUser motorista) {
        return carregamentoRepository
                .findTop20ByMotoristaIdOrderByDataHoraInicioDesc(motorista.getId())
                .stream().map(mapper::toResumo).toList();
    }

    @Transactional(readOnly = true)
    public CarregamentoDetalhe detalhe(Long id, AuthenticatedUser motorista) {
        return carregamentoService.detalhe(id, motorista);
    }
}
